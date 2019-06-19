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

import static com.google.gerrit.elasticsearch.ElasticVersion.V6_7;

import com.google.gson.JsonObject;

public class ElasticQueryAdapter {
  static final String V6_TYPE = "_doc";

  private static final String INCLUDE_TYPE = "include_type_name=true";
  private static final String INDICES = "?allow_no_indices=false";

  private final boolean useV5Type;
  private final boolean useV6Type;
  private final boolean omitType;
  private final int defaultNumberOfShards;

  private final String searchFilteringName;
  private final String indicesExistParams;
  private final String exactFieldType;
  private final String stringFieldType;
  private final String indexProperty;
  private final String rawFieldsKey;
  private final String versionDiscoveryUrl;
  private final String includeTypeNameParam;

  ElasticQueryAdapter(ElasticVersion version) {
    this.useV5Type = !version.isV6OrLater();
    this.useV6Type = version.isV6();
    this.omitType = version.isV7OrLater();
    this.defaultNumberOfShards = version.isV7OrLater() ? 1 : 5;
    this.versionDiscoveryUrl = version.isV6OrLater() ? "/%s*" : "/%s*/_aliases";
    this.searchFilteringName = "_source";
    this.indicesExistParams =
        version.isAtLeastMinorVersion(V6_7) ? INDICES + "&" + INCLUDE_TYPE : INDICES;
    this.exactFieldType = "keyword";
    this.stringFieldType = "text";
    this.indexProperty = "true";
    this.rawFieldsKey = "_source";
    this.includeTypeNameParam = version.isAtLeastMinorVersion(V6_7) ? "?" + INCLUDE_TYPE : "";
  }

  public void setType(JsonObject properties, String type) {
    if (useV5Type) {
      properties.addProperty("_type", type);
    }
  }

  public String searchFilteringName() {
    return searchFilteringName;
  }

  String indicesExistParams() {
    return indicesExistParams;
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

  boolean deleteToReplace() {
    return useV5Type;
  }

  boolean useV5Type() {
    return useV5Type;
  }

  boolean useV6Type() {
    return useV6Type;
  }

  boolean omitType() {
    return omitType;
  }

  int getDefaultNumberOfShards() {
    return defaultNumberOfShards;
  }

  String getType() {
    return getType("");
  }

  String getType(String type) {
    if (useV6Type()) {
      return V6_TYPE;
    }
    return useV5Type() ? type : "";
  }

  String getVersionDiscoveryUrl(String name) {
    return String.format(versionDiscoveryUrl, name);
  }

  String includeTypeNameParam() {
    return includeTypeNameParam;
  }
}
