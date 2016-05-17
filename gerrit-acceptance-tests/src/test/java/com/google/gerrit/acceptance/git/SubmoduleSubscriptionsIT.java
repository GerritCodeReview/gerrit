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

import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.testutil.ConfigSuite;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

@NoHttpd
public class SubmoduleSubscriptionsIT extends AbstractSubmoduleSubscription {
  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Test
  @GerritConfig(name = "submodule.enableSuperProjectSubscriptions", value = "false")
  public void testSubscriptionWithoutServerSetting() throws Exception {
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
    allowSubmoduleSubscription("subscribed-to-project", "refs/heads/master",
        "super-project", "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    pushChangeTo(subRepo, "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEAD);
  }

  @Test
  public void testSubscriptionToExistingRepo() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowSubmoduleSubscription("subscribed-to-project", "refs/heads/master",
        "super-project", "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEAD);
  }

  @Test
  public void testSubscriptionWildcardACLForSingleBranch() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    // master is allowed to be subscribed to any superprojects branch:
    allowSubmoduleSubscription("subscribed-to-project", "refs/heads/master",
        "super-project", null);
    // create 'branch':
    pushChangeTo(superRepo, "branch");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    createSubmoduleSubscription(superRepo, "branch", "subscribed-to-project", "master");

    ObjectId subHEAD = pushChangeTo(subRepo, "master");

    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEAD);
    expectToHaveSubmoduleState(superRepo, "branch",
        "subscribed-to-project", subHEAD);
  }

  @Test
  public void testSubscriptionWildcardACLOneOnOneMapping() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    // any branch is allowed to be subscribed to the same superprojects branch:
    allowSubmoduleSubscription("subscribed-to-project", "refs/heads/*",
        "super-project", "refs/heads/*");

    // create 'branch' in both repos:
    pushChangeTo(superRepo, "branch");
    pushChangeTo(subRepo, "branch");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    createSubmoduleSubscription(superRepo, "branch", "subscribed-to-project", "branch");

    ObjectId subHEAD1 = pushChangeTo(subRepo, "master");
    ObjectId subHEAD2 = pushChangeTo(subRepo, "branch");

    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEAD1);
    expectToHaveSubmoduleState(superRepo, "branch",
        "subscribed-to-project", subHEAD2);

    // Now test that cross subscriptions do not work:
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "branch");
    ObjectId subHEAD3 = pushChangeTo(subRepo, "branch");

    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEAD1);
    expectToHaveSubmoduleState(superRepo, "branch",
        "subscribed-to-project", subHEAD3);
  }

  @Test
  @GerritConfig(name = "submodule.verboseSuperprojectUpdate", value = "false")
  public void testSubmoduleShortCommitMessage() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowSubmoduleSubscription("subscribed-to-project", "refs/heads/master",
        "super-project", "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");

    // The first update doesn't include any commit messages
    ObjectId subRepoId = pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subRepoId);
    expectToHaveCommitMessage(superRepo, "master",
        "Update git submodules\n\n");

    // Any following update also has a short message
    subRepoId = pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subRepoId);
    expectToHaveCommitMessage(superRepo, "master",
        "Update git submodules\n\n");
  }

  @Test
  public void testSubmoduleCommitMessage() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowSubmoduleSubscription("subscribed-to-project", "refs/heads/master",
        "super-project", "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");

    // The first update doesn't include the rev log
    RevWalk rw = subRepo.getRevWalk();
    RevCommit subCommitMsg = rw.parseCommit(subHEAD);
    expectToHaveCommitMessage(superRepo, "master",
        "Update git submodules\n\n" +
        "Project: " + name("subscribed-to-project")
            + " master " + subHEAD.name() + "\n\n");

    // The next commit should generate only its commit message,
    // omitting previous commit logs
    subHEAD = pushChangeTo(subRepo, "master");
    subCommitMsg = rw.parseCommit(subHEAD);
    expectToHaveCommitMessage(superRepo, "master",
        "Update git submodules\n\n" +
        "Project: " + name("subscribed-to-project")
            + " master " + subHEAD.name() + "\n\n" +
        subCommitMsg.getFullMessage() + "\n\n");
  }

  @Test
  public void testSubscriptionUnsubscribe() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowSubmoduleSubscription("subscribed-to-project", "refs/heads/master",
        "super-project", "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");

    pushChangeTo(subRepo, "master");
    ObjectId subHEADbeforeUnsubscribing = pushChangeTo(subRepo, "master");

    deleteAllSubscriptions(superRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEADbeforeUnsubscribing);

    pushChangeTo(superRepo, "refs/heads/master",
        "commit after unsubscribe", "");
    pushChangeTo(subRepo, "refs/heads/master",
        "commit after unsubscribe", "");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEADbeforeUnsubscribing);
  }

  @Test
  public void testSubscriptionUnsubscribeByDeletingGitModules() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowSubmoduleSubscription("subscribed-to-project", "refs/heads/master",
        "super-project", "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");

    pushChangeTo(subRepo, "master");
    ObjectId subHEADbeforeUnsubscribing = pushChangeTo(subRepo, "master");

    deleteGitModulesFile(superRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEADbeforeUnsubscribing);

    pushChangeTo(superRepo, "refs/heads/master",
        "commit after unsubscribe", "");
    pushChangeTo(subRepo, "refs/heads/master",
        "commit after unsubscribe", "");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEADbeforeUnsubscribing);
  }

  @Test
  public void testSubscriptionToDifferentBranches() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowSubmoduleSubscription("subscribed-to-project", "refs/heads/foo",
        "super-project", "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "foo");
    ObjectId subFoo = pushChangeTo(subRepo, "foo");
    pushChangeTo(subRepo, "master");

    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subFoo);
  }

  @Test
  public void testCircularSubscriptionIsDetected() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowSubmoduleSubscription("subscribed-to-project", "refs/heads/master",
        "super-project", "refs/heads/master");
    allowSubmoduleSubscription("super-project", "refs/heads/master",
        "subscribed-to-project", "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    createSubmoduleSubscription(subRepo, "master", "super-project", "master");

    ObjectId subHEAD = pushChangeTo(subRepo, "master");
    pushChangeTo(superRepo, "master");

    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEAD);

    assertThat(hasSubmodule(subRepo, "master", "super-project")).isFalse();
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
    allowSubmoduleSubscription("subscribed-to-project", "refs/heads/master",
        "wrong-super-project", "refs/heads/master");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", "subscribed-to-project")).isFalse();
  }

  @Test
  public void testSubscriptionFailOnWrongBranchACL() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowSubmoduleSubscription("subscribed-to-project", "refs/heads/master",
        "super-project", "refs/heads/wrong-branch");

    pushChangeTo(subRepo, "master");
    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    pushChangeTo(subRepo, "master");
    assertThat(hasSubmodule(superRepo, "master", "subscribed-to-project")).isFalse();
  }

  private void deleteAllSubscriptions(TestRepository<?> repo, String branch)
      throws Exception {
    repo.git().fetch().setRemote("origin").call();
    repo.reset("refs/remotes/origin/" + branch);

    ObjectId expectedId = repo.branch("HEAD").commit().insertChangeId()
      .message("delete contents in .gitmodules")
      .add(".gitmodules", "") // Just remove the contents of the file!
      .create();
    repo.git().push().setRemote("origin").setRefSpecs(
      new RefSpec("HEAD:refs/heads/" + branch)).call();

    ObjectId actualId = repo.git().fetch().setRemote("origin").call()
      .getAdvertisedRef("refs/heads/master").getObjectId();
    assertThat(actualId).isEqualTo(expectedId);
  }

  private void deleteGitModulesFile(TestRepository<?> repo, String branch)
      throws Exception {
    repo.git().fetch().setRemote("origin").call();
    repo.reset("refs/remotes/origin/" + branch);

    ObjectId expectedId = repo.branch("HEAD").commit().insertChangeId()
      .message("delete .gitmodules")
      .rm(".gitmodules")
      .create();
    repo.git().push().setRemote("origin").setRefSpecs(
      new RefSpec("HEAD:refs/heads/" + branch)).call();

    ObjectId actualId = repo.git().fetch().setRemote("origin").call()
      .getAdvertisedRef("refs/heads/master").getObjectId();
    assertThat(actualId).isEqualTo(expectedId);
  }

  private boolean hasSubmodule(TestRepository<?> repo, String branch,
      String submodule) throws Exception {

    ObjectId commitId = repo.git().fetch().setRemote("origin").call()
        .getAdvertisedRef("refs/heads/" + branch).getObjectId();

    RevWalk rw = repo.getRevWalk();
    RevCommit c = rw.parseCommit(commitId);
    rw.parseBody(c.getTree());

    RevTree tree = c.getTree();
    try {
      repo.get(tree, submodule);
      return true;
    } catch (AssertionError e) {
      return false;
    }
  }

  private void expectToHaveCommitMessage(TestRepository<?> repo,
      String branch, String expectedMessage) throws Exception {

    ObjectId commitId = repo.git().fetch().setRemote("origin").call()
        .getAdvertisedRef("refs/heads/" + branch).getObjectId();

    RevWalk rw = repo.getRevWalk();
    RevCommit c = rw.parseCommit(commitId);
    assertThat(c.getFullMessage()).isEqualTo(expectedMessage);
  }
}
