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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInfoDifference;
import com.google.inject.Inject;
import java.util.Collection;
import org.junit.Test;

public class GetMetaDiffIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  private static final String UNSAVED_REV_ID = "0000000000000000000000000000000000000001";
  private static final String TOPIC = "topic";
  private static final String HASHTAG = "hashtag";

  @Test
  public void metaDiff() throws Exception {
    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).topic(TOPIC);
    ChangeInfo oldInfo = gApi.changes().id(changeId).get();
    gApi.changes().id(changeId).topic(TOPIC + "-2");
    gApi.changes().id(changeId).setHashtags(new HashtagsInput(ImmutableSet.of(HASHTAG)));
    ChangeInfo newInfo = gApi.changes().id(changeId).get();

    ChangeInfoDifference difference =
        gApi.changes().id(changeId).metaDiff(oldInfo.metaRevId, newInfo.metaRevId);

    assertThat(difference.added().topic).isEqualTo(newInfo.topic);
    assertThat(difference.added().hashtags).isNotNull();
    assertThat(difference.added().hashtags).containsExactly(HASHTAG);
    assertThat(difference.removed().topic).isEqualTo(oldInfo.topic);
    assertThat(difference.removed().hashtags).isNull();
  }

  @Test
  public void metaDiffSubmitReq() throws Exception {
    PushOneCommit.Result ch = createChange();
    ChangeApi chApi = gApi.changes().id(ch.getChangeId());
    ChangeInfo oldInfo = chApi.get();
    chApi.setHashtags(new HashtagsInput(ImmutableSet.of(HASHTAG)));
    ChangeInfo newInfo = chApi.get();

    ChangeInfoDifference difference =
        chApi.metaDiff(oldInfo.metaRevId, newInfo.metaRevId, ListChangesOption.SUBMIT_REQUIREMENTS);

    assertThat(difference.added().submitRequirements).isNull();
    assertThat(difference.removed().submitRequirements).isNull();
  }

  @Test
  public void metaDiffReturnsSuccessful() throws Exception {
    PushOneCommit.Result ch = createChange();
    ChangeInfo info = gApi.changes().id(ch.getChangeId()).get();

    RestResponse resp =
        adminRestSession.get("/changes/" + ch.getChangeId() + "/meta_diff/?meta=" + info.metaRevId);

    resp.assertOK();
  }

  @Test
  public void metaDiffUnreachableNewSha1() throws Exception {
    PushOneCommit.Result ch1 = createChange();
    PushOneCommit.Result ch2 = createChange();

    ChangeInfo info2 = gApi.changes().id(ch2.getChangeId()).get();

    RestResponse resp =
        adminRestSession.get(
            "/changes/" + ch1.getChangeId() + "/meta_diff/?meta=" + info2.metaRevId);

    resp.assertStatus(412);
  }

  @Test
  public void metaDiffInvalidNewSha1() throws Exception {
    PushOneCommit.Result ch = createChange();

    RestResponse resp =
        adminRestSession.get("/changes/" + ch.getChangeId() + "/meta_diff/?meta=invalid");

    resp.assertBadRequest();
  }

  @Test
  public void metaDiffInvalidOldSha1() throws Exception {
    PushOneCommit.Result ch = createChange();

    RestResponse resp =
        adminRestSession.get("/changes/" + ch.getChangeId() + "/meta_diff/?old=invalid");

    resp.assertBadRequest();
  }

  @Test
  public void metaDiffWithNewSha1NotInRepo() throws Exception {
    PushOneCommit.Result ch = createChange();

    RestResponse resp =
        adminRestSession.get("/changes/" + ch.getChangeId() + "/meta_diff/?meta=" + UNSAVED_REV_ID);

    resp.assertStatus(412);
  }

  @Test
  public void metaDiffUnreachableOldSha1UsesDefault() throws Exception {
    PushOneCommit.Result ch1 = createChange();
    PushOneCommit.Result ch2 = createChange();
    gApi.changes().id(ch1.getChangeId()).topic("intermediate-topic");
    gApi.changes().id(ch1.getChangeId()).topic(TOPIC);
    ChangeInfo info1 = gApi.changes().id(ch1.getChangeId()).get();
    ChangeInfo info2 = gApi.changes().id(ch2.getChangeId()).get();

    ChangeInfoDifference difference =
        gApi.changes().id(ch1.getChangeId()).metaDiff(info2.metaRevId, info1.metaRevId);

    assertThat(difference.added().topic).isEqualTo(TOPIC);
    assertThat(difference.removed().topic).isNull();
  }

  @Test
  public void metaDiffWithOldSha1NotInRepoUsesDefault() throws Exception {
    PushOneCommit.Result ch = createChange();
    gApi.changes().id(ch.getChangeId()).topic("intermediate-topic");
    gApi.changes().id(ch.getChangeId()).topic(TOPIC);
    ChangeInfo info = gApi.changes().id(ch.getChangeId()).get();

    ChangeInfoDifference difference =
        gApi.changes().id(ch.getChangeId()).metaDiff(UNSAVED_REV_ID, info.metaRevId);

    assertThat(difference.added().topic).isEqualTo(TOPIC);
    assertThat(difference.removed().topic).isNull();
  }

  @Test
  public void metaDiffNoOldMetaGivenUsesPatchSetBeforeNew() throws Exception {
    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).topic(TOPIC);
    ChangeInfo newInfo = gApi.changes().id(changeId).get();
    gApi.changes().id(changeId).topic(TOPIC + "2");

    ChangeInfoDifference difference = gApi.changes().id(changeId).metaDiff(null, newInfo.metaRevId);

    assertThat(difference.added().topic).isEqualTo(TOPIC);
    assertThat(difference.removed().topic).isNull();
  }

  @Test
  public void metaDiffNoNewMetaGivenUsesCurrentPatchSet() throws Exception {
    PushOneCommit.Result ch = createChange();
    ChangeApi chApi = gApi.changes().id(ch.getChangeId());
    ChangeInfo oldInfo = chApi.get();
    chApi.topic(TOPIC);

    ChangeInfoDifference difference = chApi.metaDiff(oldInfo.metaRevId, null);

    assertThat(difference.added().topic).isEqualTo(TOPIC);
    assertThat(difference.removed().topic).isNull();
  }

  @Test
  public void metaDiffWithoutOptionDoesNotIncludeExtraInformation() throws Exception {
    PushOneCommit.Result ch = createChange();
    ChangeApi chApi = gApi.changes().id(ch.getChangeId());
    ChangeInfo oldInfo = chApi.get();
    amendChange(ch.getChangeId());
    ChangeInfo newInfo = chApi.get();

    ChangeInfoDifference difference = chApi.metaDiff(oldInfo.metaRevId, newInfo.metaRevId);

    assertThat(difference.added().currentRevision).isNull();
    assertThat(difference.removed().currentRevision).isNull();
  }

  @Test
  public void metaDiffWithOptionIncludesExtraInformation() throws Exception {
    String changeId = createChange().getChangeId();
    ChangeInfo oldInfo = gApi.changes().id(changeId).get(ListChangesOption.CURRENT_REVISION);
    amendChange(changeId);
    ChangeInfo newInfo = gApi.changes().id(changeId).get(ListChangesOption.CURRENT_REVISION);

    ChangeInfoDifference difference =
        gApi.changes()
            .id(changeId)
            .metaDiff(
                oldInfo.metaRevId,
                newInfo.metaRevId,
                ImmutableSet.of(ListChangesOption.CURRENT_REVISION));

    assertThat(newInfo.currentRevision).isNotNull();
    assertThat(oldInfo.currentRevision).isNotNull();
    assertThat(difference.added().currentRevision).isEqualTo(newInfo.currentRevision);
    assertThat(difference.removed().currentRevision).isEqualTo(oldInfo.currentRevision);
  }

  @Test
  public void staticField() throws Exception {
    PushOneCommit.Result result = createChange();
    ReviewInput in = new ReviewInput();
    in.message("hello");

    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(result.getChangeId()).revision("current").review(in);
    ChangeApi chApi = gApi.changes().id(result.getChangeId());
    ChangeInfoDifference difference = chApi.metaDiff(null, null, ListChangesOption.LABELS);
    assertThat(difference.added().reviewers).containsKey(ReviewerState.CC);
    assertThat(difference.added().reviewers).hasSize(1);
    Collection<AccountInfo> reviewers = difference.added().reviewers.get(ReviewerState.CC);
    assertThat(reviewers).hasSize(1);
    AccountInfo info = reviewers.iterator().next();
    assertThat(info._accountId).isEqualTo(user.id().get());
  }
}
