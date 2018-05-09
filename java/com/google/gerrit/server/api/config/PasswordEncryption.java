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
import com.google.gerrit.server.securestore.SecureStore;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.security.AlgorithmParameters;
import java.security.GeneralSecurityException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import joptsimple.internal.Strings;

@Singleton
public class PasswordEncryption {

  private static final SecureRandom secureRandom = new SecureRandom();
  private static final int keyLength = 128;
  private static final int iterationCount = 1000;
  private static final String algorithm = "AES";
  private static final String hashAlgorithm = "PBKDF2WithHmacSHA512";
  private static final String transformation = "AES/CBC/PKCS5Padding";
  private static final String SECTION = "encryption";
  private static final String KEY = "passwordKey";
  private static final String delimiter = ":";

  private final SecureStore secureStore;

  @Inject
  public PasswordEncryption(SecureStore secureStore) {
    this.secureStore = secureStore;
  }

  public String encrypt(String password) {
    if (Strings.isNullOrEmpty(getKeyFromConfig())) {
      byte[] saltBytes = newSalt();
      SecretKeySpec key;
      try {
        key = createSecretKey(password.toCharArray(), saltBytes, iterationCount, keyLength);
        String keyStr = base64Encode(key.getEncoded());
        secureStore.set(SECTION, null, KEY, keyStr);
        return encrypt(password, key);
      } catch (GeneralSecurityException e) {
        e.printStackTrace();
      }
    }
    return encryptWithExistingKey(password);
  }

  private String getKeyFromConfig() {
    return secureStore.get(SECTION, null, KEY);
  }

  private String encryptWithExistingKey(String password) {
    try {
      String key = getKeyFromConfig();
      byte[] keyBytes = base64Decode(key);
      return encrypt(password, new SecretKeySpec(keyBytes, algorithm));
    } catch (GeneralSecurityException e) {
      e.printStackTrace();
    }
    return null;
  }

  public String decrypt(String encryptedPassword) {
    String key = getKeyFromConfig();
    if (!Strings.isNullOrEmpty(key)) {
      try {
        byte[] keyBytes = base64Decode(key);
        return decrypt(encryptedPassword, new SecretKeySpec(keyBytes, algorithm));
      } catch (GeneralSecurityException e) {
        e.printStackTrace();
      }
    }
    return null;
  }

  private static SecretKeySpec createSecretKey(
      char[] password, byte[] salt, int iterationCount, int keyLength)
      throws NoSuchAlgorithmException, InvalidKeySpecException {
    SecretKeyFactory keyFactory = SecretKeyFactory.getInstance(hashAlgorithm);
    PBEKeySpec keySpec = new PBEKeySpec(password, salt, iterationCount, keyLength);
    SecretKey key = keyFactory.generateSecret(keySpec);
    return new SecretKeySpec(key.getEncoded(), algorithm);
  }

  private static String encrypt(String property, SecretKeySpec key)
      throws GeneralSecurityException {
    Cipher pbeCipher = Cipher.getInstance(transformation);
    pbeCipher.init(Cipher.ENCRYPT_MODE, key);
    AlgorithmParameters parameters = pbeCipher.getParameters();
    IvParameterSpec ivParameterSpec = parameters.getParameterSpec(IvParameterSpec.class);
    byte[] cryptoText = pbeCipher.doFinal(property.getBytes(Charsets.UTF_8));
    byte[] initializationVector = ivParameterSpec.getIV();
    return base64Encode(initializationVector) + delimiter + base64Encode(cryptoText);
  }

  private static String base64Encode(byte[] bytes) {
    return Base64.getEncoder().encodeToString(bytes);
  }

  private static String decrypt(String string, SecretKeySpec key) throws GeneralSecurityException {
    String initializationVector = string.split(delimiter)[0];
    String property = string.split(delimiter)[1];
    Cipher pbeCipher = Cipher.getInstance(transformation);
    pbeCipher.init(
        Cipher.DECRYPT_MODE, key, new IvParameterSpec(base64Decode(initializationVector)));
    return new String(pbeCipher.doFinal(base64Decode(property)), Charsets.UTF_8);
  }

  private static byte[] base64Decode(String property) {
    return Base64.getDecoder().decode(property);
  }

  private static byte[] newSalt() {
    byte[] bytes = new byte[16];
    secureRandom.nextBytes(bytes);
    return bytes;
  }
}
