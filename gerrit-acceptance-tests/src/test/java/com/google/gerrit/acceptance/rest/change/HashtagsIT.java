// Copyright (C) 2014 The Android Open Source Project
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
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
import com.google.common.truth.IterableSubject;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.common.ChangeMessageInfo;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gwtorm.server.OrmException;

import org.junit.Before;
import org.junit.Test;

import java.sql.Timestamp;

@NoHttpd
public class HashtagsIT extends AbstractDaemonTest {
  @Before
  public void before() {
    assume().that(notesMigration.enabled()).isTrue();
  }

  @Test
  public void testGetNoHashtags() throws Exception {
    // Get on a change with no hashtags returns an empty list.
    PushOneCommit.Result r = createChange();
    assertThatGet(r).isEmpty();
  }

  @Test
  public void testAddSingleHashtag() throws Exception {
    PushOneCommit.Result r = createChange();

    // Adding a single hashtag returns a single hashtag.
    addHashtags(r, "tag2");
    assertThatGet(r).containsExactly("tag2");
    assertMessage(r, "Hashtag added: tag2");

    // Adding another single hashtag to change that already has one hashtag
    // returns a sorted list of hashtags with existing and new.
    addHashtags(r, "tag1");
    assertThatGet(r).containsExactly("tag1", "tag2").inOrder();
    assertMessage(r, "Hashtag added: tag1");
  }

  @Test
  public void testAddMultipleHashtags() throws Exception {
    PushOneCommit.Result r = createChange();

    // Adding multiple hashtags returns a sorted list of hashtags.
    addHashtags(r, "tag3", "tag1");
    assertThatGet(r).containsExactly("tag1", "tag3").inOrder();
    assertMessage(r, "Hashtags added: tag1, tag3");

    // Adding multiple hashtags to change that already has hashtags returns a
    // sorted list of hashtags with existing and new.
    addHashtags(r, "tag2", "tag4");
    assertThatGet(r).containsExactly("tag1", "tag2", "tag3", "tag4").inOrder();
    assertMessage(r, "Hashtags added: tag2, tag4");
  }

  @Test
  public void testAddAlreadyExistingHashtag() throws Exception {
    // Adding a hashtag that already exists on the change returns a sorted list
    // of hashtags without duplicates.
    PushOneCommit.Result r = createChange();
    addHashtags(r, "tag2");
    assertThatGet(r).containsExactly("tag2");
    assertMessage(r, "Hashtag added: tag2");
    Timestamp lastMsgDate = getLastMessage(r).date;

    addHashtags(r, "tag2");
    assertThatGet(r).containsExactly("tag2");
    assertNoNewMessageSince(r, lastMsgDate);

    addHashtags(r, "tag1", "tag2");
    assertThatGet(r).containsExactly("tag1", "tag2").inOrder();
    assertMessage(r, "Hashtag added: tag1");
  }

  @Test
  public void testHashtagsWithPrefix() throws Exception {
    PushOneCommit.Result r = createChange();

    // Leading # is stripped from added tag.
    addHashtags(r, "#tag1");
    assertThatGet(r).containsExactly("tag1");
    assertMessage(r, "Hashtag added: tag1");

    // Leading # is stripped from multiple added tags.
    addHashtags(r, "#tag2", "#tag3");
    assertThatGet(r).containsExactly("tag1", "tag2", "tag3").inOrder();
    assertMessage(r, "Hashtags added: tag2, tag3");

    // Leading # is stripped from removed tag.
    removeHashtags(r, "#tag2");
    assertThatGet(r).containsExactly("tag1", "tag3").inOrder();
    assertMessage(r, "Hashtag removed: tag2");

    // Leading # is stripped from multiple removed tags.
    removeHashtags(r, "#tag1", "#tag3");
    assertThatGet(r).isEmpty();
    assertMessage(r, "Hashtags removed: tag1, tag3");

    // Leading # and space are stripped from added tag.
    addHashtags(r, "# tag1");
    assertThatGet(r).containsExactly("tag1");
    assertMessage(r, "Hashtag added: tag1");

    // Multiple leading # are stripped from added tag.
    addHashtags(r, "##tag2");
    assertThatGet(r).containsExactly("tag1", "tag2").inOrder();
    assertMessage(r, "Hashtag added: tag2");

    // Multiple leading spaces and # are stripped from added tag.
    addHashtags(r, "# # tag3");
    assertThatGet(r).containsExactly("tag1", "tag2", "tag3").inOrder();
    assertMessage(r, "Hashtag added: tag3");
  }

  @Test
  public void testRemoveSingleHashtag() throws Exception {
    // Removing a single tag from a change that only has that tag returns an
    // empty list.
    PushOneCommit.Result r = createChange();
    addHashtags(r, "tag1");
    assertThatGet(r).containsExactly("tag1");
    removeHashtags(r, "tag1");
    assertThatGet(r).isEmpty();
    assertMessage(r, "Hashtag removed: tag1");

    // Removing a single tag from a change that has multiple tags returns a
    // sorted list of remaining tags.
    addHashtags(r, "tag1", "tag2", "tag3");
    removeHashtags(r, "tag2");
    assertThatGet(r).containsExactly("tag1", "tag3").inOrder();
    assertMessage(r, "Hashtag removed: tag2");
  }

