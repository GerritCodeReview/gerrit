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

import static com.google.common.base.MoreObjects.firstNonNull;

import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import org.apache.http.HttpHost;
import org.eclipse.jgit.lib.Config;

@Singleton
class ElasticConfiguration {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  static final String SECTION_ELASTICSEARCH = "elasticsearch";
  static final String KEY_PASSWORD = "password";
  static final String KEY_USERNAME = "username";
  static final String KEY_PREFIX = "prefix";
  static final String KEY_SERVER = "server";
  static final String KEY_NUMBER_OF_SHARDS = "numberOfShards";
  static final String KEY_NUMBER_OF_REPLICAS = "numberOfReplicas";
  static final String DEFAULT_PORT = "9200";
  static final String DEFAULT_USERNAME = "elastic";
  static final int DEFAULT_NUMBER_OF_SHARDS = 5;
  static final int DEFAULT_NUMBER_OF_REPLICAS = 1;

  private final Config cfg;
  private final List<HttpHost> hosts;

  final String username;
  final String password;
  final int numberOfShards;
  final int numberOfReplicas;
  final String prefix;

  @Inject
  ElasticConfiguration(@GerritServerConfig Config cfg) {
    this.cfg = cfg;
    this.password = cfg.getString(SECTION_ELASTICSEARCH, null, KEY_PASSWORD);
    this.username =
        password == null
            ? null
            : firstNonNull(
                cfg.getString(SECTION_ELASTICSEARCH, null, KEY_USERNAME), DEFAULT_USERNAME);
    this.prefix = Strings.nullToEmpty(cfg.getString(SECTION_ELASTICSEARCH, null, KEY_PREFIX));
    this.numberOfShards =
        cfg.getInt(SECTION_ELASTICSEARCH, null, KEY_NUMBER_OF_SHARDS, DEFAULT_NUMBER_OF_SHARDS);
    this.numberOfReplicas =
        cfg.getInt(SECTION_ELASTICSEARCH, null, KEY_NUMBER_OF_REPLICAS, DEFAULT_NUMBER_OF_REPLICAS);
    this.hosts = new ArrayList<>();
    for (String server : cfg.getStringList(SECTION_ELASTICSEARCH, null, KEY_SERVER)) {
      try {
        URI uri = new URI(server);
        int port = uri.getPort();
        HttpHost httpHost =
            new HttpHost(
                uri.getHost(), port == -1 ? Integer.valueOf(DEFAULT_PORT) : port, uri.getScheme());
        this.hosts.add(httpHost);
      } catch (URISyntaxException | IllegalArgumentException e) {
        logger.atSevere().log("Invalid server URI %s: %s", server, e.getMessage());
      }
    }

    if (hosts.isEmpty()) {
      throw new ProvisionException("No valid Elasticsearch servers configured");
    }

    logger.atInfo().log("Elasticsearch servers: %s", hosts);
  }

  Config getConfig() {
    return cfg;
  }

  HttpHost[] getHosts() {
    return hosts.toArray(new HttpHost[hosts.size()]);
  }

  String getIndexName(String name, int schemaVersion) {
    return String.format("%s%s_%04d", prefix, name, schemaVersion);
  }
}
