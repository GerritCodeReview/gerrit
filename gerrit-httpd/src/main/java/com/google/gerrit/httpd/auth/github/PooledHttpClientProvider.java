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

import com.google.common.base.Strings;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.client.HttpClient;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.impl.client.ProxyAuthenticationStrategy;
import org.eclipse.jgit.lib.Config;

import java.net.MalformedURLException;
import java.net.URL;

@Singleton
class PooledHttpClientProvider implements Provider<HttpClient> {
  private final int maxTotalConnection;
  private final int maxConnectionPerRoute;
  private URL proxyUrl;
  private String proxyUser;
  private String proxyPassword;

  @Inject
  PooledHttpClientProvider(@GerritServerConfig Config config)
      throws MalformedURLException {
    maxConnectionPerRoute = config.getInt("http", null,
        "pooledMaxConnectionPerRoute", 512);
    maxTotalConnection = config.getInt("http", null,
        "pooledMaxTotalConnection", 1024);

    // TODO(davido): The code below is borrowed from OpenIdServiceImpl.
    // Move it in some common place and reuse.
    if (config.getString("http", null, "proxy") != null) {
      proxyUrl = new URL(config.getString("http", null, "proxy"));
      proxyUser = config.getString("http", null, "proxyUsername");
      proxyPassword = config.getString("http", null, "proxyPassword");
      String userInfo = proxyUrl.getUserInfo();
      if (userInfo != null) {
        int c = userInfo.indexOf(':');
        if (0 < c) {
          proxyUser = userInfo.substring(0, c);
          proxyPassword = userInfo.substring(c + 1);
        } else {
          proxyUser = userInfo;
        }
      }
    }
  }

  @Override
  public HttpClient get() {
    HttpClientBuilder builder = HttpClientBuilder
        .create()
        .setMaxConnPerRoute(maxConnectionPerRoute)
        .setMaxConnTotal(maxTotalConnection);

    if (proxyUrl != null) {
      builder.setProxy(new HttpHost(proxyUrl.getHost(), proxyUrl.getPort()));
      if (!Strings.isNullOrEmpty(proxyUser)
          && !Strings.isNullOrEmpty(proxyPassword)) {
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(
            proxyUser, proxyPassword);
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(proxyUrl.getHost(),
            proxyUrl.getPort()), creds);
        builder.setDefaultCredentialsProvider(credsProvider);
        builder.setProxyAuthenticationStrategy(
            new ProxyAuthenticationStrategy());
      }
    }

    return builder.build();
  }
}
