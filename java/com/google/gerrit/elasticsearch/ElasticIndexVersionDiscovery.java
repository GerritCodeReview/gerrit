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

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import io.searchbox.client.JestResult;
import io.searchbox.client.http.JestHttpClient;
import io.searchbox.indices.aliases.GetAliases;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;

@Singleton
class ElasticIndexVersionDiscovery {
  private final JestHttpClient client;

  @Inject
  ElasticIndexVersionDiscovery(JestClientBuilder clientBuilder) {
    this.client = clientBuilder.build();
  }

  List<String> discover(String prefix, String indexName) throws IOException {
    String name = prefix + indexName + "_";
    JestResult result = client.execute(new GetAliases.Builder().addIndex(name + "*").build());
    if (result.isSucceeded()) {
      JsonObject object = result.getJsonObject().getAsJsonObject();
      List<String> versions = new ArrayList<>(object.size());
      for (Entry<String, JsonElement> entry : object.entrySet()) {
        versions.add(entry.getKey().replace(name, ""));
      }
      return versions;
    }
    return Collections.emptyList();
  }
}
