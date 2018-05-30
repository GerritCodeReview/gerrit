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

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.io.IOException;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthScope;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.apache.http.client.CredentialsProvider;
import org.apache.http.impl.client.BasicCredentialsProvider;
import org.apache.http.impl.nio.client.HttpAsyncClientBuilder;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.elasticsearch.client.RestClientBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class ElasticRestClientProvider implements Provider<RestClient> {
  private static final Logger log = LoggerFactory.getLogger(ElasticRestClientProvider.class);

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

  @Override
  public RestClient get() {
    if (client == null) {
      synchronized (this) {
        if (client == null) {
          client = build();
          try {
            Response response = client.performRequest("GET", "");
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode != HttpStatus.SC_OK) {
              throw new ProvisionException(
                  String.format(
                      "Failed to get Elasticsearch version: %d %s",
                      statusCode, response.getStatusLine().getReasonPhrase()));
            }
            String content = AbstractElasticIndex.getContent(response);
            JsonObject object = new JsonParser().parse(content).getAsJsonObject();
            String version = object.get("version").getAsJsonObject().get("number").getAsString();
            log.info("Connected to Elasticsearch version {}", version);
          } catch (IOException e) {
            throw new ProvisionException("Failed to get Elasticsearch version", e);
          }
        }
      }
    }
    return client;
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
