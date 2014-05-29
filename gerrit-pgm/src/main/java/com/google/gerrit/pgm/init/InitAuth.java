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

package com.google.gerrit.pgm.init;

import static com.google.gerrit.pgm.init.api.InitUtil.dnOf;

import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.InitUtil;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.reviewdb.client.AuthType;
import com.google.gwtjsonrpc.server.SignedToken;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.net.URISyntaxException;

/** Initialize the {@code auth} configuration section. */
@Singleton
class InitAuth implements InitStep {
  private static final String GITHUB_URL = "https://github.com";
  private static final String GITHUB_API_URL = "https://api.github.com";
  private static final String GITHUB_REGISTER_APPLICATION_PATH = "/settings/applications/new";
  private static final String GERRIT_OAUTH_CALLBACK_PATH = "oauth";

  private final ConsoleUI ui;
  private final Section auth;
  private final Section ldap;
  private final Section github;
  private final Section gerrit;
  private final Section httpd;

  @Inject
  InitAuth(final ConsoleUI ui, final Section.Factory sections) {
    this.ui = ui;
    this.auth = sections.get("auth", null);
    this.ldap = sections.get("ldap", null);
    this.github = sections.get("github", null);
    this.gerrit = sections.get("gerrit", null);
    this.httpd = sections.get("httpd", null);
  }

  @Override
  public void run() {
    ui.header("User Authentication");

    final AuthType auth_type =
        auth.select("Authentication method", "type", AuthType.OPENID);

    switch (auth_type) {
      case HTTP:
      case HTTP_LDAP: {
        String hdr = auth.get("httpHeader");
        if (ui.yesno(hdr != null, "Get username from custom HTTP header")) {
          auth.string("Username HTTP header", "httpHeader", "SM_USER");
        } else if (hdr != null) {
          auth.unset("httpHeader");
        }
        auth.string("SSO logout URL", "logoutUrl", null);
        break;
      }

      case OAUTH_GITHUB:
        auth.set("httpHeader", "GITHUB_USER");
        break;

      case CLIENT_SSL_CERT_LDAP:
      case CUSTOM_EXTENSION:
      case DEVELOPMENT_BECOME_ANY_ACCOUNT:
      case LDAP:
      case LDAP_BIND:
      case OPENID:
      case OPENID_SSO:
        break;
    }

    switch (auth_type) {
      case LDAP:
      case LDAP_BIND:
      case HTTP_LDAP: {
        String server =
            ldap.string("LDAP server", "server", "ldap://localhost");
        if (server != null //
            && !server.startsWith("ldap://") //
            && !server.startsWith("ldaps://")) {
          if (ui.yesno(false, "Use SSL")) {
            server = "ldaps://" + server;
          } else {
            server = "ldap://" + server;
          }
          ldap.set("server", server);
        }

        ldap.string("LDAP username", "username", null);
        ldap.password("username", "password");

        String aBase = ldap.string("Account BaseDN", "accountBase", dnOf(server));
        ldap.string("Group BaseDN", "groupBase", aBase);
        break;
      }

      case OAUTH_GITHUB:
        auth.set("httpHeader", "GITHUB_USER");
        auth.set("httpExternalIdHeader", "GITHUB_OAUTH_TOKEN");
        auth.set("loginUrl","/login");
        auth.set("loginText", "Sign-in with GitHub");
        auth.set("registerPageUrl", "/#/register");

        github.string("GitHub URL", "url", GITHUB_URL);
        github.string("GitHub API URL", "apiUrl", GITHUB_API_URL);
        ui.message("\nNOTE: You might need to configure a proxy using http.proxy"
            + " if you run Gerrit behind a firewall.\n");

        String gerritUrl = getAssumedCanonicalWebUrl();
        ui.header("GitHub OAuth registration and credentials");
        ui.message(
            "Register Gerrit as GitHub application on:\n" +
            "%s%s\n\n",
            github.get("url"), GITHUB_REGISTER_APPLICATION_PATH);
        ui.message("Settings (assumed Gerrit URL: %s)\n", gerritUrl);
        ui.message("* Application name: Gerrit Code Review\n");
        ui.message("* Homepage URL: %s\n", gerritUrl);
        ui.message("* Authorization callback URL: %s%s\n\n", gerritUrl, GERRIT_OAUTH_CALLBACK_PATH);
        ui.message("After registration is complete, enter the generated OAuth credentials:\n");

        github.string("GitHub Client ID", "clientId", null);
        github.passwordForKey("GitHub Client Secret", "clientSecret");
        break;

      case CLIENT_SSL_CERT_LDAP:
      case CUSTOM_EXTENSION:
      case DEVELOPMENT_BECOME_ANY_ACCOUNT:
      case HTTP:
      case OPENID:
      case OPENID_SSO:
        break;
    }

    if (auth.getSecure("registerEmailPrivateKey") == null) {
      auth.setSecure("registerEmailPrivateKey", SignedToken.generateRandomKey());
    }

    if (auth.getSecure("restTokenPrivateKey") == null) {
      auth.setSecure("restTokenPrivateKey", SignedToken.generateRandomKey());
    }
  }

  private String getAssumedCanonicalWebUrl() {
    String url = gerrit.get("canonicalWebUrl");
    if (url != null) {
      return url;
    }

    String httpListen = httpd.get("listenUrl");
    if (httpListen != null) {
      try {
        return InitUtil.toURI(httpListen).toString();
      } catch (URISyntaxException e) {
      }
    }

    return String.format("http://%s:8080/", InitUtil.hostname());
  }

  @Override
  public void postRun() throws Exception {
  }
}
