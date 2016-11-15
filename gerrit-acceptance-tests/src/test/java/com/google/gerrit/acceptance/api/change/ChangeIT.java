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
import static org.junit.Assert.fail;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AcceptanceTestRequestScope;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.GerritConfigs;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.DeleteVoteInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RebaseInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.RevisionApi;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.ChangeKind;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.AccountInfo;
import com.google.gerrit.extensions.common.ApprovalInfo;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.GitPerson;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.Util;
import com.google.gerrit.testutil.FakeEmailSender.Message;
import com.google.gerrit.testutil.NoteDbMode;
import com.google.gerrit.testutil.TestTimeUtil;

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

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@NoHttpd
public class ChangeIT extends AbstractDaemonTest {
  private String systemTimeZone;

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
  public void getAmbiguous() throws Exception {
    PushOneCommit.Result r1 = createChange();
    String changeId = r1.getChangeId();
    gApi.changes().id(changeId).get();

    BranchInput b = new BranchInput();
    b.revision = repo().exactRef("HEAD").getObjectId().name();
    gApi.projects()
        .name(project.get())
        .branch("other")
        .create(b);

    PushOneCommit push2 = pushFactory.create(db, admin.getIdent(), testRepo,
        PushOneCommit.SUBJECT, PushOneCommit.FILE_NAME,
        PushOneCommit.FILE_CONTENT, changeId);
    PushOneCommit.Result r2 = push2.to("refs/for/other");
    assertThat(r2.getChangeId()).isEqualTo(changeId);

    exception.expect(ResourceNotFoundException.class);
    exception.expectMessage("Multiple changes found for " + changeId);
    gApi.changes().id(changeId).get();
  }

