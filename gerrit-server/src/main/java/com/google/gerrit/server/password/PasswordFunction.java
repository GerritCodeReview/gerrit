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


/** A password encryption and verification algorithm. */
public interface PasswordFunction {
  /** @return unique name for this function, to identify it in stored values. */
  String getName();

  /**
   * Encrypt a plain-text password according to this function's algorithm.
   * <p>
   * When encoding {@code password} a Unicode complete character encoding like
   * UTF-8 should be used.
   *
   * @param rng a random data generator to produce unique information for this
   *        encrypted value.
   * @param password plain-text password to encrypt.
   * @return the encrypted value for storage. The caller will prefix the
   *         encrypted value with a form of {@link #getName()} to identify this
   *         function in the future.
   */
  String encrypt(Random rng, String password);

  /**
   * Convert a stored password back to its plain-text value.
   * <p>
   * This is an optional operation. Not all functions support converting back to
   * plain-text form. One-way functions such as MD5 or SHA-1 must return null.
   *
   * @param stored the encrypted value previously returned by encrypt.
   * @return the plain-text value. {@code null} if conversion back to plain-text
   *         is not supported by this function.
   */
  String decrypt(String stored);

  /**
   * Validate the plain-text password matches the stored value.
   *
   * @param password the plain-text password.
   * @param stored the previously stored value returned by encrypt.
   * @return true if the password matches; false otherwise.
   */
  boolean check(String password, String stored);
}
