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

import static com.google.common.base.Preconditions.checkState;

import com.google.common.base.Splitter;
import com.google.common.io.BaseEncoding;
import com.google.common.primitives.Ints;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.List;
import org.bouncycastle.crypto.generators.BCrypt;
import org.bouncycastle.util.Arrays;

/**
 * Holds logic for salted, hashed passwords. It uses BCrypt from BouncyCastle, which truncates
 * passwords at 72 bytes.
 */
public class HashedPassword {
  private static final String ALGORITHM_PREFIX = "bcrypt:";
  private static final String ALGORITHM_PREFIX_0 = "bcrypt0:";
  private static final SecureRandom secureRandom = new SecureRandom();
  private static final BaseEncoding codec = BaseEncoding.base64();

  // bcrypt uses 2^cost rounds. Since we use a generated random password, no need
  // for a high cost.
  private static final int DEFAULT_COST = 4;

  public static class DecoderException extends Exception {
    private static final long serialVersionUID = 1L;

    public DecoderException(String message) {
      super(message);
    }
  }

  /**
   * decodes a hashed password encoded with {@link #encode}.
   *
   * @throws DecoderException if input is malformed.
   */
  public static HashedPassword decode(String encoded) throws DecoderException {
    if (!encoded.startsWith(ALGORITHM_PREFIX) && !encoded.startsWith(ALGORITHM_PREFIX_0)) {
      throw new DecoderException("unrecognized algorithm");
    }

    List<String> fields = Splitter.on(':').splitToList(encoded);
    if (fields.size() != 4) {
      throw new DecoderException("want 4 fields");
    }

    Integer cost = Ints.tryParse(fields.get(1));
    if (cost == null) {
      throw new DecoderException("cost parse failed");
    }

    if (!(cost >= 4 && cost < 32)) {
      throw new DecoderException("cost should be 4..31 inclusive, got " + cost);
    }

    byte[] salt = codec.decode(fields.get(2));
    if (salt.length != 16) {
      throw new DecoderException("salt should be 16 bytes, got " + salt.length);
    }
    return new HashedPassword(
        codec.decode(fields.get(3)), salt, cost, encoded.startsWith(ALGORITHM_PREFIX_0));
  }

  private static byte[] hashPassword(
      String password, byte[] salt, int cost, boolean nullTerminate) {
    byte[] pwBytes = password.getBytes(StandardCharsets.UTF_8);
    if (nullTerminate && !password.endsWith("\0")) {
      pwBytes = Arrays.append(pwBytes, (byte) 0);
    }
    return BCrypt.generate(pwBytes, salt, cost);
  }

  public static HashedPassword fromPassword(String password) {
    byte[] salt = newSalt();

    return new HashedPassword(
        hashPassword(password, salt, DEFAULT_COST, true), salt, DEFAULT_COST, true);
  }

  private static byte[] newSalt() {
    byte[] bytes = new byte[16];
    secureRandom.nextBytes(bytes);
    return bytes;
  }

  private byte[] salt;
  private byte[] hashed;
  private int cost;
  // Raw bcrypt repeats the password, so "ABC" works for "ABCABC" too. To prevent this, add
  // the terminating null char to the password.
  boolean nullTerminate;

  private HashedPassword(byte[] hashed, byte[] salt, int cost, boolean nullTerminate) {
    this.salt = salt;
    this.hashed = hashed;
    this.cost = cost;
    this.nullTerminate = nullTerminate;

    checkState(cost >= 4 && cost < 32);

    // salt must be 128 bit.
    checkState(salt.length == 16);
  }

  /**
   * Serialize the hashed password and its parameters for persistent storage.
   *
   * @return one-line string encoding the hash and salt.
   */
  public String encode() {
    return (nullTerminate ? ALGORITHM_PREFIX_0 : ALGORITHM_PREFIX)
        + cost
        + ":"
        + codec.encode(salt)
        + ":"
        + codec.encode(hashed);
  }

  public boolean checkPassword(String password) {
    // Constant-time comparison, because we're paranoid.
    return Arrays.constantTimeAreEqual(hashPassword(password, salt, cost, nullTerminate), hashed);
  }
}
