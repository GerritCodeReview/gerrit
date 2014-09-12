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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gson.reflect.TypeToken;

import org.apache.http.HttpStatus;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class HashtagsIT extends AbstractDaemonTest {
  @Test
  public void testNoHashtagsExist() throws Exception {
    // GET hashtags on a change with no hashtags returns an empty list
    String changeId = createChange().getChangeId();
    RestResponse r = adminSession.get("/changes/" + changeId + "/hashtags/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    List<String> result = toHashtagList(r);
    assertEquals(0, result.size());
  }

  private static List<String> toHashtagList(RestResponse r)
      throws IOException {
    List<String> result =
        newGson().fromJson(r.getReader(),
            new TypeToken<List<String>>() {}.getType());
    return result;
  }

  @Test
  public void testAddSingleHashtag() throws Exception {
    // PUT a single hashtag
  }

  @Test
  public void testAddMultipleHashtags() throws Exception {
    // PUT multiple hashtags
  }

  @Test
  public void testAddAlreadyExistingHashtags() throws Exception {
    // PUT a hashtag that already exists on the change
  }

  @Test
  public void testDeleteSingleHashtag() throws Exception {
    // DELETE a single hashtag
  }

  @Test
  public void testDeleteMultipleHashtags() throws Exception {
    // DELETE multiple hashtags
  }

  @Test
  public void testDeleteNotExistingHashtags() throws Exception {
    // DELETE hashtags that do not exist
  }
}
