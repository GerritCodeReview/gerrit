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
import static com.google.gerrit.extensions.client.ReviewerState.REVIEWER;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.testing.FakeEmailSender;
import com.google.gson.reflect.TypeToken;
import com.google.inject.Inject;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import org.junit.Test;

public class DeleteVoteIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void deleteVoteOnChange() throws Exception {
    deleteVote(false);
  }

  @Test
  public void deleteVoteOnRevision() throws Exception {
    deleteVote(true);
  }

  private void deleteVote(boolean onRevisionLevel) throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());

    PushOneCommit.Result r2 = amendChange(r.getChangeId());

    requestScopeOperations.setApiUser(user.id());
    recommend(r.getChangeId());

    sender.clear();
    String endPoint =
        "/changes/"
            + r.getChangeId()
            + (onRevisionLevel ? ("/revisions/" + r2.getCommit().getName()) : "")
            + "/reviewers/"
            + user.id().toString()
            + "/votes/Code-Review";

    RestResponse response = adminRestSession.delete(endPoint);
    response.assertNoContent();

    List<FakeEmailSender.Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    FakeEmailSender.Message msg = messages.get(0);
    assertThat(msg.rcpt()).containsExactly(user.getEmailAddress());
    assertThat(msg.body()).contains(admin.fullName() + " has removed a vote on this change.\n");
    assertThat(msg.body())
        .contains("Removed Code-Review+1 by " + user.fullName() + " <" + user.email() + ">\n");

    endPoint =
        "/changes/"
            + r.getChangeId()
            + (onRevisionLevel ? ("/revisions/" + r2.getCommit().getName()) : "")
            + "/reviewers/"
            + user.id().toString()
            + "/votes";

    response = adminRestSession.get(endPoint);
    response.assertOK();

    Map<String, Short> m =
        newGson().fromJson(response.getReader(), new TypeToken<Map<String, Short>>() {}.getType());

    assertThat(m).containsExactly("Code-Review", Short.valueOf((short) 0));

    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();

    ChangeMessageInfo message = Iterables.getLast(c.messages);
    assertThat(message.author._accountId).isEqualTo(admin.id().get());
    assertThat(message.message).isEqualTo("Removed Code-Review+1 by User <user@example.com>\n");
    assertThat(getReviewers(c.reviewers.get(REVIEWER)))
        .containsExactlyElementsIn(ImmutableSet.of(admin.id(), user.id()));
  }

  private Iterable<Account.Id> getReviewers(Collection<AccountInfo> r) {
    return Iterables.transform(r, a -> new Account.Id(a._accountId));
  }
}
