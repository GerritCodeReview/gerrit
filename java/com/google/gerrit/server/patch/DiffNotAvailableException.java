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

package com.google.gerrit.server.patch;

import com.google.gerrit.server.patch.diff.ModifiedFilesCache;
import com.google.gerrit.server.patch.gitdiff.GitModifiedFilesCache;

/**
 * Thrown by the diff caches - the {@link GitModifiedFilesCache} and the {@link ModifiedFilesCache},
 * if the implementations failed to retrieve the modified files between the 2 commits.
 */
public class DiffNotAvailableException extends Exception {
  private static final long serialVersionUID = 1L;

  public DiffNotAvailableException(Throwable cause) {
    super(cause);
  }

  public DiffNotAvailableException(String message) {
    super(message);
  }

  public DiffNotAvailableException(String message, Throwable cause) {
    super(message, cause);
  }
}
