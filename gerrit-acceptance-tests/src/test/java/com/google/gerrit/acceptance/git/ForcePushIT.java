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
import static org.eclipse.jgit.lib.Constants.HEAD;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.Permission;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.transport.RemoteRefUpdate.Status;
import org.junit.Test;

import java.io.IOException;

public class ForcePushIT extends AbstractDaemonTest {

  @Test
  public void forcePushNotAllowed() throws GitAPIException, IOException {
    ObjectId initial = git.getRepository().getRef(HEAD).getLeaf().getObjectId();
    PushOneCommit push1 =
        pushFactory.create(db, admin.getIdent(), "change1", "a.txt", "content");
    PushOneCommit.Result r1 = push1.to(git, "refs/heads/master");
    r1.assertOkStatus();

    // Reset HEAD to initial so the new change is a non-fast forward
    RefUpdate ru = git.getRepository().updateRef(HEAD);
    ru.setNewObjectId(initial);
    assertThat(ru.forceUpdate()).isEqualTo(RefUpdate.Result.FORCED);

    PushOneCommit push2 =
        pushFactory.create(db, admin.getIdent(), "change2", "b.txt", "content");
    push2.setForce(true);
    PushOneCommit.Result r2 = push2.to(git, "refs/heads/master");
    r2.assertErrorStatus("non-fast forward");
  }

  @Test
  public void forcePushAllowed() throws GitAPIException, IOException,
      ConfigInvalidException {
    ObjectId initial = git.getRepository().getRef(HEAD).getLeaf().getObjectId();
    grant(Permission.PUSH, project, "refs/*", true);
    PushOneCommit push1 =
        pushFactory.create(db, admin.getIdent(), "change1", "a.txt", "content");
    PushOneCommit.Result r1 = push1.to(git, "refs/heads/master");
    r1.assertOkStatus();

    // Reset HEAD to initial so the new change is a non-fast forward
    RefUpdate ru = git.getRepository().updateRef(HEAD);
    ru.setNewObjectId(initial);
    assertThat(ru.forceUpdate()).isEqualTo(RefUpdate.Result.FORCED);

    PushOneCommit push2 =
        pushFactory.create(db, admin.getIdent(), "change2", "b.txt", "content");
    push2.setForce(true);
    PushOneCommit.Result r2 = push2.to(git, "refs/heads/master");
    r2.assertOkStatus();
  }
}
