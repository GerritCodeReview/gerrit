/*
 * Copyright 2017 CollabNet, Inc. All rights reserved.
 * http://www.collab.net
 */

package com.google.gerrit.elasticsearch;

import com.google.common.base.MoreObjects;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.client.http.JestHttpClient;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.Config;

@Singleton
class JestClientBuilder {

  private final String url;

  final boolean refresh;

  @Inject
  JestClientBuilder(@GerritServerConfig Config cfg) {
    String protocol = MoreObjects.firstNonNull(cfg.getString("index", null, "protocol"), "http");
    String hostname =
        MoreObjects.firstNonNull(cfg.getString("index", null, "hostname"), "localhost");
    String port = String.valueOf(cfg.getInt("index", null, "port", 9200));

    // By default Elasticsearch has a 1s delay before changes are available in
    // the index.  Setting refresh(true) on calls to the index makes the index
    // refresh immediately.
    //
    // Discovery should be disabled during test mode to prevent spurious
    // connection failures caused by the client starting up and being ready
    // before the test node.
    //
    // This setting should only be set to true during testing, and is not
    // documented.
    this.refresh = cfg.getBoolean("index", "elasticsearch", "test", false);

    this.url = buildUrl(protocol, hostname, port);
  }

  JestHttpClient build() {
    JestClientFactory factory = new JestClientFactory();
    factory.setHttpClientConfig(
        new HttpClientConfig.Builder(url)
            .multiThreaded(true)
            .discoveryEnabled(!refresh)
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
