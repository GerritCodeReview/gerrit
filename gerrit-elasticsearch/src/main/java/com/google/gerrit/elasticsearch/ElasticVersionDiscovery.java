/*
 * Copyright 2017 CollabNet, Inc. All rights reserved.
 * http://www.collab.net
 */

package com.google.gerrit.elasticsearch;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import io.searchbox.client.JestResult;
import io.searchbox.client.http.JestHttpClient;
import io.searchbox.indices.aliases.GetAliases;

@Singleton
class ElasticVersionDiscovery {
  private final JestHttpClient client;

  @Inject
  ElasticVersionDiscovery(JestClientBuilder clientBuilder) {
    this.client = clientBuilder.build();
  }

  List<String> discover(String prefix, String indexName) throws IOException {
    String name = prefix + indexName + "_";
    GetAliases.Builder builder = new GetAliases.Builder();
    builder.addIndex(name + "*");
    JestResult result = client.execute(builder.build());
    if (result.isSucceeded()) {
      JsonObject object = result.getJsonObject().getAsJsonObject();
      List<String> versions = new ArrayList<>(object.size());
      for (Entry<String, JsonElement> entry : object.entrySet()) {
        String key = entry.getKey();
        String version = key.replace(name, "");
        versions.add(version);
      }
      return versions;
    }
    return Collections.emptyList();
  }
}
