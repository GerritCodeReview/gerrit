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

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class SubmoduleSubscriptionsIT extends AbstractSubmoduleSubscription {

  @Test
  public void testSubscriptionToEmptyRepo() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    createSubscription(superRepo, "master", "subscribed-to-project", "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEAD);
  }

  @Test
  public void testSubscriptionToExistingRepo() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    pushChangeTo(subRepo, "master");
    createSubscription(superRepo, "master", "subscribed-to-project", "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEAD);
  }

  @Test
  public void testSubmoduleCommitMessage() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    pushChangeTo(subRepo, "master");
    createSubscription(superRepo, "master", "subscribed-to-project", "master");
    ObjectId subHEAD = pushChangeTo(subRepo, "master");

    RevWalk rw = subRepo.getRevWalk();
    RevCommit subCommitMsg = rw.parseCommit(subHEAD);

    expectToHaveCommitMessage(superRepo, "master",
        "Updated git submodules\n\n" +
        "Project: " + name("subscribed-to-project")
            + "  " + subHEAD.name() + "\n\n" +
        subCommitMsg.getFullMessage() + "\n");
  }

  @Test
  public void testSubscriptionUnsubscribe() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    pushChangeTo(subRepo, "master");
    createSubscription(superRepo, "master", "subscribed-to-project", "master");

    pushChangeTo(subRepo, "master");
    ObjectId subHEADbeforeUnsubscribing = pushChangeTo(subRepo, "master");

    deleteAllSubscriptions(superRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEADbeforeUnsubscribing);

    pushChangeTo(superRepo, "master", "commit after unsubscribe");
    pushChangeTo(subRepo, "master", "commit after unsubscribe");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEADbeforeUnsubscribing);
  }

  @Test
  public void testSubscriptionUnsubscribeByDeletingGitModules() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    pushChangeTo(subRepo, "master");
    createSubscription(superRepo, "master", "subscribed-to-project", "master");

    pushChangeTo(subRepo, "master");
    ObjectId subHEADbeforeUnsubscribing = pushChangeTo(subRepo, "master");

    deleteGitModulesFile(superRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEADbeforeUnsubscribing);

    pushChangeTo(superRepo, "master", "commit after unsubscribe");
    pushChangeTo(subRepo, "master", "commit after unsubscribe");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEADbeforeUnsubscribing);
  }

  @Test
  public void testSubscriptionToDifferentBranches() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    createSubscription(superRepo, "master", "subscribed-to-project", "foo");
    ObjectId subFoo = pushChangeTo(subRepo, "foo");
    pushChangeTo(subRepo, "master");

    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subFoo);
  }

  @Test
  public void testCircularSubscriptionIsDetected() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    pushChangeTo(subRepo, "master");
    createSubscription(superRepo, "master", "subscribed-to-project", "master");
    createSubscription(subRepo, "master", "super-project", "master");

    ObjectId subHEAD = pushChangeTo(subRepo, "master");
    pushChangeTo(superRepo, "master");

    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEAD);

    assertThat(hasSubmodule(subRepo, "master", "super-project")).isFalse();
  }

  private static AtomicInteger contentCounter = new AtomicInteger(0);

  private ObjectId pushChangeTo(TestRepository<?> repo, String branch, String message)
      throws Exception {

    ObjectId ret = repo.branch("HEAD").commit().insertChangeId()
      .message(message)
      .add("a.txt", "a contents: " + contentCounter.addAndGet(1))
      .create();

    repo.git().push().setRemote("origin").setRefSpecs(
        new RefSpec("HEAD:refs/heads/" + branch)).call();

    return ret;
  }

  private ObjectId pushChangeTo(TestRepository<?> repo, String branch)
      throws Exception {
    return pushChangeTo(repo, branch, "some change");
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
