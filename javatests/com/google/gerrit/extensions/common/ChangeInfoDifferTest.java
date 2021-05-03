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
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
    assertThat(diff.removed()._number).isNull();
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
    RevisionInfo oldRevision = new RevisionInfo(new AccountInfo("name", "email@mail.com"));
    RevisionInfo newRevision = new RevisionInfo(oldRevision.uploader);
    ChangeInfo oldChangeInfo = new ChangeInfo(ImmutableMap.of(REVISION, oldRevision));
    ChangeInfo newChangeInfo = new ChangeInfo(ImmutableMap.of(REVISION, newRevision));

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

  private static ChangeInfo createChangeInfoWithHashtags(String... hashtags) {
    ChangeInfo changeInfo = new ChangeInfo();
    changeInfo.hashtags = ImmutableList.copyOf(hashtags);
    return changeInfo;
  }
}
