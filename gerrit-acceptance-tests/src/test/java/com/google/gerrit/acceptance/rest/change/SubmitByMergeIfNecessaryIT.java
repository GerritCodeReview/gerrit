package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

import java.util.List;

public class SubmitByMergeIfNecessaryIT extends AbstractSubmitByMerge {

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.MERGE_IF_NECESSARY;
  }

  @Test
  public void submitWithFastForward() throws Exception {
    RevCommit oldHead = getRemoteHead();
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());
    RevCommit head = getRemoteHead();
    assertThat(head.getId()).isEqualTo(change.getCommitId());
    assertThat(head.getParent(0)).isEqualTo(oldHead);
    assertSubmitter(change.getChangeId(), 1);
    assertPersonEquals(admin.getIdent(), head.getAuthorIdent());
    assertPersonEquals(admin.getIdent(), head.getCommitterIdent());
  }

  @Test
  public void submitMultipleChanges() throws Exception {
    RevCommit initialHead = getRemoteHead();

    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "b", "b");

    testRepo.reset(initialHead);
    PushOneCommit.Result change3 = createChange("Change 3", "c", "c");

    testRepo.reset(initialHead);
    PushOneCommit.Result change4 = createChange("Change 4", "d", "d");

    // Change 2 stays untouched.
    approve(change2.getChangeId());
    // Change 3 is a fast-forward, no need to merge.
    submit(change3.getChangeId());

    RevCommit tip = getRemoteLog().get(0);
    assertThat(tip.getShortMessage()).isEqualTo(
        change3.getCommit().getShortMessage());
    assertThat(tip.getParent(0).getId()).isEqualTo(
        initialHead.getId());
    assertPersonEquals(admin.getIdent(), tip.getAuthorIdent());
    assertPersonEquals(admin.getIdent(), tip.getCommitterIdent());

    // We need to merge change 4.
    submit(change4.getChangeId());

    tip = getRemoteLog().get(0);
    assertThat(tip.getParent(1).getShortMessage()).isEqualTo(
        change4.getCommit().getShortMessage());
    assertThat(tip.getParent(0).getShortMessage()).isEqualTo(
        change3.getCommit().getShortMessage());

    assertPersonEquals(admin.getIdent(), tip.getAuthorIdent());
    assertPersonEquals(serverIdent.get(), tip.getCommitterIdent());

    assertNew(change2.getChangeId());
  }

  @Test
  public void submitWithOutdatedParentsBlocked() throws Exception {
    RevCommit initialHead = getRemoteHead();
    PushOneCommit.Result change1 = pushFactory.create(db, admin.getIdent(),
        testRepo, "Change 1", "b", "b")
        .to("refs/for/master");
    change1.assertOkStatus();
    approve(change1.getChangeId());

    PushOneCommit.Result change2 = pushFactory.create(db, admin.getIdent(),
        testRepo, "Change 2", "c", "c")
        .to("refs/for/master");
    change2.assertOkStatus();
    approve(change2.getChangeId());

    testRepo.reset("HEAD^");
    PushOneCommit.Result change1b = pushFactory.create(db, admin.getIdent(),
        testRepo, "Change 1b", "d", "d",
        change1.getChangeId())
        .to("refs/for/master");
    change1b.assertOkStatus();
    approve(change1b.getChangeId());

    // Change 2 depends on an outdated revision of change 1, which should
    // prevent submitting change 2. change 1 is approved in both revisions,
    // so the error triggered should come from the outdated revision.
    submitWithConflict(change2.getChangeId(), "Merge Conflict");

    RevCommit tip1  = getRemoteLog(project, "master").get(0);
    assertThat(tip1.getShortMessage()).isEqualTo(
        initialHead.getShortMessage());
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

    PushOneCommit.Result change1a = createChange(repo1, "master",
        "An ancestor of the change we want to submit",
        "a.txt", "1", "dependent-topic");
    PushOneCommit.Result change1b = createChange(repo1, "master",
        "We're interested in submitting this change",
        "a.txt", "2", "topic-to-submit");

    PushOneCommit.Result change2a = createChange(repo2, "master",
        "indirection level 1",
        "a.txt", "1", "topic-indirect");
    PushOneCommit.Result change2b = createChange(repo2, "master",
        "should go in with first change",
        "a.txt", "2", "dependent-topic");

    PushOneCommit.Result change3 = createChange(repo3, "master",
        "indirection level 2",
        "a.txt", "1", "topic-indirect");

    approve(change1a.getChangeId());
    approve(change2a.getChangeId());
    approve(change2b.getChangeId());
    approve(change3.getChangeId());
    submit(change1b.getChangeId());

    RevCommit tip1  = getRemoteLog(p1, "master").get(0);
    RevCommit tip2  = getRemoteLog(p2, "master").get(0);
    RevCommit tip3  = getRemoteLog(p3, "master").get(0);

    assertThat(tip1.getShortMessage()).isEqualTo(
        change1b.getCommit().getShortMessage());

    if (isSubmitWholeTopicEnabled()) {
      assertThat(tip2.getShortMessage()).isEqualTo(
          change2b.getCommit().getShortMessage());
      assertThat(tip3.getShortMessage()).isEqualTo(
          change3.getCommit().getShortMessage());
    } else {
      assertThat(tip2.getShortMessage()).isEqualTo(
          initialHead2.getShortMessage());
      assertThat(tip3.getShortMessage()).isEqualTo(
          initialHead3.getShortMessage());
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

    PushOneCommit.Result change1a = createChange(repo1, "master",
        "An ancestor of the change we want to submit",
        "a.txt", "1", "dependent-topic");
    PushOneCommit.Result change1b = createChange(repo1, "master",
        "we're interested to submit this change",
        "a.txt", "2", "topic-to-submit");

    PushOneCommit.Result change2a = createChange(repo2, "master",
        "indirection level 2a",
        "a.txt", "1", "topic-indirect");
    PushOneCommit.Result change2b = createChange(repo2, "master",
        "should go in with first change",
        "a.txt", "2", "dependent-topic");

    PushOneCommit.Result change3 = createChange(repo3, "master",
        "indirection level 2b",
        "a.txt", "1", "topic-indirect");

    // Create a merge conflict for change3 which is only indirectly related
    // via topics.
    repo3.reset(initialHead3);
    PushOneCommit.Result change3Conflict = createChange(repo3, "master",
        "conflicting change",
        "a.txt", "2\n2", "conflicting-topic");
    submit(change3Conflict.getChangeId());
    RevCommit tipConflict = getRemoteLog(p3, "master").get(0);
    assertThat(tipConflict.getShortMessage()).isEqualTo(
        change3Conflict.getCommit().getShortMessage());

    approve(change1a.getChangeId());
    approve(change2a.getChangeId());
    approve(change2b.getChangeId());
    approve(change3.getChangeId());

    if (isSubmitWholeTopicEnabled()) {
      submitWithConflict(change1b.getChangeId(),
          "Cannot merge " + change3.getCommit().name() + "\n" +
          "Change could not be merged due to a path conflict.\n\n" +
          "Please rebase the change locally " +
          "and upload the rebased commit for review.");
    } else {
      submit(change1b.getChangeId());
    }

    RevCommit tip1  = getRemoteLog(p1, "master").get(0);
    RevCommit tip2  = getRemoteLog(p2, "master").get(0);
    RevCommit tip3  = getRemoteLog(p3, "master").get(0);
    if (isSubmitWholeTopicEnabled()) {
      assertThat(tip1.getShortMessage()).isEqualTo(
          initialHead1.getShortMessage());
      assertThat(tip2.getShortMessage()).isEqualTo(
          initialHead2.getShortMessage());
      assertThat(tip3.getShortMessage()).isEqualTo(
          change3Conflict.getCommit().getShortMessage());
      assertNoSubmitter(change1a.getChangeId(), 1);
      assertNoSubmitter(change2a.getChangeId(), 1);
      assertNoSubmitter(change2b.getChangeId(), 1);
      assertNoSubmitter(change3.getChangeId(), 1);
    } else {
      assertThat(tip1.getShortMessage()).isEqualTo(
          change1b.getCommit().getShortMessage());
      assertThat(tip2.getShortMessage()).isEqualTo(
          initialHead2.getShortMessage());
      assertThat(tip3.getShortMessage()).isEqualTo(
          change3Conflict.getCommit().getShortMessage());
      assertNoSubmitter(change2a.getChangeId(), 1);
      assertNoSubmitter(change2b.getChangeId(), 1);
      assertNoSubmitter(change3.getChangeId(), 1);
    }
  }

  @Test
  public void submitWithMergedAncestorsOnOtherBranch() throws Exception {
    PushOneCommit.Result change1 = createChange(testRepo,  "master",
        "base commit",
        "a.txt", "1", "");
    submit(change1.getChangeId());

    gApi.projects()
        .name(project.get())
        .branch("branch")
        .create(new BranchInput());

    PushOneCommit.Result change2 = createChange(testRepo,  "master",
        "We want to commit this to master first",
        "a.txt", "2", "");

    submit(change2.getChangeId());

    RevCommit tip1 = getRemoteLog(project, "master").get(0);
    assertThat(tip1.getShortMessage()).isEqualTo(
        change2.getCommit().getShortMessage());

    RevCommit tip2 = getRemoteLog(project, "branch").get(0);
    assertThat(tip2.getShortMessage()).isEqualTo(
        change1.getCommit().getShortMessage());

    PushOneCommit.Result change3 = createChange(testRepo,  "branch",
        "This commit is based on master, which includes change2, "
        + "but is targeted at branch, which doesn't include it.",
        "a.txt", "3", "");

    submit(change3.getChangeId());

    List<RevCommit> log3 = getRemoteLog(project, "branch");
    assertThat(log3.get(0).getShortMessage()).isEqualTo(
        change3.getCommit().getShortMessage());
    assertThat(log3.get(1).getShortMessage()).isEqualTo(
        change2.getCommit().getShortMessage());
  }

  @Test
  public void submitWithOpenAncestorsOnOtherBranch() throws Exception {
    PushOneCommit.Result change1 = createChange(testRepo,  "master",
        "base commit",
        "a.txt", "1", "");
    submit(change1.getChangeId());

    gApi.projects()
        .name(project.get())
        .branch("branch")
        .create(new BranchInput());

    PushOneCommit.Result change2 = createChange(testRepo,  "master",
        "We want to commit this to master first",
        "a.txt", "2", "");

    approve(change2.getChangeId());

    RevCommit tip1 = getRemoteLog(project, "master").get(0);
    assertThat(tip1.getShortMessage()).isEqualTo(
        change1.getCommit().getShortMessage());

    RevCommit tip2 = getRemoteLog(project, "branch").get(0);
    assertThat(tip2.getShortMessage()).isEqualTo(
        change1.getCommit().getShortMessage());

    PushOneCommit.Result change3a = createChange(testRepo,  "branch",
        "This commit is based on change2 pending for master, "
        + "but is targeted itself at branch, which doesn't include it.",
        "a.txt", "3", "a-topic-here");

    Project.NameKey p3 = createProject("project-related-to-change3");
    TestRepository<?> repo3 = cloneProject(p3);
    RevCommit initialHead = getRemoteHead(p3, "master");
    PushOneCommit.Result change3b = createChange(repo3, "master",
        "some accompanying changes for change3a in another repo "
        + "tied together via topic",
        "a.txt", "1", "a-topic-here");
    approve(change3b.getChangeId());

    submitWithConflict(change3a.getChangeId(), "Merge Conflict");

    RevCommit tipbranch = getRemoteLog(project, "branch").get(0);
    assertThat(tipbranch.getShortMessage()).isEqualTo(
        change1.getCommit().getShortMessage());

    RevCommit tipmaster = getRemoteLog(p3, "master").get(0);
    assertThat(tipmaster.getShortMessage()).isEqualTo(
        initialHead.getShortMessage());
  }
}
