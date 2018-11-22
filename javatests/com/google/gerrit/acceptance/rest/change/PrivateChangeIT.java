// Copyright (C) 2018 The Android Open Source Project
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
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static com.google.gerrit.acceptance.PushOneCommit.SUBJECT;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.ChangeMessagesUtil;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.junit.Test;

public class PrivateChangeIT extends AbstractDaemonTest {

  @Test
  public void setPrivateByOwner() throws Exception {
    TestRepository<InMemoryRepository> userRepo = cloneProject(project, user);
    PushOneCommit.Result result =
        pushFactory.create(db, user.getIdent(), userRepo).to("refs/for/master");

    setApiUser(user);
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
  public void administratorCanSetUserChangePrivate() throws Exception {
    TestRepository<InMemoryRepository> userRepo = cloneProject(project, user);
    PushOneCommit.Result result =
        pushFactory.create(db, user.getIdent(), userRepo).to("refs/for/master");

    String changeId = result.getChangeId();
    assertThat(gApi.changes().id(changeId).get().isPrivate).isNull();

    gApi.changes().id(changeId).setPrivate(true, null);
    setApiUser(user);
    ChangeInfo info = gApi.changes().id(changeId).get();
    assertThat(info.isPrivate).isTrue();
  }

  @Test
  public void cannotSetOtherUsersChangePrivate() throws Exception {
    PushOneCommit.Result result = createChange();
    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("not allowed to mark private");
    gApi.changes().id(result.getChangeId()).setPrivate(true, null);
  }

  @Test
  public void accessPrivate() throws Exception {
    TestRepository<InMemoryRepository> userRepo = cloneProject(project, user);
    PushOneCommit.Result result =
        pushFactory.create(db, user.getIdent(), userRepo).to("refs/for/master");

    setApiUser(user);
    gApi.changes().id(result.getChangeId()).setPrivate(true, null);
    // Owner can always access its private changes.
    assertThat(gApi.changes().id(result.getChangeId()).get().isPrivate).isTrue();

    // Add admin as a reviewer.
    gApi.changes().id(result.getChangeId()).addReviewer(admin.getId().toString());

    // This change should be visible for admin as a reviewer.
    setApiUser(admin);
    assertThat(gApi.changes().id(result.getChangeId()).get().isPrivate).isTrue();

    // Remove admin from reviewers.
    gApi.changes().id(result.getChangeId()).reviewer(admin.getId().toString()).remove();

    // This change should not be visible for admin anymore.
    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Not found: " + result.getChangeId());
    gApi.changes().id(result.getChangeId());
  }

  @Test
  public void privateChangeOfOtherUserCanBeAccessedWithPermission() throws Exception {
    PushOneCommit.Result result = createChange();
    gApi.changes().id(result.getChangeId()).setPrivate(true, null);

    allow("refs/*", Permission.VIEW_PRIVATE_CHANGES, REGISTERED_USERS);
    setApiUser(user);
    assertThat(gApi.changes().id(result.getChangeId()).get().isPrivate).isTrue();
  }

  @Test
  public void administratorCanUnmarkPrivateAfterMerging() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    gApi.changes().id(changeId).setPrivate(true, null);
    assertThat(gApi.changes().id(changeId).get().isPrivate).isTrue();
    merge(result);
    gApi.changes().id(changeId).setPrivate(false, null);
    assertThat(gApi.changes().id(changeId).get().isPrivate).isNull();
  }

  @Test
  public void administratorCanMarkPrivateAfterMerging() throws Exception {
    PushOneCommit.Result result = createChange();
    String changeId = result.getChangeId();
    assertThat(gApi.changes().id(changeId).get().isPrivate).isNull();
    merge(result);
    gApi.changes().id(changeId).setPrivate(true, null);
    assertThat(gApi.changes().id(changeId).get().isPrivate).isTrue();
  }

