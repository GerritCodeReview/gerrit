// Copyright (C) 2017 The Android Open Source Project
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
import com.google.gerrit.extensions.common.PureRevertInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.OrmException;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ProjectCache;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.diff.DiffEntry;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.ThreeWayMerger;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class PureRevert {
  private final MergeUtil.Factory mergeUtilFactory;
  private final GitRepositoryManager repoManager;
  private final ProjectCache projectCache;
  private final ChangeNotes.Factory notesFactory;
  private final PatchSetUtil psUtil;

  @Inject
  PureRevert(
      MergeUtil.Factory mergeUtilFactory,
      GitRepositoryManager repoManager,
      ProjectCache projectCache,
      ChangeNotes.Factory notesFactory,
      PatchSetUtil psUtil) {
    this.mergeUtilFactory = mergeUtilFactory;
    this.repoManager = repoManager;
    this.projectCache = projectCache;
    this.notesFactory = notesFactory;
    this.psUtil = psUtil;
  }

  public PureRevertInfo get(ChangeNotes notes, @Nullable String claimedOriginal)
      throws OrmException, IOException, BadRequestException, ResourceConflictException {
    PatchSet currentPatchSet = psUtil.current(notes);
    if (currentPatchSet == null) {
      throw new ResourceConflictException("current revision is missing");
    }

    if (claimedOriginal == null) {
      if (notes.getChange().getRevertOf() == null) {
        throw new BadRequestException("no ID was provided and change isn't a revert");
      }
      PatchSet ps =
          psUtil.current(
              notesFactory.createChecked(notes.getProjectName(), notes.getChange().getRevertOf()));
      claimedOriginal = ps.getRevision().get();
    }

    try (Repository repo = repoManager.openRepository(notes.getProjectName());
        ObjectInserter oi = repo.newObjectInserter();
        RevWalk rw = new RevWalk(repo)) {
      RevCommit claimedOriginalCommit;
      try {
        claimedOriginalCommit = rw.parseCommit(ObjectId.fromString(claimedOriginal));
      } catch (InvalidObjectIdException | MissingObjectException e) {
        throw new BadRequestException("invalid object ID");
      }
      if (claimedOriginalCommit.getParentCount() == 0) {
        throw new BadRequestException("can't check against initial commit");
      }
      RevCommit claimedRevertCommit =
          rw.parseCommit(ObjectId.fromString(currentPatchSet.getRevision().get()));
      if (claimedRevertCommit.getParentCount() == 0) {
        throw new BadRequestException("claimed revert has no parents");
      }
      // Rebase claimed revert onto claimed original
      ThreeWayMerger merger =
          mergeUtilFactory
              .create(projectCache.checkedGet(notes.getProjectName()))
              .newThreeWayMerger(oi, repo.getConfig());
      merger.setBase(claimedRevertCommit.getParent(0));
      boolean success = merger.merge(claimedRevertCommit, claimedOriginalCommit);
      if (!success || merger.getResultTreeId() == null) {
        // Merge conflict during rebase
        return new PureRevertInfo(false);
      }

      // Any differences between claimed original's parent and the rebase result indicate that the
      // claimedRevert is not a pure revert but made content changes
      try (DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream())) {
        df.setReader(oi.newReader(), repo.getConfig());
        List<DiffEntry> entries =
            df.scan(claimedOriginalCommit.getParent(0), merger.getResultTreeId());
        return new PureRevertInfo(entries.isEmpty());
      }
    }
  }
}
