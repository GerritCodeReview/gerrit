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

import com.google.common.base.Joiner;
import java.util.regex.Pattern;

public enum ElasticVersion {
  V2_4("2.4.*"),
  V5_6("5.6.*"),
  V6_2("6.2.*"),
  V6_3("6.3.*");

  private final String version;
  private final Pattern pattern;

  ElasticVersion(String version) {
    this.version = version;
    this.pattern = Pattern.compile(version);
  }

  public static class InvalidVersion extends ElasticException {
    private static final long serialVersionUID = 1L;

    InvalidVersion(String version) {
      super(
          String.format(
              "Invalid version: [%s]. Supported versions: %s", version, supportedVersions()));
    }
  }

  public static ElasticVersion forVersion(String version) throws InvalidVersion {
    for (ElasticVersion value : ElasticVersion.values()) {
      if (value.pattern.matcher(version).matches()) {
        return value;
      }
    }
    throw new InvalidVersion(version);
  }

  public static String supportedVersions() {
    return Joiner.on(", ").join(ElasticVersion.values());
  }

  @Override
  public String toString() {
    return version;
  }
}
