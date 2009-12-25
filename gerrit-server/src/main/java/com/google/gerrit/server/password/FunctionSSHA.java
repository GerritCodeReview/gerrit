// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.password;

import org.apache.commons.codec.binary.Base64;

import java.io.UnsupportedEncodingException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/** Implements the same storage format as OpenLDAP's SSHA encryption. */
class FunctionSSHA implements PasswordFunction {
  private static final int SALT_SIZE = 4;
  private static final int HASH_SIZE = 20;

  @Override
  public String getName() {
    return "SSHA";
  }

  @Override
  public String encrypt(Random rng, String password) {
    try {
      final byte[] salt = new byte[SALT_SIZE];
      rng.nextBytes(salt);

      final MessageDigest md = MessageDigest.getInstance("SHA-1");
      md.update(password.getBytes("UTF-8"));
      md.update(salt);
      final byte[] hash = md.digest();
      if (hash.length != HASH_SIZE) {
        throw new RuntimeException("SHA-1 produced " + hash.length + " bytes");
      }

      final byte[] str = new byte[HASH_SIZE + salt.length];
      System.arraycopy(hash, 0, str, 0, HASH_SIZE);
      System.arraycopy(salt, 0, str, HASH_SIZE, salt.length);

      return new String(Base64.encodeBase64(str, false), "ISO-8859-1");

    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Cannot encrypt password", e);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Cannot encrypt password", e);
    }
  }

  @Override
  public String decrypt(String stored) {
    return null;
  }

  @Override
  public boolean check(String password, String stored) {
    try {
      final byte[] str = Base64.decodeBase64(stored.getBytes("ISO-8859-1"));

      final MessageDigest md = MessageDigest.getInstance("SHA-1");
      md.update(password.getBytes("UTF-8"));
      md.update(str, HASH_SIZE, str.length - HASH_SIZE);

      final byte[] hash = md.digest();
      return eq(hash, str);

    } catch (UnsupportedEncodingException e) {
      throw new RuntimeException("Cannot encrypt password", e);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Cannot encrypt password", e);
    }
  }

  private static final boolean eq(final byte[] a, final byte[] b) {
    for (int i = 0; i < HASH_SIZE; i++) {
      if (a[i] != b[i]) {
        return false;
      }
    }
    return true;
  }
}
