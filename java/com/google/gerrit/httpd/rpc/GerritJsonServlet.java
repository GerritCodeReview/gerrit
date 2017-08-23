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

import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.audit.AuditService;
import com.google.gerrit.audit.RpcAuditEvent;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.audit.Audit;
import com.google.gerrit.common.auth.SignInRequired;
import com.google.gerrit.common.errors.NotSignedInException;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.httpd.WebSession;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.CurrentUser;
import com.google.gson.GsonBuilder;
import com.google.gwtjsonrpc.common.RemoteJsonService;
import com.google.gwtjsonrpc.server.ActiveCall;
import com.google.gwtjsonrpc.server.JsonServlet;
import com.google.gwtjsonrpc.server.MethodHandle;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Base JSON servlet to ensure the current user is not forged. */
@SuppressWarnings("serial")
final class GerritJsonServlet extends JsonServlet<GerritJsonServlet.GerritCall> {
  private static final Logger log = LoggerFactory.getLogger(GerritJsonServlet.class);
  private static final ThreadLocal<GerritCall> currentCall = new ThreadLocal<>();
  private static final ThreadLocal<MethodHandle> currentMethod = new ThreadLocal<>();
  private final DynamicItem<WebSession> session;
  private final RemoteJsonService service;
  private final AuditService audit;

  @Inject
  GerritJsonServlet(final DynamicItem<WebSession> w, RemoteJsonService s, AuditService a) {
    session = w;
    service = s;
    audit = a;
  }

  @Override
  protected GerritCall createActiveCall(final HttpServletRequest req, HttpServletResponse rsp) {
    final GerritCall call = new GerritCall(session.get(), req, new AuditedHttpServletResponse(rsp));
    currentCall.set(call);
    return call;
  }

  @Override
  protected GsonBuilder createGsonBuilder() {
    return gerritDefaultGsonBuilder();
  }

  private static GsonBuilder gerritDefaultGsonBuilder() {
    final GsonBuilder g = defaultGsonBuilder();

    g.registerTypeAdapter(
        org.eclipse.jgit.diff.Edit.class, new org.eclipse.jgit.diff.EditDeserializer());

    return g;
  }

  @Override
  protected void preInvoke(GerritCall call) {
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
      }
    }
  }

  @Override
  protected Object createServiceHandle() {
    return service;
  }

  @Override
  protected void service(HttpServletRequest req, HttpServletResponse resp) throws IOException {
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
      MethodHandle method = call.getMethod();
      if (method == null) {
        return;
      }
      Audit note = method.getAnnotation(Audit.class);
      if (note != null) {
        String sid = call.getWebSession().getSessionId();
        CurrentUser username = call.getWebSession().getUser();
        ListMultimap<String, ?> args = extractParams(note, call);
        String what = extractWhat(note, call);
        Object result = call.getResult();

        audit.dispatch(
            new RpcAuditEvent(
                sid,
                username,
                what,
                call.getWhen(),
                args,
                call.getHttpServletRequest().getMethod(),
                call.getHttpServletRequest().getMethod(),
                ((AuditedHttpServletResponse) (call.getHttpServletResponse())).getStatus(),
                result));
      }
    } catch (Throwable all) {
      log.error("Unable to log the call", all);
    }
  }

  private ListMultimap<String, ?> extractParams(Audit note, GerritCall call) {
    ListMultimap<String, Object> args = MultimapBuilder.hashKeys().arrayListValues().build();

    Object[] params = call.getParams();
    for (int i = 0; i < params.length; i++) {
      args.put("$" + i, params[i]);
    }

    for (int idx : note.obfuscate()) {
      args.removeAll("$" + idx);
      args.put("$" + idx, "*****");
    }
    return args;
  }

  private String extractWhat(Audit note, GerritCall call) {
    Class<?> methodClass = call.getMethodClass();
    String methodClassName = methodClass != null ? methodClass.getName() : "<UNKNOWN_CLASS>";
    methodClassName = methodClassName.substring(methodClassName.lastIndexOf(".") + 1);
    String what = note.action();
    if (what.length() == 0) {
      what = call.getMethod().getName();
    }

    return methodClassName + "." + what;
  }

  static class GerritCall extends ActiveCall {
    private final WebSession session;
    private final long when;
    private static final Field resultField;
    private static final Field methodField;

    // Needed to allow access to non-public result field in GWT/JSON-RPC
    static {
      resultField = getPrivateField(ActiveCall.class, "result");
      methodField = getPrivateField(MethodHandle.class, "method");
    }

    private static Field getPrivateField(Class<?> clazz, String fieldName) {
      Field declaredField = null;
      try {
        declaredField = clazz.getDeclaredField(fieldName);
        declaredField.setAccessible(true);
      } catch (Exception e) {
        log.error("Unable to expose RPS/JSON result field");
      }
      return declaredField;
    }

    // Surrogate of the missing getMethodClass() in GWT/JSON-RPC
    public Class<?> getMethodClass() {
      if (methodField == null) {
        return null;
      }

      try {
        Method method = (Method) methodField.get(this.getMethod());
        return method.getDeclaringClass();
      } catch (IllegalArgumentException e) {
        log.error("Cannot access result field");
      } catch (IllegalAccessException e) {
        log.error("No permissions to access result field");
      }

      return null;
    }

    // Surrogate of the missing getResult() in GWT/JSON-RPC
    public Object getResult() {
      if (resultField == null) {
        return null;
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

    GerritCall(WebSession session, HttpServletRequest i, HttpServletResponse o) {
      super(i, o);
      this.session = session;
      this.when = TimeUtil.nowMs();
    }

    @Override
    public MethodHandle getMethod() {
      if (currentMethod.get() == null) {
        return super.getMethod();
      }
      return currentMethod.get();
    }

    @Override
    public void onFailure(Throwable error) {
      if (error instanceof IllegalArgumentException || error instanceof IllegalStateException) {
        super.onFailure(error);
      } else if (error instanceof OrmException || error instanceof RuntimeException) {
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

      } else if (session.isSignedIn() && session.isValidXGerritAuth(keyIn)) {
        // The session must exist, and must be using this token.
        //
        session.getUser().setAccessPath(AccessPath.JSON_RPC);
        return true;
      }
      return false;
    }

    public WebSession getWebSession() {
      return session;
    }

    public long getWhen() {
      return when;
    }

    public long getElapsed() {
      return TimeUtil.nowMs() - when;
    }
  }
}
