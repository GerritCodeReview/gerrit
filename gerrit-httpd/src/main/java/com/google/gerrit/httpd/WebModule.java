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

import static com.google.gerrit.extensions.registration.PrivateInternals_DynamicTypes.registerInParentInjectors;
import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.httpd.gitweb.GitWebModule;
import com.google.gerrit.httpd.rpc.UiRpcModule;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.CmdLineParserModule;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.ChangeUserName;
import com.google.gerrit.server.account.ClearPassword;
import com.google.gerrit.server.account.GeneratePassword;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.config.GerritRequestModule;
import com.google.gerrit.server.config.RealmWebModule;
import com.google.gerrit.server.contact.ContactStore;
import com.google.gerrit.server.contact.ContactStoreProvider;
import com.google.gerrit.server.util.GuiceRequestScopePropagator;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.servlet.RequestScoped;
import com.google.inject.servlet.ServletModule;

import java.net.SocketAddress;

import javax.annotation.Nullable;

public class WebModule extends FactoryModule {
  private final UrlModule.UrlConfig urlConfig;
  private final boolean wantSSL;
  private final GitWebConfig gitWebConfig;
  private final ServletModule realmWebModule;

  @Inject
  WebModule(final UrlModule.UrlConfig urlConfig,
      @CanonicalWebUrl @Nullable final String canonicalUrl,
      final Injector creatingInjector,
      @RealmWebModule ServletModule realmWebModule) {
    this.urlConfig = urlConfig;
    this.realmWebModule = realmWebModule;
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
    bind(RequestScopePropagator.class).to(GuiceRequestScopePropagator.class);
    bind(HttpRequestContext.class);

    if (wantSSL) {
      install(new RequireSslFilter.Module());
    }
    install(realmWebModule);

    install(new UrlModule(urlConfig));
    install(new UiRpcModule());
    install(new GerritRequestModule());
    install(new GitOverHttpServlet.Module());

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
    install(new CmdLineParserModule());
    factory(GeneratePassword.Factory.class);

    bind(SocketAddress.class).annotatedWith(RemotePeer.class).toProvider(
        HttpRemotePeerProvider.class).in(RequestScoped.class);

    install(new LifecycleModule() {
      @Override
      protected void configure() {
        listener().toInstance(registerInParentInjectors());
      }
    });
  }
}
