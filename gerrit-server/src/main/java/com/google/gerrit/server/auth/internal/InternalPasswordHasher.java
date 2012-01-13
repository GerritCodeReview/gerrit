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

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

public final class InternalPasswordHasher {

  public static String hashPassword(String password, int salt) {
    MessageDigest sha1 = getDigest();
    String secret = salt + password;
    byte[] digest = sha1.digest(secret.getBytes());

    return formatHash(digest);
  }

  private static MessageDigest getDigest() {
    try {
      return MessageDigest.getInstance("SHA-1");
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("Cannot find provider for SHA-1");
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
