package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.PushOneCommit;
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

    PushOneCommit.Result change1a = createChange(repo1,
        "An ancestor of the change we want to submit",
        "a.txt", "1", "dependent-topic");
    PushOneCommit.Result change1b = createChange(repo1,
        "we're interested to submit this change",
        "a.txt", "2", "topic-to-submit");

    PushOneCommit.Result change2a = createChange(repo2,
        "indirection level 1",
        "a.txt", "1", "topic-indirect");
    PushOneCommit.Result change2b = createChange(repo2,
        "should go in with first change",
        "a.txt", "2", "dependent-topic");

    PushOneCommit.Result change3 = createChange(repo3,
        "indirection level 2",
        "a.txt", "1", "topic-indirect");

    submitStatusOnly(change1a.getChangeId());
    submitStatusOnly(change2a.getChangeId());
    submitStatusOnly(change2b.getChangeId());
    submitStatusOnly(change3.getChangeId());
    submit(change1b.getChangeId());

    List<RevCommit> log1 = getRemoteLog(p1);
    RevCommit tip1 = log1.get(0);
    assertThat(tip1.getShortMessage()).isEqualTo(
        change1b.getCommit().getShortMessage());

    if (isSubmitWholeTopicEnabled()) {
      List<RevCommit> log2 = getRemoteLog(p2);
      RevCommit tip2 = log2.get(0);
      assertThat(tip2.getShortMessage()).isEqualTo(
          change2b.getCommit().getShortMessage());

      List<RevCommit> log3 = getRemoteLog(p3);
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

    PushOneCommit.Result change1a = createChange(repo1,
        "An ancestor of the change we want to submit",
        "a.txt", "1", "dependent-topic");
    PushOneCommit.Result change1b = createChange(repo1,
        "we're interested to submit this change",
        "a.txt", "2", "topic-to-submit");

    PushOneCommit.Result change2a = createChange(repo2,
        "indirection level 2a",
        "a.txt", "1", "topic-indirect");
    PushOneCommit.Result change2b = createChange(repo2,
        "should go in with first change",
        "a.txt", "2", "dependent-topic");

    PushOneCommit.Result change3 = createChange(repo3,
        "indirection level 2b",
        "a.txt", "1", "topic-indirect");

    repo3.reset(initialHead);
    PushOneCommit.Result change3Conflict = createChange(repo3,
        "conflicting change",
        "a.txt", "2\n2", "conflicting-topic");

    submit(change3Conflict.getChangeId());
    List<RevCommit> logConflict = getRemoteLog(p3);
    RevCommit tipConflict = logConflict.get(0);
    assertThat(tipConflict.getShortMessage()).isEqualTo(
        change3Conflict.getCommit().getShortMessage());

    submitStatusOnly(change1a.getChangeId());
    submitStatusOnly(change2a.getChangeId());
    submitStatusOnly(change2b.getChangeId());
    submitStatusOnly(change3.getChangeId());

    if (isSubmitWholeTopicEnabled()) {
      submitWithConflict(change1b.getChangeId());

      List<RevCommit> log1 = getRemoteLog(p1);
      RevCommit tip1 = log1.get(0);
      assertThat(tip1.getShortMessage()).isNotEqualTo(
          change1b.getCommit().getShortMessage());

      List<RevCommit> log2 = getRemoteLog(p2);
      RevCommit tip2 = log2.get(0);
      assertThat(tip2.getShortMessage()).isNotEqualTo(
          change2b.getCommit().getShortMessage());

      List<RevCommit> log3 = getRemoteLog(p3);
      RevCommit tip3 = log3.get(0);
      assertThat(tip3.getShortMessage()).isNotEqualTo(
          change3.getCommit().getShortMessage());
    } else {
      submit(change1b.getChangeId());

      List<RevCommit> log1 = getRemoteLog(p1);
      RevCommit tip1 = log1.get(0);
      assertThat(tip1.getShortMessage()).isEqualTo(
          change1b.getCommit().getShortMessage());
    }
  }
}
