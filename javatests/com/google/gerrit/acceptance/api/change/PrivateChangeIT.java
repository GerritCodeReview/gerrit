// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.junit.Test;

public class PrivateChangeIT extends AbstractDaemonTest {

  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;

  @Test
  public void setPrivateByOwner() throws Exception {
    TestRepository<InMemoryRepository> userRepo = cloneProject(project, user);
    PushOneCommit.Result result =
        pushFactory.create(user.newIdent(), userRepo).to("refs/for/master");

    requestScopeOperations.setApiUser(user.id());
    String changeId = result.getChangeId();
    assertThat(gApi.changes().id(changeId).get().isPrivate).isNull();

    gApi.changes().id(changeId).setPrivate(true, null);
    ChangeInfo info = gApi.changes().id(changeId).get();
    assertThat(info.isPrivate).isTrue();
    assertThat(Iterables.getLast(info.messages).message).isEqualTo("Set private");
    assertThat(Iterables.getLast(info.messages).tag).contains(ChangeMessagesUtil.TAG_SET_PRIVATE);

    gApi.changes().id(changeId).setPrivate(false, null);
    info = gApi.changes().id(changeId).get();
    assertThat(info.isPrivate).isNull();
    assertThat(Iterables.getLast(info.messages).message).isEqualTo("Unset private");
    assertThat(Iterables.getLast(info.messages).tag).contains(ChangeMessagesUtil.TAG_UNSET_PRIVATE);

    String msg = "This is a security fix that must not be public.";
    gApi.changes().id(changeId).setPrivate(true, msg);
    info = gApi.changes().id(changeId).get();
    assertThat(info.isPrivate).isTrue();
    assertThat(Iterables.getLast(info.messages).message).isEqualTo("Set private\n\n" + msg);
    assertThat(Iterables.getLast(info.messages).tag).contains(ChangeMessagesUtil.TAG_SET_PRIVATE);

    msg = "After this security fix has been released we can make it public now.";
    gApi.changes().id(changeId).setPrivate(false, msg);
    info = gApi.changes().id(changeId).get();
    assertThat(info.isPrivate).isNull();
    assertThat(Iterables.getLast(info.messages).message).isEqualTo("Unset private\n\n" + msg);
    assertThat(Iterables.getLast(info.messages).tag).contains(ChangeMessagesUtil.TAG_UNSET_PRIVATE);
  }

