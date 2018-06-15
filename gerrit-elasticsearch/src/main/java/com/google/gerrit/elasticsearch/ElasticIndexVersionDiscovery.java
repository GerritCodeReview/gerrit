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

import static java.util.stream.Collectors.toList;

import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpGet;
import org.elasticsearch.client.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
class ElasticIndexVersionDiscovery {
  private static final Logger log = LoggerFactory.getLogger(ElasticIndexVersionDiscovery.class);

  private final ElasticRestClientProvider client;

  @Inject
  ElasticIndexVersionDiscovery(ElasticRestClientProvider client) {
    this.client = client;
  }

  List<String> discover(String prefix, String indexName) throws IOException {
    String name = prefix + indexName + "_";
    Response response =
        client
            .get()
            .performRequest(HttpGet.METHOD_NAME, client.adapter().getVersionDiscoveryUrl(name));

    StatusLine statusLine = response.getStatusLine();
    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
      String message =
          String.format(
              "Failed to discover index versions for %s: %d: %s",
              name, statusLine.getStatusCode(), statusLine.getReasonPhrase());
      log.error(message);
      throw new IOException(message);
    }

    return new JsonParser()
        .parse(AbstractElasticIndex.getContent(response))
        .getAsJsonObject()
        .entrySet()
        .stream()
        .map(e -> e.getKey().replace(name, ""))
        .collect(toList());
  }
}
