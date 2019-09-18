// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.reviewdb.client.Change;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;

public class ChangeId {
  private static final SecureRandom rng;

  static {
    try {
      rng = SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Cannot create RNG for Change-Id generator", e);
    }
  }

  public static ObjectId generateChangeId() {
    byte[] rand = new byte[Constants.OBJECT_ID_STRING_LENGTH];
    rng.nextBytes(rand);
    String randomString = new String(rand, UTF_8);

    try (ObjectInserter f = new ObjectInserter.Formatter()) {
      return f.idFor(Constants.OBJ_COMMIT, Constants.encode(randomString));
    }
  }

  public static Change.Key generateKey() {
    return new Change.Key("I" + generateChangeId().name());
  }
}