  @Test
  public void cannotSetMergedChangePrivate() throws Exception {
    PushOneCommit.Result result = createChange();
    approve(result.getChangeId());
    merge(result);

    String changeId = result.getChangeId();
    assertThat(gApi.changes().id(changeId).get().isPrivate).isNull();

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> gApi.changes().id(changeId).setPrivate(true));
    assertThat(thrown).hasMessageThat().contains("cannot set merged change to private");
  }

  @Test
  public void cannotSetAbandonedChangePrivate() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();

    gApi.changes().id(changeId).abandon();
    assertThat(gApi.changes().id(changeId).get().isPrivate).isNull();

    BadRequestException thrown =
        assertThrows(BadRequestException.class, () -> gApi.changes().id(changeId).setPrivate(true));
    assertThat(thrown).hasMessageThat().contains("cannot set abandoned change to private");
  }

  @Test
  public void administratorCanSetUserChangePrivate() throws Exception {
    TestRepository<InMemoryRepository> userRepo = cloneProject(project, user);
    PushOneCommit.Result result =
        pushFactory.create(user.newIdent(), userRepo).to("refs/for/master");

    String changeId = result.getChangeId();
    assertThat(gApi.changes().id(changeId).get().isPrivate).isNull();

    gApi.changes().id(changeId).setPrivate(true, null);
    requestScopeOperations.setApiUser(user.id());
    ChangeInfo info = gApi.changes().id(changeId).get();
    assertThat(info.isPrivate).isTrue();
  }

  @Test
  public void cannotSetOtherUsersChangePrivate() throws Exception {
    PushOneCommit.Result result = createChange();
    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(
            AuthException.class,
            () -> gApi.changes().id(result.getChangeId()).setPrivate(true, null));
    assertThat(thrown).hasMessageThat().contains("not allowed to mark private");
  }

  @Test
  public void accessPrivate() throws Exception {
    TestRepository<InMemoryRepository> userRepo = cloneProject(project, user);
    PushOneCommit.Result result =
        pushFactory.create(user.newIdent(), userRepo).to("refs/for/master");

    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(result.getChangeId()).setPrivate(true, null);
    // Owner can always access its private changes.
    assertThat(gApi.changes().id(result.getChangeId()).get().isPrivate).isTrue();

    // Add admin as a reviewer.
    gApi.changes().id(result.getChangeId()).addReviewer(admin.id().toString());

    // This change should be visible for admin as a reviewer.
    requestScopeOperations.setApiUser(admin.id());
    assertThat(gApi.changes().id(result.getChangeId()).get().isPrivate).isTrue();

    // Remove admin from reviewers.
    gApi.changes().id(result.getChangeId()).reviewer(admin.id().toString()).remove();

    // This change should not be visible for admin anymore.
    ResourceNotFoundException thrown =
        assertThrows(
            ResourceNotFoundException.class, () -> gApi.changes().id(result.getChangeId()));
    assertThat(thrown).hasMessageThat().contains("Not found: " + result.getChangeId());
  }

  @Test
  public void privateChangeOfOtherUserCanBeAccessedWithPermission() throws Exception {
    PushOneCommit.Result result = createChange();
    gApi.changes().id(result.getChangeId()).setPrivate(true, null);

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.VIEW_PRIVATE_CHANGES).ref("refs/*").group(REGISTERED_USERS))
        .update();
    requestScopeOperations.setApiUser(user.id());
    assertThat(gApi.changes().id(result.getChangeId()).get().isPrivate).isTrue();
  }

  @Test
  public void administratorCanUnmarkPrivateAfterMerging() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    merge(result);
    markMergedChangePrivate(Change.id(gApi.changes().id(changeId).get()._number));

    gApi.changes().id(changeId).setPrivate(false, null);
    assertThat(gApi.changes().id(changeId).get().isPrivate).isNull();
  }

  @Test
  public void ownerCannotMarkPrivateAfterMerging() throws Exception {
    TestRepository<InMemoryRepository> userRepo = cloneProject(project, user);
    PushOneCommit.Result result =
        pushFactory.create(user.newIdent(), userRepo).to("refs/for/master");

    String changeId = result.getChangeId();
    assertThat(gApi.changes().id(changeId).get().isPrivate).isNull();

    merge(result);

    requestScopeOperations.setApiUser(user.id());
    AuthException thrown =
        assertThrows(AuthException.class, () -> gApi.changes().id(changeId).setPrivate(true, null));
    assertThat(thrown).hasMessageThat().contains("not allowed to mark private");
  }

  @Test
  public void mergingPrivateChangePublishesIt() throws Exception {
    PushOneCommit.Result result = createChange();
    gApi.changes().id(result.getChangeId()).setPrivate(true);
    assertThat(gApi.changes().id(result.getChangeId()).get().isPrivate).isTrue();

    approve(result.getChangeId());
    merge(result);

    assertThat(gApi.changes().id(result.getChangeId()).get().status).isEqualTo(ChangeStatus.MERGED);
    assertThat(gApi.changes().id(result.getChangeId()).get().isPrivate).isNull();
  }

  @Test
  public void ownerCanUnmarkPrivateAfterMerging() throws Exception {
    TestRepository<InMemoryRepository> userRepo = cloneProject(project, user);
    PushOneCommit.Result result =
        pushFactory.create(user.newIdent(), userRepo).to("refs/for/master");

    String changeId = result.getChangeId();
    gApi.changes().id(changeId).addReviewer(admin.id().toString());
    merge(result);
    markMergedChangePrivate(Change.id(gApi.changes().id(changeId).get()._number));

    requestScopeOperations.setApiUser(user.id());
    gApi.changes().id(changeId).setPrivate(false, null);
    assertThat(gApi.changes().id(changeId).get().isPrivate).isNull();
  }

  @Test
  public void mergingPrivateChangeThroughGitPublishesIt() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).setPrivate(true);

    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo);
    PushOneCommit.Result result = push.to("refs/heads/master");
    result.assertOkStatus();

    assertThat(gApi.changes().id(r.getChangeId()).get().isPrivate).isNull();
  }

  private void markMergedChangePrivate(Change.Id changeId) throws Exception {
    try (BatchUpdate u =
        batchUpdateFactory.create(
            project, identifiedUserFactory.create(admin.id()), TimeUtil.nowTs())) {
      u.addOp(
              changeId,
              new BatchUpdateOp() {
                @Override
                public boolean updateChange(ChangeContext ctx) {
                  ctx.getChange().setPrivate(true);
                  ChangeUpdate update = ctx.getUpdate(ctx.getChange().currentPatchSetId());
                  ctx.getChange().setPrivate(true);
                  ctx.getChange().setLastUpdatedOn(ctx.getWhen());
                  update.setPrivate(true);
                  return true;
                }
              })
          .execute();
    }
    assertThat(gApi.changes().id(changeId.get()).get().isPrivate).isTrue();
  }
}
