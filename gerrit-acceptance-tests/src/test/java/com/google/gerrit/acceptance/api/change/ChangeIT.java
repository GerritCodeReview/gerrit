// Copyright (C) 2013 The Android Open Source Project
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
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.GitUtil.assertPushOk;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static com.google.gerrit.acceptance.PushOneCommit.SUBJECT;
import static com.google.gerrit.extensions.client.ReviewerState.CC;
import static com.google.gerrit.extensions.client.ReviewerState.REVIEWER;
import static com.google.gerrit.reviewdb.client.RefNames.changeMetaRef;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.CHANGE_OWNER;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.Util.blockLabel;
import static com.google.gerrit.server.project.Util.category;
import static com.google.gerrit.server.project.Util.value;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.extensions.api.changes.DeleteReviewerInput;
import com.google.gerrit.extensions.api.changes.DeleteVoteInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.ReviewInput.DraftHandling;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.Comment.Range;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.GitPerson;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.gerrit.extensions.common.MergePatchSetInput;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.LabelId;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.git.ChangeMessageModifier;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.notedb.NoteDbChangeState.PrimaryStorage;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.Util;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.inject.Inject;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public class ChangeIT extends AbstractDaemonTest {
  private String systemTimeZone;

  @Inject private DynamicSet<ChangeMessageModifier> changeMessageModifiers;

  @Before
  public void setTimeForTesting() {
    systemTimeZone = System.setProperty("user.timezone", "US/Eastern");
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
    System.setProperty("user.timezone", systemTimeZone);
  }

  @Test
  public void reflog() throws Exception {
    // Tests are using DfsRepository which does not implement getReflogReader,
    // so this will always fail.
    // TODO: change this if/when DfsRepository#getReflogReader is implemented.
    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("reflog not supported");
    gApi.projects().name(project.get()).branch("master").reflog();
  }

  @Test
  public void get() throws Exception {
    PushOneCommit.Result r = createChange();
    String triplet = project.get() + "~master~" + r.getChangeId();
    ChangeInfo c = info(triplet);
    assertThat(c.id).isEqualTo(triplet);
    assertThat(c.project).isEqualTo(project.get());
    assertThat(c.branch).isEqualTo("master");
    assertThat(c.status).isEqualTo(ChangeStatus.NEW);
    assertThat(c.subject).isEqualTo("test commit");
    assertThat(c.submitType).isEqualTo(SubmitType.MERGE_IF_NECESSARY);
    assertThat(c.mergeable).isTrue();
    assertThat(c.changeId).isEqualTo(r.getChangeId());
    assertThat(c.created).isEqualTo(c.updated);
    assertThat(c._number).isEqualTo(r.getChange().getId().get());

    assertThat(c.owner._accountId).isEqualTo(admin.getId().get());
    assertThat(c.owner.name).isNull();
    assertThat(c.owner.email).isNull();
    assertThat(c.owner.username).isNull();
    assertThat(c.owner.avatars).isNull();
  }

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

    allow(Permission.VIEW_PRIVATE_CHANGES, REGISTERED_USERS, "refs/*");
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
  public void userCannotMarkPrivateAfterMerging() throws Exception {
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
  public void userCannotUnmarkPrivateAfterMerging() throws Exception {
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
    exception.expect(AuthException.class);
    exception.expectMessage("not allowed to unmark private");
    gApi.changes().id(changeId).setPrivate(false, null);
  }

  @Test
  public void setWorkInProgressNotAllowedWithoutPermission() throws Exception {
    PushOneCommit.Result rwip = createChange();
    String changeId = rwip.getChangeId();

    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("not allowed to set work in progress");
    gApi.changes().id(changeId).setWorkInProgress();
  }

  @Test
  public void setReadyForReviewNotAllowedWithoutPermission() throws Exception {
    PushOneCommit.Result rready = createChange();
    String changeId = rready.getChangeId();
    gApi.changes().id(changeId).setWorkInProgress();

    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("not allowed to set ready for review");
    gApi.changes().id(changeId).setReadyForReview();
  }

  @Test
  public void toggleWorkInProgressState() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();

    // With message
    gApi.changes().id(changeId).setWorkInProgress("Needs some refactoring");

    ChangeInfo info = gApi.changes().id(changeId).get();

    assertThat(info.workInProgress).isTrue();
    assertThat(Iterables.getLast(info.messages).message).contains("Needs some refactoring");
    assertThat(Iterables.getLast(info.messages).tag).contains(ChangeMessagesUtil.TAG_SET_WIP);

    gApi.changes().id(changeId).setReadyForReview("PTAL");

    info = gApi.changes().id(changeId).get();
    assertThat(info.workInProgress).isNull();
    assertThat(Iterables.getLast(info.messages).message).contains("PTAL");
    assertThat(Iterables.getLast(info.messages).tag).contains(ChangeMessagesUtil.TAG_SET_READY);

    // No message
    gApi.changes().id(changeId).setWorkInProgress();

    info = gApi.changes().id(changeId).get();

    assertThat(info.workInProgress).isTrue();
    assertThat(Iterables.getLast(info.messages).message).isEqualTo("Set Work In Progress");
    assertThat(Iterables.getLast(info.messages).tag).contains(ChangeMessagesUtil.TAG_SET_WIP);

    gApi.changes().id(changeId).setReadyForReview();

    info = gApi.changes().id(changeId).get();
    assertThat(info.workInProgress).isNull();
    assertThat(Iterables.getLast(info.messages).message).isEqualTo("Set Ready For Review");
    assertThat(Iterables.getLast(info.messages).tag).contains(ChangeMessagesUtil.TAG_SET_READY);
  }

  @Test
  public void getAmbiguous() throws Exception {
    PushOneCommit.Result r1 = createChange();
    String changeId = r1.getChangeId();
    gApi.changes().id(changeId).get();

    BranchInput b = new BranchInput();
    b.revision = repo().exactRef("HEAD").getObjectId().name();
    gApi.projects().name(project.get()).branch("other").create(b);

    PushOneCommit push2 =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME,
            PushOneCommit.FILE_CONTENT,
            changeId);
    PushOneCommit.Result r2 = push2.to("refs/for/other");
    assertThat(r2.getChangeId()).isEqualTo(changeId);

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Multiple changes found for " + changeId);
    gApi.changes().id(changeId).get();
  }

  @Test
  public void revert() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();
    ChangeInfo revertChange = gApi.changes().id(r.getChangeId()).revert().get();

    // expected messages on source change:
    // 1. Uploaded patch set 1.
    // 2. Patch Set 1: Code-Review+2
    // 3. Change has been successfully merged by Administrator
    // 4. Patch Set 1: Reverted
    List<ChangeMessageInfo> sourceMessages =
        new ArrayList<>(gApi.changes().id(r.getChangeId()).get().messages);
    assertThat(sourceMessages).hasSize(4);
    String expectedMessage =
        String.format("Created a revert of this change as %s", revertChange.changeId);
    assertThat(sourceMessages.get(3).message).isEqualTo(expectedMessage);

    assertThat(revertChange.messages).hasSize(1);
    assertThat(revertChange.messages.iterator().next().message).isEqualTo("Uploaded patch set 1.");
  }

  @Test
  public void revertPreservesReviewersAndCcs() throws Exception {
    PushOneCommit.Result r = createChange();

    ReviewInput in = ReviewInput.approve();
    in.reviewer(user.email);
    in.reviewer(accountCreator.user2().email, ReviewerState.CC, true);
    // Add user as reviewer that will create the revert
    in.reviewer(accountCreator.admin2().email);

    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(in);
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();

    // expect both the original reviewers and CCs to be preserved
    // original owner should be added as reviewer, user requesting the revert (new owner) removed
    setApiUser(accountCreator.admin2());
    Map<ReviewerState, Collection<AccountInfo>> result =
        gApi.changes().id(r.getChangeId()).revert().get().reviewers;
    assertThat(result).containsKey(ReviewerState.REVIEWER);

    List<Integer> reviewers =
        result.get(ReviewerState.REVIEWER).stream().map(a -> a._accountId).collect(toList());
    if (notesMigration.readChanges()) {
      assertThat(result).containsKey(ReviewerState.CC);
      List<Integer> ccs =
          result.get(ReviewerState.CC).stream().map(a -> a._accountId).collect(toList());
      assertThat(ccs).containsExactly(accountCreator.user2().id.get());
      assertThat(reviewers).containsExactly(user.id.get(), admin.id.get());
    } else {
      assertThat(reviewers)
          .containsExactly(user.id.get(), admin.id.get(), accountCreator.user2().id.get());
    }
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void revertInitialCommit() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Cannot revert initial commit");
    gApi.changes().id(r.getChangeId()).revert();
  }

  @FunctionalInterface
  private interface Rebase {
    void call(String id) throws RestApiException;
  }

  @Test
  public void rebaseViaRevisionApi() throws Exception {
    testRebase(id -> gApi.changes().id(id).current().rebase());
  }

  @Test
  public void rebaseViaChangeApi() throws Exception {
    testRebase(id -> gApi.changes().id(id).rebase());
  }

  private void testRebase(Rebase rebase) throws Exception {
    // Create two changes both with the same parent
    PushOneCommit.Result r = createChange();
    testRepo.reset("HEAD~1");
    PushOneCommit.Result r2 = createChange();

    // Approve and submit the first change
    RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
    revision.review(ReviewInput.approve());
    revision.submit();

    String changeId = r2.getChangeId();
    // Rebase the second change
    rebase.call(changeId);

    // Second change should have 2 patch sets
    ChangeInfo c2 = gApi.changes().id(changeId).get();
    assertThat(c2.revisions.get(c2.currentRevision)._number).isEqualTo(2);

    // ...and the committer and description should be correct
    ChangeInfo info =
        gApi.changes()
            .id(changeId)
            .get(EnumSet.of(ListChangesOption.CURRENT_REVISION, ListChangesOption.CURRENT_COMMIT));
    GitPerson committer = info.revisions.get(info.currentRevision).commit.committer;
    assertThat(committer.name).isEqualTo(admin.fullName);
    assertThat(committer.email).isEqualTo(admin.email);
    String description = info.revisions.get(info.currentRevision).description;
    assertThat(description).isEqualTo("Rebase");

    // Rebasing the second change again should fail
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Change is already up to date");
    gApi.changes().id(changeId).current().rebase();
  }

  @Test
  public void rebaseNotAllowedWithoutPermission() throws Exception {
    // Create two changes both with the same parent
    PushOneCommit.Result r = createChange();
    testRepo.reset("HEAD~1");
    PushOneCommit.Result r2 = createChange();

    // Approve and submit the first change
    RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
    revision.review(ReviewInput.approve());
    revision.submit();

    // Rebase the second
    String changeId = r2.getChangeId();
    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("rebase not permitted");
    gApi.changes().id(changeId).rebase();
  }

  @Test
  public void rebaseAllowedWithPermission() throws Exception {
    // Create two changes both with the same parent
    PushOneCommit.Result r = createChange();
    testRepo.reset("HEAD~1");
    PushOneCommit.Result r2 = createChange();

    // Approve and submit the first change
    RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
    revision.review(ReviewInput.approve());
    revision.submit();

    grant(project, "refs/heads/master", Permission.REBASE, false, REGISTERED_USERS);

    // Rebase the second
    String changeId = r2.getChangeId();
    setApiUser(user);
    gApi.changes().id(changeId).rebase();
  }

  @Test
  public void rebaseNotAllowedWithoutPushPermission() throws Exception {
    // Create two changes both with the same parent
    PushOneCommit.Result r = createChange();
    testRepo.reset("HEAD~1");
    PushOneCommit.Result r2 = createChange();

    // Approve and submit the first change
    RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
    revision.review(ReviewInput.approve());
    revision.submit();

    grant(project, "refs/heads/master", Permission.REBASE, false, REGISTERED_USERS);
    block("refs/for/*", Permission.PUSH, REGISTERED_USERS);

    // Rebase the second
    String changeId = r2.getChangeId();
    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("rebase not permitted");
    gApi.changes().id(changeId).rebase();
  }

  @Test
  public void rebaseNotAllowedForOwnerWithoutPushPermission() throws Exception {
    // Create two changes both with the same parent
    PushOneCommit.Result r = createChange();
    testRepo.reset("HEAD~1");
    PushOneCommit.Result r2 = createChange();

    // Approve and submit the first change
    RevisionApi revision = gApi.changes().id(r.getChangeId()).current();
    revision.review(ReviewInput.approve());
    revision.submit();

    block("refs/for/*", Permission.PUSH, REGISTERED_USERS);

    // Rebase the second
    String changeId = r2.getChangeId();
    exception.expect(AuthException.class);
    exception.expectMessage("rebase not permitted");
    gApi.changes().id(changeId).rebase();
  }

  @Test
  public void publish() throws Exception {
    PushOneCommit.Result r = createChange("refs/drafts/master");
    assertThat(info(r.getChangeId()).status).isEqualTo(ChangeStatus.DRAFT);
    gApi.changes().id(r.getChangeId()).publish();
    assertThat(info(r.getChangeId()).status).isEqualTo(ChangeStatus.NEW);
  }

  @Test
  public void deleteDraftChange() throws Exception {
    PushOneCommit.Result r = createChange("refs/drafts/master");
    assertThat(query(r.getChangeId())).hasSize(1);
    assertThat(info(r.getChangeId()).status).isEqualTo(ChangeStatus.DRAFT);
    gApi.changes().id(r.getChangeId()).delete();
    assertThat(query(r.getChangeId())).isEmpty();
  }

  @Test
  public void deleteNewChangeAsAdmin() throws Exception {
    PushOneCommit.Result changeResult = createChange();
    String changeId = changeResult.getChangeId();

    gApi.changes().id(changeId).delete();

    assertThat(query(changeId)).isEmpty();
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void deleteNewChangeAsNormalUser() throws Exception {
    PushOneCommit.Result changeResult =
        pushFactory.create(db, user.getIdent(), testRepo).to("refs/for/master");
    String changeId = changeResult.getChangeId();

    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("delete not permitted");
    gApi.changes().id(changeId).delete();
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void deleteChangeAsUserWithDeleteOwnChangesPermission() throws Exception {
    allow(Permission.DELETE_OWN_CHANGES, REGISTERED_USERS, "refs/*");

    try {
      PushOneCommit.Result changeResult =
          pushFactory.create(db, user.getIdent(), testRepo).to("refs/for/master");
      String changeId = changeResult.getChangeId();
      int id = changeResult.getChange().getId().id;
      RevCommit commit = changeResult.getCommit();

      setApiUser(user);
      gApi.changes().id(changeId).delete();

      assertThat(query(changeId)).isEmpty();

      String ref = new Change.Id(id).toRefPrefix() + "1";
      eventRecorder.assertRefUpdatedEvents(project.get(), ref, null, commit, commit, null);
    } finally {
      removePermission(project, "refs/*", Permission.DELETE_OWN_CHANGES);
    }
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void deleteNewChangeOfAnotherUserAsAdmin() throws Exception {
    PushOneCommit.Result changeResult =
        pushFactory.create(db, user.getIdent(), testRepo).to("refs/for/master");
    changeResult.assertOkStatus();
    String changeId = changeResult.getChangeId();

    setApiUser(admin);
    gApi.changes().id(changeId).delete();

    assertThat(query(changeId)).isEmpty();
  }

  @Test
  public void deleteNewChangeOfAnotherUserWithDeleteOwnChangesPermission() throws Exception {
    allow(Permission.DELETE_OWN_CHANGES, REGISTERED_USERS, "refs/*");

    try {
      PushOneCommit.Result changeResult = createChange();
      String changeId = changeResult.getChangeId();

      setApiUser(user);
      exception.expect(AuthException.class);
      exception.expectMessage("delete not permitted");
      gApi.changes().id(changeId).delete();
    } finally {
      removePermission(project, "refs/*", Permission.DELETE_OWN_CHANGES);
    }
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void deleteNewChangeForBranchWithoutCommits() throws Exception {
    PushOneCommit.Result changeResult = createChange();
    String changeId = changeResult.getChangeId();

    gApi.changes().id(changeId).delete();

    assertThat(query(changeId)).isEmpty();
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void deleteAbandonedChangeAsNormalUser() throws Exception {
    PushOneCommit.Result changeResult =
        pushFactory.create(db, user.getIdent(), testRepo).to("refs/for/master");
    String changeId = changeResult.getChangeId();

    setApiUser(user);
    gApi.changes().id(changeId).abandon();

    exception.expect(AuthException.class);
    exception.expectMessage("delete not permitted");
    gApi.changes().id(changeId).delete();
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void deleteAbandonedChangeOfAnotherUserAsAdmin() throws Exception {
    PushOneCommit.Result changeResult =
        pushFactory.create(db, user.getIdent(), testRepo).to("refs/for/master");
    String changeId = changeResult.getChangeId();

    gApi.changes().id(changeId).abandon();

    gApi.changes().id(changeId).delete();

    assertThat(query(changeId)).isEmpty();
  }

  @Test
  public void deleteMergedChange() throws Exception {
    PushOneCommit.Result changeResult = createChange();
    String changeId = changeResult.getChangeId();

    merge(changeResult);

    exception.expect(MethodNotAllowedException.class);
    exception.expectMessage("delete not permitted");
    gApi.changes().id(changeId).delete();
  }

  @Test
  @TestProjectInput(cloneAs = "user")
  public void deleteMergedChangeWithDeleteOwnChangesPermission() throws Exception {
    allow(Permission.DELETE_OWN_CHANGES, REGISTERED_USERS, "refs/*");

    try {
      PushOneCommit.Result changeResult =
          pushFactory.create(db, user.getIdent(), testRepo).to("refs/for/master");
      String changeId = changeResult.getChangeId();

      merge(changeResult);

      setApiUser(user);
      exception.expect(MethodNotAllowedException.class);
      exception.expectMessage("delete not permitted");
      gApi.changes().id(changeId).delete();
    } finally {
      removePermission(project, "refs/*", Permission.DELETE_OWN_CHANGES);
    }
  }

  @Test
  public void deleteNewChangeWithMergedPatchSet() throws Exception {
    PushOneCommit.Result changeResult = createChange();
    String changeId = changeResult.getChangeId();
    Change.Id id = changeResult.getChange().getId();

    merge(changeResult);
    setChangeStatus(id, Change.Status.NEW);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage(
        String.format("Cannot delete change %s: patch set 1 is already merged", id));
    gApi.changes().id(changeId).delete();
  }

  @Test
  public void rebaseUpToDateChange() throws Exception {
    PushOneCommit.Result r = createChange();
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Change is already up to date");
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).rebase();
  }

  @Test
  public void rebaseConflict() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();

    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            PushOneCommit.FILE_NAME,
            "other content",
            "If09d8782c1e59dd0b33de2b1ec3595d69cc10ad5");
    r = push.to("refs/for/master");
    r.assertOkStatus();

    exception.expect(ResourceConflictException.class);
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).rebase();
  }

  @Test
  public void rebaseChangeBase() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = createChange();
    PushOneCommit.Result r3 = createChange();
    RebaseInput ri = new RebaseInput();

    // rebase r3 directly onto master (break dep. towards r2)
    ri.base = "";
    gApi.changes().id(r3.getChangeId()).revision(r3.getCommit().name()).rebase(ri);
    PatchSet ps3 = r3.getPatchSet();
    assertThat(ps3.getId().get()).isEqualTo(2);

    // rebase r2 onto r3 (referenced by ref)
    ri.base = ps3.getId().toRefName();
    gApi.changes().id(r2.getChangeId()).revision(r2.getCommit().name()).rebase(ri);
    PatchSet ps2 = r2.getPatchSet();
    assertThat(ps2.getId().get()).isEqualTo(2);

    // rebase r1 onto r2 (referenced by commit)
    ri.base = ps2.getRevision().get();
    gApi.changes().id(r1.getChangeId()).revision(r1.getCommit().name()).rebase(ri);
    PatchSet ps1 = r1.getPatchSet();
    assertThat(ps1.getId().get()).isEqualTo(2);

    // rebase r1 onto r3 (referenced by change number)
    ri.base = String.valueOf(r3.getChange().getId().get());
    gApi.changes().id(r1.getChangeId()).revision(ps1.getRevision().get()).rebase(ri);
    assertThat(r1.getPatchSetId().get()).isEqualTo(3);
  }

  @Test
  public void rebaseChangeBaseRecursion() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = createChange();

    RebaseInput ri = new RebaseInput();
    ri.base = r2.getCommit().name();
    String expectedMessage =
        "base change "
            + r2.getChangeId()
            + " is a descendant of the current change - recursion not allowed";
    exception.expect(ResourceConflictException.class);
    exception.expectMessage(expectedMessage);
    gApi.changes().id(r1.getChangeId()).revision(r1.getCommit().name()).rebase(ri);
  }

  @Test
  public void rebaseAbandonedChange() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    gApi.changes().id(changeId).abandon();
    ChangeInfo info = get(changeId);
    assertThat(info.status).isEqualTo(ChangeStatus.ABANDONED);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("change is abandoned");
    gApi.changes().id(changeId).revision(r.getCommit().name()).rebase();
  }

  @Test
  public void rebaseOntoAbandonedChange() throws Exception {
    // Create two changes both with the same parent
    PushOneCommit.Result r = createChange();
    testRepo.reset("HEAD~1");
    PushOneCommit.Result r2 = createChange();

    // Abandon the first change
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    gApi.changes().id(changeId).abandon();
    ChangeInfo info = get(changeId);
    assertThat(info.status).isEqualTo(ChangeStatus.ABANDONED);

    RebaseInput ri = new RebaseInput();
    ri.base = r.getCommit().name();

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("base change is abandoned: " + changeId);
    gApi.changes().id(r2.getChangeId()).revision(r2.getCommit().name()).rebase(ri);
  }

  @Test
  public void rebaseOntoSelf() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String commit = r.getCommit().name();
    RebaseInput ri = new RebaseInput();
    ri.base = commit;
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("cannot rebase change onto itself");
    gApi.changes().id(changeId).revision(commit).rebase(ri);
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void changeNoParentToOneParent() throws Exception {
    // create initial commit with no parent and push it as change, so that patch
    // set 1 has no parent
    RevCommit c = testRepo.commit().message("Initial commit").insertChangeId().create();
    String id = GitUtil.getChangeId(testRepo, c).get();
    testRepo.reset(c);

    PushResult pr = pushHead(testRepo, "refs/for/master", false);
    assertPushOk(pr, "refs/for/master");

    ChangeInfo change = gApi.changes().id(id).get();
    assertThat(change.revisions.get(change.currentRevision).commit.parents).isEmpty();

    // create another initial commit with no parent and push it directly into
    // the remote repository
    c = testRepo.amend(c.getId()).message("Initial Empty Commit").create();
    testRepo.reset(c);
    pr = pushHead(testRepo, "refs/heads/master", false);
    assertPushOk(pr, "refs/heads/master");

    // create a successor commit and push it as second patch set to the change,
    // so that patch set 2 has 1 parent
    RevCommit c2 =
        testRepo
            .commit()
            .message("Initial commit")
            .parent(c)
            .insertChangeId(id.substring(1))
            .create();
    testRepo.reset(c2);

    pr = pushHead(testRepo, "refs/for/master", false);
    assertPushOk(pr, "refs/for/master");

    change = gApi.changes().id(id).get();
    RevisionInfo rev = change.revisions.get(change.currentRevision);
    assertThat(rev.commit.parents).hasSize(1);
    assertThat(rev.commit.parents.get(0).commit).isEqualTo(c.name());

    // check that change kind is correctly detected as REWORK
    assertThat(rev.kind).isEqualTo(ChangeKind.REWORK);
  }

  @Test
  public void pushCommitOfOtherUser() throws Exception {
    // admin pushes commit of user
    PushOneCommit push = pushFactory.create(db, user.getIdent(), testRepo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();

    ChangeInfo change = gApi.changes().id(result.getChangeId()).get();
    assertThat(change.owner._accountId).isEqualTo(admin.id.get());
    CommitInfo commit = change.revisions.get(change.currentRevision).commit;
    assertThat(commit.author.email).isEqualTo(user.email);
    assertThat(commit.committer.email).isEqualTo(user.email);

    // check that the author/committer was added as reviewer
    Collection<AccountInfo> reviewers = change.reviewers.get(REVIEWER);
    assertThat(reviewers).isNotNull();
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.iterator().next()._accountId).isEqualTo(user.getId().get());
    assertThat(change.reviewers.get(CC)).isNull();

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress);
    assertThat(m.body()).contains(admin.fullName + " has uploaded this change for review");
    assertThat(m.body()).contains("Change subject: " + PushOneCommit.SUBJECT + "\n");
    assertMailReplyTo(m, admin.email);
  }

  @Test
  public void pushCommitOfOtherUserThatCannotSeeChange() throws Exception {
    // create hidden project that is only visible to administrators
    Project.NameKey p = createProject("p");
    ProjectConfig cfg = projectCache.checkedGet(p).getConfig();
    Util.allow(
        cfg,
        Permission.READ,
        groupCache.get(new AccountGroup.NameKey("Administrators")).getGroupUUID(),
        "refs/*");
    Util.block(cfg, Permission.READ, REGISTERED_USERS, "refs/*");
    saveProjectConfig(p, cfg);

    // admin pushes commit of user
    TestRepository<InMemoryRepository> repo = cloneProject(p, admin);
    PushOneCommit push = pushFactory.create(db, user.getIdent(), repo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();

    ChangeInfo change = gApi.changes().id(result.getChangeId()).get();
    assertThat(change.owner._accountId).isEqualTo(admin.id.get());
    CommitInfo commit = change.revisions.get(change.currentRevision).commit;
    assertThat(commit.author.email).isEqualTo(user.email);
    assertThat(commit.committer.email).isEqualTo(user.email);

    // check the user cannot see the change
    setApiUser(user);
    try {
      gApi.changes().id(result.getChangeId()).get();
      fail("Expected ResourceNotFoundException");
    } catch (ResourceNotFoundException e) {
      // Expected.
    }

    // check that the author/committer was NOT added as reviewer (he can't see
    // the change)
    assertThat(change.reviewers.get(REVIEWER)).isNull();
    assertThat(change.reviewers.get(CC)).isNull();
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void pushCommitWithFooterOfOtherUser() throws Exception {
    // admin pushes commit that references 'user' in a footer
    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT
                + "\n\n"
                + FooterConstants.REVIEWED_BY.getName()
                + ": "
                + user.getIdent().toExternalString(),
            PushOneCommit.FILE_NAME,
            PushOneCommit.FILE_CONTENT);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();

    // check that 'user' was added as reviewer
    ChangeInfo change = gApi.changes().id(result.getChangeId()).get();
    Collection<AccountInfo> reviewers = change.reviewers.get(REVIEWER);
    assertThat(reviewers).isNotNull();
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.iterator().next()._accountId).isEqualTo(user.getId().get());
    assertThat(change.reviewers.get(CC)).isNull();

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress);
    assertThat(m.body()).contains("Hello " + user.fullName + ",\n");
    assertThat(m.body()).contains("I'd like you to do a code review.");
    assertThat(m.body()).contains("Change subject: " + PushOneCommit.SUBJECT + "\n");
    assertMailReplyTo(m, admin.email);
  }

  @Test
  public void pushCommitWithFooterOfOtherUserThatCannotSeeChange() throws Exception {
    // create hidden project that is only visible to administrators
    Project.NameKey p = createProject("p");
    ProjectConfig cfg = projectCache.checkedGet(p).getConfig();
    Util.allow(
        cfg,
        Permission.READ,
        groupCache.get(new AccountGroup.NameKey("Administrators")).getGroupUUID(),
        "refs/*");
    Util.block(cfg, Permission.READ, REGISTERED_USERS, "refs/*");
    saveProjectConfig(p, cfg);

    // admin pushes commit that references 'user' in a footer
    TestRepository<InMemoryRepository> repo = cloneProject(p, admin);
    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            repo,
            PushOneCommit.SUBJECT
                + "\n\n"
                + FooterConstants.REVIEWED_BY.getName()
                + ": "
                + user.getIdent().toExternalString(),
            PushOneCommit.FILE_NAME,
            PushOneCommit.FILE_CONTENT);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();

    // check that 'user' cannot see the change
    setApiUser(user);
    try {
      gApi.changes().id(result.getChangeId()).get();
      fail("Expected ResourceNotFoundException");
    } catch (ResourceNotFoundException e) {
      // Expected.
    }

    // check that 'user' was NOT added as cc ('user' can't see the change)
    setApiUser(admin);
    ChangeInfo change = gApi.changes().id(result.getChangeId()).get();
    assertThat(change.reviewers.get(REVIEWER)).isNull();
    assertThat(change.reviewers.get(CC)).isNull();
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void addReviewerThatCannotSeeChange() throws Exception {
    // create hidden project that is only visible to administrators
    Project.NameKey p = createProject("p");
    ProjectConfig cfg = projectCache.checkedGet(p).getConfig();
    Util.allow(
        cfg,
        Permission.READ,
        groupCache.get(new AccountGroup.NameKey("Administrators")).getGroupUUID(),
        "refs/*");
    Util.block(cfg, Permission.READ, REGISTERED_USERS, "refs/*");
    saveProjectConfig(p, cfg);

    // create change
    TestRepository<InMemoryRepository> repo = cloneProject(p, admin);
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), repo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();

    // check the user cannot see the change
    setApiUser(user);
    try {
      gApi.changes().id(result.getChangeId()).get();
      fail("Expected ResourceNotFoundException");
    } catch (ResourceNotFoundException e) {
      // Expected.
    }

    // try to add user as reviewer
    setApiUser(admin);
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    AddReviewerResult r = gApi.changes().id(result.getChangeId()).addReviewer(in);

    assertThat(r.input).isEqualTo(user.email);
    assertThat(r.error).contains("does not have permission to see this change");
    assertThat(r.reviewers).isNull();
  }

  @Test
  public void addReviewerThatIsInactive() throws Exception {
    PushOneCommit.Result result = createChange();

    String username = name("new-user");
    gApi.accounts().create(username).setActive(false);

    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = username;
    AddReviewerResult r = gApi.changes().id(result.getChangeId()).addReviewer(in);

    assertThat(r.input).isEqualTo(username);
    assertThat(r.error).contains("identifies an inactive account");
    assertThat(r.reviewers).isNull();
  }

  @Test
  public void addReviewerThatIsInactiveEmailFallback() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();

    ConfigInput conf = new ConfigInput();
    conf.enableReviewerByEmail = InheritableBoolean.TRUE;
    gApi.projects().name(project.get()).config(conf);

    PushOneCommit.Result result = createChange();

    String username = "user@domain.com";
    gApi.accounts().create(username).setActive(false);

    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = username;
    in.state = ReviewerState.CC;
    AddReviewerResult r = gApi.changes().id(result.getChangeId()).addReviewer(in);

    assertThat(r.input).isEqualTo(username);
    assertThat(r.error).isNull();
    // When adding by email, the reviewers field is also empty because we can't
    // render a ReviewerInfo object for a non-account.
    assertThat(r.reviewers).isNull();
  }

  @Test
  public void addReviewer() throws Exception {
    TestTimeUtil.resetWithClockStep(1, SECONDS);
    PushOneCommit.Result r = createChange();
    ChangeResource rsrc = parseResource(r);
    String oldETag = rsrc.getETag();
    Timestamp oldTs = rsrc.getChange().getLastUpdatedOn();

    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(r.getChangeId()).addReviewer(in);

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress);
    assertThat(m.body()).contains("Hello " + user.fullName + ",\n");
    assertThat(m.body()).contains("I'd like you to do a code review.");
    assertThat(m.body()).contains("Change subject: " + PushOneCommit.SUBJECT + "\n");
    assertMailReplyTo(m, admin.email);
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();

    // When NoteDb is enabled adding a reviewer records that user as reviewer
    // in NoteDb. When NoteDb is disabled adding a reviewer results in a dummy 0
    // approval on the change which is treated as CC when the ChangeInfo is
    // created.
    Collection<AccountInfo> reviewers = c.reviewers.get(REVIEWER);
    assertThat(reviewers).isNotNull();
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.iterator().next()._accountId).isEqualTo(user.getId().get());

    // Ensure ETag and lastUpdatedOn are updated.
    rsrc = parseResource(r);
    assertThat(rsrc.getETag()).isNotEqualTo(oldETag);
    assertThat(rsrc.getChange().getLastUpdatedOn()).isNotEqualTo(oldTs);
  }

  @Test
  public void notificationsForAddedWorkInProgressReviewers() throws Exception {
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    ReviewInput batchIn = new ReviewInput();
    batchIn.reviewers = ImmutableList.of(in);

    // Added reviewers not notified by default.
    PushOneCommit.Result r = createWorkInProgressChange();
    gApi.changes().id(r.getChangeId()).addReviewer(in);
    assertThat(sender.getMessages()).hasSize(0);

    // Default notification handling can be overridden.
    r = createWorkInProgressChange();
    in.notify = NotifyHandling.OWNER_REVIEWERS;
    gApi.changes().id(r.getChangeId()).addReviewer(in);
    assertThat(sender.getMessages()).hasSize(1);
    sender.clear();

    // Reviewers added via PostReview also not notified by default.
    // In this case, the child ReviewerInput has a notify=OWNER_REVIEWERS
    // that should be ignored.
    r = createWorkInProgressChange();
    gApi.changes().id(r.getChangeId()).revision("current").review(batchIn);
    assertThat(sender.getMessages()).hasSize(0);

    // Top-level notify property can force notifications when adding reviewer
    // via PostReview.
    r = createWorkInProgressChange();
    batchIn.notify = NotifyHandling.OWNER_REVIEWERS;
    gApi.changes().id(r.getChangeId()).revision("current").review(batchIn);
    assertThat(sender.getMessages()).hasSize(1);
  }

  @Test
  public void addReviewerWithNoteDbWhenDummyApprovalInReviewDbExists() throws Exception {
    assume().that(notesMigration.enabled()).isTrue();
    assume().that(notesMigration.changePrimaryStorage()).isEqualTo(PrimaryStorage.REVIEW_DB);

    PushOneCommit.Result r = createChange();

    // insert dummy approval in ReviewDb
    PatchSetApproval psa =
        new PatchSetApproval(
            new PatchSetApproval.Key(r.getPatchSetId(), user.id, new LabelId("Code-Review")),
            (short) 0,
            TimeUtil.nowTs());
    db.patchSetApprovals().insert(Collections.singleton(psa));

    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(r.getChangeId()).addReviewer(in);
  }

  @Test
  public void addSelfAsReviewer() throws Exception {
    TestTimeUtil.resetWithClockStep(1, SECONDS);
    PushOneCommit.Result r = createChange();
    ChangeResource rsrc = parseResource(r);
    String oldETag = rsrc.getETag();
    Timestamp oldTs = rsrc.getChange().getLastUpdatedOn();

    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    setApiUser(user);
    gApi.changes().id(r.getChangeId()).addReviewer(in);

    // There should be no email notification when adding self
    assertThat(sender.getMessages()).isEmpty();

    // When NoteDb is enabled adding a reviewer records that user as reviewer
    // in NoteDb. When NoteDb is disabled adding a reviewer results in a dummy 0
    // approval on the change which is treated as CC when the ChangeInfo is
    // created.
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    Collection<AccountInfo> reviewers = c.reviewers.get(REVIEWER);
    assertThat(reviewers).isNotNull();
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.iterator().next()._accountId).isEqualTo(user.getId().get());

    // Ensure ETag and lastUpdatedOn are updated.
    rsrc = parseResource(r);
    assertThat(rsrc.getETag()).isNotEqualTo(oldETag);
    assertThat(rsrc.getChange().getLastUpdatedOn()).isNotEqualTo(oldTs);
  }

  @Test
  public void implicitlyCcOnNonVotingReviewPgStyle() throws Exception {
    PushOneCommit.Result r = createChange();
    setApiUser(user);
    assertThat(getReviewerState(r.getChangeId(), user.id)).isEmpty();

    // Exact request format made by PG UI at ddc6b7160fe416fed9e7e3180489d44c82fd64f8.
    ReviewInput in = new ReviewInput();
    in.drafts = DraftHandling.PUBLISH_ALL_REVISIONS;
    in.labels = ImmutableMap.of();
    in.message = "comment";
    in.reviewers = ImmutableList.of();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(in);

    // If we're not reading from NoteDb, then the CCed user will be returned in the REVIEWER state.
    assertThat(getReviewerState(r.getChangeId(), user.id))
        .hasValue(notesMigration.readChanges() ? CC : REVIEWER);
  }

  @Test
  public void implicitlyCcOnNonVotingReviewGwtStyle() throws Exception {
    PushOneCommit.Result r = createChange();
    setApiUser(user);
    assertThat(getReviewerState(r.getChangeId(), user.id)).isEmpty();

    // Exact request format made by GWT UI at ddc6b7160fe416fed9e7e3180489d44c82fd64f8.
    ReviewInput in = new ReviewInput();
    in.labels = ImmutableMap.of("Code-Review", (short) 0);
    in.strictLabels = true;
    in.drafts = DraftHandling.PUBLISH_ALL_REVISIONS;
    in.message = "comment";
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(in);

    // If we're not reading from NoteDb, then the CCed user will be returned in the REVIEWER state.
    assertThat(getReviewerState(r.getChangeId(), user.id))
        .hasValue(notesMigration.readChanges() ? CC : REVIEWER);
  }

  @Test
  public void implicitlyAddReviewerOnVotingReview() throws Exception {
    PushOneCommit.Result r = createChange();
    setApiUser(user);
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.recommend().message("LGTM"));

    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    assertThat(c.reviewers.get(REVIEWER).stream().map(ai -> ai._accountId).collect(toList()))
        .containsExactly(user.id.get());

    // Further test: remove the vote, then comment again. The user should be
    // implicitly re-added to the ReviewerSet, as a CC if we're using NoteDb.
    setApiUser(admin);
    gApi.changes().id(r.getChangeId()).reviewer(user.getId().toString()).remove();
    c = gApi.changes().id(r.getChangeId()).get();
    assertThat(c.reviewers.values()).isEmpty();

    setApiUser(user);
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(new ReviewInput().message("hi"));
    c = gApi.changes().id(r.getChangeId()).get();
    ReviewerState state = notesMigration.readChanges() ? CC : REVIEWER;
    assertThat(c.reviewers.get(state).stream().map(ai -> ai._accountId).collect(toList()))
        .containsExactly(user.id.get());
  }

  @Test
  public void addReviewerToClosedChange() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();

    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    Collection<AccountInfo> reviewers = c.reviewers.get(REVIEWER);
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.iterator().next()._accountId).isEqualTo(admin.getId().get());
    assertThat(c.reviewers).doesNotContainKey(CC);

    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(r.getChangeId()).addReviewer(in);

    c = gApi.changes().id(r.getChangeId()).get();
    reviewers = c.reviewers.get(REVIEWER);
    assertThat(reviewers).hasSize(2);
    Iterator<AccountInfo> reviewerIt = reviewers.iterator();
    assertThat(reviewerIt.next()._accountId).isEqualTo(admin.getId().get());
    assertThat(reviewerIt.next()._accountId).isEqualTo(user.getId().get());
    assertThat(c.reviewers).doesNotContainKey(CC);
  }

  @Test
  public void emailNotificationForFileLevelComment() throws Exception {
    String changeId = createChange().getChangeId();

    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(changeId).addReviewer(in);
    sender.clear();

    ReviewInput review = new ReviewInput();
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();
    comment.path = PushOneCommit.FILE_NAME;
    comment.side = Side.REVISION;
    comment.message = "comment 1";
    review.comments = new HashMap<>();
    review.comments.put(comment.path, Lists.newArrayList(comment));
    gApi.changes().id(changeId).current().review(review);

    assertThat(sender.getMessages()).hasSize(1);
    Message m = sender.getMessages().get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress);
  }

  @Test
  public void invalidRange() throws Exception {
    String changeId = createChange().getChangeId();

    ReviewInput review = new ReviewInput();
    ReviewInput.CommentInput comment = new ReviewInput.CommentInput();

    comment.range = new Range();
    comment.range.startLine = 1;
    comment.range.endLine = 1;
    comment.range.startCharacter = -1;
    comment.range.endCharacter = 0;

    comment.path = PushOneCommit.FILE_NAME;
    comment.side = Side.REVISION;
    comment.message = "comment 1";
    review.comments = ImmutableMap.of(comment.path, Lists.newArrayList(comment));

    exception.expect(BadRequestException.class);
    gApi.changes().id(changeId).current().review(review);
  }

  @Test
  public void listVotes() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());

    Map<String, Short> m =
        gApi.changes().id(r.getChangeId()).reviewer(admin.getId().toString()).votes();

    assertThat(m).hasSize(1);
    assertThat(m).containsEntry("Code-Review", Short.valueOf((short) 2));

    setApiUser(user);
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.dislike());

    m = gApi.changes().id(r.getChangeId()).reviewer(user.getId().toString()).votes();

    assertThat(m).hasSize(1);
    assertThat(m).containsEntry("Code-Review", Short.valueOf((short) -1));
  }

  @Test
  public void removeReviewerNoVotes() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();

    LabelType verified =
        category("Verified", value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
    cfg.getLabelSections().put(verified.getName(), verified);

    AccountGroup.UUID registeredUsers = systemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    String heads = RefNames.REFS_HEADS + "*";
    Util.allow(cfg, Permission.forLabel(Util.verified().getName()), -1, 1, registeredUsers, heads);
    saveProjectConfig(project, cfg);

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    gApi.changes().id(changeId).addReviewer(user.getId().toString());

    // ReviewerState will vary between ReviewDb and NoteDb; we just care that it
    // shows up somewhere.
    Iterable<AccountInfo> reviewers =
        Iterables.concat(gApi.changes().id(changeId).get().reviewers.values());
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.iterator().next()._accountId).isEqualTo(user.getId().get());

    sender.clear();
    gApi.changes().id(changeId).reviewer(user.getId().toString()).remove();
    assertThat(gApi.changes().id(changeId).get().reviewers).isEmpty();

    assertThat(sender.getMessages()).hasSize(1);
    Message message = sender.getMessages().get(0);
    assertThat(message.body()).contains("Removed reviewer " + user.fullName + ".");
    assertThat(message.body()).doesNotContain("with the following votes");

    // Make sure the reviewer can still be added again.
    gApi.changes().id(changeId).addReviewer(user.getId().toString());
    reviewers = Iterables.concat(gApi.changes().id(changeId).get().reviewers.values());
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.iterator().next()._accountId).isEqualTo(user.getId().get());

    // Remove again, and then try to remove once more to verify 404 is
    // returned.
    gApi.changes().id(changeId).reviewer(user.getId().toString()).remove();
    exception.expect(ResourceNotFoundException.class);
    gApi.changes().id(changeId).reviewer(user.getId().toString()).remove();
  }

  @Test
  public void removeReviewer() throws Exception {
    testRemoveReviewer(true);
  }

  @Test
  public void removeNoNotify() throws Exception {
    testRemoveReviewer(false);
  }

  private void testRemoveReviewer(boolean notify) throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    gApi.changes().id(changeId).revision(r.getCommit().name()).review(ReviewInput.approve());

    setApiUser(user);
    gApi.changes().id(changeId).revision(r.getCommit().name()).review(ReviewInput.recommend());

    Collection<AccountInfo> reviewers = gApi.changes().id(changeId).get().reviewers.get(REVIEWER);

    assertThat(reviewers).hasSize(2);
    Iterator<AccountInfo> reviewerIt = reviewers.iterator();
    assertThat(reviewerIt.next()._accountId).isEqualTo(admin.getId().get());
    assertThat(reviewerIt.next()._accountId).isEqualTo(user.getId().get());

    sender.clear();
    setApiUser(admin);
    DeleteReviewerInput input = new DeleteReviewerInput();
    if (!notify) {
      input.notify = NotifyHandling.NONE;
    }
    gApi.changes().id(changeId).reviewer(user.getId().toString()).remove(input);

    if (notify) {
      assertThat(sender.getMessages()).hasSize(1);
      Message message = sender.getMessages().get(0);
      assertThat(message.body())
          .contains("Removed reviewer " + user.fullName + " with the following votes");
      assertThat(message.body()).contains("* Code-Review+1 by " + user.fullName);
    } else {
      assertThat(sender.getMessages()).isEmpty();
    }

    reviewers = gApi.changes().id(changeId).get().reviewers.get(REVIEWER);
    assertThat(reviewers).hasSize(1);
    reviewerIt = reviewers.iterator();
    assertThat(reviewerIt.next()._accountId).isEqualTo(admin.getId().get());

    eventRecorder.assertReviewerDeletedEvents(changeId, user.email);
  }

  @Test
  public void removeReviewerNotPermitted() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    gApi.changes().id(changeId).revision(r.getCommit().name()).review(ReviewInput.approve());

    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("delete reviewer not permitted");
    gApi.changes().id(r.getChangeId()).reviewer(admin.getId().toString()).remove();
  }

  @Test
  public void deleteVote() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());

    setApiUser(user);
    recommend(r.getChangeId());

    setApiUser(admin);
    sender.clear();
    gApi.changes().id(r.getChangeId()).reviewer(user.getId().toString()).deleteVote("Code-Review");

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message msg = messages.get(0);
    assertThat(msg.rcpt()).containsExactly(user.emailAddress);
    assertThat(msg.body()).contains(admin.fullName + " has removed a vote on this change.\n");
    assertThat(msg.body())
        .contains("Removed Code-Review+1 by " + user.fullName + " <" + user.email + ">\n");

    Map<String, Short> m =
        gApi.changes().id(r.getChangeId()).reviewer(user.getId().toString()).votes();

    // Dummy 0 approval on the change to block vote copying to this patch set.
    assertThat(m).containsExactly("Code-Review", Short.valueOf((short) 0));

    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();

    ChangeMessageInfo message = Iterables.getLast(c.messages);
    assertThat(message.author._accountId).isEqualTo(admin.getId().get());
    assertThat(message.message).isEqualTo("Removed Code-Review+1 by User <user@example.com>\n");
    assertThat(getReviewers(c.reviewers.get(REVIEWER)))
        .containsExactlyElementsIn(ImmutableSet.of(admin.getId(), user.getId()));
  }

  @Test
  public void deleteVoteNotifyNone() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());

    setApiUser(user);
    recommend(r.getChangeId());

    setApiUser(admin);
    sender.clear();
    DeleteVoteInput in = new DeleteVoteInput();
    in.label = "Code-Review";
    in.notify = NotifyHandling.NONE;
    gApi.changes().id(r.getChangeId()).reviewer(user.getId().toString()).deleteVote(in);
    assertThat(sender.getMessages()).isEmpty();
  }

  @Test
  public void deleteVoteNotifyAccount() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());

    DeleteVoteInput in = new DeleteVoteInput();
    in.label = "Code-Review";
    in.notify = NotifyHandling.NONE;

    // notify unrelated account as TO
    TestAccount user2 = accountCreator.user2();
    setApiUser(user);
    recommend(r.getChangeId());
    setApiUser(admin);
    sender.clear();
    in.notifyDetails = new HashMap<>();
    in.notifyDetails.put(RecipientType.TO, new NotifyInfo(ImmutableList.of(user2.email)));
    gApi.changes().id(r.getChangeId()).reviewer(user.getId().toString()).deleteVote(in);
    assertNotifyTo(user2);

    // notify unrelated account as CC
    setApiUser(user);
    recommend(r.getChangeId());
    setApiUser(admin);
    sender.clear();
    in.notifyDetails = new HashMap<>();
    in.notifyDetails.put(RecipientType.CC, new NotifyInfo(ImmutableList.of(user2.email)));
    gApi.changes().id(r.getChangeId()).reviewer(user.getId().toString()).deleteVote(in);
    assertNotifyCc(user2);

    // notify unrelated account as BCC
    setApiUser(user);
    recommend(r.getChangeId());
    setApiUser(admin);
    sender.clear();
    in.notifyDetails = new HashMap<>();
    in.notifyDetails.put(RecipientType.BCC, new NotifyInfo(ImmutableList.of(user2.email)));
    gApi.changes().id(r.getChangeId()).reviewer(user.getId().toString()).deleteVote(in);
    assertNotifyBcc(user2);
  }

  @Test
  public void deleteVoteNotPermitted() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());

    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("delete vote not permitted");
    gApi.changes().id(r.getChangeId()).reviewer(admin.getId().toString()).deleteVote("Code-Review");
  }

  @Test
  public void nonVotingReviewerStaysAfterSubmit() throws Exception {
    LabelType verified =
        category("Verified", value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.getLabelSections().put(verified.getName(), verified);
    String heads = "refs/heads/*";
    AccountGroup.UUID owners = systemGroupBackend.getGroup(CHANGE_OWNER).getUUID();
    AccountGroup.UUID registered = systemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    Util.allow(cfg, Permission.forLabel(verified.getName()), -1, 1, owners, heads);
    Util.allow(cfg, Permission.forLabel("Code-Review"), -2, +2, registered, heads);
    saveProjectConfig(project, cfg);

    // Set Code-Review+2 and Verified+1 as admin (change owner)
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String commit = r.getCommit().name();
    ReviewInput input = ReviewInput.approve();
    input.label(verified.getName(), 1);
    gApi.changes().id(changeId).revision(commit).review(input);

    // Reviewers should only be "admin"
    ChangeInfo c = gApi.changes().id(changeId).get();
    assertThat(getReviewers(c.reviewers.get(REVIEWER)))
        .containsExactlyElementsIn(ImmutableSet.of(admin.getId()));
    assertThat(c.reviewers.get(CC)).isNull();

    // Add the user as reviewer
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(changeId).addReviewer(in);
    c = gApi.changes().id(changeId).get();
    assertThat(getReviewers(c.reviewers.get(REVIEWER)))
        .containsExactlyElementsIn(ImmutableSet.of(admin.getId(), user.getId()));

    // Approve the change as user, then remove the approval
    // (only to confirm that the user does have Code-Review+2 permission)
    setApiUser(user);
    gApi.changes().id(changeId).revision(commit).review(ReviewInput.approve());
    gApi.changes().id(changeId).revision(commit).review(ReviewInput.noScore());

    // Submit the change
    setApiUser(admin);
    gApi.changes().id(changeId).revision(commit).submit();

    // User should still be on the change
    c = gApi.changes().id(changeId).get();
    assertThat(getReviewers(c.reviewers.get(REVIEWER)))
        .containsExactlyElementsIn(ImmutableSet.of(admin.getId(), user.getId()));
  }

  @Test
  public void createEmptyChange() throws Exception {
    ChangeInput in = new ChangeInput();
    in.branch = Constants.MASTER;
    in.subject = "Create a change from the API";
    in.project = project.get();
    ChangeInfo info = gApi.changes().create(in).get();
    assertThat(info.project).isEqualTo(in.project);
    assertThat(info.branch).isEqualTo(in.branch);
    assertThat(info.subject).isEqualTo(in.subject);
    assertThat(Iterables.getOnlyElement(info.messages).message).isEqualTo("Uploaded patch set 1.");
  }

  @Test
  public void queryChangesNoQuery() throws Exception {
    PushOneCommit.Result r = createChange();
    List<ChangeInfo> results = gApi.changes().query().get();
    assertThat(results.size()).isAtLeast(1);
    List<Integer> ids = new ArrayList<>(results.size());
    for (int i = 0; i < results.size(); i++) {
      ChangeInfo info = results.get(i);
      if (i == 0) {
        assertThat(info._number).isEqualTo(r.getChange().getId().get());
      }
      assertThat(Change.Status.forChangeStatus(info.status).isOpen()).isTrue();
      ids.add(info._number);
    }
    assertThat(ids).contains(r.getChange().getId().get());
  }

  @Test
  public void queryChangesNoResults() throws Exception {
    createChange();
    assertThat(query("message:test")).isNotEmpty();
    assertThat(query("message:{" + getClass().getName() + "fhqwhgads}")).isEmpty();
  }

  @Test
  public void queryChanges() throws Exception {
    PushOneCommit.Result r1 = createChange();
    createChange();
    List<ChangeInfo> results = query("project:{" + project.get() + "} " + r1.getChangeId());
    assertThat(Iterables.getOnlyElement(results).changeId).isEqualTo(r1.getChangeId());
  }

  @Test
  public void queryChangesLimit() throws Exception {
    createChange();
    PushOneCommit.Result r2 = createChange();
    List<ChangeInfo> results = gApi.changes().query().withLimit(1).get();
    assertThat(results).hasSize(1);
    assertThat(Iterables.getOnlyElement(results).changeId).isEqualTo(r2.getChangeId());
  }

  @Test
  public void queryChangesStart() throws Exception {
    PushOneCommit.Result r1 = createChange();
    createChange();
    List<ChangeInfo> results =
        gApi.changes().query("project:{" + project.get() + "}").withStart(1).get();
    assertThat(Iterables.getOnlyElement(results).changeId).isEqualTo(r1.getChangeId());
  }

  @Test
  public void queryChangesNoOptions() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeInfo result = Iterables.getOnlyElement(query(r.getChangeId()));
    assertThat(result.labels).isNull();
    assertThat(result.messages).isNull();
    assertThat(result.revisions).isNull();
    assertThat(result.actions).isNull();
  }

  @Test
  public void queryChangesOptions() throws Exception {
    PushOneCommit.Result r = createChange();

    ChangeInfo result = Iterables.getOnlyElement(gApi.changes().query(r.getChangeId()).get());
    assertThat(result.labels).isNull();
    assertThat(result.messages).isNull();
    assertThat(result.actions).isNull();
    assertThat(result.revisions).isNull();

    EnumSet<ListChangesOption> options =
        EnumSet.of(
            ListChangesOption.ALL_REVISIONS,
            ListChangesOption.CHANGE_ACTIONS,
            ListChangesOption.CURRENT_ACTIONS,
            ListChangesOption.DETAILED_LABELS,
            ListChangesOption.MESSAGES);
    result =
        Iterables.getOnlyElement(gApi.changes().query(r.getChangeId()).withOptions(options).get());
    assertThat(Iterables.getOnlyElement(result.labels.keySet())).isEqualTo("Code-Review");
    assertThat(result.messages).hasSize(1);
    assertThat(result.actions).isNotEmpty();

    RevisionInfo rev = Iterables.getOnlyElement(result.revisions.values());
    assertThat(rev._number).isEqualTo(r.getPatchSetId().get());
    assertThat(rev.created).isNotNull();
    assertThat(rev.uploader._accountId).isEqualTo(admin.getId().get());
    assertThat(rev.ref).isEqualTo(r.getPatchSetId().toRefName());
    assertThat(rev.actions).isNotEmpty();
  }

  @Test
  public void queryChangesOwnerWithDifferentUsers() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(
            Iterables.getOnlyElement(query("project:{" + project.get() + "} owner:self")).changeId)
        .isEqualTo(r.getChangeId());
    setApiUser(user);
    assertThat(query("owner:self project:{" + project.get() + "}")).isEmpty();
  }

  @Test
  public void checkReviewedFlagBeforeAndAfterReview() throws Exception {
    PushOneCommit.Result r = createChange();
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(r.getChangeId()).addReviewer(in);

    setApiUser(user);
    assertThat(get(r.getChangeId()).reviewed).isNull();

    revision(r).review(ReviewInput.recommend());
    assertThat(get(r.getChangeId()).reviewed).isTrue();
  }

  @Test
  public void topic() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(gApi.changes().id(r.getChangeId()).topic()).isEqualTo("");
    gApi.changes().id(r.getChangeId()).topic("mytopic");
    assertThat(gApi.changes().id(r.getChangeId()).topic()).isEqualTo("mytopic");
    gApi.changes().id(r.getChangeId()).topic("");
    assertThat(gApi.changes().id(r.getChangeId()).topic()).isEqualTo("");
  }

  @Test
  public void editTopicWithoutPermissionNotAllowed() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(gApi.changes().id(r.getChangeId()).topic()).isEqualTo("");
    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("edit topic name not permitted");
    gApi.changes().id(r.getChangeId()).topic("mytopic");
  }

  @Test
  public void editTopicWithPermissionAllowed() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(gApi.changes().id(r.getChangeId()).topic()).isEqualTo("");
    grant(project, "refs/heads/master", Permission.EDIT_TOPIC_NAME, false, REGISTERED_USERS);
    setApiUser(user);
    gApi.changes().id(r.getChangeId()).topic("mytopic");
    assertThat(gApi.changes().id(r.getChangeId()).topic()).isEqualTo("mytopic");
  }

  @Test
  public void submitted() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    assertThat(gApi.changes().id(r.getChangeId()).info().submitted).isNull();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();
    assertThat(gApi.changes().id(r.getChangeId()).info().submitted).isNotNull();
  }

  @Test
  public void submitStaleChange() throws Exception {
    PushOneCommit.Result r = createChange();

    disableChangeIndexWrites();
    try {
      r = amendChange(r.getChangeId());
    } finally {
      enableChangeIndexWrites();
    }

    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());

    gApi.changes().id(r.getChangeId()).current().submit();
    assertThat(gApi.changes().id(r.getChangeId()).info().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void submitNotAllowedWithoutPermission() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("submit not permitted");
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();
  }

  @Test
  public void submitAllowedWithPermission() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    grant(project, "refs/heads/master", Permission.SUBMIT, false, REGISTERED_USERS);
    setApiUser(user);
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();
    assertThat(gApi.changes().id(r.getChangeId()).info().status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void check() throws Exception {
    // TODO(dborowitz): Re-enable when ConsistencyChecker supports NoteDb.
    assume().that(notesMigration.enabled()).isFalse();
    PushOneCommit.Result r = createChange();
    assertThat(gApi.changes().id(r.getChangeId()).get().problems).isNull();
    assertThat(gApi.changes().id(r.getChangeId()).get(EnumSet.of(ListChangesOption.CHECK)).problems)
        .isEmpty();
  }

  @Test
  public void commitFooters() throws Exception {
    LabelType verified =
        category("Verified", value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
    LabelType custom1 =
        category("Custom1", value(1, "Positive"), value(0, "No score"), value(-1, "Negative"));
    LabelType custom2 =
        category("Custom2", value(1, "Positive"), value(0, "No score"), value(-1, "Negative"));
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.getLabelSections().put(verified.getName(), verified);
    cfg.getLabelSections().put(custom1.getName(), custom1);
    cfg.getLabelSections().put(custom2.getName(), custom2);
    String heads = "refs/heads/*";
    AccountGroup.UUID anon = systemGroupBackend.getGroup(ANONYMOUS_USERS).getUUID();
    Util.allow(cfg, Permission.forLabel("Verified"), -1, 1, anon, heads);
    Util.allow(cfg, Permission.forLabel("Custom1"), -1, 1, anon, heads);
    Util.allow(cfg, Permission.forLabel("Custom2"), -1, 1, anon, heads);
    saveProjectConfig(project, cfg);

    PushOneCommit.Result r1 = createChange();
    r1.assertOkStatus();
    PushOneCommit.Result r2 =
        pushFactory
            .create(
                db, admin.getIdent(), testRepo, SUBJECT, FILE_NAME, "new content", r1.getChangeId())
            .to("refs/for/master");
    r2.assertOkStatus();

    ReviewInput in = new ReviewInput();
    in.label("Code-Review", 1);
    in.label("Verified", 1);
    in.label("Custom1", -1);
    in.label("Custom2", 1);
    gApi.changes().id(r2.getChangeId()).current().review(in);

    EnumSet<ListChangesOption> options =
        EnumSet.of(ListChangesOption.ALL_REVISIONS, ListChangesOption.COMMIT_FOOTERS);
    ChangeInfo actual = gApi.changes().id(r2.getChangeId()).get(options);
    assertThat(actual.revisions).hasSize(2);

    // No footers except on latest patch set.
    assertThat(actual.revisions.get(r1.getCommit().getName()).commitWithFooters).isNull();

    List<String> footers =
        new ArrayList<>(
            Arrays.asList(
                actual.revisions.get(r2.getCommit().getName()).commitWithFooters.split("\\n")));
    // remove subject + blank line
    footers.remove(0);
    footers.remove(0);

    List<String> expectedFooters =
        Arrays.asList(
            "Change-Id: " + r2.getChangeId(),
            "Reviewed-on: " + canonicalWebUrl.get() + r2.getChange().getId(),
            "Reviewed-by: Administrator <admin@example.com>",
            "Custom2: Administrator <admin@example.com>",
            "Tested-by: Administrator <admin@example.com>");

    assertThat(footers).containsExactlyElementsIn(expectedFooters);
  }

  @Test
  public void customCommitFooters() throws Exception {
    PushOneCommit.Result change = createChange();
    RegistrationHandle handle =
        changeMessageModifiers.add(
            new ChangeMessageModifier() {
              @Override
              public String onSubmit(
                  String newCommitMessage,
                  RevCommit original,
                  RevCommit mergeTip,
                  Branch.NameKey destination) {
                assertThat(original.getName()).isNotEqualTo(mergeTip.getName());
                return newCommitMessage + "Custom: " + destination.get();
              }
            });
    ChangeInfo actual;
    try {
      EnumSet<ListChangesOption> options =
          EnumSet.of(ListChangesOption.ALL_REVISIONS, ListChangesOption.COMMIT_FOOTERS);
      actual = gApi.changes().id(change.getChangeId()).get(options);
    } finally {
      handle.remove();
    }
    List<String> footers =
        new ArrayList<>(
            Arrays.asList(
                actual.revisions.get(change.getCommit().getName()).commitWithFooters.split("\\n")));
    // remove subject + blank line
    footers.remove(0);
    footers.remove(0);

    List<String> expectedFooters =
        Arrays.asList(
            "Change-Id: " + change.getChangeId(),
            "Reviewed-on: " + canonicalWebUrl.get() + change.getChange().getId(),
            "Custom: refs/heads/master");
    assertThat(footers).containsExactlyElementsIn(expectedFooters);
  }

  @Test
  public void defaultSearchDoesNotTouchDatabase() throws Exception {
    setApiUser(admin);
    PushOneCommit.Result r1 = createChange();
    gApi.changes()
        .id(r1.getChangeId())
        .revision(r1.getCommit().name())
        .review(ReviewInput.approve());
    gApi.changes().id(r1.getChangeId()).revision(r1.getCommit().name()).submit();

    createChange();
    createDraftChange();

    setApiUser(user);
    AcceptanceTestRequestScope.Context ctx = disableDb();
    try {
      assertThat(
              gApi.changes()
                  .query()
                  .withQuery("project:{" + project.get() + "} (status:open OR status:closed)")
                  // Options should match defaults in AccountDashboardScreen.
                  .withOption(ListChangesOption.LABELS)
                  .withOption(ListChangesOption.DETAILED_ACCOUNTS)
                  .withOption(ListChangesOption.REVIEWED)
                  .get())
          .hasSize(2);
    } finally {
      enableDb(ctx);
    }
  }

  @Test
  public void votable() throws Exception {
    PushOneCommit.Result r = createChange();
    String triplet = project.get() + "~master~" + r.getChangeId();
    gApi.changes().id(triplet).addReviewer(user.username);
    ChangeInfo c = gApi.changes().id(triplet).get(EnumSet.of(ListChangesOption.DETAILED_LABELS));
    LabelInfo codeReview = c.labels.get("Code-Review");
    assertThat(codeReview.all).hasSize(1);
    ApprovalInfo approval = codeReview.all.get(0);
    assertThat(approval._accountId).isEqualTo(user.id.get());
    assertThat(approval.value).isEqualTo(0);

    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    blockLabel(cfg, "Code-Review", REGISTERED_USERS, "refs/heads/*");
    saveProjectConfig(project, cfg);
    c = gApi.changes().id(triplet).get(EnumSet.of(ListChangesOption.DETAILED_LABELS));
    codeReview = c.labels.get("Code-Review");
    assertThat(codeReview.all).hasSize(1);
    approval = codeReview.all.get(0);
    assertThat(approval._accountId).isEqualTo(user.id.get());
    assertThat(approval.value).isNull();
  }

  @Test
  @GerritConfig(name = "gerrit.editGpgKeys", value = "true")
  @GerritConfig(name = "receive.enableSignedPush", value = "true")
  public void pushCertificates() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = amendChange(r1.getChangeId());

    ChangeInfo info =
        gApi.changes()
            .id(r1.getChangeId())
            .get(EnumSet.of(ListChangesOption.ALL_REVISIONS, ListChangesOption.PUSH_CERTIFICATES));

    RevisionInfo rev1 = info.revisions.get(r1.getCommit().name());
    assertThat(rev1).isNotNull();
    assertThat(rev1.pushCertificate).isNotNull();
    assertThat(rev1.pushCertificate.certificate).isNull();
    assertThat(rev1.pushCertificate.key).isNull();

    RevisionInfo rev2 = info.revisions.get(r2.getCommit().name());
    assertThat(rev2).isNotNull();
    assertThat(rev2.pushCertificate).isNotNull();
    assertThat(rev2.pushCertificate.certificate).isNull();
    assertThat(rev2.pushCertificate.key).isNull();
  }

  @Test
  public void anonymousRestApi() throws Exception {
    setApiUserAnonymous();
    PushOneCommit.Result r = createChange();

    ChangeInfo info = gApi.changes().id(r.getChangeId()).get();
    assertThat(info.changeId).isEqualTo(r.getChangeId());

    String triplet = project.get() + "~master~" + r.getChangeId();
    info = gApi.changes().id(triplet).get();
    assertThat(info.changeId).isEqualTo(r.getChangeId());

    info = gApi.changes().id(info._number).get();
    assertThat(info.changeId).isEqualTo(r.getChangeId());

    exception.expect(AuthException.class);
    gApi.changes().id(triplet).current().review(ReviewInput.approve());
  }

  @Test
  public void noteDbCommitsOnPatchSetCreation() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();

    PushOneCommit.Result r = createChange();
    pushFactory
        .create(
            db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, "b.txt", "4711", r.getChangeId())
        .to("refs/for/master")
        .assertOkStatus();
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit commitPatchSetCreation =
          rw.parseCommit(repo.exactRef(changeMetaRef(new Change.Id(c._number))).getObjectId());

      assertThat(commitPatchSetCreation.getShortMessage()).isEqualTo("Create patch set 2");
      PersonIdent expectedAuthor =
          changeNoteUtil.newIdent(
              accountCache.get(admin.id).getAccount(), c.updated,
              serverIdent.get(), AnonymousCowardNameProvider.DEFAULT);
      assertThat(commitPatchSetCreation.getAuthorIdent()).isEqualTo(expectedAuthor);
      assertThat(commitPatchSetCreation.getCommitterIdent())
          .isEqualTo(new PersonIdent(serverIdent.get(), c.updated));
      assertThat(commitPatchSetCreation.getParentCount()).isEqualTo(1);

      RevCommit commitChangeCreation = rw.parseCommit(commitPatchSetCreation.getParent(0));
      assertThat(commitChangeCreation.getShortMessage()).isEqualTo("Create change");
      expectedAuthor =
          changeNoteUtil.newIdent(
              accountCache.get(admin.id).getAccount(),
              c.created,
              serverIdent.get(),
              AnonymousCowardNameProvider.DEFAULT);
      assertThat(commitChangeCreation.getAuthorIdent()).isEqualTo(expectedAuthor);
      assertThat(commitChangeCreation.getCommitterIdent())
          .isEqualTo(new PersonIdent(serverIdent.get(), c.created));
      assertThat(commitChangeCreation.getParentCount()).isEqualTo(0);
    }
  }

  @Test
  public void createEmptyChangeOnNonExistingBranch() throws Exception {
    ChangeInput in = new ChangeInput();
    in.branch = "foo";
    in.subject = "Create a change on new branch from the API";
    in.project = project.get();
    in.newBranch = true;
    ChangeInfo info = gApi.changes().create(in).get();
    assertThat(info.project).isEqualTo(in.project);
    assertThat(info.branch).isEqualTo(in.branch);
    assertThat(info.subject).isEqualTo(in.subject);
    assertThat(Iterables.getOnlyElement(info.messages).message).isEqualTo("Uploaded patch set 1.");
  }

  @Test
  public void createEmptyChangeOnExistingBranchWithNewBranch() throws Exception {
    ChangeInput in = new ChangeInput();
    in.branch = Constants.MASTER;
    in.subject = "Create a change on new branch from the API";
    in.project = project.get();
    in.newBranch = true;

    exception.expect(ResourceConflictException.class);
    gApi.changes().create(in).get();
  }

  @Test
  public void createNewPatchSetOnVisibleDraftPatchSet() throws Exception {
    // Clone separate repositories of the same project as admin and as user
    TestRepository<InMemoryRepository> adminTestRepo = cloneProject(project, admin);
    TestRepository<InMemoryRepository> userTestRepo = cloneProject(project, user);

    // Create change as admin
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), adminTestRepo);
    PushOneCommit.Result r1 = push.to("refs/for/master");
    r1.assertOkStatus();

    // Amend draft as admin
    PushOneCommit.Result r2 =
        amendChange(r1.getChangeId(), "refs/drafts/master", admin, adminTestRepo);
    r2.assertOkStatus();

    // Add user as reviewer to make this patch set visible
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(r1.getChangeId()).addReviewer(in);

    // Fetch change
    GitUtil.fetch(userTestRepo, r2.getPatchSet().getRefName() + ":ps");
    userTestRepo.reset("ps");

    // Amend change as user
    PushOneCommit.Result r3 =
        amendChange(r2.getChangeId(), "refs/drafts/master", user, userTestRepo);
    r3.assertOkStatus();
  }

  @Test
  public void createNewPatchSetOnInvisibleDraftPatchSet() throws Exception {
    // Clone separate repositories of the same project as admin and as user
    TestRepository<InMemoryRepository> adminTestRepo = cloneProject(project, admin);
    TestRepository<InMemoryRepository> userTestRepo = cloneProject(project, user);

    // Create change as admin
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), adminTestRepo);
    PushOneCommit.Result r1 = push.to("refs/for/master");
    r1.assertOkStatus();

    // Amend draft as admin
    PushOneCommit.Result r2 =
        amendChange(r1.getChangeId(), "refs/drafts/master", admin, adminTestRepo);
    r2.assertOkStatus();

    // Fetch change
    GitUtil.fetch(userTestRepo, r1.getPatchSet().getRefName() + ":ps");
    userTestRepo.reset("ps");

    // Amend change as user
    PushOneCommit.Result r3 = amendChange(r1.getChangeId(), "refs/for/master", user, userTestRepo);
    r3.assertErrorStatus("cannot add patch set to " + r3.getChange().change().getChangeId() + ".");
  }

  @Test
  public void createNewPatchSetWithoutPermission() throws Exception {
    // Create new project with clean permissions
    Project.NameKey p = createProject("addPatchSet1");

    // Clone separate repositories of the same project as admin and as user
    TestRepository<InMemoryRepository> adminTestRepo = cloneProject(p, admin);
    TestRepository<InMemoryRepository> userTestRepo = cloneProject(p, user);

    // Block default permission
    block(p, "refs/for/*", Permission.ADD_PATCH_SET, REGISTERED_USERS);

    // Create change as admin
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), adminTestRepo);
    PushOneCommit.Result r1 = push.to("refs/for/master");
    r1.assertOkStatus();

    // Fetch change
    GitUtil.fetch(userTestRepo, r1.getPatchSet().getRefName() + ":ps");
    userTestRepo.reset("ps");

    // Amend change as user
    PushOneCommit.Result r2 = amendChange(r1.getChangeId(), "refs/for/master", user, userTestRepo);
    r2.assertErrorStatus("cannot add patch set to " + r1.getChange().getId().id + ".");
  }

  @Test
  public void createNewSetPatchWithPermission() throws Exception {
    // Clone separate repositories of the same project as admin and as user
    TestRepository<?> adminTestRepo = cloneProject(project, admin);
    TestRepository<?> userTestRepo = cloneProject(project, user);

    // Create change as admin
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), adminTestRepo);
    PushOneCommit.Result r1 = push.to("refs/for/master");
    r1.assertOkStatus();

    // Fetch change
    GitUtil.fetch(userTestRepo, r1.getPatchSet().getRefName() + ":ps");
    userTestRepo.reset("ps");

    // Amend change as user
    PushOneCommit.Result r2 = amendChange(r1.getChangeId(), "refs/for/master", user, userTestRepo);
    r2.assertOkStatus();
  }

  @Test
  public void createNewPatchSetAsOwnerWithoutPermission() throws Exception {
    // Create new project with clean permissions
    Project.NameKey p = createProject("addPatchSet2");
    // Clone separate repositories of the same project as admin and as user
    TestRepository<?> adminTestRepo = cloneProject(project, admin);

    // Block default permission
    block(p, "refs/for/*", Permission.ADD_PATCH_SET, REGISTERED_USERS);

    // Create change as admin
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), adminTestRepo);
    PushOneCommit.Result r1 = push.to("refs/for/master");
    r1.assertOkStatus();

    // Fetch change
    GitUtil.fetch(adminTestRepo, r1.getPatchSet().getRefName() + ":ps");
    adminTestRepo.reset("ps");

    // Amend change as admin
    PushOneCommit.Result r2 =
        amendChange(r1.getChangeId(), "refs/for/master", admin, adminTestRepo);
    r2.assertOkStatus();
  }

  @Test
  public void createNewPatchSetAsReviewerOnDraftChange() throws Exception {
    // Clone separate repositories of the same project as admin and as user
    TestRepository<?> adminTestRepo = cloneProject(project, admin);
    TestRepository<?> userTestRepo = cloneProject(project, user);

    // Create change as admin
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), adminTestRepo);
    PushOneCommit.Result r1 = push.to("refs/drafts/master");
    r1.assertOkStatus();

    // Add user as reviewer
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(r1.getChangeId()).addReviewer(in);

    // Fetch change
    GitUtil.fetch(userTestRepo, r1.getPatchSet().getRefName() + ":ps");
    userTestRepo.reset("ps");

    // Amend change as user
    PushOneCommit.Result r2 = amendChange(r1.getChangeId(), "refs/for/master", user, userTestRepo);
    r2.assertOkStatus();
  }

  @Test
  public void createNewDraftPatchSetOnDraftChange() throws Exception {
    // Create new project with clean permissions
    Project.NameKey p = createProject("addPatchSet4");
    // Clone separate repositories of the same project as admin and as user
    TestRepository<?> adminTestRepo = cloneProject(p, admin);
    TestRepository<?> userTestRepo = cloneProject(p, user);

    // Block default permission
    block(p, "refs/for/*", Permission.ADD_PATCH_SET, REGISTERED_USERS);

    // Create change as admin
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), adminTestRepo);
    PushOneCommit.Result r1 = push.to("refs/drafts/master");
    r1.assertOkStatus();

    // Add user as reviewer
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes().id(r1.getChangeId()).addReviewer(in);

    // Fetch change
    GitUtil.fetch(userTestRepo, r1.getPatchSet().getRefName() + ":ps");
    userTestRepo.reset("ps");

    // Amend change as user
    PushOneCommit.Result r2 =
        amendChange(r1.getChangeId(), "refs/drafts/master", user, userTestRepo);
    r2.assertErrorStatus("cannot add patch set to " + r1.getChange().getId().id + ".");
  }

  @Test
  public void createMergePatchSet() throws Exception {
    PushOneCommit.Result start = pushTo("refs/heads/master");
    start.assertOkStatus();
    // create a change for master
    PushOneCommit.Result r = createChange();
    r.assertOkStatus();
    String changeId = r.getChangeId();

    testRepo.reset(start.getCommit());
    PushOneCommit.Result currentMaster = pushTo("refs/heads/master");
    currentMaster.assertOkStatus();
    String parent = currentMaster.getCommit().getName();

    // push a commit into dev branch
    createBranch(new Branch.NameKey(project, "dev"));
    PushOneCommit.Result changeA =
        pushFactory
            .create(db, user.getIdent(), testRepo, "change A", "A.txt", "A content")
            .to("refs/heads/dev");
    changeA.assertOkStatus();
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "dev";
    MergePatchSetInput in = new MergePatchSetInput();
    in.merge = mergeInput;
    in.subject = "update change by merge ps2";
    gApi.changes().id(changeId).createMergePatchSet(in);
    ChangeInfo changeInfo =
        gApi.changes()
            .id(changeId)
            .get(
                EnumSet.of(
                    ListChangesOption.ALL_REVISIONS,
                    ListChangesOption.CURRENT_COMMIT,
                    ListChangesOption.CURRENT_REVISION));
    assertThat(changeInfo.revisions.size()).isEqualTo(2);
    assertThat(changeInfo.subject).isEqualTo(in.subject);
    assertThat(changeInfo.revisions.get(changeInfo.currentRevision).commit.parents.get(0).commit)
        .isEqualTo(parent);
  }

  @Test
  public void createMergePatchSetInheritParent() throws Exception {
    PushOneCommit.Result start = pushTo("refs/heads/master");
    start.assertOkStatus();
    // create a change for master
    PushOneCommit.Result r = createChange();
    r.assertOkStatus();
    String changeId = r.getChangeId();
    String parent = r.getCommit().getParent(0).getName();

    // advance master branch
    testRepo.reset(start.getCommit());
    PushOneCommit.Result currentMaster = pushTo("refs/heads/master");
    currentMaster.assertOkStatus();

    // push a commit into dev branch
    createBranch(new Branch.NameKey(project, "dev"));
    PushOneCommit.Result changeA =
        pushFactory
            .create(db, user.getIdent(), testRepo, "change A", "A.txt", "A content")
            .to("refs/heads/dev");
    changeA.assertOkStatus();
    MergeInput mergeInput = new MergeInput();
    mergeInput.source = "dev";
    MergePatchSetInput in = new MergePatchSetInput();
    in.merge = mergeInput;
    in.subject = "update change by merge ps2 inherit parent of ps1";
    in.inheritParent = true;
    gApi.changes().id(changeId).createMergePatchSet(in);
    ChangeInfo changeInfo =
        gApi.changes()
            .id(changeId)
            .get(
                EnumSet.of(
                    ListChangesOption.ALL_REVISIONS,
                    ListChangesOption.CURRENT_COMMIT,
                    ListChangesOption.CURRENT_REVISION));

    assertThat(changeInfo.revisions.size()).isEqualTo(2);
    assertThat(changeInfo.subject).isEqualTo(in.subject);
    assertThat(changeInfo.revisions.get(changeInfo.currentRevision).commit.parents.get(0).commit)
        .isEqualTo(parent);
    assertThat(changeInfo.revisions.get(changeInfo.currentRevision).commit.parents.get(0).commit)
        .isNotEqualTo(currentMaster.getCommit().getName());
  }

  @Test
  public void checkLabelsForOpenChange() throws Exception {
    PushOneCommit.Result r = createChange();
    ChangeInfo change = gApi.changes().id(r.getChangeId()).get();
    assertThat(change.status).isEqualTo(ChangeStatus.NEW);
    assertThat(change.labels.keySet()).containsExactly("Code-Review");
    assertThat(change.permittedLabels.keySet()).containsExactly("Code-Review");

    // add new label and assert that it's returned for existing changes
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    LabelType verified = Util.verified();
    cfg.getLabelSections().put(verified.getName(), verified);
    AccountGroup.UUID registeredUsers = systemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    String heads = RefNames.REFS_HEADS + "*";
    Util.allow(cfg, Permission.forLabel(verified.getName()), -1, 1, registeredUsers, heads);
    saveProjectConfig(project, cfg);

    change = gApi.changes().id(r.getChangeId()).get();
    assertThat(change.labels.keySet()).containsExactly("Code-Review", "Verified");
    assertThat(change.permittedLabels.keySet()).containsExactly("Code-Review", "Verified");
    assertPermitted(change, "Code-Review", -2, -1, 0, 1, 2);
    assertPermitted(change, "Verified", -1, 0, 1);

    // add an approval on the new label
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(new ReviewInput().label(verified.getName(), verified.getMax().getValue()));

    // remove label and assert that it's no longer returned for existing
    // changes, even if there is an approval for it
    cfg.getLabelSections().remove(verified.getName());
    Util.remove(cfg, Permission.forLabel(verified.getName()), registeredUsers, heads);
    saveProjectConfig(project, cfg);

    change = gApi.changes().id(r.getChangeId()).get();
    assertThat(change.labels.keySet()).containsExactly("Code-Review");
    assertThat(change.permittedLabels.keySet()).containsExactly("Code-Review");
  }

  @Test
  public void checkLabelsForMergedChange() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();

    ChangeInfo change = gApi.changes().id(r.getChangeId()).get();
    assertThat(change.status).isEqualTo(ChangeStatus.MERGED);
    assertThat(change.labels.keySet()).containsExactly("Code-Review");
    assertThat(change.permittedLabels.keySet()).containsExactly("Code-Review");
    assertPermitted(change, "Code-Review", 2);

    // add new label and assert that it's returned for existing changes
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    LabelType verified = Util.verified();
    cfg.getLabelSections().put(verified.getName(), verified);
    AccountGroup.UUID registeredUsers = systemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    String heads = RefNames.REFS_HEADS + "*";
    Util.allow(cfg, Permission.forLabel(verified.getName()), -1, 1, registeredUsers, heads);
    saveProjectConfig(project, cfg);

    change = gApi.changes().id(r.getChangeId()).get();
    assertThat(change.labels.keySet()).containsExactly("Code-Review", "Verified");
    assertThat(change.permittedLabels.keySet()).containsExactly("Code-Review", "Verified");
    assertPermitted(change, "Code-Review", 2);
    assertPermitted(change, "Verified", 0, 1);

    // ignore the new label by Prolog submit rule and assert that the label is
    // no longer returned
    GitUtil.fetch(testRepo, RefNames.REFS_CONFIG + ":config");
    testRepo.reset("config");
    PushOneCommit push2 =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            "Ignore Verified",
            "rules.pl",
            "submit_rule(submit(CR)) :-\n  gerrit:max_with_block(-2, 2, 'Code-Review', CR).");
    push2.to(RefNames.REFS_CONFIG);

    change = gApi.changes().id(r.getChangeId()).get();
    assertPermitted(change, "Code-Review", 2);
    assertPermitted(change, "Verified");

    // add an approval on the new label and assert that the label is now
    // returned although it is ignored by the Prolog submit rule and hence not
    // included in the submit records
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(new ReviewInput().label(verified.getName(), verified.getMax().getValue()));

    change = gApi.changes().id(r.getChangeId()).get();
    assertThat(change.labels.keySet()).containsExactly("Code-Review", "Verified");
    assertPermitted(change, "Code-Review", 2);
    assertPermitted(change, "Verified");

    // remove label and assert that it's no longer returned for existing
    // changes, even if there is an approval for it
    cfg = projectCache.checkedGet(project).getConfig();
    cfg.getLabelSections().remove(verified.getName());
    Util.remove(cfg, Permission.forLabel(verified.getName()), registeredUsers, heads);
    saveProjectConfig(project, cfg);

    change = gApi.changes().id(r.getChangeId()).get();
    assertThat(change.labels.keySet()).containsExactly("Code-Review");
    assertThat(change.permittedLabels.keySet()).containsExactly("Code-Review");
    assertPermitted(change, "Code-Review", 2);
  }

  @Test
  public void checkLabelsForMergedChangeWithNonAuthorCodeReview() throws Exception {
    // Configure Non-Author-Code-Review
    RevCommit oldHead = getRemoteHead();
    GitUtil.fetch(testRepo, RefNames.REFS_CONFIG + ":config");
    testRepo.reset("config");
    PushOneCommit push2 =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            "Configure Non-Author-Code-Review",
            "rules.pl",
            "submit_rule(S) :-\n"
                + "  gerrit:default_submit(X),\n"
                + "  X =.. [submit | Ls],\n"
                + "  add_non_author_approval(Ls, R),\n"
                + "  S =.. [submit | R].\n"
                + "\n"
                + "add_non_author_approval(S1, S2) :-\n"
                + "  gerrit:commit_author(A),\n"
                + "  gerrit:commit_label(label('Code-Review', 2), R),\n"
                + "  R \\= A, !,\n"
                + "  S2 = [label('Non-Author-Code-Review', ok(R)) | S1].\n"
                + "add_non_author_approval(S1,"
                + " [label('Non-Author-Code-Review', need(_)) | S1]).");
    push2.to(RefNames.REFS_CONFIG);
    testRepo.reset(oldHead);

    // Allow user to approve
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    AccountGroup.UUID registeredUsers = systemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    String heads = RefNames.REFS_HEADS + "*";
    Util.allow(
        cfg, Permission.forLabel(Util.codeReview().getName()), -2, 2, registeredUsers, heads);
    saveProjectConfig(project, cfg);

    PushOneCommit.Result r = createChange();

    setApiUser(user);
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(ReviewInput.approve());

    setApiUser(admin);
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).submit();

    ChangeInfo change = gApi.changes().id(r.getChangeId()).get();
    assertThat(change.status).isEqualTo(ChangeStatus.MERGED);
    assertThat(change.labels.keySet()).containsExactly("Code-Review", "Non-Author-Code-Review");
    assertThat(change.permittedLabels.keySet()).containsExactly("Code-Review");
    assertPermitted(change, "Code-Review", 0, 1, 2);
  }

  @Test
  public void checkLabelsForAutoClosedChange() throws Exception {
    PushOneCommit.Result r = createChange();

    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    PushOneCommit.Result result = push.to("refs/heads/master");
    result.assertOkStatus();

    ChangeInfo change = gApi.changes().id(r.getChangeId()).get();
    assertThat(change.status).isEqualTo(ChangeStatus.MERGED);
    assertThat(change.labels.keySet()).containsExactly("Code-Review");
    assertPermitted(change, "Code-Review", 0, 1, 2);
  }

  @Test
  public void checkLabelsForAbandonedChange() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes().id(r.getChangeId()).abandon();

    ChangeInfo change = gApi.changes().id(r.getChangeId()).get();
    assertThat(change.status).isEqualTo(ChangeStatus.ABANDONED);
    assertThat(change.labels).isEmpty();
    assertThat(change.permittedLabels).isEmpty();
  }

  @Test
  public void maxPermittedValueAllowed() throws Exception {
    final int minPermittedValue = -2;
    final int maxPermittedValue = +2;
    String heads = "refs/heads/*";

    PushOneCommit.Result r = createChange();
    String triplet = project.get() + "~master~" + r.getChangeId();

    gApi.changes().id(triplet).addReviewer(user.username);

    ChangeInfo c = gApi.changes().id(triplet).get(EnumSet.of(ListChangesOption.DETAILED_LABELS));
    LabelInfo codeReview = c.labels.get("Code-Review");
    assertThat(codeReview.all).hasSize(1);
    ApprovalInfo approval = codeReview.all.get(0);
    assertThat(approval._accountId).isEqualTo(user.id.get());
    assertThat(approval.permittedVotingRange).isNotNull();
    // default values
    assertThat(approval.permittedVotingRange.min).isEqualTo(-1);
    assertThat(approval.permittedVotingRange.max).isEqualTo(1);

    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    Util.allow(
        cfg,
        Permission.forLabel("Code-Review"),
        minPermittedValue,
        maxPermittedValue,
        REGISTERED_USERS,
        heads);
    saveProjectConfig(project, cfg);

    c = gApi.changes().id(triplet).get(EnumSet.of(ListChangesOption.DETAILED_LABELS));
    codeReview = c.labels.get("Code-Review");
    assertThat(codeReview.all).hasSize(1);
    approval = codeReview.all.get(0);
    assertThat(approval._accountId).isEqualTo(user.id.get());
    assertThat(approval.permittedVotingRange).isNotNull();
    assertThat(approval.permittedVotingRange.min).isEqualTo(minPermittedValue);
    assertThat(approval.permittedVotingRange.max).isEqualTo(maxPermittedValue);
  }

  @Test
  public void maxPermittedValueBlocked() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    blockLabel(cfg, "Code-Review", REGISTERED_USERS, "refs/heads/*");
    saveProjectConfig(project, cfg);

    PushOneCommit.Result r = createChange();
    String triplet = project.get() + "~master~" + r.getChangeId();

    gApi.changes().id(triplet).addReviewer(user.username);

    ChangeInfo c = gApi.changes().id(triplet).get(EnumSet.of(ListChangesOption.DETAILED_LABELS));
    LabelInfo codeReview = c.labels.get("Code-Review");
    assertThat(codeReview.all).hasSize(1);
    ApprovalInfo approval = codeReview.all.get(0);
    assertThat(approval._accountId).isEqualTo(user.id.get());
    assertThat(approval.permittedVotingRange).isNull();
  }

  @Sandboxed
  @Test
  public void unresolvedCommentsBlocked() throws Exception {
    RevCommit oldHead = getRemoteHead();
    GitUtil.fetch(testRepo, RefNames.REFS_CONFIG + ":config");
    testRepo.reset("config");
    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            "Configure",
            "rules.pl",
            "submit_rule(submit(R)) :- \n"
                + "gerrit:unresolved_comments_count(0), \n"
                + "!,"
                + "gerrit:commit_author(A), \n"
                + "R = label('All-Comments-Resolved', ok(A)).\n"
                + "submit_rule(submit(R)) :- \n"
                + "gerrit:unresolved_comments_count(U), \n"
                + "U > 0,"
                + "R = label('All-Comments-Resolved', need(_)). \n\n");

    push.to(RefNames.REFS_CONFIG);
    testRepo.reset(oldHead);

    oldHead = getRemoteHead();
    PushOneCommit.Result result1 =
        pushFactory.create(db, user.getIdent(), testRepo).to("refs/for/master");
    testRepo.reset(oldHead);
    PushOneCommit.Result result2 =
        pushFactory.create(db, user.getIdent(), testRepo).to("refs/for/master");

    addComment(result1, "comment 1", true, false, null);
    addComment(result2, "comment 2", true, true, null);

    gApi.changes().id(result1.getChangeId()).current().submit();

    exception.expect(ResourceConflictException.class);
    exception.expectMessage(
        "Failed to submit 1 change due to the following problems:\n"
            + "Change 2: needs All-Comments-Resolved");
    gApi.changes().id(result2.getChangeId()).current().submit();
  }

  private void addComment(
      PushOneCommit.Result r,
      String message,
      boolean omitDuplicateComments,
      Boolean unresolved,
      String inReplyTo)
      throws Exception {
    ReviewInput.CommentInput c = new ReviewInput.CommentInput();
    c.line = 1;
    c.message = message;
    c.path = FILE_NAME;
    c.unresolved = unresolved;
    c.inReplyTo = inReplyTo;
    ReviewInput in = new ReviewInput();
    in.comments = new HashMap<>();
    in.comments.put(c.path, Lists.newArrayList(c));
    in.omitDuplicateComments = omitDuplicateComments;
    gApi.changes().id(r.getChangeId()).revision(r.getCommit().name()).review(in);
  }

  private static Iterable<Account.Id> getReviewers(Collection<AccountInfo> r) {
    return Iterables.transform(r, a -> new Account.Id(a._accountId));
  }

  private ChangeResource parseResource(PushOneCommit.Result r) throws Exception {
    List<ChangeControl> ctls = changeFinder.find(r.getChangeId(), atrScope.get().getUser());
    assertThat(ctls).hasSize(1);
    return changeResourceFactory.create(ctls.get(0));
  }

  private Optional<ReviewerState> getReviewerState(String changeId, Account.Id accountId)
      throws Exception {
    ChangeInfo c = gApi.changes().id(changeId).get(EnumSet.of(ListChangesOption.DETAILED_LABELS));
    Set<ReviewerState> states =
        c.reviewers
            .entrySet()
            .stream()
            .filter(e -> e.getValue().stream().anyMatch(a -> a._accountId == accountId.get()))
            .map(e -> e.getKey())
            .collect(toSet());
    assertThat(states.size()).named(states.toString()).isAtMost(1);
    return states.stream().findFirst();
  }

  private void setChangeStatus(Change.Id id, Change.Status newStatus) throws Exception {
    try (BatchUpdate batchUpdate =
        batchUpdateFactory.create(db, project, atrScope.get().getUser(), TimeUtil.nowTs())) {
      batchUpdate.addOp(id, new ChangeStatusUpdateOp(newStatus));
      batchUpdate.execute();
    }

    ChangeStatus changeStatus = gApi.changes().id(id.get()).get().status;
    assertThat(changeStatus).isEqualTo(newStatus.asChangeStatus());
  }

  private static class ChangeStatusUpdateOp implements BatchUpdateOp {
    private final Change.Status newStatus;

    ChangeStatusUpdateOp(Change.Status newStatus) {
      this.newStatus = newStatus;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws Exception {
      Change change = ctx.getChange();

      // Change status in database.
      change.setStatus(newStatus);

      // Change status in NoteDb.
      PatchSet.Id currentPatchSetId = change.currentPatchSetId();
      ctx.getUpdate(currentPatchSetId).setStatus(newStatus);

      return true;
    }
  }
}
