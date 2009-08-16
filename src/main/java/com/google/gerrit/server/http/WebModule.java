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

package com.google.gerrit.server.http;

import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.client.data.GerritConfig;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritConfigProvider;
import com.google.gerrit.server.config.GerritRequestModule;
import com.google.gerrit.server.rpc.UiRpcModule;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gwtexpui.server.CacheControlFilter;
import com.google.inject.Key;
import com.google.inject.Provider;
import com.google.inject.servlet.RequestScoped;
import com.google.inject.servlet.ServletModule;

import java.net.SocketAddress;

class WebModule extends FactoryModule {
  private final Provider<SshInfo> sshInfoProvider;

  WebModule(final Provider<SshInfo> sshInfoProvider) {
    this.sshInfoProvider = sshInfoProvider;
  }

  @Override
  protected void configure() {
    install(new ServletModule() {
      @Override
      protected void configureServlets() {
        filter("/*").through(Key.get(CacheControlFilter.class));
        bind(Key.get(CacheControlFilter.class)).in(SINGLETON);

        serve("/").with(HostPageServlet.class);
        serve("/Gerrit").with(LegacyGerritServlet.class);
        serve("/cat/*").with(CatServlet.class);
        serve("/logout").with(HttpLogoutServlet.class);
        serve("/prettify/*").with(PrettifyServlet.class);
        serve("/signout").with(HttpLogoutServlet.class);
        serve("/ssh_info").with(SshServlet.class);
        serve("/static/*").with(StaticServlet.class);
      }
    });
    install(new UiRpcModule());
    install(new GerritRequestModule());

    bind(SshInfo.class).toProvider(sshInfoProvider);
    bind(GerritConfig.class).toProvider(GerritConfigProvider.class).in(
        SINGLETON);
    bind(AccountManager.class).in(SINGLETON);
    bind(SocketAddress.class).annotatedWith(RemotePeer.class).toProvider(
        HttpRemotePeerProvider.class).in(RequestScoped.class);

    bind(WebSession.class).in(RequestScoped.class);
    bind(WebSessionManager.class).in(SINGLETON);

    bind(CurrentUser.class).toProvider(HttpCurrentUserProvider.class).in(
        RequestScoped.class);
    bind(IdentifiedUser.class).toProvider(HttpIdentifiedUserProvider.class).in(
        RequestScoped.class);
  }
}
