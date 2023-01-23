// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.common.truth.Truth.assertThat;
import static java.util.Comparator.comparing;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSortedMap;
import com.google.common.truth.Correspondence;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.metrics.DisabledMetricMaker;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.restapi.change.CommentPorter.Metrics;
import com.google.gerrit.truth.NullAwareCorrespondence;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;
import org.eclipse.jgit.lib.ObjectId;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnit;
import org.mockito.junit.MockitoRule;

public class CommentPorterTest {

  private final ObjectId dummyObjectId =
      ObjectId.fromString("0123456789012345678901234567890123456789");

  @Rule public final MockitoRule mockito = MockitoJUnit.rule();

  @Mock private DiffOperations diffOperations;
  @Mock private CommentsUtil commentsUtil;

  private static final CommentPorter.Metrics metrics = new Metrics(new DisabledMetricMaker());

  private int uuidCounter = 0;

  @Test
  public void commentsAreNotDroppedWhenDiffNotAvailable() throws Exception {
    Project.NameKey project = Project.nameKey("myProject");
    Change.Id changeId = Change.id(1);
    Change change = createChange(project, changeId);
    PatchSet patchset1 = createPatchset(PatchSet.id(changeId, 1));
    PatchSet patchset2 = createPatchset(PatchSet.id(changeId, 2));
    ChangeNotes changeNotes = mockChangeNotes(project, change, patchset1, patchset2);

    CommentPorter commentPorter = new CommentPorter(diffOperations, commentsUtil, metrics);
    HumanComment comment = createComment(patchset1.id(), "myFile");
    when(commentsUtil.determineCommitId(any(), any(), anyShort()))
        .thenReturn(Optional.of(dummyObjectId));
    when(diffOperations.listModifiedFiles(
            any(Project.NameKey.class),
            any(ObjectId.class),
            any(ObjectId.class),
            any(DiffOptions.class)))
        .thenThrow(DiffNotAvailableException.class);
    ImmutableList<HumanComment> portedComments =
        commentPorter.portComments(
            changeNotes, patchset2, ImmutableList.of(comment), ImmutableList.of());

    assertThat(portedComments).isNotEmpty();
  }

  @Test
  public void commentsAreNotDroppedWhenDiffHasUnexpectedError() throws Exception {
    Project.NameKey project = Project.nameKey("myProject");
    Change.Id changeId = Change.id(1);
    Change change = createChange(project, changeId);
    PatchSet patchset1 = createPatchset(PatchSet.id(changeId, 1));
    PatchSet patchset2 = createPatchset(PatchSet.id(changeId, 2));
    ChangeNotes changeNotes = mockChangeNotes(project, change, patchset1, patchset2);

    CommentPorter commentPorter = new CommentPorter(diffOperations, commentsUtil, metrics);
    HumanComment comment = createComment(patchset1.id(), "myFile");
    when(commentsUtil.determineCommitId(any(), any(), anyShort()))
        .thenReturn(Optional.of(dummyObjectId));
    when(diffOperations.listModifiedFiles(
            any(Project.NameKey.class),
            any(ObjectId.class),
            any(ObjectId.class),
            any(DiffOptions.class)))
        .thenThrow(IllegalStateException.class);
    ImmutableList<HumanComment> portedComments =
        commentPorter.portComments(
            changeNotes, patchset2, ImmutableList.of(comment), ImmutableList.of());

    assertThat(portedComments).isNotEmpty();
  }

  @Test
  public void commentsAreNotDroppedWhenRetrievingCommitSha1sHasUnexpectedError() {
    Project.NameKey project = Project.nameKey("myProject");
    Change.Id changeId = Change.id(1);
    Change change = createChange(project, changeId);
    PatchSet patchset1 = createPatchset(PatchSet.id(changeId, 1));
    PatchSet patchset2 = createPatchset(PatchSet.id(changeId, 2));
    ChangeNotes changeNotes = mockChangeNotes(project, change, patchset1, patchset2);

    CommentPorter commentPorter = new CommentPorter(diffOperations, commentsUtil, metrics);
    HumanComment comment = createComment(patchset1.id(), "myFile");
    when(commentsUtil.determineCommitId(any(), any(), anyShort()))
        .thenThrow(IllegalStateException.class);
    ImmutableList<HumanComment> portedComments =
        commentPorter.portComments(
            changeNotes, patchset2, ImmutableList.of(comment), ImmutableList.of());

    assertThat(portedComments).isNotEmpty();
  }

  @Test
  public void commentsAreMappedToPatchsetLevelOnDiffError() throws Exception {
    Project.NameKey project = Project.nameKey("myProject");
    Change.Id changeId = Change.id(1);
    Change change = createChange(project, changeId);
    PatchSet patchset1 = createPatchset(PatchSet.id(changeId, 1));
    PatchSet patchset2 = createPatchset(PatchSet.id(changeId, 2));
    ChangeNotes changeNotes = mockChangeNotes(project, change, patchset1, patchset2);

    CommentPorter commentPorter = new CommentPorter(diffOperations, commentsUtil, metrics);
    HumanComment comment = createComment(patchset1.id(), "myFile");
    when(commentsUtil.determineCommitId(any(), any(), anyShort()))
        .thenReturn(Optional.of(dummyObjectId));
    when(diffOperations.listModifiedFiles(
            any(Project.NameKey.class),
            any(ObjectId.class),
            any(ObjectId.class),
            any(DiffOptions.class)))
        .thenThrow(IllegalStateException.class);
    ImmutableList<HumanComment> portedComments =
        commentPorter.portComments(
            changeNotes, patchset2, ImmutableList.of(comment), ImmutableList.of());

    assertThat(portedComments)
        .comparingElementsUsing(hasFilePath())
        .containsExactly(Patch.PATCHSET_LEVEL);
  }

