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

import com.google.gerrit.common.auth.SignInRequired;
import com.google.gerrit.common.errors.NotSignedInException;
import com.google.gerrit.httpd.WebSession;
import com.google.gson.GsonBuilder;
import com.google.gwtjsonrpc.common.RemoteJsonService;
import com.google.gwtjsonrpc.server.ActiveCall;
import com.google.gwtjsonrpc.server.JsonServlet;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Base JSON servlet to ensure the current user is not forged.
 */
@SuppressWarnings("serial")
final class GerritJsonServlet extends JsonServlet<GerritJsonServlet.GerritCall> {
  private final Provider<WebSession> session;
  private final RemoteJsonService service;

  @Inject
  GerritJsonServlet(final Provider<WebSession> w, final RemoteJsonService s) {
    session = w;
    service = s;
  }

  @Override
  protected GerritCall createActiveCall(final HttpServletRequest req,
      final HttpServletResponse rsp) {
    return new GerritCall(session.get(), req, rsp);
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

  static class GerritCall extends ActiveCall {
    private final WebSession session;

    GerritCall(final WebSession session, final HttpServletRequest i,
        final HttpServletResponse o) {
      super(i, o);
      this.session = session;
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
  }
}
