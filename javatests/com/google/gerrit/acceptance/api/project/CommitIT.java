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
import static com.google.gerrit.acceptance.GitUtil.pushHead;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.allow;
import static com.google.gerrit.acceptance.testsuite.project.TestProjectUpdate.block;
import static com.google.gerrit.server.group.SystemGroupBackend.REGISTERED_USERS;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.Constants.R_TAGS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.testsuite.project.ProjectOperations;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.CherryPickInput;
import com.google.gerrit.extensions.api.changes.IncludedInInfo;
import com.google.gerrit.extensions.api.changes.IncludedInRefsInfo;
import com.google.gerrit.extensions.api.changes.ReviewInput;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.api.projects.TagInput;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.extensions.common.GitPerson;
import com.google.gerrit.extensions.common.RevisionInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.inject.Inject;
import java.util.Arrays;
import java.util.Collections;
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
  public void includedInMergedChange_filtersOutNonVisibleBranches() throws Exception {
    Result baseChange = createAndSubmitChange("refs/for/master");

    createBranch(BranchNameKey.create(project, "test-branch-1"));
    createBranch(BranchNameKey.create(project, "test-branch-2"));
    createAndSubmitChange("refs/for/test-branch-1");
    createAndSubmitChange("refs/for/test-branch-2");

    assertThat(getIncludedIn(baseChange.getCommit().getId()).branches)
        .containsExactly("master", "test-branch-1", "test-branch-2");

    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref("refs/heads/test-branch-1").group(REGISTERED_USERS))
        .update();

    assertThat(getIncludedIn(baseChange.getCommit().getId()).branches)
        .containsExactly("master", "test-branch-2");
  }

  @Test
  public void includedInMergedChange_filtersOutNonVisibleTags() throws Exception {
    String tagBase = "tag_base";
    String tagBranch1 = "tag_1";

    Result baseChange = createAndSubmitChange("refs/for/master");
    createLightWeightTag(tagBase);
    assertThat(getIncludedIn(baseChange.getCommit().getId()).tags).containsExactly(tagBase);

    createBranch(BranchNameKey.create(project, "test-branch-1"));
    createAndSubmitChange("refs/for/test-branch-1");
    createLightWeightTag(tagBranch1);
    assertThat(getIncludedIn(baseChange.getCommit().getId()).tags)
        .containsExactly(tagBase, tagBranch1);

    projectOperations
        .project(project)
        .forUpdate()
        // Tag permissions are controlled by read permissions on branches. Blocking read permission
        // on test-branch-1 so that tagBranch1 becomes non-visible
        .add(block(Permission.READ).ref("refs/heads/test-branch-1").group(REGISTERED_USERS))
        .update();
    assertThat(getIncludedIn(baseChange.getCommit().getId()).tags).containsExactly(tagBase);
  }

  @Test
  public void includedInRefsOpenChange() throws Exception {
    Result result = createChange();
    assertThat(
            getIncludedInRefs(result.getCommit().getId(), Arrays.asList(R_HEADS + "master")).refs)
        .isEmpty();
  }

  @Test
  public void includedInRefsMergedChange() throws Exception {
    createBranch(BranchNameKey.create(project, "test-branch-1"));
    Result result = createChange();
    gApi.changes()
        .id(result.getChangeId())
        .revision(result.getCommit().name())
        .review(ReviewInput.approve());
    gApi.changes().id(result.getChangeId()).revision(result.getCommit().name()).submit();

    assertThat(getIncludedInRefs(result.getCommit().getId(), Collections.emptyList()).refs)
        .isEmpty();
    assertThat(getIncludedInRefs(result.getCommit().getId(), Arrays.asList(R_HEADS + "foo")).refs)
        .isEmpty();
    assertThat(
            getIncludedInRefs(result.getCommit().getId(), Arrays.asList(R_HEADS + "master")).refs)
        .containsExactly(R_HEADS + "master");
    assertThat(
            getIncludedInRefs(
                    result.getCommit().getId(),
                    Arrays.asList(R_HEADS + "master", R_HEADS + "test-branch-1"))
                .refs)
        .containsExactly(R_HEADS + "master");

    projectOperations
        .project(project)
        .forUpdate()
        .add(allow(Permission.CREATE_TAG).ref(R_TAGS + "*").group(adminGroupUuid()))
        .update();
    gApi.projects().name(result.getChange().project().get()).tag("test-tag").create(new TagInput());

    assertThat(
            getIncludedInRefs(result.getCommit().getId(), Arrays.asList(R_TAGS + "test-tag")).refs)
        .containsExactly(R_TAGS + "test-tag");
    assertThat(
            getIncludedInRefs(
                    result.getCommit().getId(),
                    Arrays.asList(R_HEADS + "master", R_TAGS + "test-tag"))
                .refs)
        .containsExactly(R_HEADS + "master", R_TAGS + "test-tag");

    createBranch(BranchNameKey.create(project, "test-branch-2"));

    assertThat(
            getIncludedInRefs(
                    result.getCommit().getId(),
                    Arrays.asList(
                        R_HEADS + "master",
                        R_TAGS + "test-tag",
                        R_HEADS + "test-branch-1",
                        R_HEADS + "test-branch-2"))
                .refs)
        .containsExactly(R_HEADS + "master", R_TAGS + "test-tag", R_HEADS + "test-branch-2");
  }

  @Test
  public void includedInRefsMergedChange_filtersOutNonVisibleBranchRefs() throws Exception {
    Result baseChange = createAndSubmitChange("refs/for/master");

    createBranch(BranchNameKey.create(project, "test-branch-1"));
    createBranch(BranchNameKey.create(project, "test-branch-2"));
    createAndSubmitChange("refs/for/test-branch-1");
    createAndSubmitChange("refs/for/test-branch-2");

    assertThat(
            getIncludedInRefs(
                    baseChange.getCommit().getId(),
                    Arrays.asList(
                        R_HEADS + "master", R_HEADS + "test-branch-1", R_HEADS + "test-branch-2"))
                .refs)
        .containsExactly(R_HEADS + "master", R_HEADS + "test-branch-1", R_HEADS + "test-branch-2");

    projectOperations
        .project(project)
        .forUpdate()
        .add(block(Permission.READ).ref(R_HEADS + "test-branch-1").group(REGISTERED_USERS))
        .update();

    assertThat(
            getIncludedInRefs(
                    baseChange.getCommit().getId(),
                    Arrays.asList(
                        R_HEADS + "master", R_HEADS + "test-branch-1", R_HEADS + "test-branch-2"))
                .refs)
        .containsExactly(R_HEADS + "master", R_HEADS + "test-branch-2");
  }

  @Test
  public void includedInRefsMergedChange_filtersOutNonVisibleTagRefs() throws Exception {
    String tagBase = "tag_base";
    String tagBranch1 = "tag_1";

    Result baseChange = createAndSubmitChange("refs/for/master");
    createLightWeightTag(tagBase);
    assertThat(
            getIncludedInRefs(baseChange.getCommit().getId(), Arrays.asList(R_TAGS + tagBase)).refs)
        .containsExactly(R_TAGS + tagBase);

    createBranch(BranchNameKey.create(project, "test-branch-1"));
    createAndSubmitChange("refs/for/test-branch-1");
    createLightWeightTag(tagBranch1);
    assertThat(
            getIncludedInRefs(
                    baseChange.getCommit().getId(),
                    Arrays.asList(R_TAGS + tagBase, R_TAGS + tagBranch1))
                .refs)
        .containsExactly(R_TAGS + tagBase, R_TAGS + tagBranch1);

    projectOperations
        .project(project)
        .forUpdate()
        // Tag permissions are controlled by read permissions on branches. Blocking read permission
        // on test-branch-1 so that tagBranch1 becomes non-visible
        .add(block(Permission.READ).ref(R_HEADS + "test-branch-1").group(REGISTERED_USERS))
        .update();
    assertThat(
            getIncludedInRefs(
                    baseChange.getCommit().getId(),
                    Arrays.asList(R_TAGS + tagBase, R_TAGS + tagBranch1))
                .refs)
        .containsExactly(R_TAGS + tagBase);
  }

  @Test
  public void cherryPickWithoutMessageSameBranch() throws Exception {
    String destBranch = "master";

    // Create change to cherry-pick
    PushOneCommit.Result r = createChange();
    ChangeInfo changeToCherryPick = info(r.getChangeId());
    RevCommit commitToCherryPick = r.getCommit();

    // Cherry-pick without message.
    CherryPickInput input = new CherryPickInput();
    input.destination = destBranch;
    ChangeInfo cherryPickResult =
        gApi.projects()
            .name(project.get())
            .commit(commitToCherryPick.name())
            .cherryPick(input)
            .get();

    // Expect that the Change-Id of the cherry-picked commit was used for the cherry-pick change.
    // New patch-set to existing change was uploaded.
    assertThat(cherryPickResult._number).isEqualTo(changeToCherryPick._number);
    assertThat(cherryPickResult.revisions).hasSize(2);
    assertThat(cherryPickResult.changeId).isEqualTo(changeToCherryPick.changeId);
    assertThat(cherryPickResult.messages).hasSize(2);

    // Cherry-pick of is not set, because the source change was not provided.
    assertThat(cherryPickResult.cherryPickOfChange).isNull();
    assertThat(cherryPickResult.cherryPickOfPatchSet).isNull();
    // Expect that the message of the cherry-picked commit was used for the cherry-pick change.
    RevisionInfo revInfo = cherryPickResult.revisions.get(cherryPickResult.currentRevision);
    assertThat(revInfo).isNotNull();
    assertThat(revInfo.commit.message).isEqualTo(commitToCherryPick.getFullMessage());
  }

  @Test
  public void cherryPickWithoutMessageOtherBranch() throws Exception {
    String destBranch = "foo";
    createBranch(BranchNameKey.create(project, destBranch));

    // Create change to cherry-pick
    PushOneCommit.Result r = createChange();
    ChangeInfo changeToCherryPick = info(r.getChangeId());
    RevCommit commitToCherryPick = r.getCommit();

    // Cherry-pick without message.
    CherryPickInput input = new CherryPickInput();
    input.destination = destBranch;
    ChangeInfo cherryPickResult =
        gApi.projects()
            .name(project.get())
            .commit(commitToCherryPick.name())
            .cherryPick(input)
            .get();

    // Expect that the Change-Id of the cherry-picked commit was used for the cherry-pick change.
    // New change in destination branch was created.
    assertThat(cherryPickResult._number).isGreaterThan(changeToCherryPick._number);
    assertThat(cherryPickResult.revisions).hasSize(1);
    assertThat(cherryPickResult.changeId).isEqualTo(changeToCherryPick.changeId);
    assertThat(cherryPickResult.messages).hasSize(1);

    // Cherry-pick of is not set, because the source change was not provided.
    assertThat(cherryPickResult.cherryPickOfChange).isNull();
    assertThat(cherryPickResult.cherryPickOfPatchSet).isNull();
    // Expect that the message of the cherry-picked commit was used for the cherry-pick change.
    RevisionInfo revInfo = cherryPickResult.revisions.get(cherryPickResult.currentRevision);
    assertThat(revInfo).isNotNull();
    assertThat(revInfo.commit.message).isEqualTo(commitToCherryPick.getFullMessage());
  }

  @Test
  public void cherryPickCommitWithoutChangeIdCreateNewChange() throws Exception {
    String destBranch = "foo";
    createBranch(BranchNameKey.create(project, destBranch));

    CherryPickInput input = new CherryPickInput();
    input.destination = destBranch;
    input.message = "it goes to foo branch";

    RevCommit commitToCherryPick =
        createNewCommitWithoutChangeId("refs/heads/master", "a.txt", "content");
    ChangeInfo cherryPickResult =
        gApi.projects()
            .name(project.get())
            .commit(commitToCherryPick.getName())
            .cherryPick(input)
            .get();

    assertThat(cherryPickResult.messages).hasSize(1);
    Iterator<ChangeMessageInfo> messageIterator = cherryPickResult.messages.iterator();
    String expectedMessage =
        String.format("Patch Set 1: Cherry Picked from commit %s.", commitToCherryPick.getName());
    assertThat(messageIterator.next().message).isEqualTo(expectedMessage);

    RevisionInfo revInfo = cherryPickResult.revisions.get(cherryPickResult.currentRevision);
    assertThat(revInfo).isNotNull();
    CommitInfo commitInfo = revInfo.commit;
    assertThat(commitInfo.message)
        .isEqualTo(input.message + "\n\nChange-Id: " + cherryPickResult.changeId + "\n");
  }

  @Test
  public void cherryPickCommitWithChangeIdCreateNewChange() throws Exception {
    String destBranch = "foo";
    createBranch(BranchNameKey.create(project, destBranch));

    PushOneCommit.Result r = createChange();
    ChangeInfo changeToCherryPick = info(r.getChangeId());
    RevCommit commitToCherryPick = r.getCommit();
    List<String> footers = commitToCherryPick.getFooterLines("Change-Id");
    assertThat(footers).hasSize(1);
    String changeId = footers.get(0);

    CherryPickInput input = new CherryPickInput();
    input.destination = destBranch;
    input.message =
        String.format(
            "it goes to foo branch\n\nChange-Id: Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef\n\nChange-Id: %s\n",
            changeId);

    ChangeInfo cherryPickResult =
        gApi.projects()
            .name(project.get())
            .commit(commitToCherryPick.getName())
            .cherryPick(input)
            .get();

    // No change was found in destination branch with the provided Change-Id.
    assertThat(cherryPickResult._number).isGreaterThan(changeToCherryPick._number);
    assertThat(cherryPickResult.changeId).isEqualTo(changeId);
    assertThat(cherryPickResult.revisions).hasSize(1);
    assertThat(cherryPickResult.messages).hasSize(1);
    Iterator<ChangeMessageInfo> messageIterator = cherryPickResult.messages.iterator();
    String expectedMessage =
        String.format("Patch Set 1: Cherry Picked from commit %s.", commitToCherryPick.getName());
    assertThat(messageIterator.next().message).isEqualTo(expectedMessage);

    // Cherry-pick of is not set, because the source change was not provided.
    assertThat(cherryPickResult.cherryPickOfChange).isNull();
    assertThat(cherryPickResult.cherryPickOfPatchSet).isNull();
    RevisionInfo revInfo = cherryPickResult.revisions.get(cherryPickResult.currentRevision);
    assertThat(revInfo).isNotNull();
    assertThat(revInfo.commit.message).isEqualTo(input.message);
  }

  @Test
  public void cherryPickCommitToExistingChange() throws Exception {
    String destBranch = "foo";
    createBranch(BranchNameKey.create(project, destBranch));

    PushOneCommit.Result r = createChange("refs/for/" + destBranch);
    ChangeInfo existingDestChange = info(r.getChangeId());

    String commitToCherryPick = createChange().getCommit().getName();

    CherryPickInput input = new CherryPickInput();
    input.destination = destBranch;
    input.message =
        String.format(
            "it goes to foo branch\n\nChange-Id: Ideadbeefdeadbeefdeadbeefdeadbeefdeadbeef\n\nChange-Id: %s\n",
            existingDestChange.changeId);
    input.allowConflicts = true;
    input.allowEmpty = true;

    ChangeInfo cherryPickResult =
        gApi.projects().name(project.get()).commit(commitToCherryPick).cherryPick(input).get();

    // New patch-set to existing change was uploaded.
    assertThat(cherryPickResult._number).isEqualTo(existingDestChange._number);
    assertThat(cherryPickResult.changeId).isEqualTo(existingDestChange.changeId);
    assertThat(cherryPickResult.messages).hasSize(2);
    assertThat(cherryPickResult.revisions).hasSize(2);
    Iterator<ChangeMessageInfo> messageIterator = cherryPickResult.messages.iterator();

    assertThat(messageIterator.next().message).isEqualTo("Uploaded patch set 1.");
    assertThat(messageIterator.next().message).isEqualTo("Uploaded patch set 2.");
    // Cherry-pick of is not set, because the source change was not provided.
    assertThat(cherryPickResult.cherryPickOfChange).isNull();
    assertThat(cherryPickResult.cherryPickOfPatchSet).isNull();
    RevisionInfo revInfo = cherryPickResult.revisions.get(cherryPickResult.currentRevision);
    assertThat(revInfo).isNotNull();
    assertThat(revInfo.commit.message).isEqualTo(input.message);
  }

  @Test
  public void cherryPickCommitToExistingCherryPickedChange() throws Exception {
    String destBranch = "foo";
    createBranch(BranchNameKey.create(project, destBranch));

    PushOneCommit.Result r = createChange("refs/for/" + destBranch);
    ChangeInfo existingDestChange = info(r.getChangeId());

    r = createChange();
    ChangeInfo changeToCherryPick = info(r.getChangeId());
    RevCommit commitToCherryPick = r.getCommit();

    CherryPickInput input = new CherryPickInput();
    input.destination = destBranch;
    input.message =
        String.format("it goes to foo branch\n\nChange-Id: %s\n", existingDestChange.changeId);
    input.allowConflicts = true;
    input.allowEmpty = true;
    // Use RevisionAPI to submit initial cherryPick.
    ChangeInfo cherryPickResult =
        gApi.changes().id(changeToCherryPick.changeId).current().cherryPick(input).get();
    assertThat(cherryPickResult.changeId).isEqualTo(existingDestChange.changeId);
    // Cherry-pick was set.
    assertThat(cherryPickResult.cherryPickOfChange).isEqualTo(changeToCherryPick._number);
    assertThat(cherryPickResult.cherryPickOfPatchSet).isEqualTo(1);
    RevisionInfo revInfo = cherryPickResult.revisions.get(cherryPickResult.currentRevision);
    assertThat(revInfo).isNotNull();
    assertThat(revInfo.commit.message).isEqualTo(input.message);
    // Use CommitApi to update the cherryPick change.
    cherryPickResult =
        gApi.projects()
            .name(project.get())
            .commit(commitToCherryPick.getName())
            .cherryPick(input)
            .get();

    assertThat(cherryPickResult.changeId).isEqualTo(existingDestChange.changeId);
    assertThat(cherryPickResult.messages).hasSize(3);
    Iterator<ChangeMessageInfo> messageIterator = cherryPickResult.messages.iterator();

    assertThat(messageIterator.next().message).isEqualTo("Uploaded patch set 1.");
    assertThat(messageIterator.next().message).isEqualTo("Uploaded patch set 2.");
    assertThat(messageIterator.next().message).isEqualTo("Uploaded patch set 3.");
    // Cherry-pick was reset to empty value.
    assertThat(cherryPickResult._number).isEqualTo(existingDestChange._number);
    assertThat(cherryPickResult.cherryPickOfChange).isNull();
    assertThat(cherryPickResult.cherryPickOfPatchSet).isNull();
  }

  @Test
  public void cherryPickCommitWithChangeIdToClosedChange() throws Exception {
    String destBranch = "refs/heads/foo";
    createBranch(BranchNameKey.create(project, destBranch));

    PushOneCommit.Result r = createChange("refs/for/" + destBranch);
    ChangeInfo existingDestChange = info(r.getChangeId());
    String commitToCherryPick = createChange().getCommit().getName();

    gApi.changes().id(existingDestChange.changeId).current().review(ReviewInput.approve());
    gApi.changes().id(existingDestChange.changeId).current().submit();

    CherryPickInput input = new CherryPickInput();
    input.destination = destBranch;
    input.message =
        String.format("it goes to foo branch\n\nChange-Id: %s\n", existingDestChange.changeId);
    input.allowConflicts = true;
    input.allowEmpty = true;

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> gApi.projects().name(project.get()).commit(commitToCherryPick).cherryPick(input));
    assertThat(thrown)
        .hasMessageThat()
        .isEqualTo(
            String.format(
                "Cherry-pick with Change-Id %s could not update the existing change %d in "
                    + "destination branch %s of project %s, because the change was closed (MERGED)",
                existingDestChange.changeId,
                existingDestChange._number,
                destBranch,
                project.get()));
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

  private IncludedInRefsInfo getIncludedInRefs(ObjectId id, List<String> refs) throws Exception {
    return gApi.projects().name(project.get()).commit(id.name()).includedInRefs(refs);
  }

  private static void assertPerson(GitPerson actual, TestAccount expected) {
    assertThat(actual.email).isEqualTo(expected.email());
    assertThat(actual.name).isEqualTo(expected.fullName());
  }

  private Result createAndSubmitChange(String branch) throws Exception {
    Result r = createChange(branch);
    approve(r.getChangeId());
    gApi.changes().id(r.getChangeId()).current().submit();
    return r;
  }

  private void createLightWeightTag(String tagName) throws Exception {
    pushHead(testRepo, RefNames.REFS_TAGS + tagName, false, false);
  }
}
