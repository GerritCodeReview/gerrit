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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.restapi.BadRequestException;

/** Utility functions to manipulate commit messages. */
public class CommitMessageUtil {

  private CommitMessageUtil() {}

  /**
   * Checks for null or empty commit messages and appends a newline character to the commit message.
   *
   * @throws BadRequestException if the commit message is null or empty
   * @returns the trimmed message with a trailing newline character
   */
  public static String checkAndSanitizeCommitMessage(String commitMessage)
      throws BadRequestException {
    String wellFormedMessage = Strings.nullToEmpty(commitMessage).trim();
    if (wellFormedMessage.isEmpty()) {
      throw new BadRequestException("Commit message cannot be null or empty");
    }
    wellFormedMessage = wellFormedMessage + "\n";
    return wellFormedMessage;
  }
}
