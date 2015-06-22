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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.server.git.NotifyConfig;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.testutil.FakeEmailSender;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import com.google.inject.Inject;

import org.junit.Test;

import java.util.EnumSet;
import java.util.List;

@NoHttpd
public class ProjectWatchIT extends AbstractDaemonTest {
  @Inject
  private FakeEmailSender sender;

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
    Address addr = new Address("Watcher", "watcher@example.com");
    NotifyConfig nc = new NotifyConfig();
    nc.addEmail(addr);
    nc.setName("new-patch-set");
    nc.setHeader(NotifyConfig.Header.CC);
    nc.setTypes(EnumSet.of(NotifyType.NEW_PATCHSETS));
    nc.setFilter("message:sekret");

    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.putNotifyConfig("watch", nc);
    saveProjectConfig(project, cfg);

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

  // TODO(anybody reading this): More tests.
}
