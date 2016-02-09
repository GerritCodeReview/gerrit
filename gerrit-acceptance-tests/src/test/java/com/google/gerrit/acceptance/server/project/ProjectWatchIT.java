// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.acceptance.server.project;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.base.Joiner;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.groups.GroupInput;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.AccountManager;
import com.google.gerrit.server.account.AuthRequest;
import com.google.gerrit.server.git.NotifyConfig;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.util.RequestContext;
import com.google.gerrit.server.util.ThreadLocalRequestContext;
import com.google.gerrit.testutil.FakeEmailSender;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.util.Providers;

import org.junit.Before;
import org.junit.Test;

import java.util.EnumSet;
import java.util.List;

@NoHttpd
public class ProjectWatchIT extends AbstractDaemonTest {
  @Inject
  private FakeEmailSender sender;

  @Inject
  private AccountManager accountManager;

  @Inject
  private ThreadLocalRequestContext requestContext;

  private final Address addr = new Address("Watcher", "watcher@example.com");

  @Before
  public void setup() {
    sender.clearMessages();
  }

  /**
   * Tests message project watches on new patch sets
   * <p>
   * As of 2015-06-21 this test is marked flaky for triggering race
   * conditions between indexing and project watches filters as
   * of 2015-06-21.
   * <p>
   * The test $SOMETIMES fails, stating that 2 emails instead of only
   * 1 got sent. The root issue is the inserting of two patch sets
   * (one shortly after the other), where the first patch set would
   * not match a user's filter while the second one would.
   * <p>
   * The test basically:
   * <ol>
   *   <li>Sets up a watch on the text 'sekret' in the commit message.</li>
   *   <li>Pushes a change without sekret in the commit message (no
   *     email is expected). (We'll refer to this as PS1)</li>
   *   <li>Push another patch set to the same change with sekret in the
   *     commit message (1 email is expected). (We'll refer to this as PS2)</li>
   *   <li>[...]</li>
   * </ol>
   * <p>The expected flow of actions for step 2+3 is:
   * <pre>
   *    (i) Write PS1 to the index
   *   (ii) Send out emails for PS1 after checking project watches from
   *        fresh ChangeData
   *  (iii) Write PS2 to the index
   *   (iv) Send out emails for PS2 after checking project watches from
   *        fresh ChangeData
   * </pre>
   * <p>
   * But as step (ii) and step (iv) happen on separate threads, steps
   * (ii) and (iii) might get turned around and become:
   * <pre>
   *   * Write PS1 to the index
   *   * Write PS2 to the index
   *   * Send out emails for PS1 after checking project watches from
   *     fresh ChangeData
   *   * Send out emails for PS2 after checking project watches from
   *     fresh ChangeData
   * </pre>
   * <p>
   * Hence, the filters for project watches for the emails for PS1 query
   * the index after PS2 has already been written there. Hence, the
   * filters for PS1 use the commit message of PS2 when filtering on
   * 'message:sekret'.
   * <p>
   * Since in the ProjectWatchIT test, PS2 contains 'sekret', the filters
   * for sending out emails for PS1 see a commit message containing
   * 'sekret', and the watches match for both PS1 and PS2, although they
   * should only match for PS2.
   * <p>
   * This explains why the test is only failing sometimes, and also why it
   * is more likely to occur when the system is under load.
   * <p>
   * A demo exposing the race condition is available at
   * <a href="https://gerrit-review.googlesource.com/#/c/68719/1">https://gerrit-review.googlesource.com/#/c/68719/1</a>.
   */
  @Test
  public void newPatchSetsNotifyConfig() throws Exception {
    NotifyConfig nc = newNotifyConfig(EnumSet.of(NotifyType.NEW_PATCHSETS));
    nc.setFilter("message:sekret");

    addNotifyConfig(project, nc);

    PushOneCommit.Result r = pushFactory.create(db, admin.getIdent(), testRepo,
          "original subject", "a", "a1")
        .to("refs/for/master");
    r.assertOkStatus();

    r = pushFactory.create(db, admin.getIdent(), testRepo,
          "super sekret subject", "a", "a2", r.getChangeId())
        .to("refs/for/master");
    r.assertOkStatus();

    r = pushFactory.create(db, admin.getIdent(), testRepo,
          "back to original subject", "a", "a3")
        .to("refs/for/master");
    r.assertOkStatus();

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(addr);
    assertThat(m.body()).contains("Change subject: super sekret subject\n");
    assertThat(m.body()).contains("Gerrit-PatchSet: 2\n");
  }

