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

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.gerrit.server.securestore.SecureStore;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.codec.binary.Base64;
import org.apache.commons.lang.RandomStringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StringEncryptor {
  private static final Logger log = LoggerFactory.getLogger(StringEncryptor.class);

  private static final SecureRandom secureRandom = new SecureRandom();
  private static final String KEY = "key";
  private static final String DELIMITER = ":";
  private static final int RANDOM_STR_LENGTH = 15;

  private final EncryptionConfig config;
  private final SecureStore secureStore;

  @Inject
  public StringEncryptor(EncryptionConfig config, SecureStore secureStore) {
    this.secureStore = secureStore;
    this.config = config;
    createAndSaveSecretKey();
  }

  public String encrypt(String password) {
    try {
      byte[] keyBytes = base64Decode(getKeyFromConfig());
      return encrypt(password, new SecretKeySpec(keyBytes, config.algorithm()));
    } catch (GeneralSecurityException e) {
      log.error("Failed to encrypt the password, Cause: {}", e);
    }
    return null;
  }

  public String decrypt(String encryptedPassword) {
    String key = getKeyFromConfig();
    if (!Strings.isNullOrEmpty(key)) {
      try {
        return decrypt(encryptedPassword, new SecretKeySpec(base64Decode(key), config.algorithm()));
      } catch (GeneralSecurityException e) {
        log.error("Failed to decrypt the password, Cause: {}", e);
      }
    }
    return null;
  }

  private void createAndSaveSecretKey() {
    if (Strings.isNullOrEmpty(getKeyFromConfig())) {
      try {
        secureStore.set(
            SECTION, null, KEY, createSecretKey(randomString().toCharArray(), newSalt()));
      } catch (GeneralSecurityException e) {
        log.error("Failed to generate the secret key, Cause: {}", e);
      }
    }
  }

  private String getKeyFromConfig() {
    return secureStore.get(SECTION, null, KEY);
  }

  private String createSecretKey(char[] password, byte[] salt)
      throws NoSuchAlgorithmException, InvalidKeySpecException {
    SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(config.hashAlgorithm());
    PBEKeySpec keySpec = new PBEKeySpec(password, salt, config.iterations(), config.keyLength());
    SecretKey key = keyFactory.generateSecret(keySpec);
    SecretKeySpec secretKeySpec = new SecretKeySpec(key.getEncoded(), config.algorithm());
    return base64Encode(secretKeySpec.getEncoded());
  }

  private String encrypt(String property, SecretKeySpec key) throws GeneralSecurityException {
    Cipher pbeCipher = Cipher.getInstance(config.transformation());
    pbeCipher.init(Cipher.ENCRYPT_MODE, key);
    AlgorithmParameters parameters = pbeCipher.getParameters();
    IvParameterSpec ivParameterSpec = parameters.getParameterSpec(IvParameterSpec.class);
    byte[] cryptoText = pbeCipher.doFinal(property.getBytes(Charsets.UTF_8));
    return base64Encode(ivParameterSpec.getIV()) + DELIMITER + base64Encode(cryptoText);
  }

  private static String base64Encode(byte[] bytes) {
    return Base64.encodeBase64String(bytes);
  }

  private String decrypt(String encryptedString, SecretKeySpec key)
      throws GeneralSecurityException {
    List<String> encryptedTokens = Splitter.on(DELIMITER).splitToList(encryptedString);
    Cipher pbeCipher = Cipher.getInstance(config.transformation());
    pbeCipher.init(
        Cipher.DECRYPT_MODE, key, new IvParameterSpec(base64Decode(encryptedTokens.get(0))));
    return new String(pbeCipher.doFinal(base64Decode(encryptedTokens.get(1))), Charsets.UTF_8);
  }

  private static byte[] base64Decode(String property) {
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
