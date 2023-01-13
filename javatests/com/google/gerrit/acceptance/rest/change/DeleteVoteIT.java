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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabelRemoval;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.blockLabelRemoval;
import static com.google.gerrit.extensions.client.ReviewerState.REVIEWER;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.server.util.AccountTemplateUtil;
import com.google.gerrit.testing.FakeEmailSender;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class DeleteVoteIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void deleteVoteOnChange_withRemoveLabelPermission() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabelRemoval(LabelId.CODE_REVIEW)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .add(block(Permission.REMOVE_REVIEWER).ref("refs/*").group(REGISTERED_USERS))
        .update();
    verifyDeleteVote(false);
  }

  @Test
  public void deleteVoteOnChange_withRemoveReviewerPermission() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            blockLabelRemoval(LabelId.CODE_REVIEW)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .add(allow(Permission.REMOVE_REVIEWER).ref("refs/*").group(REGISTERED_USERS))
        .update();
    verifyDeleteVote(false);
  }

  @Test
  public void deleteVoteOnChange_noPermission() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            blockLabelRemoval(LabelId.CODE_REVIEW)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .add(block(Permission.REMOVE_REVIEWER).ref("refs/*").group(REGISTERED_USERS))
        .update();
    verifyCannotDeleteVote(false);
  }

  @Test
  public void deleteVoteOnRevision_withRemoveLabelPermission() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            allowLabelRemoval(LabelId.CODE_REVIEW)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .add(block(Permission.REMOVE_REVIEWER).ref("refs/*").group(REGISTERED_USERS))
        .update();
    verifyDeleteVote(true);
  }

  @Test
  public void deleteVoteOnRevision_withRemoveReviewerPermission() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            blockLabelRemoval(LabelId.CODE_REVIEW)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .add(allow(Permission.REMOVE_REVIEWER).ref("refs/*").group(REGISTERED_USERS))
        .update();
    verifyDeleteVote(true);
  }

  @Test
  public void deleteVoteOnRevision_noPermission() throws Exception {
    projectOperations
        .project(project)
        .forUpdate()
        .add(
            blockLabelRemoval(LabelId.CODE_REVIEW)
                .ref("refs/heads/*")
                .group(REGISTERED_USERS)
                .range(-2, 2))
        .add(block(Permission.REMOVE_REVIEWER).ref("refs/*").group(REGISTERED_USERS))
        .update();
    verifyCannotDeleteVote(true);
  }

  private void verifyDeleteVote(boolean onRevisionLevel) throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());

    PushOneCommit.Result r2 = amendChange(r.getChangeId());

    requestScopeOperations.setApiUser(admin.id());
    recommend(r.getChangeId());

    TestAccount user2 = accountCreator.user2();
    requestScopeOperations.setApiUser(user2.id());
    recommend(r.getChangeId());

    sender.clear();
    String deleteAdminVoteEndPoint =
        "/changes/"
            + r.getChangeId()
            + (onRevisionLevel ? ("/revisions/" + r2.getCommit().getName()) : "")
            + "/reviewers/"
            + admin.id().toString()
            + "/votes/Code-Review";

    RestResponse response = userRestSession.delete(deleteAdminVoteEndPoint);
    response.assertNoContent();

    List<FakeEmailSender.Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    FakeEmailSender.Message msg = messages.get(0);
    assertThat(msg.rcpt()).containsExactly(admin.getNameEmail(), user2.getNameEmail());
    assertThat(msg.body()).contains(user.fullName() + " has removed a vote from this change.");
    assertThat(msg.body())
        .contains("Removed Code-Review+1 by " + admin.fullName() + " <" + admin.email() + ">\n");

    String viewVotesEndPoint =
        "/changes/"
            + r.getChangeId()
            + (onRevisionLevel ? ("/revisions/" + r2.getCommit().getName()) : "")
            + "/reviewers/"
            + admin.id().toString()
            + "/votes";

    response = userRestSession.get(viewVotesEndPoint);
    response.assertOK();

    Map<String, Short> m =
        newGson().fromJson(response.getReader(), new TypeToken<Map<String, Short>>() {}.getType());

    assertThat(m).containsExactly(LabelId.CODE_REVIEW, Short.valueOf((short) 0));

    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();

    ChangeMessageInfo message = Iterables.getLast(c.messages);
    assertThat(message.author._accountId).isEqualTo(user.id().get());
    assertThat(message.message)
        .isEqualTo(
            String.format(
                "Removed Code-Review+1 by %s\n",
                AccountTemplateUtil.getAccountTemplate(admin.id())));
    assertThat(getReviewers(c.reviewers.get(REVIEWER)))
        .containsExactlyElementsIn(ImmutableSet.of(user2.id(), admin.id()));
  }

  private void verifyCannotDeleteVote(boolean onRevisionLevel) throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());

    PushOneCommit.Result r2 = amendChange(r.getChangeId());

    requestScopeOperations.setApiUser(admin.id());
    recommend(r.getChangeId());

    sender.clear();
    String deleteAdminVoteEndPoint =
        "/changes/"
            + r.getChangeId()
            + (onRevisionLevel ? ("/revisions/" + r2.getCommit().getName()) : "")
            + "/reviewers/"
            + admin.id().toString()
            + "/votes/Code-Review";

    RestResponse response = userRestSession.delete(deleteAdminVoteEndPoint);
    response.assertForbidden();

    assertThat(sender.getMessages()).isEmpty();
  }

  private Iterable<Account.Id> getReviewers(Collection<AccountInfo> r) {
    return Iterables.transform(r, a -> Account.id(a._accountId));
  }
}
