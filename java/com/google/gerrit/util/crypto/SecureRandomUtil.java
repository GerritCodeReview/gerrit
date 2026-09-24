// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.util.crypto;

import com.google.common.io.BaseEncoding;
import java.security.SecureRandom;

/**
 * Central source of cryptographically strong randomness for Gerrit.
 *
 * <p>Exposes a single shared {@link SecureRandom}. On the JDK's SUN provider every {@code
 * SecureRandom} algorithm is registered with the {@code ThreadSafe=true} service attribute, so the
 * shared instance is safe for concurrent use without external locking. On Unix-like platforms
 * {@code new SecureRandom()} resolves to {@code NativePRNG} (the kernel CSPRNG via {@code
 * /dev/urandom}); on platforms without native support it resolves to {@code DRBG}.
 *
 * <p>Prefer these helpers over {@code new SecureRandom()} and over explicitly requesting a named
 * algorithm, so the algorithm choice lives in exactly one place and no caller is pinned to a
 * specific, possibly legacy, generator.
 */
public final class SecureRandomUtil {
  private static final SecureRandom RANDOM = new SecureRandom();

  /** Returns {@code numBytes} cryptographically strong random bytes. */
  public static byte[] newBytes(int numBytes) {
    byte[] bytes = new byte[numBytes];
    RANDOM.nextBytes(bytes);
    return bytes;
  }

  /**
   * Returns a URL- and cookie-safe random string carrying {@code numBytes} of entropy, encoded as
   * unpadded base64url. The result is longer than {@code numBytes} (roughly {@code ceil(numBytes *
   * 4 / 3)} characters).
   *
   * <p>Do not use this for values that are persisted and later decoded with a fixed codec (password
   * salts, stored signing keys, session cookies): those must keep their existing encoding. Use
   * {@link #newBytes(int)} there and encode at the call site.
   */
  public static String newRandomString(int numBytes) {
    return BaseEncoding.base64Url().omitPadding().encode(newBytes(numBytes));
  }

  /**
   * Returns a random string carrying <b>256 bits (32 bytes)</b> of entropy, encoded as unpadded
   * base64url (about 43 characters).
   *
   * <p>256 bits is the conventional strength for an unguessable token or nonce (CSRF/OAuth state,
   * session identifiers): it matches the 256-bit primitives already in use, and because brute-force
   * guessing is infeasible well below that, it is a comfortable conservative choice — larger sizes
   * add string length without added practical security, and 128 bits is the floor.
   *
   * <p>Sugar for {@link #newRandomString(int) newRandomString(32)} so callers don't repeat the
   * literal; the {@code 32} in the name is the entropy in bytes, not the resulting string length.
   */
  public static String newRandomString32() {
    return newRandomString(32);
  }

  private SecureRandomUtil() {}
}
