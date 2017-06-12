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
import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.GitUtil.getChangeId;

import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.testutil.ConfigSuite;
import java.util.Map;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

@NoHttpd
public class SubmoduleSubscriptionsWholeTopicMergeIT extends AbstractSubmoduleSubscription {

  @ConfigSuite.Default
  public static Config mergeIfNecessary() {
    return submitByMergeIfNecessary();
  }

  @ConfigSuite.Config
  public static Config mergeAlways() {
    return submitByMergeAlways();
  }

  @ConfigSuite.Config
  public static Config cherryPick() {
    return submitByCherryPickConfig();
  }

  @ConfigSuite.Config
  public static Config rebaseAlways() {
    return submitByRebaseAlwaysConfig();
  }

  @ConfigSuite.Config
  public static Config rebaseIfNecessary() {
    return submitByRebaseIfNecessaryConfig();
  }

  @Test
  public void testSubscriptionUpdateOfManyChanges() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");

    ObjectId subHEAD =
        subRepo
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .message("some change")
            .add("a.txt", "a contents ")
            .create();
    subRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    RevCommit c = subRepo.getRevWalk().parseCommit(subHEAD);

    RevCommit c1 =
        subRepo
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .message("first change")
            .add("asdf", "asdf\n")
            .create();
    subRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/for/master/" + name("topic-foo")))
        .call();

    subRepo.reset(c.getId());
    RevCommit c2 =
        subRepo
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .message("qwerty")
            .add("qwerty", "qwerty")
            .create();

    RevCommit c3 =
        subRepo
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .message("qwerty followup")
            .add("qwerty", "qwerty\nqwerty\n")
            .create();
    subRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/for/master/" + name("topic-foo")))
        .call();

    String id1 = getChangeId(subRepo, c1).get();
    String id2 = getChangeId(subRepo, c2).get();
    String id3 = getChangeId(subRepo, c3).get();
    gApi.changes().id(id1).current().review(ReviewInput.approve());
    gApi.changes().id(id2).current().review(ReviewInput.approve());
    gApi.changes().id(id3).current().review(ReviewInput.approve());

    BinaryResult request = gApi.changes().id(id1).current().submitPreview();
    Map<Branch.NameKey, RevTree> preview = fetchFromBundles(request);

    gApi.changes().id(id1).current().submit();
    ObjectId subRepoId =
        subRepo
            .git()
            .fetch()
            .setRemote("origin")
            .call()
            .getAdvertisedRef("refs/heads/master")
            .getObjectId();

    expectToHaveSubmoduleState(superRepo, "master", "subscribed-to-project", subRepoId);

    // As the submodules have changed commits, the superproject tree will be
    // different, so we cannot directly compare the trees here, so make
    // assumptions only about the changed branches:
    Project.NameKey p1 = new Project.NameKey(name("super-project"));
    Project.NameKey p2 = new Project.NameKey(name("subscribed-to-project"));
    assertThat(preview).containsKey(new Branch.NameKey(p1, "refs/heads/master"));
    assertThat(preview).containsKey(new Branch.NameKey(p2, "refs/heads/master"));

