// Copyright (C) 2011 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.server.auth.internal;

import com.google.gerrit.reviewdb.client.AccountExternalId;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Formatter;

import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

public final class InternalPasswordHasher {

  public static String hashPassword(AccountExternalId authData, String password) {
    SecretKeyFactory sha1 = getPbkdf2();
    int salt = authData.getAccountId().get();
    String login = authData.getExternalId();
    byte[] saltBytes = (password + salt + login).getBytes();
    char[] passwordChars = password.toCharArray();
    PBEKeySpec pbeKeySpec = new PBEKeySpec(passwordChars, saltBytes, 512, 512);
    SecretKey secret = generateSecret(sha1, pbeKeySpec);
    byte[] digest = secret.getEncoded();

    return formatHash(digest);
  }

  private static SecretKeyFactory getPbkdf2() {
    try {
      return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Cannot find provider for SHA-1");
    }
  }

  private static SecretKey generateSecret(SecretKeyFactory secretKeyFactory, PBEKeySpec spec) {
    try {
      return secretKeyFactory.generateSecret(spec);
    } catch (InvalidKeySpecException e) {
      throw new RuntimeException("Failed while generating password hash.", e);
    }
  }

  private static String formatHash(byte[] digest) {
    Formatter formatter = new Formatter();
    for (byte b : digest) {
      formatter.format("%02x", b);
    }

    return formatter.toString();
  }

  private InternalPasswordHasher() {
    // do not instantiate
  }

}
