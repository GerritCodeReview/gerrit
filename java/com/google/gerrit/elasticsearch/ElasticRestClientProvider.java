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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;

@Singleton
class ElasticRestClientProvider implements Provider<RestClient>, LifecycleListener {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ElasticConfiguration cfg;

  private volatile RestClient client;
  private ElasticQueryAdapter adapter;

  @Inject
  ElasticRestClientProvider(ElasticConfiguration cfg) {
    this.cfg = cfg;
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
          ElasticVersion version = getVersion();
          logger.atInfo().log("Elasticsearch integration version %s", version);
          adapter = new ElasticQueryAdapter(version);
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

  ElasticQueryAdapter adapter() {
    get(); // Make sure we're connected
    return adapter;
  }

  public static class FailedToGetVersion extends ElasticException {
    private static final long serialVersionUID = 1L;
    private static final String MESSAGE = "Failed to get Elasticsearch version";

    FailedToGetVersion(StatusLine status) {
      super(String.format("%s: %d %s", MESSAGE, status.getStatusCode(), status.getReasonPhrase()));
    }

    FailedToGetVersion(Throwable cause) {
      super(MESSAGE, cause);
    }
  }

  private ElasticVersion getVersion() throws ElasticException {
    try {
      Response response = client.performRequest(new Request("GET", "/"));
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
        throw new FailedToGetVersion(statusLine);
      }
      String version =
          new JsonParser()
              .parse(AbstractElasticIndex.getContent(response))
              .getAsJsonObject()
              .get("version")
              .getAsJsonObject()
              .get("number")
              .getAsString();
      logger.atInfo().log("Connected to Elasticsearch version %s", version);
      return ElasticVersion.forVersion(version);
    } catch (IOException e) {
      throw new FailedToGetVersion(e);
    }
  }

  private RestClient build() {
    RestClientBuilder builder = RestClient.builder(cfg.getHosts());
    setConfiguredCredentialsIfAny(builder);
    return builder.build();
  }

  private void setConfiguredCredentialsIfAny(RestClientBuilder builder) {
    String username = cfg.username;
    String password = cfg.password;
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
