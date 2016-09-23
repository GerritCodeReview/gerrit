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
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.ReplaceOp;
import com.google.gerrit.server.git.UpdateException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.TimeZone;

@Singleton
public class UpdateChangeByMerge implements
    RestModifyView<ChangeResource, MergeInput> {

  private final Provider<ReviewDb> db;
  private final GitRepositoryManager gitManager;
  private final TimeZone serverTimeZone;
  private final Provider<CurrentUser> user;
  private final ChangeJson.Factory jsonFactory;
  private final PatchSetUtil psUtil;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final MergeUtil.Factory mergeUtilFactory;
  private final SubmitType submitType;
  private final ChangeData.Factory changeDataFactory;
  private final ReplaceOp.Factory replaceOpFactory;
  private final BatchUpdate.Factory batchUpdateFactory;

  @Inject
  UpdateChangeByMerge(Provider<ReviewDb> db,
      GitRepositoryManager gitManager,
      @GerritPersonIdent PersonIdent myIdent,
      Provider<CurrentUser> user,
      ChangeJson.Factory json,
      PatchSetUtil psUtil,
      PatchSetInfoFactory patchSetInfoFactory,
      @GerritServerConfig Config config,
      MergeUtil.Factory mergeUtilFactory,
      ChangeData.Factory changeDataFactory,
      ReplaceOp.Factory replaceOpFactory,
      BatchUpdate.Factory batchUpdateFactory) {
    this.db = db;
    this.gitManager = gitManager;
    this.serverTimeZone = myIdent.getTimeZone();
    this.user = user;
    this.jsonFactory = json;
    this.psUtil = psUtil;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.submitType = config
        .getEnum("project", null, "submitType", SubmitType.MERGE_IF_NECESSARY);
    this.mergeUtilFactory = mergeUtilFactory;
    this.changeDataFactory = changeDataFactory;
    this.replaceOpFactory = replaceOpFactory;
    this.batchUpdateFactory = batchUpdateFactory;
  }

  @Override
  public Response<ChangeInfo> apply(ChangeResource req,
      MergeInput merge)
      throws NoSuchChangeException, OrmException, IOException,
      InvalidChangeOperationException,
      RestApiException, UpdateException {
    if (merge == null) {
      throw new BadRequestException("merge must be non-null");
    }
    ChangeControl ctl = req.getControl();
    if (!ctl.isVisible(db.get())) {
      throw new InvalidChangeOperationException(
          "Base change not found: " + req.getId());
    }
    PatchSet ps = psUtil.current(db.get(), ctl.getNotes());
    if (!ctl.canAddPatchSet(db.get())) {
      throw new AuthException("cannot upload patchset");
    }

    ChangeData changeData =
        changeDataFactory.create(db.get(), ctl.getChange());
    ProjectControl projectControl = ctl.getProjectControl();
    Project.NameKey project = ctl.getChange().getProject();
    Branch.NameKey dest = ctl.getChange().getDest();
    try (Repository git = gitManager.openRepository(project);
        ObjectInserter oi = git.newObjectInserter();
        RevWalk rw = new RevWalk(oi.newReader())) {
      ObjectId parentCommit = null;
      Ref destRef = git.getRefDatabase().exactRef(dest.get());
      if (destRef != null) {
        parentCommit = destRef.getObjectId();
      }
      RevCommit mergeTip = rw.parseCommit(parentCommit);

      Timestamp now = TimeUtil.nowTs();
      IdentifiedUser me = user.get().asIdentifiedUser();
      PersonIdent author = me.newCommitterIdent(now, serverTimeZone);

      // create a merge commit
      if (!(submitType.equals(SubmitType.MERGE_ALWAYS) ||
          submitType.equals(SubmitType.MERGE_IF_NECESSARY))) {
        throw new BadRequestException(
            "Submit type: " + submitType + " is not supported");
      }
      if (Strings.isNullOrEmpty(merge.source)) {
        throw new BadRequestException("merge.source must be non-empty");
      }

      RevCommit sourceCommit =
          MergeUtil.resolveCommit(git, rw, merge.source);
      if (!projectControl.canReadCommit(db.get(), git, sourceCommit)) {
        throw new BadRequestException(
            "do not have read permission for: " + merge.source);
      }

      MergeUtil mergeUtil =
          mergeUtilFactory.create(projectControl.getProjectState());
      // default merge strategy from project settings
      String mergeStrategy = MoreObjects.firstNonNull(
          Strings.emptyToNull(merge.strategy),
          mergeUtil.mergeStrategyName());

      RevCommit newCommit =
          MergeUtil.createMergeCommit(git, oi, mergeTip, sourceCommit,
              mergeStrategy, author, changeData.commitMessage(), rw);

      PatchSet.Id nextPsId = ChangeUtil.nextPatchSetId(ps.getId());
      PatchSetInfo nextPsInfo = patchSetInfoFactory.get(rw, newCommit, nextPsId);
      Ref priorPsRef = git.getRefDatabase().exactRef(ps.getRefName());
      ReplaceOp replaceOp = replaceOpFactory
          .create(projectControl, dest, false,
              ps.getId(), rw.parseCommit(priorPsRef.getObjectId()), nextPsId,
              newCommit, nextPsInfo, Collections.emptyList(), null, null);
      try (BatchUpdate bu = batchUpdateFactory.create(db.get(), project, me, now)) {
        bu.setRepository(git, rw, oi);
        bu.addOp(changeData.getId(), replaceOp);
        bu.execute();
      }
      ChangeJson json = jsonFactory.create(ChangeJson.NO_OPTIONS);
      return Response.ok(json.format(replaceOp.getChange()));
    } catch (IllegalArgumentException e) {
      throw new BadRequestException(e.getMessage());
    }
  }
}
