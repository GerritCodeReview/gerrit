// Copyright (C) 2017 The Android Open Source Project
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

import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.eclipse.jgit.lib.Config;

import com.google.common.base.MoreObjects;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.client.http.JestHttpClient;

@Singleton
class JestClientBuilder {
  private final List<String> urls;

  @Inject
  JestClientBuilder(@GerritServerConfig Config cfg) {
    String protocol = MoreObjects.firstNonNull(cfg.getString("index", null, "protocol"), "http");
    String[] hostnames =
        MoreObjects.firstNonNull(
            cfg.getStringList("index", null, "hostname"), new String[] {"localhost"});
    String port = String.valueOf(cfg.getInt("index", null, "port", 9200));

    this.urls = new ArrayList<>(hostnames.length);
    for (String hostname : hostnames) {
      this.urls.add(buildUrl(protocol, hostname, port));
    }
  }

  JestHttpClient build() {
    JestClientFactory factory = new JestClientFactory();
    factory.setHttpClientConfig(
        new HttpClientConfig.Builder(urls)
            .multiThreaded(true)
            .discoveryEnabled(false)
            .discoveryFrequency(1L, TimeUnit.MINUTES)
            .build());
    return (JestHttpClient) factory.getObject();
  }

  private String buildUrl(String protocol, String hostname, String port) {
    try {
      return new URL(protocol, hostname, Integer.parseInt(port), "").toString();
    } catch (MalformedURLException | NumberFormatException e) {
      throw new RuntimeException(
          "Cannot build url to Elasticsearch from values: protocol="
              + protocol
              + " hostname="
              + hostname
              + " port="
              + port,
          e);
    }
  }
}
