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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.GerritConfig;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.webui.WebUiPlugin;
import com.google.gerrit.httpd.auth.become.BecomeAnyAccountModule;
import com.google.gerrit.httpd.auth.container.HttpAuthModule;
import com.google.gerrit.httpd.auth.container.HttpsClientSslCertModule;
import com.google.gerrit.httpd.auth.ldap.LdapAuthModule;
import com.google.gerrit.httpd.gitweb.GitWebModule;
import com.google.gerrit.httpd.rpc.UiRpcModule;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.RemotePeer;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.GerritRequestModule;
import com.google.gerrit.server.git.AsyncReceiveCommits;
import com.google.gerrit.server.util.GuiceRequestScopePropagator;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.ProvisionException;
import com.google.inject.servlet.RequestScoped;

import java.net.SocketAddress;

public class WebModule extends LifecycleModule {
  private final AuthConfig authConfig;
  private final boolean wantSSL;
  private final GitWebConfig gitWebConfig;
  private final GerritOptions options;

  @Inject
  WebModule(final AuthConfig authConfig,
      @CanonicalWebUrl @Nullable final String canonicalUrl,
      GerritOptions options,
      final Injector creatingInjector) {
    this.authConfig = authConfig;
    this.wantSSL = canonicalUrl != null && canonicalUrl.startsWith("https:");
    this.options = options;

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
    install(new RunAsFilter.Module());

    installAuthModule();
    if (options.enableMasterFeatures()) {
      install(new UrlModule(options, authConfig));
      install(new UiRpcModule());
    }
    install(new GerritRequestModule());
    install(new GitOverHttpServlet.Module(options.enableMasterFeatures()));

    bind(GitWebConfig.class).toInstance(gitWebConfig);
    if (gitWebConfig.getGitwebCGI() != null) {
      install(new GitWebModule());
    }

    bind(GerritConfigProvider.class);
    bind(GerritConfig.class).toProvider(GerritConfigProvider.class);
    DynamicSet.setOf(binder(), WebUiPlugin.class);

    install(new AsyncReceiveCommits.Module());

    bind(SocketAddress.class).annotatedWith(RemotePeer.class).toProvider(
        HttpRemotePeerProvider.class).in(RequestScoped.class);

    bind(ProxyProperties.class).toProvider(ProxyPropertiesProvider.class);

    listener().toInstance(registerInParentInjectors());
  }

  private void installAuthModule() {
    switch (authConfig.getAuthType()) {
      case HTTP:
      case HTTP_LDAP:
        install(new HttpAuthModule(authConfig));
        break;

      case CLIENT_SSL_CERT_LDAP:
        install(new HttpsClientSslCertModule());
        break;

      case LDAP:
      case LDAP_BIND:
        install(new LdapAuthModule());
        break;

      case DEVELOPMENT_BECOME_ANY_ACCOUNT:
        install(new BecomeAnyAccountModule());
        break;

      case OAUTH:
        // OAuth support is bound in WebAppInitializer and Daemon.
      case OPENID:
      case OPENID_SSO:
        // OpenID support is bound in WebAppInitializer and Daemon.
      case CUSTOM_EXTENSION:
        break;
      default:
        throw new ProvisionException("Unsupported loginType: " + authConfig.getAuthType());
    }
  }
}