  /**
   * Tests for project watch type "all comments" with a basic "label:" query.
   */
  @Test
  public void labelWithoutGroupConfig() throws Exception {
    NotifyConfig nc = newNotifyConfig(EnumSet.of(NotifyType.ALL_COMMENTS));
    nc.setFilter("label:Code-Review=+1");
    addNotifyConfig(project, nc);

    PushOneCommit.Result r = pushFactory.create(db, admin.getIdent(), testRepo,
        "subject", "a", "a1")
      .to("refs/for/master");
    r.assertOkStatus();

    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.recommend());

    List<Message> messages = sender.getMessagesFor(addr);
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(addr);
    assertThat(m.body()).contains("Change subject: subject\n");
    assertThat(m.body()).contains("Patch Set 1: Code-Review+1\n");
  }

  /**
   * Tests for project watch type "all comments" with a "label:" query
   * with group parameter specifying a group that does not exist. This
   * should not cause any notification to be sent.
   */
  @Test
  public void labelWithGroupConfigNoMembers() throws Exception {
    NotifyConfig nc = newNotifyConfig(EnumSet.of(NotifyType.ALL_COMMENTS));
    nc.setFilter("label:Code-Review=+1,group=test-group");
    addNotifyConfig(project, nc);

    PushOneCommit.Result r = pushFactory.create(db, admin.getIdent(), testRepo,
        "subject", "a", "a1")
      .to("refs/for/master");
    r.assertOkStatus();

    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.recommend());

    List<Message> messages = sender.getMessagesFor(addr);
    assertThat(messages).hasSize(0);
  }

  /**
   * Tests for project watch type "all comments" with a "label:" query
   * with group parameter.
   */
  @Test
  public void labelWithGroupConfig() throws Exception {
    NotifyConfig nc = newNotifyConfig(EnumSet.of(NotifyType.ALL_COMMENTS));
    nc.setFilter("label:Code-Review=+1,group=test-group");
    addNotifyConfig(project, nc);

    // Create 'test-group' and add a test user to it
    Account.Id user1 = createAccount("testuser");
    String group = createGroup("test-group", "Administrators");
    gApi.groups().id(group).addMembers("testuser");

    PushOneCommit.Result r = pushFactory.create(db, user.getIdent(), testRepo,
        "subject", "a", "a1")
      .to("refs/for/master");
    r.assertOkStatus();

    // Send a review as the test user
    requestContext.setContext(newRequestContext(user1));
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.recommend());

    List<Message> messages = sender.getMessagesFor(addr);
    assertThat(messages).hasSize(1);
    Message m = messages.get(1);
    assertThat(m.rcpt()).hasSize(1);
    assertThat(m.rcpt()).contains(addr);
    assertThat(m.body()).contains("Change subject: subject\n");
    assertThat(m.body()).contains("Patch Set 1: Code-Review+1\n");
  }

  private Account.Id createAccount(String name) throws Exception {
    return accountManager.authenticate(
        AuthRequest.forUser(name)).getAccountId();
  }

  private String createGroup(String name, String owner) throws Exception {
    GroupInput in = new GroupInput();
    in.name = name;
    in.ownerId = owner;
    in.visibleToAll = true;
    gApi.groups().create(in);
    return name;
  }

  private NotifyConfig newNotifyConfig(EnumSet<NotifyType> types) throws Exception {
    NotifyConfig nc = new NotifyConfig();
    nc.addEmail(addr);
    nc.setName(Joiner.on("-").join(types));
    nc.setHeader(NotifyConfig.Header.CC);
    nc.setTypes(types);
    return nc;
  }

  private void addNotifyConfig(Project.NameKey project, NotifyConfig nc)
      throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.putNotifyConfig("watch", nc);
    saveProjectConfig(project, cfg);
  }

  private RequestContext newRequestContext(Account.Id requestUserId) {
    final CurrentUser requestUser =
        identifiedUserFactory.create(Providers.of(db), requestUserId);
    return new RequestContext() {
      @Override
      public CurrentUser getUser() {
        return requestUser;
      }

      @Override
      public Provider<ReviewDb> getReviewDbProvider() {
        return Providers.of(db);
      }
    };
  }

  // TODO(anybody reading this): More tests.
}
