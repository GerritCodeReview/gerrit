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
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseClockStep;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.util.Collection;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.junit.Test;

@NoHttpd
public class SubmoduleSubscriptionsIT extends AbstractSubmoduleSubscription {
  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Inject private ProjectOperations projectOperations;
  @Inject private SubmitRuleEvaluator.Factory evaluatorFactory;

  @Test
  @GerritConfig(name = "submodule.enableSuperProjectSubscriptions", value = "false")
  public void testSubscriptionWithoutGlobalServerSetting() throws Exception {
    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", subKey)).isFalse();
  }

  @Test
  public void subscriptionWithoutSpecificSubscription() throws Exception {
    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", subKey)).isFalse();
  }

  @Test
  public void subscriptionToEmptyRepo() throws Exception {

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    pushChangeTo(subRepo, "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", subKey)).isTrue();
    expectToHaveSubmoduleState(superRepo, "master", subKey, subHEAD);
  }

  @Test
  public void subscriptionToExistingRepo() throws Exception {

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", subKey)).isTrue();
    expectToHaveSubmoduleState(superRepo, "master", subKey, subHEAD);
  }

  @Test
  public void subscriptionWildcardACLForSingleBranch() throws Exception {

    // master is allowed to be subscribed to master branch only:
    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, null);
    // create 'branch':
    pushChangeTo(superRepo, "branch");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    createSubmoduleSubscription(superRepo, "branch", subKey, "master");

    ObjectId subHEAD = pushChangeTo(subRepo, "master");

    expectToHaveSubmoduleState(superRepo, "master", subKey, subHEAD);
    assertThat(hasSubmodule(superRepo, "branch", subKey)).isFalse();
  }

  @Test
  public void subscriptionWildcardACLForMissingProject() throws Exception {

    allowMatchingSubmoduleSubscription(
        subKey, "refs/heads/*", Project.nameKey("not-existing-super-project"), "refs/heads/*");
    pushChangeTo(subRepo, "master");
  }

  @Test
  public void subscriptionWildcardACLForMissingBranch() throws Exception {
    allowMatchingSubmoduleSubscription(subKey, "refs/heads/*", superKey, "refs/heads/*");
    pushChangeTo(subRepo, "foo");
  }

  @Test
  public void subscriptionWildcardACLForMissingGitmodules() throws Exception {

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/*", superKey, "refs/heads/*");
    pushChangeTo(superRepo, "master");
    pushChangeTo(subRepo, "master");
  }

  @Test
  public void subscriptionWildcardACLOneOnOneMapping() throws Exception {

    // any branch is allowed to be subscribed to the same superprojects branch:
    allowMatchingSubmoduleSubscription(subKey, "refs/heads/*", superKey, "refs/heads/*");

    // create 'branch' in both repos:
    pushChangeTo(superRepo, "branch");
    pushChangeTo(subRepo, "branch");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    createSubmoduleSubscription(superRepo, "branch", subKey, "branch");

    ObjectId subHEAD1 = pushChangeTo(subRepo, "master");
    ObjectId subHEAD2 = pushChangeTo(subRepo, "branch");

    expectToHaveSubmoduleState(superRepo, "master", subKey, subHEAD1);
    expectToHaveSubmoduleState(superRepo, "branch", subKey, subHEAD2);

    // Now test that cross subscriptions do not work:
    createSubmoduleSubscription(superRepo, "master", subKey, "branch");
    ObjectId subHEAD3 = pushChangeTo(subRepo, "branch");

    expectToHaveSubmoduleState(superRepo, "master", subKey, subHEAD1);
    expectToHaveSubmoduleState(superRepo, "branch", subKey, subHEAD3);
  }

  @Test
  public void subscriptionWildcardACLForManyBranches() throws Exception {

    // Any branch is allowed to be subscribed to any superproject branch:
    allowSubmoduleSubscription(subKey, "refs/heads/*", superKey, null, false);
    pushChangeTo(superRepo, "branch");
    pushChangeTo(subRepo, "another-branch");
    createSubmoduleSubscription(superRepo, "branch", subKey, "another-branch");
    ObjectId subHEAD = pushChangeTo(subRepo, "another-branch");
    expectToHaveSubmoduleState(superRepo, "branch", subKey, subHEAD);
  }

  @Test
  public void subscriptionWildcardACLOneToManyBranches() throws Exception {

    // Any branch is allowed to be subscribed to any superproject branch:
    allowSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/*", false);
    pushChangeTo(superRepo, "branch");
    createSubmoduleSubscription(superRepo, "branch", subKey, "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "branch", subKey, subHEAD);

    createSubmoduleSubscription(superRepo, "branch", subKey, "branch");
    pushChangeTo(subRepo, "branch");

    // no change expected, as only master is subscribed:
    expectToHaveSubmoduleState(superRepo, "branch", subKey, subHEAD);
  }

  @Test
  @GerritConfig(name = "submodule.verboseSuperprojectUpdate", value = "false")
  public void testSubmoduleShortCommitMessage() throws Exception {

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");

    // The first update doesn't include any commit messages
    ObjectId subRepoId = pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master", subKey, subRepoId);
    expectToHaveCommitMessage(superRepo, "master", "Update git submodules\n\n");

    // Any following update also has a short message
    subRepoId = pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master", subKey, subRepoId);
    expectToHaveCommitMessage(superRepo, "master", "Update git submodules\n\n");
  }

  @Test
  @GerritConfig(name = "submodule.verboseSuperprojectUpdate", value = "SUBJECT_ONLY")
  public void testSubmoduleSubjectCommitMessage() throws Exception {

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");

    // The first update doesn't include the rev log
    RevWalk rw = subRepo.getRevWalk();
    expectToHaveCommitMessage(
        superRepo,
        "master",
        "Update git submodules\n\n"
            + "* Update "
            + subKey.get()
            + " from branch 'master'\n  to "
            + subHEAD.getName());

    // The next commit should generate only its commit message,
    // omitting previous commit logs
    subHEAD = pushChangeTo(subRepo, "master");
    RevCommit subCommitMsg = rw.parseCommit(subHEAD);
    expectToHaveCommitMessage(
        superRepo,
        "master",
        "Update git submodules\n\n"
            + "* Update "
            + subKey.get()
            + " from branch 'master'\n  to "
            + subHEAD.getName()
            + "\n  - "
            + subCommitMsg.getShortMessage());
  }

  @Test
  public void submoduleCommitMessage() throws Exception {

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");

    // The first update doesn't include the rev log
    RevWalk rw = subRepo.getRevWalk();
    expectToHaveCommitMessage(
        superRepo,
        "master",
        "Update git submodules\n\n"
            + "* Update "
            + subKey.get()
            + " from branch 'master'\n  to "
            + subHEAD.getName());

    // The next commit should generate only its commit message,
    // omitting previous commit logs
    subHEAD = pushChangeTo(subRepo, "master");
    RevCommit subCommitMsg = rw.parseCommit(subHEAD);
    expectToHaveCommitMessage(
        superRepo,
        "master",
        "Update git submodules\n\n"
            + "* Update "
            + subKey.get()
            + " from branch 'master'\n  to "
            + subHEAD.getName()
            + "\n  - "
            + subCommitMsg.getFullMessage().replace("\n", "\n    "));
  }

  @Test
  public void subscriptionUnsubscribe() throws Exception {

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");

    pushChangeTo(subRepo, "master");
    ObjectId subHEADbeforeUnsubscribing = pushChangeTo(subRepo, "master");

    deleteAllSubscriptions(superRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master", subKey, subHEADbeforeUnsubscribing);

    pushChangeTo(superRepo, "refs/heads/master", "commit after unsubscribe", "");
    pushChangeTo(subRepo, "refs/heads/master", "commit after unsubscribe", "");
    expectToHaveSubmoduleState(superRepo, "master", subKey, subHEADbeforeUnsubscribing);
  }

  @Test
  public void subscriptionUnsubscribeByDeletingGitModules() throws Exception {

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");

    pushChangeTo(subRepo, "master");
    ObjectId subHEADbeforeUnsubscribing = pushChangeTo(subRepo, "master");

    deleteGitModulesFile(superRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master", subKey, subHEADbeforeUnsubscribing);

    pushChangeTo(superRepo, "refs/heads/master", "commit after unsubscribe", "");
    pushChangeTo(subRepo, "refs/heads/master", "commit after unsubscribe", "");
    expectToHaveSubmoduleState(superRepo, "master", subKey, subHEADbeforeUnsubscribing);
  }

  @Test
  public void subscriptionToDifferentBranches() throws Exception {

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/foo", superKey, "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", subKey, "foo");
    ObjectId subFoo = pushChangeTo(subRepo, "foo");
    pushChangeTo(subRepo, "master");

    expectToHaveSubmoduleState(superRepo, "master", subKey, subFoo);
  }

  @Test
  public void branchCircularSubscription() throws Exception {

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(superKey, "refs/heads/master", subKey, "refs/heads/master");

    pushChangeTo(subRepo, "master");
    pushChangeTo(superRepo, "master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    createSubmoduleSubscription(subRepo, "master", superKey, "master");

    pushChangeTo(subRepo, "master");
    pushChangeTo(superRepo, "master");

    assertThat(hasSubmodule(subRepo, "master", superKey)).isFalse();
    assertThat(hasSubmodule(superRepo, "master", subKey)).isFalse();
  }

  @Test
  public void projectCircularSubscription() throws Exception {

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(superKey, "refs/heads/dev", subKey, "refs/heads/dev");

    pushChangeTo(subRepo, "master");
    pushChangeTo(superRepo, "master");
    pushChangeTo(subRepo, "dev");
    pushChangeTo(superRepo, "dev");

    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    createSubmoduleSubscription(subRepo, "dev", superKey, "dev");

    ObjectId subMasterHead = pushChangeTo(subRepo, "master");
    ObjectId superDevHead = pushChangeTo(superRepo, "dev");

    assertThat(hasSubmodule(superRepo, "master", subKey)).isTrue();
    assertThat(hasSubmodule(subRepo, "dev", superKey)).isTrue();
    expectToHaveSubmoduleState(superRepo, "master", subKey, subMasterHead);
    expectToHaveSubmoduleState(subRepo, "dev", superKey, superDevHead);
  }

  @Test
  public void subscriptionFailOnMissingACL() throws Exception {

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", subKey)).isFalse();
  }

  @Test
  public void subscriptionFailOnWrongProjectACL() throws Exception {
    allowMatchingSubmoduleSubscription(
        subKey, "refs/heads/master", Project.nameKey("wrong-super-project"), "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", subKey)).isFalse();
  }

  @Test
  public void subscriptionFailOnWrongBranchACL() throws Exception {
    allowMatchingSubmoduleSubscription(
        subKey, "refs/heads/master", superKey, "refs/heads/wrong-branch");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", subKey)).isFalse();
  }

  @Test
  public void subscriptionInheritACL() throws Exception {
    Project.NameKey configKey = projectOperations.newProject().submitType(getSubmitType()).create();
    grantPush(configKey);
    Project.NameKey config2Key =
        projectOperations.newProject().parent(configKey).submitType(getSubmitType()).create();
    grantPush(config2Key);
    cloneProject(config2Key);

    subKey = projectOperations.newProject().parent(config2Key).submitType(getSubmitType()).create();
    grantPush(subKey);
    subRepo = cloneProject(subKey);

    allowMatchingSubmoduleSubscription(configKey, "refs/heads/*", superKey, "refs/heads/*");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master", subKey, subHEAD);
  }

  @Test
  public void allowedButNotSubscribed() throws Exception {

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");

    pushChangeTo(subRepo, "master");
    subRepo
        .branch("HEAD")
        .commit()
        .insertChangeId()
        .message("some change")
        .add("b.txt", "b contents for testing")
        .create();
    String refspec = "HEAD:refs/heads/master";
    PushResult r =
        Iterables.getOnlyElement(
            subRepo.git().push().setRemote("origin").setRefSpecs(new RefSpec(refspec)).call());
    assertThat(r.getMessages()).doesNotContain("error");
    assertThat(r.getRemoteUpdate("refs/heads/master").getStatus())
        .isEqualTo(RemoteRefUpdate.Status.OK);

    assertThat(hasSubmodule(superRepo, "master", subKey)).isFalse();
  }

  @Test
  public void subscriptionDeepRelative() throws Exception {
    Project.NameKey nest = createProjectForPush(getSubmitType());
    TestRepository<?> subRepo = cloneProject(nest);
    // master is allowed to be subscribed to any superprojects branch:
    allowMatchingSubmoduleSubscription(nest, "refs/heads/master", superKey, null);

    pushChangeTo(subRepo, "master");
    createRelativeSubmoduleSubscription(superRepo, "master", "../", nest, "master");

    ObjectId subHEAD = pushChangeTo(subRepo, "master");

    expectToHaveSubmoduleState(superRepo, "master", nest, subHEAD);
  }

  @Test
  @GerritConfig(name = "submodule.verboseSuperprojectUpdate", value = "SUBJECT_ONLY")
  @GerritConfig(name = "submodule.maxCommitMessages", value = "1")
  public void submoduleSubjectCommitMessageCountLimit() throws Exception {
    testSubmoduleSubjectCommitMessageAndExpectTruncation();
  }

  @Test
  @GerritConfig(name = "submodule.verboseSuperprojectUpdate", value = "SUBJECT_ONLY")
  // The value 110 must tuned to the test environment, and is sensitive to the
  // length of the uniquified repository name.
  @GerritConfig(name = "submodule.maxCombinedCommitMessageSize", value = "110")
  public void submoduleSubjectCommitMessageSizeLimit() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isFalse();
    testSubmoduleSubjectCommitMessageAndExpectTruncation();
  }

  @Test
  @UseClockStep
  public void superRepoCommitHasSameAuthorAsSubmoduleCommit() throws Exception {
    // Make sure that the commit is created at an earlier timestamp than the submit timestamp.
    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");

    PushOneCommit.Result pushResult =
        createChange(subRepo, "refs/heads/master", "Change", "a.txt", "some content", null);
    approve(pushResult.getChangeId());
    gApi.changes().id(pushResult.getChangeId()).current().submit();

    // Expect that the author name/email is preserved for the superRepo commit, but a new author
    // timestamp is used.
    PersonIdent authorIdent = getAuthor(superRepo, "master");
    assertThat(authorIdent.getName()).isEqualTo(admin.fullName());
    assertThat(authorIdent.getEmailAddress()).isEqualTo(admin.email());
    assertThat(authorIdent.getWhen())
        .isGreaterThan(pushResult.getCommit().getAuthorIdent().getWhen());
  }

  @Test
  @UseClockStep
  public void superRepoCommitHasSameAuthorAsSubmoduleCommits() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();

    Project.NameKey proj2 = createProjectForPush(getSubmitType());

    TestRepository<?> subRepo2 = cloneProject(proj2);
    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(proj2, "refs/heads/master", superKey, "refs/heads/master");

    Config config = new Config();
    prepareSubmoduleConfigEntry(config, subKey, subKey, "master");
    prepareSubmoduleConfigEntry(config, proj2, proj2, "master");
    pushSubmoduleConfig(superRepo, "master", config);

    String topic = "foo";

    PushOneCommit.Result pushResult1 =
        createChange(subRepo, "refs/heads/master", "Change 1", "a.txt", "some content", topic);
    approve(pushResult1.getChangeId());

    PushOneCommit.Result pushResult2 =
        createChange(subRepo2, "refs/heads/master", "Change 2", "b.txt", "other content", topic);
    approve(pushResult2.getChangeId());

    // Submit the topic, 2 changes with the same author.
    gApi.changes().id(pushResult1.getChangeId()).current().submit();

    // Expect that the author name/email is preserved for the superRepo commit, but a new author
    // timestamp is used.
    PersonIdent authorIdent = getAuthor(superRepo, "master");
    assertThat(authorIdent.getName()).isEqualTo(admin.fullName());
    assertThat(authorIdent.getEmailAddress()).isEqualTo(admin.email());
    assertThat(authorIdent.getWhen())
        .isGreaterThan(pushResult1.getCommit().getAuthorIdent().getWhen());
    assertThat(authorIdent.getWhen())
        .isGreaterThan(pushResult2.getCommit().getAuthorIdent().getWhen());
  }

  @Test
  @UseClockStep
  public void superRepoCommitHasGerritAsAuthorIfAuthorsOfSubmoduleCommitsDiffer() throws Exception {
    assume().that(isSubmitWholeTopicEnabled()).isTrue();

    Project.NameKey proj2 = createProjectForPush(getSubmitType());
    TestRepository<InMemoryRepository> repo2 = cloneProject(proj2, user);

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(proj2, "refs/heads/master", superKey, "refs/heads/master");

    Config config = new Config();
    prepareSubmoduleConfigEntry(config, subKey, subKey, "master");
    prepareSubmoduleConfigEntry(config, proj2, proj2, "master");
    pushSubmoduleConfig(superRepo, "master", config);

    String topic = "foo";

    // Create change as admin.
    PushOneCommit.Result pushResult1 =
        createChange(subRepo, "refs/heads/master", "Change 1", "a.txt", "some content", topic);
    approve(pushResult1.getChangeId());

    // Create change as user.
    PushOneCommit push =
        pushFactory.create(user.newIdent(), repo2, "Change 2", "b.txt", "other content");
    PushOneCommit.Result pushResult2 = push.to("refs/for/master%topic=" + name(topic));
    approve(pushResult2.getChangeId());

    // Submit the topic, 2 changes with the different author.
    gApi.changes().id(pushResult1.getChangeId()).current().submit();

    // Expect that the Gerrit server identity is chosen as author for the superRepo commit and a
    // new author timestamp is used.
    PersonIdent authorIdent = getAuthor(superRepo, "master");
    assertThat(authorIdent.getName()).isEqualTo(serverIdent.get().getName());
    assertThat(authorIdent.getEmailAddress()).isEqualTo(serverIdent.get().getEmailAddress());
    assertThat(authorIdent.getWhen())
        .isGreaterThan(pushResult1.getCommit().getAuthorIdent().getWhen());
    assertThat(authorIdent.getWhen())
        .isGreaterThan(pushResult2.getCommit().getAuthorIdent().getWhen());
  }

  @Test
  public void updateOnlyRelevantSubmodules() throws Exception {
    Project.NameKey subkey1 = createProjectForPush(getSubmitType());
    Project.NameKey subkey2 = createProjectForPush(getSubmitType());
    TestRepository<?> subRepo1 = cloneProject(subkey1);
    TestRepository<?> subRepo2 = cloneProject(subkey2);

    allowMatchingSubmoduleSubscription(subkey1, "refs/heads/master", superKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(subkey2, "refs/heads/master", superKey, "refs/heads/master");

    Config config = new Config();
    prepareSubmoduleConfigEntry(config, subkey1, "master");
    prepareSubmoduleConfigEntry(config, subkey2, "master");
    pushSubmoduleConfig(superRepo, "master", config);

    // Push once to initialize submodules.
    ObjectId subTip2 = pushChangeTo(subRepo2, "master");
    ObjectId subTip1 = pushChangeTo(subRepo1, "master");

    expectToHaveSubmoduleState(superRepo, "master", subkey1, subTip1);
    expectToHaveSubmoduleState(superRepo, "master", subkey2, subTip2);

    directUpdateRef(subkey2, "refs/heads/master");
    subTip1 = pushChangeTo(subRepo1, "master");

    expectToHaveSubmoduleState(superRepo, "master", subkey1, subTip1);
    expectToHaveSubmoduleState(superRepo, "master", subkey2, subTip2);
  }

  @Test
  public void skipUpdatingBrokenGitlinkPointer() throws Exception {

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");

    // Push once to initialize submodule.
    ObjectId subTip = pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master", subKey, subTip);

    // Write an invalid SHA-1 directly to the gitlink.
    ObjectId badId = ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    directUpdateSubmodule(superKey, "refs/heads/master", subKey, badId);
    expectToHaveSubmoduleState(superRepo, "master", subKey, badId);

    // Push succeeds, but gitlink update is skipped.
    pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master", subKey, badId);
  }

  @Test
  public void blockSubmissionForChangesModifyingSpecifiedSubmodule() throws Exception {
    ObjectId commitId = getCommitWithSubmoduleUpdate();

    CherryPickInput cherryPickInput = new CherryPickInput();
    cherryPickInput.destination = "branch";
    cherryPickInput.allowConflicts = true;

    // The rule will fail if the next change has a submodule file modification with subKey.
    modifySubmitRulesToBlockSubmoduleChanges(String.format("file('%s','M','SUBMODULE')", subKey));

    // Cherry-pick the newly created commit which contains a submodule update, to branch "branch".
    ChangeApi changeApi =
        gApi.projects().name(superKey.get()).commit(commitId.getName()).cherryPick(cherryPickInput);

    // Add another file to this change for good measure.
    PushOneCommit.Result result =
        amendChange(changeApi.get().changeId, "subject", "newFile", "content");

    assertThat(getStatus(result.getChange())).isEqualTo(SubmitRecord.Status.NOT_READY);
    assertThat(gApi.changes().id(result.getChangeId()).get().submittable).isFalse();
  }

  @Test
  public void blockSubmissionWithSubmodules() throws Exception {
    ObjectId commitId = getCommitWithSubmoduleUpdate();
    CherryPickInput cherryPickInput = new CherryPickInput();
    cherryPickInput.destination = "branch";
    cherryPickInput.allowConflicts = true;

    // The rule will fail if the next change has any submodule file.
    modifySubmitRulesToBlockSubmoduleChanges("file(_,_,'SUBMODULE')");

    // Cherry-pick the newly created commit which contains a submodule update, to branch "branch".
    ChangeApi changeApi =
        gApi.projects().name(superKey.get()).commit(commitId.getName()).cherryPick(cherryPickInput);

    // Add another file to this change for good measure.
    PushOneCommit.Result result =
        amendChange(changeApi.get().changeId, "subject", "newFile", "content");

    assertThat(getStatus(result.getChange())).isEqualTo(SubmitRecord.Status.NOT_READY);
    assertThat(gApi.changes().id(result.getChangeId()).get().submittable).isFalse();
  }

  @Test
  public void doNotBlockSubmissionWithoutSubmodules() throws Exception {
    modifySubmitRulesToBlockSubmoduleChanges("file(_,_,'SUBMODULE')");

    PushOneCommit.Result result =
        createChange(superRepo, "refs/heads/master", "subject", "newFile", "content", null);

    assertThat(getStatus(result.getChange())).isEqualTo(SubmitRecord.Status.OK);
    assertThat(gApi.changes().id(result.getChangeId()).get().submittable).isTrue();
  }

  private ObjectId getCommitWithSubmoduleUpdate() throws Exception {
    allowMatchingSubmoduleSubscription(subKey, "refs/heads/*", superKey, "refs/heads/*");
    // Create branch "branch" for the parent and the submodule
    pushChangeTo(superRepo, "branch");
    pushChangeTo(subRepo, "branch");

    // Make the superRepo a parent repo of the subRepo, for both branches.
    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    createSubmoduleSubscription(superRepo, "branch", subKey, "branch");
    pushChangeTo(subRepo, "master");
    pushChangeTo(subRepo, "branch");

    // This push creates a new commit in subRepo, master branch, which makes superRepo update their
    // submodule.
    pushChangeTo(subRepo, "master");

    // Fetch the commit from superRepo that Gerrit created automatically to fulfill the submodule
    // subscription.
    return superRepo
        .git()
        .fetch()
        .setRemote("origin")
        .call()
        .getAdvertisedRef("refs/heads/" + "master")
        .getObjectId();
  }

  private void modifySubmitRulesToBlockSubmoduleChanges(String file) throws Exception {
    String newContent =
        String.format(
            "member(X,[X|_]).\n"
                + "member(X,[Y|T]) :- member(X,T).\n"
                + "submit_rule(submit(R)) :-\n"
                + "  gerrit:files(List),\n"
                + "  member(%s, List),\n"
                + "  !,\n"
                + "  R = label('All-Submodules-Resolved', need(_)).\n"
                + "submit_rule(submit(label('All-Submodules-Resolved', ok(A)))) :-\n"
                + "  gerrit:commit_author(A).",
            file);

    try (Repository repo = repoManager.openRepository(superKey);
        TestRepository<Repository> testRepo = new TestRepository<>(repo)) {
      testRepo
          .branch(RefNames.REFS_CONFIG)
          .commit()
          .author(admin.newIdent())
          .committer(admin.newIdent())
          .add("rules.pl", newContent)
          .message("Modify rules.pl")
          .create();
    }
    projectCache.evict(superKey);
  }

  private SubmitRecord.Status getStatus(ChangeData cd) throws Exception {

    Collection<SubmitRecord> records;
    try (AutoCloseable changeIndex = disableChangeIndex()) {
      try (AutoCloseable accountIndex = disableAccountIndex()) {
        SubmitRuleEvaluator ruleEvaluator = evaluatorFactory.create(SubmitRuleOptions.defaults());
        records = ruleEvaluator.evaluate(cd);
      }
    }

    assertThat(records).hasSize(1);
    SubmitRecord record = records.iterator().next();
    return record.status;
  }

  private ObjectId directUpdateRef(Project.NameKey project, String ref) throws Exception {
    try (Repository serverRepo = repoManager.openRepository(project);
        TestRepository<Repository> tr = new TestRepository<>(serverRepo)) {
      return tr.branch(ref).commit().create().copy();
    }
  }

  private void testSubmoduleSubjectCommitMessageAndExpectTruncation() throws Exception {
    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    // The first update doesn't include the rev log, so we ignore it
    pushChangeTo(subRepo, "master");

    // Next, we push two commits at once. Since maxCommitMessages=1, we expect to see only the first
    // message plus ellipsis to mark truncation.
    ObjectId subHEAD = pushChangesTo(subRepo, "master", 2);
    RevCommit subCommitMsg = subRepo.getRevWalk().parseCommit(subHEAD);
    expectToHaveCommitMessage(
        superRepo,
        "master",
        String.format(
            "Update git submodules\n\n* Update %s from branch 'master'\n  to %s\n  - %s\n\n[...]",
            subKey.get(), subHEAD.getName(), subCommitMsg.getShortMessage()));
  }
}
