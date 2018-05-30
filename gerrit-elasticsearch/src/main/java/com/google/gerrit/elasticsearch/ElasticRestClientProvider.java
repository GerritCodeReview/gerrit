// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.elasticsearch;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.apache.http.HttpHost;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

@Singleton
class ElasticRestClientProvider implements Provider<RestClient>, LifecycleListener {

  private final HttpHost[] hosts;
  private final String username;
  private final String password;

  private RestClient client;

  @Inject
  ElasticRestClientProvider(ElasticConfiguration cfg) {
    hosts = cfg.urls.toArray(new HttpHost[cfg.urls.size()]);
    username = cfg.username;
    password = cfg.password;
  }

  public static LifecycleModule module() {
    return new LifecycleModule() {
      @Override
      protected void configure() {
        listener().to(ElasticRestClientProvider.class);
      }
    };
  }

  @Override
  public RestClient get() {
    if (client == null) {
      synchronized (this) {
        if (client == null) {
          client = build();
        }
      }
    }
    return client;
  }

  @Override
  public void start() {}

  @Override
  public void stop() {
    if (client != null) {
      try {
        client.close();
      } catch (IOException e) {
        // Ignore. We can't do anything about it.
      }
    }
  }

  private RestClient build() {
    RestClientBuilder builder = RestClient.builder(hosts);
    setConfiguredCredentialsIfAny(builder);
    return builder.build();
  }

  private void setConfiguredCredentialsIfAny(RestClientBuilder builder) {
    if (username != null && password != null) {
      CredentialsProvider credentialsProvider = new BasicCredentialsProvider();
      credentialsProvider.setCredentials(
          AuthScope.ANY, new UsernamePasswordCredentials(username, password));
      builder.setHttpClientConfigCallback(
          (HttpAsyncClientBuilder httpClientBuilder) ->
              httpClientBuilder.setDefaultCredentialsProvider(credentialsProvider));
    }
  }
}
