// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.httpd.auth.saml;

import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.servlet.ServletModule;

import org.pac4j.saml.client.SAML2Client;
import org.pac4j.saml.client.SAML2ClientConfiguration;

/** Servlets and support related to OAuth authentication. */
public class SamlModule extends ServletModule {
  @Override
  protected void configureServlets() {
    filter("/login", "/login/*", "/saml").through(SamlWebFilter.class);
    // This is needed to invalidate SAML session during logout
    serve("/logout").with(SamlLogoutServlet.class);
  }

  @Provides
  @Singleton
  SAML2Client clientFromConfig(SamlConfig samlConfig,
      @CanonicalWebUrl String canonicalWebUrl) {
    SAML2Client client =
        new SAML2Client(new SAML2ClientConfiguration(
            samlConfig.getKeystorePath(), samlConfig.getKeystorePassword(),
            samlConfig.getPrivateKeyPassword(), samlConfig.getMetadataPath()));
    client.setCallbackUrl(canonicalWebUrl + "saml");
    return client;
  }
}
