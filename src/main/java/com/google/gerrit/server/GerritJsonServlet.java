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

package com.google.gerrit.server;

import com.google.gerrit.client.rpc.NotSignedInException;
import com.google.gerrit.client.rpc.SignInRequired;
import com.google.gerrit.git.WorkQueue;
import com.google.gwtjsonrpc.server.JsonServlet;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import org.jsecurity.web.WebUtils;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Base JSON servlet to ensure the current user is not forged.
 */
public abstract class GerritJsonServlet extends JsonServlet<GerritCall> {
  @SuppressWarnings("unchecked")
  public static final GerritCall getCurrentCall() {
    return JsonServlet.<GerritCall> getCurrentCall();
  }

  private GerritServer server;

  @Override
  public void init(final ServletConfig config) throws ServletException {
    super.init(config);

    try {
      server = GerritServer.getInstance();
    } catch (OrmException e) {
      throw new ServletException("Cannot configure GerritServer", e);
    } catch (XsrfException e) {
      throw new ServletException("Cannot configure GerritServer", e);
    }
  }

  @Override
  public void destroy() {
    WorkQueue.terminate();
    GerritServer.closeDataSource();
    super.destroy();
  }

  @Override
  protected SignedToken createXsrfSignedToken() throws XsrfException {
    try {
      return GerritServer.getInstance().getXsrfToken();
    } catch (OrmException e) {
      throw new XsrfException("Cannot configure GerritServer", e);
    }
  }

  @Override
  protected void service(final HttpServletRequest req,
      final HttpServletResponse rsp) throws IOException {
    try {
      WebUtils.bind(req);
      WebUtils.bind(rsp);
      super.service(req, rsp);
    } finally {
      WebUtils.unbindServletRequest();
      WebUtils.unbindServletResponse();
    }
  }

  @Override
  protected GerritCall createActiveCall(final HttpServletRequest req,
      final HttpServletResponse resp) {
    return new GerritCall(server, req, resp);
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
      if (!call.requireXsrfValid()) {
        return;
      }

      if (call.getAccountId() == null) {
        call.onFailure(new NotSignedInException());
        return;
      }
    }
  }

  @Override
  protected abstract Object createServiceHandle() throws Exception;
}
