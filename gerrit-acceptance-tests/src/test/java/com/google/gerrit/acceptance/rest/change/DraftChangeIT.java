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
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.NoteDbMode;

import org.eclipse.jgit.lib.Config;
import org.junit.Test;

import java.util.Collection;

public class DraftChangeIT extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config allowDraftsDisabled() {
    return allowDraftsDisabledConfig();
  }

  @Test
  public void deleteChange() throws Exception {
    PushOneCommit.Result result = createChange();
    result.assertOkStatus();
    String changeId = result.getChangeId();
    String triplet = project.get() + "~master~" + changeId;
    ChangeInfo c = get(triplet);
    assertThat(c.id).isEqualTo(triplet);
    assertThat(c.status).isEqualTo(ChangeStatus.NEW);
    RestResponse response = deleteChange(changeId, adminRestSession);
    assertThat(response.getEntityContent())
        .isEqualTo("Change is not a draft: " + c._number);
    response.assertConflict();
  }

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

    ReviewerState rs = NoteDbMode.readWrite()
        ? ReviewerState.REVIEWER : ReviewerState.CC;
    Collection<AccountInfo> ccs = info.reviewers.get(rs);
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

  private static RestResponse deleteChange(String changeId,
      RestSession s) throws Exception {
    return s.delete("/changes/" + changeId);
  }

  private RestResponse publishChange(String changeId) throws Exception {
    return adminRestSession.post("/changes/" + changeId + "/publish");
  }

  private RestResponse publishPatchSet(String changeId) throws Exception {
    PatchSet patchSet = Iterables.getOnlyElement(
        queryProvider.get().byKeyPrefix(changeId)).currentPatchSet();
    return adminRestSession.post("/changes/"
        + changeId
        + "/revisions/"
        + patchSet.getRevision().get()
        + "/publish");
  }
}
