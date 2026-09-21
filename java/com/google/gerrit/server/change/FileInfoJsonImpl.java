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

package com.google.gerrit.server.change;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.FilePathAdapter;
import com.google.gerrit.server.patch.PatchListNotAvailableException;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.lib.ObjectId;

/** Implementation of {@link FileInfoJson} using {@link DiffOperations}. */
public class FileInfoJsonImpl implements FileInfoJson {
  private final DiffOperations diffs;
  private final com.google.gerrit.server.git.GitRepositoryManager repoManager;

  @Inject
  FileInfoJsonImpl(
      DiffOperations diffOperations,
      com.google.gerrit.server.git.GitRepositoryManager repoManager) {
    this.diffs = diffOperations;
    this.repoManager = repoManager;
  }

  @Nullable
  @Override
  public Map<String, FileInfo> getFileInfoMapWithoutDiffStat(
      Change change,
      PatchSet patchSet,
      @Nullable org.eclipse.jgit.lib.Repository repo,
      @Nullable org.eclipse.jgit.revwalk.RevWalk rw)
      throws ResourceConflictException, PatchListNotAvailableException {
    try {
      if (repo != null && rw != null) {
        return computeFromTreeWalk(change.getProject(), patchSet.commitId(), rw);
      }
      try (org.eclipse.jgit.lib.Repository r = repoManager.openRepository(change.getProject());
          org.eclipse.jgit.revwalk.RevWalk walk = new org.eclipse.jgit.revwalk.RevWalk(r)) {
        return computeFromTreeWalk(change.getProject(), patchSet.commitId(), walk);
      }
    } catch (java.io.IOException | DiffNotAvailableException e) {
      convertException(
          e instanceof DiffNotAvailableException
              ? (DiffNotAvailableException) e
              : new DiffNotAvailableException(e));
      return null;
    }
  }

  private Map<String, FileInfo> computeFromTreeWalk(
      Project.NameKey project, ObjectId objectId, org.eclipse.jgit.revwalk.RevWalk rw)
      throws java.io.IOException,
          DiffNotAvailableException,
          ResourceConflictException,
          PatchListNotAvailableException {
    org.eclipse.jgit.revwalk.RevCommit newCommit = rw.parseCommit(objectId);
    if (newCommit.getParentCount() > 1) {
      return getFileInfoMap(project, objectId, 0);
    }
    ObjectId oldCommit =
        newCommit.getParentCount() == 1 ? newCommit.getParent(0).getId() : ObjectId.zeroId();
    com.google.common.collect.ImmutableList<com.google.gerrit.server.patch.gitdiff.ModifiedFile>
        modifiedFiles = diffs.getModifiedFilesCached(project, oldCommit, objectId);
    org.eclipse.jgit.revwalk.RevTree aTree =
        oldCommit.equals(ObjectId.zeroId()) ? null : rw.parseTree(oldCommit);
    org.eclipse.jgit.revwalk.RevTree bTree = rw.parseTree(newCommit);
    Map<String, ObjectId> oldShas = new HashMap<>();
    Map<String, Integer> oldModes = new HashMap<>();
    Map<String, ObjectId> newShas = new HashMap<>();
    Map<String, Integer> newModes = new HashMap<>();
    java.util.Set<String> allPaths = new java.util.HashSet<>();
    for (com.google.gerrit.server.patch.gitdiff.ModifiedFile mf : modifiedFiles) {
      mf.oldPath().ifPresent(allPaths::add);
      mf.newPath().ifPresent(allPaths::add);
    }
    if (!allPaths.isEmpty()) {
      try (org.eclipse.jgit.treewalk.TreeWalk tw =
          new org.eclipse.jgit.treewalk.TreeWalk(rw.getObjectReader())) {
        tw.setRecursive(true);
        tw.setFilter(org.eclipse.jgit.treewalk.filter.PathFilterGroup.createFromStrings(allPaths));
        int aIdx = aTree != null ? tw.addTree(aTree) : -1;
        int bIdx = tw.addTree(bTree);
        while (tw.next()) {
          String path = tw.getPathString();
          if (aIdx >= 0 && !tw.getFileMode(aIdx).equals(org.eclipse.jgit.lib.FileMode.MISSING)) {
            oldShas.put(path, tw.getObjectId(aIdx));
            oldModes.put(path, tw.getRawMode(aIdx));
          }
          if (!tw.getFileMode(bIdx).equals(org.eclipse.jgit.lib.FileMode.MISSING)) {
            newShas.put(path, tw.getObjectId(bIdx));
            newModes.put(path, tw.getRawMode(bIdx));
          }
        }
      }
    }
    Map<String, FileInfo> result = new HashMap<>();
    for (com.google.gerrit.server.patch.gitdiff.ModifiedFile mf : modifiedFiles) {
      String path = mf.getDefaultPath();
      if (path.equals(Patch.COMMIT_MSG) || path.equals(Patch.MERGE_LIST)) {
        continue;
      }
      FileInfo fileInfo = new FileInfo();
      fileInfo.status =
          mf.changeType() != Patch.ChangeType.MODIFIED ? mf.changeType().getCode() : null;
      fileInfo.oldPath = FilePathAdapter.getOldPath(mf.oldPath(), mf.changeType());
      if (mf.oldPath().isPresent()) {
        ObjectId oSha = oldShas.get(mf.oldPath().get());
        if (oSha != null && !oSha.equals(ObjectId.zeroId())) {
          fileInfo.oldSha = oSha.name();
        }
        fileInfo.oldMode = oldModes.get(mf.oldPath().get());
      }
      if (mf.newPath().isPresent()) {
        ObjectId nSha = newShas.get(mf.newPath().get());
        if (nSha != null && !nSha.equals(ObjectId.zeroId())) {
          fileInfo.newSha = nSha.name();
        }
        fileInfo.newMode = newModes.get(mf.newPath().get());
      }
      result.put(path, fileInfo);
    }
    return result;
  }

