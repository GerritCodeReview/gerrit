// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.verifier;

import java.security.MessageDigest;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;

public class VerifierUUID {
  /**
   * Creates a new UUID for a verifier.
   *
   * <p>The creation of the UUID is non-deterministic. This means invoking this method multiple
   * times with the same parameters will result in a different UUID for each call.
   *
   * @param verifierName verifier name.
   * @return verifier UUID.
   */
  public static String make(String verifierName) {
    MessageDigest md = Constants.newMessageDigest();
    md.update(Constants.encode("verifier " + verifierName + "\n"));
    md.update(Constants.encode(String.valueOf(Math.random())));
    return ObjectId.fromRaw(md.digest()).name();
  }

  private VerifierUUID() {}
}
