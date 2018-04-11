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

package com.google.gerrit.acceptance.git;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.GitUtil.assertPushOk;
import static com.google.gerrit.acceptance.GitUtil.assertPushRejected;
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.GitUtil.pushOne;
import static com.google.gerrit.acceptance.PushOneCommit.FILE_NAME;
import static com.google.gerrit.common.FooterConstants.CHANGE_ID;
import static com.google.gerrit.extensions.client.ListChangesOption.ALL_REVISIONS;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_ACCOUNTS;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.extensions.client.ListChangesOption.MESSAGES;
import static com.google.gerrit.extensions.common.testing.EditInfoSubject.assertThat;
import static com.google.gerrit.server.git.receive.ReceiveConstants.PUSH_OPTION_SKIP_VALIDATION;
import static com.google.gerrit.server.group.SystemGroupBackend.ANONYMOUS_USERS;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.server.project.testing.Util.category;
import static com.google.gerrit.server.project.testing.Util.value;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Streams;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.Sandboxed;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.DraftInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.ProjectWatchInfo;
import com.google.gerrit.extensions.client.ReviewerState;
import com.google.gerrit.extensions.client.Side;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommentInfo;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.common.testing.EditInfoSubject;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.git.receive.NoteDbPushOption;
import com.google.gerrit.server.git.receive.ReceiveConstants;
import com.google.gerrit.server.git.validators.CommitValidators.ChangeIdValidator;
import com.google.gerrit.server.group.SystemGroupBackend;
import com.google.gerrit.server.mail.Address;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.testing.Util;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testing.FakeEmailSender.Message;
import com.google.gerrit.testing.TestTimeUtil;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

public abstract class AbstractPushForReview extends AbstractDaemonTest {
  protected enum Protocol {
    // TODO(dborowitz): TEST.
    SSH,
    HTTP
  }

  private LabelType patchSetLock;

  @BeforeClass
  public static void setTimeForTesting() {
    TestTimeUtil.resetWithClockStep(1, SECONDS);
  }

  @AfterClass
  public static void restoreTime() {
    TestTimeUtil.useSystemTime();
  }