  @Nullable
  @Override
  public Map<String, FileInfo> getFileInfoMap(
      Change change, ObjectId objectId, @Nullable PatchSet base)
      throws ResourceConflictException, PatchListNotAvailableException {
    try {
      if (base == null) {
        // Setting parentNum=0 requests the default parent, which is the only parent for
        // single-parent commits, or the auto-merge otherwise
        return asFileInfo(
            diffs.listModifiedFilesAgainstParent(
                change.getProject(), objectId, /* parentNum= */ 0, DiffOptions.DEFAULTS));
      }
      return asFileInfo(
          diffs.listModifiedFiles(
              change.getProject(), base.commitId(), objectId, DiffOptions.DEFAULTS));
    } catch (DiffNotAvailableException e) {
      convertException(e);
      return null; // unreachable. handleAndThrow will throw an exception anyway
    }
  }

  @Nullable
  @Override
  public Map<String, FileInfo> getFileInfoMap(
      Project.NameKey project, ObjectId objectId, int parent)
      throws ResourceConflictException, PatchListNotAvailableException {
    try {
      Map<String, FileDiffOutput> modifiedFiles =
          diffs.listModifiedFilesAgainstParent(project, objectId, parent, DiffOptions.DEFAULTS);
      return asFileInfo(modifiedFiles);
    } catch (DiffNotAvailableException e) {
      convertException(e);
      return null; // unreachable. handleAndThrow will throw an exception anyway
    }
  }

  private void convertException(DiffNotAvailableException e)
      throws ResourceConflictException, PatchListNotAvailableException {
    Throwable cause = e.getCause();
    if (cause != null && !(cause instanceof NoMergeBaseException)) {
      cause = cause.getCause();
    }
    if (cause instanceof NoMergeBaseException) {
      throw new ResourceConflictException(
          String.format("Cannot create auto merge commit: %s", e.getMessage()), e);
    }
    throw new PatchListNotAvailableException(e);
  }

  private Map<String, FileInfo> asFileInfo(Map<String, FileDiffOutput> fileDiffs) {
    Map<String, FileInfo> result = new HashMap<>();
    for (String path : fileDiffs.keySet()) {
      FileDiffOutput fileDiff = fileDiffs.get(path);
      FileInfo fileInfo = new FileInfo();
      if (fileDiff.isNegative() || fileDiff.isTooExpensive()) {
        fileInfo.diffsTooExpensiveToCompute = true;
        fileInfo.status =
            fileDiff.changeType() != Patch.ChangeType.MODIFIED
                ? fileDiff.changeType().getCode()
                : null;
        result.put(path, fileInfo);
        continue;
      }
      fileInfo.status =
          fileDiff.changeType() != Patch.ChangeType.MODIFIED
              ? fileDiff.changeType().getCode()
              : null;
      fileInfo.oldPath = FilePathAdapter.getOldPath(fileDiff.oldPath(), fileDiff.changeType());
      fileInfo.sizeDelta = fileDiff.sizeDelta();
      fileInfo.size = fileDiff.size();
      fileInfo.oldMode =
          fileDiff.oldMode().isPresent() && !fileDiff.oldMode().get().equals(Patch.FileMode.MISSING)
              ? fileDiff.oldMode().get().getMode()
              : null;
      fileInfo.newMode =
          fileDiff.newMode().isPresent() && !fileDiff.newMode().get().equals(Patch.FileMode.MISSING)
              ? fileDiff.newMode().get().getMode()
              : null;
      fileDiff.oldSha().ifPresent(sha -> fileInfo.oldSha = sha.name());
      fileDiff.newSha().ifPresent(sha -> fileInfo.newSha = sha.name());

      if (fileDiff.patchType().get() == Patch.PatchType.BINARY) {
        fileInfo.binary = true;
      } else {
        fileInfo.linesInserted = fileDiff.insertions() > 0 ? fileDiff.insertions() : null;
        fileInfo.linesDeleted = fileDiff.deletions() > 0 ? fileDiff.deletions() : null;
      }
      result.put(path, fileInfo);
    }
    return result;
  }
}
