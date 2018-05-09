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
import java.security.NoSuchAlgorithmException;
import javax.crypto.Cipher;
import org.eclipse.jgit.lib.Config;

public class EncryptionConfig {

  public static final int KEY_LENGTH = 256;
  public static final int RESTRICTED_KEY_LENGTH = 128;
  public static final String ALGORITHM = "AES";
  public static final String BLOCK_MODE = "GCM";
  public static final String PADDING = "NoPadding";
  public static final String SECTION = "encryption";

  private final Config gerritConfig;

  @Inject
  public EncryptionConfig(@GerritServerConfig Config config) {
    this.gerritConfig = config;
  }

  public int keyLength() {
    int value = gerritConfig.getInt(SECTION, "keyLength", KEY_LENGTH);
    if (restrictedCryptography() && value > 128) {
      return RESTRICTED_KEY_LENGTH;
    }
    return value;
  }

  public String algorithm() {
    return getConfigString("algorithm", ALGORITHM);
  }

  public String transformation() {
    return algorithm() + "/" + BLOCK_MODE + "/" + padding();
  }

  private String padding() {
    return getConfigString("padding", PADDING);
  }

  private String getConfigString(String name, String defaultValue) {
    String value = gerritConfig.getString(SECTION, null, name);
    if (Strings.isNullOrEmpty(value)) {
      return defaultValue;
    }
    return value;
  }

  /**
   * Determines if cryptography restrictions apply. Restrictions apply if the value of {@link
   * Cipher#getMaxAllowedKeyLength(String)} returns a value smaller than {@link Integer#MAX_VALUE}
   * if there are any restrictions according to the JavaDoc of the method. This method is added for
   * backward compatibility.
   *
   * @return <code>true</code> if restrictions apply, <code>false</code> otherwise
   */
  private boolean restrictedCryptography() {
    try {
      return Cipher.getMaxAllowedKeyLength(transformation()) < Integer.MAX_VALUE;
    } catch (final NoSuchAlgorithmException e) {
      throw new IllegalStateException(
          "The transform \"" + transformation() + "\" is not available", e);
    }
  }
}
