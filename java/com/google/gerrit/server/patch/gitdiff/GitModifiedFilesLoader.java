// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.patch.gitdiff;

import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Patch;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.io.DisabledOutputStream;

/**
 * /** Class to load the files that have been modified between two Git trees.
 *
 * <p>Rename detection is off unless {@link #withRenameDetection(int)} is called.
 *
 * <p>The commits and the commit trees are looked up via the {@link RevWalk} instance that is
 * provided to the {@link #load(Config, ObjectReader, ObjectId, ObjectId)} method.
 */
public class GitModifiedFilesLoader {
  private static final ImmutableMap<ChangeType, Patch.ChangeType> changeTypeMap =
      ImmutableMap.of(
          DiffEntry.ChangeType.ADD,
          Patch.ChangeType.ADDED,
          DiffEntry.ChangeType.MODIFY,
          Patch.ChangeType.MODIFIED,
          DiffEntry.ChangeType.DELETE,
          Patch.ChangeType.DELETED,
          DiffEntry.ChangeType.RENAME,
          Patch.ChangeType.RENAMED,
          DiffEntry.ChangeType.COPY,
          Patch.ChangeType.COPIED);

  private @Nullable Integer renameScore = null;

  /**
   * Enables rename detection
   *
   * @param renameScore the score that should be used for the rename detection.
   */
  @CanIgnoreReturnValue
  public GitModifiedFilesLoader withRenameDetection(int renameScore) {
    checkState(renameScore >= 0);
    this.renameScore = renameScore;
    return this;
  }

  /**
   * Loads the files that have been modified between {@code aTree} and {@code bTree}.
   *
   * <p>The trees are looked up via the given {@code revWalk} instance,
   */
  public ImmutableList<ModifiedFile> load(
      Config repoConfig, ObjectReader reader, ObjectId aTree, ObjectId bTree) throws IOException {
    List<DiffEntry> entries = getGitTreeDiff(repoConfig, reader, aTree, bTree);
    return entries.stream().map(GitModifiedFilesLoader::toModifiedFile).collect(toImmutableList());
  }

  private List<DiffEntry> getGitTreeDiff(
      Config repoConfig, ObjectReader reader, ObjectId aTree, ObjectId bTree) throws IOException {
    try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      df.setReader(reader, repoConfig);
      if (renameScore != null) {
        df.setDetectRenames(true);
        df.getRenameDetector().setRenameScore(renameScore);

        // Skip detecting content renames for binary files.
        df.getRenameDetector().setSkipContentRenamesForBinaryFiles(true);
      }
      // The scan method only returns the file paths that are different. Callers may choose to
      // format these paths themselves.
      return df.scan(aTree.equals(ObjectId.zeroId()) ? null : aTree, bTree);
    }
  }

  private static ModifiedFile toModifiedFile(DiffEntry entry) {
    String oldPath = entry.getOldPath();
    String newPath = entry.getNewPath();
    return ModifiedFile.builder()
        .changeType(toChangeType(entry.getChangeType()))
        .oldPath(oldPath.equals(DiffEntry.DEV_NULL) ? Optional.empty() : Optional.of(oldPath))
        .newPath(newPath.equals(DiffEntry.DEV_NULL) ? Optional.empty() : Optional.of(newPath))
        .build();
  }

  private static Patch.ChangeType toChangeType(DiffEntry.ChangeType changeType) {
    if (!changeTypeMap.containsKey(changeType)) {
      throw new IllegalArgumentException("Unsupported type " + changeType);
    }
    return changeTypeMap.get(changeType);
  }
}
