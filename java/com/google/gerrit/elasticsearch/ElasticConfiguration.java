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
import com.google.gerrit.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

@Singleton
class ElasticConfiguration {
  private static final String DEFAULT_HOST = "localhost";
  private static final String DEFAULT_PORT = "9200";
  private static final String DEFAULT_PROTOCOL = "http";

  final List<String> urls;
  final String username;
  final String password;
  final boolean requestCompression;
  final long connectionTimeout;
  final long maxConnectionIdleTime;
  final TimeUnit maxConnectionIdleUnit = TimeUnit.MILLISECONDS;
  final int maxTotalConnection;
  final int readTimeout;

  @Inject
  ElasticConfiguration(@GerritServerConfig Config cfg) {
    this.username = cfg.getString("elasticsearch", null, "username");
    this.password = cfg.getString("elasticsearch", null, "password");
    this.requestCompression = cfg.getBoolean("elasticsearch", null, "requestCompression", false);
    this.connectionTimeout =
        cfg.getTimeUnit("elasticsearch", null, "connectionTimeout", 3000, TimeUnit.MILLISECONDS);
    this.maxConnectionIdleTime =
        cfg.getTimeUnit(
            "elasticsearch", null, "maxConnectionIdleTime", 3000, TimeUnit.MILLISECONDS);
    this.maxTotalConnection = cfg.getInt("elasticsearch", null, "maxTotalConnection", 1);
    this.readTimeout =
        (int) cfg.getTimeUnit("elasticsearch", null, "readTimeout", 3000, TimeUnit.MICROSECONDS);

    Set<String> subsections = cfg.getSubsections("elasticsearch");
    if (subsections.isEmpty()) {
      this.urls = Arrays.asList(buildUrl(DEFAULT_PROTOCOL, DEFAULT_HOST, DEFAULT_PORT));
    } else {
      this.urls = new ArrayList<>(subsections.size());
      for (String subsection : subsections) {
        String port = getString(cfg, subsection, "port", DEFAULT_PORT);
        String host = getString(cfg, subsection, "hostname", DEFAULT_HOST);
        String protocol = getString(cfg, subsection, "protocol", DEFAULT_PROTOCOL);
        this.urls.add(buildUrl(protocol, host, port));
      }
    }
  }

  private String getString(Config cfg, String subsection, String name, String defaultValue) {
    return MoreObjects.firstNonNull(cfg.getString("elasticsearch", subsection, name), defaultValue);
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
