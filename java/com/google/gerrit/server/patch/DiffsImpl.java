//  Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import com.google.common.collect.ImmutableMap;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.patch.entities.GitFileDiff;
import com.google.gerrit.server.patch.entities.GitModifiedFile;
import com.google.inject.Inject;
import com.google.inject.Module;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;
import org.eclipse.jgit.diff.DiffEntry.ChangeType;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;

public class DiffsImpl implements Diffs {
  private final ModifiedFilesCache modifiedFilesCache;
  private final FileDiffCache fileDiffCache;
  private final DiffUtil diffUtil;

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        bind(Diffs.class).to(DiffsImpl.class);
        install(GitModifiedFilesCache.module());
        install(ModifiedFilesCache.module());
        install(GitFileDiffCache.module());
        install(FileDiffCache.module());
      }
    };
  }

  @Inject
  public DiffsImpl(
      ModifiedFilesCache modifiedFilesCache, FileDiffCache fileDiffCache, DiffUtil diffUtil) {
    this.modifiedFilesCache = modifiedFilesCache;
    this.fileDiffCache = fileDiffCache;
    this.diffUtil = diffUtil;
  }

  /**
   * Returns the list of added, deleted or modified files between 2 commits (patchsets). The commit
   * message and merge list (for merge commits) are also returned.
   *
   * @param project a project name representing a git repository
   * @param key the key containing information about the 2 commits and other attributes
   * @return the list of modified files between the 2 commits.
   * @throws PatchListNotAvailableException
   */
  @Override
  public Map<String, FileInfo> getModifiedFilesIn(Project.NameKey project, PatchListKey key)
      throws PatchListNotAvailableException {
    Map<String, FileInfo> files = new TreeMap<>();
    try {
      RevObject aCommit = diffUtil.getBaseCommit(project, key);
      RevCommit bCommit = diffUtil.getRevCommit(project, key.getNewId());
      ComparisonType cmp = diffUtil.getComparisonType(key, aCommit, bCommit);

      // TODO(ghareeb): pass renameDetectionFlag and renameScore as parameters
      ModifiedFilesCache.Key all =
          ModifiedFilesCache.Key.create(
              project,
              aCommit,
              bCommit,
              Whitespace.IGNORE_NONE,
              true,
              60,
              (aCommit instanceof RevCommit) ? true : false);

      List<GitModifiedFile> modifiedFilesEntities = modifiedFilesCache.get(all).gitModifiedFiles();
      List<FileDiffCache.Key> keys = new ArrayList<>();
      Map<FileDiffCache.Key, GitModifiedFile> m = new HashMap<>();
      for (GitModifiedFile entity : modifiedFilesEntities) {
        // TODO(ghareeb): I check on aCommit being an instance of RevCommit because the test
        // ChangeIt#createEmptyChangeOnNonExistingBranch}
        // and other tests failed because aCommit evaluates in an empty tree here.
        // Revisit and implement a better handling for this case.
        if (aCommit instanceof RevCommit) {
           FileDiffCache.Key fileDiffCacheKey = FileDiffCache.Key.create(
              project,
              aCommit,
              bCommit,
              entity.newPath(),
              null,
              null,
              Whitespace.IGNORE_NONE,
              cmp);
          keys.add(fileDiffCacheKey);
          m.put(fileDiffCacheKey, entity);
        } else {
          FileInfo fileInfo = new FileInfo();
          fileInfo.oldPath = entity.oldPath();
          if (entity.changeType() == ChangeType.DELETE) {
            files.put(entity.oldPath(), fileInfo);
          } else {
            files.put(entity.newPath(), fileInfo);
          }
        }
      }

      ImmutableMap<FileDiffCache.Key, PatchListEntry> allValues = fileDiffCache.getAll(keys);

      for (Map.Entry<FileDiffCache.Key, PatchListEntry> entry : allValues.entrySet()) {
        PatchListEntry ple = entry.getValue();
        if (ple.isEmpty()) {
          continue;
        }
        FileInfo fileInfo = getFileInfo(ple);
        GitModifiedFile entity = m.get(entry.getKey());
        if (entity.changeType() == ChangeType.DELETE) {
          files.put(entity.oldPath(), fileInfo);
        } else {
          files.put(entity.newPath(), fileInfo);
        }
      }
    } catch (IOException | ExecutionException e) {
      // TODO(ghareeb): Add more handling here
      throw new PatchListNotAvailableException(e);
    }
    return files;
  }

  @Override
  public PatchListEntry getOneModifiedFile(
      Project.NameKey project, PatchListKey key, String fileName) throws IOException {
    try {
      RevObject aCommit = diffUtil.getBaseCommit(project, key);
      RevCommit bCommit = diffUtil.getRevCommit(project, key.getNewId());
      ComparisonType cmp = diffUtil.getComparisonType(key, aCommit, bCommit);

      // TODO(ghareeb): Add parameters for renameDetection and renameScore
      ModifiedFilesCache.Key allKey =
          ModifiedFilesCache.Key.create(
              project, aCommit.getId(), bCommit.getId(), key.getWhitespace(), true, 60, true);

      List<GitModifiedFile> modifiedFilesEntities =
          modifiedFilesCache.get(allKey).gitModifiedFiles();

      modifiedFilesEntities =
          modifiedFilesEntities.stream()
              .filter(
                  entry ->
                      entry.changeType() == ChangeType.DELETE
                          ? entry.oldPath().equals(fileName)
                          : entry.newPath().equals(fileName))
              .collect(Collectors.toList());

      if (modifiedFilesEntities.isEmpty()) {
        return PatchListEntry.empty(fileName);
      }
      // TODO(ghareeb): assign similarityLevel and diffAlgorithm
      FileDiffCache.Key diffKey =
          FileDiffCache.Key.create(
              project,
              aCommit.getId(),
              bCommit.getId(),
              modifiedFilesEntities.get(0).newPath(),
              null,
              null,
              key.getWhitespace(),
              cmp);
      return fileDiffCache.get(diffKey);
    } catch (ExecutionException e) {
      // TODO(ghareeb): handle all exceptions
      e.printStackTrace();
      throw new RuntimeException("EXECUTION EXCEPTION");
    }
  }

  private Optional getFileDiff(
      Project.NameKey project,
      RevObject aCommit,
      RevCommit bCommit,
      ComparisonType cmp,
      GitModifiedFile entity)
      throws ExecutionException {
    FileDiffCache.Key key =
        FileDiffCache.Key.create(
            project, aCommit, bCommit, entity.newPath(), null, null, Whitespace.IGNORE_NONE, cmp);
    PatchListEntry ple = fileDiffCache.get(key);
    if (ple.isEmpty()) {
      return Optional.empty();
    }
    return Optional.of(ple);
  }

  private FileInfo getFileInfo(PatchListEntry e) {
    FileInfo d = new FileInfo();
    d.status = e.getChangeType() != Patch.ChangeType.MODIFIED ? e.getChangeType().getCode() : null;
    d.oldPath = e.getOldName();
    d.sizeDelta = e.getSizeDelta();
    d.size = e.getSize();
    if (e.getPatchType() == Patch.PatchType.BINARY) {
      d.binary = true;
    } else {
      d.linesInserted = e.getInsertions() > 0 ? e.getInsertions() : null;
      d.linesDeleted = e.getDeletions() > 0 ? e.getDeletions() : null;
    }
    return d;
  }
}
