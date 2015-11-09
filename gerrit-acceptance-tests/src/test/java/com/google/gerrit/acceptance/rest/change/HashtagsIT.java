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

import com.google.common.collect.Sets;
import com.google.common.truth.IterableSubject;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.extensions.api.changes.HashtagsInput;

import org.junit.Test;

@NoHttpd
public class HashtagsIT extends AbstractDaemonTest {
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

    // Adding another single hashtag to change that already has one hashtag
    // returns a sorted list of hashtags with existing and new.
    addHashtags(r, "tag1");
    assertThatGet(r).containsExactly("tag1", "tag2").inOrder();
  }

  @Test
  public void testAddMultipleHashtags() throws Exception {
    PushOneCommit.Result r = createChange();

    // Adding multiple hashtags returns a sorted list of hashtags.
    addHashtags(r, "tag3", "tag1");
    assertThatGet(r).containsExactly("tag1", "tag3").inOrder();

    // Adding multiple hashtags to change that already has hashtags returns a
    // sorted list of hashtags with existing and new.
    addHashtags(r, "tag2", "tag4");
    assertThatGet(r).containsExactly("tag1", "tag2", "tag3", "tag4").inOrder();
  }

  @Test
  public void testAddAlreadyExistingHashtag() throws Exception {
    // Adding a hashtag that already exists on the change returns a sorted list
    // of hashtags without duplicates.
    PushOneCommit.Result r = createChange();
    addHashtags(r, "tag2");
    assertThatGet(r).containsExactly("tag2");
    addHashtags(r, "tag2");
    assertThatGet(r).containsExactly("tag2");
    addHashtags(r, "tag1", "tag2");
    assertThatGet(r).containsExactly("tag1", "tag2").inOrder();
  }

  @Test
  public void testHashtagsWithPrefix() throws Exception {
    PushOneCommit.Result r = createChange();

    // Leading # is stripped from added tag.
    addHashtags(r, "#tag1");
    assertThatGet(r).containsExactly("tag1");

    // Leading # is stripped from multiple added tags.
    addHashtags(r, "#tag2", "#tag3");
    assertThatGet(r).containsExactly("tag1", "tag2", "tag3").inOrder();

    // Leading # is stripped from removed tag.
    removeHashtags(r, "#tag2");
    assertThatGet(r).containsExactly("tag1", "tag3").inOrder();

    // Leading # is stripped from multiple removed tags.
    removeHashtags(r, "#tag1", "#tag3");
    assertThatGet(r).isEmpty();

    // Leading # and space are stripped from added tag.
    addHashtags(r, "# tag1");
    assertThatGet(r).containsExactly("tag1");

    // Multiple leading # are stripped from added tag.
    addHashtags(r, "##tag2");
    assertThatGet(r).containsExactly("tag1", "tag2").inOrder();

    // Multiple leading spaces and # are stripped from added tag.
    addHashtags(r, "# # tag3");
    assertThatGet(r).containsExactly("tag1", "tag2", "tag3").inOrder();
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

    // Removing a single tag from a change that has multiple tags returns a
    // sorted list of remaining tags.
    addHashtags(r, "tag1", "tag2", "tag3");
    removeHashtags(r, "tag2");
    assertThatGet(r).containsExactly("tag1", "tag3").inOrder();
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

    // Removing multiple tags from a change that has multiple tags returns a
    // sorted list of remaining tags.
    addHashtags(r, "tag1", "tag2", "tag3", "tag4");
    assertThatGet(r).containsExactly("tag1", "tag2", "tag3", "tag4").inOrder();
    removeHashtags(r, "tag2", "tag4");
    assertThatGet(r).containsExactly("tag1", "tag3").inOrder();
  }

  @Test
  public void testRemoveNotExistingHashtag() throws Exception {
    // Removing a single hashtag from change that has no hashtags returns an
    // empty list.
    PushOneCommit.Result r = createChange();
    removeHashtags(r, "tag1");
    assertThatGet(r).isEmpty();

    // Removing a single non-existing tag from a change that only has one other
    // tag returns a list of only one tag.
    addHashtags(r, "tag1");
    removeHashtags(r, "tag4");
    assertThatGet(r).containsExactly("tag1");

    // Removing a single non-existing tag from a change that has multiple tags
    // returns a sorted list of tags without any deleted.
    addHashtags(r, "tag1", "tag2", "tag3");
    removeHashtags(r, "tag4");
    assertThatGet(r).containsExactly("tag1", "tag2", "tag3").inOrder();
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

    // Adding and removing the same hashtag actually removes it.
    addHashtags(r, "tag1", "tag2");
    input = new HashtagsInput();
    input.add = Sets.newHashSet("tag3", "tag4");
    input.remove = Sets.newHashSet("tag3");
    gApi.changes().id(r.getChange().getId().get()).setHashtags(input);
    assertThatGet(r).containsExactly("tag1", "tag2", "tag4");
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
}
