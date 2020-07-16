// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.acceptance.api.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.IncludedInInfo;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.GitPerson;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.inject.Inject;
import java.util.Iterator;
import java.util.List;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.junit.Test;

@NoHttpd
public class CommitIT extends AbstractDaemonTest {
  @Inject private ProjectOperations projectOperations;

  @Test
  public void getCommitInfo() throws Exception {
    Result result = createChange();
    String commitId = result.getCommit().getId().name();
    CommitInfo info = gApi.projects().name(project.get()).commit(commitId).get();
    assertThat(info.commit).isEqualTo(commitId);
    assertThat(info.parents.stream().map(c -> c.commit).collect(toList()))
        .containsExactly(result.getCommit().getParent(0).name());
    assertThat(info.subject).isEqualTo(result.getCommit().getShortMessage());
    assertPerson(info.author, admin);
    assertPerson(info.committer, admin);
    assertThat(info.webLinks).isNull();
  }

  @Test
  public void includedInOpenChange() throws Exception {
    Result result = createChange();
    assertThat(getIncludedIn(result.getCommit().getId()).branches).isEmpty();
    assertThat(getIncludedIn(result.getCommit().getId()).tags).isEmpty();
  }

  @Test
  public void includedInMergedChange() throws Exception {
    Result result = createChange();
    gApi.changes()
        .id(result.getChangeId())
        .revision(result.getCommit().name())
        .review(ReviewInput.approve());
    gApi.changes().id(result.getChangeId()).revision(result.getCommit().name()).submit();

    assertThat(getIncludedIn(result.getCommit().getId()).branches).containsExactly("master");
    assertThat(getIncludedIn(result.getCommit().getId()).tags).isEmpty();

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE_TAG).ref(R_TAGS + "*").group(adminGroupUuid()))
        .update();
    gApi.projects().name(result.getChange().project().get()).tag("test-tag").create(new TagInput());

    assertThat(getIncludedIn(result.getCommit().getId()).tags).containsExactly("test-tag");

    createBranch(BranchNameKey.create(project, "test-branch"));

    assertThat(getIncludedIn(result.getCommit().getId()).branches)
        .containsExactly("master", "test-branch");
  }

  @Test
  public void cherryPickWithoutMessage() throws Exception {
    String branch = "foo";

    // Create change to cherry-pick
    RevCommit revCommit = createChange().getCommit();

    // Create target branch to cherry-pick to.
    gApi.projects().name(project.get()).branch(branch).create(new BranchInput());

    // Cherry-pick without message.
    CherryPickInput input = new CherryPickInput();
    input.destination = branch;
    String changeId =
        gApi.projects().name(project.get()).commit(revCommit.name()).cherryPick(input).get().id;

    // Expect that the message of the cherry-picked commit was used for the cherry-pick change.
    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    RevisionInfo revInfo = changeInfo.revisions.get(changeInfo.currentRevision);
    assertThat(revInfo).isNotNull();
    assertThat(revInfo.commit.message).isEqualTo(revCommit.getFullMessage());
  }

  @Test
  public void cherryPickCommitWithoutChangeId() throws Exception {
    CherryPickInput input = new CherryPickInput();
    input.destination = "foo";
    input.message = "it goes to foo branch";
    gApi.projects().name(project.get()).branch(input.destination).create(new BranchInput());

    RevCommit revCommit = createNewCommitWithoutChangeId("refs/heads/master", "a.txt", "content");
    ChangeInfo changeInfo =
        gApi.projects().name(project.get()).commit(revCommit.getName()).cherryPick(input).get();

    assertThat(changeInfo.messages).hasSize(1);
    Iterator<ChangeMessageInfo> messageIterator = changeInfo.messages.iterator();
    String expectedMessage =
        String.format("Patch Set 1: Cherry Picked from commit %s.", revCommit.getName());
    assertThat(messageIterator.next().message).isEqualTo(expectedMessage);

    RevisionInfo revInfo = changeInfo.revisions.get(changeInfo.currentRevision);
    assertThat(revInfo).isNotNull();
    CommitInfo commitInfo = revInfo.commit;
    assertThat(commitInfo.message)
        .isEqualTo(input.message + "\n\nChange-Id: " + changeInfo.changeId + "\n");
  }

  @Test
  public void cherryPickCommitWithChangeId() throws Exception {
    CherryPickInput input = new CherryPickInput();
    input.destination = "foo";

    RevCommit revCommit = createChange().getCommit();
    List<String> footers = revCommit.getFooterLines("Change-Id");
    assertThat(footers).hasSize(1);
    String changeId = footers.get(0);

    input.message = "it goes to foo branch\n\nChange-Id: " + changeId;
    gApi.projects().name(project.get()).branch(input.destination).create(new BranchInput());

    ChangeInfo changeInfo =
        gApi.projects().name(project.get()).commit(revCommit.getName()).cherryPick(input).get();

    assertThat(changeInfo.messages).hasSize(1);
    Iterator<ChangeMessageInfo> messageIterator = changeInfo.messages.iterator();
    String expectedMessage =
        String.format("Patch Set 1: Cherry Picked from commit %s.", revCommit.getName());
    assertThat(messageIterator.next().message).isEqualTo(expectedMessage);

    RevisionInfo revInfo = changeInfo.revisions.get(changeInfo.currentRevision);
    assertThat(revInfo).isNotNull();
    assertThat(revInfo.commit.message).isEqualTo(input.message + "\n");
  }

  @Test
  public void cherryPickCommitWithSetTopic() throws Exception {
    String branch = "foo";
    RevCommit revCommit = createChange().getCommit();
    gApi.projects().name(project.get()).branch(branch).create(new BranchInput());
    CherryPickInput input = new CherryPickInput();
    input.destination = branch;
    input.topic = "topic";
    String changeId =
        gApi.projects().name(project.get()).commit(revCommit.name()).cherryPick(input).get().id;

    ChangeInfo changeInfo = gApi.changes().id(changeId).get();
    assertThat(changeInfo.topic).isEqualTo(input.topic);
  }

  private IncludedInInfo getIncludedIn(ObjectId id) throws Exception {
    return gApi.projects().name(project.get()).commit(id.name()).includedIn();
  }

  private static void assertPerson(GitPerson actual, TestAccount expected) {
    assertThat(actual.email).isEqualTo(expected.email());
    assertThat(actual.name).isEqualTo(expected.fullName());
  }
}
