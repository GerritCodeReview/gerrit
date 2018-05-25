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

public enum ElasticsearchVersion {
  V2_4("2.4"),
  V5_6("5.6");

  private final String version;

  private ElasticsearchVersion(String version) {
    this.version = version;
  }

  @Override
  public String toString() {
    return version;
  }

  public static ElasticsearchVersion fromString(String version) {
    if (version == null) {
      return null;
    }
    for (ElasticsearchVersion value : ElasticsearchVersion.values()) {
      if (value.version.equals(version)) {
        return value;
      }
    }
    return null;
  }
}
