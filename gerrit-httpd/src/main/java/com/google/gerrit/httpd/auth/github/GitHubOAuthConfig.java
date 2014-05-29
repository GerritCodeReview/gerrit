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

import com.google.common.base.CharMatcher;
import com.google.common.base.MoreObjects;
import com.google.common.base.Preconditions;
import com.google.common.base.Strings;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

import java.net.MalformedURLException;
import java.net.URL;

@Singleton
class GitHubOAuthConfig {
  protected static final String CONF_SECTION = "github";
  private static final String GITHUB_OAUTH_AUTHORIZE = "/login/oauth/authorize";
  public static final String GITHUB_OAUTH_ACCESS_TOKEN =
      "/login/oauth/access_token";
  private static final String GITHUB_GET_USER = "/user";
  private static final String GERRIT_OAUTH_FINAL = "/oauth";
  private static final String GITHUB_URL_DEFAULT = "https://github.com";
  private static final String GITHUB_API_URL_DEFAULT = "https://api.github.com";
  static final String GERRIT_LOGIN = "/login";

  private final String gitHubUrl;
  private final String gitHubApiUrl;
  final String gitHubUserUrl;
  final String gitHubClientId;
  final String gitHubClientSecret;
  final String httpHeader;
  final String gitHubOAuthUrl;
  final String oAuthFinalRedirectUrl;
  final String gitHubOAuthAccessTokenUrl;
  final boolean autoLogin;

  @Inject
  GitHubOAuthConfig(@GerritServerConfig Config config, AuthConfig authConfig)
      throws MalformedURLException {
    httpHeader =
        Preconditions.checkNotNull(
            config.getString("auth", null, "httpHeader"),
            "HTTP Header for GitHub user must be provided");
    gitHubUrl =
        trimTrailingSlash(MoreObjects.firstNonNull(
            config.getString(CONF_SECTION, null, "url"), GITHUB_URL_DEFAULT));
    gitHubApiUrl =
        trimTrailingSlash(MoreObjects.firstNonNull(
            config.getString(CONF_SECTION, null, "apiUrl"),
            GITHUB_API_URL_DEFAULT));
    gitHubClientId = Preconditions.checkNotNull(
        config.getString(CONF_SECTION, null, "clientId"),
        "GitHub `clientId` must be provided");
    gitHubClientSecret = Preconditions.checkNotNull(
        config.getString(CONF_SECTION, null, "clientSecret"),
        "GitHub `clientSecret` must be provided");

    gitHubOAuthUrl = getUrl(gitHubUrl, GITHUB_OAUTH_AUTHORIZE);
    gitHubOAuthAccessTokenUrl = getUrl(gitHubUrl, GITHUB_OAUTH_ACCESS_TOKEN);
    gitHubUserUrl = getUrl(gitHubApiUrl, GITHUB_GET_USER);
    oAuthFinalRedirectUrl =
        getUrl(config.getString("gerrit", null, "canonicalWebUrl"),
            GERRIT_OAUTH_FINAL);
    autoLogin = Strings.isNullOrEmpty(authConfig.getLoginUrl());
  }

  private static String trimTrailingSlash(String url) {
    return CharMatcher.is('/').trimTrailingFrom(url);
  }

  private String getUrl(String baseUrl, String path)
      throws MalformedURLException {
    if (baseUrl.indexOf("://") > 0) {
      return new URL(new URL(baseUrl), path).toExternalForm();
    } else {
      return baseUrl + trimTrailingSlash(baseUrl) + "/"
          + CharMatcher.is('/').trimLeadingFrom(path);
    }
  }
}
