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

import static com.google.gerrit.server.api.config.EncryptionConfig.SECTION;

import com.google.common.base.Strings;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class KeyManager {
  private static final Logger log = LoggerFactory.getLogger(KeyManager.class);

  public static final String KEY = "key";

  private static final SecureRandom secureRandom = new SecureRandom();
  private static final int RANDOM_STR_LENGTH = 15;

  private final EncryptionConfig config;
  private final SecureStore secureStore;

  @Inject
  public KeyManager(EncryptionConfig config, SecureStore secureStore) {
    this.secureStore = secureStore;
    this.config = config;
    createAndSaveSecretKey();
  }

  public String getSecretKey() {
    return secureStore.get(SECTION, null, KEY);
  }

  public SecretKeySpec getSecretKeySpec() {
    return new SecretKeySpec(base64Decode(getSecretKey()), config.algorithm());
  }

  private void createAndSaveSecretKey() {
    if (Strings.isNullOrEmpty(getSecretKey())) {
      try {
        String secretKey = createSecretKey(randomString().toCharArray(), newSalt());
        if (!Strings.isNullOrEmpty(secretKey)) {
          secureStore.set(SECTION, null, KEY, secretKey);
        }
      } catch (GeneralSecurityException e) {
        log.error("Failed to generate the secret key, Cause: {}", e);
      }
    }
  }

  private String createSecretKey(char[] password, byte[] salt)
      throws NoSuchAlgorithmException, InvalidKeySpecException {
    SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(config.hashAlgorithm());
    PBEKeySpec keySpec = new PBEKeySpec(password, salt, config.iterations(), config.keyLength());
    SecretKey key = keyFactory.generateSecret(keySpec);
    SecretKeySpec secretKeySpec = new SecretKeySpec(key.getEncoded(), config.algorithm());
    return base64Encode(secretKeySpec.getEncoded());
  }

  public static String base64Encode(byte[] bytes) {
    return Base64.encodeBase64String(bytes);
  }

  public static byte[] base64Decode(String property) {
    return Base64.decodeBase64(property);
  }

  private static byte[] newSalt() {
    byte[] bytes = new byte[16];
    secureRandom.nextBytes(bytes);
    return bytes;
  }

  private static String randomString() {
    return RandomStringUtils.random(RANDOM_STR_LENGTH, 0, 0, false, false, null, secureRandom);
  }
}
