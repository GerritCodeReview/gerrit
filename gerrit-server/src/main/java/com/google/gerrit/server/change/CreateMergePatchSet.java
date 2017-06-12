// Copyright (C) 2016 The Android Open Source Project
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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.gerrit.extensions.common.MergePatchSetInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MergeConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeIdenticalTreeException;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.sql.Timestamp;
import java.util.EnumSet;
import java.util.TimeZone;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.ChangeIdUtil;

@Singleton
public class CreateMergePatchSet implements RestModifyView<ChangeResource, MergePatchSetInput> {

  private final Provider<ReviewDb> db;
  private final GitRepositoryManager gitManager;
  private final TimeZone serverTimeZone;
  private final Provider<CurrentUser> user;
  private final ChangeJson.Factory jsonFactory;
  private final PatchSetUtil psUtil;
  private final MergeUtil.Factory mergeUtilFactory;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final PatchSetInserter.Factory patchSetInserterFactory;

  @Inject
  CreateMergePatchSet(
      Provider<ReviewDb> db,
      GitRepositoryManager gitManager,
      @GerritPersonIdent PersonIdent myIdent,
      Provider<CurrentUser> user,
      ChangeJson.Factory json,
      PatchSetUtil psUtil,
      MergeUtil.Factory mergeUtilFactory,
      BatchUpdate.Factory batchUpdateFactory,
      PatchSetInserter.Factory patchSetInserterFactory) {
    this.db = db;
    this.gitManager = gitManager;
    this.serverTimeZone = myIdent.getTimeZone();
    this.user = user;
    this.jsonFactory = json;
    this.psUtil = psUtil;
    this.mergeUtilFactory = mergeUtilFactory;
    this.batchUpdateFactory = batchUpdateFactory;
    this.patchSetInserterFactory = patchSetInserterFactory;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource req, MergePatchSetInput in)
      throws NoSuchChangeException, OrmException, IOException, InvalidChangeOperationException,
          RestApiException, UpdateException {
    if (in.merge == null) {
      throw new BadRequestException("merge field is required");
    }

    MergeInput merge = in.merge;
    if (Strings.isNullOrEmpty(merge.source)) {
      throw new BadRequestException("merge.source must be non-empty");
    }

    ChangeControl ctl = req.getControl();
    if (!ctl.isVisible(db.get())) {
      throw new InvalidChangeOperationException("Base change not found: " + req.getId());
    }
    PatchSet ps = psUtil.current(db.get(), ctl.getNotes());
    if (!ctl.canAddPatchSet(db.get())) {
      throw new AuthException("cannot add patch set");
    }

    ProjectControl projectControl = ctl.getProjectControl();
    Change change = ctl.getChange();
    Project.NameKey project = change.getProject();
    Branch.NameKey dest = change.getDest();
    try (Repository git = gitManager.openRepository(project);
        ObjectInserter oi = git.newObjectInserter();
        RevWalk rw = new RevWalk(oi.newReader())) {

      RevCommit sourceCommit = MergeUtil.resolveCommit(git, rw, merge.source);
      if (!projectControl.canReadCommit(db.get(), git, sourceCommit)) {
        throw new ResourceNotFoundException(
            "cannot find source commit: " + merge.source + " to merge.");
      }

      RevCommit currentPsCommit = rw.parseCommit(ObjectId.fromString(ps.getRevision().get()));

      Timestamp now = TimeUtil.nowTs();
      IdentifiedUser me = user.get().asIdentifiedUser();
      PersonIdent author = me.newCommitterIdent(now, serverTimeZone);

      RevCommit newCommit =
          createMergeCommit(
              in,
              projectControl,
              dest,
              git,
              oi,
              rw,
              currentPsCommit,
              sourceCommit,
              author,
              ObjectId.fromString(change.getKey().get().substring(1)));

      PatchSet.Id nextPsId = ChangeUtil.nextPatchSetId(ps.getId());
      PatchSetInserter psInserter = patchSetInserterFactory.create(ctl, nextPsId, newCommit);
      try (BatchUpdate bu = batchUpdateFactory.create(db.get(), project, me, now)) {
        bu.setRepository(git, rw, oi);
        bu.addOp(
            ctl.getId(),
            psInserter
                .setMessage("Uploaded patch set " + nextPsId.get() + ".")
                .setDraft(ps.isDraft())
                .setNotify(NotifyHandling.NONE));
        bu.execute();
      }

      ChangeJson json = jsonFactory.create(EnumSet.of(ListChangesOption.CURRENT_REVISION));
      return Response.ok(json.format(psInserter.getChange()));
    }
  }

  private RevCommit createMergeCommit(
      MergePatchSetInput in,
      ProjectControl projectControl,
      Branch.NameKey dest,
      Repository git,
      ObjectInserter oi,
      RevWalk rw,
      RevCommit currentPsCommit,
      RevCommit sourceCommit,
      PersonIdent author,
      ObjectId changeId)
      throws ResourceNotFoundException, MergeIdenticalTreeException, MergeConflictException,
          IOException {

    ObjectId parentCommit;
    if (in.inheritParent) {
      // inherit first parent from previous patch set
      parentCommit = currentPsCommit.getParent(0);
    } else {
      // get the current branch tip of destination branch
      Ref destRef = git.getRefDatabase().exactRef(dest.get());
      if (destRef != null) {
        parentCommit = destRef.getObjectId();
      } else {
        throw new ResourceNotFoundException("cannot find destination branch");
      }
    }
    RevCommit mergeTip = rw.parseCommit(parentCommit);

    String commitMsg;
    if (Strings.emptyToNull(in.subject) != null) {
      commitMsg = ChangeIdUtil.insertId(in.subject, changeId);
    } else {
      // reuse previous patch set commit message
      commitMsg = currentPsCommit.getFullMessage();
    }

    String mergeStrategy =
        MoreObjects.firstNonNull(
            Strings.emptyToNull(in.merge.strategy),
            mergeUtilFactory.create(projectControl.getProjectState()).mergeStrategyName());

    return MergeUtil.createMergeCommit(
        git, oi, mergeTip, sourceCommit, mergeStrategy, author, commitMsg, rw);
  }
}
