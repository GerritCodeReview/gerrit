// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.sshd;

import java.security.PublicKey;
import java.security.spec.InvalidKeySpecException;

public class InvalidKeyAlgorithmException extends InvalidKeySpecException {
  private final String invalidKeyAlgo;
  private final String expectedKeyAlgo;
  private final PublicKey publicKey;

  public InvalidKeyAlgorithmException(
      String invalidKeyAlgo, String expectedKeyAlgo, PublicKey publicKey) {
    super("Key algorithm mismatch: expected " + expectedKeyAlgo + " but got " + invalidKeyAlgo);
    this.invalidKeyAlgo = invalidKeyAlgo;
    this.expectedKeyAlgo = expectedKeyAlgo;
    this.publicKey = publicKey;
  }

  public String getInvalidKeyAlgo() {
    return invalidKeyAlgo;
  }

  public String getExpectedKeyAlgo() {
    return expectedKeyAlgo;
  }

  public PublicKey getPublicKey() {
    return publicKey;
  }
}
