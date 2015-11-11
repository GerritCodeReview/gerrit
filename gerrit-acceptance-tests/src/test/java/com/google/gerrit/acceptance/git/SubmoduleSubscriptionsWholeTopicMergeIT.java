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

import static com.google.common.truth.Truth.assertWithMessage;
import static com.google.gerrit.acceptance.GitUtil.getChangeId;

import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.SubmitInput;
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
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Test
  public void testSubscriptionUpdateOfManyChanges() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
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
  public void testUpdateManySubmodules() throws Exception {
    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> sub1 = createProjectWithPush("sub1");
    TestRepository<?> sub2 = createProjectWithPush("sub2");
    TestRepository<?> sub3 = createProjectWithPush("sub3");

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

    SubmitInput input = new SubmitInput();
    input.submitWholeTopic = true;
    gApi.changes().id(getChangeId(sub1, sub1Id).get()).current().submit(input);

    expectToHaveSubmoduleState(superRepo, "master", "sub1", sub1Id);
    expectToHaveSubmoduleState(superRepo, "master", "sub2", sub2Id);
    expectToHaveSubmoduleState(superRepo, "master", "sub3", sub3Id);

    superRepo.git().fetch().setRemote("origin").call()
      .getAdvertisedRef("refs/heads/master").getObjectId();

    assertWithMessage("submodule subscription update "
        + "should have made one commit")
        .that(superRepo.getRepository().resolve("origin/master^"))
        .isEqualTo(superPreviousId);
  }
}
