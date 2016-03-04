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

  protected ObjectId pushChangeTo(TestRepository<?> repo, String ref,
      String message, String topic) throws Exception {
    ObjectId ret = repo.branch("HEAD").commit().insertChangeId()
      .message(message)
      .add("a.txt", "a contents: " + contentCounter.incrementAndGet())
      .create();
    String refspec = "HEAD:" + ref;
    if (!topic.isEmpty()) {
      refspec += "/" + topic;
    }
    repo.git().push().setRemote("origin").setRefSpecs(
        new RefSpec(refspec)).call();
    return ret;
  }

  protected ObjectId pushChangeTo(TestRepository<?> repo, String branch)
      throws Exception {
    return pushChangeTo(repo, "refs/heads/" + branch, "some change", "");
  }

  protected void createSubmoduleSubscription(TestRepository<?> repo, String branch,
      String subscribeToRepo, String subscribeToBranch) throws Exception {
    Config config = new Config();
    prepareSubmoduleConfigEntry(config, subscribeToRepo, subscribeToBranch);
    pushSubmoduleConfig(repo, branch, config);
  }

  protected void allowSubmoduleSubscription(String submodule,
      String subBranch, String superproject, String superBranch)
          throws Exception {

    Project.NameKey sub = new Project.NameKey(name(submodule));
    grant(Permission.SUBMIT, sub, "refs/for/refs/meta/config");
    grant(Permission.PUSH, sub, "refs/for/refs/meta/config");

    TestRepository<?> repo = cloneProject(project);
    repo.git().fetch()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("refs/meta/config:refs/meta/config"))
        .call();
    repo.reset("refs/meta/config");

    RevCommit rev = repo.commit(repo.tree( //
        repo.file("project.config", repo.blob(""//
            + "[subscribe \"" + name(superproject) + "\"]\n"//
            + "  refs = " + subBranch +":" + superBranch + "\n"//
        ))));

    repo.branch("refs/meta/config").update(rev);
    repo.git().push()
        .setRemote("origin")
        .setRefSpecs(new RefSpec("refs/meta/config:refs/meta/config"))
        .call();
  }

  protected void prepareSubmoduleConfigEntry(Config config,
      String subscribeToRepo, String subscribeToBranch) {
    subscribeToRepo = name(subscribeToRepo);
    // The submodule subscription module checks for gerrit.canonicalWebUrl to
    // detect if it's configured for automatic updates. It doesn't matter if
    // it serves from that URL.
    String url = cfg.getString("gerrit", null, "canonicalWebUrl") + "/"
        + subscribeToRepo;
    config.setString("submodule", subscribeToRepo, "path", subscribeToRepo);
    config.setString("submodule", subscribeToRepo, "url", url);
    if (subscribeToBranch != null) {
      config.setString("submodule", subscribeToRepo, "branch", subscribeToBranch);
    }
  }

  protected void pushSubmoduleConfig(TestRepository<?> repo,
      String branch, Config config) throws Exception {

    repo.branch("HEAD").commit().insertChangeId()
      .message("subject: adding new subscription")
      .add(".gitmodules", config.toText().toString())
      .create();

    repo.git().push().setRemote("origin").setRefSpecs(
        new RefSpec("HEAD:refs/heads/" + branch)).call();
  }

  protected void expectToHaveSubmoduleState(TestRepository<?> repo,
      String branch, String submodule, ObjectId expectedId) throws Exception {

    submodule = name(submodule);
    ObjectId commitId = repo.git().fetch().setRemote("origin").call()
        .getAdvertisedRef("refs/heads/" + branch).getObjectId();

    try (RevWalk rw = repo.getRevWalk()) {
      RevCommit c = rw.parseCommit(commitId);
      rw.parseBody(c.getTree());

      RevTree tree = c.getTree();
      RevObject actualId = repo.get(tree, submodule);

      assertThat(actualId).isEqualTo(expectedId);
    }
  }
}
