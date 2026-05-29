// Copyright (C) 2021 The Android Open Source Project
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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch.ChangeType;
import java.util.Optional;

/**
 * Adapter for old/new paths of the new diff cache to the old diff cache representation. This is
 * needed for backward compatibility with all old diff cache callers.
 *
 * <p>TODO(ghareeb): It's better to revisit this logic and update all diff cache callers to use the
 * new diff cache output directly.
 */
public class FilePathAdapter {
  private FilePathAdapter() {}

  /**
   * Converts the old file path of the new diff cache output to the old diff cache representation.
   */
  @Nullable
  public static String getOldPath(Optional<String> oldName, ChangeType changeType) {
    return switch (changeType) {
      case DELETED, ADDED, MODIFIED -> null;
      case COPIED, RENAMED -> oldName.get();
      case REWRITE -> oldName.isPresent() ? oldName.get() : null;
    };
  }

  /**
   * Converts the new file path of the new diff cache output to the old diff cache representation.
   */
  public static String getNewPath(
      Optional<String> oldName, Optional<String> newName, ChangeType changeType) {
    return switch (changeType) {
      case DELETED -> oldName.get();
      case ADDED, MODIFIED, REWRITE, COPIED, RENAMED -> newName.get();
    };
  }
}
