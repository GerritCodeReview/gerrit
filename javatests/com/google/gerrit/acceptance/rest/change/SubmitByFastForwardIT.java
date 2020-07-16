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
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;

import com.google.gerrit.acceptance.GitUtil;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.inject.Inject;
import java.util.Map;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.PushResult;
import org.junit.Test;

public class SubmitByFastForwardIT extends AbstractSubmit {
  @Inject private ProjectOperations projectOperations;

  @Override
  protected SubmitType getSubmitType() {
    return SubmitType.FAST_FORWARD_ONLY;
  }

  @Test
  public void submitWithFastForward() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange();
    submit(change.getChangeId());
    RevCommit updatedHead = projectOperations.project(project).getHead("master");
    assertThat(updatedHead.getId()).isEqualTo(change.getCommit());
    assertThat(updatedHead.getParent(0)).isEqualTo(initialHead);
    assertSubmitter(change.getChangeId(), 1);

    assertRefUpdatedEvents(initialHead, updatedHead);
    assertChangeMergedEvents(change.getChangeId(), updatedHead.name());
  }

  @Test
  public void submitMultipleChangesWithFastForward() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    PushOneCommit.Result change = createChange();
    PushOneCommit.Result change2 = createChange();
    PushOneCommit.Result change3 = createChange();

    String id1 = change.getChangeId();
    String id2 = change2.getChangeId();
    String id3 = change3.getChangeId();
    approve(id1);
    approve(id2);
    submit(id3);

    RevCommit updatedHead = projectOperations.project(project).getHead("master");
    assertThat(updatedHead.getId()).isEqualTo(change3.getCommit());
    assertThat(updatedHead.getParent(0).getId()).isEqualTo(change2.getCommit());
    assertSubmitter(change.getChangeId(), 1);
    assertSubmitter(change2.getChangeId(), 1);
    assertSubmitter(change3.getChangeId(), 1);
    assertPersonEquals(admin.newIdent(), updatedHead.getAuthorIdent());
    assertPersonEquals(admin.newIdent(), updatedHead.getCommitterIdent());
    assertSubmittedTogether(id1, id3, id2, id1);
    assertSubmittedTogether(id2, id3, id2, id1);
    assertSubmittedTogether(id3, id3, id2, id1);

    assertRefUpdatedEvents(initialHead, updatedHead);
    assertChangeMergedEvents(
        id1, updatedHead.name(), id2, updatedHead.name(), id3, updatedHead.name());
  }

  @Test
  public void submitTwoChangesWithFastForward_missingDependency() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change1 = createChange();
    PushOneCommit.Result change2 = createChange();

    Change.Id id1 = change1.getPatchSetId().changeId();
    submitWithConflict(
        change2.getChangeId(),
        "Failed to submit 2 changes due to the following problems:\n"
            + "Change "
            + id1
            + ": needs Code-Review");

    RevCommit updatedHead = projectOperations.project(project).getHead("master");
    assertThat(updatedHead.getId()).isEqualTo(initialHead.getId());
    assertRefUpdatedEvents();
    assertChangeMergedEvents();
  }

  @Test
  public void submitFastForwardNotPossible_Conflict() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");
    PushOneCommit.Result change = createChange("Change 1", "a.txt", "content");
    submit(change.getChangeId());

    RevCommit headAfterFirstSubmit = projectOperations.project(project).getHead("master");
    testRepo.reset(initialHead);
    PushOneCommit.Result change2 = createChange("Change 2", "b.txt", "other content");

    approve(change2.getChangeId());

    Map<String, ActionInfo> actions =
        gApi.changes().id(change2.getChangeId()).revision(1).actions();

    assertThat(actions).containsKey("submit");
    ActionInfo info = actions.get("submit");
    assertThat(info.enabled).isNull();

    submitWithConflict(
        change2.getChangeId(),
        "Failed to submit 1 change due to the following problems:\n"
            + "Change "
            + change2.getChange().getId()
            + ": Project policy requires "
            + "all submissions to be a fast-forward. Please rebase the change "
            + "locally and upload again for review.");
    assertThat(projectOperations.project(project).getHead("master"))
        .isEqualTo(headAfterFirstSubmit);
    assertSubmitter(change.getChangeId(), 1);

    assertRefUpdatedEvents(initialHead, headAfterFirstSubmit);
    assertChangeMergedEvents(change.getChangeId(), headAfterFirstSubmit.name());
  }

  @Test
  public void submitSameCommitsAsInExperimentalBranch() throws Throwable {
    RevCommit initialHead = projectOperations.project(project).getHead("master");

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE).ref("refs/heads/*").group(adminGroupUuid()))
        .add(allow(Permission.PUSH).ref("refs/heads/experimental").group(adminGroupUuid()))
        .update();

    RevCommit c1 = commitBuilder().add("b.txt", "1").message("commit at tip").create();
    String id1 = GitUtil.getChangeId(testRepo, c1).get();

    PushResult r1 = pushHead(testRepo, "refs/for/master", false);
    assertThat(r1.getRemoteUpdate("refs/for/master").getNewObjectId()).isEqualTo(c1.getId());

    PushResult r2 = pushHead(testRepo, "refs/heads/experimental", false);
    assertThat(r2.getRemoteUpdate("refs/heads/experimental").getNewObjectId())
        .isEqualTo(c1.getId());

    submit(id1);
    RevCommit headAfterSubmit = projectOperations.project(project).getHead("master");

    assertThat(projectOperations.project(project).getHead("master").getId()).isEqualTo(c1.getId());
    assertSubmitter(id1, 1);

    assertRefUpdatedEvents(initialHead, headAfterSubmit);
    assertChangeMergedEvents(id1, headAfterSubmit.name());
  }
}
