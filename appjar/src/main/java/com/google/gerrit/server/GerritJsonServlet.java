// Copyright 2008 Google Inc.
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
import com.google.gerrit.git.MergeQueue;
import com.google.gwtjsonrpc.server.JsonServlet;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

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
    MergeQueue.terminate();
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
  protected GerritCall createActiveCall(final HttpServletRequest req,
      final HttpServletResponse resp) {
    return new GerritCall(server, req, resp);
  }

  @Override
  protected void preInvoke(final GerritCall call) {
    super.preInvoke(call);
    if (!call.isComplete()
        && call.getMethod().getAnnotation(SignInRequired.class) != null
        && call.getAccountId() == null) {
      // If SignInRequired exists on the method and we don't have an
      // account id in the request, we can't permit this call to finish.
      //
      call.onFailure(new NotSignedInException());
    }
  }

  @Override
  protected abstract Object createServiceHandle() throws Exception;
}
