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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assert_;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.extensions.client.ListChangesOption.SUBMITTABLE;
import static com.google.gerrit.server.group.SystemGroupBackend.CHANGE_OWNER;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static java.util.stream.Collectors.toList;
import static org.junit.Assert.fail;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.RegistrationHandle;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.change.Submit.TestSubmitInput;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.validators.OnSubmitValidationListener;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.Util;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gerrit.testutil.TestTimeUtil;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

@NoHttpd
public abstract class AbstractSubmit extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Inject private ApprovalsUtil approvalsUtil;

  @Inject private Submit submitHandler;

  @Inject private IdentifiedUser.GenericFactory userFactory;

  @Inject private DynamicSet<OnSubmitValidationListener> onSubmitValidationListeners;
  private RegistrationHandle onSubmitValidatorHandle;

  private String systemTimeZone;

  @Before
  public void setTimeForTesting() {
    systemTimeZone = System.setProperty("user.timezone", "US/Eastern");
    TestTimeUtil.resetWithClockStep(1, SECONDS);
  }

  @After
  public void resetTime() {
    TestTimeUtil.useSystemTime();
    System.setProperty("user.timezone", systemTimeZone);
  }

  @After
  public void cleanup() {
    db.close();
  }

  @After
  public void removeOnSubmitValidator() {
    if (onSubmitValidatorHandle != null) {
      onSubmitValidatorHandle.remove();
    }
  }

  protected abstract SubmitType getSubmitType();

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void submitToEmptyRepo() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change = createChange();
    Map<Branch.NameKey, ObjectId> actual = fetchFromSubmitPreview(change.getChangeId());
    RevCommit headAfterSubmitPreview = getRemoteHead();
    assertThat(headAfterSubmitPreview).isEqualTo(initialHead);
    assertThat(actual).hasSize(1);

    submit(change.getChangeId());
    assertThat(getRemoteHead().getId()).isEqualTo(change.getCommit());
    assertTrees(project, actual);
  }

  @Test
  public void submitSingleChange() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change = createChange();
    Map<Branch.NameKey, ObjectId> actual = fetchFromSubmitPreview(change.getChangeId());
    RevCommit headAfterSubmit = getRemoteHead();
    assertThat(headAfterSubmit).isEqualTo(initialHead);
    assertRefUpdatedEvents();
    assertChangeMergedEvents();

    if ((getSubmitType() == SubmitType.CHERRY_PICK)
        || (getSubmitType() == SubmitType.REBASE_ALWAYS)) {
      // The change is updated as well:
      assertThat(actual).hasSize(2);
    } else {
      assertThat(actual).hasSize(1);
    }

    submit(change.getChangeId());
    assertTrees(project, actual);
  }

  @Test
  public void submitMultipleChangesOtherMergeConflictPreview() throws Exception {
    RevCommit initialHead = getRemoteHead();

    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit headAfterFirstSubmit = getRemoteHead();
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "other content");
    PushOneCommit.Result change3 = createChange("Change 3", "d", "d");
    PushOneCommit.Result change4 = createChange("Change 4", "e", "e");
    // change 2 is not approved, but we ignore labels
    approve(change3.getChangeId());

    try (BinaryResult request = submitPreview(change4.getChangeId())) {
      assertThat(getSubmitType()).isEqualTo(SubmitType.CHERRY_PICK);
      submit(change4.getChangeId());
    } catch (RestApiException e) {
      switch (getSubmitType()) {
        case FAST_FORWARD_ONLY:
          assertThat(e.getMessage())
              .isEqualTo(
                  "Failed to submit 3 changes due to the following problems:\n"
                      + "Change "
                      + change2.getChange().getId()
                      + ": internal error: "
                      + "change not processed by merge strategy\n"
                      + "Change "
                      + change3.getChange().getId()
                      + ": internal error: "
                      + "change not processed by merge strategy\n"
                      + "Change "
                      + change4.getChange().getId()
                      + ": Project policy "
                      + "requires all submissions to be a fast-forward. Please "
                      + "rebase the change locally and upload again for review.");
          break;
        case REBASE_IF_NECESSARY:
        case REBASE_ALWAYS:
          String change2hash = change2.getChange().currentPatchSet().getRevision().get();
          assertThat(e.getMessage())
              .isEqualTo(
                  "Cannot rebase "
                      + change2hash
                      + ": The change could "
                      + "not be rebased due to a conflict during merge.");
          break;
        case MERGE_ALWAYS:
        case MERGE_IF_NECESSARY:
          assertThat(e.getMessage())
              .isEqualTo(
                  "Failed to submit 3 changes due to the following problems:\n"
                      + "Change "
                      + change2.getChange().getId()
                      + ": Change could not be "
                      + "merged due to a path conflict. Please rebase the change "
                      + "locally and upload the rebased commit for review.\n"
                      + "Change "
                      + change3.getChange().getId()
                      + ": Change could not be "
                      + "merged due to a path conflict. Please rebase the change "
                      + "locally and upload the rebased commit for review.\n"
                      + "Change "
                      + change4.getChange().getId()
                      + ": Change could not be "
                      + "merged due to a path conflict. Please rebase the change "
                      + "locally and upload the rebased commit for review.");
          break;
        case CHERRY_PICK:
        default:
          fail("Should not reach here.");
          break;
      }

      RevCommit headAfterSubmit = getRemoteHead();
      assertThat(headAfterSubmit).isEqualTo(headAfterFirstSubmit);
      assertRefUpdatedEvents(initialHead, headAfterFirstSubmit);
      assertChangeMergedEvents(change.getChangeId(), headAfterFirstSubmit.name());
    }
  }

  @Test
  public void submitMultipleChangesPreview() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "other content");
    PushOneCommit.Result change3 = createChange("Change 3", "d", "d");
    PushOneCommit.Result change4 = createChange("Change 4", "e", "e");
    // change 2 is not approved, but we ignore labels
    approve(change3.getChangeId());
    Map<Branch.NameKey, ObjectId> actual = fetchFromSubmitPreview(change4.getChangeId());
    Map<String, Map<String, Integer>> expected = new HashMap<>();
    expected.put(project.get(), new HashMap<>());
    expected.get(project.get()).put("refs/heads/master", 3);

    assertThat(actual).containsKey(new Branch.NameKey(project, "refs/heads/master"));
    if (getSubmitType() == SubmitType.CHERRY_PICK) {
      // CherryPick ignores dependencies, thus only change and destination
      // branch refs are modified.
      assertThat(actual).hasSize(2);
    } else if (getSubmitType() == SubmitType.REBASE_ALWAYS) {
      // RebaseAlways takes care of dependencies, therefore Change{2,3,4} and
      // destination branch will be modified.
      assertThat(actual).hasSize(4);
    } else {
      assertThat(actual).hasSize(1);
    }

    // check that the submit preview did not actually submit
    RevCommit headAfterSubmit = getRemoteHead();
    assertThat(headAfterSubmit).isEqualTo(initialHead);
    assertRefUpdatedEvents();
    assertChangeMergedEvents();

    // now check we actually have the same content:
    approve(change2.getChangeId());
    submit(change4.getChangeId());
    assertTrees(project, actual);
  }

  @Test
  public void submitNoPermission() throws Exception {
    // create project where submit is blocked
    Project.NameKey p = createProject("p");
    block(p, "refs/*", Permission.SUBMIT, REGISTERED_USERS);

    TestRepository<InMemoryRepository> repo = cloneProject(p, admin);
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), repo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();

    submit(result.getChangeId(), new SubmitInput(), AuthException.class, "submit not permitted");
  }

  @Test
  public void noSelfSubmit() throws Exception {
    // create project where submit is blocked for the change owner
    Project.NameKey p = createProject("p");
    ProjectConfig cfg = projectCache.checkedGet(p).getConfig();
    Util.block(cfg, Permission.SUBMIT, CHANGE_OWNER, "refs/*");
    Util.allow(cfg, Permission.SUBMIT, REGISTERED_USERS, "refs/heads/*");
    Util.allow(cfg, Permission.forLabel("Code-Review"), -2, +2, REGISTERED_USERS, "refs/*");
    saveProjectConfig(p, cfg);

    TestRepository<InMemoryRepository> repo = cloneProject(p, admin);
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), repo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();

    ChangeInfo change = gApi.changes().id(result.getChangeId()).get();
    assertThat(change.owner._accountId).isEqualTo(admin.id.get());

    submit(result.getChangeId(), new SubmitInput(), AuthException.class, "submit not permitted");

    setApiUser(user);
    submit(result.getChangeId());
  }

  @Test
  public void onlySelfSubmit() throws Exception {
    // create project where only the change owner can submit
    Project.NameKey p = createProject("p");
    ProjectConfig cfg = projectCache.checkedGet(p).getConfig();
    Util.block(cfg, Permission.SUBMIT, REGISTERED_USERS, "refs/*");
    Util.allow(cfg, Permission.SUBMIT, CHANGE_OWNER, "refs/*");
    Util.allow(cfg, Permission.forLabel("Code-Review"), -2, +2, REGISTERED_USERS, "refs/*");
    saveProjectConfig(p, cfg);

    TestRepository<InMemoryRepository> repo = cloneProject(p, admin);
    PushOneCommit push = pushFactory.create(db, admin.getIdent(), repo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();

    ChangeInfo change = gApi.changes().id(result.getChangeId()).get();
    assertThat(change.owner._accountId).isEqualTo(admin.id.get());

    setApiUser(user);
    submit(result.getChangeId(), new SubmitInput(), AuthException.class, "submit not permitted");

    setApiUser(admin);
    submit(result.getChangeId());
  }

  @Test
  public void submitWholeTopicMultipleProjects() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    String topic = "test-topic";

    // Create test projects
    TestRepository<?> repoA = createProjectWithPush("project-a", null, getSubmitType());
    TestRepository<?> repoB = createProjectWithPush("project-b", null, getSubmitType());

    // Create changes on project-a
    PushOneCommit.Result change1 =
        createChange(repoA, "master", "Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 =
        createChange(repoA, "master", "Change 2", "b.txt", "content", topic);

    // Create changes on project-b
    PushOneCommit.Result change3 =
        createChange(repoB, "master", "Change 3", "a.txt", "content", topic);
    PushOneCommit.Result change4 =
        createChange(repoB, "master", "Change 4", "b.txt", "content", topic);

    approve(change1.getChangeId());
    approve(change2.getChangeId());
    approve(change3.getChangeId());
    approve(change4.getChangeId());
    submit(change4.getChangeId());

    String expectedTopic = name(topic);
    change1.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change2.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change3.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change4.assertChange(Change.Status.MERGED, expectedTopic, admin);
  }

  @Test
  public void submitWholeTopicMultipleBranchesOnSameProject() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    String topic = "test-topic";

    // Create test project
    String projectName = "project-a";
    TestRepository<?> repoA = createProjectWithPush(projectName, null, getSubmitType());

    RevCommit initialHead = getRemoteHead(new Project.NameKey(name(projectName)), "master");

    // Create the dev branch on the test project
    BranchInput in = new BranchInput();
    in.revision = initialHead.name();
    gApi.projects().name(name(projectName)).branch("dev").create(in);

    // Create changes on master
    PushOneCommit.Result change1 =
        createChange(repoA, "master", "Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 =
        createChange(repoA, "master", "Change 2", "b.txt", "content", topic);

    // Create  changes on dev
    repoA.reset(initialHead);
    PushOneCommit.Result change3 =
        createChange(repoA, "dev", "Change 3", "a.txt", "content", topic);
    PushOneCommit.Result change4 =
        createChange(repoA, "dev", "Change 4", "b.txt", "content", topic);

    approve(change1.getChangeId());
    approve(change2.getChangeId());
    approve(change3.getChangeId());
    approve(change4.getChangeId());
    submit(change4.getChangeId());

    String expectedTopic = name(topic);
    change1.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change2.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change3.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change4.assertChange(Change.Status.MERGED, expectedTopic, admin);
  }

  @Test
  public void submitWholeTopic() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    String topic = "test-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", topic);
    PushOneCommit.Result change3 = createChange("Change 3", "c.txt", "content", topic);
    approve(change1.getChangeId());
    approve(change2.getChangeId());
    approve(change3.getChangeId());
    submit(change3.getChangeId());
    String expectedTopic = name(topic);
    change1.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change2.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change3.assertChange(Change.Status.MERGED, expectedTopic, admin);

    // Check for the exact change to have the correct submitter.
    assertSubmitter(change3);
    // Also check submitters for changes submitted via the topic relationship.
    assertSubmitter(change1);
    assertSubmitter(change2);

    // Check that the repo has the expected commits
    List<RevCommit> log = getRemoteLog();
    List<String> commitsInRepo = log.stream().map(c -> c.getShortMessage()).collect(toList());
    int expectedCommitCount =
        getSubmitType() == SubmitType.MERGE_ALWAYS
            ? 5 // initial commit + 3 commits + merge commit
            : 4; // initial commit + 3 commits
    assertThat(log).hasSize(expectedCommitCount);

    assertThat(commitsInRepo)
        .containsAllOf("Initial empty repository", "Change 1", "Change 2", "Change 3");
    if (getSubmitType() == SubmitType.MERGE_ALWAYS) {
      assertThat(commitsInRepo).contains("Merge changes from topic '" + expectedTopic + "'");
    }
  }

  @Test
  public void submitDraftChange() throws Exception {
    PushOneCommit.Result draft = createDraftChange();
    Change.Id num = draft.getChange().getId();
    submitWithConflict(
        draft.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
            + "Change "
            + num
            + ": Change "
            + num
            + " is draft");
  }

  @Test
  public void submitWorkInProgressChange() throws Exception {
    PushOneCommit.Result change = createWorkInProgressChange();
    Change.Id num = change.getChange().getId();
    submitWithConflict(
        change.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
            + "Change "
            + num
            + ": Change "
            + num
            + " is work in progress");
  }

  @Test
  public void submitDraftPatchSet() throws Exception {
    PushOneCommit.Result change = createChange();
    PushOneCommit.Result draft = amendChangeAsDraft(change.getChangeId());
    Change.Id num = draft.getChange().getId();

    submitWithConflict(
        draft.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
            + "Change "
            + num
            + ": submit rule error: "
            + "Cannot submit draft patch sets");
  }

  @Test
  public void submitWithHiddenBranchInSameTopic() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    PushOneCommit.Result visible = createChange("refs/for/master/" + name("topic"));
    Change.Id num = visible.getChange().getId();

    createBranch(new Branch.NameKey(project, "hidden"));
    PushOneCommit.Result hidden = createChange("refs/for/hidden/" + name("topic"));
    approve(hidden.getChangeId());
    blockRead("refs/heads/hidden");

    submit(
        visible.getChangeId(),
        new SubmitInput(),
        AuthException.class,
        "A change to be submitted with " + num + " is not visible");
  }

  @Test
  public void submitChangeWhenParentOfOtherBranchTip() throws Exception {
    // Chain of two commits
    // Push both to topic-branch
    // Push the first commit for review and submit
    //
    // C2 -- tip of topic branch
    //  |
    // C1 -- pushed for review
    //  |
    // C0 -- Master
    //
    ProjectConfig config = projectCache.checkedGet(project).getConfig();
    config.getProject().setCreateNewChangeForAllNotInTarget(InheritableBoolean.TRUE);
    saveProjectConfig(project, config);

    PushOneCommit push1 =
        pushFactory.create(
            db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, "a.txt", "content");
    PushOneCommit.Result c1 = push1.to("refs/heads/topic");
    c1.assertOkStatus();
    PushOneCommit push2 =
        pushFactory.create(
            db, admin.getIdent(), testRepo, PushOneCommit.SUBJECT, "b.txt", "anotherContent");
    PushOneCommit.Result c2 = push2.to("refs/heads/topic");
    c2.assertOkStatus();

    PushOneCommit.Result change1 = push1.to("refs/for/master");
    change1.assertOkStatus();

    approve(change1.getChangeId());
    submit(change1.getChangeId());
  }

  @Test
  public void submitMergeOfNonChangeBranchTip() throws Exception {
    // Merge a branch with commits that have not been submitted as
    // changes.
    //
    // M  -- mergeCommit (pushed for review and submitted)
    // | \
    // |  S -- stable (pushed directly to refs/heads/stable)
    // | /
    // I   -- master
    //
    RevCommit master = getRemoteHead(project, "master");
    PushOneCommit stableTip =
        pushFactory.create(
            db, admin.getIdent(), testRepo, "Tip of branch stable", "stable.txt", "");
    PushOneCommit.Result stable = stableTip.to("refs/heads/stable");
    PushOneCommit mergeCommit =
        pushFactory.create(db, admin.getIdent(), testRepo, "The merge commit", "merge.txt", "");
    mergeCommit.setParents(ImmutableList.of(master, stable.getCommit()));
    PushOneCommit.Result mergeReview = mergeCommit.to("refs/for/master");
    approve(mergeReview.getChangeId());
    submit(mergeReview.getChangeId());

    List<RevCommit> log = getRemoteLog();
    assertThat(log).contains(stable.getCommit());
    assertThat(log).contains(mergeReview.getCommit());
  }

  @Test
  public void submitMergeOfNonChangeBranchNonTip() throws Exception {
    // Merge a branch with commits that have not been submitted as
    // changes.
    //
    // MC  -- merge commit (pushed for review and submitted)
    // |\   S2 -- new stable tip (pushed directly to refs/heads/stable)
    // M \ /
    // |  S1 -- stable (pushed directly to refs/heads/stable)
    // | /
    // I -- master
    //
    RevCommit initial = getRemoteHead(project, "master");
    // push directly to stable to S1
    PushOneCommit.Result s1 =
        pushFactory
            .create(db, admin.getIdent(), testRepo, "new commit into stable", "stable1.txt", "")
            .to("refs/heads/stable");
    // move the stable tip ahead to S2
    pushFactory
        .create(db, admin.getIdent(), testRepo, "Tip of branch stable", "stable2.txt", "")
        .to("refs/heads/stable");

    testRepo.reset(initial);

    // move the master ahead
    PushOneCommit.Result m =
        pushFactory
            .create(db, admin.getIdent(), testRepo, "Move master ahead", "master.txt", "")
            .to("refs/heads/master");

    // create merge change
    PushOneCommit mc =
        pushFactory.create(db, admin.getIdent(), testRepo, "The merge commit", "merge.txt", "");
    mc.setParents(ImmutableList.of(m.getCommit(), s1.getCommit()));
    PushOneCommit.Result mergeReview = mc.to("refs/for/master");
    approve(mergeReview.getChangeId());
    submit(mergeReview.getChangeId());

    List<RevCommit> log = getRemoteLog();
    assertThat(log).contains(s1.getCommit());
    assertThat(log).contains(mergeReview.getCommit());
  }

  @Test
  public void submitChangeWithCommitThatWasAlreadyMerged() throws Exception {
    // create and submit a change
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());
    RevCommit headAfterSubmit = getRemoteHead();

    // set the status of the change back to NEW to simulate a failed submit that
    // merged the commit but failed to update the change status
    setChangeStatusToNew(change);

    // submitting the change again should detect that the commit was already
    // merged and just fix the change status to be MERGED
    submit(change.getChangeId());
    assertThat(getRemoteHead()).isEqualTo(headAfterSubmit);
  }

  @Test
  public void submitChangesWithCommitsThatWereAlreadyMerged() throws Exception {
    // create and submit 2 changes
    PushOneCommit.Result change1 = createChange();
    PushOneCommit.Result change2 = createChange();
    approve(change1.getChangeId());
    if (getSubmitType() == SubmitType.CHERRY_PICK) {
      submit(change1.getChangeId());
    }
    submit(change2.getChangeId());
    assertMerged(change1.getChangeId());
    RevCommit headAfterSubmit = getRemoteHead();

    // set the status of the changes back to NEW to simulate a failed submit that
    // merged the commits but failed to update the change status
    setChangeStatusToNew(change1, change2);

    // submitting the changes again should detect that the commits were already
    // merged and just fix the change status to be MERGED
    submit(change1.getChangeId());
    submit(change2.getChangeId());
    assertThat(getRemoteHead()).isEqualTo(headAfterSubmit);
  }

  @Test
  public void submitTopicWithCommitsThatWereAlreadyMerged() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();

    // create and submit 2 changes with the same topic
    String topic = name("topic");
    PushOneCommit.Result change1 = createChange("refs/for/master/" + topic);
    PushOneCommit.Result change2 = createChange("refs/for/master/" + topic);
    approve(change1.getChangeId());
    submit(change2.getChangeId());
    assertMerged(change1.getChangeId());
    RevCommit headAfterSubmit = getRemoteHead();

    // set the status of the second change back to NEW to simulate a failed
    // submit that merged the commits but failed to update the change status of
    // some changes in the topic
    setChangeStatusToNew(change2);

    // submitting the topic again should detect that the commits were already
    // merged and just fix the change status to be MERGED
    submit(change2.getChangeId());
    assertThat(getRemoteHead()).isEqualTo(headAfterSubmit);
  }

  @Test
  public void submitWithValidation() throws Exception {
    AtomicBoolean called = new AtomicBoolean(false);
    this.addOnSubmitValidationListener(
        new OnSubmitValidationListener() {
          @Override
          public void preBranchUpdate(Arguments args) throws ValidationException {
            called.set(true);
            HashSet<String> refs = Sets.newHashSet(args.getCommands().keySet());
            assertThat(refs).contains("refs/heads/master");
            refs.remove("refs/heads/master");
            if (!refs.isEmpty()) {
              // Some submit strategies need to insert new patchset.
              assertThat(refs).hasSize(1);
              assertThat(refs.iterator().next()).startsWith(RefNames.REFS_CHANGES);
            }
          }
        });

    PushOneCommit.Result change = createChange();
    approve(change.getChangeId());
    submit(change.getChangeId());
    assertThat(called.get()).isTrue();
  }

  @Test
  public void submitWithValidationMultiRepo() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    String topic = "test-topic";

    // Create test projects
    TestRepository<?> repoA = createProjectWithPush("project-a", null, getSubmitType());
    TestRepository<?> repoB = createProjectWithPush("project-b", null, getSubmitType());

    // Create changes on project-a
    PushOneCommit.Result change1 =
        createChange(repoA, "master", "Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 =
        createChange(repoA, "master", "Change 2", "b.txt", "content", topic);

    // Create changes on project-b
    PushOneCommit.Result change3 =
        createChange(repoB, "master", "Change 3", "a.txt", "content", topic);
    PushOneCommit.Result change4 =
        createChange(repoB, "master", "Change 4", "b.txt", "content", topic);

    List<PushOneCommit.Result> changes = Lists.newArrayList(change1, change2, change3, change4);
    for (PushOneCommit.Result change : changes) {
      approve(change.getChangeId());
    }

    // Construct validator which will throw on a second call.
    // Since there are 2 repos, first submit attempt will fail, the second will
    // succeed.
    List<String> projectsCalled = new ArrayList<>(4);
    this.addOnSubmitValidationListener(
        new OnSubmitValidationListener() {
          @Override
          public void preBranchUpdate(Arguments args) throws ValidationException {
            String master = "refs/heads/master";
            assertThat(args.getCommands()).containsKey(master);
            ReceiveCommand cmd = args.getCommands().get(master);
            ObjectId newMasterId = cmd.getNewId();
            try (Repository repo = repoManager.openRepository(args.getProject())) {
              assertThat(repo.exactRef(master).getObjectId()).isEqualTo(cmd.getOldId());
              assertThat(args.getRef(master)).hasValue(newMasterId);
              args.getRevWalk().parseBody(args.getRevWalk().parseCommit(newMasterId));
            } catch (IOException e) {
              throw new AssertionError("failed checking new ref value", e);
            }
            projectsCalled.add(args.getProject().get());
            if (projectsCalled.size() == 2) {
              throw new ValidationException("time to fail");
            }
          }
        });
    submitWithConflict(change4.getChangeId(), "time to fail");
    assertThat(projectsCalled).containsExactly(name("project-a"), name("project-b"));
    for (PushOneCommit.Result change : changes) {
      change.assertChange(Change.Status.NEW, name(topic), admin);
    }

    submit(change4.getChangeId());
    assertThat(projectsCalled)
        .containsExactly(
            name("project-a"), name("project-b"), name("project-a"), name("project-b"));
    for (PushOneCommit.Result change : changes) {
      change.assertChange(Change.Status.MERGED, name(topic), admin);
    }
  }

  @Test
  public void submitWithCommitAndItsMergeCommitTogether() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();

    RevCommit initialHead = getRemoteHead();

    // Create a stable branch and bootstrap it.
    gApi.projects().name(project.get()).branch("stable").create(new BranchInput());
    PushOneCommit push =
        pushFactory.create(db, user.getIdent(), testRepo, "initial commit", "a.txt", "a");
    PushOneCommit.Result change = push.to("refs/heads/stable");

    RevCommit stable = getRemoteHead(project, "stable");
    RevCommit master = getRemoteHead(project, "master");

    assertThat(master).isEqualTo(initialHead);
    assertThat(stable).isEqualTo(change.getCommit());

    testRepo.git().fetch().call();
    testRepo.git().branchCreate().setName("stable").setStartPoint(stable).call();
    testRepo.git().branchCreate().setName("master").setStartPoint(master).call();

    // Create a fix in stable branch.
    testRepo.reset(stable);
    RevCommit fix =
        testRepo
            .commit()
            .parent(stable)
            .message("small fix")
            .add("b.txt", "b")
            .insertChangeId()
            .create();
    testRepo.branch("refs/heads/stable").update(fix);
    testRepo
        .git()
        .push()
        .setRefSpecs(new RefSpec("refs/heads/stable:refs/for/stable/" + name("topic")))
        .call();

    // Merge the fix into master.
    testRepo.reset(master);
    RevCommit merge =
        testRepo
            .commit()
            .parent(master)
            .parent(fix)
            .message("Merge stable into master")
            .insertChangeId()
            .create();
    testRepo.branch("refs/heads/master").update(merge);
    testRepo
        .git()
        .push()
        .setRefSpecs(new RefSpec("refs/heads/master:refs/for/master/" + name("topic")))
        .call();

    // Submit together.
    String fixId = GitUtil.getChangeId(testRepo, fix).get();
    String mergeId = GitUtil.getChangeId(testRepo, merge).get();
    approve(fixId);
    approve(mergeId);
    submit(mergeId);
    assertMerged(fixId);
    assertMerged(mergeId);
    testRepo.git().fetch().call();
    RevWalk rw = testRepo.getRevWalk();
    master = rw.parseCommit(getRemoteHead(project, "master"));
    assertThat(rw.isMergedInto(merge, master)).isTrue();
    assertThat(rw.isMergedInto(fix, master)).isTrue();
  }

  @Test
  public void retrySubmitSingleChangeOnLockFailure() throws Exception {
    assume().that(notesMigration.fuseUpdates()).isTrue();

    PushOneCommit.Result change = createChange();
    String id = change.getChangeId();
    approve(id);

    TestSubmitInput input = new TestSubmitInput();
    input.generateLockFailures =
        new ArrayDeque<>(
            ImmutableList.of(
                true, // Attempt 1: lock failure
                false, // Attempt 2: success
                false)); // Leftover value to check total number of calls.
    submit(id, input);
    assertMerged(id);

    testRepo.git().fetch().call();
    RevWalk rw = testRepo.getRevWalk();
    RevCommit master = rw.parseCommit(getRemoteHead(project, "master"));
    RevCommit patchSet = parseCurrentRevision(rw, change);
    assertThat(rw.isMergedInto(patchSet, master)).isTrue();

    assertThat(input.generateLockFailures).containsExactly(false);
  }

  @Test
  public void retrySubmitTornTopicOnLockFailure() throws Exception {
    assume().that(notesMigration.fuseUpdates()).isTrue();
    assume().that(isSubmitWholeTopicEnabled()).isTrue();

    String topic = "test-topic";

    TestRepository<?> repoA = createProjectWithPush("project-a", null, getSubmitType());
    TestRepository<?> repoB = createProjectWithPush("project-b", null, getSubmitType());

    PushOneCommit.Result change1 =
        createChange(repoA, "master", "Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 =
        createChange(repoB, "master", "Change 2", "b.txt", "content", topic);

    approve(change1.getChangeId());
    approve(change2.getChangeId());

    TestSubmitInput input = new TestSubmitInput();
    input.generateLockFailures =
        new ArrayDeque<>(
            ImmutableList.of(
                false, // Change 1, attempt 1: success
                true, // Change 2, attempt 1: lock failure
                false, // Change 1, attempt 2: success
                false, // Change 2, attempt 2: success
                false)); // Leftover value to check total number of calls.
    submit(change2.getChangeId(), input);

    String expectedTopic = name(topic);
    change1.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change2.assertChange(Change.Status.MERGED, expectedTopic, admin);

    repoA.git().fetch().call();
    RevWalk rwA = repoA.getRevWalk();
    RevCommit masterA = rwA.parseCommit(getRemoteHead(name("project-a"), "master"));
    RevCommit change1Ps = parseCurrentRevision(rwA, change1);
    assertThat(rwA.isMergedInto(change1Ps, masterA)).isTrue();

    repoB.git().fetch().call();
    RevWalk rwB = repoB.getRevWalk();
    RevCommit masterB = rwB.parseCommit(getRemoteHead(name("project-b"), "master"));
    RevCommit change2Ps = parseCurrentRevision(rwB, change2);
    assertThat(rwB.isMergedInto(change2Ps, masterB)).isTrue();

    assertThat(input.generateLockFailures).containsExactly(false);
  }

  private RevCommit parseCurrentRevision(RevWalk rw, PushOneCommit.Result r) throws Exception {
    return rw.parseCommit(
        ObjectId.fromString(
            get(r.getChangeId(), ListChangesOption.CURRENT_REVISION).currentRevision));
  }

  private void setChangeStatusToNew(PushOneCommit.Result... changes) throws Exception {
    for (PushOneCommit.Result change : changes) {
      try (BatchUpdate bu =
          batchUpdateFactory.create(db, project, userFactory.create(admin.id), TimeUtil.nowTs())) {
        bu.addOp(
            change.getChange().getId(),
            new BatchUpdateOp() {
              @Override
              public boolean updateChange(ChangeContext ctx) throws OrmException {
                ctx.getChange().setStatus(Change.Status.NEW);
                ctx.getUpdate(ctx.getChange().currentPatchSetId()).setStatus(Change.Status.NEW);
                return true;
              }
            });
        bu.execute();
      }
    }
  }

  private void assertSubmitter(PushOneCommit.Result change) throws Exception {
    ChangeInfo info = get(change.getChangeId(), ListChangesOption.MESSAGES);
    assertThat(info.messages).isNotNull();
    Iterable<String> messages = Iterables.transform(info.messages, i -> i.message);
    assertThat(messages).hasSize(3);
    String last = Iterables.getLast(messages);
    if (getSubmitType() == SubmitType.CHERRY_PICK) {
      assertThat(last).startsWith("Change has been successfully cherry-picked as ");
    } else if (getSubmitType() == SubmitType.REBASE_ALWAYS) {
      assertThat(last).startsWith("Change has been successfully rebased and submitted as");
    } else {
      assertThat(last).isEqualTo("Change has been successfully merged by Administrator");
    }
  }

  @Override
  protected void updateProjectInput(ProjectInput in) {
    in.submitType = getSubmitType();
    if (in.useContentMerge == InheritableBoolean.INHERIT) {
      in.useContentMerge = InheritableBoolean.FALSE;
    }
  }

  protected void submit(String changeId) throws Exception {
    submit(changeId, new SubmitInput(), null, null);
  }

  protected void submit(String changeId, SubmitInput input) throws Exception {
    submit(changeId, input, null, null);
  }

  protected void submitWithConflict(String changeId, String expectedError) throws Exception {
    submit(changeId, new SubmitInput(), ResourceConflictException.class, expectedError);
  }

  protected void submit(
      String changeId,
      SubmitInput input,
      Class<? extends RestApiException> expectedExceptionType,
      String expectedExceptionMsg)
      throws Exception {
    approve(changeId);
    if (expectedExceptionType == null) {
      assertSubmittable(changeId);
    }
    try {
      gApi.changes().id(changeId).current().submit(input);
      if (expectedExceptionType != null) {
        fail("Expected exception of type " + expectedExceptionType.getSimpleName());
      }
    } catch (RestApiException e) {
      if (expectedExceptionType == null) {
        throw e;
      }
      // More verbose than using assertThat and/or ExpectedException, but gives
      // us the stack trace.
      if (!expectedExceptionType.isAssignableFrom(e.getClass())
          || !e.getMessage().equals(expectedExceptionMsg)) {
        throw new AssertionError(
            "Expected exception of type "
                + expectedExceptionType.getSimpleName()
                + " with message: \""
                + expectedExceptionMsg
                + "\" but got exception of type "
                + e.getClass().getSimpleName()
                + " with message \""
                + e.getMessage()
                + "\"",
            e);
      }
      return;
    }
    ChangeInfo change = gApi.changes().id(changeId).info();
    assertMerged(change.changeId);
  }

  protected void assertSubmittable(String changeId) throws Exception {
    assertThat(get(changeId, SUBMITTABLE).submittable)
        .named("submit bit on ChangeInfo")
        .isEqualTo(true);
    RevisionResource rsrc = parseCurrentRevisionResource(changeId);
    UiAction.Description desc = submitHandler.getDescription(rsrc);
    assertThat(desc.isVisible()).named("visible bit on submit action").isTrue();
    assertThat(desc.isEnabled()).named("enabled bit on submit action").isTrue();
  }

  protected void assertChangeMergedEvents(String... expected) throws Exception {
    eventRecorder.assertChangeMergedEvents(project.get(), "refs/heads/master", expected);
  }

  protected void assertRefUpdatedEvents(RevCommit... expected) throws Exception {
    eventRecorder.assertRefUpdatedEvents(project.get(), "refs/heads/master", expected);
  }

  protected void assertCurrentRevision(String changeId, int expectedNum, ObjectId expectedId)
      throws Exception {
    ChangeInfo c = get(changeId, CURRENT_REVISION);
    assertThat(c.currentRevision).isEqualTo(expectedId.name());
    assertThat(c.revisions.get(expectedId.name())._number).isEqualTo(expectedNum);
    try (Repository repo = repoManager.openRepository(new Project.NameKey(c.project))) {
      String refName = new PatchSet.Id(new Change.Id(c._number), expectedNum).toRefName();
      Ref ref = repo.exactRef(refName);
      assertThat(ref).named(refName).isNotNull();
      assertThat(ref.getObjectId()).isEqualTo(expectedId);
    }
  }

  protected void assertNew(String changeId) throws Exception {
    assertThat(get(changeId).status).isEqualTo(ChangeStatus.NEW);
  }

  protected void assertApproved(String changeId) throws Exception {
    assertApproved(changeId, admin);
  }

  protected void assertApproved(String changeId, TestAccount user) throws Exception {
    ChangeInfo c = get(changeId, DETAILED_LABELS);
    LabelInfo cr = c.labels.get("Code-Review");
    assertThat(cr.all).hasSize(1);
    assertThat(cr.all.get(0).value).isEqualTo(2);
    assertThat(new Account.Id(cr.all.get(0)._accountId)).isEqualTo(user.getId());
  }

  protected void assertMerged(String changeId) throws RestApiException {
    ChangeStatus status = gApi.changes().id(changeId).info().status;
    assertThat(status).isEqualTo(ChangeStatus.MERGED);
  }

  protected void assertPersonEquals(PersonIdent expected, PersonIdent actual) {
    assertThat(actual.getEmailAddress()).isEqualTo(expected.getEmailAddress());
    assertThat(actual.getName()).isEqualTo(expected.getName());
    assertThat(actual.getTimeZone()).isEqualTo(expected.getTimeZone());
  }

  protected void assertSubmitter(String changeId, int psId) throws Exception {
    assertSubmitter(changeId, psId, admin);
  }

  protected void assertSubmitter(String changeId, int psId, TestAccount user) throws Exception {
    Change c = getOnlyElement(queryProvider.get().byKeyPrefix(changeId)).change();
    ChangeNotes cn = notesFactory.createChecked(db, c);
    PatchSetApproval submitter =
        approvalsUtil.getSubmitter(db, cn, new PatchSet.Id(cn.getChangeId(), psId));
    assertThat(submitter).isNotNull();
    assertThat(submitter.isLegacySubmit()).isTrue();
    assertThat(submitter.getAccountId()).isEqualTo(user.getId());
  }

  protected void assertNoSubmitter(String changeId, int psId) throws Exception {
    Change c = getOnlyElement(queryProvider.get().byKeyPrefix(changeId)).change();
    ChangeNotes cn = notesFactory.createChecked(db, c);
    PatchSetApproval submitter =
        approvalsUtil.getSubmitter(db, cn, new PatchSet.Id(cn.getChangeId(), psId));
    assertThat(submitter).isNull();
  }

  protected void assertCherryPick(TestRepository<?> testRepo, boolean contentMerge)
      throws Exception {
    assertRebase(testRepo, contentMerge);
    RevCommit remoteHead = getRemoteHead();
    assertThat(remoteHead.getFooterLines("Reviewed-On")).isNotEmpty();
    assertThat(remoteHead.getFooterLines("Reviewed-By")).isNotEmpty();
  }

  protected void assertRebase(TestRepository<?> testRepo, boolean contentMerge) throws Exception {
    Repository repo = testRepo.getRepository();
    RevCommit localHead = getHead(repo);
    RevCommit remoteHead = getRemoteHead();
    assert_()
        .withFailureMessage(String.format("%s not equal %s", localHead.name(), remoteHead.name()))
        .that(localHead.getId())
        .isNotEqualTo(remoteHead.getId());
    assertThat(remoteHead.getParentCount()).isEqualTo(1);
    if (!contentMerge) {
      assertThat(getLatestRemoteDiff()).isEqualTo(getLatestDiff(repo));
    }
    assertThat(remoteHead.getShortMessage()).isEqualTo(localHead.getShortMessage());
  }

  protected List<RevCommit> getRemoteLog(Project.NameKey project, String branch) throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      rw.markStart(rw.parseCommit(repo.exactRef("refs/heads/" + branch).getObjectId()));
      return Lists.newArrayList(rw);
    }
  }

  protected List<RevCommit> getRemoteLog() throws Exception {
    return getRemoteLog(project, "master");
  }

  protected void addOnSubmitValidationListener(OnSubmitValidationListener listener) {
    assertThat(onSubmitValidatorHandle).isNull();
    onSubmitValidatorHandle = onSubmitValidationListeners.add(listener);
  }

  private String getLatestDiff(Repository repo) throws Exception {
    ObjectId oldTreeId = repo.resolve("HEAD~1^{tree}");
    ObjectId newTreeId = repo.resolve("HEAD^{tree}");
    return getLatestDiff(repo, oldTreeId, newTreeId);
  }

  private String getLatestRemoteDiff() throws Exception {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      ObjectId oldTreeId = repo.resolve("refs/heads/master~1^{tree}");
      ObjectId newTreeId = repo.resolve("refs/heads/master^{tree}");
      return getLatestDiff(repo, oldTreeId, newTreeId);
    }
  }

  private String getLatestDiff(Repository repo, ObjectId oldTreeId, ObjectId newTreeId)
      throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (DiffFormatter fmt = new DiffFormatter(out)) {
      fmt.setRepository(repo);
      fmt.format(oldTreeId, newTreeId);
      fmt.flush();
      return out.toString();
    }
  }
}
