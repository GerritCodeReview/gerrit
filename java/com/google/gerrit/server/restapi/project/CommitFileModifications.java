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

package com.google.gerrit.server.restapi.project;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.api.projects.CreateCommitInput;
import com.google.gerrit.extensions.api.projects.CreateCommitInput.FileChange;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.server.edit.tree.ChangeFileContentModification;
import com.google.gerrit.server.edit.tree.DeleteFileModification;
import com.google.gerrit.server.edit.tree.RenameFileModification;
import com.google.gerrit.server.edit.tree.TreeModification;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.eclipse.jgit.util.Base64;

/**
 * Converts the file operations of a {@link CreateCommitInput} into {@link TreeModification}s.
 *
 * <p>This is the reusable request-to-tree-modification step shared between the direct branch-commit
 * path and any future review path: it validates the caller-supplied input and builds the {@link
 * TreeModification}s that {@link com.google.gerrit.server.edit.ChangeEditModifier#createNewTree}
 * (or any other {@code TreeCreator} caller) applies. Keeping it separate lets {@link
 * BranchCommitBuilder} focus on the branch ref update.
 */
final class CommitFileModifications {
  private CommitFileModifications() {}

  /**
   * Translates the requested file operations into {@link TreeModification}s while validating the
   * caller-supplied input, so that malformed requests fail with {@code 400 Bad Request} rather than
   * leaking through as a server error.
   */
  static ImmutableList<TreeModification> fromInput(CreateCommitInput input)
      throws BadRequestException {
    if (input.files == null || input.files.isEmpty()) {
      throw new BadRequestException("files is required");
    }
    ImmutableList.Builder<TreeModification> modifications =
        ImmutableList.builderWithExpectedSize(input.files.size());
    // Two operations that touch the same path (including a rename's source path) cannot be applied
    // together; detect that here instead of letting TreeCreator throw an IllegalStateException that
    // would surface as a 500.
    Set<String> touchedPaths = new HashSet<>();
    for (Map.Entry<String, FileChange> entry : input.files.entrySet()) {
      String path = entry.getKey();
      if (Strings.isNullOrEmpty(path)) {
        throw new BadRequestException("file path must not be empty");
      }
      FileChange change = entry.getValue();
      if (change == null) {
        throw new BadRequestException("no operation given for " + path);
      }
      int ops =
          (change.content != null ? 1 : 0)
              + (change.delete ? 1 : 0)
              + (change.renameFrom != null ? 1 : 0);
      if (ops != 1) {
        throw new BadRequestException(
            "exactly one of content, delete, or rename_from is required for " + path);
      }
      if (change.fileMode != null && change.content == null) {
        throw new BadRequestException("file_mode is only valid with content for " + path);
      }
      // Gitlink entries would reinterpret `content` as a 40-character SHA-1 rather than file bytes;
      // that second meaning is out of scope for this endpoint.
      if (change.fileMode != null && change.fileMode == 160000) {
        throw new BadRequestException("file_mode 160000 (gitlink) is not supported for " + path);
      }
      TreeModification modification;
      if (change.delete) {
        modification = new DeleteFileModification(path);
      } else if (change.renameFrom != null) {
        if (change.renameFrom.isEmpty()) {
          throw new BadRequestException("rename_from must not be empty for " + path);
        }
        if (change.renameFrom.equals(path)) {
          throw new BadRequestException("rename_from must differ from the target path " + path);
        }
        modification = new RenameFileModification(change.renameFrom, path);
      } else {
        modification =
            new ChangeFileContentModification(
                path,
                RawInputUtil.create(decodeBase64(change.content, path)),
                octalToBits(change.fileMode));
      }
      for (String touched : modification.getFilePaths()) {
        if (!touchedPaths.add(touched)) {
          throw new BadRequestException("multiple operations affect the same path: " + touched);
        }
      }
      modifications.add(modification);
    }
    return modifications.build();
  }

  private static byte[] decodeBase64(String content, String path) throws BadRequestException {
    try {
      return Base64.decode(content);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("content for " + path + " is not valid base64", e);
    }
  }

  @Nullable
  private static Integer octalToBits(@Nullable Integer octalFileMode) throws BadRequestException {
    if (octalFileMode == null) {
      return null;
    }
    try {
      return Integer.parseInt(Integer.toString(octalFileMode), 8);
    } catch (NumberFormatException e) {
      throw new BadRequestException("invalid file_mode: " + octalFileMode, e);
    }
  }
}
