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

import com.google.common.base.CharMatcher;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

public class HashtagsIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    return NotesMigration.allEnabledConfig();
  }

  private static String FOOTER_KEY_HASHTAGS = "Hashtags";

  private void assertResult(RestResponse r, List<String> expected)
      throws IOException {
    assertThat(r.getStatusCode()).isEqualTo(HttpStatus.SC_OK);
    List<String> result = toHashtagList(r);
    assertThat(result).containsExactlyElementsIn(expected);
  }

  @Test
  public void testGetNoHashtags() throws Exception {
    // GET hashtags on a change with no hashtags returns an empty list
    String changeId = createChange().getChangeId();
    assertResult(GET(changeId), ImmutableList.<String>of());
  }

  @Test
  public void testHashtagsCreatedFromCommitMessage() throws Exception {
    String footer1 = "tag1";
    String footer2 = "tag2,tag3";
    List<String> created = Arrays.asList(footer1, "tag2", "tag3");
    List<String> updated = Arrays.asList("tag2", "tag3");

    // Create hashtags from commit message.
    String changeId = newChange(footer1, footer2).getChangeId();
    assertResult(GET(changeId), created);

    // If there are hastags specified in current patch-set's commit message,
    // before remove them from hashtags pool, need to delete them firstly from
    // commit message.
    assertEquals(HttpStatus.SC_CONFLICT, POST(changeId, null, "tag1")
        .getStatusCode());
    assertResult(GET(changeId), created);
    amendChange(changeId, footer2);
    assertResult(GET(changeId), created);
    assertResult(POST(changeId, null, "tag1"), updated);
  }

  @Test
  public void testAddSingleHashtag() throws Exception {
    String changeId = createChange().getChangeId();

    // POST adding a single hashtag returns a single hashtag
    List<String> expected = Arrays.asList("tag2");
    assertResult(POST(changeId, "tag2", null), expected);
    assertResult(GET(changeId), expected);

    // POST adding another single hashtag to change that already has one
    // hashtag returns a sorted list of hashtags with existing and new
    expected = Arrays.asList("tag1", "tag2");
    assertResult(POST(changeId, "tag1", null), expected);
    assertResult(GET(changeId), expected);
  }

  @Test
  public void testAddMultipleHashtags() throws Exception {
    String changeId = createChange().getChangeId();

    // POST adding multiple hashtags returns a sorted list of hashtags
    List<String> expected = Arrays.asList("tag1", "tag3");
    assertResult(POST(changeId, "tag3, tag1", null), expected);
    assertResult(GET(changeId), expected);

    // POST adding multiple hashtags to change that already has hashtags
    // returns a sorted list of hashtags with existing and new
    expected = Arrays.asList("tag1", "tag2", "tag3", "tag4");
    assertResult(POST(changeId, "tag2, tag4", null), expected);
    assertResult(GET(changeId), expected);
  }

  @Test
  public void testAddAlreadyExistingHashtag() throws Exception {
    // POST adding a hashtag that already exists on the change returns a
    // sorted list of hashtags without duplicates
    String changeId = createChange().getChangeId();
    List<String> expected = Arrays.asList("tag2");
    assertResult(POST(changeId, "tag2", null), expected);
    assertResult(GET(changeId), expected);
    assertResult(POST(changeId, "tag2", null), expected);
    assertResult(GET(changeId), expected);
    expected = Arrays.asList("tag1", "tag2");
    assertResult(POST(changeId, "tag2, tag1", null), expected);
    assertResult(GET(changeId), expected);
  }

  @Test
  public void testHashtagsWithPrefix() throws Exception {
    String changeId = createChange().getChangeId();

    // Leading # is stripped from added tag
    List<String> expected = Arrays.asList("tag1");
    assertResult(POST(changeId, "#tag1", null), expected);
    assertResult(GET(changeId), expected);

    // Leading # is stripped from multiple added tags
    expected = Arrays.asList("tag1", "tag2", "tag3");
    assertResult(POST(changeId, "#tag2, #tag3", null), expected);
    assertResult(GET(changeId), expected);

    // Leading # is stripped from removed tag
    expected = Arrays.asList("tag1", "tag3");
    assertResult(POST(changeId, null, "#tag2"), expected);
    assertResult(GET(changeId), expected);

    // Leading # is stripped from multiple removed tags
    expected = Collections.emptyList();
    assertResult(POST(changeId, null, "#tag1, #tag3"), expected);
    assertResult(GET(changeId), expected);

    // Leading # and space are stripped from added tag
    expected = Arrays.asList("tag1");
    assertResult(POST(changeId, "# tag1", null), expected);
    assertResult(GET(changeId), expected);

    // Multiple leading # are stripped from added tag
    expected = Arrays.asList("tag1", "tag2");
    assertResult(POST(changeId, "##tag2", null), expected);
    assertResult(GET(changeId), expected);

    // Multiple leading spaces and # are stripped from added tag
    expected = Arrays.asList("tag1", "tag2", "tag3");
    assertResult(POST(changeId, " # # tag3", null), expected);
    assertResult(GET(changeId), expected);
  }

  @Test
  public void testRemoveSingleHashtag() throws Exception {
    // POST removing a single tag from a change that only has that tag
    // returns an empty list
    String changeId = createChange().getChangeId();
    List<String> expected = Arrays.asList("tag1");
    assertResult(POST(changeId, "tag1", null), expected);
    assertResult(POST(changeId, null, "tag1"), ImmutableList.<String>of());
    assertResult(GET(changeId), ImmutableList.<String>of());

    // POST removing a single tag from a change that has multiple tags
    // returns a sorted list of remaining tags
    expected = Arrays.asList("tag1", "tag2", "tag3");
    assertResult(POST(changeId, "tag1, tag2, tag3", null), expected);
    expected = Arrays.asList("tag1", "tag3");
    assertResult(POST(changeId, null, "tag2"), expected);
    assertResult(GET(changeId), expected);
  }

  @Test
  public void testRemoveMultipleHashtags() throws Exception {
    // POST removing multiple tags from a change that only has those tags
    // returns an empty list
    String changeId = createChange().getChangeId();
    List<String> expected = Arrays.asList("tag1", "tag2");
    assertResult(POST(changeId, "tag1, tag2", null), expected);
    assertResult(POST(changeId, null, "tag1, tag2"), ImmutableList.<String>of());
    assertResult(GET(changeId), ImmutableList.<String>of());

    // POST removing multiple tags from a change that has multiple changes
    // returns a sorted list of remaining changes
    expected = Arrays.asList("tag1", "tag2", "tag3", "tag4");
    assertResult(POST(changeId, "tag1, tag2, tag3, tag4", null), expected);
    expected = Arrays.asList("tag2", "tag4");
    assertResult(POST(changeId, null, "tag1, tag3"), expected);
    assertResult(GET(changeId), expected);
  }

  @Test
  public void testRemoveNotExistingHashtag() throws Exception {
    // POST removing a single hashtag from change that has no hashtags
    // returns an empty list
    String changeId = createChange().getChangeId();
    assertResult(POST(changeId, null, "tag1"), ImmutableList.<String>of());
    assertResult(GET(changeId), ImmutableList.<String>of());

    // POST removing a single non-existing tag from a change that only
    // has one other tag returns a list of only one tag
    List<String> expected = Arrays.asList("tag1");
    assertResult(POST(changeId, "tag1", null), expected);
    assertResult(POST(changeId, null, "tag4"), expected);
    assertResult(GET(changeId), expected);

    // POST removing a single non-existing tag from a change that has multiple
    // tags returns a sorted list of tags without any deleted
    expected = Arrays.asList("tag1", "tag2", "tag3");
    assertResult(POST(changeId, "tag1, tag2, tag3", null), expected);
    assertResult(POST(changeId, null, "tag4"), expected);
    assertResult(GET(changeId), expected);
  }

  private RestResponse GET(String changeId) throws IOException {
    return adminSession.get("/changes/" + changeId + "/hashtags/");
  }

  private RestResponse POST(String changeId, String toAdd, String toRemove)
      throws IOException {
    HashtagsInput input = new HashtagsInput();
    if (toAdd != null) {
      input.add = new HashSet<>(
          Lists.newArrayList(Splitter.on(CharMatcher.anyOf(",")).split(toAdd)));
    }
    if (toRemove != null) {
      input.remove = new HashSet<>(
          Lists.newArrayList(Splitter.on(CharMatcher.anyOf(",")).split(toRemove)));
    }
    return adminSession.post("/changes/" + changeId + "/hashtags/", input);
  }

  private static List<String> toHashtagList(RestResponse r)
      throws IOException {
    List<String> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<List<String>>() {}.getType());
    return result;
  }

  private String getCommitMessageWithHashtagsFooters(String... hashtagsFooters) {
    StringBuilder msg = new StringBuilder("Add hashtags from commit message. ");
    msg.append("\n\n");
    for (String f : hashtagsFooters) {
      msg.append(FOOTER_KEY_HASHTAGS).append(":").append(f).append("\n");
    }
    return msg.toString();
  }

  private PushOneCommit.Result newChange(String... hashtagsFooters)
      throws GitAPIException, IOException {
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(),
            getCommitMessageWithHashtagsFooters(hashtagsFooters),
            PushOneCommit.FILE_NAME, PushOneCommit.FILE_CONTENT);
    return push.to(git, "refs/for/master");
  }

  private PushOneCommit.Result amendChange(String changeId,
      String... hashtagsFooters) throws GitAPIException, IOException {
    PushOneCommit push =
        pushFactory.create(db, admin.getIdent(),
            getCommitMessageWithHashtagsFooters(hashtagsFooters),
            PushOneCommit.FILE_NAME, PushOneCommit.FILE_CONTENT, changeId);
    return push.to(git, "refs/for/master");
  }
}
