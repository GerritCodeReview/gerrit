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
  private final boolean ignoreUnmapped;
  private final String searchFilteringName;
  private final String indicesExistParam;
  private final String keywordFieldType;
  private final String stringFieldType;
  private final String indexProperty;

  ElasticQueryAdapter(ElasticVersion version) {
    this.ignoreUnmapped = version == ElasticVersion.V2_4;
    switch (version) {
      case V5_6:
      case V6_2:
        this.searchFilteringName = "_source";
        this.indicesExistParam = "?allow_no_indices=false";
        this.keywordFieldType = "keyword";
        this.stringFieldType = "text";
        this.indexProperty = "true";
        break;
      case V2_4:
      default:
        this.searchFilteringName = "fields";
        this.indicesExistParam = "";
        this.keywordFieldType = "string";
        this.stringFieldType = "string";
        this.indexProperty = "not_analyzed";
        break;
    }
  }

  void setIgnoreUnmapped(JsonObject properties) {
    if (ignoreUnmapped) {
      properties.addProperty("ignore_unmapped", true);
    }
  }

  public String searchFilteringName() {
    return searchFilteringName;
  }

  String indicesExistParam() {
    return indicesExistParam;
  }

  String keywordFieldType() {
    return keywordFieldType;
  }

  String stringFieldType() {
    return stringFieldType;
  }

  String indexProperty() {
    return indexProperty;
  }
}
