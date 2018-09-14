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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.util.concurrent.TimeUnit.SECONDS;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.AssigneeInput;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.account.AccountResolver.UnresolvableAccountException;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.gerrit.testing.TestTimeUtil;
import com.google.inject.Inject;
import java.util.Iterator;
import java.util.List;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

@NoHttpd
public class AssigneeIT extends AbstractDaemonTest {
  @Inject private RequestScopeOperations requestScopeOperations;

  @BeforeClass
  public static void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, SECONDS);
  }

  @AfterClass
  public static void restoreTime() {
    TestTimeUtil.useSystemTime();
  }

  @Test
  public void getNoAssignee() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(getAssignee(r)).isNull();
  }

  @Test
  public void addGetAssignee() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(setAssignee(r, user.email())._accountId).isEqualTo(user.id().get());
    assertThat(getAssignee(r)._accountId).isEqualTo(user.id().get());

    assertThat(sender.getMessages()).hasSize(1);
    Message m = sender.getMessages().get(0);
    assertThat(m.rcpt()).containsExactly(user.getEmailAddress());
  }

  @Test
  public void setNewAssigneeWhenExists() throws Exception {
    PushOneCommit.Result r = createChange();
    setAssignee(r, user.email());
    assertThat(setAssignee(r, user.email())._accountId).isEqualTo(user.id().get());
  }

  @Test
  public void getPastAssignees() throws Exception {
    PushOneCommit.Result r = createChange();
    setAssignee(r, user.email());
    setAssignee(r, admin.email());
    List<AccountInfo> assignees = getPastAssignees(r);
    assertThat(assignees).hasSize(2);
    Iterator<AccountInfo> itr = assignees.iterator();
    assertThat(itr.next()._accountId).isEqualTo(user.id().get());
    assertThat(itr.next()._accountId).isEqualTo(admin.id().get());
  }

  @Test
  public void assigneeAddedAsReviewer() throws Exception {
    ReviewerState state = ReviewerState.CC;
    PushOneCommit.Result r = createChange();
    Iterable<AccountInfo> reviewers = getReviewers(r, state);
    assertThat(reviewers).isNull();
    assertThat(setAssignee(r, user.email())._accountId).isEqualTo(user.id().get());
    reviewers = getReviewers(r, state);
    assertThat(reviewers).hasSize(1);
    AccountInfo reviewer = Iterables.getFirst(reviewers, null);
    assertThat(reviewer._accountId).isEqualTo(user.id().get());
  }

  @Test
  public void setAlreadyExistingAssignee() throws Exception {
    PushOneCommit.Result r = createChange();
    setAssignee(r, user.email());
    assertThat(setAssignee(r, user.email())._accountId).isEqualTo(user.id().get());
  }

  @Test
  public void deleteAssignee() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(setAssignee(r, user.email())._accountId).isEqualTo(user.id().get());
    assertThat(deleteAssignee(r)._accountId).isEqualTo(user.id().get());
    assertThat(getAssignee(r)).isNull();
  }

  @Test
  public void deleteAssigneeWhenNoAssignee() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(deleteAssignee(r)).isNull();
  }

  @Test
  public void setAssigneeToInactiveUser() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.accounts().id(user.id().get()).setActive(false);
    try {
      setAssignee(r, user.email());
      assert_().fail("expected UnresolvableAccountException");
    } catch (UnresolvableAccountException e) {
      assertThat(e)
          .hasMessageThat()
          .isEqualTo(
              "Account '"
                  + user.email()
                  + "' only matches inactive accounts. To use an inactive account, retry with one"
                  + " of the following exact account IDs:\n"
                  + user.id()
                  + ": User <user@example.com>");
    }
  }

  @Test
  public void setAssigneeToInactiveUserById() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.accounts().id(user.id().get()).setActive(false);
    setAssignee(r, user.id().toString());
    assertThat(getAssignee(r)._accountId).isEqualTo(user.id().get());
  }

  @Test
  public void setAssigneeForNonVisibleChange() throws Exception {
    git().fetch().setRefSpecs(new RefSpec("refs/meta/config:refs/meta/config")).call();
    testRepo.reset(RefNames.REFS_CONFIG);
    PushOneCommit.Result r = createChange("refs/for/refs/meta/config");
    exception.expect(AuthException.class);
    exception.expectMessage("read not permitted");
    setAssignee(r, user.email());
  }

  @Test
  public void setAssigneeNotAllowedWithoutPermission() throws Exception {
    PushOneCommit.Result r = createChange();
    requestScopeOperations.setApiUser(user.id());
    exception.expect(AuthException.class);
    exception.expectMessage("not permitted");
    setAssignee(r, user.email());
  }

  @Test
  public void setAssigneeAllowedWithPermission() throws Exception {
    PushOneCommit.Result r = createChange();
    grant(project, "refs/heads/master", Permission.EDIT_ASSIGNEE, false, REGISTERED_USERS);
    requestScopeOperations.setApiUser(user.id());
    assertThat(setAssignee(r, user.email())._accountId).isEqualTo(user.id().get());
  }

  private AccountInfo getAssignee(PushOneCommit.Result r) throws Exception {
    return gApi.changes().id(r.getChange().getId().get()).getAssignee();
  }

  private List<AccountInfo> getPastAssignees(PushOneCommit.Result r) throws Exception {
    return gApi.changes().id(r.getChange().getId().get()).getPastAssignees();
  }

  private Iterable<AccountInfo> getReviewers(PushOneCommit.Result r, ReviewerState state)
      throws Exception {
    return get(r.getChangeId(), DETAILED_LABELS).reviewers.get(state);
  }

  private AccountInfo setAssignee(PushOneCommit.Result r, String identifieer) throws Exception {
    AssigneeInput input = new AssigneeInput();
    input.assignee = identifieer;
    return gApi.changes().id(r.getChange().getId().get()).setAssignee(input);
  }

  private AccountInfo deleteAssignee(PushOneCommit.Result r) throws Exception {
    return gApi.changes().id(r.getChange().getId().get()).deleteAssignee();
  }
}
