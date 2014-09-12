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

import static org.junit.Assert.assertEquals;

import com.google.common.collect.ImmutableList;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.testutil.ConfigSuite;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.lib.Config;
import org.junit.Test;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class HashtagsIT extends AbstractDaemonTest {
  @ConfigSuite.Default
  public static Config defaultConfig() {
    return NotesMigration.allEnabledConfig();
  }

  private void assertResult(RestResponse r, List<String> expected)
      throws IOException {
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    List<String> result = toHashtagList(r);
    assertEquals(expected, result);
  }

  @Test
  public void testGetNoHashtags() throws Exception {
    // GET hashtags on a change with no hashtags returns an empty list
    String changeId = createChange().getChangeId();
    assertResult(GET(changeId), ImmutableList.<String>of());
  }

  @Test
  public void testPutSingleHashtag() throws Exception {
    String changeId = createChange().getChangeId();

    // PUT a single hashtag returns a single hashtag
    List<String> expected = Arrays.asList("tag2");
    assertResult(PUT(changeId, "tag2"), expected);
    assertResult(GET(changeId), expected);

    // PUT another single hashtag to change that already has one hashtag
    // returns sorted list of hashtags with existing and new
    expected = Arrays.asList("tag1", "tag2");
    assertResult(PUT(changeId, "tag1"), expected);
    assertResult(GET(changeId), expected);
  }

  @Test
  public void testPutMultipleHashtags() throws Exception {
    String changeId = createChange().getChangeId();

    // PUT multiple hashtags returns sorted list of hashtags
    List<String> expected = Arrays.asList("tag1", "tag3");
    assertResult(PUT(changeId, "tag3, tag1"), expected);
    assertResult(GET(changeId), expected);

    // PUT multiple hashtags to change that already has hashtags
    // returns sorted list of hashtags with existing and new
    expected = Arrays.asList("tag1", "tag2", "tag3", "tag4");
    assertResult(PUT(changeId, "tag2, tag4"), expected);
    assertResult(GET(changeId), expected);
  }

  @Test
  public void testPutAlreadyExistingHashtag() throws Exception {
    // PUT a hashtag that already exists on the change returns
    // sorted list of hashtags without duplicates
    String changeId = createChange().getChangeId();
    List<String> expected = Arrays.asList("tag2");
    assertResult(PUT(changeId, "tag2"), expected);
    assertResult(GET(changeId), expected);
    assertResult(PUT(changeId, "tag2"), expected);
    assertResult(GET(changeId), expected);
    expected = Arrays.asList("tag1", "tag2");
    assertResult(PUT(changeId, "tag2, tag1"), expected);
    assertResult(GET(changeId), expected);
  }

  @Test
  public void testDeleteSingleHashtag() throws Exception {
    // DELETE a single tag from a change that only has that tag
    // returns empty list
    String changeId = createChange().getChangeId();
    List<String> expected = Arrays.asList("tag1");
    assertResult(PUT(changeId, "tag1"), expected);
    assertResult(DELETE(changeId, "tag1"), ImmutableList.<String>of());
    assertResult(GET(changeId), ImmutableList.<String>of());

    // DELETE a single tag from a change that has multiple tags
    // returns sorted list of remaining tags
    expected = Arrays.asList("tag1", "tag2", "tag3");
    assertResult(PUT(changeId, "tag1, tag2, tag3"), expected);
    expected = Arrays.asList("tag1, tag3");
    assertResult(DELETE(changeId, "tag2"), expected);
    assertResult(GET(changeId), expected);
  }

  @Test
  public void testDeleteMultipleHashtags() throws Exception {
    // DELETE multiple tags from a change that only has those tags
    // returns empty list
    String changeId = createChange().getChangeId();
    List<String> expected = Arrays.asList("tag1, tag2");
    assertResult(PUT(changeId, "tag1, tag2"), expected);
    assertResult(DELETE(changeId, "tag1, tag2"), ImmutableList.<String>of());
    assertResult(GET(changeId), expected);

    // DELETE multiple tags from a change that has multiple changes
    // returns sorted list of remaining changes
    expected = Arrays.asList("tag1, tag2, tag3, tag4");
    assertResult(PUT(changeId, "tag1, tag2, tag3, tag4"), expected);
    expected = Arrays.asList("tag2, tag4");
    assertResult(DELETE(changeId, "tag1, tag2"), expected);
    assertResult(GET(changeId), expected);
  }

  @Test
  public void testDeleteNotExistingHashtag() throws Exception {
    // DELETE a single hashtag from change that has no hashtags
    // returns empty list
    String changeId = createChange().getChangeId();
    assertResult(DELETE(changeId, "tag1"), ImmutableList.<String>of());
    assertResult(GET(changeId), ImmutableList.<String>of());

    // DELETE a single non-existing tag from a change that only
    // has one other tag returns a list of only one tag
    List<String> expected = Arrays.asList("tag1");
    assertResult(PUT(changeId, "tag1"), expected);
    assertResult(DELETE(changeId, "tag4"), expected);
    assertResult(GET(changeId), expected);

    // DELETE a single non-existing tag from a change that has multiple
    // tags returns sorted list of tags without any deleted
    expected = Arrays.asList("tag1", "tag2", "tag3");
    assertResult(PUT(changeId, "tag1, tag2, tag3"), expected);
    assertResult(DELETE(changeId, "tag4"), expected);
    assertResult(GET(changeId), expected);
  }

  private RestResponse GET(String changeId) throws IOException {
    return adminSession.get("/changes/" + changeId + "/hashtags/");
  }

  private RestResponse PUT(String changeId, String hashtags)
      throws IOException {
    return adminSession.put("/changes/" + changeId + "/hashtags/", hashtags);
  }

  private RestResponse DELETE(String changeId, String hashtags)
      throws IOException {
    return adminSession.delete("/changes/" + changeId + "/hashtags/"
      + Url.encode(hashtags));
  }

  private static List<String> toHashtagList(RestResponse r)
      throws IOException {
    List<String> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<List<String>>() {}.getType());
    return result;
  }
}
