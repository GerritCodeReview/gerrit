// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.zip.GZIPInputStream;
import org.apache.commons.compress.archivers.ArchiveStreamFactory;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
import org.junit.Test;

public class SubmitByMergeIfNecessaryIT extends AbstractSubmitByMerge {

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.MERGE_IF_NECESSARY;
  }

  @Test
  public void submitWithFastForward() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());
    RevCommit updatedHead = getRemoteHead();
    assertThat(updatedHead.getId()).isEqualTo(change.getCommit());
    assertThat(updatedHead.getParent(0)).isEqualTo(initialHead);
    assertSubmitter(change.getChangeId(), 1);
    assertPersonEquals(admin.getIdent(), updatedHead.getAuthorIdent());
    assertPersonEquals(admin.getIdent(), updatedHead.getCommitterIdent());

    assertRefUpdatedEvents(initialHead, updatedHead);
    assertChangeMergedEvents(change.getChangeId(), updatedHead.name());
  }

  @Test
  public void submitMultipleChanges() throws Exception {
    RevCommit initialHead = getRemoteHead();

    testRepo.reset(initialHead);
    PushOneCommit.Result change = createChange("Change 1", "b", "b");

    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "c", "c");

    testRepo.reset(initialHead);
    PushOneCommit.Result change3 = createChange("Change 3", "d", "d");
    PushOneCommit.Result change4 = createChange("Change 4", "e", "e");
    PushOneCommit.Result change5 = createChange("Change 5", "f", "f");

    // Change 2 is a fast-forward, no need to merge.
    submit(change2.getChangeId());

    RevCommit headAfterFirstSubmit = getRemoteLog().get(0);
    assertThat(headAfterFirstSubmit.getShortMessage())
        .isEqualTo(change2.getCommit().getShortMessage());
    assertThat(headAfterFirstSubmit.getParent(0).getId()).isEqualTo(initialHead.getId());
    assertPersonEquals(admin.getIdent(), headAfterFirstSubmit.getAuthorIdent());
    assertPersonEquals(admin.getIdent(), headAfterFirstSubmit.getCommitterIdent());

    // We need to merge changes 3, 4 and 5.
    approve(change3.getChangeId());
    approve(change4.getChangeId());
    submit(change5.getChangeId());

    RevCommit headAfterSecondSubmit = getRemoteLog().get(0);
    assertThat(headAfterSecondSubmit.getParent(1).getShortMessage())
        .isEqualTo(change5.getCommit().getShortMessage());
    assertThat(headAfterSecondSubmit.getParent(0).getShortMessage())
        .isEqualTo(change2.getCommit().getShortMessage());

    assertPersonEquals(admin.getIdent(), headAfterSecondSubmit.getAuthorIdent());
    assertPersonEquals(serverIdent.get(), headAfterSecondSubmit.getCommitterIdent());

    // First change stays untouched.
    assertNew(change.getChangeId());

    // The two submit operations should have resulted in two ref-update events
    // and three change-merged events.
    assertRefUpdatedEvents(
        initialHead, headAfterFirstSubmit, headAfterFirstSubmit, headAfterSecondSubmit);
    assertChangeMergedEvents(
        change2.getChangeId(),
        headAfterFirstSubmit.name(),
        change3.getChangeId(),
        headAfterSecondSubmit.name(),
        change4.getChangeId(),
        headAfterSecondSubmit.name(),
        change5.getChangeId(),
        headAfterSecondSubmit.name());
  }

  @Test
  public void submitChangesAcrossRepos() throws Exception {
    Project.NameKey p1 = createProject("project-where-we-submit");
    Project.NameKey p2 = createProject("project-impacted-via-topic");
    Project.NameKey p3 = createProject("project-impacted-indirectly-via-topic");

    RevCommit initialHead2 = getRemoteHead(p2, "master");
    RevCommit initialHead3 = getRemoteHead(p3, "master");

    TestRepository<?> repo1 = cloneProject(p1);
    TestRepository<?> repo2 = cloneProject(p2);
    TestRepository<?> repo3 = cloneProject(p3);

    PushOneCommit.Result change1a =
        createChange(
            repo1,
            "master",
            "An ancestor of the change we want to submit",
            "a.txt",
            "1",
            "dependent-topic");
    PushOneCommit.Result change1b =
        createChange(
            repo1,
            "master",
            "We're interested in submitting this change",
            "a.txt",
            "2",
            "topic-to-submit");

    PushOneCommit.Result change2a =
        createChange(repo2, "master", "indirection level 1", "a.txt", "1", "topic-indirect");
    PushOneCommit.Result change2b =
        createChange(
            repo2, "master", "should go in with first change", "a.txt", "2", "dependent-topic");

    PushOneCommit.Result change3 =
        createChange(repo3, "master", "indirection level 2", "a.txt", "1", "topic-indirect");

    approve(change1a.getChangeId());
    approve(change2a.getChangeId());
    approve(change2b.getChangeId());
    approve(change3.getChangeId());

    // get a preview before submitting:
    Map<Branch.NameKey, ObjectId> preview = fetchFromSubmitPreview(change1b.getChangeId());
    submit(change1b.getChangeId());

    RevCommit tip1 = getRemoteLog(p1, "master").get(0);
    RevCommit tip2 = getRemoteLog(p2, "master").get(0);
    RevCommit tip3 = getRemoteLog(p3, "master").get(0);

    assertThat(tip1.getShortMessage()).isEqualTo(change1b.getCommit().getShortMessage());

    if (isSubmitWholeTopicEnabled()) {
      assertThat(tip2.getShortMessage()).isEqualTo(change2b.getCommit().getShortMessage());
      assertThat(tip3.getShortMessage()).isEqualTo(change3.getCommit().getShortMessage());

      // check that the preview matched what happened:
      assertThat(preview).hasSize(3);

      assertThat(preview).containsKey(new Branch.NameKey(p1, "refs/heads/master"));
      assertTrees(p1, preview);

      assertThat(preview).containsKey(new Branch.NameKey(p2, "refs/heads/master"));
      assertTrees(p2, preview);

      assertThat(preview).containsKey(new Branch.NameKey(p3, "refs/heads/master"));
      assertTrees(p3, preview);
    } else {
      assertThat(tip2.getShortMessage()).isEqualTo(initialHead2.getShortMessage());
      assertThat(tip3.getShortMessage()).isEqualTo(initialHead3.getShortMessage());
      assertThat(preview).hasSize(1);
      assertThat(preview.get(new Branch.NameKey(p1, "refs/heads/master"))).isNotNull();
    }
  }

  @Test
  public void submitChangesAcrossReposBlocked() throws Exception {
    Project.NameKey p1 = createProject("project-where-we-submit");
    Project.NameKey p2 = createProject("project-impacted-via-topic");
    Project.NameKey p3 = createProject("project-impacted-indirectly-via-topic");

    TestRepository<?> repo1 = cloneProject(p1);
    TestRepository<?> repo2 = cloneProject(p2);
    TestRepository<?> repo3 = cloneProject(p3);

    RevCommit initialHead1 = getRemoteHead(p1, "master");
    RevCommit initialHead2 = getRemoteHead(p2, "master");
    RevCommit initialHead3 = getRemoteHead(p3, "master");

    PushOneCommit.Result change1a =
        createChange(
            repo1,
            "master",
            "An ancestor of the change we want to submit",
            "a.txt",
            "1",
            "dependent-topic");
    PushOneCommit.Result change1b =
        createChange(
            repo1,
            "master",
            "we're interested to submit this change",
            "a.txt",
            "2",
            "topic-to-submit");

    PushOneCommit.Result change2a =
        createChange(repo2, "master", "indirection level 2a", "a.txt", "1", "topic-indirect");
    PushOneCommit.Result change2b =
        createChange(
            repo2, "master", "should go in with first change", "a.txt", "2", "dependent-topic");

    PushOneCommit.Result change3 =
        createChange(repo3, "master", "indirection level 2b", "a.txt", "1", "topic-indirect");

    // Create a merge conflict for change3 which is only indirectly related
    // via topics.
    repo3.reset(initialHead3);
    PushOneCommit.Result change3Conflict =
        createChange(repo3, "master", "conflicting change", "a.txt", "2\n2", "conflicting-topic");
    submit(change3Conflict.getChangeId());
    RevCommit tipConflict = getRemoteLog(p3, "master").get(0);
    assertThat(tipConflict.getShortMessage())
        .isEqualTo(change3Conflict.getCommit().getShortMessage());

    approve(change1a.getChangeId());
    approve(change2a.getChangeId());
    approve(change2b.getChangeId());
    approve(change3.getChangeId());

    if (isSubmitWholeTopicEnabled()) {
      String msg =
          "Failed to submit 5 changes due to the following problems:\n"
              + "Change "
              + change3.getChange().getId()
              + ": Change could not be "
              + "merged due to a path conflict. Please rebase the change locally "
              + "and upload the rebased commit for review.";

      // Get a preview before submitting:
      try (BinaryResult r = gApi.changes().id(change1b.getChangeId()).current().submitPreview()) {
        // We cannot just use the ExpectedException infrastructure as provided
        // by AbstractDaemonTest, as then we'd stop early and not test the
        // actual submit.

        fail("expected failure");
      } catch (RestApiException e) {
        assertThat(e.getMessage()).isEqualTo(msg);
      }
      submitWithConflict(change1b.getChangeId(), msg);
    } else {
      submit(change1b.getChangeId());
    }

    RevCommit tip1 = getRemoteLog(p1, "master").get(0);
    RevCommit tip2 = getRemoteLog(p2, "master").get(0);
    RevCommit tip3 = getRemoteLog(p3, "master").get(0);
    if (isSubmitWholeTopicEnabled()) {
      assertThat(tip1.getShortMessage()).isEqualTo(initialHead1.getShortMessage());
      assertThat(tip2.getShortMessage()).isEqualTo(initialHead2.getShortMessage());
      assertThat(tip3.getShortMessage()).isEqualTo(change3Conflict.getCommit().getShortMessage());
      assertNoSubmitter(change1a.getChangeId(), 1);
      assertNoSubmitter(change2a.getChangeId(), 1);
      assertNoSubmitter(change2b.getChangeId(), 1);
      assertNoSubmitter(change3.getChangeId(), 1);
    } else {
      assertThat(tip1.getShortMessage()).isEqualTo(change1b.getCommit().getShortMessage());
      assertThat(tip2.getShortMessage()).isEqualTo(initialHead2.getShortMessage());
      assertThat(tip3.getShortMessage()).isEqualTo(change3Conflict.getCommit().getShortMessage());
      assertNoSubmitter(change2a.getChangeId(), 1);
      assertNoSubmitter(change2b.getChangeId(), 1);
      assertNoSubmitter(change3.getChangeId(), 1);
    }
  }

  @Test
  public void submitWithMergedAncestorsOnOtherBranch() throws Exception {
    RevCommit initialHead = getRemoteHead();

    PushOneCommit.Result change1 =
        createChange(testRepo, "master", "base commit", "a.txt", "1", "");
    submit(change1.getChangeId());
    RevCommit headAfterFirstSubmit = getRemoteHead();

    gApi.projects().name(project.get()).branch("branch").create(new BranchInput());

    PushOneCommit.Result change2 =
        createChange(
            testRepo, "master", "We want to commit this to master first", "a.txt", "2", "");

    submit(change2.getChangeId());

    RevCommit headAfterSecondSubmit = getRemoteLog(project, "master").get(0);
    assertThat(headAfterSecondSubmit.getShortMessage())
        .isEqualTo(change2.getCommit().getShortMessage());

    RevCommit tip2 = getRemoteLog(project, "branch").get(0);
    assertThat(tip2.getShortMessage()).isEqualTo(change1.getCommit().getShortMessage());

    PushOneCommit.Result change3 =
        createChange(
            testRepo,
            "branch",
            "This commit is based on master, which includes change2, "
                + "but is targeted at branch, which doesn't include it.",
            "a.txt",
            "3",
            "");

    submit(change3.getChangeId());

    List<RevCommit> log3 = getRemoteLog(project, "branch");
    assertThat(log3.get(0).getShortMessage()).isEqualTo(change3.getCommit().getShortMessage());
    assertThat(log3.get(1).getShortMessage()).isEqualTo(change2.getCommit().getShortMessage());

    assertRefUpdatedEvents(
        initialHead, headAfterFirstSubmit, headAfterFirstSubmit, headAfterSecondSubmit);
    assertChangeMergedEvents(
        change1.getChangeId(),
        headAfterFirstSubmit.name(),
        change2.getChangeId(),
        headAfterSecondSubmit.name());
  }

  @Test
  public void submitWithOpenAncestorsOnOtherBranch() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change1 =
        createChange(testRepo, "master", "base commit", "a.txt", "1", "");
    submit(change1.getChangeId());
    RevCommit headAfterFirstSubmit = getRemoteHead();

    gApi.projects().name(project.get()).branch("branch").create(new BranchInput());

    PushOneCommit.Result change2 =
        createChange(
            testRepo, "master", "We want to commit this to master first", "a.txt", "2", "");

    approve(change2.getChangeId());

    RevCommit tip1 = getRemoteLog(project, "master").get(0);
    assertThat(tip1.getShortMessage()).isEqualTo(change1.getCommit().getShortMessage());

    RevCommit tip2 = getRemoteLog(project, "branch").get(0);
    assertThat(tip2.getShortMessage()).isEqualTo(change1.getCommit().getShortMessage());

    PushOneCommit.Result change3a =
        createChange(
            testRepo,
            "branch",
            "This commit is based on change2 pending for master, "
                + "but is targeted itself at branch, which doesn't include it.",
            "a.txt",
            "3",
            "a-topic-here");

    Project.NameKey p3 = createProject("project-related-to-change3");
    TestRepository<?> repo3 = cloneProject(p3);
    RevCommit repo3Head = getRemoteHead(p3, "master");
    PushOneCommit.Result change3b =
        createChange(
            repo3,
            "master",
            "some accompanying changes for change3a in another repo tied together via topic",
            "a.txt",
            "1",
            "a-topic-here");
    approve(change3b.getChangeId());

    String cnt = isSubmitWholeTopicEnabled() ? "2 changes" : "1 change";
    submitWithConflict(
        change3a.getChangeId(),
        "Failed to submit "
            + cnt
            + " due to the following problems:\n"
            + "Change "
            + change3a.getChange().getId()
            + ": depends on change that"
            + " was not submitted");

    RevCommit tipbranch = getRemoteLog(project, "branch").get(0);
    assertThat(tipbranch.getShortMessage()).isEqualTo(change1.getCommit().getShortMessage());

    RevCommit tipmaster = getRemoteLog(p3, "master").get(0);
    assertThat(tipmaster.getShortMessage()).isEqualTo(repo3Head.getShortMessage());

    assertRefUpdatedEvents(initialHead, headAfterFirstSubmit);
    assertChangeMergedEvents(change1.getChangeId(), headAfterFirstSubmit.name());
  }

  @Test
  public void gerritWorkflow() throws Exception {
    RevCommit initialHead = getRemoteHead();

    // We'll setup a master and a stable branch.
    // Then we create a change to be applied to master, which is
    // then cherry picked back to stable. The stable branch will
    // be merged up into master again.
    gApi.projects().name(project.get()).branch("stable").create(new BranchInput());

    // Push a change to master
    PushOneCommit push =
        pushFactory.create(db, user.getIdent(), testRepo, "small fix", "a.txt", "2");
    PushOneCommit.Result change = push.to("refs/for/master");
    submit(change.getChangeId());
    RevCommit headAfterFirstSubmit = getRemoteLog(project, "master").get(0);
    assertThat(headAfterFirstSubmit.getShortMessage())
        .isEqualTo(change.getCommit().getShortMessage());

    // Now cherry pick to stable
    CherryPickInput in = new CherryPickInput();
    in.destination = "stable";
    in.message = "This goes to stable as well\n" + headAfterFirstSubmit.getFullMessage();
    ChangeApi orig = gApi.changes().id(change.getChangeId());
    String cherryId = orig.current().cherryPick(in).id();
    gApi.changes().id(cherryId).current().review(ReviewInput.approve());
    gApi.changes().id(cherryId).current().submit();

    // Create the merge locally
    RevCommit stable = getRemoteHead(project, "stable");
    RevCommit master = getRemoteHead(project, "master");
    testRepo.git().fetch().call();
    testRepo.git().branchCreate().setName("stable").setStartPoint(stable).call();
    testRepo.git().branchCreate().setName("master").setStartPoint(master).call();

    RevCommit merge =
        testRepo
            .commit()
            .parent(master)
            .parent(stable)
            .message("Merge stable into master")
            .insertChangeId()
            .create();

    testRepo.branch("refs/heads/master").update(merge);
    testRepo.git().push().setRefSpecs(new RefSpec("refs/heads/master:refs/for/master")).call();

    String changeId = GitUtil.getChangeId(testRepo, merge).get();
    approve(changeId);
    submit(changeId);
    RevCommit headAfterSecondSubmit = getRemoteLog(project, "master").get(0);
    assertThat(headAfterSecondSubmit.getShortMessage()).isEqualTo(merge.getShortMessage());

    assertRefUpdatedEvents(
        initialHead, headAfterFirstSubmit, headAfterFirstSubmit, headAfterSecondSubmit);
    assertChangeMergedEvents(
        change.getChangeId(), headAfterFirstSubmit.name(), changeId, headAfterSecondSubmit.name());
  }

  @Test
  public void openChangeForTargetBranchPreventsMerge() throws Exception {
    gApi.projects().name(project.get()).branch("stable").create(new BranchInput());

    // Propose a change for master, but leave it open for master!
    PushOneCommit change =
        pushFactory.create(db, user.getIdent(), testRepo, "small fix", "a.txt", "2");
    PushOneCommit.Result change2result = change.to("refs/for/master");

    // Now cherry pick to stable
    CherryPickInput in = new CherryPickInput();
    in.destination = "stable";
    in.message = "it goes to stable branch";
    ChangeApi orig = gApi.changes().id(change2result.getChangeId());
    ChangeApi cherry = orig.current().cherryPick(in);
    cherry.current().review(ReviewInput.approve());
    cherry.current().submit();

    // Create a commit locally
    testRepo.git().fetch().setRefSpecs(new RefSpec("refs/heads/stable")).call();

    PushOneCommit.Result change3 = createChange(testRepo, "stable", "test", "a.txt", "3", "");
    submitWithConflict(
        change3.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
            + "Change "
            + change3.getPatchSetId().getParentKey().get()
            + ": depends on change that was not submitted");

    assertRefUpdatedEvents();
    assertChangeMergedEvents();
  }

  @Test
  public void testPreviewSubmitTgz() throws Exception {
    Project.NameKey p1 = createProject("project-name");

    TestRepository<?> repo1 = cloneProject(p1);
    PushOneCommit.Result change1 = createChange(repo1, "master", "test", "a.txt", "1", "topic");
    approve(change1.getChangeId());

    // get a preview before submitting:
    File tempfile;
    try (BinaryResult request =
        gApi.changes().id(change1.getChangeId()).current().submitPreview("tgz")) {
      assertThat(request.getContentType()).isEqualTo("application/x-gzip");
      tempfile = File.createTempFile("test", null);
      request.writeTo(Files.newOutputStream(tempfile.toPath()));
    }

    InputStream is = new GZIPInputStream(Files.newInputStream(tempfile.toPath()));

    List<String> untarredFiles = new ArrayList<>();
    try (TarArchiveInputStream tarInputStream =
        (TarArchiveInputStream) new ArchiveStreamFactory().createArchiveInputStream("tar", is)) {
      TarArchiveEntry entry = null;
      while ((entry = (TarArchiveEntry) tarInputStream.getNextEntry()) != null) {
        untarredFiles.add(entry.getName());
      }
    }
    assertThat(untarredFiles).containsExactly(name("project-name") + ".git");
  }
}
