// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.elasticsearch.ElasticRestClientProvider.SupportedVersion;
import com.google.gson.JsonObject;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
public class ElasticRestClientAdapter {

  private final ElasticRestClientProvider client;

  @Inject
  ElasticRestClientAdapter(ElasticRestClientProvider client) {
    this.client = client;
  }

  String getIndicesExistParameter() {
    if (client.initGetVersion().equals(SupportedVersion.V5.getVersion())) {
      return "?allow_no_indices=false";
    }
    return "";
  }

  public String getSearchFilteringName() {
    if (client.initGetVersion().equals(SupportedVersion.V5.getVersion())) {
      return "_source";
    }
    return "fields";
  }

  String getUuidFieldType() {
    if (client.initGetVersion().equals(SupportedVersion.V5.getVersion())) {
      return "keyword";
    }
    return "string";
  }

  void setIgnoreUnmapped(JsonObject properties) {
    if (!client.initGetVersion().equals(SupportedVersion.V5.getVersion())) {
      properties.addProperty("ignore_unmapped", true);
    }
  }
}
