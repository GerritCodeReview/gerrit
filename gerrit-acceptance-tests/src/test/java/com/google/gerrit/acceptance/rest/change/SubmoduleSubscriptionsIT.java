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

package com.google.gerrit.acceptance.rest.change;

import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.common.truth.Truth.assertThat;
import static org.junit.Assert.fail;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.reviewdb.client.Project;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

public class SubmoduleSubscriptionsIT extends AbstractDaemonTest {

  @Test
  public void testSubscriptionToEmptyRepo() throws Exception {

    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    createSubscription(superRepo, "master", "subscribed-to-project", "master");
    ObjectId subHEAD = pushRandomChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEAD);
  }

  @Test
  public void testSubscriptionToExistingRepo() throws Exception {

    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    pushRandomChangeTo(subRepo, "master");
    createSubscription(superRepo, "master", "subscribed-to-project", "master");
    ObjectId subHEAD = pushRandomChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEAD);
  }

  @Test
  public void testSubscriptionUnsubscribe() throws Exception {

    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    pushRandomChangeTo(subRepo, "master");
    createSubscription(superRepo, "master", "subscribed-to-project", "master");

    pushRandomChangeTo(subRepo, "master");
    pushRandomChangeTo(subRepo, "master");
    ObjectId subHEADbeforeUnsubscribing = pushRandomChangeTo(subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEADbeforeUnsubscribing);

    deleteAllSubscriptions(superRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEADbeforeUnsubscribing);
    pushRandomChangeTo(superRepo, "master");
    pushRandomChangeTo(subRepo, "master");
    pushRandomChangeTo(subRepo, "master");

    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEADbeforeUnsubscribing);
  }

  @Test
  public void testSubscriptionToDifferentBranches() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    pushRandomChangeTo(subRepo, "master");
    createSubscription(superRepo, "master", "subscribed-to-project", "foo-1");
    pushRandomChangeTo(subRepo, "master");
    ObjectId subHEAD = pushRandomChangeTo(subRepo, "foo-1");
    pushRandomChangeTo(subRepo, "master");

    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEAD);
  }

  @Test
  public void testCircularSubscriptionIsDetected() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    pushRandomChangeTo(subRepo, "master");
    createSubscription(superRepo, "master", "subscribed-to-project", "master");
    createSubscription(subRepo, "master", "super-project", "master");

    ObjectId subHEAD = pushRandomChangeTo(subRepo, "master");
    pushRandomChangeTo(superRepo, "master");

    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subHEAD);

    expectToHaveNoSubmodule(subRepo, "master", "super-project");
  }

  private TestRepository<?> createProjectWithPush(String name)
      throws Exception {
    Project.NameKey project = new Project.NameKey(name);
    createProject(project.get());
    grant(Permission.PUSH, project, "refs/heads/*");
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/*");
    return cloneProject(project, sshSession);
  }

  private ObjectId pushRandomChangeTo(TestRepository<?> repo, String branch)
      throws Exception {
    repo.branch("HEAD").commit().insertChangeId()
      .message("first change")
      .add("a.txt", "a contents")
      .create();
    repo.git().push().setRemote("origin").setRefSpecs(
        new RefSpec("HEAD:refs/heads/" + branch)).call();

    return repo.getRepository().resolve("HEAD");
  }

  private void createSubscription(TestRepository<?> repo,
      String branch,
      String subscribeToRepo,
      String subscribeToBranch)
          throws Exception {

    String url = cfg.getString("gerrit", null, "canonicalWebUrl") + "/" + subscribeToRepo;
    String content = buildSubmoduleSection(subscribeToRepo, subscribeToRepo, url, subscribeToBranch).toString();
    repo.branch("HEAD").commit().insertChangeId()
      .message("subject: adding new subscription")
      .add(".gitmodules", content)
      .create();

    repo.git().push().setRemote("origin").setRefSpecs(
        new RefSpec("HEAD:refs/heads/" + branch)).call();
  }

  private void deleteAllSubscriptions(TestRepository<?> repo, String branch)
      throws Exception {
    repo.branch("HEAD").commit().insertChangeId()
      .message("delete contents in .gitmodules")
      .add(".gitmodules", "")
      .create();
    repo.git().push().setRemote("origin").setRefSpecs(
      new RefSpec("HEAD:refs/heads/" + branch)).call();
  }

  private void expectToHaveSubmoduleState(TestRepository<?> repo, String branch,
      String submodule, ObjectId expectedId) throws Exception {

    ObjectId commitId = repo.git().fetch().setRemote("origin").call()
        .getAdvertisedRef("refs/heads/" + branch).getObjectId();

    RevWalk rw = repo.getRevWalk();
    RevCommit c = rw.parseCommit(commitId);
    rw.parseBody(c.getTree());

    RevTree tree = c.getTree();
    RevObject actualId = repo.get(tree, submodule);

    assertThat(actualId).isEqualTo(expectedId);
  }

  private void expectToHaveNoSubmodule(TestRepository<?> repo, String branch,
      String submodule) throws Exception {

    ObjectId commitId = repo.git().fetch().setRemote("origin").call()
        .getAdvertisedRef("refs/heads/" + branch).getObjectId();

    RevWalk rw = repo.getRevWalk();
    RevCommit c = rw.parseCommit(commitId);
    rw.parseBody(c.getTree());

    RevTree tree = c.getTree();
    try {
      repo.get(tree, submodule);
      fail("circular subscription created");
    } catch (AssertionError e) {
      // expected
    }
  }

  private static final String newLine = System.getProperty("line.separator");
  private static StringBuilder buildSubmoduleSection(String name,
      String path, String url, String branch) {
    final StringBuilder sb = new StringBuilder();

    sb.append("[submodule \"");
    sb.append(name);
    sb.append("\"]");
    sb.append(newLine);

    sb.append("\tpath = ");
    sb.append(path);
    sb.append(newLine);

    sb.append("\turl = ");
    sb.append(url);
    sb.append(newLine);

    sb.append("\tbranch = ");
    sb.append(branch);
    sb.append(newLine);

    return sb;
  }
}
