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

package com.google.gerrit.httpd;

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

import java.net.URL;

@Singleton
public class PooledHttpClientProvider implements Provider<HttpClient> {
  private final int maxTotalConnection;
  private final int maxConnectionPerRoute;
  private final ProxyProperties proxy;

  @Inject
  PooledHttpClientProvider(@GerritServerConfig Config config,
      ProxyProperties proxyProperties) {
    this.proxy = proxyProperties;
    maxConnectionPerRoute = config.getInt("http", null,
        "pooledMaxConnectionsPerRoute", 512);
    maxTotalConnection = config.getInt("http", null,
        "pooledMaxTotalConnections", 1024);
  }

  @Override
  public HttpClient get() {
    HttpClientBuilder builder = HttpClientBuilder
        .create()
        .setMaxConnPerRoute(maxConnectionPerRoute)
        .setMaxConnTotal(maxTotalConnection);

    if (proxy.getProxyUrl() != null) {
      URL url = proxy.getProxyUrl();
      builder.setProxy(new HttpHost(url.getHost(), url.getPort()));
      if (!Strings.isNullOrEmpty(proxy.getUsername())
          && !Strings.isNullOrEmpty(proxy.getPassword())) {
        UsernamePasswordCredentials creds = new UsernamePasswordCredentials(
            proxy.getUsername(), proxy.getPassword());
        CredentialsProvider credsProvider = new BasicCredentialsProvider();
        credsProvider.setCredentials(new AuthScope(url.getHost(),
            url.getPort()), creds);
        builder.setDefaultCredentialsProvider(credsProvider);
        builder.setProxyAuthenticationStrategy(
            new ProxyAuthenticationStrategy());
      }
    }

    return builder.build();
  }
}
