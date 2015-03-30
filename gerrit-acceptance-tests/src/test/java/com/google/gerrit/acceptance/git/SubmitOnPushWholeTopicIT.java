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
import static com.google.gerrit.acceptance.GitUtil.cloneProject;
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
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;


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

  @Test
  public void submitOnPush() throws Exception {
    grant(Permission.SUBMIT, project, "refs/for/refs/heads/master");
    PushOneCommit.Result r = pushTo("refs/for/master/foo");
    PushOneCommit.Result r2 = pushTo("refs/for/master/foo%submit");
    r.assertOkStatus();
    r.assertChange(Change.Status.MERGED, null, admin);
    assertSubmitApproval(r.getPatchSetId());

    r2.assertOkStatus();
    r2.assertChange(Change.Status.MERGED, null, admin);
    assertSubmitApproval(r2.getPatchSetId());
  }

  @Test
  public void testSubscriptionUpdateOfManyChanges() throws Exception {

    if (!isSubmitWholeTopicEnabled()) {
      // this test only makes sense with submitwholetopic enabled
      return;
    }

    TestRepository<?> superRepo = createProjectWithPush("super-project");
    TestRepository<?> subRepo = createProjectWithPush("subscribed-to-project");
    createSubscription(superRepo, "master", "subscribed-to-project", "master");

    ObjectId superHEAD = pushRandomChangeTo(superRepo, "master");
    ObjectId subHEAD = pushRandomChangeTo(subRepo, "master");
    RevCommit c = subRepo.getRevWalk().parseCommit(subHEAD);

    RevCommit c1 = subRepo.branch("HEAD").commit().insertChangeId()
      .message("first change")
      .add("b.txt", "b contents")
      .parent(c)
      .create();
    subRepo.git().push().setRemote("origin")
      .setRefSpecs(new RefSpec("HEAD:refs/for/master/topic-foo")).call();

    RevCommit c2 = subRepo.branch("HEAD").commit().insertChangeId()
      .message("second change")
      .add("a.txt", "a contents")
      .parent(c)
      .create();
    subRepo.git().push().setRemote("origin")
      .setRefSpecs(new RefSpec("HEAD:refs/for/master/topic-foo")).call();

    RevCommit c3 = subRepo.branch("HEAD").commit().insertChangeId()
      .message("third change")
      .add("a.txt", "a contents\n  another line")
      .parent(c2)
      .create();
    subRepo.git().push().setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/for/master/topic-foo")).call();

    RevCommit c4 = subRepo.branch("HEAD").commit().insertChangeId()
      .message("fourth change")
      .add("c.txt", "c contents")
      .parent(c)
      .create();
    subRepo.git().push().setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/for/master/topic-foo")).call();

    RevCommit c5 = subRepo.branch("HEAD").commit().insertChangeId()
      .message("fifth change")
      .add("c.txt", "c contents 2")
      .parent(c4)
      .create();
    subRepo.git().push().setRemote("origin")
        .setRefSpecs(new RefSpec("HEAD:refs/for/master/topic-foo")).call();

    String id1 = getChangeId(subRepo, c1).get();
    String id2 = getChangeId(subRepo, c2).get();
    String id3 = getChangeId(subRepo, c3).get();
    String id4 = getChangeId(subRepo, c4).get();
    String id5 = getChangeId(subRepo, c5).get();
    gApi.changes().id(id1).current().review(ReviewInput.approve());
    gApi.changes().id(id2).current().review(ReviewInput.approve());
    gApi.changes().id(id3).current().review(ReviewInput.approve());
    gApi.changes().id(id4).current().review(ReviewInput.approve());
    gApi.changes().id(id5).current().review(ReviewInput.approve());

    gApi.changes().id(id5).current().submit();

    // as there will be a merge commit generated by Gerrit, we don't know
    // exactly what to expect for the super project, it must have changed though
    ObjectId commitId = superRepo.git().fetch().setRemote("origin").call()
        .getAdvertisedRef("refs/heads/master").getObjectId();

    assertThat(commitId).isNotEqualTo(superHEAD);
  }

  public static ReviewInput approve() {
    return new ReviewInput().label("Code-Review", 2);
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

  private PatchSetApproval getSubmitter(PatchSet.Id patchSetId)
      throws OrmException {
    Change c = db.changes().get(patchSetId.getParentKey());
    ChangeNotes notes = changeNotesFactory.create(c).load();
    return approvalsUtil.getSubmitter(db, notes, patchSetId);
  }

  private void assertSubmitApproval(PatchSet.Id patchSetId) throws OrmException {
    PatchSetApproval a = getSubmitter(patchSetId);
    assertThat(a.isSubmit()).isTrue();
    assertThat(a.getValue()).isEqualTo((short) 1);
    assertThat(a.getAccountId()).isEqualTo(admin.id);
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
