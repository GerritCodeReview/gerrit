// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.git.BatchUpdate;
import com.google.gerrit.server.notedb.PatchSetState;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

public class DraftChangeIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config allowDraftsDisabled() {
    return allowDraftsDisabledConfig();
  }

  @Inject private BatchUpdate.Factory updateFactory;

  @Test
  public void deleteDraftChange() throws Exception {
    assume().that(isAllowDrafts()).isTrue();
    PushOneCommit.Result result = createDraftChange();
    result.assertOkStatus();
    String changeId = result.getChangeId();
    String triplet = project.get() + "~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertThat(c.id).isEqualTo(triplet);
    assertThat(c.status).isEqualTo(ChangeStatus.DRAFT);
    deleteChange(changeId, adminRestSession).assertNoContent();

    exception.expect(ResourceNotFoundException.class);
    get(triplet);
  }

  @Test
  public void deleteDraftChangeOfAnotherUser() throws Exception {
    assume().that(isAllowDrafts()).isTrue();
    PushOneCommit.Result changeResult = createDraftChange();
    changeResult.assertOkStatus();
    String changeId = changeResult.getChangeId();
    Change.Id id = changeResult.getChange().getId();

    // The user needs to be able to see the draft change (which reviewers can).
    gApi.changes().id(changeId).addReviewer(user.fullName);

    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage(String.format("Deleting change %s is not permitted", id));
    gApi.changes().id(changeId).delete();
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void deleteDraftChangeWhenDraftsNotAllowedAsNormalUser() throws Exception {
    assume().that(isAllowDrafts()).isFalse();

    setApiUser(user);
    // We can't create a draft change while the draft workflow is disabled.
    // For this reason, we create a normal change and modify the database.
    PushOneCommit.Result changeResult =
        pushFactory.create(db, user.getIdent(), testRepo).to("refs/for/master");
    Change.Id id = changeResult.getChange().getId();
    markChangeAsDraft(id);
    setDraftStatusOfPatchSetsOfChange(id, true);

    String changeId = changeResult.getChangeId();
    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("Draft workflow is disabled");
    gApi.changes().id(changeId).delete();
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void deleteDraftChangeWhenDraftsNotAllowedAsAdmin() throws Exception {
    assume().that(isAllowDrafts()).isFalse();

    setApiUser(user);
    // We can't create a draft change while the draft workflow is disabled.
    // For this reason, we create a normal change and modify the database.
    PushOneCommit.Result changeResult =
        pushFactory.create(db, user.getIdent(), testRepo).to("refs/for/master");
    Change.Id id = changeResult.getChange().getId();
    markChangeAsDraft(id);
    setDraftStatusOfPatchSetsOfChange(id, true);

    String changeId = changeResult.getChangeId();

    // Grant those permissions to admins.
    grant(Permission.VIEW_DRAFTS, project, "refs/*");
    grant(Permission.DELETE_DRAFTS, project, "refs/*");

    try {
      setApiUser(admin);
      gApi.changes().id(changeId).delete();
    } finally {
      removePermission(Permission.DELETE_DRAFTS, project, "refs/*");
      removePermission(Permission.VIEW_DRAFTS, project, "refs/*");
    }

    setApiUser(user);
    assertThat(query(changeId)).isEmpty();
  }

  @Test
  public void deleteDraftChangeWithNonDraftPatchSet() throws Exception {
    assume().that(isAllowDrafts()).isTrue();

    PushOneCommit.Result changeResult = createDraftChange();
    Change.Id id = changeResult.getChange().getId();
    setDraftStatusOfPatchSetsOfChange(id, false);

    String changeId = changeResult.getChangeId();
    exception.expect(ResourceConflictException.class);
    exception.expectMessage(
        String.format("Cannot delete draft change %s: patch set 1 is not a draft", id));
    gApi.changes().id(changeId).delete();
  }

  @Test
  public void publishDraftChange() throws Exception {
    assume().that(isAllowDrafts()).isTrue();
    PushOneCommit.Result result = createDraftChange();
    result.assertOkStatus();
    String changeId = result.getChangeId();
    String triplet = project.get() + "~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertThat(c.id).isEqualTo(triplet);
    assertThat(c.status).isEqualTo(ChangeStatus.DRAFT);
    assertThat(c.revisions.get(c.currentRevision).draft).isTrue();
    publishChange(changeId).assertNoContent();
    c = get(triplet);
    assertThat(c.status).isEqualTo(ChangeStatus.NEW);
    assertThat(c.revisions.get(c.currentRevision).draft).isNull();
  }

  @Test
  public void publishDraftPatchSet() throws Exception {
    assume().that(isAllowDrafts()).isTrue();
    PushOneCommit.Result result = createDraftChange();
    result.assertOkStatus();
    String changeId = result.getChangeId();
    String triplet = project.get() + "~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertThat(c.id).isEqualTo(triplet);
    assertThat(c.status).isEqualTo(ChangeStatus.DRAFT);
    publishPatchSet(changeId).assertNoContent();
    assertThat(get(triplet).status).isEqualTo(ChangeStatus.NEW);
  }

  @Test
  public void createDraftChangeWhenDraftsNotAllowed() throws Exception {
    assume().that(isAllowDrafts()).isFalse();
    PushOneCommit.Result r = createDraftChange();
    r.assertErrorStatus("draft workflow is disabled");
  }

  @Test
  public void listApprovalsOnDraftChange() throws Exception {
    assume().that(isAllowDrafts()).isTrue();
    PushOneCommit.Result result = createDraftChange();
    result.assertOkStatus();
    String changeId = result.getChangeId();
    String triplet = project.get() + "~master~" + changeId;

    gApi.changes().id(triplet).addReviewer(user.fullName);

    ChangeInfo info = get(triplet);
    LabelInfo label = info.labels.get("Code-Review");
    assertThat(label.all).hasSize(1);
    assertThat(label.all.get(0)._accountId).isEqualTo(user.id.get());
    assertThat(label.all.get(0).value).isEqualTo(0);

    Collection<AccountInfo> ccs = info.reviewers.get(ReviewerState.REVIEWER);
    assertThat(ccs).hasSize(1);
    assertThat(ccs.iterator().next()._accountId).isEqualTo(user.id.get());

    setApiUser(user);
    gApi.changes().id(triplet).current().review(ReviewInput.recommend());
    setApiUser(admin);

    label = get(triplet).labels.get("Code-Review");
    assertThat(label.all).hasSize(1);
    assertThat(label.all.get(0)._accountId).isEqualTo(user.id.get());
    assertThat(label.all.get(0).value).isEqualTo(1);
  }

  private static RestResponse deleteChange(String changeId, RestSession s) throws Exception {
    return s.delete("/changes/" + changeId);
  }

  private RestResponse publishChange(String changeId) throws Exception {
    return adminRestSession.post("/changes/" + changeId + "/publish");
  }

  private RestResponse publishPatchSet(String changeId) throws Exception {
    PatchSet patchSet =
        Iterables.getOnlyElement(queryProvider.get().byKeyPrefix(changeId)).currentPatchSet();
    return adminRestSession.post(
        "/changes/" + changeId + "/revisions/" + patchSet.getRevision().get() + "/publish");
  }

  private void markChangeAsDraft(Change.Id id) throws Exception {
    try (BatchUpdate batchUpdate =
        updateFactory.create(db, project, atrScope.get().getUser(), TimeUtil.nowTs())) {
      batchUpdate.addOp(id, new MarkChangeAsDraftUpdateOp());
      batchUpdate.execute();
    }

    ChangeStatus changeStatus = gApi.changes().id(id.get()).get().status;
    assertThat(changeStatus).isEqualTo(ChangeStatus.DRAFT);
  }

  private void setDraftStatusOfPatchSetsOfChange(Change.Id id, boolean draftStatus)
      throws Exception {
    try (BatchUpdate batchUpdate =
        updateFactory.create(db, project, atrScope.get().getUser(), TimeUtil.nowTs())) {
      batchUpdate.addOp(id, new DraftStatusOfPatchSetsUpdateOp(draftStatus));
      batchUpdate.execute();
    }

    Boolean expectedDraftStatus = draftStatus ? Boolean.TRUE : null;
    List<Boolean> patchSetDraftStatuses = getPatchSetDraftStatuses(id);
    patchSetDraftStatuses.forEach(status -> assertThat(status).isEqualTo(expectedDraftStatus));
  }

  private List<Boolean> getPatchSetDraftStatuses(Change.Id id) throws Exception {
    Collection<RevisionInfo> revisionInfos =
        gApi.changes()
            .id(id.get())
            .get(EnumSet.of(ListChangesOption.ALL_REVISIONS))
            .revisions
            .values();
    return revisionInfos
        .stream()
        .map(revisionInfo -> revisionInfo.draft)
        .collect(Collectors.toList());
  }

  private class MarkChangeAsDraftUpdateOp extends BatchUpdate.Op {
    @Override
    public boolean updateChange(BatchUpdate.ChangeContext ctx) throws Exception {
      Change change = ctx.getChange();

      // Change status in database.
      change.setStatus(Change.Status.DRAFT);

      // Change status in NoteDb.
      PatchSet.Id currentPatchSetId = change.currentPatchSetId();
      ctx.getUpdate(currentPatchSetId).setStatus(Change.Status.DRAFT);

      return true;
    }
  }

  private class DraftStatusOfPatchSetsUpdateOp extends BatchUpdate.Op {
    private final boolean draftStatus;

    DraftStatusOfPatchSetsUpdateOp(boolean draftStatus) {
      this.draftStatus = draftStatus;
    }

    @Override
    public boolean updateChange(BatchUpdate.ChangeContext ctx) throws Exception {
      Collection<PatchSet> patchSets = psUtil.byChange(db, ctx.getNotes());

      // Change status in database.
      patchSets.forEach(patchSet -> patchSet.setDraft(draftStatus));
      db.patchSets().update(patchSets);

      // Change status in NoteDb.
      PatchSetState patchSetState = draftStatus ? PatchSetState.DRAFT : PatchSetState.PUBLISHED;
      patchSets
          .stream()
          .map(PatchSet::getId)
          .map(ctx::getUpdate)
          .forEach(changeUpdate -> changeUpdate.setPatchSetState(patchSetState));

      return true;
    }
  }
}
