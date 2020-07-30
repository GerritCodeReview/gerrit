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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allowLabel;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.extensions.client.ListChangesOption.CURRENT_REVISION;
import static com.google.gerrit.extensions.client.ListChangesOption.DETAILED_LABELS;
import static com.google.gerrit.extensions.client.ListChangesOption.SUBMITTABLE;
import static com.google.gerrit.server.group.SystemGroupBackend.CHANGE_OWNER;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.EMPTY_TREE_ID;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.UseTimezone;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.acceptance.testsuite.request.RequestScopeOperations;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.AttentionSetUpdate;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.AttentionSetInput;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.LabelInfo;
import com.google.gerrit.extensions.events.ChangeIndexedListener;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.TestSubmitInput;
import com.google.gerrit.server.git.validators.OnSubmitValidationListener;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.restapi.change.Submit;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gerrit.testing.ConfigSuite;
import com.google.gerrit.testing.GerritJUnit.ThrowingRunnable;
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
import java.util.stream.Collectors;
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
import org.junit.Test;

@NoHttpd
@UseClockStep
@UseTimezone(timezone = "US/Eastern")
public abstract class AbstractSubmit extends AbstractDaemonTest {
  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Inject private ApprovalsUtil approvalsUtil;
  @Inject private IdentifiedUser.GenericFactory userFactory;
  @Inject private ProjectOperations projectOperations;
  @Inject private RequestScopeOperations requestScopeOperations;
  @Inject private Submit submitHandler;
  @Inject private ExtensionRegistry extensionRegistry;

  protected abstract SubmitType getSubmitType();

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void submitToEmptyRepo() throws Throwable {
    assertThat(projectOperations.project(project).hasHead("master")).isFalse();
    PushOneCommit.Result change = createChange();
    assertThat(change.getCommit().getParents()).isEmpty();
    Map<BranchNameKey, ObjectId> actual = fetchFromSubmitPreview(change.getChangeId());
    assertThat(projectOperations.project(project).hasHead("master")).isFalse();
    assertThat(actual).hasSize(1);

    submit(change.getChangeId());
    assertThat(projectOperations.project(project).getHead("master").getId())
        .isEqualTo(change.getCommit());
    assertTrees(project, actual);
  }

