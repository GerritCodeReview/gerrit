// Copyright (C) 2013 The Android Open Source Project
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
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.GerritConfigs;
import com.google.gerrit.server.change.SuggestReviewers.SuggestedReviewerInfo;
import com.google.gson.reflect.TypeToken;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

public class SuggestReviewersIT extends AbstractDaemonTest {

  @Before
  public void setUp() throws Exception {
    group("users1");
    group("users2");
    group("users3");

    accounts.create("user1", "user1@example.com", "User1", "users1");
    accounts.create("user2", "user2@example.com", "User2", "users2");
    accounts.create("user3", "user3@example.com", "User3", "users1", "users2");
  }

  @Test
  @GerritConfig(name = "suggest.accounts", value = "false")
  public void suggestReviewersNoResult1() throws GitAPIException, IOException,
      Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId, "u", 6);
    assertEquals(reviewers.size(), 0);
  }

  @Test
  @GerritConfigs(
      {@GerritConfig(name = "suggest.accounts", value = "true"),
       @GerritConfig(name = "suggest.from", value = "1"),
       @GerritConfig(name = "accounts.visibility", value = "NONE")
      })
  public void suggestReviewersNoResult2() throws GitAPIException, IOException,
      Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId, "u", 6);
    assertEquals(reviewers.size(), 0);
  }

  @Test
  @GerritConfig(name = "suggest.from", value = "2")
  public void suggestReviewersNoResult3() throws GitAPIException, IOException,
      Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId, "u", 6);
    assertEquals(reviewers.size(), 0);
  }

  @Test
  public void suggestReviewersChange() throws GitAPIException,
      IOException, Exception {
    String changeId = createChange().getChangeId();
    List<SuggestedReviewerInfo> reviewers = suggestReviewers(changeId, "u", 6);
    assertEquals(reviewers.size(), 6);
    reviewers = suggestReviewers(changeId, "u", 5);
    assertEquals(reviewers.size(), 5);
    reviewers = suggestReviewers(changeId, "users3", 10);
    assertEquals(reviewers.size(), 1);
  }

  private List<SuggestedReviewerInfo> suggestReviewers(String changeId,
      String query, int n)
      throws IOException {
    return newGson().fromJson(
        adminSession.get("/changes/"
            + changeId
            + "/suggest_reviewers?q="
            + query
            + "&n="
            + n)
        .getReader(),
        new TypeToken<List<SuggestedReviewerInfo>>() {}
        .getType());
  }

  private void group(String name) throws IOException {
    adminSession.put("/groups/" + name, new Object()).consume();
  }
}
