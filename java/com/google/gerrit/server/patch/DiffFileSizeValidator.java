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

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import org.eclipse.jgit.lib.Config;

@Singleton
public class DiffFileSizeValidator implements DiffValidator {
  static final int DEFAULT_MAX_FILE_SIZE = -1;
  private static final String ERROR_MESSAGE =
      "File size for file %s exceeded the max file size threshold. Threshold = %d MiB, Actual size = %d MiB";

  @VisibleForTesting int maxFileSize;

  @VisibleForTesting
  void setMaxFileSize(int threshold) {
    this.maxFileSize = threshold;
  }

  @Inject
  public DiffFileSizeValidator(@GerritServerConfig Config cfg) {
    this.maxFileSize = cfg.getInt("change", "diffFileSizeThreshold", DEFAULT_MAX_FILE_SIZE);
  }

  @Override
  public void validate(FileDiffOutput fileDiff) throws LargeObjectException {
    if (maxFileSize == DEFAULT_MAX_FILE_SIZE) {
      // Do not apply limits if the config is not set.
      return;
    }
    if (fileDiff.size() > maxFileSize) {
      throw new LargeObjectException(
          String.format(ERROR_MESSAGE, fileDiff.getDefaultPath(), maxFileSize, fileDiff.size()));
    }
    if (fileDiff.sizeDelta() > maxFileSize) {
      throw new LargeObjectException(
          String.format(
              ERROR_MESSAGE, fileDiff.getDefaultPath(), maxFileSize, fileDiff.sizeDelta()));
    }
  }
}
