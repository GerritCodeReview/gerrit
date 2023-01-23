// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.extensions.common;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.client.ReviewerState;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public final class ChangeInfoDifferTest {

  private static final String REVISION = "abc123";

  @Test
  public void getDiff_givenEmptyChangeInfos_returnsEmptyDifference() {
    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(new ChangeInfo(), new ChangeInfo());

    // Spot check a few fields, including collections and maps.
    assertThat(diff.added()._number).isNull();
    assertThat(diff.added().branch).isNull();
    assertThat(diff.added().project).isNull();
    assertThat(diff.added().currentRevision).isNull();
    assertThat(diff.added().actions).isNull();
    assertThat(diff.added().messages).isNull();
    assertThat(diff.added().reviewers).isNull();
    assertThat(diff.added().hashtags).isNull();
    assertThat(diff.added().removableLabels).isNull();
    assertThat(diff.removed()._number).isNull();
    assertThat(diff.removed().branch).isNull();
    assertThat(diff.removed().project).isNull();
    assertThat(diff.removed().currentRevision).isNull();
    assertThat(diff.removed().actions).isNull();
    assertThat(diff.removed().messages).isNull();
    assertThat(diff.removed().reviewers).isNull();
    assertThat(diff.removed().hashtags).isNull();
    assertThat(diff.removed().removableLabels).isNull();
  }

  @Test
  public void getDiff_returnsOldAndNewChangeInfos() {
    ChangeInfo oldChangeInfo = createChangeInfoWithTopic("topic");
    ChangeInfo newChangeInfo = createChangeInfoWithTopic(oldChangeInfo.topic);

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.oldChangeInfo()).isEqualTo(oldChangeInfo);
    assertThat(diff.newChangeInfo()).isEqualTo(newChangeInfo);
  }

  @Test
  public void getDiff_givenUnchangedTopic_returnsNullTopics() {
    ChangeInfo oldChangeInfo = createChangeInfoWithTopic("topic");
    ChangeInfo newChangeInfo = createChangeInfoWithTopic(oldChangeInfo.topic);

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().topic).isNull();
    assertThat(diff.removed().topic).isNull();
  }

  @Test
  public void getDiff_givenChangedTopic_returnsTopics() {
    ChangeInfo oldChangeInfo = createChangeInfoWithTopic("old-topic");
    ChangeInfo newChangeInfo = createChangeInfoWithTopic("new-topic");

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().topic).isEqualTo(newChangeInfo.topic);
    assertThat(diff.removed().topic).isEqualTo(oldChangeInfo.topic);
  }

  @Test
  public void getDiff_givenEqualAssignees_returnsNullAssignee() {
    ChangeInfo oldChangeInfo =
        createChangeInfoWithAccount(new AccountInfo("name", "mail@mail.com"));
    ChangeInfo newChangeInfo =
        createChangeInfoWithAccount(
            new AccountInfo(oldChangeInfo.assignee.name, oldChangeInfo.assignee.email));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().assignee).isNull();
    assertThat(diff.removed().assignee).isNull();
  }

  @Test
  public void getDiff_givenNewAssignee_returnsAssignee() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo =
        createChangeInfoWithAccount(new AccountInfo("name", "mail@mail.com"));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().assignee).isEqualTo(newChangeInfo.assignee);
    assertThat(diff.removed().assignee).isNull();
  }

  @Test
  public void getDiff_withRemovedAssignee_returnsAssignee() {
    ChangeInfo oldChangeInfo =
        createChangeInfoWithAccount(new AccountInfo("name", "mail@mail.com"));
    ChangeInfo newChangeInfo = new ChangeInfo();

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().assignee).isNull();
    assertThat(diff.removed().assignee).isEqualTo(oldChangeInfo.assignee);
  }

  @Test
  public void getDiff_givenAssigneeWithNewName_returnsNameButNotEmail() {
    ChangeInfo oldChangeInfo =
        createChangeInfoWithAccount(new AccountInfo("old name", "mail@mail.com"));
    ChangeInfo newChangeInfo =
        createChangeInfoWithAccount(new AccountInfo("new name", oldChangeInfo.assignee.email));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().assignee).isNotNull();
    assertThat(diff.added().assignee.name).isEqualTo(newChangeInfo.assignee.name);
    assertThat(diff.added().assignee.email).isNull();
    assertThat(diff.removed().assignee).isNotNull();
    assertThat(diff.removed().assignee.name).isEqualTo(oldChangeInfo.assignee.name);
    assertThat(diff.removed().assignee.email).isNull();
  }

  @Test
  public void getDiff_whenHashtagsChanged_returnsHashtags() {
    String removedHashtag = "removed";
    String addedHashtag = "added";
    ChangeInfo oldChangeInfo = createChangeInfoWithHashtags(removedHashtag, "existing");
    ChangeInfo newChangeInfo = createChangeInfoWithHashtags("existing", addedHashtag);

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().hashtags).isNotNull();
    assertThat(diff.added().hashtags).containsExactly(addedHashtag);
    assertThat(diff.removed().hashtags).isNotNull();
    assertThat(diff.removed().hashtags).containsExactly(removedHashtag);
  }

  @Test
  public void getDiff_whenDuplicateHashtagAdded_returnsHashtag() {
    String hashtag = "hashtag";
    ChangeInfo oldChangeInfo = createChangeInfoWithHashtags(hashtag, hashtag);
    ChangeInfo newChangeInfo = createChangeInfoWithHashtags(hashtag, hashtag, hashtag);

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().hashtags).isNotNull();
    assertThat(diff.added().hashtags).containsExactly(hashtag);
    assertThat(diff.removed().hashtags).isNull();
  }

  @Test
  public void getDiff_whenChangeMessageUnchanged_returnsNullMessage() {
    String message = "message";
    ChangeInfo oldChangeInfo = new ChangeInfo(new ChangeMessageInfo(message));
    ChangeInfo newChangeInfo = new ChangeInfo(new ChangeMessageInfo(message));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().messages).isNull();
    assertThat(diff.removed().messages).isNull();
  }

  @Test
  public void getDiff_whenChangeMessageAdded_returnsAdded() {
    ChangeMessageInfo addedMessage = new ChangeMessageInfo("added");
    ChangeMessageInfo existingMessage = new ChangeMessageInfo("existing");
    ChangeInfo oldChangeInfo = new ChangeInfo(existingMessage);
    ChangeInfo newChangeInfo = new ChangeInfo(existingMessage, addedMessage);

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().messages).isNotNull();
    assertThat(diff.added().messages).containsExactly(addedMessage);
    assertThat(diff.removed().messages).isNull();
  }

  @Test
  public void getDiff_whenChangeMessageRemoved_returnsRemoved() {
    ChangeMessageInfo removedMessage = new ChangeMessageInfo("removed");
    ChangeMessageInfo existingMessage = new ChangeMessageInfo("existing");
    ChangeInfo oldChangeInfo = new ChangeInfo(existingMessage, removedMessage);
    ChangeInfo newChangeInfo = new ChangeInfo(existingMessage);

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().messages).isNull();
    assertThat(diff.removed().messages).isNotNull();
    assertThat(diff.removed().messages).containsExactly(removedMessage);
  }

  @Test
  public void getDiff_whenDuplicateMessagesAdded_returnsDuplicates() {
    ChangeMessageInfo message = new ChangeMessageInfo("message");
    ChangeInfo oldChangeInfo = new ChangeInfo(message, message);
    ChangeInfo newChangeInfo = new ChangeInfo(message, message, message, message);

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().messages).isNotNull();
    assertThat(diff.added().messages).containsExactly(message, message);
    assertThat(diff.removed().messages).isNull();
  }

  @Test
  public void getDiff_whenNoNewRevisions_returnsNullRevisions() {
    ChangeInfo oldChangeInfo = new ChangeInfo(ImmutableMap.of(REVISION, new RevisionInfo("ref")));
    ChangeInfo newChangeInfo = new ChangeInfo(ImmutableMap.of(REVISION, new RevisionInfo("ref")));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().revisions).isNull();
    assertThat(diff.removed().revisions).isNull();
  }

  @Test
  public void getDiff_whenOneAddedRevision_returnsRevision() {
    RevisionInfo addedRevision = new RevisionInfo("ref");
    ChangeInfo oldChangeInfo = new ChangeInfo(ImmutableMap.of());
    ChangeInfo newChangeInfo = new ChangeInfo(ImmutableMap.of(REVISION, addedRevision));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().revisions).isNotNull();
    assertThat(diff.added().revisions).hasSize(1);
    assertThat(diff.added().revisions).containsKey(REVISION);
    assertThat(diff.added().revisions.get(REVISION).ref).isEqualTo(addedRevision.ref);
    assertThat(diff.removed().revisions).isNull();
  }

  @Test
  public void getDiff_whenOneModifiedRevision_returnsModificationsToRevision() {
    RevisionInfo oldRevision = new RevisionInfo("ref", 1);
    RevisionInfo newRevision = new RevisionInfo(oldRevision.ref, 2);
    ChangeInfo oldChangeInfo = new ChangeInfo(ImmutableMap.of(REVISION, oldRevision));
    ChangeInfo newChangeInfo = new ChangeInfo(ImmutableMap.of(REVISION, newRevision));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().revisions).isNotNull();
    assertThat(diff.added().revisions).hasSize(1);
    assertThat(diff.added().revisions).containsKey(REVISION);
    assertThat(diff.added().revisions.get(REVISION).ref).isNull();
    assertThat(diff.added().revisions.get(REVISION)._number).isEqualTo(newRevision._number);
    assertThat(diff.removed().revisions).isNotNull();
    assertThat(diff.removed().revisions).hasSize(1);
    assertThat(diff.removed().revisions).containsKey(REVISION);
    assertThat(diff.removed().revisions.get(REVISION).ref).isNull();
    assertThat(diff.removed().revisions.get(REVISION)._number).isEqualTo(oldRevision._number);
  }

  @Test
  public void getDiff_whenOneModifiedRevisionUploader_returnsModificationsToRevisionUploader() {
    RevisionInfo oldRevision = new RevisionInfo(new AccountInfo("name", "email@mail.com"));
    RevisionInfo newRevision =
        new RevisionInfo(
            new AccountInfo(oldRevision.uploader.name, oldRevision.uploader.email + "2"));
    ChangeInfo oldChangeInfo = new ChangeInfo(ImmutableMap.of(REVISION, oldRevision));
    ChangeInfo newChangeInfo = new ChangeInfo(ImmutableMap.of(REVISION, newRevision));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().revisions).isNotNull();
    assertThat(diff.added().revisions).hasSize(1);
    assertThat(diff.added().revisions).containsKey(REVISION);
    assertThat(diff.added().revisions.get(REVISION).uploader).isNotNull();
    assertThat(diff.added().revisions.get(REVISION).uploader.name).isNull();
    assertThat(diff.added().revisions.get(REVISION).uploader.email)
        .isEqualTo(newRevision.uploader.email);
    assertThat(diff.added().revisions.get(REVISION).realUploader).isNull();
    assertThat(diff.removed().revisions).isNotNull();
    assertThat(diff.removed().revisions).hasSize(1);
    assertThat(diff.removed().revisions).containsKey(REVISION);
    assertThat(diff.removed().revisions.get(REVISION).uploader).isNotNull();
    assertThat(diff.removed().revisions.get(REVISION).uploader.name).isNull();
    assertThat(diff.removed().revisions.get(REVISION).uploader.email)
        .isEqualTo(oldRevision.uploader.email);
    assertThat(diff.removed().revisions.get(REVISION).realUploader).isNull();
  }

  @Test
  public void getDiff_whenOneModifiedRevisionUploader_returnsModificationsToRevisionRealUploader() {
    RevisionInfo oldRevision = new RevisionInfo(new AccountInfo("uploader", "uploader@mail.com"));
    oldRevision.realUploader = new AccountInfo("real-uploader", "real-uploader@mail.com");
    RevisionInfo newRevision = new RevisionInfo(oldRevision.uploader);
    newRevision.realUploader =
        new AccountInfo(oldRevision.realUploader.name, oldRevision.realUploader.email + "2");
    ChangeInfo oldChangeInfo = new ChangeInfo(ImmutableMap.of(REVISION, oldRevision));
    ChangeInfo newChangeInfo = new ChangeInfo(ImmutableMap.of(REVISION, newRevision));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().revisions).isNotNull();
    assertThat(diff.added().revisions).hasSize(1);
    assertThat(diff.added().revisions).containsKey(REVISION);
    assertThat(diff.added().revisions.get(REVISION).uploader).isNull();
    assertThat(diff.added().revisions.get(REVISION).realUploader).isNotNull();
    assertThat(diff.added().revisions.get(REVISION).realUploader.name).isNull();
    assertThat(diff.added().revisions.get(REVISION).realUploader.email)
        .isEqualTo(newRevision.realUploader.email);
    assertThat(diff.removed().revisions).isNotNull();
    assertThat(diff.removed().revisions).hasSize(1);
    assertThat(diff.removed().revisions).containsKey(REVISION);
    assertThat(diff.removed().revisions.get(REVISION).uploader).isNull();
    assertThat(diff.removed().revisions.get(REVISION).realUploader).isNotNull();
    assertThat(diff.removed().revisions.get(REVISION).realUploader.name).isNull();
    assertThat(diff.removed().revisions.get(REVISION).realUploader.email)
        .isEqualTo(oldRevision.realUploader.email);
  }

  @Test
  public void getDiff_whenOneUnchangedRevisionUploader_returnsNullRevision() {
    RevisionInfo oldRevision = new RevisionInfo(new AccountInfo("name", "email@mail.com"));
    RevisionInfo newRevision = new RevisionInfo(oldRevision.uploader);
    ChangeInfo oldChangeInfo = new ChangeInfo(ImmutableMap.of(REVISION, oldRevision));
    ChangeInfo newChangeInfo = new ChangeInfo(ImmutableMap.of(REVISION, newRevision));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().revisions).isNull();
    assertThat(diff.removed().revisions).isNull();
  }

  @Test
  public void getDiff_removableLabelsEmpty_returnsNullRemovableLabels() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo = new ChangeInfo();
    oldChangeInfo.removableLabels = ImmutableMap.of();
    newChangeInfo.removableLabels = ImmutableMap.of();

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().removableLabels).isNull();
    assertThat(diff.removed().removableLabels).isNull();
  }

  @Test
  public void getDiff_removableLabelsNullAndEmpty_returnsEmptyRemovableLabels() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo = new ChangeInfo();
    newChangeInfo.removableLabels = ImmutableMap.of();

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().removableLabels).isEmpty();
    assertThat(diff.removed().removableLabels).isNull();
  }

  @Test
  public void getDiff_removableLabelsEmptyAndNull_returnsEmptyRemovableLabels() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo = new ChangeInfo();
    oldChangeInfo.removableLabels = ImmutableMap.of();

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().removableLabels).isNull();
    assertThat(diff.removed().removableLabels).isEmpty();
  }

  @Test
  public void getDiff_removableLabelsLabelAdded() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo = new ChangeInfo();
    AccountInfo acc1 = new AccountInfo();
    acc1.name = "Cow";
    AccountInfo acc2 = new AccountInfo();
    acc2.name = "Pig";
    AccountInfo acc3 = new AccountInfo();
    acc3.name = "Cat";
    AccountInfo acc4 = new AccountInfo();
    acc4.name = "Dog";

    oldChangeInfo.removableLabels =
        ImmutableMap.of(
            "Code-Review",
            ImmutableMap.of("+1", ImmutableList.of(acc1), "-1", ImmutableList.of(acc2, acc3)));
    newChangeInfo.removableLabels =
        ImmutableMap.of(
            "Code-Review",
            ImmutableMap.of("+1", ImmutableList.of(acc1), "-1", ImmutableList.of(acc2, acc3)),
            "Verified",
            ImmutableMap.of("-1", ImmutableList.of(acc4)));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().removableLabels)
        .containsExactlyEntriesIn(
            ImmutableMap.of("Verified", ImmutableMap.of("-1", ImmutableList.of(acc4))));
    assertThat(diff.removed().removableLabels).isNull();
  }

  @Test
  public void getDiff_removableLabelsLabelRemoved() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo = new ChangeInfo();
    AccountInfo acc1 = new AccountInfo();
    acc1.name = "Cow";
    AccountInfo acc2 = new AccountInfo();
    acc2.name = "Pig";
    AccountInfo acc3 = new AccountInfo();
    acc3.name = "Cat";
    AccountInfo acc4 = new AccountInfo();
    acc4.name = "Dog";

    oldChangeInfo.removableLabels =
        ImmutableMap.of(
            "Code-Review",
            ImmutableMap.of("+1", ImmutableList.of(acc1), "-1", ImmutableList.of(acc2, acc3)),
            "Verified",
            ImmutableMap.of("-1", ImmutableList.of(acc4)));
    newChangeInfo.removableLabels =
        ImmutableMap.of(
            "Code-Review",
            ImmutableMap.of("+1", ImmutableList.of(acc1), "-1", ImmutableList.of(acc2, acc3)));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().removableLabels).isNull();
    assertThat(diff.removed().removableLabels)
        .containsExactlyEntriesIn(
            ImmutableMap.of("Verified", ImmutableMap.of("-1", ImmutableList.of(acc4))));
  }

  @Test
  public void getDiff_removableLabelsVoteAdded() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo = new ChangeInfo();
    AccountInfo acc1 = new AccountInfo();
    acc1.name = "acc1";
    AccountInfo acc2 = new AccountInfo();
    acc2.name = "acc2";
    AccountInfo acc3 = new AccountInfo();
    acc3.name = "acc3";

    oldChangeInfo.removableLabels =
        ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc1)));
    newChangeInfo.removableLabels =
        ImmutableMap.of(
            "Code-Review",
            ImmutableMap.of("+1", ImmutableList.of(acc1), "-1", ImmutableList.of(acc2, acc3)));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().removableLabels)
        .containsExactlyEntriesIn(
            ImmutableMap.of("Code-Review", ImmutableMap.of("-1", ImmutableList.of(acc2, acc3))));
    assertThat(diff.removed().removableLabels).isNull();
  }

  @Test
  public void getDiff_removableLabelsVoteRemoved() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo = new ChangeInfo();
    AccountInfo acc1 = new AccountInfo();
    acc1.name = "acc1";
    AccountInfo acc2 = new AccountInfo();
    acc2.name = "acc2";
    AccountInfo acc3 = new AccountInfo();
    acc3.name = "acc3";

    oldChangeInfo.removableLabels =
        ImmutableMap.of(
            "Code-Review",
            ImmutableMap.of("+1", ImmutableList.of(acc1), "-1", ImmutableList.of(acc2, acc3)));
    newChangeInfo.removableLabels =
        ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc1)));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().removableLabels).isNull();
    assertThat(diff.removed().removableLabels)
        .containsExactlyEntriesIn(
            ImmutableMap.of("Code-Review", ImmutableMap.of("-1", ImmutableList.of(acc2, acc3))));
  }

  @Test
  public void getDiff_removableLabelsAccountAdded() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo = new ChangeInfo();
    AccountInfo acc1 = new AccountInfo();
    acc1.name = "acc1";
    AccountInfo acc2 = new AccountInfo();
    acc2.name = "acc2";

    oldChangeInfo.removableLabels =
        ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc1)));
    newChangeInfo.removableLabels =
        ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc1, acc2)));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().removableLabels)
        .containsExactlyEntriesIn(
            ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc2))));
    assertThat(diff.removed().removableLabels).isNull();
  }

  @Test
  public void getDiff_removableLabelsAccountRemoved() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo = new ChangeInfo();
    AccountInfo acc1 = new AccountInfo();
    acc1.name = "acc1";
    AccountInfo acc2 = new AccountInfo();
    acc2.name = "acc2";

    oldChangeInfo.removableLabels =
        ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc1)));
    newChangeInfo.removableLabels =
        ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc1, acc2)));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().removableLabels)
        .containsExactlyEntriesIn(
            ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc2))));
    assertThat(diff.removed().removableLabels).isNull();
  }

  @Test
  public void getDiff_removableLabelsAccountChanged() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo = new ChangeInfo();
    AccountInfo acc1 = new AccountInfo();
    acc1.name = "acc1";
    AccountInfo acc2 = new AccountInfo();
    acc2.name = "acc2";

    oldChangeInfo.removableLabels =
        ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc1)));
    newChangeInfo.removableLabels =
        ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc2)));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().removableLabels)
        .containsExactlyEntriesIn(
            ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc2))));
    assertThat(diff.removed().removableLabels)
        .containsExactlyEntriesIn(
            ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc1))));
  }

  @Test
  public void getDiff_removableLabelsScoreChanged() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo = new ChangeInfo();
    AccountInfo acc1 = new AccountInfo();
    acc1.name = "acc1";

    oldChangeInfo.removableLabels =
        ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc1)));
    newChangeInfo.removableLabels =
        ImmutableMap.of("Code-Review", ImmutableMap.of("-1", ImmutableList.of(acc1)));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().removableLabels)
        .containsExactlyEntriesIn(
            ImmutableMap.of("Code-Review", ImmutableMap.of("-1", ImmutableList.of(acc1))));
    assertThat(diff.removed().removableLabels)
        .containsExactlyEntriesIn(
            ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc1))));
  }

  @Test
  public void getDiff_removableLabelsLabelChanged() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo = new ChangeInfo();
    AccountInfo acc1 = new AccountInfo();
    acc1.name = "acc1";

    oldChangeInfo.removableLabels =
        ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc1)));
    newChangeInfo.removableLabels =
        ImmutableMap.of("Verified", ImmutableMap.of("+1", ImmutableList.of(acc1)));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().removableLabels)
        .containsExactlyEntriesIn(
            ImmutableMap.of("Verified", ImmutableMap.of("+1", ImmutableList.of(acc1))));
    assertThat(diff.removed().removableLabels)
        .containsExactlyEntriesIn(
            ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc1))));
  }

  @Test
  public void getDiff_removableLabelsLabelScoreAndAccountChanged() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo = new ChangeInfo();
    AccountInfo acc1 = new AccountInfo();
    acc1.name = "acc1";
    AccountInfo acc2 = new AccountInfo();
    acc2.name = "acc2";

    oldChangeInfo.removableLabels =
        ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc1)));
    newChangeInfo.removableLabels =
        ImmutableMap.of("Verified", ImmutableMap.of("-1", ImmutableList.of(acc2)));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().removableLabels)
        .containsExactlyEntriesIn(
            ImmutableMap.of("Verified", ImmutableMap.of("-1", ImmutableList.of(acc2))));
    assertThat(diff.removed().removableLabels)
        .containsExactlyEntriesIn(
            ImmutableMap.of("Code-Review", ImmutableMap.of("+1", ImmutableList.of(acc1))));
  }

  @Test
  public void getDiff_assertCanConstructAllChangeInfoReferences() throws Exception {
    buildObjectWithFullFields(ChangeInfo.class);
  }

  @Test
  public void getDiff_arrayListInMap() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo = new ChangeInfo();

    AccountInfo i1 = new AccountInfo();
    i1._accountId = 1;
    AccountInfo i2 = new AccountInfo();
    i2._accountId = 2;

    ArrayList<AccountInfo> a1 = new ArrayList<>();
    ArrayList<AccountInfo> a2 = new ArrayList<>();

    a1.add(i1);
    a2.add(i1);
    a2.add(i2);
    oldChangeInfo.reviewers = ImmutableMap.of(ReviewerState.REVIEWER, a1);
    newChangeInfo.reviewers = ImmutableMap.of(ReviewerState.REVIEWER, a2);

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);
    assertThat(diff.added().reviewers).hasSize(1);
    assertThat(diff.added().reviewers).containsKey(ReviewerState.REVIEWER);
    assertThat(diff.added().reviewers.get(ReviewerState.REVIEWER)).containsExactly(i2);
    assertThat(diff.removed().reviewers).isNull();
  }

  @Nullable
  private static Object buildObjectWithFullFields(Class<?> c) throws Exception {
    if (c == null) {
      return null;
    }
    Object toPopulate = ChangeInfoDiffer.construct(c);
    for (Field field : toPopulate.getClass().getDeclaredFields()) {
      Class<?> parameterizedType = getParameterizedType(field);
      if (!ChangeInfoDiffer.isSimple(field.getType())
          && !field.getType().isArray()
          && !Map.class.isAssignableFrom(field.getType())
          && !Collection.class.isAssignableFrom(field.getType())) {
        field.set(toPopulate, buildObjectWithFullFields(field.getType()));
      } else if (Collection.class.isAssignableFrom(field.getType())
          && parameterizedType != null
          && !ChangeInfoDiffer.isSimple(parameterizedType)) {
        field.set(toPopulate, ImmutableList.of(buildObjectWithFullFields(parameterizedType)));
      }
    }
    return toPopulate;
  }

  @Nullable
  private static Class<?> getParameterizedType(Field field) {
    if (!Collection.class.isAssignableFrom(field.getType())) {
      return null;
    }
    Type genericType = field.getGenericType();
    if (genericType instanceof ParameterizedType) {
      return (Class<?>) ((ParameterizedType) genericType).getActualTypeArguments()[0];
    }
    return null;
  }

  private static ChangeInfo createChangeInfoWithTopic(String topic) {
    ChangeInfo changeInfo = new ChangeInfo();
    changeInfo.topic = topic;
    return changeInfo;
  }

  private static ChangeInfo createChangeInfoWithAccount(AccountInfo accountInfo) {
    ChangeInfo changeInfo = new ChangeInfo();
    changeInfo.assignee = accountInfo;
    return changeInfo;
  }

  private static ChangeInfo createChangeInfoWithHashtags(String... hashtags) {
    ChangeInfo changeInfo = new ChangeInfo();
    changeInfo.hashtags = ImmutableList.copyOf(hashtags);
    return changeInfo;
  }
}
