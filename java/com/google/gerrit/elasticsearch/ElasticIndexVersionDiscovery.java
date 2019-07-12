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

import com.google.common.flogger.FluentLogger;
import com.google.gson.JsonParser;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;

@Singleton
class ElasticIndexVersionDiscovery {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ElasticRestClientProvider client;

  @Inject
  ElasticIndexVersionDiscovery(ElasticRestClientProvider client) {
    this.client = client;
  }

  List<String> discover(String prefix, String indexName) throws IOException {
    String name = prefix + indexName + "_";
    Request request = new Request("GET", client.adapter().getVersionDiscoveryUrl(name));
    Response response = client.get().performRequest(request);

    StatusLine statusLine = response.getStatusLine();
    if (statusLine.getStatusCode() != HttpStatus.SC_OK) {
      String message =
          String.format(
              "Failed to discover index versions for %s: %d: %s",
              name, statusLine.getStatusCode(), statusLine.getReasonPhrase());
      logger.atSevere().log(message);
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