  @Test
  public void abandon() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    gApi.changes()
        .id(changeId)
        .abandon();
    ChangeInfo info = get(changeId);
    assertThat(info.status).isEqualTo(ChangeStatus.ABANDONED);
    assertThat(Iterables.getLast(info.messages).message.toLowerCase())
        .contains("abandoned");

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("change is abandoned");
    gApi.changes()
        .id(changeId)
        .abandon();
  }

  @Test
  public void abandonDraft() throws Exception {
    PushOneCommit.Result r = createDraftChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.DRAFT);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("draft changes cannot be abandoned");
    gApi.changes()
        .id(changeId)
        .abandon();
  }

  @Test
  public void restore() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    gApi.changes()
        .id(changeId)
        .abandon();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.ABANDONED);

    gApi.changes()
        .id(changeId)
        .restore();
    ChangeInfo info = get(changeId);
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);
    assertThat(Iterables.getLast(info.messages).message.toLowerCase())
        .contains("restored");

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("change is new");
    gApi.changes()
        .id(changeId)
        .restore();
  }

  @Test
  public void revert() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .submit();
    ChangeInfo revertChange =
        gApi.changes()
            .id(r.getChangeId())
            .revert().get();

    // expected messages on source change:
    // 1. Uploaded patch set 1.
    // 2. Patch Set 1: Code-Review+2
    // 3. Change has been successfully merged by Administrator
    // 4. Patch Set 1: Reverted
    List<ChangeMessageInfo> sourceMessages = new ArrayList<>(
        gApi.changes().id(r.getChangeId()).get().messages);
    assertThat(sourceMessages).hasSize(4);
    String expectedMessage = String.format(
        "Created a revert of this change as %s",
        revertChange.changeId);
    assertThat(sourceMessages.get(3).message).isEqualTo(expectedMessage);

    assertThat(revertChange.messages).hasSize(1);
    assertThat(revertChange.messages.iterator().next().message)
        .isEqualTo("Uploaded patch set 1.");
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void revertInitialCommit() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .submit();

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Cannot revert initial commit");
    gApi.changes()
        .id(r.getChangeId())
        .revert();
  }

  @Test
  public void rebase() throws Exception {
    // Create two changes both with the same parent
    PushOneCommit.Result r = createChange();
    testRepo.reset("HEAD~1");
    PushOneCommit.Result r2 = createChange();

    // Approve and submit the first change
    RevisionApi revision = gApi.changes()
        .id(r.getChangeId())
        .current();
    revision.review(ReviewInput.approve());
    revision.submit();

    String changeId = r2.getChangeId();
    // Rebase the second change
    gApi.changes()
        .id(changeId)
        .current()
        .rebase();

    // Second change should have 2 patch sets
    ChangeInfo c2 = gApi.changes().id(changeId).get();
    assertThat(c2.revisions.get(c2.currentRevision)._number).isEqualTo(2);

    // ...and the committer should be correct
    ChangeInfo info = gApi.changes()
        .id(changeId).get(EnumSet.of(
            ListChangesOption.CURRENT_REVISION,
            ListChangesOption.CURRENT_COMMIT));
    GitPerson committer = info.revisions.get(
        info.currentRevision).commit.committer;
    assertThat(committer.name).isEqualTo(admin.fullName);
    assertThat(committer.email).isEqualTo(admin.email);

    // Rebasing the second change again should fail
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Change is already up to date");
    gApi.changes()
        .id(changeId)
        .current()
        .rebase();
  }

  @Test
  public void publish() throws Exception {
    PushOneCommit.Result r = createChange("refs/drafts/master");
    assertThat(info(r.getChangeId()).status).isEqualTo(ChangeStatus.DRAFT);
    gApi.changes()
      .id(r.getChangeId())
      .publish();
    assertThat(info(r.getChangeId()).status).isEqualTo(ChangeStatus.NEW);
  }

  @Test
  public void delete() throws Exception {
    PushOneCommit.Result r = createChange("refs/drafts/master");
    assertThat(query(r.getChangeId())).hasSize(1);
    assertThat(info(r.getChangeId()).status).isEqualTo(ChangeStatus.DRAFT);
    gApi.changes()
      .id(r.getChangeId())
      .delete();
    assertThat(query(r.getChangeId())).isEmpty();
  }

  @Test
  public void voteOnClosedChange() throws Exception {
    PushOneCommit.Result r = createChange();
    merge(r);
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("change is closed");
    revision(r).review(ReviewInput.reject());
  }

  @Test
  public void voteOnBehalfOf() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    LabelType codeReviewType = Util.codeReview();
    String forCodeReviewAs = Permission.forLabelAs(codeReviewType.getName());
    String heads = "refs/heads/*";
    AccountGroup.UUID owner =
        SystemGroupBackend.getGroup(CHANGE_OWNER).getUUID();
    Util.allow(cfg, forCodeReviewAs, -1, 1, owner, heads);
    saveProjectConfig(project, cfg);

    PushOneCommit.Result r = createChange();
    RevisionApi revision = gApi.changes()
        .id(r.getChangeId())
        .current();

    ReviewInput in = ReviewInput.recommend();
    in.onBehalfOf = user.id.toString();
    revision.review(in);

    ChangeInfo c = gApi.changes()
        .id(r.getChangeId())
        .get();

    LabelInfo codeReview = c.labels.get("Code-Review");
    assertThat(codeReview.all).hasSize(1);
    ApprovalInfo approval = codeReview.all.get(0);
    assertThat(approval._accountId).isEqualTo(user.id.get());
    assertThat(approval.value).isEqualTo(1);
  }

  @Test
  public void rebaseUpToDateChange() throws Exception {
    PushOneCommit.Result r = createChange();
    exception.expect(ResourceConflictException.class);
    exception.expectMessage("Change is already up to date");
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .rebase();
  }

  @Test
  public void rebaseConflict() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .submit();

    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo,
        PushOneCommit.SUBJECT, PushOneCommit.FILE_NAME, "other content",
        "If09d8782c1e59dd0b33de2b1ec3595d69cc10ad5");
    r = push.to("refs/for/master");
    r.assertOkStatus();

    exception.expect(ResourceConflictException.class);
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .rebase();
  }

  @Test
  public void rebaseChangeBase() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = createChange();
    PushOneCommit.Result r3 = createChange();
    RebaseInput ri = new RebaseInput();

    // rebase r3 directly onto master (break dep. towards r2)
    ri.base = "";
    gApi.changes()
        .id(r3.getChangeId())
        .revision(r3.getCommit().name())
        .rebase(ri);
    PatchSet ps3 = r3.getPatchSet();
    assertThat(ps3.getId().get()).isEqualTo(2);

    // rebase r2 onto r3 (referenced by ref)
    ri.base = ps3.getId().toRefName();
    gApi.changes()
        .id(r2.getChangeId())
        .revision(r2.getCommit().name())
        .rebase(ri);
    PatchSet ps2 = r2.getPatchSet();
    assertThat(ps2.getId().get()).isEqualTo(2);

    // rebase r1 onto r2 (referenced by commit)
    ri.base = ps2.getRevision().get();
    gApi.changes()
        .id(r1.getChangeId())
        .revision(r1.getCommit().name())
        .rebase(ri);
    PatchSet ps1 = r1.getPatchSet();
    assertThat(ps1.getId().get()).isEqualTo(2);

    // rebase r1 onto r3 (referenced by change number)
    ri.base = String.valueOf(r3.getChange().getId().get());
    gApi.changes()
        .id(r1.getChangeId())
        .revision(ps1.getRevision().get())
        .rebase(ri);
    assertThat(r1.getPatchSetId().get()).isEqualTo(3);
  }

  @Test
  public void rebaseChangeBaseRecursion() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = createChange();

    RebaseInput ri = new RebaseInput();
    ri.base = r2.getCommit().name();
    String expectedMessage = "base change " + r2.getChangeId()
        + " is a descendant of the current change - recursion not allowed";
    exception.expect(ResourceConflictException.class);
    exception.expectMessage(expectedMessage);
    gApi.changes()
        .id(r1.getChangeId())
        .revision(r1.getCommit().name())
        .rebase(ri);
  }

  @Test
  public void rebaseAbandonedChange() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    gApi.changes()
        .id(changeId)
        .abandon();
    ChangeInfo info = get(changeId);
    assertThat(info.status).isEqualTo(ChangeStatus.ABANDONED);

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("change is abandoned");
    gApi.changes()
        .id(changeId)
        .revision(r.getCommit().name())
        .rebase();
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
    gApi.changes()
        .id(changeId)
        .abandon();
    ChangeInfo info = get(changeId);
    assertThat(info.status).isEqualTo(ChangeStatus.ABANDONED);

    RebaseInput ri = new RebaseInput();
    ri.base = r.getCommit().name();

    exception.expect(ResourceConflictException.class);
    exception.expectMessage("base change is abandoned: " + changeId);
    gApi.changes()
        .id(r2.getChangeId())
        .revision(r2.getCommit().name())
        .rebase(ri);
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
    gApi.changes()
        .id(changeId)
        .revision(commit)
        .rebase(ri);
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void changeNoParentToOneParent() throws Exception {
    // create initial commit with no parent and push it as change, so that patch
    // set 1 has no parent
    RevCommit c =
        testRepo.commit().message("Initial commit").insertChangeId().create();
    String id = GitUtil.getChangeId(testRepo, c).get();
    testRepo.reset(c);

    PushResult pr = pushHead(testRepo, "refs/for/master", false);
    assertPushOk(pr, "refs/for/master");

    ChangeInfo change = gApi.changes().id(id).get();
    assertThat(change.revisions.get(change.currentRevision).commit.parents)
        .isEmpty();

    // create another initial commit with no parent and push it directly into
    // the remote repository
    c = testRepo.amend(c.getId()).message("Initial Empty Commit").create();
    testRepo.reset(c);
    pr = pushHead(testRepo, "refs/heads/master", false);
    assertPushOk(pr, "refs/heads/master");

    // create a successor commit and push it as second patch set to the change,
    // so that patch set 2 has 1 parent
    RevCommit c2 = testRepo.commit().message("Initial commit").parent(c)
        .insertChangeId(id.substring(1)).create();
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
  public void addReviewerThatCannotSeeChange() throws Exception {
    // create hidden project that is only visible to administrators
    Project.NameKey p = createProject("p");
    ProjectConfig cfg = projectCache.checkedGet(p).getConfig();
    Util.allow(cfg,
        Permission.READ,
        groupCache.get(new AccountGroup.NameKey("Administrators"))
            .getGroupUUID(),
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
    exception.expect(UnprocessableEntityException.class);
    exception.expectMessage("Change not visible to " + user.email);
    gApi.changes()
        .id(result.getChangeId())
        .addReviewer(in);
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
    gApi.changes()
        .id(r.getChangeId())
        .addReviewer(in);

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message m = messages.get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress);
    assertThat(m.body()).contains("Hello " + user.fullName + ",\n");
    assertThat(m.body()).contains("I'd like you to do a code review.");
    assertThat(m.body()).contains("Change subject: " + PushOneCommit.SUBJECT + "\n");
    assertMailFrom(m, admin.email);
    ChangeInfo c = gApi.changes()
        .id(r.getChangeId())
        .get();

    // When NoteDb is enabled adding a reviewer records that user as reviewer
    // in NoteDb. When NoteDb is disabled adding a reviewer results in a dummy 0
    // approval on the change which is treated as CC when the ChangeInfo is
    // created.
    Collection<AccountInfo> reviewers = c.reviewers.get(REVIEWER);
    assertThat(reviewers).isNotNull();
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.iterator().next()._accountId)
        .isEqualTo(user.getId().get());

    // Ensure ETag and lastUpdatedOn are updated.
    rsrc = parseResource(r);
    assertThat(rsrc.getETag()).isNotEqualTo(oldETag);
    assertThat(rsrc.getChange().getLastUpdatedOn()).isNotEqualTo(oldTs);
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
    gApi.changes()
        .id(r.getChangeId())
        .addReviewer(in);

    // There should be no email notification when adding self
    assertThat(sender.getMessages()).isEmpty();

    // When NoteDb is enabled adding a reviewer records that user as reviewer
    // in NoteDb. When NoteDb is disabled adding a reviewer results in a dummy 0
    // approval on the change which is treated as CC when the ChangeInfo is
    // created.
    ChangeInfo c = gApi.changes()
        .id(r.getChangeId())
        .get();
    Collection<AccountInfo> reviewers = c.reviewers.get(REVIEWER);
    assertThat(reviewers).isNotNull();
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.iterator().next()._accountId)
        .isEqualTo(user.getId().get());

    // Ensure ETag and lastUpdatedOn are updated.
    rsrc = parseResource(r);
    assertThat(rsrc.getETag()).isNotEqualTo(oldETag);
    assertThat(rsrc.getChange().getLastUpdatedOn()).isNotEqualTo(oldTs);
  }

  @Test
  public void addReviewerToClosedChange() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .submit();

    ChangeInfo c = gApi.changes()
        .id(r.getChangeId())
        .get();
    Collection<AccountInfo> reviewers = c.reviewers.get(REVIEWER);
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.iterator().next()._accountId)
        .isEqualTo(admin.getId().get());
    assertThat(c.reviewers).doesNotContainKey(CC);

    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes()
        .id(r.getChangeId())
        .addReviewer(in);

    c = gApi.changes()
        .id(r.getChangeId())
        .get();
    reviewers = c.reviewers.get(REVIEWER);
    assertThat(reviewers).hasSize(2);
    Iterator<AccountInfo> reviewerIt = reviewers.iterator();
    assertThat(reviewerIt.next()._accountId)
        .isEqualTo(admin.getId().get());
    assertThat(reviewerIt.next()._accountId)
        .isEqualTo(user.getId().get());
    assertThat(c.reviewers).doesNotContainKey(CC);
  }

  @Test
  public void listVotes() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());

    Map<String, Short> m = gApi.changes()
        .id(r.getChangeId())
        .reviewer(admin.getId().toString())
        .votes();

    assertThat(m).hasSize(1);
    assertThat(m).containsEntry("Code-Review", Short.valueOf((short)2));

    setApiUser(user);
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.dislike());

    m = gApi.changes()
        .id(r.getChangeId())
        .reviewer(user.getId().toString())
        .votes();

    assertThat(m).hasSize(1);
    assertThat(m).containsEntry("Code-Review", Short.valueOf((short)-1));
  }

  @Test
  public void removeReviewerNoVotes() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();

    LabelType verified = category("Verified", value(1, "Passes"),
        value(0, "No score"), value(-1, "Failed"));
    cfg.getLabelSections().put(verified.getName(), verified);

    AccountGroup.UUID registeredUsers =
        SystemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    String heads = RefNames.REFS_HEADS + "*";
    Util.allow(cfg, Permission.forLabel(Util.verified().getName()), -1, 1,
        registeredUsers, heads);
    saveProjectConfig(project, cfg);

    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    gApi.changes()
        .id(changeId)
        .addReviewer(user.getId().toString());

    // ReviewerState will vary between ReviewDb and NoteDb; we just care that it
    // shows up somewhere.
    Iterable<AccountInfo> reviewers = Iterables.concat(
        gApi.changes().id(changeId).get().reviewers.values());
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.iterator().next()._accountId)
        .isEqualTo(user.getId().get());

    gApi.changes()
        .id(changeId)
        .reviewer(user.getId().toString())
        .remove();
    assertThat(gApi.changes().id(changeId).get().reviewers.isEmpty());

    // Make sure the reviewer can still be added again.
    gApi.changes()
        .id(changeId)
        .addReviewer(user.getId().toString());
    reviewers = Iterables.concat(gApi.changes().id(changeId).get().reviewers.values());
    assertThat(reviewers).hasSize(1);
    assertThat(reviewers.iterator().next()._accountId)
        .isEqualTo(user.getId().get());

    // Remove again, and then try to remove once more to verify 404 is
    // returned.
    gApi.changes()
        .id(changeId)
        .reviewer(user.getId().toString())
        .remove();
    exception.expect(ResourceNotFoundException.class);
    gApi.changes()
        .id(changeId)
        .reviewer(user.getId().toString())
        .remove();
  }

  @Test
  public void removeReviewer() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    gApi.changes()
        .id(changeId)
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());

    setApiUser(user);
    gApi.changes()
        .id(changeId)
        .revision(r.getCommit().name())
        .review(ReviewInput.recommend());

    Collection<AccountInfo> reviewers = gApi.changes()
        .id(changeId)
        .get()
        .reviewers.get(REVIEWER);

    assertThat(reviewers).hasSize(2);
    Iterator<AccountInfo> reviewerIt = reviewers.iterator();
    assertThat(reviewerIt.next()._accountId)
        .isEqualTo(admin.getId().get());
    assertThat(reviewerIt.next()._accountId)
        .isEqualTo(user.getId().get());

    setApiUser(admin);
    gApi.changes()
        .id(changeId)
        .reviewer(user.getId().toString())
        .remove();

    reviewers = gApi.changes()
        .id(changeId)
        .get()
        .reviewers.get(REVIEWER);
    assertThat(reviewers).hasSize(1);
    reviewerIt = reviewers.iterator();
    assertThat(reviewerIt.next()._accountId)
      .isEqualTo(admin.getId().get());

    eventRecorder.assertReviewerDeletedEvents(changeId, user.email);
  }

  @Test
  public void removeReviewerNotPermitted() throws Exception {
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    gApi.changes()
        .id(changeId)
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());

    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("delete reviewer not permitted");
    gApi.changes()
        .id(r.getChangeId())
        .reviewer(admin.getId().toString())
        .remove();
  }

  @Test
  public void deleteVote() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());

    setApiUser(user);
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.recommend());

    setApiUser(admin);
    sender.clear();
    gApi.changes()
        .id(r.getChangeId())
        .reviewer(user.getId().toString())
        .deleteVote("Code-Review");

    List<Message> messages = sender.getMessages();
    assertThat(messages).hasSize(1);
    Message msg = messages.get(0);
    assertThat(msg.rcpt()).containsExactly(user.emailAddress);
    assertThat(msg.body()).contains(
        admin.fullName + " has removed a vote on this change.\n");
    assertThat(msg.body()).contains(
        "Removed Code-Review+1 by "
            + user.fullName + " <" + user.email + ">" + "\n");

    Map<String, Short> m = gApi.changes()
        .id(r.getChangeId())
        .reviewer(user.getId().toString())
        .votes();

    if (NoteDbMode.readWrite()) {
      // When NoteDb is enabled each reviewer is explicitly recorded in the
      // NoteDb and this record stays even when all votes of that user have been
      // deleted, hence there is no dummy 0 approval left when a vote is
      // deleted.
      assertThat(m).isEmpty();
    } else {
      // When NoteDb is disabled there is a dummy 0 approval on the change so
      // that the user is still returned as CC when all votes of that user have
      // been deleted.
      assertThat(m).containsEntry("Code-Review", Short.valueOf((short)0));
    }

    ChangeInfo c = gApi.changes()
        .id(r.getChangeId())
        .get();

    ChangeMessageInfo message = Iterables.getLast(c.messages);
    assertThat(message.author._accountId).isEqualTo(admin.getId().get());
    assertThat(message.message).isEqualTo(
        "Removed Code-Review+1 by User <user@example.com>\n");
    assertThat(getReviewers(c.reviewers.get(REVIEWER)))
        .containsExactlyElementsIn(
            ImmutableSet.of(admin.getId(), user.getId()));
  }

  @Test
  public void deleteVoteNotifyNone() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());

    setApiUser(user);
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.recommend());

    setApiUser(admin);
    sender.clear();
    DeleteVoteInput in = new DeleteVoteInput();
    in.label = "Code-Review";
    in.notify = NotifyHandling.NONE;
    gApi.changes()
        .id(r.getChangeId())
        .reviewer(user.getId().toString())
        .deleteVote(in);
    assertThat(sender.getMessages()).hasSize(0);
  }

  @Test
  public void deleteVoteNotPermitted() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());

    setApiUser(user);
    exception.expect(AuthException.class);
    exception.expectMessage("delete vote not permitted");
    gApi.changes()
        .id(r.getChangeId())
        .reviewer(admin.getId().toString())
        .deleteVote("Code-Review");
  }

  @Test
  public void nonVotingReviewerStaysAfterSubmit() throws Exception {
    LabelType verified = category("Verified",
        value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.getLabelSections().put(verified.getName(), verified);
    String heads = "refs/heads/*";
    AccountGroup.UUID owners =
        SystemGroupBackend.getGroup(CHANGE_OWNER).getUUID();
    AccountGroup.UUID registered =
        SystemGroupBackend.getGroup(REGISTERED_USERS).getUUID();
    Util.allow(cfg,
        Permission.forLabel(verified.getName()), -1, 1, owners, heads);
    Util.allow(cfg,
        Permission.forLabel("Code-Review"), -2, +2, registered, heads);
    saveProjectConfig(project, cfg);

    // Set Code-Review+2 and Verified+1 as admin (change owner)
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    String commit = r.getCommit().name();
    ReviewInput input = ReviewInput.approve();
    input.label(verified.getName(), 1);
    gApi.changes()
        .id(changeId)
        .revision(commit)
        .review(input);

    // Reviewers should only be "admin"
    ChangeInfo c = gApi.changes().id(changeId).get();
    assertThat(getReviewers(c.reviewers.get(REVIEWER)))
        .containsExactlyElementsIn(ImmutableSet.of(admin.getId()));
    assertThat(c.reviewers.get(CC)).isNull();

    // Add the user as reviewer
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes()
        .id(changeId)
        .addReviewer(in);
    c = gApi.changes().id(changeId).get();
    assertThat(getReviewers(c.reviewers.get(REVIEWER)))
        .containsExactlyElementsIn(ImmutableSet.of(
            admin.getId(), user.getId()));

    // Approve the change as user, then remove the approval
    // (only to confirm that the user does have Code-Review+2 permission)
    setApiUser(user);
    gApi.changes()
        .id(changeId)
        .revision(commit)
        .review(ReviewInput.approve());
    gApi.changes()
        .id(changeId)
        .revision(commit)
        .review(ReviewInput.noScore());

    // Submit the change
    setApiUser(admin);
    gApi.changes()
        .id(changeId)
        .revision(commit)
        .submit();

    // User should still be on the change
    c = gApi.changes().id(changeId).get();
    assertThat(getReviewers(c.reviewers.get(REVIEWER)))
        .containsExactlyElementsIn(ImmutableSet.of(
            admin.getId(), user.getId()));
  }

  @Test
  public void createEmptyChange() throws Exception {
    ChangeInput in = new ChangeInput();
    in.branch = Constants.MASTER;
    in.subject = "Create a change from the API";
    in.project = project.get();
    ChangeInfo info = gApi
        .changes()
        .create(in)
        .get();
    assertThat(info.project).isEqualTo(in.project);
    assertThat(info.branch).isEqualTo(in.branch);
    assertThat(info.subject).isEqualTo(in.subject);
    assertThat(Iterables.getOnlyElement(info.messages).message)
        .isEqualTo("Uploaded patch set 1.");
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
    assertThat(query("message:{" + getClass().getName() + "fhqwhgads}"))
        .isEmpty();
  }

  @Test
  public void queryChanges() throws Exception {
    PushOneCommit.Result r1 = createChange();
    createChange();
    List<ChangeInfo> results =
        query("project:{" + project.get() + "} " + r1.getChangeId());
    assertThat(Iterables.getOnlyElement(results).changeId)
        .isEqualTo(r1.getChangeId());
  }

  @Test
  public void queryChangesLimit() throws Exception {
    createChange();
    PushOneCommit.Result r2 = createChange();
    List<ChangeInfo> results = gApi.changes().query().withLimit(1).get();
    assertThat(results).hasSize(1);
    assertThat(Iterables.getOnlyElement(results).changeId)
        .isEqualTo(r2.getChangeId());
  }

  @Test
  public void queryChangesStart() throws Exception {
    PushOneCommit.Result r1 = createChange();
    createChange();
    List<ChangeInfo> results = gApi.changes()
        .query("project:{" + project.get() + "}").withStart(1).get();
    assertThat(Iterables.getOnlyElement(results).changeId)
        .isEqualTo(r1.getChangeId());
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

    ChangeInfo result = Iterables.getOnlyElement(gApi.changes()
        .query(r.getChangeId())
        .get());
    assertThat(result.labels).isNull();
    assertThat(result.messages).isNull();
    assertThat(result.actions).isNull();
    assertThat(result.revisions).isNull();

    EnumSet<ListChangesOption> options = EnumSet.of(
        ListChangesOption.ALL_REVISIONS,
        ListChangesOption.CHANGE_ACTIONS,
        ListChangesOption.CURRENT_ACTIONS,
        ListChangesOption.DETAILED_LABELS,
        ListChangesOption.MESSAGES);
    result = Iterables.getOnlyElement(gApi.changes()
        .query(r.getChangeId())
        .withOptions(options)
        .get());
    assertThat(Iterables.getOnlyElement(result.labels.keySet()))
        .isEqualTo("Code-Review");
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
    assertThat(Iterables.getOnlyElement(
            query("project:{" + project.get() + "} owner:self")).changeId)
        .isEqualTo(r.getChangeId());
    setApiUser(user);
    assertThat(query("owner:self")).isEmpty();
  }

  @Test
  public void checkReviewedFlagBeforeAndAfterReview() throws Exception {
    PushOneCommit.Result r = createChange();
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes()
        .id(r.getChangeId())
        .addReviewer(in);

    setApiUser(user);
    assertThat(get(r.getChangeId()).reviewed).isNull();

    revision(r).review(ReviewInput.recommend());
    assertThat(get(r.getChangeId()).reviewed).isTrue();
  }

  @Test
  public void topic() throws Exception {
    PushOneCommit.Result r = createChange();
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .topic()).isEqualTo("");
    gApi.changes()
        .id(r.getChangeId())
        .topic("mytopic");
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .topic()).isEqualTo("mytopic");
    gApi.changes()
        .id(r.getChangeId())
        .topic("");
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .topic()).isEqualTo("");
  }

  @Test
  public void submitted() throws Exception {
    PushOneCommit.Result r = createChange();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .review(ReviewInput.approve());
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .info().submitted).isNull();
    gApi.changes()
        .id(r.getChangeId())
        .revision(r.getCommit().name())
        .submit();
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .info().submitted).isNotNull();
  }

  @Test
  public void check() throws Exception {
    // TODO(dborowitz): Re-enable when ConsistencyChecker supports NoteDb.
    assume().that(notesMigration.enabled()).isFalse();
    PushOneCommit.Result r = createChange();
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .get()
        .problems).isNull();
    assertThat(gApi.changes()
        .id(r.getChangeId())
        .get(EnumSet.of(ListChangesOption.CHECK))
        .problems).isEmpty();
  }

  @Test
  public void commitFooters() throws Exception {
    LabelType verified = category("Verified",
        value(1, "Passes"), value(0, "No score"), value(-1, "Failed"));
    LabelType custom1 = category("Custom1",
        value(1, "Positive"), value(0, "No score"), value(-1, "Negative"));
    LabelType custom2 = category("Custom2",
        value(1, "Positive"), value(0, "No score"), value(-1, "Negative"));
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    cfg.getLabelSections().put(verified.getName(), verified);
    cfg.getLabelSections().put(custom1.getName(), custom1);
    cfg.getLabelSections().put(custom2.getName(), custom2);
    String heads = "refs/heads/*";
    AccountGroup.UUID anon =
        SystemGroupBackend.getGroup(ANONYMOUS_USERS).getUUID();
    Util.allow(cfg, Permission.forLabel("Verified"), -1, 1, anon, heads);
    Util.allow(cfg, Permission.forLabel("Custom1"), -1, 1, anon, heads);
    Util.allow(cfg, Permission.forLabel("Custom2"), -1, 1, anon, heads);
    saveProjectConfig(project, cfg);

    PushOneCommit.Result r1 = createChange();
    r1.assertOkStatus();
    PushOneCommit.Result r2 = pushFactory.create(
          db, admin.getIdent(), testRepo, SUBJECT, FILE_NAME, "new content",
          r1.getChangeId())
        .to("refs/for/master");
    r2.assertOkStatus();

    ReviewInput in = new ReviewInput();
    in.label("Code-Review", 1);
    in.label("Verified", 1);
    in.label("Custom1", -1);
    in.label("Custom2", 1);
    gApi.changes().id(r2.getChangeId()).current().review(in);

    EnumSet<ListChangesOption> options = EnumSet.of(
        ListChangesOption.ALL_REVISIONS, ListChangesOption.COMMIT_FOOTERS);
    ChangeInfo actual = gApi.changes().id(r2.getChangeId()).get(options);
    assertThat(actual.revisions).hasSize(2);

    // No footers except on latest patch set.
    assertThat(actual.revisions.get(r1.getCommit().getName()).commitWithFooters)
        .isNull();

    List<String> footers =
        new ArrayList<>(Arrays.asList(
            actual.revisions.get(r2.getCommit().getName())
            .commitWithFooters.split("\\n")));
    // remove subject + blank line
    footers.remove(0);
    footers.remove(0);

    List<String> expectedFooters = Arrays.asList(
        "Change-Id: " + r2.getChangeId(),
        "Reviewed-on: "
            + canonicalWebUrl.get() + r2.getChange().getId(),
        "Reviewed-by: Administrator <admin@example.com>",
        "Custom2: Administrator <admin@example.com>",
        "Tested-by: Administrator <admin@example.com>");

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
    gApi.changes()
        .id(r1.getChangeId())
        .revision(r1.getCommit().name())
        .submit();

    createChange();
    createDraftChange();

    setApiUser(user);
    AcceptanceTestRequestScope.Context ctx = disableDb();
    try {
      assertThat(gApi.changes().query()
            .withQuery(
              "project:{" + project.get() + "} (status:open OR status:closed)")
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
    ChangeInfo c = gApi.changes().id(triplet).get(EnumSet.of(
        ListChangesOption.DETAILED_LABELS));
    LabelInfo codeReview = c.labels.get("Code-Review");
    assertThat(codeReview.all).hasSize(1);
    ApprovalInfo approval = codeReview.all.get(0);
    assertThat(approval._accountId).isEqualTo(user.id.get());
    assertThat(approval.value).isEqualTo(0);

    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    blockLabel(cfg, "Code-Review", REGISTERED_USERS, "refs/heads/*");
    saveProjectConfig(project, cfg);
    c = gApi.changes().id(triplet).get(EnumSet.of(
        ListChangesOption.DETAILED_LABELS));
    codeReview = c.labels.get("Code-Review");
    assertThat(codeReview.all).hasSize(1);
    approval = codeReview.all.get(0);
    assertThat(approval._accountId).isEqualTo(user.id.get());
    assertThat(approval.value).isNull();
  }

  @Test
  @GerritConfigs({
    @GerritConfig(name = "gerrit.editGpgKeys", value = "true"),
    @GerritConfig(name = "receive.enableSignedPush", value = "true"),
  })
  public void pushCertificates() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = amendChange(r1.getChangeId());

    ChangeInfo info = gApi.changes()
        .id(r1.getChangeId())
        .get(EnumSet.of(
            ListChangesOption.ALL_REVISIONS,
            ListChangesOption.PUSH_CERTIFICATES));

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
    gApi.changes()
        .id(triplet)
        .current()
        .review(ReviewInput.approve());
  }

  @Test
  public void noteDbCommitsOnPatchSetCreation() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();

    PushOneCommit.Result r = createChange();
    pushFactory.create(db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT,
        "b.txt", "4711", r.getChangeId()).to("refs/for/master").assertOkStatus();
    ChangeInfo c = gApi.changes().id(r.getChangeId()).get();
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit commitPatchSetCreation = rw.parseCommit(
          repo.exactRef(changeMetaRef(new Change.Id(c._number))).getObjectId());

      assertThat(commitPatchSetCreation.getShortMessage())
          .isEqualTo("Create patch set 2");
      PersonIdent expectedAuthor = changeNoteUtil.newIdent(
          accountCache.get(admin.id).getAccount(), c.updated,
          serverIdent.get(), AnonymousCowardNameProvider.DEFAULT);
      assertThat(commitPatchSetCreation.getAuthorIdent())
          .isEqualTo(expectedAuthor);
      assertThat(commitPatchSetCreation.getCommitterIdent())
          .isEqualTo(new PersonIdent(serverIdent.get(), c.updated));
      assertThat(commitPatchSetCreation.getParentCount()).isEqualTo(1);

      RevCommit commitChangeCreation =
          rw.parseCommit(commitPatchSetCreation.getParent(0));
      assertThat(commitChangeCreation.getShortMessage())
          .isEqualTo("Create change");
      expectedAuthor = changeNoteUtil.newIdent(
          accountCache.get(admin.id).getAccount(), c.created, serverIdent.get(),
          AnonymousCowardNameProvider.DEFAULT);
      assertThat(commitChangeCreation.getAuthorIdent())
          .isEqualTo(expectedAuthor);
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
    ChangeInfo info = gApi
        .changes()
        .create(in)
        .get();
    assertThat(info.project).isEqualTo(in.project);
    assertThat(info.branch).isEqualTo(in.branch);
    assertThat(info.subject).isEqualTo(in.subject);
    assertThat(Iterables.getOnlyElement(info.messages).message)
        .isEqualTo("Uploaded patch set 1.");
  }

  @Test
  public void createEmptyChangeOnExistingBranchWithNewBranch() throws Exception {
    ChangeInput in = new ChangeInput();
    in.branch = Constants.MASTER;
    in.subject = "Create a change on new branch from the API";
    in.project = project.get();
    in.newBranch = true;

    exception.expect(ResourceConflictException.class);
    gApi.changes()
        .create(in)
        .get();
  }

  @Test
  public void createNewPatchSetOnVisibleDraftPatchSet() throws Exception {
    // Clone separate repositories of the same project as admin and as user
    TestRepository<InMemoryRepository> adminTestRepo =
        cloneProject(project, admin);
    TestRepository<InMemoryRepository> userTestRepo =
        cloneProject(project, user);

    // Create change as admin
    PushOneCommit push = pushFactory.create(
        db, admin.getIdent(), adminTestRepo);
    PushOneCommit.Result r1 = push.to("refs/for/master");
    r1.assertOkStatus();

    // Amend draft as admin
    PushOneCommit.Result r2 = amendChange(
        r1.getChangeId(), "refs/drafts/master", admin, adminTestRepo);
    r2.assertOkStatus();

    // Add user as reviewer to make this patch set visible
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes()
        .id(r1.getChangeId())
        .addReviewer(in);

    // Fetch change
    GitUtil.fetch(userTestRepo, r2.getPatchSet().getRefName() + ":ps");
    userTestRepo.reset("ps");

    // Amend change as user
    PushOneCommit.Result r3 = amendChange(
        r2.getChangeId(), "refs/drafts/master", user, userTestRepo);
    r3.assertOkStatus();
  }

  @Test
  public void createNewPatchSetOnInvisibleDraftPatchSet() throws Exception {
    // Clone separate repositories of the same project as admin and as user
    TestRepository<InMemoryRepository> adminTestRepo =
        cloneProject(project, admin);
    TestRepository<InMemoryRepository> userTestRepo =
        cloneProject(project, user);

    // Create change as admin
    PushOneCommit push = pushFactory.create(
        db, admin.getIdent(), adminTestRepo);
    PushOneCommit.Result r1 = push.to("refs/for/master");
    r1.assertOkStatus();

    // Amend draft as admin
    PushOneCommit.Result r2 = amendChange(
        r1.getChangeId(), "refs/drafts/master", admin, adminTestRepo);
    r2.assertOkStatus();

    // Fetch change
    GitUtil.fetch(userTestRepo, r1.getPatchSet().getRefName() + ":ps");
    userTestRepo.reset("ps");

    // Amend change as user
    PushOneCommit.Result r3 = amendChange(
        r1.getChangeId(), "refs/for/master", user, userTestRepo);
    r3.assertErrorStatus("cannot add patch set to "
        + r3.getChange().change().getChangeId() + ".");
  }

  @Test
  public void createNewPatchSetWithoutPermission() throws Exception {
    // Create new project with clean permissions
    Project.NameKey p = createProject("addPatchSet1");

    // Clone separate repositories of the same project as admin and as user
    TestRepository<InMemoryRepository> adminTestRepo =
        cloneProject(p, admin);
    TestRepository<InMemoryRepository> userTestRepo =
        cloneProject(p, user);

    // Block default permission
    block(Permission.ADD_PATCH_SET,
        REGISTERED_USERS, "refs/for/*", p);

    // Create change as admin
    PushOneCommit push = pushFactory.create(
        db, admin.getIdent(), adminTestRepo);
    PushOneCommit.Result r1 = push.to("refs/for/master");
    r1.assertOkStatus();

    // Fetch change
    GitUtil.fetch(userTestRepo, r1.getPatchSet().getRefName() + ":ps");
    userTestRepo.reset("ps");

    // Amend change as user
    PushOneCommit.Result r2 =
        amendChange(r1.getChangeId(), "refs/for/master", user, userTestRepo);
    r2.assertErrorStatus("cannot add patch set to "
        + r1.getChange().getId().id + ".");
  }

  @Test
  public void createNewSetPatchWithPermission() throws Exception {
    // Clone separate repositories of the same project as admin and as user
    TestRepository<?> adminTestRepo = cloneProject(project, admin);
    TestRepository<?> userTestRepo = cloneProject(project, user);

    // Create change as admin
    PushOneCommit push = pushFactory.create(
        db, admin.getIdent(), adminTestRepo);
    PushOneCommit.Result r1 = push.to("refs/for/master");
    r1.assertOkStatus();

    // Fetch change
    GitUtil.fetch(userTestRepo, r1.getPatchSet().getRefName() + ":ps");
    userTestRepo.reset("ps");

    // Amend change as user
    PushOneCommit.Result r2 = amendChange(
        r1.getChangeId(), "refs/for/master", user, userTestRepo);
    r2.assertOkStatus();
  }

  @Test
  public void createNewPatchSetAsOwnerWithoutPermission() throws Exception {
    // Create new project with clean permissions
    Project.NameKey p = createProject("addPatchSet2");
    // Clone separate repositories of the same project as admin and as user
    TestRepository<?> adminTestRepo = cloneProject(project, admin);

    // Block default permission
    block(Permission.ADD_PATCH_SET, REGISTERED_USERS, "refs/for/*", p);

    // Create change as admin
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(), adminTestRepo);
    PushOneCommit.Result r1 = push.to("refs/for/master");
    r1.assertOkStatus();

    // Fetch change
    GitUtil.fetch(adminTestRepo, r1.getPatchSet().getRefName() + ":ps");
    adminTestRepo.reset("ps");

    // Amend change as admin
    PushOneCommit.Result r2 = amendChange(
        r1.getChangeId(), "refs/for/master", admin, adminTestRepo);
    r2.assertOkStatus();
  }

  @Test
  public void createNewPatchSetAsReviewerOnDraftChange() throws Exception {
    // Clone separate repositories of the same project as admin and as user
    TestRepository<?> adminTestRepo = cloneProject(project, admin);
    TestRepository<?> userTestRepo = cloneProject(project, user);

    // Create change as admin
    PushOneCommit push = pushFactory.create(
        db, admin.getIdent(), adminTestRepo);
    PushOneCommit.Result r1 = push.to("refs/drafts/master");
    r1.assertOkStatus();

    // Add user as reviewer
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes()
        .id(r1.getChangeId())
        .addReviewer(in);

    // Fetch change
    GitUtil.fetch(userTestRepo, r1.getPatchSet().getRefName() + ":ps");
    userTestRepo.reset("ps");

    // Amend change as user
    PushOneCommit.Result r2 = amendChange(
        r1.getChangeId(), "refs/for/master", user, userTestRepo);
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
    block(Permission.ADD_PATCH_SET, REGISTERED_USERS, "refs/for/*", p);

    // Create change as admin
    PushOneCommit push = pushFactory.create(
        db, admin.getIdent(), adminTestRepo);
    PushOneCommit.Result r1 = push.to("refs/drafts/master");
    r1.assertOkStatus();

    // Add user as reviewer
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = user.email;
    gApi.changes()
        .id(r1.getChangeId())
        .addReviewer(in);

    // Fetch change
    GitUtil.fetch(userTestRepo, r1.getPatchSet().getRefName() + ":ps");
    userTestRepo.reset("ps");

    // Amend change as user
    PushOneCommit.Result r2 = amendChange(
        r1.getChangeId(), "refs/drafts/master", user, userTestRepo);
    r2.assertErrorStatus("cannot add patch set to "
        + r1.getChange().getId().id + ".");
  }

  private static Iterable<Account.Id> getReviewers(
      Collection<AccountInfo> r) {
    return Iterables.transform(r, new Function<AccountInfo, Account.Id>() {
      @Override
      public Account.Id apply(AccountInfo account) {
        return new Account.Id(account._accountId);
      }
    });
  }

  private ChangeResource parseResource(PushOneCommit.Result r)
      throws Exception {
    List<ChangeControl> ctls = changeFinder.find(
        r.getChangeId(), atrScope.get().getUser());
    assertThat(ctls).hasSize(1);
    return changeResourceFactory.create(ctls.get(0));
  }
}
