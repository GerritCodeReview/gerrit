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
import java.lang.reflect.Field;
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
    assertThat(diff.added().branch).isNull();
    assertThat(diff.added().project).isNull();
    assertThat(diff.added().currentRevision).isNull();
    assertThat(diff.added().actions).isNull();
    assertThat(diff.added().messages).isNull();
    assertThat(diff.added().reviewers).isNull();
    assertThat(diff.added().hashtags).isNull();
    assertThat(diff.removed().branch).isNull();
    assertThat(diff.removed().project).isNull();
    assertThat(diff.removed().currentRevision).isNull();
    assertThat(diff.removed().actions).isNull();
    assertThat(diff.removed().messages).isNull();
    assertThat(diff.removed().reviewers).isNull();
    assertThat(diff.removed().hashtags).isNull();
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
        createChangeInfoWithAccount(createAccountInfo("name", "mail@mail.com"));
    ChangeInfo newChangeInfo =
        createChangeInfoWithAccount(
            createAccountInfo(oldChangeInfo.assignee.name, oldChangeInfo.assignee.email));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().assignee).isNull();
    assertThat(diff.removed().assignee).isNull();
  }

  @Test
  public void getDiff_givenNewAssignee_returnsAssignee() {
    ChangeInfo oldChangeInfo = new ChangeInfo();
    ChangeInfo newChangeInfo =
        createChangeInfoWithAccount(createAccountInfo("name", "mail@mail.com"));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().assignee).isEqualTo(newChangeInfo.assignee);
    assertThat(diff.removed().assignee).isNull();
  }

  @Test
  public void getDiff_withRemovedAssignee_returnsAssignee() {
    ChangeInfo oldChangeInfo =
        createChangeInfoWithAccount(createAccountInfo("name", "mail@mail.com"));
    ChangeInfo newChangeInfo = new ChangeInfo();

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().assignee).isNull();
    assertThat(diff.removed().assignee).isEqualTo(oldChangeInfo.assignee);
  }

  @Test
  public void getDiff_givenAssigneeWithNewName_returnsNameButNotEmail() {
    ChangeInfo oldChangeInfo =
        createChangeInfoWithAccount(createAccountInfo("old name", "mail@mail.com"));
    ChangeInfo newChangeInfo =
        createChangeInfoWithAccount(createAccountInfo("new name", oldChangeInfo.assignee.email));

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
    ChangeInfo oldChangeInfo = createChangeInfoWithMessages(createChangeMessageInfo(message));
    ChangeInfo newChangeInfo = createChangeInfoWithMessages(createChangeMessageInfo(message));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().messages).isNull();
    assertThat(diff.removed().messages).isNull();
  }

  @Test
  public void getDiff_whenChangeMessageAdded_returnsAdded() {
    ChangeMessageInfo addedMessage = createChangeMessageInfo("added");
    ChangeMessageInfo existingMessage = createChangeMessageInfo("existing");
    ChangeInfo oldChangeInfo = createChangeInfoWithMessages(existingMessage);
    ChangeInfo newChangeInfo = createChangeInfoWithMessages(existingMessage, addedMessage);

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().messages).isNotNull();
    assertThat(diff.added().messages).containsExactly(addedMessage);
    assertThat(diff.removed().messages).isNull();
  }

  @Test
  public void getDiff_whenChangeMessageRemoved_returnsRemoved() {
    ChangeMessageInfo removedMessage = createChangeMessageInfo("removed");
    ChangeMessageInfo existingMessage = createChangeMessageInfo("existing");
    ChangeInfo oldChangeInfo = createChangeInfoWithMessages(existingMessage, removedMessage);
    ChangeInfo newChangeInfo = createChangeInfoWithMessages(existingMessage);

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().messages).isNull();
    assertThat(diff.removed().messages).isNotNull();
    assertThat(diff.removed().messages).containsExactly(removedMessage);
  }

  @Test
  public void getDiff_whenDuplicateMessagesAdded_returnsDuplicates() {
    ChangeMessageInfo message = createChangeMessageInfo("message");
    ChangeInfo oldChangeInfo = createChangeInfoWithMessages(message, message);
    ChangeInfo newChangeInfo = createChangeInfoWithMessages(message, message, message, message);

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().messages).isNotNull();
    assertThat(diff.added().messages).containsExactly(message, message);
    assertThat(diff.removed().messages).isNull();
  }

  @Test
  public void getDiff_whenNoNewRevisions_returnsNullRevisions() {
    ChangeInfo oldChangeInfo =
        createChangeInfoWithRevisions(ImmutableMap.of(REVISION, createRevisionInfo("ref")));
    ChangeInfo newChangeInfo =
        createChangeInfoWithRevisions(ImmutableMap.of(REVISION, createRevisionInfo("ref")));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().revisions).isNull();
    assertThat(diff.removed().revisions).isNull();
  }

  @Test
  public void getDiff_whenOneAddedRevision_returnsRevision() {
    RevisionInfo addedRevision = createRevisionInfo("ref");
    ChangeInfo oldChangeInfo = createChangeInfoWithRevisions(ImmutableMap.of());
    ChangeInfo newChangeInfo =
        createChangeInfoWithRevisions(ImmutableMap.of(REVISION, addedRevision));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().revisions).isNotNull();
    assertThat(diff.added().revisions).hasSize(1);
    assertThat(diff.added().revisions).containsKey(REVISION);
    assertThat(diff.added().revisions.get(REVISION).ref).isEqualTo(addedRevision.ref);
    assertThat(diff.removed().revisions).isNull();
  }

  @Test
  public void getDiff_whenOneModifiedRevision_returnsModificationsToRevision() {
    RevisionInfo oldRevision = createRevisionInfo("ref", 1);
    RevisionInfo newRevision = createRevisionInfo(oldRevision.ref, 2);
    ChangeInfo oldChangeInfo =
        createChangeInfoWithRevisions(ImmutableMap.of(REVISION, oldRevision));
    ChangeInfo newChangeInfo =
        createChangeInfoWithRevisions(ImmutableMap.of(REVISION, newRevision));

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
    RevisionInfo oldRevision =
        createRevisionInfoWithUploader(createAccountInfo("name", "email@mail.com"));
    RevisionInfo newRevision =
        createRevisionInfoWithUploader(
            createAccountInfo(oldRevision.uploader.name, oldRevision.uploader.email + "2"));
    ChangeInfo oldChangeInfo =
        createChangeInfoWithRevisions(ImmutableMap.of(REVISION, oldRevision));
    ChangeInfo newChangeInfo =
        createChangeInfoWithRevisions(ImmutableMap.of(REVISION, newRevision));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().revisions).isNotNull();
    assertThat(diff.added().revisions).hasSize(1);
    assertThat(diff.added().revisions).containsKey(REVISION);
    assertThat(diff.added().revisions.get(REVISION).uploader).isNotNull();
    assertThat(diff.added().revisions.get(REVISION).uploader.name).isNull();
    assertThat(diff.added().revisions.get(REVISION).uploader.email)
        .isEqualTo(newRevision.uploader.email);
    assertThat(diff.removed().revisions).isNotNull();
    assertThat(diff.removed().revisions).hasSize(1);
    assertThat(diff.removed().revisions).containsKey(REVISION);
    assertThat(diff.removed().revisions.get(REVISION).uploader).isNotNull();
    assertThat(diff.removed().revisions.get(REVISION).uploader.name).isNull();
    assertThat(diff.removed().revisions.get(REVISION).uploader.email)
        .isEqualTo(oldRevision.uploader.email);
  }

  @Test
  public void getDiff_whenOneUnchangedRevisionUploader_returnsNullRevision() {
    RevisionInfo oldRevision =
        createRevisionInfoWithUploader(createAccountInfo("name", "email@mail.com"));
    RevisionInfo newRevision = createRevisionInfoWithUploader(oldRevision.uploader);
    ChangeInfo oldChangeInfo =
        createChangeInfoWithRevisions(ImmutableMap.of(REVISION, oldRevision));
    ChangeInfo newChangeInfo =
        createChangeInfoWithRevisions(ImmutableMap.of(REVISION, newRevision));

    ChangeInfoDifference diff = ChangeInfoDiffer.getDifference(oldChangeInfo, newChangeInfo);

    assertThat(diff.added().revisions).isNull();
    assertThat(diff.removed().revisions).isNull();
  }

  @Test
  public void getDiff_assertCanConstructAllChangeInfoReferences() throws Exception {
    buildObjectWithFullFields(ChangeInfo.class);
  }

  private static Object buildObjectWithFullFields(Class<?> c) throws Exception {
    if (c == null) {
      return null;
    }
    Object toPopulate = construct(c);
    for (Field field : toPopulate.getClass().getDeclaredFields()) {
      if (!ChangeInfoDiffer.isSimple(field.getType())
          && !field.getType().isArray()
          && !Map.class.isAssignableFrom(field.getType())
          && !Collection.class.isAssignableFrom(field.getType())) {
        field.set(toPopulate, buildObjectWithFullFields(field.getType()));
      }
    }
    return toPopulate;
  }

  private static Object construct(Class<?> c) {
    Object obj = ChangeInfoDiffer.construct(c);
    if (obj == null) {
      throw new IllegalStateException("Valid constructor for class not found: " + c.getName());
    }
    return obj;
  }

  private static ChangeMessageInfo createChangeMessageInfo(String message) {
    ChangeMessageInfo changeMessageInfo = new ChangeMessageInfo();
    changeMessageInfo.message = message;
    return changeMessageInfo;
  }

  private static AccountInfo createAccountInfo(String name, String email) {
    AccountInfo accountInfo = new AccountInfo();
    accountInfo.name = name;
    accountInfo.email = email;
    return accountInfo;
  }

  private static RevisionInfo createRevisionInfoWithUploader(AccountInfo uploader) {
    RevisionInfo revisionInfo = new RevisionInfo();
    revisionInfo.uploader = uploader;
    return revisionInfo;
  }

  private static RevisionInfo createRevisionInfo(String ref, int number) {
    RevisionInfo revisionInfo = new RevisionInfo();
    revisionInfo.ref = ref;
    revisionInfo._number = number;
    return revisionInfo;
  }

  private static RevisionInfo createRevisionInfo(String ref) {
    RevisionInfo revisionInfo = new RevisionInfo();
    revisionInfo.ref = ref;
    return revisionInfo;
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

  private static ChangeInfo createChangeInfoWithRevisions(Map<String, RevisionInfo> revisions) {
    ChangeInfo changeInfo = new ChangeInfo();
    changeInfo.revisions = revisions;
    return changeInfo;
  }

  private static ChangeInfo createChangeInfoWithHashtags(String... hashtags) {
    ChangeInfo changeInfo = new ChangeInfo();
    changeInfo.hashtags = ImmutableList.copyOf(hashtags);
    return changeInfo;
  }

  private static ChangeInfo createChangeInfoWithMessages(ChangeMessageInfo... changeMessageInfos) {
    ChangeInfo changeInfo = new ChangeInfo();
    changeInfo.messages = ImmutableList.copyOf(changeMessageInfos);
    return changeInfo;
  }
}