  @Test
  public void submitSingleChange() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange();
    Map<BranchNameKey, ObjectId> actual = fetchFromSubmitPreview(change.getChangeId());
    RevCommit headAfterSubmit = projectOperations.project(project).getHead("master");
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
  public void submitMultipleChangesOtherMergeConflictPreview() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit headAfterFirstSubmit = projectOperations.project(project).getHead("master");
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "other content");
    PushOneCommit.Result change3 = createChange("Change 3", "d", "d");
    PushOneCommit.Result change4 = createChange("Change 4", "e", "e");
    // change 2 is not approved, but we ignore labels
    approve(change3.getChangeId());

    try (BinaryResult request =
        gApi.changes().id(change4.getChangeId()).current().submitPreview()) {
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
                      + ": Project policy "
                      + "requires all submissions to be a fast-forward. Please "
                      + "rebase the change locally and upload again for review.\n"
                      + "Change "
                      + change3.getChange().getId()
                      + ": Project policy "
                      + "requires all submissions to be a fast-forward. Please "
                      + "rebase the change locally and upload again for review.\n"
                      + "Change "
                      + change4.getChange().getId()
                      + ": Project policy "
                      + "requires all submissions to be a fast-forward. Please "
                      + "rebase the change locally and upload again for review.");
          break;
        case REBASE_IF_NECESSARY:
        case REBASE_ALWAYS:
          String change2hash = change2.getChange().currentPatchSet().commitId().name();
          assertThat(e.getMessage())
              .isEqualTo(
                  "Cannot rebase "
                      + change2hash
                      + ": The change could "
                      + "not be rebased due to a conflict during merge.");
          break;
        case MERGE_ALWAYS:
        case MERGE_IF_NECESSARY:
        case INHERIT:
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
          assertWithMessage("Should not reach here.").fail();
          break;
      }

      RevCommit headAfterSubmit = projectOperations.project(project).getHead("master");
      assertThat(headAfterSubmit).isEqualTo(headAfterFirstSubmit);
      assertRefUpdatedEvents(initialHead, headAfterFirstSubmit);
      assertChangeMergedEvents(change.getChangeId(), headAfterFirstSubmit.name());
    }
  }

  @Test
  public void submitMultipleChangesPreview() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "other content");
    PushOneCommit.Result change3 = createChange("Change 3", "d", "d");
    PushOneCommit.Result change4 = createChange("Change 4", "e", "e");
    // change 2 is not approved, but we ignore labels
    approve(change3.getChangeId());
    Map<BranchNameKey, ObjectId> actual = fetchFromSubmitPreview(change4.getChangeId());
    Map<String, Map<String, Integer>> expected = new HashMap<>();
    expected.put(project.get(), new HashMap<>());
    expected.get(project.get()).put("refs/heads/master", 3);

    assertThat(actual).containsKey(BranchNameKey.create(project, "refs/heads/master"));
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
    RevCommit headAfterSubmit = projectOperations.project(project).getHead("master");
    assertThat(headAfterSubmit).isEqualTo(initialHead);
    assertRefUpdatedEvents();
    assertChangeMergedEvents();

    // now check we actually have the same content:
    approve(change2.getChangeId());
    submit(change4.getChangeId());
    assertTrees(project, actual);
  }

  /**
   * Tests the following situation:
   *
   * <ul>
   *   <li>1. create a change series, consisting out of a merge commit and a normal commit
   *   <li>2. before submitting the change series, another non-conflicting change gets submitted
   *   <li>3. when the change series gets submitted, Gerrit must perform a merge/rebase/cherry-pick
   * </ul>
   */
  @Test
  public void submitChangeSeriesWithMergeCommitThatIsBasedOnOldTip() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    // create a commit which will become the first parent of a merge commit
    PushOneCommit.Result parent1 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "parent 2",
                ImmutableMap.of("foo", "foo-2", "bar", "bar-2"))
            .to("refs/heads/master");

    // reset the testRepo in order to create a sibling of parent1
    testRepo.reset(initialHead);

    // create a stable branch that we can merge back into master later
    BranchInput in = new BranchInput();
    in.revision = initialHead.getName();
    gApi.projects().name(project.get()).branch("refs/heads/stable").create(in);

    // create one commit in the stable branch, which will become the second parent of the merge
    // commit
    PushOneCommit.Result parent2 =
        pushFactory
            .create(
                admin.newIdent(),
                testRepo,
                "parent 1",
                ImmutableMap.of("foo", "foo-1", "bar", "bar-1"))
            .to("refs/heads/stable");

    // create a merge change that merges the stable branch back into master
    testRepo.reset(parent1.getCommit());
    PushOneCommit m =
        pushFactory.create(
            admin.newIdent(), testRepo, "merge", ImmutableMap.of("foo", "foo-1", "bar", "bar-2"));
    m.setParents(ImmutableList.of(parent1.getCommit(), parent2.getCommit()));
    PushOneCommit.Result mergeChange = m.to("refs/for/master");
    mergeChange.assertOkStatus();

    // approve the merge change so that it becomes submittable
    approve(mergeChange.getChangeId());

    // create a successor change that depends on the merge change
    PushOneCommit.Result successorChange = createChange("refs/for/master");

    // simulate another developer submitting a change in the meantime (non-conflicting sibling
    // commit of the merge commit), this means when the change series gets submitted Gerrit must
    // perform a merge/rebase/cherry-pick now
    testRepo.reset(parent1.getCommit());
    submit(createChange("Other Change", "x.txt", "x content").getChangeId());

    // submit the change series
    if (getSubmitType() != SubmitType.FAST_FORWARD_ONLY) {
      submit(successorChange.getChangeId());
    } else {
      submitWithConflict(
          successorChange.getChangeId(),
          "Failed to submit 2 changes due to the following problems:\n"
              + "Change "
              + mergeChange.getChange().getId()
              + ": Project policy "
              + "requires all submissions to be a fast-forward. Please "
              + "rebase the change locally and upload again for review.\n"
              + "Change "
              + successorChange.getChange().getId()
              + ": Project policy "
              + "requires all submissions to be a fast-forward. Please "
              + "rebase the change locally and upload again for review.");
    }
  }

  @Test
  public void submitNoPermission() throws Throwable {
    // create project where submit is blocked
    Project.NameKey p = projectOperations.newProject().create();
    projectOperations
        .project(p)
        .forUpdate()
        .add(block(Permission.SUBMIT).ref("refs/*").group(REGISTERED_USERS))
        .update();

    TestRepository<InMemoryRepository> repo = cloneProject(p, admin);
    PushOneCommit push = pushFactory.create(admin.newIdent(), repo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();

    submit(result.getChangeId(), new SubmitInput(), AuthException.class, "submit not permitted");
  }

  @Test
  public void noSelfSubmit() throws Throwable {
    // create project where submit is blocked for the change owner
    Project.NameKey p = projectOperations.newProject().create();
    projectOperations
        .project(p)
        .forUpdate()
        .add(block(Permission.SUBMIT).ref("refs/*").group(CHANGE_OWNER))
        .add(allow(Permission.SUBMIT).ref("refs/heads/*").group(REGISTERED_USERS))
        .add(allowLabel("Code-Review").ref("refs/*").group(REGISTERED_USERS).range(-2, +2))
        .update();

    TestRepository<InMemoryRepository> repo = cloneProject(p, admin);
    PushOneCommit push = pushFactory.create(admin.newIdent(), repo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();

    ChangeInfo change = gApi.changes().id(result.getChangeId()).get();
    assertThat(change.owner._accountId).isEqualTo(admin.id().get());

    submit(result.getChangeId(), new SubmitInput(), AuthException.class, "submit not permitted");

    requestScopeOperations.setApiUser(user.id());
    submit(result.getChangeId());
  }

  @Test
  public void onlySelfSubmit() throws Throwable {
    // create project where only the change owner can submit
    Project.NameKey p = projectOperations.newProject().create();
    projectOperations
        .project(p)
        .forUpdate()
        .add(block(Permission.SUBMIT).ref("refs/*").group(REGISTERED_USERS))
        .add(allow(Permission.SUBMIT).ref("refs/*").group(CHANGE_OWNER))
        .add(allowLabel("Code-Review").ref("refs/*").group(REGISTERED_USERS).range(-2, +2))
        .update();

    TestRepository<InMemoryRepository> repo = cloneProject(p, admin);
    PushOneCommit push = pushFactory.create(admin.newIdent(), repo);
    PushOneCommit.Result result = push.to("refs/for/master");
    result.assertOkStatus();

    ChangeInfo change = gApi.changes().id(result.getChangeId()).get();
    assertThat(change.owner._accountId).isEqualTo(admin.id().get());

    requestScopeOperations.setApiUser(user.id());
    submit(result.getChangeId(), new SubmitInput(), AuthException.class, "submit not permitted");

    requestScopeOperations.setApiUser(admin.id());
    submit(result.getChangeId());
  }

  @Test
  public void submitWholeTopicMultipleProjects() throws Throwable {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    String topic = "test-topic";

    // Create test projects
    Project.NameKey keyA = createProjectForPush(getSubmitType());
    TestRepository<?> repoA = cloneProject(keyA);
    Project.NameKey keyB = createProjectForPush(getSubmitType());
    TestRepository<?> repoB = cloneProject(keyB);

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
  public void submitWholeTopicMultipleBranchesOnSameProject() throws Throwable {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    String topic = "test-topic";

    // Create test project
    Project.NameKey keyA = createProjectForPush(getSubmitType());
    TestRepository<?> repoA = cloneProject(keyA);

    RevCommit initialHead = projectOperations.project(keyA).getHead("master");

    // Create the dev branch on the test project
    BranchInput in = new BranchInput();
    in.revision = initialHead.name();
    gApi.projects().name(keyA.get()).branch("dev").create(in);

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
  public void submitWholeTopic() throws Throwable {
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
    List<String> commitsInRepo = log.stream().map(RevCommit::getShortMessage).collect(toList());
    int expectedCommitCount =
        getSubmitType() == SubmitType.MERGE_ALWAYS
            ? 5 // initial commit + 3 commits + merge commit
            : 4; // initial commit + 3 commits
    assertThat(log).hasSize(expectedCommitCount);

    assertThat(commitsInRepo)
        .containsAtLeast("Initial empty repository", "Change 1", "Change 2", "Change 3");
    if (getSubmitType() == SubmitType.MERGE_ALWAYS) {
      assertThat(commitsInRepo).contains("Merge changes from topic \"" + expectedTopic + "\"");
    }
  }

  @Test
  public void submitWholeTopicWithMultipleTopics() throws Throwable {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    String topic1 = "test-topic-1";
    String topic2 = "test-topic-2";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic1);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "content", topic1);
    PushOneCommit.Result change3 = createChange("Change 3", "c.txt", "content", topic2);
    PushOneCommit.Result change4 = createChange("Change 4", "d.txt", "content", topic2);
    approve(change1.getChangeId());
    approve(change2.getChangeId());
    approve(change3.getChangeId());
    approve(change4.getChangeId());
    submit(change4.getChangeId());
    String expectedTopic1 = name(topic1);
    String expectedTopic2 = name(topic2);
    if (getSubmitType() == SubmitType.CHERRY_PICK) {
      change1.assertChange(Change.Status.NEW, expectedTopic1, admin);
      change2.assertChange(Change.Status.NEW, expectedTopic1, admin);

    } else {
      change1.assertChange(Change.Status.MERGED, expectedTopic1, admin);
      change2.assertChange(Change.Status.MERGED, expectedTopic1, admin);
    }

    // Check for the exact change to have the correct submitter.
    assertSubmitter(change4);
    // Also check submitters for changes submitted via the topic relationship.
    assertSubmitter(change3);
    if (getSubmitType() != SubmitType.CHERRY_PICK) {
      assertSubmitter(change1);
      assertSubmitter(change2);
    }

    // Check that the repo has the expected commits
    List<RevCommit> log = getRemoteLog();
    List<String> commitsInRepo = log.stream().map(RevCommit::getShortMessage).collect(toList());
    int expectedCommitCount;
    switch (getSubmitType()) {
      case MERGE_ALWAYS:
        // initial commit + 4 commits + merge commit
        expectedCommitCount = 6;
        break;
      case CHERRY_PICK:
        // initial commit + 2 commits
        expectedCommitCount = 3;
        break;
      case FAST_FORWARD_ONLY:
      case INHERIT:
      case MERGE_IF_NECESSARY:
      case REBASE_ALWAYS:
      case REBASE_IF_NECESSARY:
      default:
        // initial commit + 4 commits
        expectedCommitCount = 5;
        break;
    }
    assertThat(log).hasSize(expectedCommitCount);

    if (getSubmitType() == SubmitType.CHERRY_PICK) {
      assertThat(commitsInRepo).containsAtLeast("Initial empty repository", "Change 3", "Change 4");
      assertThat(commitsInRepo).doesNotContain("Change 1");
      assertThat(commitsInRepo).doesNotContain("Change 2");
    } else if (getSubmitType() == SubmitType.MERGE_ALWAYS) {
      assertThat(commitsInRepo)
          .contains(
              String.format(
                  "Merge changes from topics \"%s\", \"%s\"", expectedTopic1, expectedTopic2));
    } else {
      assertThat(commitsInRepo)
          .containsAtLeast(
              "Initial empty repository", "Change 1", "Change 2", "Change 3", "Change 4");
    }
  }

  @Test
  public void submitReusingOldTopic() throws Throwable {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();

    String topic = "test-topic";
    PushOneCommit.Result change1 = createChange("Change 1", "a.txt", "content", topic);
    PushOneCommit.Result change2 = createChange("Change 2", "a.txt", "content", topic);
    String id1 = change1.getChangeId();
    String id2 = change2.getChangeId();
    approve(id1);
    approve(id2);
    assertSubmittedTogether(id1, ImmutableList.of(id1, id2));
    assertSubmittedTogether(id2, ImmutableList.of(id1, id2));
    submit(id2);

    String expectedTopic = name(topic);
    change1.assertChange(Change.Status.MERGED, expectedTopic, admin);
    change2.assertChange(Change.Status.MERGED, expectedTopic, admin);
    assertSubmittedTogether(id1, ImmutableList.of(id1, id2));
    assertSubmittedTogether(id2, ImmutableList.of(id1, id2));

    PushOneCommit.Result change3 = createChange("Change 3", "c.txt", "content", topic);
    String id3 = change3.getChangeId();
    approve(id3);
    assertSubmittedTogether(id3, ImmutableList.of());
    submit(id3);

    change3.assertChange(Change.Status.MERGED, expectedTopic, admin);
    assertSubmittedTogether(id3, ImmutableList.of());
  }

  private void assertSubmittedTogether(String changeId, Iterable<String> expected)
      throws Throwable {
    assertThat(gApi.changes().id(changeId).submittedTogether().stream().map(i -> i.changeId))
        .containsExactlyElementsIn(expected);
  }

  @Test
  public void submitWorkInProgressChange() throws Throwable {
    PushOneCommit.Result change = pushTo("refs/for/master%wip");
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
  public void submitWithHiddenBranchInSameTopic() throws Throwable {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    PushOneCommit.Result visible = createChange("refs/for/master%topic=" + name("topic"));
    Change.Id num = visible.getChange().getId();

    createBranch(BranchNameKey.create(project, "hidden"));
    PushOneCommit.Result hidden = createChange("refs/for/hidden%topic=" + name("topic"));
    approve(hidden.getChangeId());
    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/heads/hidden").group(REGISTERED_USERS))
        .update();

    submit(
        visible.getChangeId(),
        new SubmitInput(),
        AuthException.class,
        "A change to be submitted with " + num + " is not visible");
  }

  @Test
  public void submitChangeWhenParentOfOtherBranchTip() throws Throwable {
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
    enableCreateNewChangeForAllNotInTarget();

    PushOneCommit push1 =
        pushFactory.create(admin.newIdent(), testRepo, PushOneCommit.SUBJECT, "a.txt", "content");
    PushOneCommit.Result c1 = push1.to("refs/heads/topic");
    c1.assertOkStatus();
    PushOneCommit push2 =
        pushFactory.create(
            admin.newIdent(), testRepo, PushOneCommit.SUBJECT, "b.txt", "anotherContent");
    PushOneCommit.Result c2 = push2.to("refs/heads/topic");
    c2.assertOkStatus();

    PushOneCommit.Result change1 = push1.to("refs/for/master");
    change1.assertOkStatus();

    approve(change1.getChangeId());
    submit(change1.getChangeId());
  }

  @Test
  public void submitMergeOfNonChangeBranchTip() throws Throwable {
    // Merge a branch with commits that have not been submitted as
    // changes.
    //
    // M  -- mergeCommit (pushed for review and submitted)
    // | \
    // |  S -- stable (pushed directly to refs/heads/stable)
    // | /
    // I   -- master
    //
    RevCommit master = projectOperations.project(project).getHead("master");
    PushOneCommit stableTip =
        pushFactory.create(admin.newIdent(), testRepo, "Tip of branch stable", "stable.txt", "");
    PushOneCommit.Result stable = stableTip.to("refs/heads/stable");
    PushOneCommit mergeCommit =
        pushFactory.create(admin.newIdent(), testRepo, "The merge commit", "merge.txt", "");
    mergeCommit.setParents(ImmutableList.of(master, stable.getCommit()));
    PushOneCommit.Result mergeReview = mergeCommit.to("refs/for/master");
    approve(mergeReview.getChangeId());
    submit(mergeReview.getChangeId());

    List<RevCommit> log = getRemoteLog();
    assertThat(log).contains(stable.getCommit());
    assertThat(log).contains(mergeReview.getCommit());
  }

  @Test
  public void submitMergeOfNonChangeBranchNonTip() throws Throwable {
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
    RevCommit initial = projectOperations.project(project).getHead("master");
    // push directly to stable to S1
    PushOneCommit.Result s1 =
        pushFactory
            .create(admin.newIdent(), testRepo, "new commit into stable", "stable1.txt", "")
            .to("refs/heads/stable");
    // move the stable tip ahead to S2
    pushFactory
        .create(admin.newIdent(), testRepo, "Tip of branch stable", "stable2.txt", "")
        .to("refs/heads/stable");

    testRepo.reset(initial);

    // move the master ahead
    PushOneCommit.Result m =
        pushFactory
            .create(admin.newIdent(), testRepo, "Move master ahead", "master.txt", "")
            .to("refs/heads/master");

    // create merge change
    PushOneCommit mc =
        pushFactory.create(admin.newIdent(), testRepo, "The merge commit", "merge.txt", "");
    mc.setParents(ImmutableList.of(m.getCommit(), s1.getCommit()));
    PushOneCommit.Result mergeReview = mc.to("refs/for/master");
    approve(mergeReview.getChangeId());
    submit(mergeReview.getChangeId());

    List<RevCommit> log = getRemoteLog();
    assertThat(log).contains(s1.getCommit());
    assertThat(log).contains(mergeReview.getCommit());
  }

  @Test
  public void submitChangeWithCommitThatWasAlreadyMerged() throws Throwable {
    // create and submit a change
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());
    RevCommit headAfterSubmit = projectOperations.project(project).getHead("master");

    // set the status of the change back to NEW to simulate a failed submit that
    // merged the commit but failed to update the change status
    setChangeStatusToNew(change);

    // submitting the change again should detect that the commit was already
    // merged and just fix the change status to be MERGED
    submit(change.getChangeId());
    assertThat(projectOperations.project(project).getHead("master")).isEqualTo(headAfterSubmit);
  }

  @Test
  public void submitChangesWithCommitsThatWereAlreadyMerged() throws Throwable {
    // create and submit 2 changes
    PushOneCommit.Result change1 = createChange();
    PushOneCommit.Result change2 = createChange();
    approve(change1.getChangeId());
    if (getSubmitType() == SubmitType.CHERRY_PICK) {
      submit(change1.getChangeId());
    }
    submit(change2.getChangeId());
    assertMerged(change1.getChangeId());
    RevCommit headAfterSubmit = projectOperations.project(project).getHead("master");

    // set the status of the changes back to NEW to simulate a failed submit that
    // merged the commits but failed to update the change status
    setChangeStatusToNew(change1, change2);

    // submitting the changes again should detect that the commits were already
    // merged and just fix the change status to be MERGED
    submit(change1.getChangeId());
    submit(change2.getChangeId());
    assertThat(projectOperations.project(project).getHead("master")).isEqualTo(headAfterSubmit);
  }

  @Test
  public void submitTopicWithCommitsThatWereAlreadyMerged() throws Throwable {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();

    // create and submit 2 changes with the same topic
    String topic = name("topic");
    PushOneCommit.Result change1 = createChange("refs/for/master%topic=" + topic);
    PushOneCommit.Result change2 = createChange("refs/for/master%topic=" + topic);
    approve(change1.getChangeId());
    submit(change2.getChangeId());
    assertMerged(change1.getChangeId());
    RevCommit headAfterSubmit = projectOperations.project(project).getHead("master");

    // set the status of the second change back to NEW to simulate a failed
    // submit that merged the commits but failed to update the change status of
    // some changes in the topic
    setChangeStatusToNew(change2);

    // submitting the topic again should detect that the commits were already
    // merged and just fix the change status to be MERGED
    submit(change2.getChangeId());
    assertThat(projectOperations.project(project).getHead("master")).isEqualTo(headAfterSubmit);
  }

  @Test
  public void submitWithValidation() throws Throwable {
    AtomicBoolean called = new AtomicBoolean(false);
    OnSubmitValidationListener listener =
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
        };

    try (Registration registration = extensionRegistry.newRegistration().add(listener)) {
      PushOneCommit.Result change = createChange();
      approve(change.getChangeId());
      submit(change.getChangeId());
      assertThat(called.get()).isTrue();
    }
  }

  @Test
  public void submitWithValidationMultiRepo() throws Throwable {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();
    String topic = "test-topic";

    // Create test projects
    Project.NameKey keyA = createProjectForPush(getSubmitType());
    TestRepository<?> repoA = cloneProject(keyA);
    Project.NameKey keyB = createProjectForPush(getSubmitType());
    TestRepository<?> repoB = cloneProject(keyB);

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
    OnSubmitValidationListener listener =
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
        };
    try (Registration registration = extensionRegistry.newRegistration().add(listener)) {
      submitWithConflict(change4.getChangeId(), "time to fail");
      assertThat(projectsCalled).containsExactly(keyA.get(), keyB.get());
      for (PushOneCommit.Result change : changes) {
        change.assertChange(Change.Status.NEW, name(topic), admin);
      }

      submit(change4.getChangeId());
      assertThat(projectsCalled).containsExactly(keyA.get(), keyB.get(), keyA.get(), keyB.get());
      for (PushOneCommit.Result change : changes) {
        change.assertChange(Change.Status.MERGED, name(topic), admin);
      }
    }
  }

  @Test
  public void submitWithCommitAndItsMergeCommitTogether() throws Throwable {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();

    RevCommit initialHead = projectOperations.project(project).getHead("master");

    // Create a stable branch and bootstrap it.
    gApi.projects().name(project.get()).branch("stable").create(new BranchInput());
    PushOneCommit push =
        pushFactory.create(user.newIdent(), testRepo, "initial commit", "a.txt", "a");
    PushOneCommit.Result change = push.to("refs/heads/stable");

    RevCommit stable = projectOperations.project(project).getHead("stable");
    RevCommit master = projectOperations.project(project).getHead("master");

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
        .setRefSpecs(new RefSpec("refs/heads/stable:refs/for/stable%topic=" + name("topic")))
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
        .setRefSpecs(new RefSpec("refs/heads/master:refs/for/master%topic=" + name("topic")))
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
    master = rw.parseCommit(projectOperations.project(project).getHead("master"));
    assertThat(rw.isMergedInto(merge, master)).isTrue();
    assertThat(rw.isMergedInto(fix, master)).isTrue();
  }

  @Test
  public void retrySubmitSingleChangeOnLockFailure() throws Throwable {
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
    RevCommit master = rw.parseCommit(projectOperations.project(project).getHead("master"));
    RevCommit patchSet = parseCurrentRevision(rw, change.getChangeId());
    assertThat(rw.isMergedInto(patchSet, master)).isTrue();

    assertThat(input.generateLockFailures).containsExactly(false);
  }

  @Test
  public void retrySubmitAfterTornTopicOnLockFailure() throws Throwable {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();

    String topic = "test-topic";

    Project.NameKey keyA = createProjectForPush(getSubmitType());
    Project.NameKey keyB = createProjectForPush(getSubmitType());
    TestRepository<?> repoA = cloneProject(keyA);
    TestRepository<?> repoB = cloneProject(keyB);

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
    RevCommit masterA = rwA.parseCommit(projectOperations.project(keyA).getHead("master"));
    RevCommit change1Ps = parseCurrentRevision(rwA, change1.getChangeId());
    assertThat(rwA.isMergedInto(change1Ps, masterA)).isTrue();

    repoB.git().fetch().call();
    RevWalk rwB = repoB.getRevWalk();
    RevCommit masterB = rwB.parseCommit(projectOperations.project(keyB).getHead("master"));
    RevCommit change2Ps = parseCurrentRevision(rwB, change2.getChangeId());
    assertThat(rwB.isMergedInto(change2Ps, masterB)).isTrue();

    assertThat(input.generateLockFailures).containsExactly(false);
  }

  @Test
  public void authorAndCommitDateAreEqual() throws Throwable {
    assume().that(getSubmitType()).isNotEqualTo(SubmitType.FAST_FORWARD_ONLY);

    ConfigInput ci = new ConfigInput();
    ci.matchAuthorToCommitterDate = InheritableBoolean.TRUE;
    gApi.projects().name(project.get()).config(ci);

    RevCommit initialHead = projectOperations.project(project).getHead("master");
    testRepo.reset(initialHead);
    PushOneCommit.Result change = createChange("Change 1", "b", "b");

    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "c", "c");

    if (getSubmitType() == SubmitType.MERGE_IF_NECESSARY
        || getSubmitType() == SubmitType.REBASE_IF_NECESSARY) {
      // Merge another change so that change2 is not a fast-forward
      submit(change.getChangeId());
    }

    submit(change2.getChangeId());
    assertAuthorAndCommitDateEquals(projectOperations.project(project).getHead("master"));
  }

  @Test
  @TestProjectInput(rejectEmptyCommit = InheritableBoolean.FALSE)
  public void submitEmptyCommitPatchSetCanNotFastForward_emptyCommitAllowed() throws Throwable {
    assume().that(getSubmitType()).isNotEqualTo(SubmitType.FAST_FORWARD_ONLY);

    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    ChangeApi revert1 = gApi.changes().id(change.getChangeId()).revert();
    approve(revert1.id());
    revert1.current().submit();

    ChangeApi revert2 = gApi.changes().id(change.getChangeId()).revert();
    approve(revert2.id());
    revert2.current().submit();
  }

  @Test
  @TestProjectInput(rejectEmptyCommit = InheritableBoolean.TRUE)
  public void submitEmptyCommitPatchSetCanNotFastForward_emptyCommitNotAllowed() throws Throwable {
    assume().that(getSubmitType()).isNotEqualTo(SubmitType.FAST_FORWARD_ONLY);

    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    ChangeApi revert1 = gApi.changes().id(change.getChangeId()).revert();
    approve(revert1.id());
    revert1.current().submit();

    ChangeApi revert2 = gApi.changes().id(change.getChangeId()).revert();
    approve(revert2.id());

    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> revert2.current().submit());
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Change "
                + revert2.get()._number
                + ": Change could not be merged because the commit is empty. Project policy"
                + " requires all commits to contain modifications to at least one file.");
  }

  @Test
  @TestProjectInput(rejectEmptyCommit = InheritableBoolean.FALSE)
  public void submitEmptyCommitPatchSetCanFastForward_emptyCommitAllowed() throws Throwable {
    ChangeInput ci = new ChangeInput();
    ci.subject = "Empty change";
    ci.project = project.get();
    ci.branch = "master";
    ChangeApi change = gApi.changes().create(ci);
    approve(change.id());
    change.current().submit();
  }

  @Test
  @TestProjectInput(rejectEmptyCommit = InheritableBoolean.TRUE)
  public void submitEmptyCommitPatchSetCanFastForward_emptyCommitNotAllowed() throws Throwable {
    ChangeInput ci = new ChangeInput();
    ci.subject = "Empty change";
    ci.project = project.get();
    ci.branch = "master";
    ChangeApi change = gApi.changes().create(ci);
    approve(change.id());

    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> change.current().submit());
    assertThat(thrown)
        .hasMessageThat()
        .contains(
            "Change "
                + change.get()._number
                + ": Change could not be merged because the commit is empty. Project policy"
                + " requires all commits to contain modifications to at least one file.");
  }

  @Test
  @TestProjectInput(createEmptyCommit = false, rejectEmptyCommit = InheritableBoolean.TRUE)
  public void submitNonemptyCommitToEmptyRepoWithRejectEmptyCommit_allowed() throws Throwable {
    assertThat(projectOperations.project(project).hasHead("master")).isFalse();
    PushOneCommit.Result change = createChange();
    assertThat(change.getCommit().getParents()).isEmpty();
    Map<BranchNameKey, ObjectId> actual = fetchFromSubmitPreview(change.getChangeId());
    assertThat(projectOperations.project(project).hasHead("master")).isFalse();
    assertThat(actual).hasSize(1);

    submit(change.getChangeId());
    assertThat(projectOperations.project(project).getHead("master").getId())
        .isEqualTo(change.getCommit());
    assertTrees(project, actual);
  }

  @Test
  @TestProjectInput(createEmptyCommit = false, rejectEmptyCommit = InheritableBoolean.TRUE)
  public void submitEmptyCommitToEmptyRepoWithRejectEmptyCommit_allowed() throws Throwable {
    assertThat(projectOperations.project(project).hasHead("master")).isFalse();
    PushOneCommit.Result change =
        pushFactory
            .create(admin.newIdent(), testRepo, "Change 1", ImmutableMap.of())
            .to("refs/for/master");
    change.assertOkStatus();
    assertThat(change.getCommit().getTree()).isEqualTo(EMPTY_TREE_ID);

    Map<BranchNameKey, ObjectId> actual = fetchFromSubmitPreview(change.getChangeId());
    assertThat(projectOperations.project(project).hasHead("master")).isFalse();
    assertThat(actual).hasSize(1);

    submit(change.getChangeId());
    assertThat(projectOperations.project(project).getHead("master").getId())
        .isEqualTo(change.getCommit());
    assertTrees(project, actual);
  }

  private void setChangeStatusToNew(PushOneCommit.Result... changes) throws Throwable {
    for (PushOneCommit.Result change : changes) {
      try (BatchUpdate bu =
          batchUpdateFactory.create(project, userFactory.create(admin.id()), TimeUtil.nowTs())) {
        bu.addOp(
            change.getChange().getId(),
            new BatchUpdateOp() {
              @Override
              public boolean updateChange(ChangeContext ctx) {
                ctx.getChange().setStatus(Change.Status.NEW);
                ctx.getUpdate(ctx.getChange().currentPatchSetId()).setStatus(Change.Status.NEW);
                return true;
              }
            });
        bu.execute();
      }
    }
  }

  @Test
  @GerritConfig(
      name = "change.mergeabilityComputationBehavior",
      value = "API_REF_UPDATED_AND_CHANGE_REINDEX")
  public void submitSchedulesOpenChangesOfSameBranchForReindexing() throws Throwable {
    // Create a merged change.
    PushOneCommit push =
        pushFactory.create(admin.newIdent(), testRepo, "Merged Change", "foo.txt", "foo");
    PushOneCommit.Result mergedChange = push.to("refs/for/master");
    mergedChange.assertOkStatus();
    approve(mergedChange.getChangeId());
    submit(mergedChange.getChangeId());

    // Create some open changes.
    PushOneCommit.Result change1 = createChange();
    PushOneCommit.Result change2 = createChange();
    PushOneCommit.Result change3 = createChange();

    // Create a branch with one open change.
    BranchInput in = new BranchInput();
    in.revision = projectOperations.project(project).getHead("master").name();
    gApi.projects().name(project.get()).branch("dev").create(in);
    PushOneCommit.Result changeOtherBranch = createChange("refs/for/dev");

    ChangeIndexedListener changeIndexedListener = mock(ChangeIndexedListener.class);
    try (Registration registration =
        extensionRegistry.newRegistration().add(changeIndexedListener)) {
      // submit a change, this should trigger asynchronous reindexing of the open changes on the
      // same branch
      approve(change1.getChangeId());
      submit(change1.getChangeId());
      assertThat(gApi.changes().id(change1.getChangeId()).get().status)
          .isEqualTo(ChangeStatus.MERGED);

      // on submit the change that is submitted gets reindexed synchronously
      verify(changeIndexedListener, atLeast(1))
          .onChangeScheduledForIndexing(project.get(), change1.getChange().getId().get());
      verify(changeIndexedListener, atLeast(1))
          .onChangeIndexed(project.get(), change1.getChange().getId().get());

      // the open changes on the same branch get reindexed asynchronously
      verify(changeIndexedListener, times(1))
          .onChangeScheduledForIndexing(project.get(), change2.getChange().getId().get());
      verify(changeIndexedListener, times(1))
          .onChangeScheduledForIndexing(project.get(), change3.getChange().getId().get());

      // merged changes don't get reindexed
      verify(changeIndexedListener, times(0))
          .onChangeScheduledForIndexing(project.get(), mergedChange.getChange().getId().get());

      // open changes on other branches don't get reindexed
      verify(changeIndexedListener, times(0))
          .onChangeScheduledForIndexing(project.get(), changeOtherBranch.getChange().getId().get());
    }
  }

  /**
   * There is currently a bug that adds the person who submitted the change as reviewer, which in
   * turn adds them to the attention set. This test ensures this doesn't happen.
   */
  @Test
  public void submitDoesNotAddReviewersToAttentionSet() throws Exception {
    PushOneCommit.Result r = createChange("refs/heads/master", "file1", "content");

    // Someone else approves, because if admin reviews, they will be added to the reviewers (and the
    // bug won't be reproduced).
    requestScopeOperations.setApiUser(accountCreator.admin2().id());
    change(r).current().review(ReviewInput.approve().addUserToAttentionSet(user.email(), "reason"));

    requestScopeOperations.setApiUser(admin.id());

    change(r).attention(admin.email()).remove(new AttentionSetInput("remove"));
    change(r).current().submit();

    AttentionSetUpdate attentionSet =
        Iterables.getOnlyElement(getAttentionSetUpdatesForUser(r, admin));

    assertThat(attentionSet.account()).isEqualTo(admin.id());
    assertThat(attentionSet.operation()).isEqualTo(AttentionSetUpdate.Operation.REMOVE);
    assertThat(attentionSet.reason()).isEqualTo("remove");
  }

  private List<AttentionSetUpdate> getAttentionSetUpdatesForUser(
      PushOneCommit.Result r, TestAccount account) {
    return r.getChange().attentionSet().stream()
        .filter(a -> a.account().get() == account.id().get())
        .collect(Collectors.toList());
  }

  private void assertSubmitter(PushOneCommit.Result change) throws Throwable {
    ChangeInfo info = get(change.getChangeId(), ListChangesOption.MESSAGES);
    assertThat(info.messages).isNotNull();
    Iterable<String> messages = Iterables.transform(info.messages, i -> i.message);
    assertThat(messages).hasSize(3);
    String last = Iterables.getLast(messages);
    if (getSubmitType() == SubmitType.CHERRY_PICK) {
      assertThat(last).startsWith("Change has been successfully cherry-picked as");
    } else if (getSubmitType() == SubmitType.REBASE_ALWAYS) {
      assertThat(last).startsWith("Change has been successfully rebased and submitted as");
    } else {
      assertThat(last).isEqualTo("Change has been successfully merged");
    }
  }

  @Override
  protected void updateProjectInput(ProjectInput in) {
    in.submitType = getSubmitType();
    if (in.useContentMerge == InheritableBoolean.INHERIT) {
      in.useContentMerge = InheritableBoolean.FALSE;
    }
  }

  protected void submit(String changeId) throws Throwable {
    submit(changeId, new SubmitInput(), null, null);
  }

  protected void submit(String changeId, SubmitInput input) throws Throwable {
    submit(changeId, input, null, null);
  }

  protected void submitWithConflict(String changeId, String expectedError) throws Throwable {
    submit(changeId, new SubmitInput(), ResourceConflictException.class, expectedError);
  }

  protected void submit(
      String changeId,
      SubmitInput input,
      @Nullable Class<? extends RestApiException> expectedExceptionType,
      String expectedExceptionMsg)
      throws Throwable {
    approve(changeId);
    if (expectedExceptionType == null) {
      assertSubmittable(changeId);
    } else {
      requireNonNull(expectedExceptionMsg);
    }
    ThrowingRunnable submit = () -> gApi.changes().id(changeId).current().submit(input);
    if (expectedExceptionType != null) {
      RestApiException thrown = assertThrows(expectedExceptionType, submit);
      assertThat(thrown).hasMessageThat().isEqualTo(expectedExceptionMsg);
      return;
    }
    submit.run();
    ChangeInfo change = gApi.changes().id(changeId).info();
    assertMerged(change.changeId);
  }

  protected void assertSubmittable(String changeId) throws Throwable {
    assertWithMessage("submit bit on ChangeInfo")
        .that(get(changeId, SUBMITTABLE).submittable)
        .isTrue();
    RevisionResource rsrc = parseCurrentRevisionResource(changeId);
    UiAction.Description desc = submitHandler.getDescription(rsrc);
    assertWithMessage("visible bit on submit action").that(desc.isVisible()).isTrue();
    assertWithMessage("enabled bit on submit action").that(desc.isEnabled()).isTrue();
  }

  protected void assertChangeMergedEvents(String... expected) throws Throwable {
    eventRecorder.assertChangeMergedEvents(project.get(), "refs/heads/master", expected);
  }

  protected void assertRefUpdatedEvents(RevCommit... expected) throws Throwable {
    eventRecorder.assertRefUpdatedEvents(project.get(), "refs/heads/master", expected);
  }

  protected void assertCurrentRevision(String changeId, int expectedNum, ObjectId expectedId)
      throws Throwable {
    ChangeInfo c = get(changeId, CURRENT_REVISION);
    assertThat(c.currentRevision).isEqualTo(expectedId.name());
    assertThat(c.revisions.get(expectedId.name())._number).isEqualTo(expectedNum);
    try (Repository repo = repoManager.openRepository(Project.nameKey(c.project))) {
      String refName = PatchSet.id(Change.id(c._number), expectedNum).toRefName();
      Ref ref = repo.exactRef(refName);
      assertWithMessage(refName).that(ref).isNotNull();
      assertThat(ref.getObjectId()).isEqualTo(expectedId);
    }
  }

  protected void assertNew(String changeId) throws Throwable {
    assertThat(info(changeId).status).isEqualTo(ChangeStatus.NEW);
  }

  protected void assertApproved(String changeId) throws Throwable {
    assertApproved(changeId, admin);
  }

  protected void assertApproved(String changeId, TestAccount user) throws Throwable {
    ChangeInfo c = get(changeId, DETAILED_LABELS);
    LabelInfo cr = c.labels.get("Code-Review");
    assertThat(cr.all).hasSize(1);
    assertThat(cr.all.get(0).value).isEqualTo(2);
    assertThat(Account.id(cr.all.get(0)._accountId)).isEqualTo(user.id());
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

  protected void assertAuthorAndCommitDateEquals(RevCommit commit) {
    assertThat(commit.getAuthorIdent().getWhen()).isEqualTo(commit.getCommitterIdent().getWhen());
    assertThat(commit.getAuthorIdent().getTimeZone())
        .isEqualTo(commit.getCommitterIdent().getTimeZone());
  }

  protected void assertSubmitter(String changeId, int psId) throws Throwable {
    assertSubmitter(changeId, psId, admin);
  }

  protected void assertSubmitter(String changeId, int psId, TestAccount user) throws Throwable {
    Change c = getOnlyElement(queryProvider.get().byKeyPrefix(changeId)).change();
    ChangeNotes cn = notesFactory.createChecked(c);
    PatchSetApproval submitter =
        approvalsUtil.getSubmitter(cn, PatchSet.id(cn.getChangeId(), psId));
    assertThat(submitter).isNotNull();
    assertThat(submitter.isLegacySubmit()).isTrue();
    assertThat(submitter.accountId()).isEqualTo(user.id());
  }

  protected void assertNoSubmitter(String changeId, int psId) throws Throwable {
    Change c = getOnlyElement(queryProvider.get().byKeyPrefix(changeId)).change();
    ChangeNotes cn = notesFactory.createChecked(c);
    PatchSetApproval submitter =
        approvalsUtil.getSubmitter(cn, PatchSet.id(cn.getChangeId(), psId));
    assertThat(submitter).isNull();
  }

  protected void assertCherryPick(TestRepository<?> testRepo, boolean contentMerge)
      throws Throwable {
    assertRebase(testRepo, contentMerge);
    RevCommit remoteHead = projectOperations.project(project).getHead("master");
    assertThat(remoteHead.getFooterLines("Reviewed-On")).isNotEmpty();
    assertThat(remoteHead.getFooterLines("Reviewed-By")).isNotEmpty();
  }

  protected void assertRebase(TestRepository<?> testRepo, boolean contentMerge) throws Throwable {
    Repository repo = testRepo.getRepository();
    RevCommit localHead = getHead(repo, "HEAD");
    RevCommit remoteHead = projectOperations.project(project).getHead("master");
    assertThat(localHead.getId()).isNotEqualTo(remoteHead.getId());
    assertThat(remoteHead.getParentCount()).isEqualTo(1);
    if (!contentMerge) {
      assertThat(getLatestRemoteDiff()).isEqualTo(getLatestDiff(repo));
    }
    assertThat(remoteHead.getShortMessage()).isEqualTo(localHead.getShortMessage());
  }

  protected List<RevCommit> getRemoteLog(Project.NameKey project, String branch) throws Throwable {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      rw.markStart(rw.parseCommit(repo.exactRef("refs/heads/" + branch).getObjectId()));
      return Lists.newArrayList(rw);
    }
  }

  protected List<RevCommit> getRemoteLog() throws Throwable {
    return getRemoteLog(project, "master");
  }

  private String getLatestDiff(Repository repo) throws Throwable {
    ObjectId oldTreeId = repo.resolve("HEAD~1^{tree}");
    ObjectId newTreeId = repo.resolve("HEAD^{tree}");
    return getLatestDiff(repo, oldTreeId, newTreeId);
  }

  private String getLatestRemoteDiff() throws Throwable {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      ObjectId oldTreeId = repo.resolve("refs/heads/master~1^{tree}");
      ObjectId newTreeId = repo.resolve("refs/heads/master^{tree}");
      return getLatestDiff(repo, oldTreeId, newTreeId);
    }
  }

  private String getLatestDiff(Repository repo, ObjectId oldTreeId, ObjectId newTreeId)
      throws Throwable {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    try (DiffFormatter fmt = new DiffFormatter(out)) {
      fmt.setRepository(repo);
      fmt.format(oldTreeId, newTreeId);
      fmt.flush();
      return out.toString();
    }
  }

  // TODO(hanwen): the submodule tests have a similar method; maybe we could share code?
  protected Project.NameKey createProjectForPush(SubmitType submitType) throws Throwable {
    Project.NameKey project = projectOperations.newProject().submitType(submitType).create();
    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.PUSH).ref("refs/heads/*").group(adminGroupUuid()))
        .add(allow(Permission.SUBMIT).ref("refs/for/refs/heads/*").group(adminGroupUuid()))
        .update();
    return project;
  }

  protected PushOneCommit.Result createChange(
      String subject, String fileName, String content, String topic) throws Throwable {
    PushOneCommit push = pushFactory.create(admin.newIdent(), testRepo, subject, fileName, content);
    return push.to("refs/for/master%topic=" + name(topic));
  }
}
