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

package com.google.gerrit.acceptance.rest.account;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.server.group.SystemGroupBackend.CHANGE_OWNER;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.Util;

import org.junit.Before;
import org.junit.Test;

public class ImpersonationIT extends AbstractDaemonTest {
  private TestAccount admin2;

  @Before
  public void setUp() throws Exception {
    admin2 = accounts.admin2();
  }

  @Test
  public void voteOnBehalfOf() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    LabelType codeReviewType = Util.codeReview();
    String forCodeReviewAs = Permission.forLabelAs(codeReviewType.getName());
    String heads = "refs/heads/*";
    AccountGroup.UUID owner =
        SystemGroupBackend.getGroup(CHANGE_OWNER).getUUID();
    Util.allow(cfg, forCodeReviewAs, -1, 1, owner, heads);
    saveProjectConfig(project, cfg);

    PushOneCommit.Result r = createChange();
    RevisionApi revision = gApi.changes()
        .id(r.getChangeId())
        .current();

    ReviewInput in = ReviewInput.recommend();
    in.onBehalfOf = user.id.toString();
    revision.review(in);

    ChangeInfo c = gApi.changes()
        .id(r.getChangeId())
        .get();

    LabelInfo codeReview = c.labels.get("Code-Review");
    assertThat(codeReview.all).hasSize(1);
    ApprovalInfo approval = codeReview.all.get(0);
    assertThat(approval._accountId).isEqualTo(user.id.get());
    assertThat(approval.value).isEqualTo(1);
  }

  private void allowSubmitOnBehalfOf() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    Util.allow(cfg,
        Permission.SUBMIT_AS,
        SystemGroupBackend.getGroup(REGISTERED_USERS).getUUID(),
        "refs/heads/*");
    saveProjectConfig(project, cfg);
  }

  @Test
  public void submitOnBehalfOf() throws Exception {
    allowSubmitOnBehalfOf();
    PushOneCommit.Result r = createChange();
    String changeId = project.get() + "~master~" + r.getChangeId();
    gApi.changes()
        .id(changeId)
        .current()
        .review(ReviewInput.approve());
    SubmitInput in = new SubmitInput();
    in.onBehalfOf = admin2.email;
    gApi.changes()
        .id(changeId)
        .current()
        .submit(in);
    assertThat(gApi.changes().id(changeId).get().status)
        .isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void submitOnBehalfOfInvalidUser() throws Exception {
    allowSubmitOnBehalfOf();
    PushOneCommit.Result r = createChange();
    String changeId = project.get() + "~master~" + r.getChangeId();
    gApi.changes()
        .id(changeId)
        .current()
        .review(ReviewInput.approve());
    SubmitInput in = new SubmitInput();
    in.onBehalfOf = "doesnotexist";
    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage("Account Not Found: doesnotexist");
    gApi.changes()
        .id(changeId)
        .current()
        .submit(in);
  }

  @Test
  public void submitOnBehalfOfNotPermitted() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(project.get() + "~master~" + r.getChangeId())
        .current()
        .review(ReviewInput.approve());
    SubmitInput in = new SubmitInput();
    in.onBehalfOf = admin2.email;
    exception.expect(AuthException.class);
    exception.expectMessage("submit on behalf of not permitted");
    gApi.changes()
        .id(project.get() + "~master~" + r.getChangeId())
        .current()
        .submit(in);
  }
}
