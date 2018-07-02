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
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class ElasticConfiguration {
  private static final Logger log = LoggerFactory.getLogger(ElasticConfiguration.class);

  private static final String DEFAULT_PORT = "9200";
  private static final String DEFAULT_USERNAME = "elastic";

  private final Config cfg;
  private final List<HttpHost> hosts;

  final String username;
  final String password;
  final int maxRetryTimeout;
  final String prefix;

  @Inject
  ElasticConfiguration(@GerritServerConfig Config cfg) {
    this.cfg = cfg;
    this.password = cfg.getString("elasticsearch", null, "password");
    this.username =
        password == null
            ? null
            : firstNonNull(cfg.getString("elasticsearch", null, "username"), DEFAULT_USERNAME);
    this.maxRetryTimeout =
        (int)
            cfg.getTimeUnit("elasticsearch", null, "maxRetryTimeout", 30000, TimeUnit.MILLISECONDS);
    this.prefix = Strings.nullToEmpty(cfg.getString("elasticsearch", null, "prefix"));
    this.hosts = new ArrayList<>();
    for (String server : cfg.getStringList("elasticsearch", null, "server")) {
      try {
        URI uri = new URI(server);
        int port = uri.getPort();
        HttpHost httpHost =
            new HttpHost(
                uri.getHost(), port == -1 ? Integer.valueOf(DEFAULT_PORT) : port, uri.getScheme());
        this.hosts.add(httpHost);
      } catch (URISyntaxException | IllegalArgumentException e) {
        log.error("Invalid server URI {}: {}", server, e.getMessage());
      }
    }

    if (hosts.isEmpty()) {
      throw new ProvisionException("No valid Elasticsearch servers configured");
    }

    log.info("Elasticsearch servers: {}", hosts);
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
