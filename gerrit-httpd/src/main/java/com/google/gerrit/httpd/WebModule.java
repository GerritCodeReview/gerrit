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

package com.google.gerrit.httpd;

import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.httpd.auth.become.BecomeAnyAccountLoginServlet;
import com.google.gerrit.httpd.auth.container.HttpAuthModule;
import com.google.gerrit.httpd.auth.ldap.LdapAuthModule;
import com.google.gerrit.httpd.auth.openid.OpenIdModule;
import com.google.gerrit.httpd.gitweb.GitWebModule;
import com.google.gerrit.httpd.rpc.UiRpcModule;
import com.google.gerrit.reviewdb.AuthType;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.ChangeUserName;
import com.google.gerrit.server.account.ClearPassword;
import com.google.gerrit.server.account.GeneratePassword;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritRequestModule;
import com.google.gerrit.server.contact.ContactStore;
import com.google.gerrit.server.contact.ContactStoreProvider;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.ssh.SshKeyCache;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.servlet.RequestScoped;
import com.google.inject.servlet.ServletModule;

import java.net.SocketAddress;

import javax.annotation.Nullable;

public class WebModule extends FactoryModule {
  private final Provider<SshInfo> sshInfoProvider;
  private final Provider<SshKeyCache> sshKeyCacheProvider;
  private final AuthType authType;
  private final boolean wantSSL;
  private final GitWebConfig gitWebConfig;

  @Inject
  WebModule(final Provider<SshInfo> sshInfoProvider,
      final Provider<SshKeyCache> sshKeyCacheProvider,
      final AuthConfig authConfig,
      @CanonicalWebUrl @Nullable final String canonicalUrl,
      final Injector creatingInjector) {
    this.sshInfoProvider = sshInfoProvider;
    this.sshKeyCacheProvider = sshKeyCacheProvider;
    this.authType = authConfig.getAuthType();
    this.wantSSL = canonicalUrl != null && canonicalUrl.startsWith("https:");

    this.gitWebConfig =
        creatingInjector.createChildInjector(new AbstractModule() {
          @Override
          protected void configure() {
            bind(GitWebConfig.class);
          }
        }).getInstance(GitWebConfig.class);
  }

  @Override
  protected void configure() {
    install(new ServletModule() {
      @Override
      protected void configureServlets() {
        filter("/*").through(RequestCleanupFilter.class);
      }
    });

    if (wantSSL) {
      install(new RequireSslFilter.Module());
    }

    switch (authType) {
      case OPENID:
        install(new OpenIdModule());
        break;

      case HTTP:
      case HTTP_LDAP:
        install(new HttpAuthModule());
        break;

      case LDAP:
      case LDAP_BIND:
        install(new LdapAuthModule());
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
        throw new ProvisionException("Unsupported loginType: " + authType);
    }

    install(new UrlModule());
    install(new UiRpcModule());
    install(new GerritRequestModule());
    install(new ProjectServlet.Module());

    bind(SshInfo.class).toProvider(sshInfoProvider);
    bind(SshKeyCache.class).toProvider(sshKeyCacheProvider);

    bind(GitWebConfig.class).toInstance(gitWebConfig);
    if (gitWebConfig.getGitwebCGI() != null) {
      install(new GitWebModule());
    }

    bind(ContactStore.class).toProvider(ContactStoreProvider.class).in(
        SINGLETON);
    bind(GerritConfigProvider.class);
    bind(GerritConfig.class).toProvider(GerritConfigProvider.class);

    bind(AccountManager.class);
    bind(ChangeUserName.CurrentUser.class);
    factory(ChangeUserName.Factory.class);
    factory(ClearPassword.Factory.class);
    factory(GeneratePassword.Factory.class);

    bind(SocketAddress.class).annotatedWith(RemotePeer.class).toProvider(
        HttpRemotePeerProvider.class).in(RequestScoped.class);

    install(WebSession.module());

    bind(CurrentUser.class).toProvider(HttpCurrentUserProvider.class).in(
        RequestScoped.class);
    bind(IdentifiedUser.class).toProvider(HttpIdentifiedUserProvider.class).in(
        RequestScoped.class);
  }
}