  @Test
  public void testRemoveMultipleHashtags() throws Exception {
    // Removing multiple tags from a change that only has those tags returns an
    // empty list.
    PushOneCommit.Result r = createChange();
    addHashtags(r, "tag1", "tag2");
    assertThatGet(r).containsExactly("tag1", "tag2").inOrder();
    removeHashtags(r, "tag1", "tag2");
    assertThatGet(r).isEmpty();
    assertMessage(r, "Hashtags removed: tag1, tag2");

    // Removing multiple tags from a change that has multiple tags returns a
    // sorted list of remaining tags.
    addHashtags(r, "tag1", "tag2", "tag3", "tag4");
    assertThatGet(r).containsExactly("tag1", "tag2", "tag3", "tag4").inOrder();
    removeHashtags(r, "tag2", "tag4");
    assertThatGet(r).containsExactly("tag1", "tag3").inOrder();
    assertMessage(r, "Hashtags removed: tag2, tag4");
  }

  @Test
  public void testRemoveNotExistingHashtag() throws Exception {
    // Removing a single hashtag from change that has no hashtags returns an
    // empty list.
    PushOneCommit.Result r = createChange();
    Timestamp lastMsgDate = getLastMessage(r).date;
    removeHashtags(r, "tag1");
    assertThatGet(r).isEmpty();
    assertNoNewMessageSince(r, lastMsgDate);

    // Removing a single non-existing tag from a change that only has one other
    // tag returns a list of only one tag.
    addHashtags(r, "tag1");
    lastMsgDate = getLastMessage(r).date;
    removeHashtags(r, "tag4");
    assertThatGet(r).containsExactly("tag1");
    assertNoNewMessageSince(r, lastMsgDate);

    // Removing a single non-existing tag from a change that has multiple tags
    // returns a sorted list of tags without any deleted.
    addHashtags(r, "tag1", "tag2", "tag3");
    lastMsgDate = getLastMessage(r).date;
    removeHashtags(r, "tag4");
    assertThatGet(r).containsExactly("tag1", "tag2", "tag3").inOrder();
    assertNoNewMessageSince(r, lastMsgDate);
  }

  @Test
  public void testAddAndRemove() throws Exception {
    // Adding and remove hashtags in a single request performs correctly.
    PushOneCommit.Result r = createChange();
    addHashtags(r, "tag1", "tag2");
    HashtagsInput input = new HashtagsInput();
    input.add = Sets.newHashSet("tag3", "tag4");
    input.remove = Sets.newHashSet("tag1");
    gApi.changes().id(r.getChange().getId().get()).setHashtags(input);
    assertThatGet(r).containsExactly("tag2", "tag3", "tag4");
    assertMessage(r, "Hashtags added: tag3, tag4\nHashtag removed: tag1");

    // Adding and removing the same hashtag actually removes it.
    addHashtags(r, "tag1", "tag2");
    input = new HashtagsInput();
    input.add = Sets.newHashSet("tag3", "tag4");
    input.remove = Sets.newHashSet("tag3");
    gApi.changes().id(r.getChange().getId().get()).setHashtags(input);
    assertThatGet(r).containsExactly("tag1", "tag2", "tag4");
    assertMessage(r, "Hashtag removed: tag3");
  }

  private IterableSubject<
        ? extends IterableSubject<?, String, Iterable<String>>,
        String, Iterable<String>>
      assertThatGet(PushOneCommit.Result r) throws Exception {
    return assertThat(gApi.changes()
        .id(r.getChange().getId().get())
        .getHashtags());
  }

  private void addHashtags(PushOneCommit.Result r, String... toAdd)
      throws Exception {
    HashtagsInput input = new HashtagsInput();
    input.add = Sets.newHashSet(toAdd);
    gApi.changes()
        .id(r.getChange().getId().get())
        .setHashtags(input);
  }

  private void removeHashtags(PushOneCommit.Result r, String... toRemove)
      throws Exception {
    HashtagsInput input = new HashtagsInput();
    input.remove = Sets.newHashSet(toRemove);
    gApi.changes()
        .id(r.getChange().getId().get())
        .setHashtags(input);
  }

  private void assertMessage(PushOneCommit.Result r, String expectedMessage)
      throws RestApiException, OrmException {
    assertThat(getLastMessage(r).message).isEqualTo(expectedMessage);
  }

  private void assertNoNewMessageSince(PushOneCommit.Result r, Timestamp date)
      throws RestApiException, OrmException {
    assertThat(getLastMessage(r).date).isEqualTo(date);
  }

  private ChangeMessageInfo getLastMessage(PushOneCommit.Result r)
      throws RestApiException, OrmException {
    ChangeMessageInfo lastMessage = Iterables.getLast(
        gApi.changes().id(r.getChange().getId().get()).get().messages, null);
    assertThat(lastMessage).isNotNull();
    return lastMessage;
  }
}
