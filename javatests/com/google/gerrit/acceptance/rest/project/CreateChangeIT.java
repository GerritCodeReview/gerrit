// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.acceptance.rest.project;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static com.google.gerrit.entities.RefNames.REFS_HEADS;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.restapi.IdString;
import org.junit.Test;

public class CreateChangeIT extends AbstractDaemonTest {

  /**
   * Just a basic test. The real functionality is tested by {@link
   * com.google.gerrit.acceptance.rest.change.CreateChangeIT}.
   */
  @Test
  public void basic() throws Exception {
    BranchInput branchInput = new BranchInput();
    branchInput.ref = "foo";
    assertThat(gApi.projects().name(project.get()).branches().get().stream().map(i -> i.ref))
        .doesNotContain(REFS_HEADS + branchInput.ref);
    RestResponse r =
        adminRestSession.put(
            "/projects/" + project.get() + "/branches/" + branchInput.ref, branchInput);
    r.assertCreated();

    ChangeInput input = new ChangeInput();
    input.branch = "foo";
    input.subject = "subject";
    RestResponse response =
        adminRestSession.post("/projects/" + project.get() + "/create.change", input);
    response.assertCreated();
  }

  @Test
  public void nonMatchingProjectIsRejected() throws Exception {
    ChangeInput input = new ChangeInput();
    input.project = "non-matching-project";
    input.branch = "master";
    input.subject = "subject";
    RestResponse response =
        adminRestSession.post("/projects/" + project.get() + "/create.change", input);
    response.assertBadRequest();
    assertThat(response.getEntityContent()).isEqualTo("project must match URL");
  }

  @Test
  public void matchingProjectIsAccepted() throws Exception {
    ChangeInput input = new ChangeInput();
    input.project = project.get();
    input.branch = "master";
    input.subject = "subject";
    RestResponse response =
        adminRestSession.post("/projects/" + project.get() + "/create.change", input);
    response.assertCreated();
  }

  @Test
  public void matchingProjectWithTrailingSlashIsAccepted() throws Exception {
    ChangeInput input = new ChangeInput();
    input.project = project.get() + "/";
    input.branch = "master";
    input.subject = "subject";
    RestResponse response =
        adminRestSession.post(
            "/projects/" + IdString.fromDecoded(project.get() + "/").encoded() + "/create.change",
            input);
    response.assertCreated();
  }
}
