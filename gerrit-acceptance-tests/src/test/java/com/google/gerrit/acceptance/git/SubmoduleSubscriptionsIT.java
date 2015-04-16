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
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.Project;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

public class SubmoduleSubscriptionsIT extends AbstractDaemonTest {

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

  private TestRepository<?> createProjectWithPush(String name)
      throws Exception {
    Project.NameKey project = createProject(name);
    grant(Permission.PUSH, project, "refs/heads/*");
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/*");
    return cloneProject(project);
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

  private void createSubscription(
      TestRepository<?> repo, String branch, String subscribeToRepo,
      String subscribeToBranch) throws Exception {
    subscribeToRepo = name(subscribeToRepo);

    // The submodule subscription module checks for gerrit.canonicalWebUrl to
    // detect if it's configured for automatic updates. It doesn't matter if
    // it serves from that URL.
    String url = cfg.getString("gerrit", null, "canonicalWebUrl") + "/"
        + subscribeToRepo;
    String content = buildSubmoduleSection(subscribeToRepo, subscribeToRepo,
        url, subscribeToBranch);
    repo.branch("HEAD").commit().insertChangeId()
      .message("subject: adding new subscription")
      .add(".gitmodules", content)
      .create();

    repo.git().push().setRemote("origin").setRefSpecs(
        new RefSpec("HEAD:refs/heads/" + branch)).call();
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

  private void expectToHaveSubmoduleState(TestRepository<?> repo, String branch,
      String submodule, ObjectId expectedId) throws Exception {

    submodule = name(submodule);
    ObjectId commitId = repo.git().fetch().setRemote("origin").call()
        .getAdvertisedRef("refs/heads/" + branch).getObjectId();

    RevWalk rw = repo.getRevWalk();
    RevCommit c = rw.parseCommit(commitId);
    rw.parseBody(c.getTree());

    RevTree tree = c.getTree();
    RevObject actualId = repo.get(tree, submodule);

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

  private static String buildSubmoduleSection(String name,
      String path, String url, String branch) {
    Config cfg = new Config();
    cfg.setString("submodule", name, "path", path);
    cfg.setString("submodule", name, "url", url);
    cfg.setString("submodule", name, "branch", branch);
    return cfg.toText().toString();
  }
}
