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

import com.google.common.collect.Iterables;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.testutil.ConfigSuite;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
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

  @Test
  @GerritConfig(name = "submodule.enableSuperProjectSubscriptions", value = "false")
  public void testSubscriptionWithoutGlobalServerSetting() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", "subscribed-to-project")).isFalse();
  }

  @Test
  public void testSubscriptionWithoutSpecificSubscription() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", "subscribed-to-project")).isFalse();
  }

  @Test
  public void testSubscriptionToEmptyRepo() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    pushChangeTo(subRepo, "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", "subscribed-to-project")).isTrue();
    expectToHaveSubmoduleState(superRepo, "master", "subscribed-to-project", subHEAD);
  }

  @Test
  public void testSubscriptionToExistingRepo() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", "subscribed-to-project")).isTrue();
    expectToHaveSubmoduleState(superRepo, "master", "subscribed-to-project", subHEAD);
  }

  @Test
  public void testSubscriptionWildcardACLForSingleBranch() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    // master is allowed to be subscribed to master branch only:
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", null);
    // create 'branch':
    pushChangeTo(superRepo, "branch");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    createSubmoduleSubscription(superRepo, "branch", "subscribed-to-project", "master");

    ObjectId subHEAD = pushChangeTo(subRepo, "master");

    expectToHaveSubmoduleState(superRepo, "master", "subscribed-to-project", subHEAD);
    assertThat(hasSubmodule(superRepo, "branch", "subscribed-to-project")).isFalse();
  }

  @Test
  public void testSubscriptionWildcardACLForMissingProject() throws Exception {
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/*", "not-existing-super-project", "refs/heads/*");
    pushChangeTo(subRepo, "master");
  }

  @Test
  public void testSubscriptionWildcardACLForMissingBranch() throws Exception {
    createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/*", "super-project", "refs/heads/*");
    pushChangeTo(subRepo, "foo");
  }

  @Test
  public void testSubscriptionWildcardACLForMissingGitmodules() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/*", "super-project", "refs/heads/*");
    pushChangeTo(superRepo, "master");
    pushChangeTo(subRepo, "master");
  }

  @Test
  public void testSubscriptionWildcardACLOneOnOneMapping() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    // any branch is allowed to be subscribed to the same superprojects branch:
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/*", "super-project", "refs/heads/*");

    // create 'branch' in both repos:
    pushChangeTo(superRepo, "branch");
    pushChangeTo(subRepo, "branch");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    createSubmoduleSubscription(superRepo, "branch", "subscribed-to-project", "branch");

    ObjectId subHEAD1 = pushChangeTo(subRepo, "master");
    ObjectId subHEAD2 = pushChangeTo(subRepo, "branch");

    expectToHaveSubmoduleState(superRepo, "master", "subscribed-to-project", subHEAD1);
    expectToHaveSubmoduleState(superRepo, "branch", "subscribed-to-project", subHEAD2);

    // Now test that cross subscriptions do not work:
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "branch");
    ObjectId subHEAD3 = pushChangeTo(subRepo, "branch");

    expectToHaveSubmoduleState(superRepo, "master", "subscribed-to-project", subHEAD1);
    expectToHaveSubmoduleState(superRepo, "branch", "subscribed-to-project", subHEAD3);
  }

  @Test
  public void testSubscriptionWildcardACLForManyBranches() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    // Any branch is allowed to be subscribed to any superproject branch:
    allowSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/*", "super-project", null, false);
    pushChangeTo(superRepo, "branch");
    pushChangeTo(subRepo, "another-branch");
    createSubmoduleSubscription(superRepo, "branch", "subscribed-to-project", "another-branch");
    ObjectId subHEAD = pushChangeTo(subRepo, "another-branch");
    expectToHaveSubmoduleState(superRepo, "branch", "subscribed-to-project", subHEAD);
  }

  @Test
  public void testSubscriptionWildcardACLOneToManyBranches() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    // Any branch is allowed to be subscribed to any superproject branch:
    allowSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/*", false);
    pushChangeTo(superRepo, "branch");
    createSubmoduleSubscription(superRepo, "branch", "subscribed-to-project", "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "branch", "subscribed-to-project", subHEAD);

    createSubmoduleSubscription(superRepo, "branch", "subscribed-to-project", "branch");
    pushChangeTo(subRepo, "branch");

    // no change expected, as only master is subscribed:
    expectToHaveSubmoduleState(superRepo, "branch", "subscribed-to-project", subHEAD);
  }

  @Test
  @GerritConfig(name = "submodule.verboseSuperprojectUpdate", value = "false")
  public void testSubmoduleShortCommitMessage() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");

    // The first update doesn't include any commit messages
    ObjectId subRepoId = pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master", "subscribed-to-project", subRepoId);
    expectToHaveCommitMessage(superRepo, "master", "Update git submodules\n\n");

    // Any following update also has a short message
    subRepoId = pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master", "subscribed-to-project", subRepoId);
    expectToHaveCommitMessage(superRepo, "master", "Update git submodules\n\n");
  }

  @Test
  @GerritConfig(name = "submodule.verboseSuperprojectUpdate", value = "SUBJECT_ONLY")
  public void testSubmoduleSubjectCommitMessage() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");

    // The first update doesn't include the rev log
    RevWalk rw = subRepo.getRevWalk();
    expectToHaveCommitMessage(
        superRepo,
        "master",
        "Update git submodules\n\n"
            + "* Update "
            + name("subscribed-to-project")
            + " from branch 'master'");

    // The next commit should generate only its commit message,
    // omitting previous commit logs
    subHEAD = pushChangeTo(subRepo, "master");
    RevCommit subCommitMsg = rw.parseCommit(subHEAD);
    expectToHaveCommitMessage(
        superRepo,
        "master",
        "Update git submodules\n\n"
            + "* Update "
            + name("subscribed-to-project")
            + " from branch 'master'"
            + "\n  - "
            + subCommitMsg.getShortMessage());
  }

  @Test
  public void testSubmoduleCommitMessage() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");

    // The first update doesn't include the rev log
    RevWalk rw = subRepo.getRevWalk();
    expectToHaveCommitMessage(
        superRepo,
        "master",
        "Update git submodules\n\n"
            + "* Update "
            + name("subscribed-to-project")
            + " from branch 'master'");

    // The next commit should generate only its commit message,
    // omitting previous commit logs
    subHEAD = pushChangeTo(subRepo, "master");
    RevCommit subCommitMsg = rw.parseCommit(subHEAD);
    expectToHaveCommitMessage(
        superRepo,
        "master",
        "Update git submodules\n\n"
            + "* Update "
            + name("subscribed-to-project")
            + " from branch 'master'"
            + "\n  - "
            + subCommitMsg.getFullMessage().replace("\n", "\n    "));
  }

  @Test
  public void testSubscriptionUnsubscribe() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");

    pushChangeTo(subRepo, "master");
    ObjectId subHEADbeforeUnsubscribing = pushChangeTo(subRepo, "master");

    deleteAllSubscriptions(superRepo, "master");
    expectToHaveSubmoduleState(
        superRepo, "master", "subscribed-to-project", subHEADbeforeUnsubscribing);

    pushChangeTo(superRepo, "refs/heads/master", "commit after unsubscribe", "");
    pushChangeTo(subRepo, "refs/heads/master", "commit after unsubscribe", "");
    expectToHaveSubmoduleState(
        superRepo, "master", "subscribed-to-project", subHEADbeforeUnsubscribing);
  }

  @Test
  public void testSubscriptionUnsubscribeByDeletingGitModules() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");

    pushChangeTo(subRepo, "master");
    ObjectId subHEADbeforeUnsubscribing = pushChangeTo(subRepo, "master");

    deleteGitModulesFile(superRepo, "master");
    expectToHaveSubmoduleState(
        superRepo, "master", "subscribed-to-project", subHEADbeforeUnsubscribing);

    pushChangeTo(superRepo, "refs/heads/master", "commit after unsubscribe", "");
    pushChangeTo(subRepo, "refs/heads/master", "commit after unsubscribe", "");
    expectToHaveSubmoduleState(
        superRepo, "master", "subscribed-to-project", subHEADbeforeUnsubscribing);
  }

  @Test
  public void testSubscriptionToDifferentBranches() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/foo", "super-project", "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "foo");
    ObjectId subFoo = pushChangeTo(subRepo, "foo");
    pushChangeTo(subRepo, "master");

    expectToHaveSubmoduleState(superRepo, "master", "subscribed-to-project", subFoo);
  }

  @Test
  public void testBranchCircularSubscription() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription(
        "super-project", "refs/heads/master", "subscribed-to-project", "refs/heads/master");

    pushChangeTo(subRepo, "master");
    pushChangeTo(superRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    createSubmoduleSubscription(subRepo, "master", "super-project", "master");

    pushChangeTo(subRepo, "master");
    pushChangeTo(superRepo, "master");

    assertThat(hasSubmodule(subRepo, "master", "super-project")).isFalse();
    assertThat(hasSubmodule(superRepo, "master", "subscribed-to-project")).isFalse();
  }

  @Test
  public void testProjectCircularSubscription() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription(
        "super-project", "refs/heads/dev", "subscribed-to-project", "refs/heads/dev");

    pushChangeTo(subRepo, "master");
    pushChangeTo(superRepo, "master");
    pushChangeTo(subRepo, "dev");
    pushChangeTo(superRepo, "dev");

    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    createSubmoduleSubscription(subRepo, "dev", "super-project", "dev");

    ObjectId subMasterHead = pushChangeTo(subRepo, "master");
    ObjectId superDevHead = pushChangeTo(superRepo, "dev");

    assertThat(hasSubmodule(superRepo, "master", "subscribed-to-project")).isTrue();
    assertThat(hasSubmodule(subRepo, "dev", "super-project")).isTrue();
    expectToHaveSubmoduleState(superRepo, "master", "subscribed-to-project", subMasterHead);
    expectToHaveSubmoduleState(subRepo, "dev", "super-project", superDevHead);
  }

  @Test
  public void testSubscriptionFailOnMissingACL() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", "subscribed-to-project")).isFalse();
  }

  @Test
  public void testSubscriptionFailOnWrongProjectACL() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "wrong-super-project", "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", "subscribed-to-project")).isFalse();
  }

  @Test
  public void testSubscriptionFailOnWrongBranchACL() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/wrong-branch");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", "subscribed-to-project")).isFalse();
  }

  @Test
  public void testSubscriptionInheritACL() throws Exception {
    createProjectWithPush("config-repo");
    createProjectWithPush("config-repo2", new Project.NameKey(name("config-repo")));
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo =
        createProjectWithPush("subscribed-to-project", new Project.NameKey(name("config-repo2")));
    allowMatchingSubmoduleSubscription(
        "config-repo", "refs/heads/*", "super-project", "refs/heads/*");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master", "subscribed-to-project", subHEAD);
  }

  @Test
  public void testAllowedButNotSubscribed() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/master");

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

    assertThat(hasSubmodule(superRepo, "master", "subscribed-to-project")).isFalse();
  }

  @Test
  public void testSubscriptionDeepRelative() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("nested/subscribed-to-project");
    // master is allowed to be subscribed to any superprojects branch:
    allowMatchingSubmoduleSubscription(
        "nested/subscribed-to-project", "refs/heads/master", "super-project", null);

    pushChangeTo(subRepo, "master");
    createRelativeSubmoduleSubscription(
        superRepo, "master", "../", "nested/subscribed-to-project", "master");

    ObjectId subHEAD = pushChangeTo(subRepo, "master");

    expectToHaveSubmoduleState(superRepo, "master", "nested/subscribed-to-project", subHEAD);
  }
}
