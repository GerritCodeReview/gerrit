package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.TestProjectInput;
import com.google.gerrit.extensions.api.changes.ChangeApi;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.data.RefUpdateAttribute;
import com.google.gerrit.server.events.RefEvent;
import com.google.gerrit.server.events.RefUpdatedEvent;

import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.RefSpec;
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
    assertThat(head.getId()).isEqualTo(change.getCommit());
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
    PushOneCommit.Result change5 = createChange("Change 5", "d", "d");

    // Change 2 stays untouched.
    approve(change2.getChangeId());
    // Change 3 is a fast-forward, no need to merge.
    submit(change3.getChangeId());

    RevCommit headAfterFirstSubmit = getRemoteLog().get(0);
    assertThat(headAfterFirstSubmit.getShortMessage()).isEqualTo(
        change3.getCommit().getShortMessage());
    assertThat(headAfterFirstSubmit.getParent(0).getId()).isEqualTo(
        initialHead.getId());
    assertPersonEquals(admin.getIdent(), headAfterFirstSubmit.getAuthorIdent());
    assertPersonEquals(admin.getIdent(), headAfterFirstSubmit.getCommitterIdent());

    // We need to merge changes 4 and 5.
    approve(change4.getChangeId());
    submit(change5.getChangeId());

    RevCommit headAfterSecondSubmit = getRemoteLog().get(0);
    assertThat(headAfterSecondSubmit.getParent(1).getShortMessage()).isEqualTo(
        change5.getCommit().getShortMessage());
    assertThat(headAfterSecondSubmit.getParent(0).getShortMessage()).isEqualTo(
        change3.getCommit().getShortMessage());

    assertPersonEquals(admin.getIdent(), headAfterSecondSubmit.getAuthorIdent());
    assertPersonEquals(serverIdent.get(), headAfterSecondSubmit.getCommitterIdent());

    assertNew(change2.getChangeId());

    // The two submit operations should have resulted in two ref-update events
    List<RefEvent> refUpdates = eventRecorder.getRefUpdates(
        project.get(), "refs/heads/master", 2);

    RefUpdateAttribute refUpdate =
        ((RefUpdatedEvent)(refUpdates.get(0))).refUpdate.get();
    assertThat(refUpdate.oldRev).isEqualTo(initialHead.name());
    assertThat(refUpdate.newRev).isEqualTo(headAfterFirstSubmit.name());

    refUpdate = ((RefUpdatedEvent)(refUpdates.get(1))).refUpdate.get();
    assertThat(refUpdate.oldRev).isEqualTo(headAfterFirstSubmit.name());
    assertThat(refUpdate.newRev).isEqualTo(headAfterSecondSubmit.name());

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
          "Failed to submit 5 changes due to the following problems:\n" +
          "Change " + change3.getChange().getId() + ": Change could not be " +
          "merged due to a path conflict. Please rebase the change locally " +
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
    PushOneCommit.Result change1 = createChange(testRepo, "master",
        "base commit",
        "a.txt", "1", "");
    submit(change1.getChangeId());

    gApi.projects()
        .name(project.get())
        .branch("branch")
        .create(new BranchInput());

    PushOneCommit.Result change2 = createChange(testRepo, "master",
        "We want to commit this to master first",
        "a.txt", "2", "");

    approve(change2.getChangeId());

    RevCommit tip1 = getRemoteLog(project, "master").get(0);
    assertThat(tip1.getShortMessage()).isEqualTo(
        change1.getCommit().getShortMessage());

    RevCommit tip2 = getRemoteLog(project, "branch").get(0);
    assertThat(tip2.getShortMessage()).isEqualTo(
        change1.getCommit().getShortMessage());

    PushOneCommit.Result change3a = createChange(testRepo, "branch",
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

    String cnt = isSubmitWholeTopicEnabled() ? "2 changes" : "1 change";
    submitWithConflict(change3a.getChangeId(),
        "Failed to submit " + cnt + " due to the following problems:\n"
        + "Change " + change3a.getChange().getId() + ": depends on change that"
        + " was not submitted");

    RevCommit tipbranch = getRemoteLog(project, "branch").get(0);
    assertThat(tipbranch.getShortMessage()).isEqualTo(
        change1.getCommit().getShortMessage());

    RevCommit tipmaster = getRemoteLog(p3, "master").get(0);
    assertThat(tipmaster.getShortMessage()).isEqualTo(
        initialHead.getShortMessage());
  }

  @Test
  public void testGerritWorkflow() throws Exception {
    // We'll setup a master and a stable branch.
    // Then we create a change to be applied to master, which is
    // then cherry picked back to stable. The stable branch will
    // be merged up into master again.
    gApi.projects()
        .name(project.get())
        .branch("stable")
        .create(new BranchInput());

    // Push a change to master
    PushOneCommit change2 =
        pushFactory.create(db, user.getIdent(), testRepo,
            "small fix", "a.txt", "2");
    PushOneCommit.Result change2result = change2.to("refs/for/master");
    submit(change2result.getChangeId());
    RevCommit tipmaster = getRemoteLog(project, "master").get(0);
    assertThat(tipmaster.getShortMessage()).isEqualTo(
        change2result.getCommit().getShortMessage());

    // Now cherry pick to stable
    CherryPickInput in = new CherryPickInput();
    in.destination = "stable";
    in.message = "This goes to stable as well\n" + tipmaster.getFullMessage();
    ChangeApi orig = gApi.changes()
        .id(change2result.getChangeId());
    String cherryId = orig.current().cherryPick(in).id();
    gApi.changes().id(cherryId).current().review(ReviewInput.approve());
    gApi.changes().id(cherryId).current().submit();

    // Create the merge locally
    RevCommit stable = getRemoteHead(project, "stable");
    RevCommit master = getRemoteHead(project, "master");
    testRepo.git().fetch().call();
    testRepo.git()
        .branchCreate()
        .setName("stable")
        .setStartPoint(stable)
        .call();
    testRepo.git()
        .branchCreate()
        .setName("master")
        .setStartPoint(master)
        .call();

    RevCommit merge = testRepo.commit()
        .parent(master)
        .parent(stable)
        .message("Merge stable into master")
        .insertChangeId()
        .create();

    testRepo.branch("refs/heads/master").update(merge);
    testRepo.git().push()
        .setRefSpecs(new RefSpec("refs/heads/master:refs/for/master"))
        .call();

    String changeId = GitUtil.getChangeId(testRepo, merge).get();
    approve(changeId);
    submit(changeId);
    tipmaster = getRemoteLog(project, "master").get(0);
    assertThat(tipmaster.getShortMessage()).isEqualTo(merge.getShortMessage());
  }

  @Test
  public void openChangeForTargetBranchPreventsMerge() throws Exception {
    gApi.projects()
        .name(project.get())
        .branch("stable")
        .create(new BranchInput());

    // Propose a change for master, but leave it open for master!
    PushOneCommit change2 =
        pushFactory.create(db, user.getIdent(), testRepo,
            "small fix", "a.txt", "2");
    PushOneCommit.Result change2result = change2.to("refs/for/master");

    // Now cherry pick to stable
    CherryPickInput in = new CherryPickInput();
    in.destination = "stable";
    in.message = "it goes to stable branch";
    ChangeApi orig = gApi.changes()
        .id(change2result.getChangeId());
    ChangeApi cherry = orig.current().cherryPick(in);
    cherry.current().review(ReviewInput.approve());
    cherry.current().submit();

    // Create a commit locally
    testRepo.git().fetch().setRefSpecs(new RefSpec("refs/heads/stable")).call();

    PushOneCommit.Result change3 = createChange(testRepo, "stable",
        "test","a.txt", "3", "");
    submitWithConflict(change3.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n" +
        "Change " + change3.getPatchSetId().getParentKey().get() +
        ": depends on change that was not submitted");
  }

  @Test
  @TestProjectInput(createEmptyCommit = false)
  public void mergeWithMissingChange() throws Exception {
    // create a draft change
    PushOneCommit.Result draftResult = createDraftChange();

    // create a new change based on the draft change
    PushOneCommit.Result changeResult = createChange();

    // delete the draft change
    gApi.changes().id(draftResult.getChangeId()).delete();

    // approve and submit the change
    submit(changeResult.getChangeId(), new SubmitInput(),
        ResourceConflictException.class, "nothing to merge", false);
  }
}
