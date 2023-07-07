// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import static org.eclipse.jgit.storage.pack.PackConfig.DEFAULT_BIG_FILE_THRESHOLD;

import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;

public class DiffFileSizeValidator implements DiffValidator {
  static final int MAX_FILE_SIZE = DEFAULT_BIG_FILE_THRESHOLD;

  private static final String ERROR_MESSAGE =
      "File size for file %s exceeded the max file size threshold. Threshold = %d bytes, Actual size = %d bytes";

  @Override
  public void validate(FileDiffOutput fileDiff) throws LargeObjectException {
    if (fileDiff.size() > MAX_FILE_SIZE) {
      throw new LargeObjectException(
          String.format(ERROR_MESSAGE, fileDiff.getDefaultPath(), MAX_FILE_SIZE, fileDiff.size()));
    }
    if (fileDiff.sizeDelta() > MAX_FILE_SIZE) {
      throw new LargeObjectException(
          String.format(
              ERROR_MESSAGE, fileDiff.getDefaultPath(), MAX_FILE_SIZE, fileDiff.sizeDelta()));
    }
  }
}
