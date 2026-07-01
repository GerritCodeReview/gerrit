// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.extensions.api.projects;

import java.util.Map;

/**
 * Input for creating a commit that applies a set of file operations to a branch in a single call.
 */
public class CommitFilesInput {
  /** Commit message. If unset, a default message is generated. */
  public String commitMessage;

  /**
   * Optional base commit (SHA-1).
   *
   * <p>This is the expected current commit of the target branch: the request is rejected if the
   * branch no longer points at it (optimistic concurrency / lost-update protection). When unset,
   * the current branch tip is used.
   */
  public String baseRevision;

  /**
   * File operations to apply, keyed by file path. Each entry either writes/creates content, deletes
   * the file, or renames another file to this path. Applied together as one commit.
   */
  public Map<String, FileChange> files;

  /** A single file operation within a {@link CommitFilesInput}. */
  public static class FileChange {
    /**
     * New file content, base64-encoded, for a write or create. Mutually exclusive with {@link
     * #delete} and {@link #renameFrom}.
     */
    public String content;

    /**
     * File mode in octal format. Supported values are {@code 100644} (regular file), {@code 100755}
     * (executable file), {@code 120000} (symlink) and {@code 160000} (gitlink). If unset, new files
     * are created as {@code 100644} and existing files keep their mode.
     */
    public Integer fileMode;

    /**
     * When {@code true}, deletes the file at this path. Mutually exclusive with the other fields.
     */
    public boolean delete;

    /**
     * Source path to rename from. The file at {@code renameFrom} is moved to this entry's path.
     * Mutually exclusive with {@link #content} and {@link #delete}.
     */
    public String renameFrom;
  }
}
