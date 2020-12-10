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

public class ElasticQueryAdapter {
  private static final String INDICES = "?allow_no_indices=false";

  private final String searchFilteringName;
  private final String exactFieldType;
  private final String stringFieldType;
  private final String indexProperty;
  private final String rawFieldsKey;
  private final String versionDiscoveryUrl;

  ElasticQueryAdapter() {
    this.versionDiscoveryUrl = "/%s*";
    this.searchFilteringName = "_source";
    this.exactFieldType = "keyword";
    this.stringFieldType = "text";
    this.indexProperty = "true";
    this.rawFieldsKey = "_source";
  }

  public String searchFilteringName() {
    return searchFilteringName;
  }

  String indicesExistParams() {
    return INDICES;
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

  String getVersionDiscoveryUrl(String name) {
    return String.format(versionDiscoveryUrl, name);
  }
}
