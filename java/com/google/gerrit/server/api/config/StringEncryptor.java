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
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.util.List;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class StringEncryptor {
  private static final Logger log = LoggerFactory.getLogger(StringEncryptor.class);

  private static final String DELIMITER = ":";

  private final EncryptionConfig config;
  private final KeyManager manager;

  @Inject
  public StringEncryptor(EncryptionConfig config, KeyManager manager) {
    this.config = config;
    this.manager = manager;
  }

  public String encrypt(String password) {
    try {
      return encrypt(password, manager.getSecretKeySpec());
    } catch (GeneralSecurityException e) {
      log.error("Failed to encrypt the password, Cause: {}", e);
    }
    return null;
  }

  public String decrypt(String encryptedPassword) {
    String key = manager.getSecretKey();
    if (!Strings.isNullOrEmpty(key)) {
      try {
        return decrypt(encryptedPassword, manager.getSecretKeySpec());
      } catch (GeneralSecurityException e) {
        log.error("Failed to decrypt the password, Cause: {}", e);
      }
    }
    return null;
  }

  private String encrypt(String property, SecretKeySpec key) throws GeneralSecurityException {
    Cipher pbeCipher = Cipher.getInstance(config.transformation());
    pbeCipher.init(Cipher.ENCRYPT_MODE, key);
    AlgorithmParameters parameters = pbeCipher.getParameters();
    IvParameterSpec ivParameterSpec = parameters.getParameterSpec(IvParameterSpec.class);
    byte[] cryptoText = pbeCipher.doFinal(property.getBytes(Charsets.UTF_8));
    return KeyManager.base64Encode(ivParameterSpec.getIV())
        + DELIMITER
        + KeyManager.base64Encode(cryptoText);
  }

  private String decrypt(String encryptedString, SecretKeySpec key)
      throws GeneralSecurityException {
    List<String> encryptedTokens = Splitter.on(DELIMITER).splitToList(encryptedString);
    Cipher pbeCipher = Cipher.getInstance(config.transformation());
    pbeCipher.init(
        Cipher.DECRYPT_MODE,
        key,
        new IvParameterSpec(KeyManager.base64Decode(encryptedTokens.get(0))));
    return new String(
        pbeCipher.doFinal(KeyManager.base64Decode(encryptedTokens.get(1))), Charsets.UTF_8);
  }
}