  @Before
  public void setUp() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    patchSetLock = Util.patchSetLock();
    cfg.getLabelSections().put(patchSetLock.getName(), patchSetLock);
    AccountGroup.UUID anonymousUsers = systemGroupBackend.getGroup(ANONYMOUS_USERS).getUUID();
    Util.allow(
        cfg, Permission.forLabel(patchSetLock.getName()), 0, 1, anonymousUsers, "refs/heads/*");
    saveProjectConfig(cfg);
    grant(project, "refs/heads/*", Permission.LABEL + "Patch-Set-Lock");
  }

  @After
  public void tearDown() throws Exception {
    setApiUser(admin);
    GeneralPreferencesInfo prefs = gApi.accounts().id(admin.id.get()).getPreferences();
    prefs.publishCommentsOnPush = false;
    gApi.accounts().id(admin.id.get()).setPreferences(prefs);
  }

  protected void selectProtocol(Protocol p) throws Exception {
    String url;
    switch (p) {
      case SSH:
        url = adminSshSession.getUrl();
        break;
      case HTTP:
        url = admin.getHttpUrl(server);
        break;
      default:
        throw new IllegalArgumentException("unexpected protocol: " + p);
    }
    testRepo = GitUtil.cloneProject(project, url + "/" + project.get());
  }

  @Test
  public void pushForMaster() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null);
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void pushInitialCommitForMasterBranch() throws Exception {
    RevCommit c = testRepo.commit().message("Initial commit").insertChangeId().create();
    String id = GitUtil.getChangeId(testRepo, c).get();
    testRepo.reset(c);

    String r = "refs/for/master";
    PushResult pr = pushHead(testRepo, r, false);
    assertPushOk(pr, r);

    ChangeInfo change = gApi.changes().id(id).info();
    assertThat(change.branch).isEqualTo("master");
    assertThat(change.status).isEqualTo(ChangeStatus.NEW);

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.resolve("master")).isNull();
    }

    gApi.changes().id(change.id).current().review(ReviewInput.approve());
    gApi.changes().id(change.id).current().submit();

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.resolve("master")).isEqualTo(c);
    }
  }

  @Test
  public void pushInitialCommitForRefsMetaConfigBranch() throws Exception {
    // delete refs/meta/config
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RefUpdate u = repo.updateRef(RefNames.REFS_CONFIG);
      u.setForceUpdate(true);
      u.setExpectedOldObjectId(repo.resolve(RefNames.REFS_CONFIG));
      assertThat(u.delete(rw)).isEqualTo(Result.FORCED);
    }

    RevCommit c =
        testRepo
            .commit()
            .message("Initial commit")
            .author(admin.getIdent())
            .committer(admin.getIdent())
            .insertChangeId()
            .create();
    String id = GitUtil.getChangeId(testRepo, c).get();
    testRepo.reset(c);

    String r = "refs/for/" + RefNames.REFS_CONFIG;
    PushResult pr = pushHead(testRepo, r, false);
    assertPushOk(pr, r);

    ChangeInfo change = gApi.changes().id(id).info();
    assertThat(change.branch).isEqualTo(RefNames.REFS_CONFIG);
    assertThat(change.status).isEqualTo(ChangeStatus.NEW);

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.resolve(RefNames.REFS_CONFIG)).isNull();
    }

    gApi.changes().id(change.id).current().review(ReviewInput.approve());
    gApi.changes().id(change.id).current().submit();

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.resolve(RefNames.REFS_CONFIG)).isEqualTo(c);
    }
  }

  @Test
  public void pushInitialCommitForNormalNonExistingBranchFails() throws Exception {
    RevCommit c =
        testRepo
            .commit()
            .message("Initial commit")
            .author(admin.getIdent())
            .committer(admin.getIdent())
            .insertChangeId()
            .create();
    testRepo.reset(c);

    String r = "refs/for/foo";
    PushResult pr = pushHead(testRepo, r, false);
    assertPushRejected(pr, r, "branch foo not found");

    try (Repository repo = repoManager.openRepository(project)) {
      assertThat(repo.resolve("foo")).isNull();
    }
  }

  @Test
  public void output() throws Exception {
    String url = canonicalWebUrl.get() + "#/c/" + project.get() + "/+/";
    ObjectId initialHead = testRepo.getRepository().resolve("HEAD");
    PushOneCommit.Result r1 = pushTo("refs/for/master");
    Change.Id id1 = r1.getChange().getId();
    r1.assertOkStatus();
    r1.assertChange(Change.Status.NEW, null);
    r1.assertMessage(
        "New changes:\n  " + url + id1 + " " + r1.getCommit().getShortMessage() + "\n");

    testRepo.reset(initialHead);
    String newMsg = r1.getCommit().getShortMessage() + " v2";
    testRepo
        .branch("HEAD")
        .commit()
        .message(newMsg)
        .insertChangeId(r1.getChangeId().substring(1))
        .create();
    PushOneCommit.Result r2 =
        pushFactory
            .create(db, admin.getIdent(), testRepo, "another commit", "b.txt", "bbb")
            .to("refs/for/master");
    Change.Id id2 = r2.getChange().getId();
    r2.assertOkStatus();
    r2.assertChange(Change.Status.NEW, null);
    r2.assertMessage(
        "New changes:\n"
            + "  "
            + url
            + id2
            + " another commit\n"
            + "\n"
            + "\n"
            + "Updated changes:\n"
            + "  "
            + url
            + id1
            + " "
            + newMsg
            + "\n");
  }

  @Test
  public void autocloseByCommit() throws Exception {
    // Create a change
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();

    // Force push it, closing it
    String master = "refs/heads/master";
    assertPushOk(pushHead(testRepo, master, false), master);

    // Attempt to push amended commit to same change
    String url = canonicalWebUrl.get() + "#/c/" + project.get() + "/+/" + r.getChange().getId();
    r = amendChange(r.getChangeId(), "refs/for/master");
    r.assertErrorStatus("change " + url + " closed");

    // Check change message that was added on auto-close
    ChangeInfo change = gApi.changes().id(r.getChange().getId().get()).get();
    assertThat(Iterables.getLast(change.messages).message)
        .isEqualTo("Change has been successfully pushed.");
  }

  @Test
  public void autocloseByChangeId() throws Exception {
    RevCommit initialHead = getRemoteHead();

    // Create a change
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();

    // Amend the commit locally
    RevCommit c = testRepo.amend(r.getCommit()).create();
    assertThat(c).isNotEqualTo(r.getCommit());
    testRepo.reset(c);

    // Force push it, closing it
    String master = "refs/heads/master";
    assertPushOk(pushHead(testRepo, master, false), master);

    // Attempt to push amended commit to same change
    String url = canonicalWebUrl.get() + "#/c/" + project.get() + "/+/" + r.getChange().getId();
    amendChange(r.getChangeId(), "refs/for/master").assertErrorStatus("change " + url + " closed");

    assertClosedByPushReviewState(r, c, false);

    assertRefUpdatedEvents(initialHead, c);
    assertChangeMergedEvents(r.getChangeId(), c.name());
    // Check that we *only* received a patchset-created event for patchset 1, not patchset 2
    // FIXME Currently generating two events and triggering CI build(s) after change closed
    // amendedCommit should not generate an event as the change has been closed.
    assertPatchSetCreatedEvents(r.getCommit().name(), c.name());
  }

  /**
   * Issue 8724: Test to verify a local cherry-pick and push gives the same result as asking Gerrit
   * to submit reviews with the cherry-pick strategy.
   *
   * <p>See SubmitByCherryPickIT submitWithCherryPick for equivalent test.
   *
   * <p>Instead of asking Gerrit to cherry-pick the two reviews we do it locally and push.
   *
   * <p>FIXME Issues identified: (1) Additional patchset-created event generated compared to submit
   * with cherry-pick (2) Additional comment added to review (possibly fixed by fixing (1)?) (3)
   * Votes are wiped (additional assertions need to be added to verify this)
   */
  @Test
  public void autocloseByChangeIdEquivalentToSubmitWithCherryPick() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content");

    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "other content");

    testRepo.reset(initialHead);
    RevCommit cherryPick1 = testRepo.cherryPick(change1.getCommit());
    RevCommit cherryPick2 = testRepo.cherryPick(change2.getCommit());

    // Force push them, closing both
    String master = "refs/heads/master";
    assertPushOk(pushHead(testRepo, master, false), master);

    // Check that cherry picks were added as latest patch sets and that both are marked as MERGED
    assertClosedByPushReviewState(change1, cherryPick1, false);
    assertClosedByPushReviewState(change2, cherryPick2, true);

    assertRefUpdatedEvents(initialHead, cherryPick2);
    // NB! change-merged events happen in reverse order, i.e. in HEAD->oldHead order, not oldHead->HEAD
    assertChangeMergedEvents(
        change2.getChangeId(), cherryPick2.name(), change1.getChangeId(), cherryPick1.name());
    // FIXME Currently generating two events per change and triggering CI build(s) after change closed.
    // cherryPick1 and cherryPick2 (patchsets 2) should not generate events as the changes have been closed.
    assertPatchSetCreatedEvents(
        change1.getCommit().name(),
        change2.getCommit().name(),
        cherryPick2.name(),
        cherryPick1.name()); // Reversed as per change-merged but shouldn't be generated
  }

  private void assertClosedByPushReviewState(
      PushOneCommit.Result change, RevCommit current, boolean isRebase) throws Exception {
    ChangeInfo info = gApi.changes().id(change.getChange().getId().get()).get();
    assertThat(info.revisions).hasSize(2);
    assertThat(info.currentRevision).isEqualTo(current.name());
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
    assertThat(info.messages.stream().map(cmi -> cmi.message))
        .containsExactly(
            "Uploaded patch set 1.",
            // FIXME This message should not be generated, can then remove isRebase
            isRebase
                ? "Uploaded patch set 2: Patch Set 1 was rebased."
                : "Uploaded patch set 2: New patch set was added with same tree, parent, and commit message as Patch Set 1.",
            "Change has been successfully pushed.");
  }

  @Test
  public void pushForMasterWithTopic() throws Exception {
    // specify topic in ref
    String topic = "my/topic";
    PushOneCommit.Result r = pushTo("refs/for/master/" + topic);
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, topic);

    // specify topic as option
    r = pushTo("refs/for/master%topic=" + topic);
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, topic);
  }

  @Test
  public void pushForMasterWithTopicOption() throws Exception {
    String topicOption = "topic=myTopic";
    List<String> pushOptions = new ArrayList<>();
    pushOptions.add(topicOption);

    PushOneCommit push = pushFactory.create(db, admin.getIdent(), testRepo);
    push.setPushOptions(pushOptions);
    PushOneCommit.Result r = push.to("refs/for/master");

    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, "myTopic");
    r.assertPushOptions(pushOptions);
  }

  @Test
  public void pushForMasterWithTopicInRefExceedLimitFails() throws Exception {
    String topic = Stream.generate(() -> "t").limit(2049).collect(joining());
    PushOneCommit.Result r = pushTo("refs/for/master/" + topic);
    r.assertErrorStatus("topic length exceeds the limit (2048)");
  }

  @Test
  public void pushForMasterWithTopicAsOptionExceedLimitFails() throws Exception {
    String topic = Stream.generate(() -> "t").limit(2049).collect(joining());
    PushOneCommit.Result r = pushTo("refs/for/master%topic=" + topic);
    r.assertErrorStatus("topic length exceeds the limit (2048)");
  }

  @Test
  public void pushForMasterWithNotify() throws Exception {
    // create a user that watches the project
    TestAccount user3 = accountCreator.create("user3", "user3@example.com", "User3");
    List<ProjectWatchInfo> projectsToWatch = new ArrayList<>();
    ProjectWatchInfo pwi = new ProjectWatchInfo();
    pwi.project = project.get();
    pwi.filter = "*";
    pwi.notifyNewChanges = true;
    projectsToWatch.add(pwi);
    setApiUser(user3);
    gApi.accounts().self().setWatchedProjects(projectsToWatch);

    TestAccount user2 = accountCreator.user2();
    String pushSpec = "refs/for/master%reviewer=" + user.email + ",cc=" + user2.email;

    sender.clear();
    PushOneCommit.Result r = pushTo(pushSpec + ",notify=" + NotifyHandling.NONE);
    r.assertOkStatus();
    assertThat(sender.getMessages()).isEmpty();

    sender.clear();
    r = pushTo(pushSpec + ",notify=" + NotifyHandling.OWNER);
    r.assertOkStatus();
    // no email notification about own changes
    assertThat(sender.getMessages()).isEmpty();

    sender.clear();
    r = pushTo(pushSpec + ",notify=" + NotifyHandling.OWNER_REVIEWERS);
    r.assertOkStatus();
    assertThat(sender.getMessages()).hasSize(1);
    Message m = sender.getMessages().get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress);

    sender.clear();
    r = pushTo(pushSpec + ",notify=" + NotifyHandling.ALL);
    r.assertOkStatus();
    assertThat(sender.getMessages()).hasSize(1);
    m = sender.getMessages().get(0);
    assertThat(m.rcpt()).containsExactly(user.emailAddress, user2.emailAddress, user3.emailAddress);

    sender.clear();
    r = pushTo(pushSpec + ",notify=" + NotifyHandling.NONE + ",notify-to=" + user3.email);
    r.assertOkStatus();
    assertNotifyTo(user3);

    sender.clear();
    r = pushTo(pushSpec + ",notify=" + NotifyHandling.NONE + ",notify-cc=" + user3.email);
    r.assertOkStatus();
    assertNotifyCc(user3);

    sender.clear();
    r = pushTo(pushSpec + ",notify=" + NotifyHandling.NONE + ",notify-bcc=" + user3.email);
    r.assertOkStatus();
    assertNotifyBcc(user3);

    // request that sender gets notified as TO, CC and BCC, email should be sent
    // even if the sender is the only recipient
    sender.clear();
    r = pushTo(pushSpec + ",notify=" + NotifyHandling.NONE + ",notify-to=" + admin.email);
    assertNotifyTo(admin);

    sender.clear();
    r = pushTo(pushSpec + ",notify=" + NotifyHandling.NONE + ",notify-cc=" + admin.email);
    r.assertOkStatus();
    assertNotifyCc(admin);

    sender.clear();
    r = pushTo(pushSpec + ",notify=" + NotifyHandling.NONE + ",notify-bcc=" + admin.email);
    r.assertOkStatus();
    assertNotifyBcc(admin);
  }

  @Test
  public void pushForMasterWithCc() throws Exception {
    // cc one user
    String topic = "my/topic";
    PushOneCommit.Result r = pushTo("refs/for/master/" + topic + "%cc=" + user.email);
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, topic, ImmutableList.of(), ImmutableList.of(user));

    // cc several users
    r =
        pushTo(
            "refs/for/master/"
                + topic
                + "%cc="
                + admin.email
                + ",cc="
                + user.email
                + ",cc="
                + accountCreator.user2().email);
    r.assertOkStatus();
    // Check that admin isn't CC'd as they own the change
    r.assertChange(
        Change.Status.NEW,
        topic,
        ImmutableList.of(),
        ImmutableList.of(user, accountCreator.user2()));

    // cc non-existing user
    String nonExistingEmail = "non.existing@example.com";
    r =
        pushTo(
            "refs/for/master/"
                + topic
                + "%cc="
                + admin.email
                + ",cc="
                + nonExistingEmail
                + ",cc="
                + user.email);
    r.assertErrorStatus("user \"" + nonExistingEmail + "\" not found");
  }

  @Test
  public void pushForMasterWithReviewer() throws Exception {
    // add one reviewer
    String topic = "my/topic";
    PushOneCommit.Result r = pushTo("refs/for/master/" + topic + "%r=" + user.email);
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, topic, user);

    // add several reviewers
    TestAccount user2 =
        accountCreator.create("another-user", "another.user@example.com", "Another User");
    r =
        pushTo(
            "refs/for/master/"
                + topic
                + "%r="
                + admin.email
                + ",r="
                + user.email
                + ",r="
                + user2.email);
    r.assertOkStatus();
    // admin is the owner of the change and should not appear as reviewer
    r.assertChange(Change.Status.NEW, topic, user, user2);

    // add non-existing user as reviewer
    String nonExistingEmail = "non.existing@example.com";
    r =
        pushTo(
            "refs/for/master/"
                + topic
                + "%r="
                + admin.email
                + ",r="
                + nonExistingEmail
                + ",r="
                + user.email);
    r.assertErrorStatus("user \"" + nonExistingEmail + "\" not found");
  }

  @Test
  public void pushPrivateChange() throws Exception {
    // Push a private change.
    PushOneCommit.Result r = pushTo("refs/for/master%private");
    r.assertOkStatus();
    r.assertMessage(" [PRIVATE]");
    assertThat(r.getChange().change().isPrivate()).isTrue();

    // Pushing a new patch set without --private doesn't remove the privacy flag from the change.
    r = amendChange(r.getChangeId(), "refs/for/master");
    r.assertOkStatus();
    r.assertMessage(" [PRIVATE]");
    assertThat(r.getChange().change().isPrivate()).isTrue();

    // Remove the privacy flag from the change.
    r = amendChange(r.getChangeId(), "refs/for/master%remove-private");
    r.assertOkStatus();
    r.assertNotMessage(" [PRIVATE]");
    assertThat(r.getChange().change().isPrivate()).isFalse();

    // Normal push: privacy flag is not added back.
    r = amendChange(r.getChangeId(), "refs/for/master");
    r.assertOkStatus();
    r.assertNotMessage(" [PRIVATE]");
    assertThat(r.getChange().change().isPrivate()).isFalse();

    // Make the change private again.
    r = pushTo("refs/for/master%private");
    r.assertOkStatus();
    r.assertMessage(" [PRIVATE]");
    assertThat(r.getChange().change().isPrivate()).isTrue();

    // Can't use --private and --remove-private together.
    r = pushTo("refs/for/master%private,remove-private");
    r.assertErrorStatus();
  }

  @Test
  public void pushWorkInProgressChange() throws Exception {
    // Push a work-in-progress change.
    PushOneCommit.Result r = pushTo("refs/for/master%wip");
    r.assertOkStatus();
    r.assertMessage(" [WIP]");
    assertThat(r.getChange().change().isWorkInProgress()).isTrue();
    assertUploadTag(r.getChange(), ChangeMessagesUtil.TAG_UPLOADED_WIP_PATCH_SET);

    // Pushing a new patch set without --wip doesn't remove the wip flag from the change.
    r = amendChange(r.getChangeId(), "refs/for/master");
    r.assertOkStatus();
    r.assertMessage(" [WIP]");
    assertThat(r.getChange().change().isWorkInProgress()).isTrue();
    assertUploadTag(r.getChange(), ChangeMessagesUtil.TAG_UPLOADED_WIP_PATCH_SET);

    // Remove the wip flag from the change.
    r = amendChange(r.getChangeId(), "refs/for/master%ready");
    r.assertOkStatus();
    r.assertNotMessage(" [WIP]");
    assertThat(r.getChange().change().isWorkInProgress()).isFalse();
    assertUploadTag(r.getChange(), ChangeMessagesUtil.TAG_UPLOADED_PATCH_SET);

    // Normal push: wip flag is not added back.
    r = amendChange(r.getChangeId(), "refs/for/master");
    r.assertOkStatus();
    r.assertNotMessage(" [WIP]");
    assertThat(r.getChange().change().isWorkInProgress()).isFalse();
    assertUploadTag(r.getChange(), ChangeMessagesUtil.TAG_UPLOADED_PATCH_SET);

    // Make the change work-in-progress again.
    r = amendChange(r.getChangeId(), "refs/for/master%wip");
    r.assertOkStatus();
    r.assertMessage(" [WIP]");
    assertThat(r.getChange().change().isWorkInProgress()).isTrue();
    assertUploadTag(r.getChange(), ChangeMessagesUtil.TAG_UPLOADED_WIP_PATCH_SET);

    // Can't use --wip and --ready together.
    r = amendChange(r.getChangeId(), "refs/for/master%wip,ready");
    r.assertErrorStatus();
  }

  private void assertUploadTag(ChangeData cd, String expectedTag) throws Exception {
    List<ChangeMessage> msgs = cd.messages();
    assertThat(msgs).isNotEmpty();
    assertThat(Iterables.getLast(msgs).getTag()).isEqualTo(expectedTag);
  }

  @Test
  public void pushWorkInProgressChangeWhenNotOwner() throws Exception {
    TestRepository<?> userRepo = cloneProject(project, user);
    PushOneCommit.Result r =
        pushFactory.create(db, user.getIdent(), userRepo).to("refs/for/master%wip");
    r.assertOkStatus();
    assertThat(r.getChange().change().getOwner()).isEqualTo(user.id);
    assertThat(r.getChange().change().isWorkInProgress()).isTrue();

    // Other user trying to move from WIP to ready should fail.
    GitUtil.fetch(testRepo, r.getPatchSet().getRefName() + ":ps");
    testRepo.reset("ps");
    r = amendChange(r.getChangeId(), "refs/for/master%ready", admin, testRepo);
    r.assertErrorStatus(ReceiveConstants.ONLY_OWNER_CAN_MODIFY_WIP);

    // Other user trying to move from WIP to WIP should succeed.
    r = amendChange(r.getChangeId(), "refs/for/master%wip", admin, testRepo);
    r.assertOkStatus();
    assertThat(r.getChange().change().isWorkInProgress()).isTrue();

    // Push as change owner to move change from WIP to ready.
    r = pushFactory.create(db, user.getIdent(), userRepo).to("refs/for/master%ready");
    r.assertOkStatus();
    assertThat(r.getChange().change().isWorkInProgress()).isFalse();

    // Other user trying to move from ready to WIP should fail.
    GitUtil.fetch(testRepo, r.getPatchSet().getRefName() + ":ps");
    testRepo.reset("ps");
    r = amendChange(r.getChangeId(), "refs/for/master%wip", admin, testRepo);
    r.assertErrorStatus(ReceiveConstants.ONLY_OWNER_CAN_MODIFY_WIP);

    // Other user trying to move from ready to ready should succeed.
    r = amendChange(r.getChangeId(), "refs/for/master%ready", admin, testRepo);
    r.assertOkStatus();
  }

  @Test
  public void pushForMasterAsEdit() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    Optional<EditInfo> edit = getEdit(r.getChangeId());
    assertThat(edit).isAbsent();
    assertThat(query("has:edit")).isEmpty();

    // specify edit as option
    r = amendChange(r.getChangeId(), "refs/for/master%edit");
    r.assertOkStatus();
    edit = getEdit(r.getChangeId());
    assertThat(edit).isPresent();
    EditInfo editInfo = edit.get();
    r.assertMessage(
        "Updated Changes:\n  "
            + canonicalWebUrl.get()
            + "#/c/"
            + project.get()
            + "/+/"
            + r.getChange().getId()
            + " "
            + editInfo.commit.subject
            + " [EDIT]\n");

    // verify that the re-indexing was triggered for the change
    assertThat(query("has:edit")).hasSize(1);
  }

  @Test
  public void pushForMasterWithMessage() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master/%m=my_test_message");
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null);
    ChangeInfo ci = get(r.getChangeId(), MESSAGES, ALL_REVISIONS);
    Collection<ChangeMessageInfo> changeMessages = ci.messages;
    assertThat(changeMessages).hasSize(1);
    for (ChangeMessageInfo cm : changeMessages) {
      assertThat(cm.message).isEqualTo("Uploaded patch set 1.\nmy test message");
    }
    Collection<RevisionInfo> revisions = ci.revisions.values();
    assertThat(revisions).hasSize(1);
    for (RevisionInfo ri : revisions) {
      assertThat(ri.description).isEqualTo("my test message");
    }
  }

  @Test
  public void pushForMasterWithMessageTwiceWithDifferentMessages() throws Exception {
    enableCreateNewChangeForAllNotInTarget();

    PushOneCommit push =
        pushFactory.create(
            db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, "a.txt", "content");
    // %2C is comma; the value below tests that percent decoding happens after splitting.
    // All three ways of representing space ("%20", "+", and "_" are also exercised.
    PushOneCommit.Result r = push.to("refs/for/master/%m=my_test%20+_message%2Cm=");
    r.assertOkStatus();

    push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            "b.txt",
            "anotherContent",
            r.getChangeId());
    r = push.to("refs/for/master/%m=new_test_message");
    r.assertOkStatus();

    ChangeInfo ci = get(r.getChangeId(), ALL_REVISIONS);
    Collection<RevisionInfo> revisions = ci.revisions.values();
    assertThat(revisions).hasSize(2);
    for (RevisionInfo ri : revisions) {
      if (ri.isCurrent) {
        assertThat(ri.description).isEqualTo("new test message");
      } else {
        assertThat(ri.description).isEqualTo("my test   message,m=");
      }
    }
  }

  @Test
  public void pushForMasterWithPercentEncodedMessage() throws Exception {
    // Exercise percent-encoding of UTF-8, underscores, and patterns reserved by git-rev-parse.
    PushOneCommit.Result r =
        pushTo(
            "refs/for/master/%m="
                + "Punctu%2E%2e%2Eation%7E%2D%40%7Bu%7D%20%7C%20%28%E2%95%AF%C2%B0%E2%96%A1%C2%B0"
                + "%EF%BC%89%E2%95%AF%EF%B8%B5%20%E2%94%BB%E2%94%81%E2%94%BB%20%5E%5F%5E");
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null);
    ChangeInfo ci = get(r.getChangeId(), MESSAGES, ALL_REVISIONS);
    Collection<ChangeMessageInfo> changeMessages = ci.messages;
    assertThat(changeMessages).hasSize(1);
    for (ChangeMessageInfo cm : changeMessages) {
      assertThat(cm.message)
          .isEqualTo("Uploaded patch set 1.\nPunctu...ation~-@{u} | (╯°□°）╯︵ ┻━┻ ^_^");
    }
    Collection<RevisionInfo> revisions = ci.revisions.values();
    assertThat(revisions).hasSize(1);
    for (RevisionInfo ri : revisions) {
      assertThat(ri.description).isEqualTo("Punctu...ation~-@{u} | (╯°□°）╯︵ ┻━┻ ^_^");
    }
  }

  @Test
  public void pushForMasterWithInvalidPercentEncodedMessage() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master/%m=not_percent_decodable_%%oops%20");
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null);
    ChangeInfo ci = get(r.getChangeId(), MESSAGES, ALL_REVISIONS);
    Collection<ChangeMessageInfo> changeMessages = ci.messages;
    assertThat(changeMessages).hasSize(1);
    for (ChangeMessageInfo cm : changeMessages) {
      assertThat(cm.message).isEqualTo("Uploaded patch set 1.\nnot percent decodable %%oops%20");
    }
    Collection<RevisionInfo> revisions = ci.revisions.values();
    assertThat(revisions).hasSize(1);
    for (RevisionInfo ri : revisions) {
      assertThat(ri.description).isEqualTo("not percent decodable %%oops%20");
    }
  }

  @Test
  public void pushForMasterWithApprovals() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master/%l=Code-Review");
    r.assertOkStatus();
    ChangeInfo ci = get(r.getChangeId(), DETAILED_LABELS, MESSAGES, DETAILED_ACCOUNTS);
    LabelInfo cr = ci.labels.get("Code-Review");
    assertThat(cr.all).hasSize(1);
    assertThat(cr.all.get(0).name).isEqualTo("Administrator");
    assertThat(cr.all.get(0).value).isEqualTo(1);
    assertThat(Iterables.getLast(ci.messages).message)
        .isEqualTo("Uploaded patch set 1: Code-Review+1.");

    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            "b.txt",
            "anotherContent",
            r.getChangeId());
    r = push.to("refs/for/master/%l=Code-Review+2");

    ci = get(r.getChangeId(), DETAILED_LABELS, MESSAGES, DETAILED_ACCOUNTS);
    cr = ci.labels.get("Code-Review");
    assertThat(Iterables.getLast(ci.messages).message)
        .isEqualTo("Uploaded patch set 2: Code-Review+2.");
    // Check that the user who pushed the change was added as a reviewer since they added a vote
    assertThatUserIsOnlyReviewer(ci, admin);

    assertThat(cr.all).hasSize(1);
    assertThat(cr.all.get(0).name).isEqualTo("Administrator");
    assertThat(cr.all.get(0).value).isEqualTo(2);

    push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            "c.txt",
            "moreContent",
            r.getChangeId());
    r = push.to("refs/for/master/%l=Code-Review+2");
    ci = get(r.getChangeId(), MESSAGES);
    assertThat(Iterables.getLast(ci.messages).message).isEqualTo("Uploaded patch set 3.");
  }

  @Test
  public void pushNewPatchSetForMasterWithApprovals() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();

    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            "b.txt",
            "anotherContent",
            r.getChangeId());
    r = push.to("refs/for/master/%l=Code-Review+2");

    ChangeInfo ci = get(r.getChangeId(), DETAILED_LABELS, MESSAGES, DETAILED_ACCOUNTS);
    LabelInfo cr = ci.labels.get("Code-Review");
    assertThat(Iterables.getLast(ci.messages).message)
        .isEqualTo("Uploaded patch set 2: Code-Review+2.");

    // Check that the user who pushed the new patch set was added as a reviewer since they added
    // a vote
    assertThatUserIsOnlyReviewer(ci, admin);

    assertThat(cr.all).hasSize(1);
    assertThat(cr.all.get(0).name).isEqualTo("Administrator");
    assertThat(cr.all.get(0).value).isEqualTo(2);
  }

  /**
   * There was a bug that allowed a user with Forge Committer Identity access right to upload a
   * commit and put *votes on behalf of another user* on it. This test checks that this is not
   * possible, but that the votes that are specified on push are applied only on behalf of the
   * uploader.
   *
   * <p>This particular bug only occurred when there was more than one label defined. However to
   * test that the votes that are specified on push are applied on behalf of the uploader a single
   * label is sufficient.
   */
  @Test
  public void pushForMasterWithApprovalsForgeCommitterButNoForgeVote() throws Exception {
    // Create a commit with "User" as author and committer
    RevCommit c =
        commitBuilder()
            .author(user.getIdent())
            .committer(user.getIdent())
            .add(PushOneCommit.FILE_NAME, PushOneCommit.FILE_CONTENT)
            .message(PushOneCommit.SUBJECT)
            .create();

    // Push this commit as "Administrator" (requires Forge Committer Identity)
    pushHead(testRepo, "refs/for/master/%l=Code-Review+1", false);

    // Expected Code-Review votes:
    // 1. 0 from User (committer):
    //    When the committer is forged, the committer is automatically added as
    //    reviewer, hence we expect a dummy 0 vote for the committer.
    // 2. +1 from Administrator (uploader):
    //    On push Code-Review+1 was specified, hence we expect a +1 vote from
    //    the uploader.
    ChangeInfo ci =
        get(GitUtil.getChangeId(testRepo, c).get(), DETAILED_LABELS, MESSAGES, DETAILED_ACCOUNTS);
    LabelInfo cr = ci.labels.get("Code-Review");
    assertThat(cr.all).hasSize(2);
    int indexAdmin = admin.fullName.equals(cr.all.get(0).name) ? 0 : 1;
    int indexUser = indexAdmin == 0 ? 1 : 0;
    assertThat(cr.all.get(indexAdmin).name).isEqualTo(admin.fullName);
    assertThat(cr.all.get(indexAdmin).value.intValue()).isEqualTo(1);
    assertThat(cr.all.get(indexUser).name).isEqualTo(user.fullName);
    assertThat(cr.all.get(indexUser).value.intValue()).isEqualTo(0);
    assertThat(Iterables.getLast(ci.messages).message)
        .isEqualTo("Uploaded patch set 1: Code-Review+1.");
    // Check that the user who pushed the change was added as a reviewer since they added a vote
    assertThatUserIsOnlyReviewer(ci, admin);
  }

  @Test
  public void pushWithMultipleApprovals() throws Exception {
    LabelType Q =
        category("Custom-Label", value(1, "Positive"), value(0, "No score"), value(-1, "Negative"));
    ProjectConfig config = projectCache.checkedGet(project).getConfig();
    AccountGroup.UUID anon = systemGroupBackend.getGroup(ANONYMOUS_USERS).getUUID();
    String heads = "refs/heads/*";
    Util.allow(config, Permission.forLabel("Custom-Label"), -1, 1, anon, heads);
    config.getLabelSections().put(Q.getName(), Q);
    saveProjectConfig(project, config);

    RevCommit c =
        commitBuilder()
            .author(admin.getIdent())
            .committer(admin.getIdent())
            .add(PushOneCommit.FILE_NAME, PushOneCommit.FILE_CONTENT)
            .message(PushOneCommit.SUBJECT)
            .create();

    pushHead(testRepo, "refs/for/master/%l=Code-Review+1,l=Custom-Label-1", false);

    ChangeInfo ci = get(GitUtil.getChangeId(testRepo, c).get(), DETAILED_LABELS, DETAILED_ACCOUNTS);
    LabelInfo cr = ci.labels.get("Code-Review");
    assertThat(cr.all).hasSize(1);
    cr = ci.labels.get("Custom-Label");
    assertThat(cr.all).hasSize(1);
    // Check that the user who pushed the change was added as a reviewer since they added a vote
    assertThatUserIsOnlyReviewer(ci, admin);
  }

  @Test
  @GerritConfig(name = "receive.allowPushToRefsChanges", value = "true")
  public void pushToRefsChangesAllowed() throws Exception {
    PushOneCommit.Result r = pushOneCommitToRefsChanges();
    r.assertOkStatus();
  }

  @Test
  public void pushNewPatchsetToRefsChanges() throws Exception {
    PushOneCommit.Result r = pushOneCommitToRefsChanges();
    r.assertErrorStatus("upload to refs/changes not allowed");
  }

  @Test
  @GerritConfig(name = "receive.allowPushToRefsChanges", value = "false")
  public void pushToRefsChangesNotAllowed() throws Exception {
    PushOneCommit.Result r = pushOneCommitToRefsChanges();
    r.assertErrorStatus("upload to refs/changes not allowed");
  }

  private PushOneCommit.Result pushOneCommitToRefsChanges() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            "b.txt",
            "anotherContent",
            r.getChangeId());
    return push.to("refs/changes/" + r.getChange().change().getId().get());
  }

  @Test
  public void pushNewPatchsetToPatchSetLockedChange() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            "b.txt",
            "anotherContent",
            r.getChangeId());
    revision(r).review(new ReviewInput().label("Patch-Set-Lock", 1));
    r = push.to("refs/for/master");
    r.assertErrorStatus("cannot add patch set to " + r.getChange().change().getChangeId() + ".");
  }

  @Test
  public void pushForMasterWithApprovals_MissingLabel() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master/%l=Verify");
    r.assertErrorStatus("label \"Verify\" is not a configured label");
  }

  @Test
  public void pushForMasterWithApprovals_ValueOutOfRange() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master/%l=Code-Review-3");
    r.assertErrorStatus("label \"Code-Review\": -3 is not a valid value");
  }

  @Test
  public void pushForNonExistingBranch() throws Exception {
    String branchName = "non-existing";
    PushOneCommit.Result r = pushTo("refs/for/" + branchName);
    r.assertErrorStatus("branch " + branchName + " not found");
  }

  @Test
  public void pushForMasterWithHashtags() throws Exception {
    // Hashtags only work when reading from NoteDB is enabled
    assume().that(notesMigration.readChanges()).isTrue();

    // specify a single hashtag as option
    String hashtag1 = "tag1";
    Set<String> expected = ImmutableSet.of(hashtag1);
    PushOneCommit.Result r = pushTo("refs/for/master%hashtag=#" + hashtag1);
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null);

    Set<String> hashtags = gApi.changes().id(r.getChangeId()).getHashtags();
    assertThat(hashtags).containsExactlyElementsIn(expected);

    // specify a single hashtag as option in new patch set
    String hashtag2 = "tag2";
    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            "b.txt",
            "anotherContent",
            r.getChangeId());
    r = push.to("refs/for/master/%hashtag=" + hashtag2);
    r.assertOkStatus();
    expected = ImmutableSet.of(hashtag1, hashtag2);
    hashtags = gApi.changes().id(r.getChangeId()).getHashtags();
    assertThat(hashtags).containsExactlyElementsIn(expected);
  }

  @Test
  public void pushForMasterWithMultipleHashtags() throws Exception {
    // Hashtags only work when reading from NoteDB is enabled
    assume().that(notesMigration.readChanges()).isTrue();

    // specify multiple hashtags as options
    String hashtag1 = "tag1";
    String hashtag2 = "tag2";
    Set<String> expected = ImmutableSet.of(hashtag1, hashtag2);
    PushOneCommit.Result r =
        pushTo("refs/for/master%hashtag=#" + hashtag1 + ",hashtag=##" + hashtag2);
    r.assertOkStatus();
    r.assertChange(Change.Status.NEW, null);

    Set<String> hashtags = gApi.changes().id(r.getChangeId()).getHashtags();
    assertThat(hashtags).containsExactlyElementsIn(expected);

    // specify multiple hashtags as options in new patch set
    String hashtag3 = "tag3";
    String hashtag4 = "tag4";
    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            "b.txt",
            "anotherContent",
            r.getChangeId());
    r = push.to("refs/for/master%hashtag=" + hashtag3 + ",hashtag=" + hashtag4);
    r.assertOkStatus();
    expected = ImmutableSet.of(hashtag1, hashtag2, hashtag3, hashtag4);
    hashtags = gApi.changes().id(r.getChangeId()).getHashtags();
    assertThat(hashtags).containsExactlyElementsIn(expected);
  }

  @Test
  public void pushForMasterWithHashtagsNoteDbDisabled() throws Exception {
    // Push with hashtags should fail when reading from NoteDb is disabled.
    assume().that(notesMigration.readChanges()).isFalse();
    PushOneCommit.Result r = pushTo("refs/for/master%hashtag=tag1");
    r.assertErrorStatus("cannot add hashtags; noteDb is disabled");
  }

  @Test
  public void pushCommitUsingSignedOffBy() throws Exception {
    PushOneCommit push =
        pushFactory.create(
            db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, "b.txt", "anotherContent");
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();

    setUseSignedOffBy(InheritableBoolean.TRUE);
    blockForgeCommitter(project, "refs/heads/master");

    push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT
                + String.format("\n\nSigned-off-by: %s <%s>", admin.fullName, admin.email),
            "b.txt",
            "anotherContent");
    r = push.to("refs/for/master");
    r.assertOkStatus();

    push =
        pushFactory.create(
            db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, "b.txt", "anotherContent");
    r = push.to("refs/for/master");
    r.assertErrorStatus("not Signed-off-by author/committer/uploader in commit message footer");
  }

  @Test
  public void createNewChangeForAllNotInTarget() throws Exception {
    enableCreateNewChangeForAllNotInTarget();

    PushOneCommit push =
        pushFactory.create(
            db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, "a.txt", "content");
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();

    push =
        pushFactory.create(
            db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, "b.txt", "anotherContent");
    r = push.to("refs/for/master");
    r.assertOkStatus();

    gApi.projects().name(project.get()).branch("otherBranch").create(new BranchInput());

    PushOneCommit.Result r2 = push.to("refs/for/otherBranch");
    r2.assertOkStatus();
    assertTwoChangesWithSameRevision(r);
  }

  @Test
  public void pushChangeBasedOnChangeOfOtherUserWithCreateNewChangeForAllNotInTarget()
      throws Exception {
    enableCreateNewChangeForAllNotInTarget();

    // create a change as admin
    PushOneCommit push =
        pushFactory.create(
            db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, "a.txt", "content");
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();
    RevCommit commitChange1 = r.getCommit();

    // create a second change as user (depends on the change from admin)
    TestRepository<?> userRepo = cloneProject(project, user);
    GitUtil.fetch(userRepo, r.getPatchSet().getRefName() + ":change");
    userRepo.reset("change");
    push =
        pushFactory.create(
            db, user.getIdent(), userRepo, PushOneCommit.SUBJECT, "b.txt", "anotherContent");
    r = push.to("refs/for/master");
    r.assertOkStatus();

    // assert that no new change was created for the commit of the predecessor change
    assertThat(query(commitChange1.name())).hasSize(1);
  }

  @Test
  public void pushSameCommitTwiceUsingMagicBranchBaseOption() throws Exception {
    grant(project, "refs/heads/master", Permission.PUSH);
    PushOneCommit.Result rBase = pushTo("refs/heads/master");
    rBase.assertOkStatus();

    gApi.projects().name(project.get()).branch("foo").create(new BranchInput());

    PushOneCommit push =
        pushFactory.create(
            db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, "b.txt", "anotherContent");

    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();

    PushResult pr =
        GitUtil.pushHead(testRepo, "refs/for/foo%base=" + rBase.getCommit().name(), false, false);

    // BatchUpdate implementations differ in how they hook into progress monitors. We mostly just
    // care that there is a new change.
    assertThat(pr.getMessages()).containsMatch("changes: new: 1,( refs: 1)? done");
    assertTwoChangesWithSameRevision(r);
  }

  @Test
  public void pushSameCommitTwice() throws Exception {
    ProjectConfig config = projectCache.checkedGet(project).getConfig();
    config
        .getProject()
        .setBooleanConfig(
            BooleanProjectConfig.CREATE_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET, InheritableBoolean.TRUE);
    saveProjectConfig(project, config);

    PushOneCommit push =
        pushFactory.create(
            db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, "a.txt", "content");
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();

    push =
        pushFactory.create(
            db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, "b.txt", "anotherContent");
    r = push.to("refs/for/master");
    r.assertOkStatus();

    assertPushRejected(
        pushHead(testRepo, "refs/for/master", false),
        "refs/for/master",
        "commit(s) already exists (as current patchset)");
  }

  @Test
  public void pushSameCommitTwiceWhenIndexFailed() throws Exception {
    ProjectConfig config = projectCache.checkedGet(project).getConfig();
    config
        .getProject()
        .setBooleanConfig(
            BooleanProjectConfig.CREATE_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET, InheritableBoolean.TRUE);
    saveProjectConfig(project, config);

    PushOneCommit push =
        pushFactory.create(
            db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, "a.txt", "content");
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();

    push =
        pushFactory.create(
            db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, "b.txt", "anotherContent");
    r = push.to("refs/for/master");
    r.assertOkStatus();

    indexer.delete(r.getChange().getId());

    assertPushRejected(
        pushHead(testRepo, "refs/for/master", false),
        "refs/for/master",
        "commit(s) already exists (as current patchset)");
  }

  private void assertTwoChangesWithSameRevision(PushOneCommit.Result result) throws Exception {
    List<ChangeInfo> changes = query(result.getCommit().name());
    assertThat(changes).hasSize(2);
    ChangeInfo c1 = get(changes.get(0).id, CURRENT_REVISION);
    ChangeInfo c2 = get(changes.get(1).id, CURRENT_REVISION);
    assertThat(c1.project).isEqualTo(c2.project);
    assertThat(c1.branch).isNotEqualTo(c2.branch);
    assertThat(c1.changeId).isEqualTo(c2.changeId);
    assertThat(c1.currentRevision).isEqualTo(c2.currentRevision);
  }

  @Test
  public void pushAFewChanges() throws Exception {
    testPushAFewChanges();
  }

  @Test
  public void pushAFewChangesWithCreateNewChangeForAllNotInTarget() throws Exception {
    enableCreateNewChangeForAllNotInTarget();
    testPushAFewChanges();
  }

  private void testPushAFewChanges() throws Exception {
    int n = 10;
    String r = "refs/for/master";
    ObjectId initialHead = testRepo.getRepository().resolve("HEAD");
    List<RevCommit> commits = createChanges(n, r);

    // Check that a change was created for each.
    for (RevCommit c : commits) {
      assertThat(byCommit(c).change().getSubject())
          .named("change for " + c.name())
          .isEqualTo(c.getShortMessage());
    }

    List<RevCommit> commits2 = amendChanges(initialHead, commits, r);

    // Check that there are correct patch sets.
    for (int i = 0; i < n; i++) {
      RevCommit c = commits.get(i);
      RevCommit c2 = commits2.get(i);
      String name = "change for " + c2.name();
      ChangeData cd = byCommit(c);
      assertThat(cd.change().getSubject()).named(name).isEqualTo(c2.getShortMessage());
      assertThat(getPatchSetRevisions(cd))
          .named(name)
          .containsExactlyEntriesIn(ImmutableMap.of(1, c.name(), 2, c2.name()));
    }

    // Pushing again results in "no new changes".
    assertPushRejected(pushHead(testRepo, r, false), r, "no new changes");
  }

  @Test
  public void pushWithoutChangeId() throws Exception {
    testPushWithoutChangeId();
  }

  @Test
  public void pushWithoutChangeIdWithCreateNewChangeForAllNotInTarget() throws Exception {
    enableCreateNewChangeForAllNotInTarget();
    testPushWithoutChangeId();
  }

  private void testPushWithoutChangeId() throws Exception {
    RevCommit c = createCommit(testRepo, "Message without Change-Id");
    assertThat(GitUtil.getChangeId(testRepo, c)).isEmpty();
    pushForReviewRejected(testRepo, "missing Change-Id in commit message footer");

    setRequireChangeId(InheritableBoolean.FALSE);
    pushForReviewOk(testRepo);
  }

  @Test
  @GerritConfig(name = "receive.allowPushToRefsChanges", value = "true")
  public void testPushWithChangedChangeId() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT
                + "\n\n"
                + "Change-Id: I55eab7c7a76e95005fa9cc469aa8f9fc16da9eba\n",
            "b.txt",
            "anotherContent",
            r.getChangeId());
    r = push.to("refs/changes/" + r.getChange().change().getId().get());
    r.assertErrorStatus(
        String.format(
            ChangeIdValidator.CHANGE_ID_MISMATCH_MSG,
            r.getCommit().abbreviate(RevId.ABBREV_LEN).name()));
  }

  @Test
  public void pushWithMultipleChangeIds() throws Exception {
    testPushWithMultipleChangeIds();
  }

  @Test
  public void pushWithMultipleChangeIdsWithCreateNewChangeForAllNotInTarget() throws Exception {
    enableCreateNewChangeForAllNotInTarget();
    testPushWithMultipleChangeIds();
  }

  private void testPushWithMultipleChangeIds() throws Exception {
    createCommit(
        testRepo,
        "Message with multiple Change-Id\n"
            + "\n"
            + "Change-Id: I10f98c2ef76e52e23aa23be5afeb71e40b350e86\n"
            + "Change-Id: Ie9a132e107def33bdd513b7854b50de911edba0a\n");
    pushForReviewRejected(testRepo, "multiple Change-Id lines in commit message footer");

    setRequireChangeId(InheritableBoolean.FALSE);
    pushForReviewRejected(testRepo, "multiple Change-Id lines in commit message footer");
  }

  @Test
  public void pushWithInvalidChangeId() throws Exception {
    testpushWithInvalidChangeId();
  }

  @Test
  public void pushWithInvalidChangeIdWithCreateNewChangeForAllNotInTarget() throws Exception {
    enableCreateNewChangeForAllNotInTarget();
    testpushWithInvalidChangeId();
  }

  private void testpushWithInvalidChangeId() throws Exception {
    createCommit(testRepo, "Message with invalid Change-Id\n\nChange-Id: X\n");
    pushForReviewRejected(testRepo, "invalid Change-Id line format in commit message footer");

    setRequireChangeId(InheritableBoolean.FALSE);
    pushForReviewRejected(testRepo, "invalid Change-Id line format in commit message footer");
  }

  @Test
  public void pushWithInvalidChangeIdFromEgit() throws Exception {
    testPushWithInvalidChangeIdFromEgit();
  }

  @Test
  public void pushWithInvalidChangeIdFromEgitWithCreateNewChangeForAllNotInTarget()
      throws Exception {
    enableCreateNewChangeForAllNotInTarget();
    testPushWithInvalidChangeIdFromEgit();
  }

  private void testPushWithInvalidChangeIdFromEgit() throws Exception {
    createCommit(
        testRepo,
        "Message with invalid Change-Id\n"
            + "\n"
            + "Change-Id: I0000000000000000000000000000000000000000\n");
    pushForReviewRejected(testRepo, "invalid Change-Id line format in commit message footer");

    setRequireChangeId(InheritableBoolean.FALSE);
    pushForReviewRejected(testRepo, "invalid Change-Id line format in commit message footer");
  }

  @Test
  public void pushWithChangeIdInSubjectLine() throws Exception {
    createCommit(testRepo, "Change-Id: I1234000000000000000000000000000000000000");
    pushForReviewRejected(testRepo, "missing subject; Change-Id must be in commit message footer");

    setRequireChangeId(InheritableBoolean.FALSE);
    pushForReviewRejected(testRepo, "missing subject; Change-Id must be in commit message footer");
  }

  @Test
  public void pushCommitWithSameChangeIdAsPredecessorChange() throws Exception {
    PushOneCommit push =
        pushFactory.create(
            db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, "a.txt", "content");
    PushOneCommit.Result r = push.to("refs/for/master");
    r.assertOkStatus();
    RevCommit commitChange1 = r.getCommit();

    createCommit(testRepo, commitChange1.getFullMessage());

    pushForReviewRejected(
        testRepo,
        "same Change-Id in multiple changes.\n"
            + "Squash the commits with the same Change-Id or ensure Change-Ids are unique for each"
            + " commit");

    ProjectConfig config = projectCache.checkedGet(project).getConfig();
    config
        .getProject()
        .setBooleanConfig(BooleanProjectConfig.REQUIRE_CHANGE_ID, InheritableBoolean.FALSE);
    saveProjectConfig(project, config);

    pushForReviewRejected(
        testRepo,
        "same Change-Id in multiple changes.\n"
            + "Squash the commits with the same Change-Id or ensure Change-Ids are unique for each"
            + " commit");
  }

  @Test
  public void pushTwoCommitWithSameChangeId() throws Exception {
    RevCommit commitChange1 = createCommitWithChangeId(testRepo, "some change");

    createCommit(testRepo, commitChange1.getFullMessage());

    pushForReviewRejected(
        testRepo,
        "same Change-Id in multiple changes.\n"
            + "Squash the commits with the same Change-Id or ensure Change-Ids are unique for each"
            + " commit");

    ProjectConfig config = projectCache.checkedGet(project).getConfig();
    config
        .getProject()
        .setBooleanConfig(BooleanProjectConfig.REQUIRE_CHANGE_ID, InheritableBoolean.FALSE);
    saveProjectConfig(project, config);

    pushForReviewRejected(
        testRepo,
        "same Change-Id in multiple changes.\n"
            + "Squash the commits with the same Change-Id or ensure Change-Ids are unique for each"
            + " commit");
  }

  private static RevCommit createCommit(TestRepository<?> testRepo, String message)
      throws Exception {
    return testRepo.branch("HEAD").commit().message(message).add("a.txt", "content").create();
  }

  private static RevCommit createCommitWithChangeId(TestRepository<?> testRepo, String message)
      throws Exception {
    RevCommit c =
        testRepo
            .branch("HEAD")
            .commit()
            .message(message)
            .insertChangeId()
            .add("a.txt", "content")
            .create();
    return testRepo.getRevWalk().parseCommit(c);
  }

  @Test
  public void cantAutoCloseChangeAlreadyMergedToBranch() throws Exception {
    PushOneCommit.Result r1 = createChange();
    Change.Id id1 = r1.getChange().getId();
    PushOneCommit.Result r2 = createChange();
    Change.Id id2 = r2.getChange().getId();

    // Merge change 1 behind Gerrit's back.
    try (Repository repo = repoManager.openRepository(project)) {
      TestRepository<?> tr = new TestRepository<>(repo);
      tr.branch("refs/heads/master").update(r1.getCommit());
    }

    assertThat(gApi.changes().id(id1.get()).info().status).isEqualTo(ChangeStatus.NEW);
    assertThat(gApi.changes().id(id2.get()).info().status).isEqualTo(ChangeStatus.NEW);
    r2 = amendChange(r2.getChangeId());
    r2.assertOkStatus();

    // Change 1 is still new despite being merged into the branch, because
    // ReceiveCommits only considers commits between the branch tip (which is
    // now the merged change 1) and the push tip (new patch set of change 2).
    assertThat(gApi.changes().id(id1.get()).info().status).isEqualTo(ChangeStatus.NEW);
    assertThat(gApi.changes().id(id2.get()).info().status).isEqualTo(ChangeStatus.NEW);
  }

  @Test
  @GerritConfig(name = "receive.allowPushToRefsChanges", value = "true")
  public void accidentallyPushNewPatchSetDirectlyToBranchAndRecoverByPushingToRefsChanges()
      throws Exception {
    Change.Id id = accidentallyPushNewPatchSetDirectlyToBranch();
    ChangeData cd = byChangeId(id);
    String ps1Rev = Iterables.getOnlyElement(cd.patchSets()).getRevision().get();

    String r = "refs/changes/" + id;
    assertPushOk(pushHead(testRepo, r, false), r);

    // Added a new patch set and auto-closed the change.
    cd = byChangeId(id);
    assertThat(cd.change().getStatus()).isEqualTo(Change.Status.MERGED);
    assertThat(getPatchSetRevisions(cd))
        .containsExactlyEntriesIn(
            ImmutableMap.of(1, ps1Rev, 2, testRepo.getRepository().resolve("HEAD").name()));
  }

  @Test
  public void accidentallyPushNewPatchSetDirectlyToBranchAndCantRecoverByPushingToRefsFor()
      throws Exception {
    Change.Id id = accidentallyPushNewPatchSetDirectlyToBranch();
    ChangeData cd = byChangeId(id);
    String ps1Rev = Iterables.getOnlyElement(cd.patchSets()).getRevision().get();

    String r = "refs/for/master";
    assertPushRejected(pushHead(testRepo, r, false), r, "no new changes");

    // Change not updated.
    cd = byChangeId(id);
    assertThat(cd.change().getStatus()).isEqualTo(Change.Status.NEW);
    assertThat(getPatchSetRevisions(cd)).containsExactlyEntriesIn(ImmutableMap.of(1, ps1Rev));
  }

  @Test
  public void forcePushAbandonedChange() throws Exception {
    grant(project, "refs/*", Permission.PUSH, true);
    PushOneCommit push1 =
        pushFactory.create(db, admin.getIdent(), testRepo, "change1", "a.txt", "content");
    PushOneCommit.Result r = push1.to("refs/for/master");
    r.assertOkStatus();

    // abandon the change
    String changeId = r.getChangeId();
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
    gApi.changes().id(changeId).abandon();
    ChangeInfo info = get(changeId);
    assertThat(info.status).isEqualTo(ChangeStatus.ABANDONED);

    push1.setForce(true);
    PushOneCommit.Result r1 = push1.to("refs/heads/master");
    r1.assertOkStatus();
    ChangeInfo result = Iterables.getOnlyElement(gApi.changes().query(r.getChangeId()).get());
    assertThat(result.status).isEqualTo(ChangeStatus.MERGED);
  }

  private Change.Id accidentallyPushNewPatchSetDirectlyToBranch() throws Exception {
    PushOneCommit.Result r = createChange();
    RevCommit ps1Commit = r.getCommit();
    Change c = r.getChange().change();

    RevCommit ps2Commit;
    try (Repository repo = repoManager.openRepository(project)) {
      // Create a new patch set of the change directly in Gerrit's repository,
      // without pushing it. In reality it's more likely that the client would
      // create and push this behind Gerrit's back (e.g. an admin accidentally
      // using direct ssh access to the repo), but that's harder to do in tests.
      TestRepository<?> tr = new TestRepository<>(repo);
      ps2Commit =
          tr.branch("refs/heads/master")
              .commit()
              .message(ps1Commit.getShortMessage() + " v2")
              .insertChangeId(r.getChangeId().substring(1))
              .create();
    }

    testRepo.git().fetch().setRefSpecs(new RefSpec("refs/heads/master")).call();
    testRepo.reset(ps2Commit);

    ChangeData cd = byCommit(ps1Commit);
    assertThat(cd.change().getStatus()).isEqualTo(Change.Status.NEW);
    assertThat(getPatchSetRevisions(cd))
        .containsExactlyEntriesIn(ImmutableMap.of(1, ps1Commit.name()));
    return c.getId();
  }

  @Test
  public void pushWithEmailInFooter() throws Exception {
    pushWithReviewerInFooter(user.emailAddress.toString(), user);
  }

  @Test
  public void pushWithNameInFooter() throws Exception {
    pushWithReviewerInFooter(user.fullName, user);
  }

  @Test
  public void pushWithEmailInFooterNotFound() throws Exception {
    pushWithReviewerInFooter(new Address("No Body", "notarealuser@example.com").toString(), null);
  }

  @Test
  public void pushWithNameInFooterNotFound() throws Exception {
    pushWithReviewerInFooter("Notauser", null);
  }

  @Test
  public void pushNewPatchsetOverridingStickyLabel() throws Exception {
    ProjectConfig cfg = projectCache.checkedGet(project).getConfig();
    LabelType codeReview = Util.codeReview();
    codeReview.setCopyMaxScore(true);
    cfg.getLabelSections().put(codeReview.getName(), codeReview);
    saveProjectConfig(cfg);

    PushOneCommit.Result r = pushTo("refs/for/master%l=Code-Review+2");
    r.assertOkStatus();
    PushOneCommit push =
        pushFactory.create(
            db,
            admin.getIdent(),
            testRepo,
            PushOneCommit.SUBJECT,
            "b.txt",
            "anotherContent",
            r.getChangeId());
    r = push.to("refs/for/master%l=Code-Review+1");
    r.assertOkStatus();
  }

  @Test
  public void createChangeForMergedCommit() throws Exception {
    String master = "refs/heads/master";
    grant(project, master, Permission.PUSH, true);

    // Update master with a direct push.
    RevCommit c1 = testRepo.commit().message("Non-change 1").create();
    RevCommit c2 =
        testRepo.parseBody(
            testRepo.commit().parent(c1).message("Non-change 2").insertChangeId().create());
    String changeId = Iterables.getOnlyElement(c2.getFooterLines(CHANGE_ID));

    testRepo.reset(c2);
    assertPushOk(pushHead(testRepo, master, false, true), master);

    String q = "commit:" + c1.name() + " OR commit:" + c2.name() + " OR change:" + changeId;
    assertThat(gApi.changes().query(q).get()).isEmpty();

    // Push c2 as a merged change.
    String r = "refs/for/master%merged";
    assertPushOk(pushHead(testRepo, r, false), r);

    EnumSet<ListChangesOption> opts = EnumSet.of(ListChangesOption.CURRENT_REVISION);
    ChangeInfo info = gApi.changes().id(changeId).get(opts);
    assertThat(info.currentRevision).isEqualTo(c2.name());
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);

    // Only c2 was created as a change.
    String q1 = "commit: " + c1.name();
    assertThat(gApi.changes().query(q1).get()).isEmpty();

    // Push c1 as a merged change.
    testRepo.reset(c1);
    assertPushOk(pushHead(testRepo, r, false), r);
    List<ChangeInfo> infos = gApi.changes().query(q1).withOptions(opts).get();
    assertThat(infos).hasSize(1);
    info = infos.get(0);
    assertThat(info.currentRevision).isEqualTo(c1.name());
    assertThat(info.status).isEqualTo(ChangeStatus.MERGED);
  }

  @Test
  public void mergedOptionFailsWhenCommitIsNotMerged() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master%merged");
    r.assertErrorStatus("not merged into branch");
  }

  @Test
  public void mergedOptionFailsWhenCommitIsMergedOnOtherBranch() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).current().submit();

    try (Repository repo = repoManager.openRepository(project)) {
      TestRepository<?> tr = new TestRepository<>(repo);
      tr.branch("refs/heads/branch").commit().message("Initial commit on branch").create();
    }

    pushTo("refs/for/master%merged").assertErrorStatus("not merged into branch");
  }

  @Test
  public void mergedOptionFailsWhenChangeExists() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).current().submit();

    testRepo.reset(r.getCommit());
    String ref = "refs/for/master%merged";
    PushResult pr = pushHead(testRepo, ref, false);
    RemoteRefUpdate rru = pr.getRemoteUpdate(ref);
    assertThat(rru.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
    assertThat(rru.getMessage()).contains("no new changes");
  }

  @Test
  public void mergedOptionWithNewCommitWithSameChangeIdFails() throws Exception {
    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    gApi.changes().id(r.getChangeId()).current().review(ReviewInput.approve());
    gApi.changes().id(r.getChangeId()).current().submit();

    RevCommit c2 =
        testRepo
            .amend(r.getCommit())
            .message("New subject")
            .insertChangeId(r.getChangeId().substring(1))
            .create();
    testRepo.reset(c2);

    String ref = "refs/for/master%merged";
    PushResult pr = pushHead(testRepo, ref, false);
    RemoteRefUpdate rru = pr.getRemoteUpdate(ref);
    assertThat(rru.getStatus()).isEqualTo(RemoteRefUpdate.Status.REJECTED_OTHER_REASON);
    assertThat(rru.getMessage()).contains("not merged into branch");
  }

  @Test
  public void mergedOptionWithExistingChangeInsertsPatchSet() throws Exception {
    String master = "refs/heads/master";
    grant(project, master, Permission.PUSH, true);

    PushOneCommit.Result r = pushTo("refs/for/master");
    r.assertOkStatus();
    ObjectId c1 = r.getCommit().copy();

    // Create a PS2 commit directly on master in the server's repo. This
    // simulates the client amending locally and pushing directly to the branch,
    // expecting the change to be auto-closed, but the change metadata update
    // fails.
    ObjectId c2;
    try (Repository repo = repoManager.openRepository(project)) {
      TestRepository<?> tr = new TestRepository<>(repo);
      RevCommit commit2 =
          tr.amend(c1).message("New subject").insertChangeId(r.getChangeId().substring(1)).create();
      c2 = commit2.copy();
      tr.update(master, c2);
    }

    testRepo.git().fetch().setRefSpecs(new RefSpec("refs/heads/master")).call();
    testRepo.reset(c2);

    String ref = "refs/for/master%merged";
    assertPushOk(pushHead(testRepo, ref, false), ref);

    ChangeInfo info = gApi.changes().id(r.getChangeId()).get(ALL_REVISIONS);
    assertThat(info.currentRevision).isEqualTo(c2.name());
    assertThat(info.revisions.keySet()).containsExactly(c1.name(), c2.name());
    // TODO(dborowitz): Fix ReceiveCommits to also auto-close the change.
    assertThat(info.status).isEqualTo(ChangeStatus.NEW);
  }

  @Test
  public void publishCommentsOnPushPublishesDraftsOnAllRevisions() throws Exception {
    PushOneCommit.Result r = createChange();
    String rev1 = r.getCommit().name();
    CommentInfo c1 = addDraft(r.getChangeId(), rev1, newDraft(FILE_NAME, 1, "comment1"));
    CommentInfo c2 = addDraft(r.getChangeId(), rev1, newDraft(FILE_NAME, 1, "comment2"));

    r = amendChange(r.getChangeId());
    String rev2 = r.getCommit().name();
    CommentInfo c3 = addDraft(r.getChangeId(), rev2, newDraft(FILE_NAME, 1, "comment3"));

    assertThat(getPublishedComments(r.getChangeId())).isEmpty();

    gApi.changes().id(r.getChangeId()).addReviewer(user.email);
    sender.clear();
    amendChange(r.getChangeId(), "refs/for/master%publish-comments");

    Collection<CommentInfo> comments = getPublishedComments(r.getChangeId());
    assertThat(comments.stream().map(c -> c.id)).containsExactly(c1.id, c2.id, c3.id);
    assertThat(comments.stream().map(c -> c.message))
        .containsExactly("comment1", "comment2", "comment3");
    assertThat(getLastMessage(r.getChangeId())).isEqualTo("Uploaded patch set 3.\n\n(3 comments)");

    List<String> messages =
        sender
            .getMessages()
            .stream()
            .map(m -> m.body())
            .sorted(Comparator.comparingInt(m -> m.contains("reexamine") ? 0 : 1))
            .collect(toList());
    assertThat(messages).hasSize(2);

    assertThat(messages.get(0)).contains("Gerrit-MessageType: newpatchset");
    assertThat(messages.get(0)).contains("I'd like you to reexamine a change");
    assertThat(messages.get(0)).doesNotContain("Uploaded patch set 3");

    assertThat(messages.get(1)).contains("Gerrit-MessageType: comment");
    assertThat(messages.get(1))
        .containsMatch(
            Pattern.compile(
                // A little weird that the comment email contains this text, but it's actually
                // what's in the ChangeMessage. Really we should fuse the emails into one, but until
                // then, this test documents the current behavior.
                "Uploaded patch set 3\\.\n"
                    + "\n"
                    + "\\(3 comments\\)\\n.*"
                    + "PS1, Line 1:.*"
                    + "comment1\\n.*"
                    + "PS1, Line 1:.*"
                    + "comment2\\n.*"
                    + "PS2, Line 1:.*"
                    + "comment3\\n",
                Pattern.DOTALL));
  }

  @Test
  public void publishCommentsOnPushWithMessage() throws Exception {
    PushOneCommit.Result r = createChange();
    String rev = r.getCommit().name();
    addDraft(r.getChangeId(), rev, newDraft(FILE_NAME, 1, "comment1"));

    r = amendChange(r.getChangeId(), "refs/for/master%publish-comments,m=The_message");

    Collection<CommentInfo> comments = getPublishedComments(r.getChangeId());
    assertThat(comments.stream().map(c -> c.message)).containsExactly("comment1");
    assertThat(getLastMessage(r.getChangeId()))
        .isEqualTo("Uploaded patch set 2.\n\n(1 comment)\n\nThe message");
  }

  @Test
  public void publishCommentsOnPushPublishesDraftsOnMultipleChanges() throws Exception {
    ObjectId initialHead = testRepo.getRepository().resolve("HEAD");
    List<RevCommit> commits = createChanges(2, "refs/for/master");
    String id1 = byCommit(commits.get(0)).change().getKey().get();
    String id2 = byCommit(commits.get(1)).change().getKey().get();
    CommentInfo c1 = addDraft(id1, commits.get(0).name(), newDraft(FILE_NAME, 1, "comment1"));
    CommentInfo c2 = addDraft(id2, commits.get(1).name(), newDraft(FILE_NAME, 1, "comment2"));

    assertThat(getPublishedComments(id1)).isEmpty();
    assertThat(getPublishedComments(id2)).isEmpty();

    amendChanges(initialHead, commits, "refs/for/master%publish-comments");

    Collection<CommentInfo> cs1 = getPublishedComments(id1);
    assertThat(cs1.stream().map(c -> c.message)).containsExactly("comment1");
    assertThat(cs1.stream().map(c -> c.id)).containsExactly(c1.id);
    assertThat(getLastMessage(id1))
        .isEqualTo("Uploaded patch set 2: Commit message was updated.\n\n(1 comment)");

    Collection<CommentInfo> cs2 = getPublishedComments(id2);
    assertThat(cs2.stream().map(c -> c.message)).containsExactly("comment2");
    assertThat(cs2.stream().map(c -> c.id)).containsExactly(c2.id);
    assertThat(getLastMessage(id2))
        .isEqualTo("Uploaded patch set 2: Commit message was updated.\n\n(1 comment)");
  }

  @Test
  public void publishCommentsOnPushOnlyPublishesDraftsOnUpdatedChanges() throws Exception {
    PushOneCommit.Result r1 = createChange();
    PushOneCommit.Result r2 = createChange();
    String id1 = r1.getChangeId();
    String id2 = r2.getChangeId();
    addDraft(id1, r1.getCommit().name(), newDraft(FILE_NAME, 1, "comment1"));
    CommentInfo c2 = addDraft(id2, r2.getCommit().name(), newDraft(FILE_NAME, 1, "comment2"));

    assertThat(getPublishedComments(id1)).isEmpty();
    assertThat(getPublishedComments(id2)).isEmpty();

    r2 = amendChange(id2, "refs/for/master%publish-comments");

    assertThat(getPublishedComments(id1)).isEmpty();
    assertThat(gApi.changes().id(id1).drafts()).hasSize(1);

    Collection<CommentInfo> cs2 = getPublishedComments(id2);
    assertThat(cs2.stream().map(c -> c.message)).containsExactly("comment2");
    assertThat(cs2.stream().map(c -> c.id)).containsExactly(c2.id);

    assertThat(getLastMessage(id1)).doesNotMatch("[Cc]omment");
    assertThat(getLastMessage(id2)).isEqualTo("Uploaded patch set 2.\n\n(1 comment)");
  }

  @Test
  public void publishCommentsOnPushWithPreference() throws Exception {
    PushOneCommit.Result r = createChange();
    addDraft(r.getChangeId(), r.getCommit().name(), newDraft(FILE_NAME, 1, "comment1"));
    r = amendChange(r.getChangeId());

    assertThat(getPublishedComments(r.getChangeId())).isEmpty();

    GeneralPreferencesInfo prefs = gApi.accounts().id(admin.id.get()).getPreferences();
    prefs.publishCommentsOnPush = true;
    gApi.accounts().id(admin.id.get()).setPreferences(prefs);

    r = amendChange(r.getChangeId());
    assertThat(getPublishedComments(r.getChangeId()).stream().map(c -> c.message))
        .containsExactly("comment1");
  }

  @Test
  public void publishCommentsOnPushOverridingPreference() throws Exception {
    PushOneCommit.Result r = createChange();
    addDraft(r.getChangeId(), r.getCommit().name(), newDraft(FILE_NAME, 1, "comment1"));

    GeneralPreferencesInfo prefs = gApi.accounts().id(admin.id.get()).getPreferences();
    prefs.publishCommentsOnPush = true;
    gApi.accounts().id(admin.id.get()).setPreferences(prefs);

    r = amendChange(r.getChangeId(), "refs/for/master%no-publish-comments");

    assertThat(getPublishedComments(r.getChangeId())).isEmpty();
  }

  @Test
  public void pushDraftGetsPrivateChange() throws Exception {
    String changeId1 = createChange("refs/drafts/master").getChangeId();
    String changeId2 = createChange("refs/for/master%draft").getChangeId();

    ChangeInfo info1 = gApi.changes().id(changeId1).get();
    ChangeInfo info2 = gApi.changes().id(changeId2).get();

    assertThat(info1.status).isEqualTo(ChangeStatus.NEW);
    assertThat(info2.status).isEqualTo(ChangeStatus.NEW);
    assertThat(info1.isPrivate).isTrue();
    assertThat(info2.isPrivate).isTrue();
    assertThat(info1.revisions).hasSize(1);
    assertThat(info2.revisions).hasSize(1);
  }

  @Sandboxed
  @Test
  public void pushWithDraftOptionToExistingNewChangeGetsChangeEdit() throws Exception {
    String changeId = createChange().getChangeId();
    EditInfoSubject.assertThat(getEdit(changeId)).isAbsent();

    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    ChangeStatus originalChangeStatus = changeInfo.status;

    PushOneCommit.Result result = amendChange(changeId, "refs/drafts/master");
    result.assertOkStatus();

    changeInfo = gApi.changes().id(changeId).get();
    assertThat(changeInfo.status).isEqualTo(originalChangeStatus);
    assertThat(changeInfo.isPrivate).isNull();
    assertThat(changeInfo.revisions).hasSize(1);

    EditInfoSubject.assertThat(getEdit(changeId)).isPresent();
  }

  @GerritConfig(name = "receive.maxBatchCommits", value = "2")
  @Test
  public void maxBatchCommits() throws Exception {
    List<RevCommit> commits = new ArrayList<>();
    commits.addAll(initChanges(2));
    String master = "refs/heads/master";
    assertPushOk(pushHead(testRepo, master), master);

    commits.addAll(initChanges(3));
    assertPushRejected(pushHead(testRepo, master), master, "too many commits");

    grantSkipValidation(project, master, SystemGroupBackend.REGISTERED_USERS);
    PushResult r =
        pushHead(testRepo, master, false, false, ImmutableList.of(PUSH_OPTION_SKIP_VALIDATION));
    assertPushOk(r, master);

    // No open changes; branch was advanced.
    String q = commits.stream().map(ObjectId::name).collect(joining(" OR commit:", "commit:", ""));
    assertThat(gApi.changes().query(q).get()).isEmpty();
    assertThat(gApi.projects().name(project.get()).branch(master).get().revision)
        .isEqualTo(Iterables.getLast(commits).name());
  }

  @Test
  public void pushToPublishMagicBranchIsAllowed() throws Exception {
    // Push to "refs/publish/*" will be a synonym of "refs/for/*".
    createChange("refs/publish/master");
    PushOneCommit.Result result = pushTo("refs/publish/master");
    result.assertOkStatus();
    assertThat(result.getMessage())
        .endsWith("Pushing to refs/publish/* is deprecated, use refs/for/* instead.\n");
  }

  @Test
  public void pushNoteDbRef() throws Exception {
    String ref = "refs/changes/34/1234/meta";
    RevCommit c = testRepo.commit().message("Junk NoteDb commit").create();
    PushResult pr = pushOne(testRepo, c.name(), ref, false, false, null);
    assertThat(pr.getMessages()).doesNotContain(NoteDbPushOption.OPTION_NAME);
    assertPushRejected(pr, ref, "NoteDb update requires -o notedb=allow");

    pr = pushOne(testRepo, c.name(), ref, false, false, ImmutableList.of("notedb=foobar"));
    assertThat(pr.getMessages()).contains("Invalid value in -o notedb=foobar");
    assertPushRejected(pr, ref, "NoteDb update requires -o notedb=allow");

    List<String> opts = ImmutableList.of("notedb=allow");
    pr = pushOne(testRepo, c.name(), ref, false, false, opts);
    assertPushRejected(pr, ref, "NoteDb update requires access database permission");

    allowGlobalCapabilities(REGISTERED_USERS, GlobalCapability.ACCESS_DATABASE);
    pr = pushOne(testRepo, c.name(), ref, false, false, opts);
    assertPushRejected(pr, ref, "prohibited by Gerrit: create not permitted for " + ref);

    grant(project, "refs/changes/*", Permission.CREATE);
    grant(project, "refs/changes/*", Permission.PUSH);
    grantSkipValidation(project, "refs/changes/*", REGISTERED_USERS);
    pr = pushOne(testRepo, c.name(), ref, false, false, opts);
    assertPushOk(pr, ref);
  }

  @Test
  public void pushNoteDbRefWithoutOptionOnlyFailsThatCommand() throws Exception {
    String ref = "refs/changes/34/1234/meta";
    RevCommit noteDbCommit = testRepo.commit().message("Junk NoteDb commit").create();
    RevCommit changeCommit =
        testRepo.branch("HEAD").commit().message("A change").insertChangeId().create();
    PushResult pr =
        Iterables.getOnlyElement(
            testRepo
                .git()
                .push()
                .setRefSpecs(
                    new RefSpec(noteDbCommit.name() + ":" + ref),
                    new RefSpec(changeCommit.name() + ":refs/for/master"))
                .call());

    assertPushRejected(pr, ref, "NoteDb update requires -o notedb=allow");
    assertPushOk(pr, "refs/for/master");
  }

  private DraftInput newDraft(String path, int line, String message) {
    DraftInput d = new DraftInput();
    d.path = path;
    d.side = Side.REVISION;
    d.line = line;
    d.message = message;
    d.unresolved = true;
    return d;
  }

  private CommentInfo addDraft(String changeId, String revId, DraftInput in) throws Exception {
    return gApi.changes().id(changeId).revision(revId).createDraft(in).get();
  }

  private Collection<CommentInfo> getPublishedComments(String changeId) throws Exception {
    return gApi.changes()
        .id(changeId)
        .comments()
        .values()
        .stream()
        .flatMap(cs -> cs.stream())
        .collect(toList());
  }

  private String getLastMessage(String changeId) throws Exception {
    return Streams.findLast(
            gApi.changes().id(changeId).get(MESSAGES).messages.stream().map(m -> m.message))
        .get();
  }

  private void assertThatUserIsOnlyReviewer(ChangeInfo ci, TestAccount reviewer) {
    assertThat(ci.reviewers).isNotNull();
    assertThat(ci.reviewers.keySet()).containsExactly(ReviewerState.REVIEWER);
    assertThat(ci.reviewers.get(ReviewerState.REVIEWER).iterator().next().email)
        .isEqualTo(reviewer.email);
  }

  private void pushWithReviewerInFooter(String nameEmail, TestAccount expectedReviewer)
      throws Exception {
    int n = 5;
    String r = "refs/for/master";
    ObjectId initialHead = testRepo.getRepository().resolve("HEAD");
    List<RevCommit> commits = createChanges(n, r, ImmutableList.of("Acked-By: " + nameEmail));
    for (int i = 0; i < n; i++) {
      RevCommit c = commits.get(i);
      ChangeData cd = byCommit(c);
      String name = "reviewers for " + (i + 1);
      if (expectedReviewer != null) {
        assertThat(cd.reviewers().all()).named(name).containsExactly(expectedReviewer.getId());
        gApi.changes().id(cd.getId().get()).reviewer(expectedReviewer.getId().toString()).remove();
      }
      assertThat(byCommit(c).reviewers().all()).named(name).isEmpty();
    }

    List<RevCommit> commits2 = amendChanges(initialHead, commits, r);
    for (int i = 0; i < n; i++) {
      RevCommit c = commits2.get(i);
      ChangeData cd = byCommit(c);
      String name = "reviewers for " + (i + 1);
      if (expectedReviewer != null) {
        assertThat(cd.reviewers().all()).named(name).containsExactly(expectedReviewer.getId());
      } else {
        assertThat(byCommit(c).reviewers().all()).named(name).isEmpty();
      }
    }
  }

  private List<RevCommit> createChanges(int n, String refsFor) throws Exception {
    return createChanges(n, refsFor, ImmutableList.of());
  }

  private List<RevCommit> createChanges(int n, String refsFor, List<String> footerLines)
      throws Exception {
    List<RevCommit> commits = initChanges(n, footerLines);
    assertPushOk(pushHead(testRepo, refsFor, false), refsFor);
    return commits;
  }

  private List<RevCommit> initChanges(int n) throws Exception {
    return initChanges(n, ImmutableList.of());
  }

  private List<RevCommit> initChanges(int n, List<String> footerLines) throws Exception {
    List<RevCommit> commits = new ArrayList<>(n);
    for (int i = 1; i <= n; i++) {
      String msg = "Change " + i;
      if (!footerLines.isEmpty()) {
        StringBuilder sb = new StringBuilder(msg).append("\n\n");
        for (String line : footerLines) {
          sb.append(line).append('\n');
        }
        msg = sb.toString();
      }
      TestRepository<?>.CommitBuilder cb =
          testRepo.branch("HEAD").commit().message(msg).insertChangeId();
      if (!commits.isEmpty()) {
        cb.parent(commits.get(commits.size() - 1));
      }
      RevCommit c = cb.create();
      testRepo.getRevWalk().parseBody(c);
      commits.add(c);
    }
    return commits;
  }

  private List<RevCommit> amendChanges(
      ObjectId initialHead, List<RevCommit> origCommits, String refsFor) throws Exception {
    testRepo.reset(initialHead);
    List<RevCommit> newCommits = new ArrayList<>(origCommits.size());
    for (RevCommit c : origCommits) {
      String msg = c.getShortMessage() + "v2";
      if (!c.getShortMessage().equals(c.getFullMessage())) {
        msg = msg + c.getFullMessage().substring(c.getShortMessage().length());
      }
      TestRepository<?>.CommitBuilder cb = testRepo.branch("HEAD").commit().message(msg);
      if (!newCommits.isEmpty()) {
        cb.parent(origCommits.get(newCommits.size() - 1));
      }
      RevCommit c2 = cb.create();
      testRepo.getRevWalk().parseBody(c2);
      newCommits.add(c2);
    }
    assertPushOk(pushHead(testRepo, refsFor, false), refsFor);
    return newCommits;
  }

  private static Map<Integer, String> getPatchSetRevisions(ChangeData cd) throws Exception {
    Map<Integer, String> revisions = new HashMap<>();
    for (PatchSet ps : cd.patchSets()) {
      revisions.put(ps.getPatchSetId(), ps.getRevision().get());
    }
    return revisions;
  }

  private ChangeData byCommit(ObjectId id) throws Exception {
    List<ChangeData> cds = queryProvider.get().byCommit(id);
    assertThat(cds).named("change for " + id.name()).hasSize(1);
    return cds.get(0);
  }

  private ChangeData byChangeId(Change.Id id) throws Exception {
    List<ChangeData> cds = queryProvider.get().byLegacyChangeId(id);
    assertThat(cds).named("change " + id).hasSize(1);
    return cds.get(0);
  }

  private static void pushForReviewOk(TestRepository<?> testRepo) throws GitAPIException {
    pushForReview(testRepo, RemoteRefUpdate.Status.OK, null);
  }

  private static void pushForReviewRejected(TestRepository<?> testRepo, String expectedMessage)
      throws GitAPIException {
    pushForReview(testRepo, RemoteRefUpdate.Status.REJECTED_OTHER_REASON, expectedMessage);
  }

  private static void pushForReview(
      TestRepository<?> testRepo, RemoteRefUpdate.Status expectedStatus, String expectedMessage)
      throws GitAPIException {
    String ref = "refs/for/master";
    PushResult r = pushHead(testRepo, ref);
    RemoteRefUpdate refUpdate = r.getRemoteUpdate(ref);
    assertThat(refUpdate.getStatus()).isEqualTo(expectedStatus);
    if (expectedMessage != null) {
      assertThat(refUpdate.getMessage()).contains(expectedMessage);
    }
  }

  private void grantSkipValidation(Project.NameKey project, String ref, AccountGroup.UUID groupUuid)
      throws Exception {
    // See SKIP_VALIDATION implementation in default permission backend.
    ProjectConfig config = projectCache.checkedGet(project).getConfig();
    Util.allow(config, Permission.FORGE_AUTHOR, groupUuid, ref);
    Util.allow(config, Permission.FORGE_COMMITTER, groupUuid, ref);
    Util.allow(config, Permission.FORGE_SERVER, groupUuid, ref);
    Util.allow(config, Permission.PUSH_MERGE, groupUuid, "refs/for/" + ref);
    saveProjectConfig(project, config);
  }
}
