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

import com.google.gerrit.audit.AuditEvent;
import com.google.gerrit.audit.AuditListener;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.common.audit.Audit;
import com.google.gerrit.common.auth.SignInRequired;
import com.google.gerrit.common.errors.NotSignedInException;
import com.google.gerrit.httpd.WebSession;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gwtjsonrpc.common.RemoteJsonService;
import com.google.gwtjsonrpc.server.ActiveCall;
import com.google.gwtjsonrpc.server.JsonServlet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtjsonrpc.server.MethodHandle;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.apache.log4j.Logger;

import java.io.IOException;
import java.lang.reflect.Field;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.List;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
/**
 * Base JSON servlet to ensure the current user is not forged.
 */
@SuppressWarnings("serial")
final class GerritJsonServlet extends JsonServlet<GerritJsonServlet.GerritCall> {
  private final Provider<WebSession> session;
  private final RemoteJsonService service;
  private final AuditService audit;

  private static final Logger LOG = Logger.getLogger(GerritJsonServlet.class);

  private static ThreadLocal<GerritCall> currentCall;
  private static ThreadLocal<MethodHandle> currentMethod;
  static {
    currentCall = new ThreadLocal<GerritCall>();
    currentMethod = new ThreadLocal<MethodHandle>();
  }

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
    final GsonBuilder g = super.createGsonBuilder();

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

  protected MethodHandle lookupMethod(final String methodName) {
//    return withAudit(super.lookupMethod(methodName));
    return super.lookupMethod(methodName);
  }

  @Override
  protected void service(final HttpServletRequest req,
      final HttpServletResponse resp) throws IOException {
    try {
      super.service(req, resp);
    } finally {
      try {
        audit();
      } catch (Throwable ignoreExceptionWhileLogging) {
      } finally {
        currentCall.set(null);
      }
    }
  }

  private void audit() {
    try {
      GerritCall call = currentCall.get();
      Audit note = (Audit) call.getMethod().getAnnotation(Audit.class);
      if (note != null) {
        final Gson gson = createGsonBuilder()
        .setDateFormat(DateFormat.LONG)
        .setFieldNamingPolicy(FieldNamingPolicy.UPPER_CAMEL_CASE)
        .setVersion(1.0)
        .create();

        final String sid = call.getWebSession().getToken();
        final String username = extractUsername(call);
        final List<Object> args = extractParams(note, call, gson);
        final String what = extractWhat(note, call.getMethod().getName());
        final Object result = call.getResult();

        audit.track(new AuditEvent(sid, username, what, call.getWhen(), args,
            result, call.getElapsed()));
      }
    } catch (Throwable all) {
      LOG.error("Unable to log the call", all);
    }
  }

  private String extractUsername(GerritCall call) {
    final String username =
        call.getWebSession().getCurrentUser().getUserName();
    if (username == null) {
      return "SYS";
    } else {
      return username;
    }
  }



  private List<Object> extractParams(final Audit note, final GerritCall call,
      Gson gson) {
    final List<Object> args = new ArrayList<Object>();
    final Object[] params = call.getParams();

    final int[] obfuscate = note.obfuscate();
    for (int i = 0; i < params.length; i++) {
      Object param = params[i];
      if (obfuscate.length > 0) {
        for (int id : obfuscate) {
          if (id == i) {
            param="*****";
            break;
          }
        }
      }

      args.add(param);
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
    private static Field resultField;

    // Needed to allow access to non-public result field in GWT/JSON-RPC
    static {
      try {
        resultField = ActiveCall.class.getDeclaredField("result");
        resultField.setAccessible(true);
      } catch (Exception e) {
        LOG.error("Unable to expose RPS/JSON result field");
        resultField = null;
      }
    }

    // Surrogate of the missing getResult() in GWT/JSON-RPC
    public Object getResult() {
      try {
        return resultField.get(this);
      } catch (IllegalArgumentException e) {
        LOG.error("Cannot access result field");
      } catch (IllegalAccessException e) {
        LOG.error("No permissions to access result field");
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
