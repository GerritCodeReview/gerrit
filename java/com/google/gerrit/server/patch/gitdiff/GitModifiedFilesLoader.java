//  Copyright (C) 2021 The Android Open Source Project
//
//  Licensed under the Apache License, Version 2.0 (the "License");
//  you may not use this file except in compliance with the License.
//  You may obtain a copy of the License at
//
//  http://www.apache.org/licenses/LICENSE-2.0
//
//  Unless required by applicable law or agreed to in writing, software
//  distributed under the License is distributed on an "AS IS" BASIS,
//  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
//  See the License for the specific language governing permissions and
//  limitations under the License.

package com.google.gerrit.server.patch.gitdiff;

import static com.google.common.collect.ImmutableList.toImmutableList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Patch;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.util.io.DisabledOutputStream;

@Singleton
/**
 * Loader for the {@link GitModifiedFilesCache} but could also be used to skip the cache and load
 * modified files directly from git.
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

  public static ImmutableList<ModifiedFile> getModifiedFiles(
      Config repoConfig, ObjectReader reader, GitModifiedFilesCacheKey key) throws IOException {
    List<DiffEntry> entries = GitModifiedFilesLoader.getGitTreeDiff(repoConfig, reader, key);
    return entries.stream().map(GitModifiedFilesLoader::toModifiedFile).collect(toImmutableList());
  }

  private static List<DiffEntry> getGitTreeDiff(
      Config repoConfig, ObjectReader reader, GitModifiedFilesCacheKey key) throws IOException {
    try (DiffFormatter df = new DiffFormatter(DisabledOutputStream.INSTANCE)) {
      df.setReader(reader, repoConfig);
      if (key.renameDetection()) {
        df.setDetectRenames(true);
        df.getRenameDetector().setRenameScore(key.renameScore());
      }
      // The scan method only returns the file paths that are different. Callers may choose to
      // format these paths themselves.
      return df.scan(key.aTree().equals(ObjectId.zeroId()) ? null : key.aTree(), key.bTree());
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
