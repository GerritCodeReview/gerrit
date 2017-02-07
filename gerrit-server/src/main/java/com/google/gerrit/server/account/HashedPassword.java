// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.account;

import com.google.common.base.Preconditions;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import org.apache.commons.codec.DecoderException;
import org.apache.commons.codec.binary.Base64;
import org.bouncycastle.crypto.generators.BCrypt;
import org.bouncycastle.util.Arrays;


/**
 * Holds logic for salted, hashed passwords. It uses BCrypt from BouncyCastle, which
 * truncates passwords at 72 bytes.
 */
public class HashedPassword {
  private static final String ALGORITHM = "bcrypt";
  private static SecureRandom secureRandom = new SecureRandom();
  private static Base64 codec = new Base64(-1);

  /**
   * decodes a hashed password encoded with {@link #encode}.
   *
   * @throws DecoderException if input is malformed.
   */
  public static HashedPassword decode(String encoded) throws DecoderException {
    if (!encoded.startsWith(ALGORITHM + ":")) {
      throw new DecoderException("unrecognized algorithm");
    }

    String[] fields = encoded.split(":");
    if (fields.length != 4) {
      throw new DecoderException("want 4 fields in");
    }

    int cost = 4;
    try {
      cost = Integer.parseInt(fields[1]);
    } catch (NumberFormatException e) {
      throw new DecoderException("cost parse failed");
    }

    if (!(cost >= 4 && cost < 32)) {
      throw new DecoderException("cost should be 4..31 inclusive: " + cost);
    }

    byte[] salt = Base64.decodeBase64(fields[2]);
    if (salt.length != 16) {
      throw new DecoderException("salt should be 16 bytes, got " + salt.length);
    }
    return new HashedPassword(Base64.decodeBase64(fields[3]), salt, cost);
  }

  private static byte[] hashPassword(String password, byte[] salt, int cost) {
    byte pwBytes[] = password.getBytes(StandardCharsets.UTF_8);

    return BCrypt.generate(pwBytes, salt, cost);
  }

  public static HashedPassword fromPassword(String password) {
    byte[] salt = newSalt();

    // bcrypt uses 2^cost rounds. Since we use generated random password, no need
    // for using a high cost function.
    final int cost = 4;
    return new HashedPassword(hashPassword(password, salt, cost), salt, cost);
  }

  private static byte[] newSalt() {
    byte bytes[] = new byte[16];
    secureRandom.nextBytes(bytes);
    return bytes;
  }

  private byte[] salt;
  private byte[] hashed;
  private int cost;

  private HashedPassword(byte[] hashed, byte[] salt, int cost) {
    this.salt = salt;
    this.hashed = hashed;
    this.cost = cost;

    Preconditions.checkState(cost >= 4 && cost < 32);

    // salt must be 128 bit.
    Preconditions.checkState(salt.length == 16);
  }

  /** @returns one-line string encoding the hash and salt. */
  public String encode() {
    return ALGORITHM
        + ":"
        + cost
        + ":"
        + codec.encodeToString(salt)
        + ":"
        + codec.encodeToString(hashed);
  }

  public boolean checkPassword(String password) {
    // Constant-time comparison, because we're paranoid.
    return Arrays.areEqual(hashPassword(password, salt, cost), hashed);
  }

}
