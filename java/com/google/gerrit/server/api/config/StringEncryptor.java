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

import com.google.common.base.Charsets;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.apache.commons.lang.RandomStringUtils;

@Singleton
public class StringEncryptor {

  private static final SecureRandom secureRandom = new SecureRandom();
  private static final String DELIMITER = ":";
  private static final int GCM_NONCE_LENGTH = 12;
  private static final int GCM_TAG_LENGTH = 16;
  private static final int RANDOM_STR_LENGTH = 16;

  private final KeyManager manager;
  private final EncryptionConfig config;

  @Inject
  public StringEncryptor(EncryptionConfig config, KeyManager manager) {
    this.config = config;
    this.manager = manager;
  }

  /**
   * Provides a secure way to encrypt strings.
   *
   * @param secret the string to be encrypted.
   * @return the encrypted data.
   * @throws GeneralSecurityException exception is thrown if the algorithm or transformation used
   *     for encryption is not available.
   */
  public String encrypt(String secret) throws GeneralSecurityException {
    return encrypt(secret, manager.getSecretKeySpec());
  }

  /**
   * Provides a secure way to decrypt strings.
   *
   * @param encryptedSecret the string to be decrypted.
   * @return the plaintext secret or null if the secret key was not found.
   * @throws GeneralSecurityException exception is thrown if the algorithm or transformation used
   *     for encryption is not available.
   */
  public String decrypt(String encryptedSecret) throws GeneralSecurityException {
    String key = manager.getSecretKey();
    if (!Strings.isNullOrEmpty(key)) {
      return decrypt(encryptedSecret, manager.getSecretKeySpec());
    }
    return null;
  }

  private String encrypt(String password, SecretKeySpec key) throws GeneralSecurityException {
    Cipher cipher = Cipher.getInstance(config.transformation());
    GCMParameterSpec spec = getSpec();
    cipher.init(Cipher.ENCRYPT_MODE, key, spec);
    byte[] additionalAuth = randomString().getBytes();
    cipher.updateAAD(additionalAuth);
    byte[] cryptoText = cipher.doFinal(password.getBytes(Charsets.UTF_8));
    return KeyManager.base64Encode(spec.getIV())
        + DELIMITER
        + KeyManager.base64Encode(additionalAuth)
        + DELIMITER
        + KeyManager.base64Encode(cryptoText);
  }

  private String decrypt(String encryptedString, SecretKeySpec key)
      throws GeneralSecurityException {
    List<String> encryptedTokens = Splitter.on(DELIMITER).splitToList(encryptedString);
    Cipher cipher = Cipher.getInstance(config.transformation());
    cipher.init(
        Cipher.DECRYPT_MODE,
        key,
        new GCMParameterSpec(GCM_TAG_LENGTH * 8, KeyManager.base64Decode(encryptedTokens.get(0))));
    cipher.updateAAD(KeyManager.base64Decode(encryptedTokens.get(1)));
    return new String(
        cipher.doFinal(KeyManager.base64Decode(encryptedTokens.get(2))), Charsets.UTF_8);
  }

  private GCMParameterSpec getSpec() {
    final byte[] nonce = new byte[GCM_NONCE_LENGTH];
    secureRandom.nextBytes(nonce);
    return new GCMParameterSpec(GCM_TAG_LENGTH * 8, nonce);
  }

  private static String randomString() {
    return RandomStringUtils.random(RANDOM_STR_LENGTH, 0, 0, false, false, null, secureRandom);
  }
}
