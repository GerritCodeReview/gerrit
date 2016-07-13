// Copyright (C) 2016 The Android Open Source Project
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

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.extensions.api.changes.AddReviewerInput;
import com.google.gerrit.extensions.api.changes.AddReviewerResult;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gson.stream.JsonReader;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class ChangeReviewersIT extends AbstractDaemonTest {
  @Test
  public void addGroupAsReviewer() throws Exception {
    // Set up two groups, one that is too large too add as reviewer, and one
    // that is too large to add without confirmation.
    String largeGroup = createGroup("largeGroup");
    String mediumGroup = createGroup("mediumGroup");

    int largeGroupSize = PostReviewers.DEFAULT_MAX_REVIEWERS + 1;
    int mediumGroupSize = PostReviewers.DEFAULT_MAX_REVIEWERS_WITHOUT_CHECK + 1;
    List<TestAccount> users = new ArrayList<>(largeGroupSize);
    List<String> largeGroupUsernames = new ArrayList<>(mediumGroupSize);
    List<String> mediumGroupUsernames = new ArrayList<>(mediumGroupSize);
    for (int i = 0; i < largeGroupSize; i++) {
      users.add(accounts.create(name("u" + i), name("u" + i + "@example.com"),
          "Full Name " + i));
      largeGroupUsernames.add(users.get(i).username);
      if (i < mediumGroupSize) {
        mediumGroupUsernames.add(users.get(i).username);
      }
    }
    gApi.groups().id(largeGroup).addMembers(
        largeGroupUsernames.toArray(new String[largeGroupSize]));
    gApi.groups().id(mediumGroup).addMembers(
        mediumGroupUsernames.toArray(new String[mediumGroupSize]));

    // Attempt to add overly large group as reviewers.
    PushOneCommit.Result r = createChange();
    String changeId = r.getChangeId();
    AddReviewerResult result = addReviewer(changeId, largeGroup);
    assertThat(result.input).isEqualTo(largeGroup);
    assertThat(result.confirm).isNull();
    assertThat(result.error)
        .contains("has too many members to add them all as reviewers");
    assertThat(result.reviewers).isNull();

    // Attempt to add medium group without confirmation.
    result = addReviewer(changeId, mediumGroup);
    assertThat(result.input).isEqualTo(mediumGroup);
    assertThat(result.confirm).isTrue();
    assertThat(result.error)
        .contains("has " + mediumGroupSize + " members. Do you want to add them"
            + " all as reviewers?");
    assertThat(result.reviewers).isNull();

    // Add medium group with confirmation.
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = mediumGroup;
    in.confirmed = true;
    result = addReviewer(changeId, in);
    assertThat(result.input).isEqualTo(mediumGroup);
    assertThat(result.confirm).isNull();
    assertThat(result.error).isNull();
    assertThat(result.reviewers).hasSize(mediumGroupSize);
  }

  private AddReviewerResult addReviewer(String changeId, String reviewer)
      throws Exception {
    AddReviewerInput in = new AddReviewerInput();
    in.reviewer = reviewer;
    return addReviewer(changeId, in);
  }

  private AddReviewerResult addReviewer(String changeId, AddReviewerInput in)
      throws Exception {
    RestResponse resp =
        adminRestSession.post("/changes/" + changeId + "/reviewers", in);
    return readContentFromJson(resp, AddReviewerResult.class);
  }

  private static <T> T readContentFromJson(RestResponse r, Class<T> clazz)
      throws Exception {
    r.assertOK();
    JsonReader jsonReader = new JsonReader(r.getReader());
    jsonReader.setLenient(true);
    return newGson().fromJson(jsonReader, clazz);
  }
}