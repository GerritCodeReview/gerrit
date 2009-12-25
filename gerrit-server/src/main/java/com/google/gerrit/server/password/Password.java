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

import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Set;

/** Encrypts passwords for storage and later authentication. */
@Singleton
public class Password {
  private final Random rng;
  private final Set<PasswordFunction> functions;
  private final PasswordFunction encrypt;

  @Inject
  Password(Random rng, Set<PasswordFunction> functions,
      @Default PasswordFunction encrypt) {
    this.rng = rng;
    this.functions = functions;
    this.encrypt = encrypt;
  }

  /**
   * Encrypt a plain-text password into its secure stored value.
   *
   * @param password the password to encrypt.
   * @return the encrypted string, prefixed with the name of the encryption
   *         function used to protect the plain-text value.
   */
  public String encrypt(String password) {
    return "{" + encrypt.getName() + "}" + encrypt.encrypt(rng, password);
  }

  /**
   * Convert a stored password back to its plain-text value.
   * <p>
   * This is an optional operation. Not all functions support converting back to
   * plain-text form. One-way functions like MD-5 or SHA-1 must return null.
   *
   * @param stored the encrypted value previously returned by encrypt.
   * @return the plain-text value. {@code null} if conversion back to plain-text
   *         is not supported by this function.
   */
  public String decrypt(String stored) {
    final int ec = stored.indexOf('}');
    if (stored.startsWith("{") && 1 < ec) {
      String type = stored.substring(1, ec);
      for (PasswordFunction e : functions) {
        if (type.equals(e.getName())) {
          return e.decrypt(stored.substring(ec + 1));
        }
      }
    }
    return null;
  }

  /**
   * Test if the password matches the stored value.
   *
   * @param password the password supplied by the authentication request.
   * @param stored the stored value.
   * @return true if they match; false if they do not.
   */
  public boolean check(String password, String stored) {
    final int ec = stored.indexOf('}');
    if (stored.startsWith("{") && 1 < ec) {
      String type = stored.substring(1, ec);
      for (PasswordFunction e : functions) {
        if (type.equals(e.getName())) {
          return e.check(password, stored.substring(ec + 1));
        }
      }
    }
    return false;
  }
}
