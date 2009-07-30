// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.rpc;

import com.google.gerrit.server.config.ServletNameImpl;
import com.google.gerrit.server.patch.PatchDetailServiceImpl;
import com.google.gwtjsonrpc.client.RemoteJsonService;
import com.google.inject.Key;
import com.google.inject.Scopes;
import com.google.inject.servlet.ServletModule;

/** Registers servlets to answer RPCs from client UI. */
public class UiRpcModule extends ServletModule {
  @Override
  protected void configureServlets() {
    serve("/login").with(OpenIdLoginServlet.class);

    rpc(AccountServiceImpl.class);
    rpc(AccountSecurityImpl.class);
    rpc(GroupAdminServiceImpl.class);
    rpc(ChangeDetailServiceImpl.class);
    rpc(ChangeListServiceImpl.class);
    rpc(ChangeManageServiceImpl.class);
    rpc(OpenIdServiceImpl.class);
    rpc(PatchDetailServiceImpl.class);
    rpc(ProjectAdminServiceImpl.class);
    rpc(SuggestServiceImpl.class);
    rpc(SystemInfoServiceImpl.class);
  }

  private void rpc(Class<? extends RemoteJsonService> clazz) {
    String name = clazz.getSimpleName();
    if (name.endsWith("Impl")) {
      name = name.substring(0, name.length() - 4);
    }
    rpc(name, clazz);
  }

  private void rpc(final String name, Class<? extends RemoteJsonService> clazz) {
    final Key<GerritJsonServlet> srv =
        Key.get(GerritJsonServlet.class, ServletNameImpl.named(name));
    final GerritJsonServletProvider provider =
        new GerritJsonServletProvider(clazz);
    serve("/gerrit/rpc/" + name).with(srv);
    bind(srv).toProvider(provider).in(Scopes.SINGLETON);
  }
}
