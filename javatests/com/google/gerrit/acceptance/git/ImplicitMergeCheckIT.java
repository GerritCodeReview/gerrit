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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.GitUtil.pushHead;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.server.git.ProjectConfig;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Test;

public class ImplicitMergeCheckIT extends AbstractDaemonTest {

  @Test
  public void implicitMergeViaFastForward() throws Exception {
    setRejectImplicitMerges();

    pushHead(testRepo, "refs/heads/stable", false);
    PushOneCommit.Result m = push("refs/heads/master", "0", "file", "0");
    PushOneCommit.Result c = push("refs/for/stable", "1", "file", "1");

    c.assertMessage(implicitMergeOf(m.getCommit()));
    c.assertErrorStatus();
  }

  @Test
  public void implicitMergeViaRealMerge() throws Exception {
    setRejectImplicitMerges();

    ObjectId base = repo().exactRef("HEAD").getObjectId();
    push("refs/heads/stable", "0", "f", "0");
    testRepo.reset(base);
    PushOneCommit.Result m = push("refs/heads/master", "1", "f", "1");
    PushOneCommit.Result c = push("refs/for/stable", "2", "f", "2");

    c.assertMessage(implicitMergeOf(m.getCommit()));
    c.assertErrorStatus();
  }

  @Test
  public void implicitMergeCheckOff() throws Exception {
    ObjectId base = repo().exactRef("HEAD").getObjectId();
    push("refs/heads/stable", "0", "f", "0");
    testRepo.reset(base);
    PushOneCommit.Result m = push("refs/heads/master", "1", "f", "1");
    PushOneCommit.Result c = push("refs/for/stable", "2", "f", "2");

    assertThat(c.getMessage().toLowerCase()).doesNotContain(implicitMergeOf(m.getCommit()));
  }

  @Test
  public void notImplicitMerge_noWarning() throws Exception {
    setRejectImplicitMerges();

    ObjectId base = repo().exactRef("HEAD").getObjectId();
    push("refs/heads/stable", "0", "f", "0");
    testRepo.reset(base);
    PushOneCommit.Result m = push("refs/heads/master", "1", "f", "1");
    PushOneCommit.Result c = push("refs/for/master", "2", "f", "2");

    assertThat(c.getMessage().toLowerCase()).doesNotContain(implicitMergeOf(m.getCommit()));
  }

  private static String implicitMergeOf(ObjectId commit) {
    return "implicit merge of " + commit.abbreviate(7).name();
  }

  private void setRejectImplicitMerges() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.getProject().setRejectImplicitMerges(InheritableBoolean.TRUE);
    saveProjectConfig(project, cfg);
  }

  private PushOneCommit.Result push(String ref, String subject, String fileName, String content)
      throws Exception {
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), testRepo, subject, fileName, content);
    return push.to(ref);
  }
}
