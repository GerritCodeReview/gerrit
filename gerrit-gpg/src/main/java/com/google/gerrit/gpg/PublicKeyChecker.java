// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.gpg;

import org.bouncycastle.openpgp.PGPPublicKey;

import java.util.ArrayList;
import java.util.List;

/** Checker for GPG public keys for use in a push certificate. */
public class PublicKeyChecker {
  /**
   * Check a public key.
   *
   * @param key the public key.
   */
  public final CheckResult check(PGPPublicKey key) {
    List<String> problems = new ArrayList<>();
    if (key.isRevoked()) {
      // TODO(dborowitz): isRevoked is overeager:
      // http://www.bouncycastle.org/jira/browse/BJB-45
      problems.add("Key is revoked");
    }

    long validSecs = key.getValidSeconds();
    if (validSecs != 0) {
      long createdSecs = key.getCreationTime().getTime() / 1000;
      long nowSecs = System.currentTimeMillis() / 1000;
      if (nowSecs - createdSecs > validSecs) {
        problems.add("Key is expired");
      }
    }
    checkCustom(key, problems);
    return new CheckResult(problems);
  }

  /**
   * Perform custom checks.
   * <p>
   * Default implementation does nothing, but may be overridden by subclasses.
   *
   * @param key the public key.
   * @param problems list to which any problems should be added.
   */
  public void checkCustom(PGPPublicKey key, List<String> problems) {
    // Default implementation does nothing.
  }
}
