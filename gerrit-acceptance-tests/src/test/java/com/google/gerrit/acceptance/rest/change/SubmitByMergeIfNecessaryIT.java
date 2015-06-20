package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.SubmitType;
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

    submitStatusOnly(change2.getChangeId());
    submitStatusOnly(change3.getChangeId());
    submit(change4.getChangeId());

    List<RevCommit> log = getRemoteLog();
    RevCommit tip = log.get(0);
    assertThat(tip.getParent(1).getShortMessage()).isEqualTo(
        change4.getCommit().getShortMessage());

    tip = tip.getParent(0);
    assertThat(tip.getParent(1).getShortMessage()).isEqualTo(
        change3.getCommit().getShortMessage());

    tip = tip.getParent(0);
    assertThat(tip.getShortMessage()).isEqualTo(
        change2.getCommit().getShortMessage());

    assertThat(tip.getParent(0).getId()).isEqualTo(initialHead.getId());
  }

  @Test
  public void submitChangesAcrossRepos() throws Exception {
    Project.NameKey p1 = createProject("project-where-we-submit");
    Project.NameKey p2 = createProject("project-impacted-via-topic");
    Project.NameKey p3 = createProject("project-impacted-indirectly-via-topic");

    TestRepository<?> repo1 = cloneProject(p1);
    TestRepository<?> repo2 = cloneProject(p2);
    TestRepository<?> repo3 = cloneProject(p3);

    PushOneCommit.Result change1a = createChange(repo1, "master",
        "An ancestor of the change we want to submit",
        "a.txt", "1", "dependent-topic");
    PushOneCommit.Result change1b = createChange(repo1, "master",
        "we're interested to submit this change",
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

    submitStatusOnly(change1a.getChangeId());
    submitStatusOnly(change2a.getChangeId());
    submitStatusOnly(change2b.getChangeId());
    submitStatusOnly(change3.getChangeId());
    submit(change1b.getChangeId());

    List<RevCommit> log1 = getRemoteLog(p1, "master");
    RevCommit tip1 = log1.get(0);
    assertThat(tip1.getShortMessage()).isEqualTo(
        change1b.getCommit().getShortMessage());

    if (isSubmitWholeTopicEnabled()) {
      List<RevCommit> log2 = getRemoteLog(p2, "master");
      RevCommit tip2 = log2.get(0);
      assertThat(tip2.getShortMessage()).isEqualTo(
          change2b.getCommit().getShortMessage());

      List<RevCommit> log3 = getRemoteLog(p3, "master");
      RevCommit tip3 = log3.get(0);
      assertThat(tip3.getShortMessage()).isEqualTo(
          change3.getCommit().getShortMessage());
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
    RevCommit initialHead = getRemoteHead(p3);

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

    repo3.reset(initialHead);
    PushOneCommit.Result change3Conflict = createChange(repo3, "master",
        "conflicting change",
        "a.txt", "2\n2", "conflicting-topic");

    submit(change3Conflict.getChangeId());
    List<RevCommit> logConflict = getRemoteLog(p3, "master");
    RevCommit tipConflict = logConflict.get(0);
    assertThat(tipConflict.getShortMessage()).isEqualTo(
        change3Conflict.getCommit().getShortMessage());

    submitStatusOnly(change1a.getChangeId());
    submitStatusOnly(change2a.getChangeId());
    submitStatusOnly(change2b.getChangeId());
    submitStatusOnly(change3.getChangeId());

    if (isSubmitWholeTopicEnabled()) {
      submitWithConflict(change1b.getChangeId());

      List<RevCommit> log1 = getRemoteLog(p1, "master");
      RevCommit tip1 = log1.get(0);
      assertThat(tip1.getShortMessage()).isNotEqualTo(
          change1b.getCommit().getShortMessage());

      List<RevCommit> log2 = getRemoteLog(p2, "master");
      RevCommit tip2 = log2.get(0);
      assertThat(tip2.getShortMessage()).isNotEqualTo(
          change2b.getCommit().getShortMessage());

      List<RevCommit> log3 = getRemoteLog(p3, "master");
      RevCommit tip3 = log3.get(0);
      assertThat(tip3.getShortMessage()).isNotEqualTo(
          change3.getCommit().getShortMessage());
    } else {
      submit(change1b.getChangeId());

      List<RevCommit> log1 = getRemoteLog(p1, "master");
      RevCommit tip1 = log1.get(0);
      assertThat(tip1.getShortMessage()).isEqualTo(
          change1b.getCommit().getShortMessage());
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

    List<RevCommit> log1 = getRemoteLog(project, "master");
    RevCommit tip1 = log1.get(0);
    assertThat(tip1.getShortMessage()).isEqualTo(
        change2.getCommit().getShortMessage());

    List<RevCommit> log2 = getRemoteLog(project, "branch");
    RevCommit tip2 = log2.get(0);
    assertThat(tip2.getShortMessage()).isEqualTo(
        change1.getCommit().getShortMessage());


    PushOneCommit.Result change3 = createChange(testRepo,  "branch",
        "This commit is based on master, which includes change2,"
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

    submitStatusOnly(change2.getChangeId());

    List<RevCommit> log1 = getRemoteLog(project, "master");
    assertThat(log1.get(0).getShortMessage()).isEqualTo(
        change1.getCommit().getShortMessage());

    List<RevCommit> log2 = getRemoteLog(project, "branch");
    assertThat(log2.get(0).getShortMessage()).isEqualTo(
        change1.getCommit().getShortMessage());

    PushOneCommit.Result change3a = createChange(testRepo,  "branch",
        "This commit is based on change2 pending for master,"
        + "but is targeted itself at branch, which doesn't include it.",
        "a.txt", "3", "a-topic-here");

    Project.NameKey p3 = createProject("project-related-to-change3");
    TestRepository<?> repo3 = cloneProject(p3);
    RevCommit initialHead = getRemoteHead(p3);
    PushOneCommit.Result change3b = createChange(repo3, "master",
        "some accompanying changes for change3a in another repo"
        + "tied together via topic",
        "a.txt", "1", "a-topic-here");
    submitStatusOnly(change3b.getChangeId());

    submitWithConflict(change3a.getChangeId());

    List<RevCommit> log3a = getRemoteLog(project, "branch");
    assertThat(log3a.get(0).getShortMessage()).isEqualTo(
        change1.getCommit().getShortMessage());

    List<RevCommit> log3b = getRemoteLog(p3, "master");
    assertThat(log3b.get(0).getShortMessage()).isEqualTo(
        initialHead.getShortMessage());
  }
}
