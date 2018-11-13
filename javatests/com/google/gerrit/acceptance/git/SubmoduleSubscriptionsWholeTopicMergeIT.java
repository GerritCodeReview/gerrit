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
import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.GitUtil.getChangeId;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.change.TestSubmitInput;
import com.google.gerrit.testing.ConfigSuite;
import java.util.ArrayDeque;
import java.util.Map;
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

    Map<Branch.NameKey, ObjectId> preview = fetchFromSubmitPreview(id1);
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

    // As the submodules have changed commits, the superproject tree will be
    // different, so we cannot directly compare the trees here, so make
    // assumptions only about the changed branches:
    assertThat(preview).containsKey(new Branch.NameKey(superKey, "refs/heads/master"));
    assertThat(preview).containsKey(new Branch.NameKey(subKey, "refs/heads/master"));

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

    expectToHaveSubmoduleState(superRepo, "master", subKey, subRepoId);
  }

  @Test
  public void updateManySubmodules() throws Exception {
    Project.NameKey subKey1 = createProjectForPush("sub1", null, true, getSubmitType());
    Project.NameKey subKey2 = createProjectForPush("sub2", null, true, getSubmitType());
    Project.NameKey subKey3 = createProjectForPush("sub3", null, true, getSubmitType());

    TestRepository<?> sub1 = cloneProject(subKey1);
    TestRepository<?> sub2 = cloneProject(subKey2);
    TestRepository<?> sub3 = cloneProject(subKey3);

    allowMatchingSubmoduleSubscription(subKey1, "refs/heads/master", superKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(subKey2, "refs/heads/master", superKey, "refs/heads/master");
    allowMatchingSubmoduleSubscription(subKey3, "refs/heads/master", superKey, "refs/heads/master");

    Config config = new Config();
    prepareSubmoduleConfigEntry(config, subKey1, "master");
    prepareSubmoduleConfigEntry(config, subKey2, "master");
    prepareSubmoduleConfigEntry(config, subKey3, "master");
    pushSubmoduleConfig(superRepo, "master", config);

    ObjectId superPreviousId = pushChangeTo(superRepo, "master");

    ObjectId sub1Id = pushChangeTo(sub1, "refs/for/master", "some message", "same-topic");
    ObjectId sub2Id = pushChangeTo(sub2, "refs/for/master", "some message", "same-topic");
    ObjectId sub3Id = pushChangeTo(sub3, "refs/for/master", "some message", "same-topic");

    approve(getChangeId(sub1, sub1Id).get());
    approve(getChangeId(sub2, sub2Id).get());
    approve(getChangeId(sub3, sub3Id).get());

    gApi.changes().id(getChangeId(sub1, sub1Id).get()).current().submit();

    expectToHaveSubmoduleState(superRepo, "master", subKey1, sub1, "master");
    expectToHaveSubmoduleState(superRepo, "master", subKey2, sub2, "master");
    expectToHaveSubmoduleState(superRepo, "master", subKey3, sub3, "master");

    String sub1HEAD =
        sub1.git()
            .fetch()
            .setRemote("origin")
            .call()
            .getAdvertisedRef("refs/heads/master")
            .getObjectId()
            .name();

    String sub2HEAD =
        sub2.git()
            .fetch()
            .setRemote("origin")
            .call()
            .getAdvertisedRef("refs/heads/master")
            .getObjectId()
            .name();

    String sub3HEAD =
        sub3.git()
            .fetch()
            .setRemote("origin")
            .call()
            .getAdvertisedRef("refs/heads/master")
            .getObjectId()
            .name();

    if (getSubmitType() == SubmitType.MERGE_IF_NECESSARY) {
      expectToHaveCommitMessage(
          superRepo,
          "master",
          "Update git submodules\n\n"
              + "* Update "
              + subKey1.get()
              + " from branch 'master'\n  to "
              + sub1HEAD
              + "\n\n* Update "
              + subKey2.get()
              + " from branch 'master'\n  to "
              + sub2HEAD
              + "\n\n* Update "
              + subKey3.get()
              + " from branch 'master'\n  to "
              + sub3HEAD);
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
    superKey = createProjectForPush("super-nc", null, false, getSubmitType());
    subKey = createProjectForPush("sub-nc", null, false, getSubmitType());
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
    RevCommit superHead = getRemoteHead(superKey, "master");
    assertThat(superHead.getShortMessage()).contains("some message");
    assertThat(superHead.getId()).isNotEqualTo(superId);
  }

  @Test
  public void useFastForwardWhenNoSubmodule() throws Exception {
    // like setup, but without empty commit
    superKey = createProjectForPush("super-nc", null, false, getSubmitType());
    subKey = createProjectForPush("sub-nc", null, false, getSubmitType());
    superRepo = cloneProject(superKey);
    subRepo = cloneProject(subKey);

    ObjectId subId = pushChangeTo(subRepo, "refs/for/master", "some message", "same-topic");
    ObjectId superId = pushChangeTo(superRepo, "refs/for/master", "some message", "same-topic");

    String subChangeId = getChangeId(subRepo, subId).get();
    approve(subChangeId);
    approve(getChangeId(superRepo, superId).get());

    gApi.changes().id(subChangeId).current().submit();

    RevCommit superHead = getRemoteHead(superKey, "master");
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

    ObjectId devHead = pushChangeTo(subRepo, "dev");
    Config config = new Config();
    prepareSubmoduleConfigEntry(config, subKey, nameKey("sub-master"), "master");
    prepareSubmoduleConfigEntry(config, subKey, nameKey("sub-dev"), "dev");
    pushSubmoduleConfig(superRepo, "master", config);

    ObjectId subMasterId =
        pushChangeTo(
            subRepo, "refs/for/master", "some message", "b.txt", "content b", "same-topic");

    subRepo.reset(devHead);
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
    Project.NameKey standaloneKey = createProjectForPush("standalone", null, true, getSubmitType());
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
    Project.NameKey topKey = createProjectForPush("top-project", null, true, getSubmitType());
    Project.NameKey midKey = createProjectForPush("mid-project", null, true, getSubmitType());
    Project.NameKey botKey = createProjectForPush("bottom-project", null, true, getSubmitType());
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
    Project.NameKey topKey = createProjectForPush("top-project", null, true, getSubmitType());
    Project.NameKey midKey = createProjectForPush("mid-project", null, true, getSubmitType());
    Project.NameKey botKey = createProjectForPush("bottom-project", null, true, getSubmitType());
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

  private String prepareBranchCircularSubscription() throws Exception {
    Project.NameKey topKey = createProjectForPush("top-project", null, true, getSubmitType());
    Project.NameKey midKey = createProjectForPush("mid-project", null, true, getSubmitType());
    Project.NameKey botKey = createProjectForPush("bottom-project", null, true, getSubmitType());
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
    exception.expectMessage("Branch level circular subscriptions detected");
    exception.expectMessage("top-project,refs/heads/master");
    exception.expectMessage("mid-project,refs/heads/master");
    exception.expectMessage("bottom-project,refs/heads/master");
    return changeId;
  }

  @Test
  public void branchCircularSubscription() throws Exception {
    String changeId = prepareBranchCircularSubscription();
    gApi.changes().id(changeId).current().submit();
  }

  @Test
  public void branchCircularSubscriptionPreview() throws Exception {
    String changeId = prepareBranchCircularSubscription();
    gApi.changes().id(changeId).current().submitPreview();
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

    exception.expectMessage("Project level circular subscriptions detected");
    exception.expectMessage(subKey.get());
    exception.expectMessage(superKey.get());
    gApi.changes().id(getChangeId(subRepo, subMasterHead).get()).current().submit();
  }

  @Test
  public void projectNoSubscriptionWholeTopic() throws Exception {
    TestRepository<?> repoA = createProjectWithPush("project-a", null, true, getSubmitType());
    TestRepository<?> repoB = createProjectWithPush("project-b", null, true, getSubmitType());
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
  public void twoProjectsMultipleBranchesWholeTopic() throws Exception {
    TestRepository<?> repoA = createProjectWithPush("project-a", null, true, getSubmitType());
    TestRepository<?> repoB = createProjectWithPush("project-b", null, true, getSubmitType());
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

  @Test
  public void retrySubmitAfterTornTopicOnLockFailure() throws Exception {
    assume().that(notesMigration.disableChangeReviewDb()).isTrue();

    Project.NameKey subKey1 = createProjectForPush("sub1", null, true, getSubmitType());
    TestRepository<?> sub1 = cloneProject(subKey1);
    Project.NameKey subKey2 = createProjectForPush("sub2", null, true, getSubmitType());
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
    RevCommit master1 = rw1.parseCommit(getRemoteHead(subKey1.get(), "master"));
    RevCommit change1Ps = parseCurrentRevision(rw1, changeId1);
    assertThat(rw1.isMergedInto(change1Ps, master1)).isTrue();

    sub2.git().fetch().call();
    RevWalk rw2 = sub2.getRevWalk();
    RevCommit master2 = rw2.parseCommit(getRemoteHead(subKey2.get(), "master"));
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
    Project.NameKey subKey1 = createProjectForPush("sub1", null, true, getSubmitType());
    TestRepository<?> sub1 = cloneProject(subKey1);
    Project.NameKey subKey2 = createProjectForPush("sub2", null, true, getSubmitType());
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
}
