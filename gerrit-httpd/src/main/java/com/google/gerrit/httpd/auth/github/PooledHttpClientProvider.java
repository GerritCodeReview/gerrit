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

import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.apache.http.HttpHost;
import org.apache.http.client.HttpClient;
import org.apache.http.conn.routing.HttpRoute;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.ssl.SSLSocketFactory;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.impl.conn.PoolingClientConnectionManager;

@Singleton
public class PooledHttpClientProvider implements Provider<HttpClient> {
  private static final int MAX_TOTAL_CONN = 1024;
  private static final int MAX_CONN_PER_ROUTE = 10;
  private static final int MAX_LOCALHOST_CONN = 512;
  private final PoolingClientConnectionManager connectionManager;

  public PooledHttpClientProvider() {
    SchemeRegistry schemeRegistry = new SchemeRegistry();
    schemeRegistry.register(new Scheme("http", 80, PlainSocketFactory
        .getSocketFactory()));
    schemeRegistry.register(new Scheme("https", 443, SSLSocketFactory
        .getSocketFactory()));

    connectionManager = new PoolingClientConnectionManager(schemeRegistry);
    connectionManager.setMaxTotal(MAX_TOTAL_CONN);
    connectionManager.setDefaultMaxPerRoute(MAX_CONN_PER_ROUTE);
    HttpHost localhost = new HttpHost("locahost", 80);
    connectionManager.setMaxPerRoute(new HttpRoute(localhost),
        MAX_LOCALHOST_CONN);
  }

  @Override
  public HttpClient get() {
    return new DefaultHttpClient(connectionManager);
  }
}
