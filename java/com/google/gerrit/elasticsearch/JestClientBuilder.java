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

import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.searchbox.client.JestClientFactory;
import io.searchbox.client.config.HttpClientConfig;
import io.searchbox.client.config.HttpClientConfig.Builder;
import io.searchbox.client.http.JestHttpClient;
import java.util.concurrent.TimeUnit;

@Singleton
class JestClientBuilder {
  private final ElasticConfiguration cfg;

  @Inject
  JestClientBuilder(ElasticConfiguration cfg) {
    this.cfg = cfg;
  }

  JestHttpClient build() {
    JestClientFactory factory = new JestClientFactory();
    Builder builder =
        new HttpClientConfig.Builder(cfg.urls)
            .multiThreaded(true)
            .discoveryEnabled(false)
            .connTimeout((int) cfg.connectionTimeout)
            .maxConnectionIdleTime(cfg.maxConnectionIdleTime, cfg.maxConnectionIdleUnit)
            .maxTotalConnection(cfg.maxTotalConnection)
            .readTimeout(cfg.readTimeout)
            .requestCompressionEnabled(cfg.requestCompression)
            .discoveryFrequency(1L, TimeUnit.MINUTES);

    if (cfg.username != null && cfg.password != null) {
      builder.defaultCredentials(cfg.username, cfg.password);
    }

    factory.setHttpClientConfig(builder.build());
    return (JestHttpClient) factory.getObject();
  }
}
