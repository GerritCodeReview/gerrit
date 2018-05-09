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

package com.google.gerrit.server.api.config;

import com.google.common.base.Strings;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.inject.Inject;
import org.eclipse.jgit.lib.Config;

public class EncryptionConfig {

  public static final int KEY_LENGTH = 128;
  public static final int ITERATION_COUNT = 1000;
  public static final String ALGORITHM = "AES";
  public static final String HASH_ALGORITHM = "PBKDF2WithHmacSHA512";
  public static final String TRANSFORMATION = "AES/CBC/PKCS5Padding";
  public static final String SECTION = "encryption";

  private final Config gerritConfig;

  @Inject
  public EncryptionConfig(@GerritServerConfig Config config) {
    this.gerritConfig = config;
  }

  public int keyLength() {
    return gerritConfig.getInt(SECTION, "keyLength", KEY_LENGTH);
  }

  public int iterations() {
    return gerritConfig.getInt(SECTION, "iterations", ITERATION_COUNT);
  }

  public String algorithm() {
    return getConfigString("algorithm", ALGORITHM);
  }

  public String hashAlgorithm() {
    return getConfigString("hashAlgorithm", HASH_ALGORITHM);
  }

  public String transformation() {
    return getConfigString("transformation", TRANSFORMATION);
  }

  private String getConfigString(String name, String defaultValue) {
    String value = gerritConfig.getString(SECTION, null, name);
    if (Strings.isNullOrEmpty(value)) {
      return defaultValue;
    }
    return value;
  }
}
