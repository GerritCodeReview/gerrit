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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.apache.http.HttpHost;
import org.eclipse.jgit.lib.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class ElasticConfiguration {
  private static final Logger log = LoggerFactory.getLogger(ElasticConfiguration.class);

  private static final String DEFAULT_HOST = "localhost";
  private static final String DEFAULT_PORT = "9200";
  private static final String DEFAULT_PROTOCOL = "http";

  private final Config cfg;
  private final List<HttpHost> hosts;

  final String username;
  final String password;
  final int maxRetryTimeout;
  final String prefix;

  @Inject
  ElasticConfiguration(@GerritServerConfig Config cfg) {
    this.cfg = cfg;
    this.username = cfg.getString("elasticsearch", null, "username");
    this.password = cfg.getString("elasticsearch", null, "password");
    this.maxRetryTimeout =
        (int)
            cfg.getTimeUnit("elasticsearch", null, "maxRetryTimeout", 30000, TimeUnit.MILLISECONDS);
    this.prefix = Strings.nullToEmpty(cfg.getString("elasticsearch", null, "prefix"));

    Set<String> subsections = cfg.getSubsections("elasticsearch");
    if (subsections.isEmpty()) {
      HttpHost httpHost =
          new HttpHost(DEFAULT_HOST, Integer.valueOf(DEFAULT_PORT), DEFAULT_PROTOCOL);
      this.hosts = Collections.singletonList(httpHost);
    } else {
      this.hosts = new ArrayList<>(subsections.size());
      for (String subsection : subsections) {
        String port = getString(cfg, subsection, "port", DEFAULT_PORT);
        String host = getString(cfg, subsection, "hostname", DEFAULT_HOST);
        String protocol = getString(cfg, subsection, "protocol", DEFAULT_PROTOCOL);

        HttpHost httpHost = new HttpHost(host, Integer.valueOf(port), protocol);
        this.hosts.add(httpHost);
      }
    }

    log.info("Elasticsearch hosts: {}", hosts);
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

  private String getString(Config cfg, String subsection, String name, String defaultValue) {
    return MoreObjects.firstNonNull(cfg.getString("elasticsearch", subsection, name), defaultValue);
  }
}
