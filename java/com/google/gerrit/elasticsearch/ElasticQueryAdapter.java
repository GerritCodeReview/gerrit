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

import com.google.gson.JsonObject;

public class ElasticQueryAdapter {
  static final String POST_V5_TYPE = "_doc";

  private final boolean ignoreUnmapped;
  private final boolean usePostV5Type;
  private final boolean omitTypeFromSearch;

  private final String searchFilteringName;
  private final String indicesExistParam;
  private final String exactFieldType;
  private final String stringFieldType;
  private final String indexProperty;
  private final String rawFieldsKey;
  private final String versionDiscoveryUrl;

  ElasticQueryAdapter(ElasticVersion version) {
    this.ignoreUnmapped = false;
    this.usePostV5Type = version.isV6OrLater();
    this.omitTypeFromSearch = version.isV7OrLater();
    this.versionDiscoveryUrl = version.isV6OrLater() ? "/%s*" : "/%s*/_aliases";
    this.searchFilteringName = "_source";
    this.indicesExistParam = "?allow_no_indices=false";
    this.exactFieldType = "keyword";
    this.stringFieldType = "text";
    this.indexProperty = "true";
    this.rawFieldsKey = "_source";
  }

  void setIgnoreUnmapped(JsonObject properties) {
    if (ignoreUnmapped) {
      properties.addProperty("ignore_unmapped", true);
    }
  }

  public void setType(JsonObject properties, String type) {
    if (!usePostV5Type) {
      properties.addProperty("_type", type);
    }
  }

  public String searchFilteringName() {
    return searchFilteringName;
  }

  String indicesExistParam() {
    return indicesExistParam;
  }

  String exactFieldType() {
    return exactFieldType;
  }

  String stringFieldType() {
    return stringFieldType;
  }

  String indexProperty() {
    return indexProperty;
  }

  String rawFieldsKey() {
    return rawFieldsKey;
  }

  boolean usePostV5Type() {
    return usePostV5Type;
  }

  boolean omitTypeFromSearch() {
    return omitTypeFromSearch;
  }

  String getType(String type) {
    return usePostV5Type() ? POST_V5_TYPE : type;
  }

  String getVersionDiscoveryUrl(String name) {
    return String.format(versionDiscoveryUrl, name);
  }
}
