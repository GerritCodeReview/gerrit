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
import com.google.gson.GsonBuilder;
import com.google.gwtjsonrpc.client.RemoteJsonService;
import com.google.gwtjsonrpc.server.JsonServlet;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.inject.Inject;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * Base JSON servlet to ensure the current user is not forged.
 */
@SuppressWarnings("serial")
public final class GerritJsonServlet extends JsonServlet<GerritCall> {
  @SuppressWarnings("unchecked")
  public static final GerritCall getCurrentCall() {
    return JsonServlet.<GerritCall> getCurrentCall();
  }

  protected GerritServer server;
  private RemoteJsonService service;

  @Inject
  GerritJsonServlet(final GerritServer gs, final RemoteJsonService s) {
    server = gs;
    service = s;
  }

  @Override
  protected SignedToken createXsrfSignedToken() {
    return server.getXsrfToken();
  }

  @Override
  protected GerritCall createActiveCall(final HttpServletRequest req,
      final HttpServletResponse resp) {
    return new GerritCall(server, req, resp);
  }

  @Override
  protected GsonBuilder createGsonBuilder() {
    final GsonBuilder g = super.createGsonBuilder();

    g.registerTypeAdapter(org.spearce.jgit.diff.Edit.class,
        new org.spearce.jgit.diff.EditDeserializer());

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
  protected Object createServiceHandle() {
    return service;
  }
}
