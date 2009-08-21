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
import com.google.gerrit.client.reviewdb.AuthType;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritRequestModule;
import com.google.gerrit.server.contact.ContactStore;
import com.google.gerrit.server.contact.ContactStoreProvider;
import com.google.gerrit.server.openid.OpenIdModule;
import com.google.gerrit.server.rpc.UiRpcModule;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.servlet.RequestScoped;
import com.google.inject.servlet.ServletModule;

import java.net.SocketAddress;

class WebModule extends FactoryModule {
  private final Provider<SshInfo> sshInfoProvider;
  private final AuthType loginType;

  @Inject
  WebModule(final Provider<SshInfo> sshInfoProvider, final AuthConfig authConfig) {
    this(sshInfoProvider, authConfig.getLoginType());
  }

  WebModule(final Provider<SshInfo> sshInfoProvider, final AuthType loginType) {
    this.sshInfoProvider = sshInfoProvider;
    this.loginType = loginType;
  }

  @Override
  protected void configure() {
    install(new ServletModule() {
      @Override
      protected void configureServlets() {
        filter("/*").through(RequestCleanupFilter.class);
      }
    });

    switch (loginType) {
      case OPENID:
        install(new OpenIdModule());
        break;

      case HTTP:
      case HTTP_LDAP:
        install(new HttpAuthModule());
        break;

      case DEVELOPMENT_BECOME_ANY_ACCOUNT:
        install(new ServletModule() {
          @Override
          protected void configureServlets() {
            serve("/become").with(BecomeAnyAccountLoginServlet.class);
          }
        });
        break;

      default:
        throw new ProvisionException("Unsupported loginType: " + loginType);
    }

    install(new UrlModule());
    install(new UiRpcModule());
    install(new GerritRequestModule());

    bind(SshInfo.class).toProvider(sshInfoProvider);
    bind(ContactStore.class).toProvider(ContactStoreProvider.class).in(
        SINGLETON);
    bind(GerritConfig.class).toProvider(GerritConfigProvider.class).in(
        SINGLETON);
    bind(AccountManager.class).in(SINGLETON);
    bind(SocketAddress.class).annotatedWith(RemotePeer.class).toProvider(
        HttpRemotePeerProvider.class).in(RequestScoped.class);

    install(WebSession.module());

    bind(CurrentUser.class).toProvider(HttpCurrentUserProvider.class).in(
        RequestScoped.class);
    bind(IdentifiedUser.class).toProvider(HttpIdentifiedUserProvider.class).in(
        RequestScoped.class);
  }
}
