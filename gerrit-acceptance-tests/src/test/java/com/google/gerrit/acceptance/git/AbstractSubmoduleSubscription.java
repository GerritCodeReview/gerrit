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

import java.util.concurrent.atomic.AtomicInteger;

public abstract class AbstractSubmoduleSubscription extends AbstractDaemonTest {
  protected TestRepository<?> createProjectWithPush(String name)
      throws Exception {
    Project.NameKey project = createProject(name);
    grant(Permission.PUSH, project, "refs/heads/*");
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/*");
    return cloneProject(project);
  }

  private static AtomicInteger contentCounter = new AtomicInteger(0);

  protected ObjectId pushChangeTo(TestRepository<?> repo, String branch, String message)
      throws Exception {

    ObjectId ret = repo.branch("HEAD").commit().insertChangeId()
      .message(message)
      .add("a.txt", "a contents: " + contentCounter.addAndGet(1))
      .create();

    repo.git().push().setRemote("origin").setRefSpecs(
        new RefSpec("HEAD:refs/heads/" + branch)).call();

    return ret;
  }

  protected ObjectId pushChangeTo(TestRepository<?> repo, String branch)
      throws Exception {
    return pushChangeTo(repo, branch, "some change");
  }

  protected void createSubscription(
      TestRepository<?> repo, String branch, String subscribeToRepo,
      String subscribeToBranch) throws Exception {

    Config cfg = new Config();
    addSubmoduleSubscription(cfg, subscribeToRepo, subscribeToBranch);
    pushSubscriptionConfig(repo, branch, cfg);
  }

  protected void addSubmoduleSubscription(Config cfg, String subscribeToRepo,
      String subscribeToBranch) {
    subscribeToRepo = name(subscribeToRepo);
    // The submodule subscription module checks for gerrit.canonicalWebUrl to
    // detect if it's configured for automatic updates. It doesn't matter if
    // it serves from that URL.
    String url = cfg.getString("gerrit", null, "canonicalWebUrl") + "/"
        + subscribeToRepo;
    cfg.setString("submodule", subscribeToRepo, "path", subscribeToRepo);
    cfg.setString("submodule", subscribeToRepo, "url", url);
    cfg.setString("submodule", subscribeToRepo, "branch", subscribeToBranch);
  }

  protected void pushSubscriptionConfig(TestRepository<?> repo,
      String branch, Config cfg) throws Exception {

    repo.branch("HEAD").commit().insertChangeId()
      .message("subject: adding new subscription")
      .add(".gitmodules", cfg.toText().toString())
      .create();

    repo.git().push().setRemote("origin").setRefSpecs(
        new RefSpec("HEAD:refs/heads/" + branch)).call();
  }

  protected void expectToHaveSubmoduleState(TestRepository<?> repo,
      String branch, String submodule, ObjectId expectedId) throws Exception {

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
}