  @Test
  public void ownerCannotMarkPrivateAfterMerging() throws Exception {
    TestRepository<InMemoryRepository> userRepo = cloneProject(project, user);
    PushOneCommit.Result result =
        pushFactory.create(db, user.getIdent(), userRepo).to("refs/for/master");

    String changeId = result.getChangeId();
    assertThat(gApi.changes().id(changeId).get().isPrivate).isNull();

    merge(result);

    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("not allowed to mark private");
    gApi.changes().id(changeId).setPrivate(true, null);
  }

  @Test
  public void ownerCanUnmarkPrivateAfterMerging() throws Exception {
    TestRepository<InMemoryRepository> userRepo = cloneProject(project, user);
    PushOneCommit.Result result =
        pushFactory.create(db, user.getIdent(), userRepo).to("refs/for/master");

    String changeId = result.getChangeId();
    assertThat(gApi.changes().id(changeId).get().isPrivate).isNull();
    gApi.changes().id(changeId).addReviewer(admin.getId().toString());
    gApi.changes().id(changeId).setPrivate(true, null);
    assertThat(gApi.changes().id(changeId).get().isPrivate).isTrue();

    merge(result);

    setApiUser(user);
    gApi.changes().id(changeId).setPrivate(false, null);
    assertThat(gApi.changes().id(changeId).get().isPrivate).isNull();
  }

  @Test
  public void setPrivateCreatesSymRef() throws Exception {
    PushOneCommit.Result result = createChange();

    Change.Id changeId = result.getChange().getId();
    gApi.changes().id(changeId.id).setPrivate(true, null);
    assertThat(gApi.changes().id(changeId.id).get().isPrivate).isTrue();

    try (Repository repo = repoManager.openRepository(project)) {
      Ref privateRef = repo.exactRef(RefNames.privateChangeMetaRef(changeId));
      assertThat(privateRef).isNotNull();
      assertThat(privateRef.isSymbolic()).isTrue();
      assertThat(privateRef.getTarget().getName()).isEqualTo(RefNames.changeMetaRef(changeId));
    }
  }

  @Test
  public void unsetPrivateDeletsSymRef() throws Exception {
    PushOneCommit.Result result = createChange("refs/for/master%private");

    Change.Id changeId = result.getChange().getId();
    gApi.changes().id(changeId.id).setPrivate(false, null);
    assertThat(gApi.changes().id(changeId.id).get().isPrivate).isNull();

    try (Repository repo = repoManager.openRepository(project)) {
      Ref privateRef = repo.exactRef(RefNames.privateChangeMetaRef(changeId));
      assertThat(privateRef).isNull();
    }
  }

  @Test
  public void pushPrivateChangeCreatesSymRef() throws Exception {
    PushOneCommit.Result result = createChange("refs/for/master%private");
    Change.Id changeId = result.getChange().getId();
    try (Repository repo = repoManager.openRepository(project)) {
      Ref privateRef = repo.exactRef(RefNames.privateChangeMetaRef(changeId));
      assertThat(privateRef).isNotNull();
      assertThat(privateRef.isSymbolic()).isTrue();
      assertThat(privateRef.getTarget().getName()).isEqualTo(RefNames.changeMetaRef(changeId));
    }
  }

  @Test
  public void pushNonPrivateUpdateOntoPrivateChangeDeletesSymRef() throws Exception {
    PushOneCommit.Result r1 =
        pushFactory
            .create(db, admin.getIdent(), testRepo, SUBJECT, FILE_NAME, "a")
            .to("refs/for/master%private");
    PushOneCommit.Result r2 =
        pushFactory
            .create(db, admin.getIdent(), testRepo, SUBJECT, FILE_NAME, "b", r1.getChangeId())
            .to("refs/for/master%remove-private");
    r2.assertOkStatus();

    Change.Id changeId = r2.getChange().getId();
    try (Repository repo = repoManager.openRepository(project)) {
      Ref privateRef = repo.exactRef(RefNames.privateChangeMetaRef(changeId));
      assertThat(privateRef).isNull();
    }
  }
}
