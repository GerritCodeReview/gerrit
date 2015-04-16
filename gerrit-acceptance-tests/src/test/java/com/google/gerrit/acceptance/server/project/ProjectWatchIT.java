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
