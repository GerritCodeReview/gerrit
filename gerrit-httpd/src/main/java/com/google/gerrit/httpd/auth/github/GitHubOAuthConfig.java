// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.httpd.auth.github;

import com.google.common.base.Objects;
import com.google.common.base.Strings;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.net.MalformedURLException;
import java.net.URI;
import java.net.URL;

@Singleton
public class GitHubOAuthConfig {
  protected static final String CONF_SECTION = "github";
  private static final String GITHUB_OAUTH_AUTHORIZE = "/login/oauth/authorize";
  public static final String GITHUB_OAUTH_ACCESS_TOKEN =
      "/login/oauth/access_token";
  private static final String GITHUB_GET_USER = "/user";
  private static final String GITHUB_URL = "https://github.com";
  private static final String GITHUB_API_URL = "https://api.github.com";
  public static final String GERRIT_OAUTH_FINAL = "/oauth";
  public static final String GERRIT_LOGIN = "/login";

  public final String gitHubUrl;
  public final String gitHubApiUrl;
  public final String gitHubUserUrl;
  public final String gitHubClientId;
  public final String gitHubClientSecret;
  public final String httpHeader;
  public final String gitHubOAuthUrl;
  public final String oAuthFinalRedirectUrl;
  public final String gitHubOAuthAccessTokenUrl;
  public final Config gerritConfig;
  public final boolean autoLogin;

  @Inject
  public GitHubOAuthConfig(@GerritServerConfig Config config, AuthConfig authConfig)
      throws MalformedURLException {
    this.gerritConfig = config;

    httpHeader = config.getString("auth", null, "httpHeader");
    gitHubUrl =
        dropTrailingSlash(Objects.firstNonNull(
            config.getString(CONF_SECTION, null, "url"), GITHUB_URL));
    gitHubApiUrl =
        dropTrailingSlash(Objects.firstNonNull(
            config.getString(CONF_SECTION, null, "apiUrl"), GITHUB_API_URL));
    gitHubClientId = config.getString(CONF_SECTION, null, "clientId");
    gitHubClientSecret = config.getString(CONF_SECTION, null, "clientSecret");
    gitHubOAuthUrl = getUrl(gitHubUrl, GITHUB_OAUTH_AUTHORIZE);
    gitHubOAuthAccessTokenUrl = getUrl(gitHubUrl, GITHUB_OAUTH_ACCESS_TOKEN);
    gitHubUserUrl = getUrl(gitHubApiUrl, GITHUB_GET_USER);
    oAuthFinalRedirectUrl =
        getUrl(config.getString("gerrit", null, "canonicalWebUrl"), GERRIT_OAUTH_FINAL);
    autoLogin = Strings.isNullOrEmpty(authConfig.getLoginUrl());
  }

  private String dropTrailingSlash(String url) {
    return (url.endsWith("/") ? url.substring(0, url.length() - 1) : url);
  }

  public String getUrl(String baseUrl, String path)
      throws MalformedURLException {
    if (baseUrl.indexOf("://") > 0) {
      return new URL(new URL(baseUrl), path).toExternalForm();
    } else {
      return baseUrl + (baseUrl.endsWith("/") ? "" : "/")
          + (path.startsWith("/") ? path.substring(1) : path);
    }
  }
}
