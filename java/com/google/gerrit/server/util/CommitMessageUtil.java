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

package com.google.gerrit.server.util;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Strings;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.restapi.BadRequestException;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;

/** Utility functions to manipulate commit messages. */
public class CommitMessageUtil {
  private static final SecureRandom rng;

  static {
    try {
      rng = SecureRandom.getInstance("SHA1PRNG");
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException("Cannot create RNG for Change-Id generator", e);
    }
  }

  private CommitMessageUtil() {}

  /**
   * Checks for invalid (empty or containing \0) commit messages and appends a newline character to
   * the commit message.
   *
   * @throws BadRequestException if the commit message is null or empty
   * @returns the trimmed message with a trailing newline character
   */
  public static String checkAndSanitizeCommitMessage(@Nullable String commitMessage)
      throws BadRequestException {
    String trimmed = Strings.nullToEmpty(commitMessage).trim();
    if (trimmed.isEmpty()) {
      throw new BadRequestException("Commit message cannot be null or empty");
    }
    if (trimmed.indexOf(0) >= 0) {
      throw new BadRequestException("Commit message cannot have NUL character");
    }
    trimmed = trimmed + "\n";
    return trimmed;
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
    return Change.key("I" + generateChangeId().name());
  }
}
