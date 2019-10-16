// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.extensions.conditions.BooleanCondition.and;
import static com.google.gerrit.server.permissions.RefPermission.CREATE_CHANGE;
import static java.util.Objects.requireNonNull;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.api.changes.RevertInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryingRestModifyView;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.apache.commons.lang.RandomStringUtils;

@Singleton
public class RevertSubmission
    extends RetryingRestModifyView<ChangeResource, RevertInput, List<ChangeInfo>>
    implements UiAction<ChangeResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final Revert revert;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeResource.Factory changeResourceFactory;
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final PatchSetUtil psUtil;
  private final ContributorAgreementsChecker contributorAgreements;

  @Inject
  RevertSubmission(
      RetryHelper retryHelper,
      Revert revert,
      Provider<InternalChangeQuery> queryProvider,
      ChangeResource.Factory changeResourceFactory,
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      PatchSetUtil psUtil,
      ContributorAgreementsChecker contributorAgreements) {
    super(retryHelper);
    this.revert = revert;
    this.queryProvider = queryProvider;
    this.changeResourceFactory = changeResourceFactory;
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.psUtil = psUtil;
    this.contributorAgreements = contributorAgreements;
  }

  @Override
  public Response<List<ChangeInfo>> applyImpl(
      BatchUpdate.Factory updateFactory, ChangeResource changeResource, RevertInput input)
      throws Exception {

    if (!changeResource.getChange().isMerged()) {
      throw new ResourceConflictException(
          String.format("change is %s.", ChangeUtil.status(changeResource.getChange())));
    }

    String submissionId =
        requireNonNull(
            changeResource.getChange().getSubmissionId(),
            String.format("merged change %s has no submission ID", changeResource.getId()));

    List<ChangeData> changeDatas = queryProvider.get().bySubmissionId(submissionId);

    for (ChangeData changeData : changeDatas) {
      Change change = changeData.change();
      contributorAgreements.check(change.getProject(), changeResource.getUser());
      permissionBackend.currentUser().ref(change.getDest()).check(CREATE_CHANGE);
      permissionBackend.currentUser().change(changeData).check(ChangePermission.READ);
      projectCache.checkedGet(change.getProject()).checkStatePermitsWrite();

      requireNonNull(
          psUtil.get(changeData.notes(), change.currentPatchSetId()),
          String.format(
              "current patch set %s of change %s not found",
              change.currentPatchSetId(), change.currentPatchSetId()));
    }
    return Response.ok(revertSubmission(changeDatas, input, submissionId));
  }

  private List<ChangeInfo> revertSubmission(
      List<ChangeData> changeDatas, RevertInput input, String submissionId) throws Exception {
    List<ChangeInfo> results;
    results = new ArrayList<>();
    if (input.topic == null) {
      input.topic =
          String.format(
              "revert-%s-%s", submissionId, RandomStringUtils.randomAlphabetic(10).toUpperCase());
    }
    for (ChangeData changeData : changeDatas) {
      ChangeResource change = changeResourceFactory.create(changeData.notes(), user.get());
      results.add(revert.apply(change, input).value());
    }
    return results;
  }

  @Override
  public Description getDescription(ChangeResource rsrc) {
    Change change = rsrc.getChange();
    boolean projectStatePermitsWrite = false;
    try {
      projectStatePermitsWrite = projectCache.checkedGet(rsrc.getProject()).statePermitsWrite();
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Failed to check if project state permits write: %s", rsrc.getProject());
    }
    return new UiAction.Description()
        .setLabel(
            "Revert this change and all changes that have been submitted together with this change")
        .setTitle("Revert submission")
        .setVisible(
            and(
                projectStatePermitsWrite,
                permissionBackend
                    .user(rsrc.getUser())
                    .ref(change.getDest())
                    .testCond(CREATE_CHANGE)));
  }
}
