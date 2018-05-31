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

import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.apache.http.HttpHost;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
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

  enum SupportedVersion {
    V2("2.4.6"),
    V5("5.6.9");

    private final String version;

    SupportedVersion(String version) {
      this.version = version;
    }

    String getVersion() {
      return version;
    }

    private static boolean isSupported(String version) {
      for (SupportedVersion supported : SupportedVersion.values()) {
        if (version.equals(supported.version)) {
          return true;
        }
      }
      return false;
    }
  }

  private final HttpHost[] hosts;
  private final String username;
  private final String password;

  private RestClient client;
  private String version = "";

  @Inject
  ElasticRestClientProvider(ElasticConfiguration cfg) {
    hosts = cfg.urls.toArray(new HttpHost[cfg.urls.size()]);
    username = cfg.username;
    password = cfg.password;
  }

  @Override
  public RestClient get() {
    initGetVersion();
    return client;
  }

  String initGetVersion() {
    if (client == null) {
      synchronized (this) {
        if (client == null) {
          client = build();
          version = getVersion();
          if (!SupportedVersion.isSupported(version)) {
            throw new ElasticRestClientException(version);
          }
          log.info("Connected to Elasticsearch version {}", version);
        }
      }
    }
    return version;
  }

  public class ElasticRestClientException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    private static final String MESSAGE = "Failed to get Elasticsearch version";
    private static final String UNSUPPORTED = "Unsupported Elasticsearch version";

    ElasticRestClientException(StatusLine status) {
      super(String.format("%s: %d %s", MESSAGE, status.getStatusCode(), status.getReasonPhrase()));
    }

    ElasticRestClientException(String version) {
      super(String.format("%s %s; supported: %s", UNSUPPORTED, version, getSupportedVersions()));
    }

    ElasticRestClientException(Throwable cause) {
      super(MESSAGE, cause);
    }
  }

  private String getVersion() throws ElasticRestClientException {
    try {
      Response response = client.performRequest("GET", "");
      StatusLine statusLine = response.getStatusLine();
      if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
        throw new ElasticRestClientException(statusLine);
      }
      return new JsonParser()
          .parse(AbstractElasticIndex.getContent(response))
          .getAsJsonObject()
          .get("version")
          .getAsJsonObject()
          .get("number")
          .getAsString();
    } catch (IOException e) {
      throw new ElasticRestClientException(e);
    }
  }

  private RestClient build() {
    RestClientBuilder builder = RestClient.builder(hosts);
    setConfiguredCredentialsIfAny(builder);
    return builder.build();
  }

  private StringBuilder getSupportedVersions() {
    StringBuilder versions = new StringBuilder();
    for (SupportedVersion supported : SupportedVersion.values()) {
      versions.append(supported.version).append(" ");
    }
    return versions;
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
