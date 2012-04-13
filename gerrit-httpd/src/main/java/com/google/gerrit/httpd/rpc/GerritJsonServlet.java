// Copyright (C) 2008 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.httpd.rpc;

import com.google.common.collect.Lists;
import com.google.gerrit.audit.AuditEvent;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.common.audit.Audit;
import com.google.gerrit.common.auth.SignInRequired;
import com.google.gerrit.common.errors.NotSignedInException;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.CurrentUser;
import com.google.gson.GsonBuilder;
import com.google.gwtjsonrpc.common.RemoteJsonService;
import com.google.gwtjsonrpc.server.ActiveCall;
import com.google.gwtjsonrpc.server.JsonServlet;
import com.google.gwtjsonrpc.server.MethodHandle;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
/**
 * Base JSON servlet to ensure the current user is not forged.
 */
@SuppressWarnings("serial")
final class GerritJsonServlet extends JsonServlet<GerritJsonServlet.GerritCall> {
  private static final Logger log = LoggerFactory.getLogger(GerritJsonServlet.class);
  private static final ThreadLocal<GerritCall> currentCall =
      new ThreadLocal<GerritCall>();
  private static final ThreadLocal<MethodHandle> currentMethod =
      new ThreadLocal<MethodHandle>();
  private final Provider<WebSession> session;
  private final RemoteJsonService service;
  private final AuditService audit;


  @Inject
  GerritJsonServlet(final Provider<WebSession> w, final RemoteJsonService s,
      final AuditService a) {
    session = w;
    service = s;
    audit = a;
  }

  @Override
  protected GerritCall createActiveCall(final HttpServletRequest req,
      final HttpServletResponse rsp) {
    final GerritCall call = new GerritCall(session.get(), req, rsp);
    currentCall.set(call);
    return call;
  }

  @Override
  protected GsonBuilder createGsonBuilder() {
    return gerritDefaultGsonBuilder();
  }

  private static GsonBuilder gerritDefaultGsonBuilder() {
    final GsonBuilder g = defaultGsonBuilder();

    g.registerTypeAdapter(org.eclipse.jgit.diff.Edit.class,
        new org.eclipse.jgit.diff.EditDeserializer());

    return g;
  }

  @Override
  protected void preInvoke(final GerritCall call) {
    super.preInvoke(call);

    if (call.isComplete()) {
      return;
    }

    if (call.getMethod().getAnnotation(SignInRequired.class) != null) {
      // If SignInRequired is set on this method we must have both a
      // valid XSRF token *and* have the user signed in. Doing these
      // checks also validates that they agree on the user identity.
      //
      if (!call.requireXsrfValid() || !session.get().isSignedIn()) {
        call.onFailure(new NotSignedInException());
        return;
      }
    }
  }

  @Override
  protected Object createServiceHandle() {
    return service;
  }

  @Override
  protected void service(final HttpServletRequest req,
      final HttpServletResponse resp) throws IOException {
    try {
      super.service(req, resp);
    } finally {
        audit();
        currentCall.set(null);
    }
  }

  private void audit() {
    try {
      GerritCall call = currentCall.get();
      Audit note = (Audit) call.getMethod().getAnnotation(Audit.class);
      if (note != null) {
        final String sid = call.getWebSession().getToken();
        final CurrentUser username = call.getWebSession().getCurrentUser();
        final List<Object> args =
            extractParams(note, call);
        final String what = extractWhat(note, call.getMethod().getName());
        final Object result = call.getResult();

        audit.dispatch(new AuditEvent(sid, username, what, call.getWhen(), args,
            result));
      }
    } catch (Throwable all) {
      log.error("Unable to log the call", all);
    }
  }

  private List<Object> extractParams(final Audit note, final GerritCall call) {
    List<Object> args = Lists.newArrayList(Arrays.asList(call.getParams()));
    for (int idx : note.obfuscate()) {
      args.set(idx, "*****");
    }
    return args;
  }

  private String extractWhat(final Audit note, final String methodName) {
    String what = note.action();
    if (what.length() == 0) {
      boolean ccase = Character.isLowerCase(methodName.charAt(0));

      StringBuilder sb = new StringBuilder();
      for (int i = 0; i < methodName.length(); i++) {
        char c = methodName.charAt(i);
        if (Character.isLowerCase(c) != ccase) {
          sb.append(' ');
        }
        sb.append(Character.toLowerCase(c));
      }
      what = sb.toString();
    }

    return what;
  }

  static class GerritCall extends ActiveCall {
    private final WebSession session;
    private final long when;
    private static final Field resultField;

    // Needed to allow access to non-public result field in GWT/JSON-RPC
    static {
      Field declaredField = null;
      try {
        declaredField = ActiveCall.class.getDeclaredField("result");
        declaredField.setAccessible(true);
      } catch (Exception e) {
        log.error("Unable to expose RPS/JSON result field");
      }

      resultField = declaredField;
    }

    // Surrogate of the missing getResult() in GWT/JSON-RPC
    public Object getResult() {
      if(resultField == null) {
        return "";
      }

      try {
        return resultField.get(this);
      } catch (IllegalArgumentException e) {
        log.error("Cannot access result field");
      } catch (IllegalAccessException e) {
        log.error("No permissions to access result field");
      }

      return null;
    }

    GerritCall(final WebSession session, final HttpServletRequest i,
        final HttpServletResponse o) {
      super(i, o);
      this.session = session;
      this.when = System.currentTimeMillis();
    }

    @Override
    public MethodHandle getMethod() {
      if (currentMethod.get() == null)
        return super.getMethod();
      else
        return currentMethod.get();
    }

    @Override
    public void onFailure(final Throwable error) {
      if (error instanceof IllegalArgumentException
          || error instanceof IllegalStateException) {
        super.onFailure(error);
      } else if (error instanceof OrmException
          || error instanceof RuntimeException) {
        onInternalFailure(error);
      } else {
        super.onFailure(error);
      }
    }

    @Override
    public boolean xsrfValidate() {
      final String keyIn = getXsrfKeyIn();
      if (keyIn == null || "".equals(keyIn)) {
        // Anonymous requests don't need XSRF protection, they shouldn't
        // be able to cause critical state changes.
        //
        return !session.isSignedIn();

      } else {
        // The session must exist, and must be using this token.
        //
        return session.isSignedIn() && session.isTokenValid(keyIn);
      }
    }

    public WebSession getWebSession() {
      return session;
    }

    public long getWhen() {
      return when;
    }

    public long getElapsed() {
      return System.currentTimeMillis() - when;
    }
  }

}
