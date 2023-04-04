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
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.testsuite.ThrowingConsumer;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.change.TestSubmitInput;
import com.google.gerrit.testing.ConfigSuite;
import com.google.inject.Inject;
import java.util.ArrayDeque;
import org.apache.commons.lang3.RandomStringUtils;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
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

  @Inject private ProjectOperations projectOperations;

  @Test
  public void subscriptionUpdateOfManyChanges() throws Exception {
    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");
    createSubmoduleSubscription(superRepo, "master", subKey, "master");

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
        .setRefSpecs(new RefSpec("HEAD:refs/for/master%topic=" + name("topic-foo")))
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
        .setRefSpecs(new RefSpec("HEAD:refs/for/master%topic=" + name("topic-foo")))
        .call();

    String id1 = getChangeId(subRepo, c1).get();
    String id2 = getChangeId(subRepo, c2).get();
    String id3 = getChangeId(subRepo, c3).get();
    gApi.changes().id(id1).current().review(ReviewInput.approve());
    gApi.changes().id(id2).current().review(ReviewInput.approve());
    gApi.changes().id(id3).current().review(ReviewInput.approve());

    gApi.changes().id(id1).current().submit();
    ObjectId subRepoId =
        subRepo
            .git()
            .fetch()
            .setRemote("origin")
            .call()
            .getAdvertisedRef("refs/heads/master")
            .getObjectId();

    expectToHaveSubmoduleState(superRepo, "master", subKey, subRepoId);
  }

  @Test
  public void subscriptionUpdateIncludingChangeInSuperproject() throws Exception {
    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", subKey, "master");

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
        .setRefSpecs(new RefSpec("HEAD:refs/for/master%topic=" + name("topic-foo")))
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
        .setRefSpecs(new RefSpec("HEAD:refs/for/master%topic=" + name("topic-foo")))
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
        .setRefSpecs(new RefSpec("HEAD:refs/for/master%topic=" + name("topic-foo")))
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

    expectToHaveSubmoduleState(superRepo, "master", subKey, subRepoId);
  }

  @Test
  public void updateManySubmodules() throws Exception {
    final int NUM = 3;
    Project.NameKey subKey[] = new Project.NameKey[NUM];
    TestRepository<?> sub[] = new TestRepository[NUM];
    String prefix = RandomStringUtils.randomAlphabetic(8);
    for (int i = 0; i < subKey.length; i++) {
      subKey[i] =
          projectOperations
              .newProject()
              .name(prefix + "sub" + i)
              .submitType(getSubmitType())
              .create();
      projectOperations
          .project(subKey[i])
          .forUpdate()
          .add(allow(Permission.PUSH).ref("refs/heads/*").group(adminGroupUuid()))
          .add(allow(Permission.SUBMIT).ref("refs/for/refs/heads/*").group(adminGroupUuid()))
          .update();
      sub[i] = cloneProject(subKey[i]);
    }

    for (int i = 0; i < subKey.length; i++) {
      allowMatchingSubmoduleSubscription(
          subKey[i], "refs/heads/master", superKey, "refs/heads/master");
    }

    Config config = new Config();
    for (int i = 0; i < subKey.length; i++) {
      prepareSubmoduleConfigEntry(config, subKey[i], "master");
    }
    pushSubmoduleConfig(superRepo, "master", config);

    ObjectId superPreviousId = pushChangeTo(superRepo, "master");

    ObjectId subId[] = new ObjectId[NUM];

    for (int i = 0; i < sub.length; i++) {
      subId[i] = pushChangeTo(sub[i], "refs/for/master", "some message", "same-topic");
      approve(getChangeId(sub[i], subId[i]).get());
    }

    gApi.changes().id(getChangeId(sub[0], subId[0]).get()).current().submit();

    for (int i = 0; i < sub.length; i++) {
      expectToHaveSubmoduleState(superRepo, "master", subKey[i], sub[i], "master");
    }

    String heads[] = new String[NUM];
    for (int i = 0; i < heads.length; i++) {
      heads[i] =
          sub[i]
              .git()
              .fetch()
              .setRemote("origin")
              .call()
              .getAdvertisedRef("refs/heads/master")
              .getObjectId()
              .name();
    }

    if (getSubmitType() == SubmitType.MERGE_IF_NECESSARY) {
      expectToHaveCommitMessage(
          superRepo,
          "master",
          "Update git submodules\n\n"
              + "* Update "
              + subKey[0].get()
              + " from branch 'master'\n  to "
              + heads[0]
              + "\n\n* Update "
              + subKey[1].get()
              + " from branch 'master'\n  to "
              + heads[1]
              + "\n\n* Update "
              + subKey[2].get()
              + " from branch 'master'\n  to "
              + heads[2]);
    }

    superRepo
        .git()
        .fetch()
        .setRemote("origin")
        .call()
        .getAdvertisedRef("refs/heads/master")
        .getObjectId();

    assertWithMessage("submodule subscription update should have made one commit")
        .that(superRepo.getRepository().resolve("origin/master^"))
        .isEqualTo(superPreviousId);
  }

  @Test
  public void doNotUseFastForward() throws Exception {
    // like setup, but without empty commit
    superKey =
        projectOperations
            .newProject()
            .submitType(getSubmitType())
            .createEmptyCommit(false)
            .create();
    grantPush(superKey);
    subKey =
        projectOperations
            .newProject()
            .submitType(getSubmitType())
            .createEmptyCommit(false)
            .create();
    grantPush(subKey);
    superRepo = cloneProject(superKey);
    subRepo = cloneProject(subKey);

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", subKey, "master");

    ObjectId subId = pushChangeTo(subRepo, "refs/for/master", "some message", "same-topic");

    ObjectId superId = pushChangeTo(superRepo, "refs/for/master", "some message", "same-topic");

    String subChangeId = getChangeId(subRepo, subId).get();
    approve(subChangeId);
    approve(getChangeId(superRepo, superId).get());

    gApi.changes().id(subChangeId).current().submit();

    expectToHaveSubmoduleState(superRepo, "master", subKey, subRepo, "master");
    RevCommit superHead = projectOperations.project(superKey).getHead("master");
    assertThat(superHead.getShortMessage()).contains("some message");
    assertThat(superHead.getId()).isNotEqualTo(superId);
  }

  @Test
  public void useFastForwardWhenNoSubmodule() throws Exception {
    // like setup, but without empty commit
    superKey =
        projectOperations
            .newProject()
            .submitType(getSubmitType())
            .createEmptyCommit(false)
            .create();
    grantPush(superKey);
    subKey =
        projectOperations
            .newProject()
            .submitType(getSubmitType())
            .createEmptyCommit(false)
            .create();
    grantPush(subKey);
    superRepo = cloneProject(superKey);
    subRepo = cloneProject(subKey);

    ObjectId subId = pushChangeTo(subRepo, "refs/for/master", "some message", "same-topic");
    ObjectId superId = pushChangeTo(superRepo, "refs/for/master", "some message", "same-topic");

    String subChangeId = getChangeId(subRepo, subId).get();
    approve(subChangeId);
    approve(getChangeId(superRepo, superId).get());

    gApi.changes().id(subChangeId).current().submit();

    RevCommit superHead = projectOperations.project(superKey).getHead("master");
    assertThat(superHead.getShortMessage()).isEqualTo("some message");
    assertThat(superHead.getId()).isEqualTo(superId);
  }

  @Test
  public void sameProjectSameBranchDifferentPaths() throws Exception {
    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");

    Config config = new Config();
    prepareSubmoduleConfigEntry(config, subKey, "master");
    Project.NameKey copyKey = nameKey("sub-copy");
    prepareSubmoduleConfigEntry(config, subKey, copyKey, "master");
    pushSubmoduleConfig(superRepo, "master", config);

    ObjectId superPreviousId = pushChangeTo(superRepo, "master");

    ObjectId subId = pushChangeTo(subRepo, "refs/for/master", "some message", "");

    approve(getChangeId(subRepo, subId).get());

    gApi.changes().id(getChangeId(subRepo, subId).get()).current().submit();

    expectToHaveSubmoduleState(superRepo, "master", subKey, subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master", copyKey, subRepo, "master");

    superRepo
        .git()
        .fetch()
        .setRemote("origin")
        .call()
        .getAdvertisedRef("refs/heads/master")
        .getObjectId();

    assertWithMessage("submodule subscription update should have made one commit")
        .that(superRepo.getRepository().resolve("origin/master^"))
        .isEqualTo(superPreviousId);
  }

  @Test
  public void sameProjectDifferentBranchDifferentPaths() throws Exception {
    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(subKey, "refs/heads/dev", superKey, "refs/heads/master");

    ObjectId revMasterBranch = pushChangeTo(subRepo, "master");
    ObjectId revDevBranch = pushChangeTo(subRepo, "dev");
    Config config = new Config();
    prepareSubmoduleConfigEntry(config, subKey, nameKey("sub-master"), "master");
    prepareSubmoduleConfigEntry(config, subKey, nameKey("sub-dev"), "dev");
    pushSubmoduleConfig(superRepo, "master", config);

    subRepo.reset(revMasterBranch);
    ObjectId subMasterId =
        pushChangeTo(
            subRepo, "refs/for/master", "some message", "b.txt", "content b", "same-topic");

    subRepo.reset(revDevBranch);
    ObjectId subDevId =
        pushChangeTo(
            subRepo, "refs/for/dev", "some message in dev", "b.txt", "content b", "same-topic");

    approve(getChangeId(subRepo, subMasterId).get());
    approve(getChangeId(subRepo, subDevId).get());

    ObjectId superPreviousId = pushChangeTo(superRepo, "master");

    gApi.changes().id(getChangeId(subRepo, subMasterId).get()).current().submit();

    expectToHaveSubmoduleState(superRepo, "master", nameKey("sub-master"), subRepo, "master");
    expectToHaveSubmoduleState(superRepo, "master", nameKey("sub-dev"), subRepo, "dev");

    superRepo
        .git()
        .fetch()
        .setRemote("origin")
        .call()
        .getAdvertisedRef("refs/heads/master")
        .getObjectId();

    assertWithMessage("submodule subscription update should have made one commit")
        .that(superRepo.getRepository().resolve("origin/master^"))
        .isEqualTo(superPreviousId);
  }

  @Test
  public void nonSubmoduleInSameTopic() throws Exception {
    Project.NameKey standaloneKey = createProjectForPush(getSubmitType());
    TestRepository<?> standAlone = cloneProject(standaloneKey);

    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");

    createSubmoduleSubscription(superRepo, "master", subKey, "master");

    ObjectId superPreviousId = pushChangeTo(superRepo, "master");

    ObjectId subId = pushChangeTo(subRepo, "refs/for/master", "some message", "same-topic");
    ObjectId standAloneId =
        pushChangeTo(standAlone, "refs/for/master", "some message", "same-topic");

    String subChangeId = getChangeId(subRepo, subId).get();
    String standAloneChangeId = getChangeId(standAlone, standAloneId).get();
    approve(subChangeId);
    approve(standAloneChangeId);

    gApi.changes().id(subChangeId).current().submit();

    expectToHaveSubmoduleState(superRepo, "master", subKey, subRepo, "master");

    ChangeStatus status = gApi.changes().id(standAloneChangeId).info().status;
    assertThat(status).isEqualTo(ChangeStatus.MERGED);

    superRepo
        .git()
        .fetch()
        .setRemote("origin")
        .call()
        .getAdvertisedRef("refs/heads/master")
        .getObjectId();

    assertWithMessage("submodule subscription update should have made one commit")
        .that(superRepo.getRepository().resolve("origin/master^"))
        .isEqualTo(superPreviousId);
  }

  @Test
  public void recursiveSubmodules() throws Exception {
    Project.NameKey topKey = createProjectForPush(getSubmitType());
    Project.NameKey midKey = createProjectForPush(getSubmitType());
    Project.NameKey botKey = createProjectForPush(getSubmitType());
    TestRepository<?> topRepo = cloneProject(topKey);
    TestRepository<?> midRepo = cloneProject(midKey);
    TestRepository<?> bottomRepo = cloneProject(botKey);

    allowMatchingSubmoduleSubscription(midKey, "refs/heads/master", topKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(botKey, "refs/heads/master", midKey, "refs/heads/master");

    createSubmoduleSubscription(topRepo, "master", midKey, "master");
    createSubmoduleSubscription(midRepo, "master", botKey, "master");

    ObjectId bottomHead = pushChangeTo(bottomRepo, "refs/for/master", "some message", "same-topic");
    ObjectId topHead = pushChangeTo(topRepo, "refs/for/master", "some message", "same-topic");

    String id1 = getChangeId(bottomRepo, bottomHead).get();
    String id2 = getChangeId(topRepo, topHead).get();

    gApi.changes().id(id1).current().review(ReviewInput.approve());
    gApi.changes().id(id2).current().review(ReviewInput.approve());

    gApi.changes().id(id1).current().submit();

    expectToHaveSubmoduleState(midRepo, "master", botKey, bottomRepo, "master");
    expectToHaveSubmoduleState(topRepo, "master", midKey, midRepo, "master");
  }

  @Test
  public void triangleSubmodules() throws Exception {
    Project.NameKey topKey = createProjectForPush(getSubmitType());
    Project.NameKey midKey = createProjectForPush(getSubmitType());
    Project.NameKey botKey = createProjectForPush(getSubmitType());
    TestRepository<?> topRepo = cloneProject(topKey);
    TestRepository<?> midRepo = cloneProject(midKey);
    TestRepository<?> bottomRepo = cloneProject(botKey);

    allowMatchingSubmoduleSubscription(midKey, "refs/heads/master", topKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(botKey, "refs/heads/master", midKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(botKey, "refs/heads/master", topKey, "refs/heads/master");

    createSubmoduleSubscription(midRepo, "master", botKey, "master");
    Config config = new Config();
    prepareSubmoduleConfigEntry(config, botKey, "master");
    prepareSubmoduleConfigEntry(config, midKey, "master");
    pushSubmoduleConfig(topRepo, "master", config);

    ObjectId bottomHead = pushChangeTo(bottomRepo, "refs/for/master", "some message", "same-topic");
    ObjectId topHead = pushChangeTo(topRepo, "refs/for/master", "some message", "same-topic");

    String id1 = getChangeId(bottomRepo, bottomHead).get();
    String id2 = getChangeId(topRepo, topHead).get();

    gApi.changes().id(id1).current().review(ReviewInput.approve());
    gApi.changes().id(id2).current().review(ReviewInput.approve());

    gApi.changes().id(id1).current().submit();

    expectToHaveSubmoduleState(midRepo, "master", botKey, bottomRepo, "master");
    expectToHaveSubmoduleState(topRepo, "master", midKey, midRepo, "master");
    expectToHaveSubmoduleState(topRepo, "master", botKey, bottomRepo, "master");
  }

  private void testBranchCircularSubscription(ThrowingConsumer<String> apiCall) throws Exception {
    Project.NameKey topKey = createProjectForPush(getSubmitType());
    Project.NameKey midKey = createProjectForPush(getSubmitType());
    Project.NameKey botKey = createProjectForPush(getSubmitType());
    TestRepository<?> topRepo = cloneProject(topKey);
    TestRepository<?> midRepo = cloneProject(midKey);
    TestRepository<?> bottomRepo = cloneProject(botKey);

    createSubmoduleSubscription(midRepo, "master", botKey, "master");
    createSubmoduleSubscription(topRepo, "master", midKey, "master");
    createSubmoduleSubscription(bottomRepo, "master", topKey, "master");

    allowMatchingSubmoduleSubscription(botKey, "refs/heads/master", midKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(midKey, "refs/heads/master", topKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(topKey, "refs/heads/master", botKey, "refs/heads/master");

    ObjectId bottomMasterHead = pushChangeTo(bottomRepo, "refs/for/master", "some message", "");
    String changeId = getChangeId(bottomRepo, bottomMasterHead).get();

    approve(changeId);

    ResourceConflictException thrown =
        assertThrows(ResourceConflictException.class, () -> apiCall.accept(changeId));
    assertThat(thrown).hasMessageThat().contains("Branch level circular subscriptions detected");
    assertThat(thrown).hasMessageThat().contains(topKey.get() + ",refs/heads/master");
    assertThat(thrown).hasMessageThat().contains(midKey.get() + ",refs/heads/master");
    assertThat(thrown).hasMessageThat().contains(botKey.get() + ",refs/heads/master");
  }

  @Test
  public void branchCircularSubscription() throws Exception {
    testBranchCircularSubscription(changeId -> gApi.changes().id(changeId).current().submit());
  }

  @Test
  public void projectCircularSubscriptionWholeTopic() throws Exception {
    allowMatchingSubmoduleSubscription(subKey, "refs/heads/master", superKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(superKey, "refs/heads/dev", subKey, "refs/heads/dev");

    pushChangeTo(subRepo, "dev");
    pushChangeTo(superRepo, "dev");

    createSubmoduleSubscription(superRepo, "master", subKey, "master");
    createSubmoduleSubscription(subRepo, "dev", superKey, "dev");

    ObjectId subMasterHead =
        pushChangeTo(
            subRepo, "refs/for/master", "b.txt", "content b", "some message", "same-topic");
    ObjectId superDevHead = pushChangeTo(superRepo, "refs/for/dev", "some message", "same-topic");

    approve(getChangeId(subRepo, subMasterHead).get());
    approve(getChangeId(superRepo, superDevHead).get());

    Throwable thrown =
        assertThrows(
            Throwable.class,
            () -> gApi.changes().id(getChangeId(subRepo, subMasterHead).get()).current().submit());
    assertThat(thrown).hasMessageThat().contains("Project level circular subscriptions detected");
    assertThat(thrown).hasMessageThat().contains(subKey.get());
    assertThat(thrown).hasMessageThat().contains(superKey.get());
  }

  @Test
  public void projectNoSubscriptionWholeTopic() throws Exception {
    Project.NameKey keyA = createProjectForPush(getSubmitType());
    Project.NameKey keyB = createProjectForPush(getSubmitType());

    TestRepository<?> repoA = cloneProject(keyA);
    TestRepository<?> repoB = cloneProject(keyB);

    // Create master- and dev branches in both repositories
    ObjectId revMasterBranchA = pushChangeTo(repoA, "master");
    ObjectId revMasterBranchB = pushChangeTo(repoB, "master");
    ObjectId revDevBranchA = pushChangeTo(repoA, "dev");
    ObjectId revDevBranchB = pushChangeTo(repoB, "dev");

    // create a change for master branch in repo a
    repoA.reset(revMasterBranchA);
    ObjectId revMasterChangeA =
        pushChangeTo(
            repoA,
            "refs/for/master",
            "master.txt",
            "content master A",
            "some message in a master.txt",
            "same-topic");

    // create a change for master branch in repo b
    repoB.reset(revMasterBranchB);
    ObjectId revMasterChangeB =
        pushChangeTo(
            repoB,
            "refs/for/master",
            "master.txt",
            "content master B",
            "some message in b master.txt",
            "same-topic");

    // create a change for dev branch in repo a
    repoA.reset(revDevBranchA);
    ObjectId revDevChangeA =
        pushChangeTo(
            repoA,
            "refs/for/dev",
            "dev.txt",
            "content dev A",
            "some message in a dev.txt",
            "same-topic");

    // create a change for dev branch in repo b
    repoB.reset(revDevBranchB);
    ObjectId revDevChangeB =
        pushChangeTo(
            repoB,
            "refs/for/dev",
            "dev.txt",
            "content dev B",
            "some message in b dev.txt",
            "same-topic");

    approve(getChangeId(repoA, revMasterChangeA).get());
    approve(getChangeId(repoB, revMasterChangeB).get());
    approve(getChangeId(repoA, revDevChangeA).get());
    approve(getChangeId(repoB, revDevChangeB).get());

    gApi.changes().id(getChangeId(repoA, revDevChangeA).get()).current().submit();
    assertThat(projectOperations.project(keyA).getHead("refs/heads/master").getShortMessage())
        .contains("some message in a master.txt");
    assertThat(projectOperations.project(keyA).getHead("refs/heads/dev").getShortMessage())
        .contains("some message in a dev.txt");
    assertThat(projectOperations.project(keyB).getHead("refs/heads/master").getShortMessage())
        .contains("some message in b master.txt");
    assertThat(projectOperations.project(keyB).getHead("refs/heads/dev").getShortMessage())
        .contains("some message in b dev.txt");
  }

  @Test
  public void twoProjectsMultipleBranchesWholeTopic() throws Exception {
    Project.NameKey keyA = createProjectForPush(getSubmitType());
    Project.NameKey keyB = createProjectForPush(getSubmitType());
    TestRepository<?> repoA = cloneProject(keyA);
    TestRepository<?> repoB = cloneProject(keyB);
    // bootstrap the dev branch
    pushChangeTo(repoA, "dev");

    // Create master- and dev branches in repo b
    ObjectId revMasterBranch = pushChangeTo(repoB, "master");
    ObjectId revDevBranch = pushChangeTo(repoB, "dev");

    allowMatchingSubmoduleSubscription(keyB, "refs/heads/master", keyA, "refs/heads/master");
    allowMatchingSubmoduleSubscription(keyB, "refs/heads/dev", keyA, "refs/heads/dev");

    createSubmoduleSubscription(repoA, "master", keyB, "master");
    createSubmoduleSubscription(repoA, "dev", keyB, "dev");

    // create a change for master branch in repo b
    repoB.reset(revMasterBranch);
    ObjectId revMasterChange =
        pushChangeTo(
            repoB,
            "refs/for/master",
            "master.txt",
            "content master B",
            "some message in b master.txt",
            "same-topic");

    // create a change for dev branch in repo b
    repoB.reset(revDevBranch);
    ObjectId revDevChange =
        pushChangeTo(
            repoB,
            "refs/for/dev",
            "dev.txt",
            "content dev B",
            "some message in b dev.txt",
            "same-topic");

    approve(getChangeId(repoB, revMasterChange).get());
    approve(getChangeId(repoB, revDevChange).get());
    gApi.changes().id(getChangeId(repoB, revMasterChange).get()).current().submit();

    expectToHaveSubmoduleState(repoA, "master", keyB, repoB, "master");
    expectToHaveSubmoduleState(repoA, "dev", keyB, repoB, "dev");
  }

  @Test
  public void retrySubmitAfterTornTopicOnLockFailure() throws Exception {
    Project.NameKey subKey1 = createProjectForPush(getSubmitType());
    TestRepository<?> sub1 = cloneProject(subKey1);
    Project.NameKey subKey2 = createProjectForPush(getSubmitType());
    TestRepository<?> sub2 = cloneProject(subKey2);

    allowMatchingSubmoduleSubscription(subKey1, "refs/heads/master", superKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(subKey2, "refs/heads/master", superKey, "refs/heads/master");

    Config config = new Config();
    prepareSubmoduleConfigEntry(config, subKey1, "master");
    prepareSubmoduleConfigEntry(config, subKey2, "master");
    pushSubmoduleConfig(superRepo, "master", config);

    ObjectId superPreviousId = pushChangeTo(superRepo, "master");

    String topic = "same-topic";
    ObjectId sub1Id = pushChangeTo(sub1, "refs/for/master", "some message", topic);
    ObjectId sub2Id = pushChangeTo(sub2, "refs/for/master", "some message", topic);

    String changeId1 = getChangeId(sub1, sub1Id).get();
    String changeId2 = getChangeId(sub2, sub2Id).get();
    approve(changeId1);
    approve(changeId2);

    TestSubmitInput input = new TestSubmitInput();
    input.generateLockFailures =
        new ArrayDeque<>(
            ImmutableList.of(
                false, // Change 1, attempt 1: success
                true, // Change 2, attempt 1: lock failure
                false, // Change 1, attempt 2: success
                false, // Change 2, attempt 2: success
                false)); // Leftover value to check total number of calls.
    gApi.changes().id(changeId1).current().submit(input);

    assertThat(info(changeId1).status).isEqualTo(ChangeStatus.MERGED);
    assertThat(info(changeId2).status).isEqualTo(ChangeStatus.MERGED);

    sub1.git().fetch().call();
    RevWalk rw1 = sub1.getRevWalk();
    RevCommit master1 = rw1.parseCommit(projectOperations.project(subKey1).getHead("master"));
    RevCommit change1Ps = parseCurrentRevision(rw1, changeId1);
    assertThat(rw1.isMergedInto(change1Ps, master1)).isTrue();

    sub2.git().fetch().call();
    RevWalk rw2 = sub2.getRevWalk();
    RevCommit master2 = rw2.parseCommit(projectOperations.project(subKey2).getHead("master"));
    RevCommit change2Ps = parseCurrentRevision(rw2, changeId2);
    assertThat(rw2.isMergedInto(change2Ps, master2)).isTrue();

    assertThat(input.generateLockFailures).containsExactly(false);

    expectToHaveSubmoduleState(superRepo, "master", subKey1, sub1, "master");
    expectToHaveSubmoduleState(superRepo, "master", subKey2, sub2, "master");

    assertWithMessage("submodule subscription update should have made one commit")
        .that(superRepo.getRepository().resolve("origin/master^"))
        .isEqualTo(superPreviousId);
  }

  @Test
  public void skipUpdatingBrokenGitlinkPointer() throws Exception {
    Project.NameKey subKey1 = createProjectForPush(getSubmitType());
    TestRepository<?> sub1 = cloneProject(subKey1);
    Project.NameKey subKey2 = createProjectForPush(getSubmitType());
    TestRepository<?> sub2 = cloneProject(subKey2);

    allowMatchingSubmoduleSubscription(subKey1, "refs/heads/master", superKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(subKey2, "refs/heads/master", superKey, "refs/heads/master");

    Config config = new Config();
    prepareSubmoduleConfigEntry(config, subKey1, "master");
    prepareSubmoduleConfigEntry(config, subKey2, "master");
    pushSubmoduleConfig(superRepo, "master", config);

    // Write an invalid SHA-1 directly to one of the gitlinks.
    ObjectId badId = ObjectId.fromString("deadbeefdeadbeefdeadbeefdeadbeefdeadbeef");
    directUpdateSubmodule(superKey, "refs/heads/master", subKey1, badId);
    expectToHaveSubmoduleState(superRepo, "master", subKey1, badId);

    String topic = "same-topic";
    ObjectId sub1Id = pushChangeTo(sub1, "refs/for/master", "some message", topic);
    ObjectId sub2Id = pushChangeTo(sub2, "refs/for/master", "some message", topic);

    String changeId1 = getChangeId(sub1, sub1Id).get();
    String changeId2 = getChangeId(sub2, sub2Id).get();
    approve(changeId1);
    approve(changeId2);

    gApi.changes().id(changeId1).current().submit();

    assertThat(info(changeId1).status).isEqualTo(ChangeStatus.MERGED);
    assertThat(info(changeId2).status).isEqualTo(ChangeStatus.MERGED);

    // sub1 was skipped but sub2 succeeded.
    expectToHaveSubmoduleState(superRepo, "master", subKey1, badId);
    expectToHaveSubmoduleState(superRepo, "master", subKey2, sub2, "master");
  }

  private Project.NameKey nameKey(String s) {
    return Project.nameKey(name(s));
  }
}
