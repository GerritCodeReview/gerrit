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

/** Implements the same storage format as OpenLDAP's MD5 encryption. */
class FunctionMD5 implements PasswordFunction {
  private static final int HASH_SIZE = 16;

  @Override
  public String getName() {
    return "MD5";
  }

  @Override
  public String encrypt(Random rng, String password) {
    try {
      final MessageDigest md = MessageDigest.getInstance("MD5");
      md.update(password.getBytes("UTF-8"));
      final byte[] str = md.digest();
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

      final MessageDigest md = MessageDigest.getInstance("MD5");
      md.update(password.getBytes("UTF-8"));

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
