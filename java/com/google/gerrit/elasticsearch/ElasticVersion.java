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
  V7_4("7.4.*"),
  V7_5("7.5.*"),
  V7_6("7.6.*"),
  V7_7("7.7.*"),
  V7_8("7.8.*");

  private final String version;
  private final Pattern pattern;

  ElasticVersion(String version) {
    this.version = version;
    this.pattern = Pattern.compile(version);
  }

  public static class UnsupportedVersion extends ElasticException {
    private static final long serialVersionUID = 1L;

    UnsupportedVersion(String version) {
      super(
          String.format(
              "Unsupported version: [%s]. Supported versions: %s", version, supportedVersions()));
    }
  }

  /**
   * Convert a version String to an ElasticVersion if supported.
   *
   * @param version for which to return an ElasticVersion
   * @return the corresponding ElasticVersion if supported
   * @throws UnsupportedVersion
   */
  public static ElasticVersion forVersion(String version) {
    for (ElasticVersion value : ElasticVersion.values()) {
      if (value.pattern.matcher(version).matches()) {
        return value;
      }
    }
    throw new UnsupportedVersion(version);
  }

  public static String supportedVersions() {
    return Joiner.on(", ").join(ElasticVersion.values());
  }

  @Override
  public String toString() {
    return version;
  }
}
