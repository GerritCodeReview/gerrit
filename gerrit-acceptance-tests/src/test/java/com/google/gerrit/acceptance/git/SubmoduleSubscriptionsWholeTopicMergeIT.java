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
import com.google.gerrit.testutil.ConfigSuite;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

@NoHttpd
public class SubmoduleSubscriptionsWholeTopicMergeIT
  extends AbstractSubmoduleSubscription {

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
    return submitByCherryPickConifg();
  }

  @ConfigSuite.Config
  public static Config rebase() {
    return submitByRebaseConifg();
  }

  @Test
  public void testSubscriptionUpdateOfManyChanges() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription("subscribed-to-project", "refs/heads/master",
        "super-project", "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");

    ObjectId subHEAD = subRepo.branch("HEAD").commit().insertChangeId()
        .message("some change")
        .add("a.txt", "a contents ")
        .create();
    subRepo.git().push().setRemote("origin").setRefSpecs(
          new RefSpec("HEAD:refs/heads/master")).call();

    RevCommit c = subRepo.getRevWalk().parseCommit(subHEAD);

    RevCommit c1 = subRepo.branch("HEAD").commit().insertChangeId()
      .message("first change")
      .add("asdf", "asdf\n")
      .create();
    subRepo.git().push().setRemote("origin")
      .setRefSpecs(new RefSpec("HEAD:refs/for/master/" + name("topic-foo")))
      .call();

    subRepo.reset(c.getId());
    RevCommit c2 = subRepo.branch("HEAD").commit().insertChangeId()
      .message("qwerty")
      .add("qwerty", "qwerty")
      .create();

    RevCommit c3 = subRepo.branch("HEAD").commit().insertChangeId()
      .message("qwerty followup")
      .add("qwerty", "qwerty\nqwerty\n")
      .create();
    subRepo.git().push().setRemote("origin")
      .setRefSpecs(new RefSpec("HEAD:refs/for/master/" + name("topic-foo")))
      .call();

    String id1 = getChangeId(subRepo, c1).get();
    String id2 = getChangeId(subRepo, c2).get();
    String id3 = getChangeId(subRepo, c3).get();
    gApi.changes().id(id1).current().review(ReviewInput.approve());
    gApi.changes().id(id2).current().review(ReviewInput.approve());
    gApi.changes().id(id3).current().review(ReviewInput.approve());

    gApi.changes().id(id1).current().submit();
    ObjectId subRepoId = subRepo.git().fetch().setRemote("origin").call()
        .getAdvertisedRef("refs/heads/master").getObjectId();

    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subRepoId);
  }

  @Test
  public void testSubscriptionUpdateIncludingChangeInSuperproject() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    allowMatchingSubmoduleSubscription("subscribed-to-project", "refs/heads/master",
        "super-project", "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", "subscribed-to-project", "master");

    ObjectId subHEAD = subRepo.branch("HEAD").commit().insertChangeId()
        .message("some change")
        .add("a.txt", "a contents ")
        .create();
    subRepo.git().push().setRemote("origin").setRefSpecs(
          new RefSpec("HEAD:refs/heads/master")).call();

    RevCommit c = subRepo.getRevWalk().parseCommit(subHEAD);

    RevCommit c1 = subRepo.branch("HEAD").commit().insertChangeId()
      .message("first change")
      .add("asdf", "asdf\n")
      .create();
    subRepo.git().push().setRemote("origin")
      .setRefSpecs(new RefSpec("HEAD:refs/for/master/" + name("topic-foo")))
      .call();

    subRepo.reset(c.getId());
    RevCommit c2 = subRepo.branch("HEAD").commit().insertChangeId()
      .message("qwerty")
      .add("qwerty", "qwerty")
      .create();

    RevCommit c3 = subRepo.branch("HEAD").commit().insertChangeId()
      .message("qwerty followup")
      .add("qwerty", "qwerty\nqwerty\n")
      .create();
    subRepo.git().push().setRemote("origin")
      .setRefSpecs(new RefSpec("HEAD:refs/for/master/" + name("topic-foo")))
      .call();

    RevCommit c4 = superRepo.branch("HEAD").commit().insertChangeId()
      .message("new change on superproject")
      .add("foo", "bar")
      .create();
    superRepo.git().push().setRemote("origin")
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
    ObjectId subRepoId = subRepo.git().fetch().setRemote("origin").call()
        .getAdvertisedRef("refs/heads/master").getObjectId();

    expectToHaveSubmoduleState(superRepo, "master",
        "subscribed-to-project", subRepoId);
  }

  @Test
  public void testUpdateManySubmodules() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> sub1 = createProjectWithPush("sub1");
    TestRepository<?> sub2 = createProjectWithPush("sub2");
    TestRepository<?> sub3 = createProjectWithPush("sub3");

    allowMatchingSubmoduleSubscription("sub1", "refs/heads/master",
        "super-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription("sub2", "refs/heads/master",
        "super-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription("sub3", "refs/heads/master",
        "super-project", "refs/heads/master");

    Config config = new Config();
    prepareSubmoduleConfigEntry(config, "sub1", "master");
    prepareSubmoduleConfigEntry(config, "sub2", "master");
    prepareSubmoduleConfigEntry(config, "sub3", "master");
    pushSubmoduleConfig(superRepo, "master", config);

    ObjectId superPreviousId = pushChangeTo(superRepo, "master");

    ObjectId sub1Id = pushChangeTo(sub1, "refs/for/master",
        "some message", "same-topic");
    ObjectId sub2Id = pushChangeTo(sub2, "refs/for/master",
        "some message", "same-topic");
    ObjectId sub3Id = pushChangeTo(sub3, "refs/for/master",
        "some message", "same-topic");

    approve(getChangeId(sub1, sub1Id).get());
    approve(getChangeId(sub2, sub2Id).get());
    approve(getChangeId(sub3, sub3Id).get());

    gApi.changes().id(getChangeId(sub1, sub1Id).get()).current().submit();

    expectToHaveSubmoduleState(superRepo, "master", "sub1", sub1, "master");
    expectToHaveSubmoduleState(superRepo, "master", "sub2", sub2, "master");
    expectToHaveSubmoduleState(superRepo, "master", "sub3", sub3, "master");

    superRepo.git().fetch().setRemote("origin").call()
      .getAdvertisedRef("refs/heads/master").getObjectId();

    assertWithMessage("submodule subscription update "
        + "should have made one commit")
        .that(superRepo.getRepository().resolve("origin/master^"))
        .isEqualTo(superPreviousId);
  }

  @Test
  public void testDifferentPaths() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> sub = createProjectWithPush("sub");

    allowMatchingSubmoduleSubscription("sub", "refs/heads/master",
        "super-project", "refs/heads/master");

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

    superRepo.git().fetch().setRemote("origin").call()
        .getAdvertisedRef("refs/heads/master").getObjectId();

    assertWithMessage("submodule subscription update "
        + "should have made one commit")
        .that(superRepo.getRepository().resolve("origin/master^"))
        .isEqualTo(superPreviousId);
  }

  @Test
  public void testNonSubmoduleInSameTopic() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> sub = createProjectWithPush("sub");
    TestRepository<?> standAlone = createProjectWithPush("standalone");

    allowMatchingSubmoduleSubscription("sub", "refs/heads/master",
        "super-project", "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", "sub", "master");

    ObjectId superPreviousId = pushChangeTo(superRepo, "master");

    ObjectId subId =
        pushChangeTo(sub, "refs/for/master", "some message", "same-topic");
    ObjectId standAloneId =
        pushChangeTo(standAlone, "refs/for/master", "some message",
            "same-topic");

    String subChangeId = getChangeId(sub, subId).get();
    String standAloneChangeId = getChangeId(standAlone, standAloneId).get();
    approve(subChangeId);
    approve(standAloneChangeId);

    gApi.changes().id(subChangeId).current().submit();

    expectToHaveSubmoduleState(superRepo, "master", "sub", sub, "master");

    ChangeStatus status = gApi.changes().id(standAloneChangeId).info().status;
    assertThat(status).isEqualTo(ChangeStatus.MERGED);

    superRepo.git().fetch().setRemote("origin").call()
        .getAdvertisedRef("refs/heads/master").getObjectId();

    assertWithMessage("submodule subscription update "
        + "should have made one commit")
        .that(superRepo.getRepository().resolve("origin/master^"))
        .isEqualTo(superPreviousId);
  }

  @Test
  public void testRecursiveSubmodules() throws Exception {
    TestRepository<?> topRepo = createProjectWithPush("top-project");
    TestRepository<?> midRepo = createProjectWithPush("mid-project");
    TestRepository<?> bottomRepo = createProjectWithPush("bottom-project");

    allowMatchingSubmoduleSubscription("mid-project", "refs/heads/master",
        "top-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription("bottom-project", "refs/heads/master",
        "mid-project", "refs/heads/master");

    createSubmoduleSubscription(topRepo, "master", "mid-project", "master");
    createSubmoduleSubscription(midRepo, "master", "bottom-project", "master");

    ObjectId bottomHead =
        pushChangeTo(bottomRepo, "refs/for/master", "some message", "same-topic");
    ObjectId topHead =
        pushChangeTo(topRepo, "refs/for/master", "some message", "same-topic");

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

    allowMatchingSubmoduleSubscription("mid-project", "refs/heads/master",
        "top-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription("bottom-project", "refs/heads/master",
        "mid-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription("bottom-project", "refs/heads/master",
        "top-project", "refs/heads/master");

    createSubmoduleSubscription(midRepo, "master", "bottom-project", "master");
    Config config = new Config();
    prepareSubmoduleConfigEntry(config, "bottom-project", "master");
    prepareSubmoduleConfigEntry(config, "mid-project", "master");
    pushSubmoduleConfig(topRepo, "master", config);

    ObjectId bottomHead =
        pushChangeTo(bottomRepo, "refs/for/master", "some message", "same-topic");
    ObjectId topHead =
        pushChangeTo(topRepo, "refs/for/master", "some message", "same-topic");

    String id1 = getChangeId(bottomRepo, bottomHead).get();
    String id2 = getChangeId(topRepo, topHead).get();

    gApi.changes().id(id1).current().review(ReviewInput.approve());
    gApi.changes().id(id2).current().review(ReviewInput.approve());

    gApi.changes().id(id1).current().submit();

    expectToHaveSubmoduleState(midRepo, "master", "bottom-project", bottomRepo, "master");
    expectToHaveSubmoduleState(topRepo, "master", "mid-project", midRepo, "master");
    expectToHaveSubmoduleState(topRepo, "master", "bottom-project", bottomRepo, "master");
  }

  @Test
  public void testBranchCircularSubscription() throws Exception {
    TestRepository<?> topRepo = createProjectWithPush("top-project");
    TestRepository<?> midRepo = createProjectWithPush("mid-project");
    TestRepository<?> bottomRepo = createProjectWithPush("bottom-project");

    createSubmoduleSubscription(midRepo, "master", "bottom-project", "master");
    createSubmoduleSubscription(topRepo, "master", "mid-project", "master");
    createSubmoduleSubscription(bottomRepo, "master", "top-project", "master");

    allowMatchingSubmoduleSubscription("bottom-project", "refs/heads/master",
        "mid-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription("mid-project", "refs/heads/master",
        "top-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription("top-project", "refs/heads/master",
        "bottom-project", "refs/heads/master");

    ObjectId bottomMasterHead =
        pushChangeTo(bottomRepo, "refs/for/master", "some message", "");
    String changeId = getChangeId(bottomRepo, bottomMasterHead).get();

    approve(changeId);

    exception.expectMessage("Branch level circular subscriptions detected");
    exception.expectMessage("top-project,refs/heads/master");
    exception.expectMessage("mid-project,refs/heads/master");
    exception.expectMessage("bottom-project,refs/heads/master");
    gApi.changes().id(changeId).current().submit();

    assertThat(hasSubmodule(midRepo, "master", "bottom-project")).isFalse();
    assertThat(hasSubmodule(topRepo, "master", "mid-project")).isFalse();
  }

  @Test
  public void testProjectCircularSubscriptionWholeTopic() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");

    allowMatchingSubmoduleSubscription("subscribed-to-project", "refs/heads/master",
        "super-project", "refs/heads/master");
    allowMatchingSubmoduleSubscription("super-project", "refs/heads/dev",
        "subscribed-to-project", "refs/heads/dev");

    pushChangeTo(subRepo, "dev");
    pushChangeTo(superRepo, "dev");

    createSubmoduleSubscription(superRepo, "master",
        "subscribed-to-project", "master");
    createSubmoduleSubscription(subRepo, "dev", "super-project", "dev");

    ObjectId subMasterHead =
        pushChangeTo(subRepo, "refs/for/master", "b.txt", "content b",
            "some message", "same-topic");
    ObjectId superDevHead =
        pushChangeTo(superRepo, "refs/for/dev",
            "some message", "same-topic");

    approve(getChangeId(subRepo, subMasterHead).get());
    approve(getChangeId(superRepo, superDevHead).get());

    exception.expectMessage("Project level circular subscriptions detected");
    exception.expectMessage("subscribed-to-project");
    exception.expectMessage("super-project");
    gApi.changes().id(getChangeId(subRepo, subMasterHead).get()).current()
        .submit();

    assertThat(hasSubmodule(superRepo, "master", "subscribed-to-project"))
        .isFalse();
    assertThat(hasSubmodule(subRepo, "dev", "super-project")).isFalse();
  }
}
