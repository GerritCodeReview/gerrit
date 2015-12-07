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
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gwtorm.server.OrmException;

import org.eclipse.jgit.lib.Config;
import org.junit.Test;

import java.io.IOException;

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
    RestResponse response = deleteChange(changeId, adminSession);
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
    deleteChange(changeId, adminSession).assertNoContent();

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

  private PushOneCommit.Result createDraftChange() throws Exception {
    return pushTo("refs/drafts/master");
  }

  private static RestResponse deleteChange(String changeId,
      RestSession s) throws IOException {
    return s.delete("/changes/" + changeId);
  }

  private RestResponse publishChange(String changeId) throws IOException {
    return adminSession.post("/changes/" + changeId + "/publish");
  }

  private RestResponse publishPatchSet(String changeId) throws IOException,
      OrmException {
    PatchSet patchSet = Iterables.getOnlyElement(
        queryProvider.get().byKeyPrefix(changeId)).currentPatchSet();
    return adminSession.post("/changes/"
        + changeId
        + "/revisions/"
        + patchSet.getRevision().get()
        + "/publish");
  }
}