    if ((getSubmitType() == SubmitType.CHERRY_PICK)
        || (getSubmitType() == SubmitType.REBASE_ALWAYS)) {
      // each change is updated and the respective target branch is updated:
      assertThat(preview).hasSize(5);
    } else if ((getSubmitType() == SubmitType.REBASE_IF_NECESSARY)) {
      // Either the first is used first as is, then the second and third need
      // rebasing, or those two stay as is and the first is rebased.
      // add in 2 master branches, expect 3 or 4:
      assertThat(preview.size()).isAnyOf(3, 4);
    } else {
      assertThat(preview).hasSize(2);
    }
  }

  @Test
  public void testSubscriptionUpdateIncludingChangeInSuperproject() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");

    ObjectId subHEAD =
        subRepo
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .message("some change")
            .add("a.txt", "a contents ")
            .create();
    subRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/heads/master"))
        .call();

    RevCommit c = subRepo.getRevWalk().parseCommit(subHEAD);

    RevCommit c1 =
        subRepo
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .message("first change")
            .add("asdf", "asdf\n")
            .create();
    subRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/for/master/" + name("topic-foo")))
        .call();

    subRepo.reset(c.getId());
    RevCommit c2 =
        subRepo
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .message("qwerty")
            .add("qwerty", "qwerty")
            .create();

    RevCommit c3 =
        subRepo
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .message("qwerty followup")
            .add("qwerty", "qwerty\nqwerty\n")
            .create();
    subRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/for/master/" + name("topic-foo")))
        .call();

    RevCommit c4 =
        superRepo
            .branch("HEAD")
            .commit()
            .insertChangeId()
            .message("new change on superproject")
            .add("foo", "bar")
            .create();
    superRepo
        .git()
        .push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/for/master/" + name("topic-foo")))
        .call();

    String id1 = getChangeId(subRepo, c1).get();
    String id2 = getChangeId(subRepo, c2).get();
    String id3 = getChangeId(subRepo, c3).get();
    String id4 = getChangeId(superRepo, c4).get();
    gApi.changes().id(id1).current().review(ReviewInput.approve());
    gApi.changes().id(id2).current().review(ReviewInput.approve());
    gApi.changes().id(id3).current().review(ReviewInput.approve());
    gApi.changes().id(id4).current().review(ReviewInput.approve());

    gApi.changes().id(id1).current().submit();
    ObjectId subRepoId =
        subRepo
            .git()
            .fetch()
            .setRemote("origin")
            .call()
            .getAdvertisedRef("refs/heads/master")
            .getObjectId();

    expectToHaveSubmoduleState(superRepo, "master", "subscribed-to-project", subRepoId);
  }

  @Test
  public void testUpdateManySubmodules() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> sub1 = createProjectWithPush("sub1");
    TestRepository<?> sub2 = createProjectWithPush("sub2");
    TestRepository<?> sub3 = createProjectWithPush("sub3");

    allowMatchingSubmoduleSubscription(
        "sub1", "refs/heads/master", "super-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription(
        "sub2", "refs/heads/master", "super-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription(
        "sub3", "refs/heads/master", "super-project", "refs/heads/master");

    Config config = new Config();
    prepareSubmoduleConfigEntry(config, "sub1", "master");
    prepareSubmoduleConfigEntry(config, "sub2", "master");
    prepareSubmoduleConfigEntry(config, "sub3", "master");
    pushSubmoduleConfig(superRepo, "master", config);

    ObjectId superPreviousId = pushChangeTo(superRepo, "master");

    ObjectId sub1Id = pushChangeTo(sub1, "refs/for/master", "some message", "same-topic");
    ObjectId sub2Id = pushChangeTo(sub2, "refs/for/master", "some message", "same-topic");
    ObjectId sub3Id = pushChangeTo(sub3, "refs/for/master", "some message", "same-topic");

    approve(getChangeId(sub1, sub1Id).get());
    approve(getChangeId(sub2, sub2Id).get());
    approve(getChangeId(sub3, sub3Id).get());

    gApi.changes().id(getChangeId(sub1, sub1Id).get()).current().submit();

    expectToHaveSubmoduleState(superRepo, "master", "sub1", sub1, "master");
    expectToHaveSubmoduleState(superRepo, "master", "sub2", sub2, "master");
    expectToHaveSubmoduleState(superRepo, "master", "sub3", sub3, "master");

    superRepo
        .git()
        .fetch()
        .setRemote("origin")
        .call()
        .getAdvertisedRef("refs/heads/master")
        .getObjectId();

    assertWithMessage("submodule subscription update " + "should have made one commit")
        .that(superRepo.getRepository().resolve("origin/master^"))
        .isEqualTo(superPreviousId);
  }

  @Test
  public void testDoNotUseFastForward() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project", false);
    TestRepository<?> sub = createProjectWithPush("sub", false);

    allowMatchingSubmoduleSubscription(
        "sub", "refs/heads/master", "super-project", "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", "sub", "master");

    ObjectId subId = pushChangeTo(sub, "refs/for/master", "some message", "same-topic");

    ObjectId superId = pushChangeTo(superRepo, "refs/for/master", "some message", "same-topic");

    String subChangeId = getChangeId(sub, subId).get();
    approve(subChangeId);
    approve(getChangeId(superRepo, superId).get());

    gApi.changes().id(subChangeId).current().submit();

    expectToHaveSubmoduleState(superRepo, "master", "sub", sub, "master");
    RevCommit superHead = getRemoteHead(name("super-project"), "master");
    assertThat(superHead.getShortMessage()).contains("some message");
    assertThat(superHead.getId()).isNotEqualTo(superId);
  }

  @Test
  public void testUseFastForwardWhenNoSubmodule() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project", false);
    TestRepository<?> sub = createProjectWithPush("sub", false);

    ObjectId subId = pushChangeTo(sub, "refs/for/master", "some message", "same-topic");

    ObjectId superId = pushChangeTo(superRepo, "refs/for/master", "some message", "same-topic");

    String subChangeId = getChangeId(sub, subId).get();
    approve(subChangeId);
    approve(getChangeId(superRepo, superId).get());

    gApi.changes().id(subChangeId).current().submit();

    RevCommit superHead = getRemoteHead(name("super-project"), "master");
    assertThat(superHead.getShortMessage()).isEqualTo("some message");
    assertThat(superHead.getId()).isEqualTo(superId);
  }

  @Test
  public void testSameProjectSameBranchDifferentPaths() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> sub = createProjectWithPush("sub");

    allowMatchingSubmoduleSubscription(
        "sub", "refs/heads/master", "super-project", "refs/heads/master");

    Config config = new Config();
    prepareSubmoduleConfigEntry(config, "sub", "master");
    prepareSubmoduleConfigEntry(config, "sub", "sub-copy", "master");
    pushSubmoduleConfig(superRepo, "master", config);

    ObjectId superPreviousId = pushChangeTo(superRepo, "master");

    ObjectId subId = pushChangeTo(sub, "refs/for/master", "some message", "");

    approve(getChangeId(sub, subId).get());

    gApi.changes().id(getChangeId(sub, subId).get()).current().submit();

    expectToHaveSubmoduleState(superRepo, "master", "sub", sub, "master");
    expectToHaveSubmoduleState(superRepo, "master", "sub-copy", sub, "master");

    superRepo
        .git()
        .fetch()
        .setRemote("origin")
        .call()
        .getAdvertisedRef("refs/heads/master")
        .getObjectId();

    assertWithMessage("submodule subscription update " + "should have made one commit")
        .that(superRepo.getRepository().resolve("origin/master^"))
        .isEqualTo(superPreviousId);
  }

  @Test
  public void testSameProjectDifferentBranchDifferentPaths() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> sub = createProjectWithPush("sub");

    allowMatchingSubmoduleSubscription(
        "sub", "refs/heads/master", "super-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription(
        "sub", "refs/heads/dev", "super-project", "refs/heads/master");

    ObjectId devHead = pushChangeTo(sub, "dev");
    Config config = new Config();
    prepareSubmoduleConfigEntry(config, "sub", "sub-master", "master");
    prepareSubmoduleConfigEntry(config, "sub", "sub-dev", "dev");
    pushSubmoduleConfig(superRepo, "master", config);

    ObjectId subMasterId =
        pushChangeTo(sub, "refs/for/master", "some message", "b.txt", "content b", "same-topic");

    sub.reset(devHead);
    ObjectId subDevId =
        pushChangeTo(
            sub, "refs/for/dev", "some message in dev", "b.txt", "content b", "same-topic");

    approve(getChangeId(sub, subMasterId).get());
    approve(getChangeId(sub, subDevId).get());

    ObjectId superPreviousId = pushChangeTo(superRepo, "master");

    gApi.changes().id(getChangeId(sub, subMasterId).get()).current().submit();

    expectToHaveSubmoduleState(superRepo, "master", "sub-master", sub, "master");
    expectToHaveSubmoduleState(superRepo, "master", "sub-dev", sub, "dev");

    superRepo
        .git()
        .fetch()
        .setRemote("origin")
        .call()
        .getAdvertisedRef("refs/heads/master")
        .getObjectId();

    assertWithMessage("submodule subscription update " + "should have made one commit")
        .that(superRepo.getRepository().resolve("origin/master^"))
        .isEqualTo(superPreviousId);
  }

  @Test
  public void testNonSubmoduleInSameTopic() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> sub = createProjectWithPush("sub");
    TestRepository<?> standAlone = createProjectWithPush("standalone");

    allowMatchingSubmoduleSubscription(
        "sub", "refs/heads/master", "super-project", "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", "sub", "master");

    ObjectId superPreviousId = pushChangeTo(superRepo, "master");

    ObjectId subId = pushChangeTo(sub, "refs/for/master", "some message", "same-topic");
    ObjectId standAloneId =
        pushChangeTo(standAlone, "refs/for/master", "some message", "same-topic");

    String subChangeId = getChangeId(sub, subId).get();
    String standAloneChangeId = getChangeId(standAlone, standAloneId).get();
    approve(subChangeId);
    approve(standAloneChangeId);

    gApi.changes().id(subChangeId).current().submit();

    expectToHaveSubmoduleState(superRepo, "master", "sub", sub, "master");

    ChangeStatus status = gApi.changes().id(standAloneChangeId).info().status;
    assertThat(status).isEqualTo(ChangeStatus.MERGED);

    superRepo
        .git()
        .fetch()
        .setRemote("origin")
        .call()
        .getAdvertisedRef("refs/heads/master")
        .getObjectId();

    assertWithMessage("submodule subscription update " + "should have made one commit")
        .that(superRepo.getRepository().resolve("origin/master^"))
        .isEqualTo(superPreviousId);
  }

  @Test
  public void testRecursiveSubmodules() throws Exception {
    TestRepository<?> topRepo = createProjectWithPush("top-project");
    TestRepository<?> midRepo = createProjectWithPush("mid-project");
    TestRepository<?> bottomRepo = createProjectWithPush("bottom-project");

    allowMatchingSubmoduleSubscription(
        "mid-project", "refs/heads/master", "top-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription(
        "bottom-project", "refs/heads/master", "mid-project", "refs/heads/master");

    createSubmoduleSubscription(topRepo, "master", "mid-project", "master");
    createSubmoduleSubscription(midRepo, "master", "bottom-project", "master");

    ObjectId bottomHead = pushChangeTo(bottomRepo, "refs/for/master", "some message", "same-topic");
    ObjectId topHead = pushChangeTo(topRepo, "refs/for/master", "some message", "same-topic");

    String id1 = getChangeId(bottomRepo, bottomHead).get();
    String id2 = getChangeId(topRepo, topHead).get();

    gApi.changes().id(id1).current().review(ReviewInput.approve());
    gApi.changes().id(id2).current().review(ReviewInput.approve());

    gApi.changes().id(id1).current().submit();

    expectToHaveSubmoduleState(midRepo, "master", "bottom-project", bottomRepo, "master");
    expectToHaveSubmoduleState(topRepo, "master", "mid-project", midRepo, "master");
  }

  @Test
  public void testTriangleSubmodules() throws Exception {
    TestRepository<?> topRepo = createProjectWithPush("top-project");
    TestRepository<?> midRepo = createProjectWithPush("mid-project");
    TestRepository<?> bottomRepo = createProjectWithPush("bottom-project");

    allowMatchingSubmoduleSubscription(
        "mid-project", "refs/heads/master", "top-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription(
        "bottom-project", "refs/heads/master", "mid-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription(
        "bottom-project", "refs/heads/master", "top-project", "refs/heads/master");

    createSubmoduleSubscription(midRepo, "master", "bottom-project", "master");
    Config config = new Config();
    prepareSubmoduleConfigEntry(config, "bottom-project", "master");
    prepareSubmoduleConfigEntry(config, "mid-project", "master");
    pushSubmoduleConfig(topRepo, "master", config);

    ObjectId bottomHead = pushChangeTo(bottomRepo, "refs/for/master", "some message", "same-topic");
    ObjectId topHead = pushChangeTo(topRepo, "refs/for/master", "some message", "same-topic");

    String id1 = getChangeId(bottomRepo, bottomHead).get();
    String id2 = getChangeId(topRepo, topHead).get();

    gApi.changes().id(id1).current().review(ReviewInput.approve());
    gApi.changes().id(id2).current().review(ReviewInput.approve());

    gApi.changes().id(id1).current().submit();

    expectToHaveSubmoduleState(midRepo, "master", "bottom-project", bottomRepo, "master");
    expectToHaveSubmoduleState(topRepo, "master", "mid-project", midRepo, "master");
    expectToHaveSubmoduleState(topRepo, "master", "bottom-project", bottomRepo, "master");
  }

  private String prepareBranchCircularSubscription() throws Exception {
    TestRepository<?> topRepo = createProjectWithPush("top-project");
    TestRepository<?> midRepo = createProjectWithPush("mid-project");
    TestRepository<?> bottomRepo = createProjectWithPush("bottom-project");

    createSubmoduleSubscription(midRepo, "master", "bottom-project", "master");
    createSubmoduleSubscription(topRepo, "master", "mid-project", "master");
    createSubmoduleSubscription(bottomRepo, "master", "top-project", "master");

    allowMatchingSubmoduleSubscription(
        "bottom-project", "refs/heads/master", "mid-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription(
        "mid-project", "refs/heads/master", "top-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription(
        "top-project", "refs/heads/master", "bottom-project", "refs/heads/master");

    ObjectId bottomMasterHead = pushChangeTo(bottomRepo, "refs/for/master", "some message", "");
    String changeId = getChangeId(bottomRepo, bottomMasterHead).get();

    approve(changeId);
    exception.expectMessage("Branch level circular subscriptions detected");
    exception.expectMessage("top-project,refs/heads/master");
    exception.expectMessage("mid-project,refs/heads/master");
    exception.expectMessage("bottom-project,refs/heads/master");
    return changeId;
  }

  @Test
  public void testBranchCircularSubscription() throws Exception {
    String changeId = prepareBranchCircularSubscription();
    gApi.changes().id(changeId).current().submit();
  }

  @Test
  public void testBranchCircularSubscriptionPreview() throws Exception {
    String changeId = prepareBranchCircularSubscription();
    gApi.changes().id(changeId).current().submitPreview();
  }

  @Test
  public void testProjectCircularSubscriptionWholeTopic() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    allowMatchingSubmoduleSubscription(
        "subscribed-to-project", "refs/heads/master", "super-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription(
        "super-project", "refs/heads/dev", "subscribed-to-project", "refs/heads/dev");

    pushChangeTo(subRepo, "dev");
    pushChangeTo(superRepo, "dev");

    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");
    createSubmoduleSubscription(subRepo, "dev", "super-project", "dev");

    ObjectId subMasterHead =
        pushChangeTo(
            subRepo, "refs/for/master", "b.txt", "content b", "some message", "same-topic");
    ObjectId superDevHead = pushChangeTo(superRepo, "refs/for/dev", "some message", "same-topic");

    approve(getChangeId(subRepo, subMasterHead).get());
    approve(getChangeId(superRepo, superDevHead).get());

    exception.expectMessage("Project level circular subscriptions detected");
    exception.expectMessage("subscribed-to-project");
    exception.expectMessage("super-project");
    gApi.changes().id(getChangeId(subRepo, subMasterHead).get()).current().submit();

    assertThat(hasSubmodule(superRepo, "master", "subscribed-to-project")).isFalse();
    assertThat(hasSubmodule(subRepo, "dev", "super-project")).isFalse();
  }

  @Test
  public void testProjectNoSubscriptionWholeTopic() throws Exception {
    TestRepository<?> repoA = createProjectWithPush("project-a");
    TestRepository<?> repoB = createProjectWithPush("project-b");
    // bootstrap the dev branch
    ObjectId a0 = pushChangeTo(repoA, "dev");

    // bootstrap the dev branch
    ObjectId b0 = pushChangeTo(repoB, "dev");

    // create a change for master branch in repo a
    ObjectId aHead =
        pushChangeTo(
            repoA,
            "refs/for/master",
            "master.txt",
            "content master A",
            "some message in a master.txt",
            "same-topic");

    // create a change for master branch in repo b
    ObjectId bHead =
        pushChangeTo(
            repoB,
            "refs/for/master",
            "master.txt",
            "content master B",
            "some message in b master.txt",
            "same-topic");

    // create a change for dev branch in repo a
    repoA.reset(a0);
    ObjectId aDevHead =
        pushChangeTo(
            repoA,
            "refs/for/dev",
            "dev.txt",
            "content dev A",
            "some message in a dev.txt",
            "same-topic");

    // create a change for dev branch in repo b
    repoB.reset(b0);
    ObjectId bDevHead =
        pushChangeTo(
            repoB,
            "refs/for/dev",
            "dev.txt",
            "content dev B",
            "some message in b dev.txt",
            "same-topic");

    approve(getChangeId(repoA, aHead).get());
    approve(getChangeId(repoB, bHead).get());
    approve(getChangeId(repoA, aDevHead).get());
    approve(getChangeId(repoB, bDevHead).get());

    gApi.changes().id(getChangeId(repoA, aDevHead).get()).current().submit();
    assertThat(getRemoteHead(name("project-a"), "refs/heads/master").getShortMessage())
        .contains("some message in a master.txt");
    assertThat(getRemoteHead(name("project-a"), "refs/heads/dev").getShortMessage())
        .contains("some message in a dev.txt");
    assertThat(getRemoteHead(name("project-b"), "refs/heads/master").getShortMessage())
        .contains("some message in b master.txt");
    assertThat(getRemoteHead(name("project-b"), "refs/heads/dev").getShortMessage())
        .contains("some message in b dev.txt");
  }

  @Test
  public void testTwoProjectsMultipleBranchesWholeTopic() throws Exception {
    TestRepository<?> repoA = createProjectWithPush("project-a");
    TestRepository<?> repoB = createProjectWithPush("project-b");
    // bootstrap the dev branch
    pushChangeTo(repoA, "dev");

    // bootstrap the dev branch
    ObjectId b0 = pushChangeTo(repoB, "dev");

    allowMatchingSubmoduleSubscription(
        "project-b", "refs/heads/master", "project-a", "refs/heads/master");
    allowMatchingSubmoduleSubscription(
        "project-b", "refs/heads/dev", "project-a", "refs/heads/dev");

    createSubmoduleSubscription(repoA, "master", "project-b", "master");
    createSubmoduleSubscription(repoA, "dev", "project-b", "dev");

    // create a change for master branch in repo b
    ObjectId bHead =
        pushChangeTo(
            repoB,
            "refs/for/master",
            "master.txt",
            "content master B",
            "some message in b master.txt",
            "same-topic");

    // create a change for dev branch in repo b
    repoB.reset(b0);
    ObjectId bDevHead =
        pushChangeTo(
            repoB,
            "refs/for/dev",
            "dev.txt",
            "content dev B",
            "some message in b dev.txt",
            "same-topic");

    approve(getChangeId(repoB, bHead).get());
    approve(getChangeId(repoB, bDevHead).get());
    gApi.changes().id(getChangeId(repoB, bHead).get()).current().submit();

    expectToHaveSubmoduleState(repoA, "master", "project-b", repoB, "master");
    expectToHaveSubmoduleState(repoA, "dev", "project-b", repoB, "dev");
  }
}
