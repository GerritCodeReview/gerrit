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
import static com.google.gerrit.acceptance.GitUtil.getChangeId;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;


@NoHttpd
public class SubmitOnPushWholeTopicIT extends AbstractDaemonTest {

  @ConfigSuite.Config
  public static Config submitWholeTopicEnabled() {
    return submitWholeTopicEnabledConfig();
  }

  @Inject
  private ApprovalsUtil approvalsUtil;

  @Inject
  private ChangeNotes.Factory changeNotesFactory;

  @Inject
  private @GerritPersonIdent PersonIdent serverIdent;

  /*
  @Test
  public void submitOnPush() throws Exception {
    if (!isSubmitWholeTopicEnabled()) {
      // this test only makes sense with submitwholetopic enabled
      return;
    }

    grant(Permission.SUBMIT, project, "refs/for/*");
    PushOneCommit.Result r = pushTo("refs/for/master/foo");
    PushOneCommit.Result r2 = pushTo("refs/for/master/foo%submit");

    r2.assertOkStatus();
    r2.assertChange(Change.Status.MERGED, null, admin);
    assertSubmitApproval(r.getPatchSetId());

    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    assertSubmitApproval(r2.getPatchSetId());
  }*/

  @Test
  public void testSubscriptionUpdateOfManyChanges() throws Exception {
    if (!isSubmitWholeTopicEnabled()) {
      // this test only makes sense with submitwholetopic enabled
      return;
    }

    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    createSubscription(superRepo, "master", "subscribed-to-project", "master");

    ObjectId subHEAD = pushChangeTo(subRepo, "master");

    RevCommit c = subRepo.getRevWalk().parseCommit(subHEAD);

    RevCommit c1 = subRepo.branch("HEAD").commit().insertChangeId()
      .message("first change")
      .add("asdf", "asdf\n")
      .parent(c)
      .create();
    subRepo.git().push().setRemote("origin")
      .setRefSpecs(new RefSpec("HEAD:refs/for/master/topic-foo")).call();

    subRepo.reset(c.getId());
    RevCommit c2 = subRepo.branch("HEAD").commit().insertChangeId()
      .message("qwerty")
      .add("qwerty", "qwerty")
      .parent(c)
      .create();

    RevCommit c3 = subRepo.branch("HEAD").commit().insertChangeId()
      .message("qwerty followup")
      .add("qwerty", "qwerty\nqwerty\n")
      .parent(c2)
      .create();
    subRepo.git().push().setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/for/master/topic-foo")).call();

    String id1 = getChangeId(subRepo, c1).get();
    String id2 = getChangeId(subRepo, c2).get();
    String id3 = getChangeId(subRepo, c3).get();
    gApi.changes().id(id1).current().review(ReviewInput.approve());
    gApi.changes().id(id2).current().review(ReviewInput.approve());
    gApi.changes().id(id3).current().review(ReviewInput.approve());

    gApi.changes().id(id1).current().submit();
    ObjectId subRepoId = subRepo.git().fetch().setRemote("origin").call()
        .getAdvertisedRef("refs/heads/master").getObjectId();

    expectToHaveSubmoduleState(superRepo,"master",
        "subscribed-to-project", subRepoId);
  }

  public static ReviewInput approve() {
    return new ReviewInput().label("Code-Review", 2);
  }

  private TestRepository<?> createProjectWithPush(String name)
      throws Exception {
    Project.NameKey project = createProject(name);
    grant(Permission.PUSH, project, "refs/heads/*");
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/*");
    return cloneProject(project);
  }

  protected void createSubscription(
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

  protected static String buildSubmoduleSection(String name,
      String path, String url, String branch) {
    Config cfg = new Config();
    cfg.setString("submodule", name, "path", path);
    cfg.setString("submodule", name, "url", url);
    cfg.setString("submodule", name, "branch", branch);
    return cfg.toText().toString();
  }

  protected ObjectId pushChangeTo(TestRepository<?> repo, String branch)
      throws Exception {
    return pushChangeTo(repo, branch, "some change");
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
}
