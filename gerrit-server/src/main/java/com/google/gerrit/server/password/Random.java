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

/** Random data generator for use by {@link PasswordFunction}. */
public interface Random {
  /**
   * Obtain random bytes and fill the passed array with it.
   * <p>
   * This function should be using a cryptographically strong random number
   * generator, such as {@link java.security.SecureRandom}.
   *
   * @param salt the array to fill with random data.
   */
  void nextBytes(byte[] salt);
}
