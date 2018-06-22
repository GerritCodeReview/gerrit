// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest;

import static com.google.common.truth.TruthJUnit.assume;
import static com.google.gerrit.acceptance.rest.AbstractRestApiBindingsTest.Method.GET;
import static org.apache.http.HttpStatus.SC_NOT_FOUND;

import com.google.common.collect.ImmutableList;
import org.junit.Test;

/**
 * Tests for checking the bindings of the changes REST API.
 *
 * <p>These tests only verify that the change REST endpoints are correctly bound, they do no test
 * the functionality of the change REST endpoints (for details see JavaDoc on {@link
 * AbstractRestApiBindingsTest}).
 */
public class ChangesRestApiBindingsIT extends AbstractRestApiBindingsTest {
  /**
   * Change REST endpoints to be tested, each URL contains a placeholder for the change identifier.
   */
  private static final ImmutableList<RestCall> CHANGE_ENDPOINTS =
      ImmutableList.of(
          RestCall.get("/changes/%s"),
          RestCall.get("/changes/%s/detail"),
          RestCall.get("/changes/%s/topic"),
          RestCall.put("/changes/%s/topic"),
          RestCall.delete("/changes/%s/topic"),
          RestCall.get("/changes/%s/in"),
          RestCall.get("/changes/%s/hashtags"),
          RestCall.post("/changes/%s/hashtags"),
          RestCall.get("/changes/%s/comments"),
          RestCall.get("/changes/%s/robotcomments"),
          RestCall.get("/changes/%s/drafts"),
          RestCall.get("/changes/%s/assignee"),
          RestCall.get("/changes/%s/past_assignees"),
          RestCall.put("/changes/%s/assignee"),
          RestCall.delete("/changes/%s/assignee"),
          RestCall.post("/changes/%s/private"),
          RestCall.post("/changes/%s/private.delete"),
          RestCall.delete("/changes/%s/private"),
          RestCall.post("/changes/%s/wip"),
          RestCall.post("/changes/%s/ready"),
          RestCall.put("/changes/%s/ignore"),
          RestCall.put("/changes/%s/unignore"),
          RestCall.put("/changes/%s/reviewed"),
          RestCall.put("/changes/%s/unreviewed"),
          RestCall.get("/changes/%s/messages"),
          RestCall.put("/changes/%s/message"),
          RestCall.post("/changes/%s/merge"),
          RestCall.post("/changes/%s/abandon"),
          RestCall.post("/changes/%s/move"),
          RestCall.post("/changes/%s/rebase"),
          RestCall.post("/changes/%s/restore"),
          RestCall.post("/changes/%s/revert"),
          RestCall.get("/changes/%s/pure_revert"),
          RestCall.post("/changes/%s/submit"),
          RestCall.get("/changes/%s/submitted_together"),
          RestCall.post("/changes/%s/index"),
          RestCall.get("/changes/%s/check"),
          RestCall.post("/changes/%s/check"),
          RestCall.get("/changes/%s/reviewers"),
          RestCall.post("/changes/%s/reviewers"),
          RestCall.get("/changes/%s/suggest_reviewers"),
          RestCall.builder(GET, "/changes/%s/revisions")
              // GET /changes/<change-id>/revisions is not implemented
              .expectedResponseCode(SC_NOT_FOUND)
              .build(),
          RestCall.get("/changes/%s/edit"),
          RestCall.post("/changes/%s/edit:rebase"),
          RestCall.get("/changes/%s/edit:message"),
          RestCall.put("/changes/%s/edit:message"),

          // Publish edit and create a new edit
          RestCall.post("/changes/%s/edit:publish"),
          RestCall.put("/changes/%s/edit/a.txt"),

          // Deletion of change edit and change must be tested last
          RestCall.delete("/changes/%s/edit"),
          RestCall.delete("/changes/%s"));

  /**
   * Change REST endpoints to be tested with NoteDb, each URL contains a placeholder for the change
   * identifier.
   */
  private static final ImmutableList<RestCall> CHANGE_ENDPOINTS_NOTEDB =
      ImmutableList.of(RestCall.post("/changes/%s/rebuild.notedb"));

  // TODO(ekempin): Add tests for REST endpoints of changes child collections

  @Test
  public void changeEndpoints() throws Exception {
    String changeId = createChange().getChangeId();
    gApi.changes().id(changeId).edit().create();
    execute(CHANGE_ENDPOINTS, changeId);
  }

  @Test
  public void changeEndpointsNoteDb() throws Exception {
    assume().that(notesMigration.readChanges()).isTrue();

    String changeId = createChange().getChangeId();
    execute(CHANGE_ENDPOINTS_NOTEDB, changeId);
  }
}
