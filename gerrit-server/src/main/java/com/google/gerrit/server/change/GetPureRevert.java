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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
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
import org.kohsuke.args4j.Option;

public class GetPureRevert implements RestReadView<ChangeResource> {
  private final MergeUtil.Factory mergeUtilFactory;
  private final GitRepositoryManager repoManager;
  private final ProjectCache projectCache;
  private final ChangeNotes.Factory notesFactory;
  private final Provider<ReviewDb> dbProvider;
  private final PatchSetUtil psUtil;

  @Option(
    name = "--claimedOriginal",
    aliases = {"-o"},
    usage = "SHA1 (40 digit hex) of the original commit"
  )
  @Nullable
  String claimedOriginal;

  @Inject
  GetPureRevert(
      MergeUtil.Factory mergeUtilFactory,
      GitRepositoryManager repoManager,
      ProjectCache projectCache,
      ChangeNotes.Factory notesFactory,
      Provider<ReviewDb> dbProvider,
      PatchSetUtil psUtil) {
    this.mergeUtilFactory = mergeUtilFactory;
    this.repoManager = repoManager;
    this.projectCache = projectCache;
    this.notesFactory = notesFactory;
    this.dbProvider = dbProvider;
    this.psUtil = psUtil;
  }

  @Override
  public PureRevertInfo apply(ChangeResource rsrc)
      throws ResourceConflictException, IOException, BadRequestException, OrmException,
          AuthException {
    PatchSet currentPatchSet = psUtil.current(dbProvider.get(), rsrc.getNotes());
    if (currentPatchSet == null) {
      throw new ResourceConflictException("current revision is missing");
    } else if (!rsrc.getControl().isPatchVisible(currentPatchSet, dbProvider.get())) {
      throw new AuthException("current revision not accessible");
    }

    if (claimedOriginal == null) {
      if (rsrc.getChange().getRevertOf() == null) {
        throw new BadRequestException("no ID was provided and change isn't a revert");
      }
      PatchSet ps =
          psUtil.current(
              dbProvider.get(),
              notesFactory.createChecked(
                  dbProvider.get(), rsrc.getProject(), rsrc.getChange().getRevertOf()));
      claimedOriginal = ps.getRevision().get();
    }

    try (Repository repo = repoManager.openRepository(rsrc.getProject());
        ObjectInserter oi = repo.newObjectInserter();
        RevWalk rw = new RevWalk(repo)) {
      RevCommit claimedOriginalCommit;
      try {
        claimedOriginalCommit = rw.parseCommit(ObjectId.fromString(claimedOriginal));
      } catch (InvalidObjectIdException | MissingObjectException e) {
        throw new BadRequestException("invalid object ID");
      }
      RevCommit claimedRevertCommit =
          rw.parseCommit(ObjectId.fromString(currentPatchSet.getRevision().get()));

      // Rebase claimed revert onto claimed original
      ThreeWayMerger merger =
          mergeUtilFactory
              .create(projectCache.checkedGet(rsrc.getProject()))
              .newThreeWayMerger(oi, repo.getConfig());
      merger.setBase(claimedOriginalCommit);
      merger.merge(claimedOriginalCommit, claimedRevertCommit);
      if (merger.getResultTreeId() == null) {
        // Merge conflict during rebase
        return new PureRevertInfo(false);
      }

      // Any differences between claimed original's parent and the rebase result indicate that the
      // claimedRevert is not a pure revert but made content changes
      DiffFormatter df = new DiffFormatter(new ByteArrayOutputStream());
      df.setRepository(repo);
      List<DiffEntry> entries =
          df.scan(claimedOriginalCommit.getParent(0).getTree(), merger.getResultTreeId());
      return new PureRevertInfo(entries.isEmpty());
    }
  }

  public GetPureRevert setClaimedOriginal(String claimedOriginal) {
    this.claimedOriginal = claimedOriginal;
    return this;
  }
}