  @Test
  public void commentsAreStillPortedWhenDiffOfOtherCommentsHasError() throws Exception {
    Project.NameKey project = Project.nameKey("myProject");
    Change.Id changeId = Change.id(1);
    Change change = createChange(project, changeId);
    PatchSet patchset1 = createPatchset(PatchSet.id(changeId, 1));
    PatchSet patchset2 = createPatchset(PatchSet.id(changeId, 2));
    PatchSet patchset3 = createPatchset(PatchSet.id(changeId, 3));
    ChangeNotes changeNotes = mockChangeNotes(project, change, patchset1, patchset2, patchset3);

    CommentPorter commentPorter = new CommentPorter(diffOperations, commentsUtil, metrics);
    // Place the comments on different patchsets to have two different diff requests.
    HumanComment comment1 = createComment(patchset1.id(), "myFile");
    HumanComment comment2 = createComment(patchset2.id(), "myFile");
    when(commentsUtil.determineCommitId(any(), any(), anyShort()))
        .thenReturn(Optional.of(dummyObjectId));
    // Throw an exception on the first diff request but return an actual value on the second.
    when(diffOperations.listModifiedFiles(
            any(Project.NameKey.class),
            any(ObjectId.class),
            any(ObjectId.class),
            any(DiffOptions.class)))
        .thenThrow(IllegalStateException.class)
        .thenReturn(ImmutableMap.of());
    ImmutableList<HumanComment> portedComments =
        commentPorter.portComments(
            changeNotes, patchset3, ImmutableList.of(comment1, comment2), ImmutableList.of());

    // One of the comments should still be ported as usual. -> Keeps its file name as the diff was
    // empty.
    assertThat(portedComments).comparingElementsUsing(hasFilePath()).contains("myFile");
  }

  @Test
  public void commentsWithInvalidPatchsetsAreIgnored() throws Exception {
    Project.NameKey project = Project.nameKey("myProject");
    Change.Id changeId = Change.id(1);
    Change change = createChange(project, changeId);
    PatchSet patchset1 = createPatchset(PatchSet.id(changeId, 1));
    PatchSet patchset2 = createPatchset(PatchSet.id(changeId, 2));
    // Leave out patchset 1 (e.g. reserved for draft patchsets in the past).
    ChangeNotes changeNotes = mockChangeNotes(project, change, patchset2);

    CommentPorter commentPorter = new CommentPorter(diffOperations, commentsUtil, metrics);
    HumanComment comment = createComment(patchset1.id(), "myFile");
    when(commentsUtil.determineCommitId(any(), any(), anyShort()))
        .thenReturn(Optional.of(dummyObjectId));
    when(diffOperations.listModifiedFiles(
            any(Project.NameKey.class),
            any(ObjectId.class),
            any(ObjectId.class),
            any(DiffOptions.class)))
        .thenReturn(ImmutableMap.of());
    ImmutableList<HumanComment> portedComments =
        commentPorter.portComments(
            changeNotes, patchset2, ImmutableList.of(comment), ImmutableList.of());

    assertThat(portedComments).isEmpty();
  }

  private Change createChange(Project.NameKey project, Change.Id changeId) {
    return new Change(
        Change.key("changeKey"),
        changeId,
        Account.id(123),
        BranchNameKey.create(project, "myBranch"),
        Instant.ofEpochMilli(12345));
  }

  private PatchSet createPatchset(PatchSet.Id id) {
    return PatchSet.builder()
        .id(id)
        .commitId(dummyObjectId)
        .uploader(Account.id(123))
        .realUploader(Account.id(456))
        .createdOn(Instant.ofEpochMilli(12345))
        .build();
  }

  private ChangeNotes mockChangeNotes(
      Project.NameKey project, Change change, PatchSet... patchsets) {
    ChangeNotes changeNotes = mock(ChangeNotes.class);
    when(changeNotes.getProjectName()).thenReturn(project);
    when(changeNotes.getChange()).thenReturn(change);
    when(changeNotes.getChangeId()).thenReturn(change.getId());
    ImmutableSortedMap<PatchSet.Id, PatchSet> sortedPatchsets =
        Arrays.stream(patchsets)
            .collect(
                ImmutableSortedMap.toImmutableSortedMap(
                    comparing(PatchSet.Id::get), PatchSet::id, patchset -> patchset));
    when(changeNotes.getPatchSets()).thenReturn(sortedPatchsets);
    return changeNotes;
  }

  private HumanComment createComment(PatchSet.Id patchsetId, String filePath) {
    return new HumanComment(
        new Comment.Key(getUniqueUuid(), filePath, patchsetId.get()),
        Account.id(100),
        Instant.ofEpochMilli(1234),
        (short) 1,
        "Comment text",
        "serverId",
        true);
  }

  private String getUniqueUuid() {
    return "commentUuid" + uuidCounter++;
  }

  private Correspondence<HumanComment, String> hasFilePath() {
    return NullAwareCorrespondence.transforming(comment -> comment.key.filename, "hasFilePath");
  }
}
