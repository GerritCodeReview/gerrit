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

import static com.google.gerrit.acceptance.GitUtil.getChangeId;

import com.google.gerrit.acceptance.git.AbstractSubmoduleSubscription;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.extensions.api.changes.ReviewInput;
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
    createSubscription(superRepo, "master", "subscribed-to-project", "master");

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
}
