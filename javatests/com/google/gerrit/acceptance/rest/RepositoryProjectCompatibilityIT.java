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

import static com.google.common.truth.Truth.assertThat;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import org.junit.Test;

public class RepositoryProjectCompatibilityIT extends AbstractDaemonTest {
  @Test
  @GerritConfig(name = "rest.enableRepositoryProjectCompatibility", value = "true")
  public void get_compatibility_containsRepository() throws Exception {
    // Get a ChangeInfo which has a project field. If compatibility is enabled, there should also be
    // a repository field.
    String changeId = createChange().getChangeId();

    RestResponse r = adminRestSession.get("/changes/" + changeId + "/detail");
    r.assertOK();

    JsonObject json = newGson().fromJson(r.getReader(), JsonElement.class).getAsJsonObject();
    assertThat(json.has("project")).isTrue();
    assertThat(json.get("project").getAsString()).isEqualTo(project.get());
    assertThat(json.has("repository")).isTrue();
    assertThat(json.get("repository").getAsString()).isEqualTo(project.get());
  }

  @Test
  public void get_noCompatibility_doesNotContainRepository() throws Exception {
    // Get a ChangeInfo which has a project field. If compatibility is enabled, there should also be
    // a repository field.
    String changeId = createChange().getChangeId();

    RestResponse r = adminRestSession.get("/changes/" + changeId + "/detail");
    r.assertOK();

    JsonObject json = newGson().fromJson(r.getReader(), JsonElement.class).getAsJsonObject();
    assertThat(json.has("project")).isTrue();
    assertThat(json.get("project").getAsString()).isEqualTo(project.get());
    assertThat(json.has("repository")).isFalse();
  }

  @Test
  @GerritConfig(name = "rest.enableRepositoryProjectCompatibility", value = "true")
  public void post_compatibility_acceptsRepository() throws Exception {
    RestResponse r =
        adminRestSession.post(
            "/changes/", new NewStyleChangeInput(project.get(), "master", "Test change"));
    r.assertCreated();
  }

  @Test
  @GerritConfig(name = "rest.enableRepositoryProjectCompatibility", value = "true")
  public void post_compatibility_acceptsProject() throws Exception {
    RestResponse r =
        adminRestSession.post("/changes/", new ChangeInput(project.get(), "master", "Test change"));
    r.assertCreated();
  }

  @Test
  public void post_noCompatibility_rejectsRepository() throws Exception {
    RestResponse r =
        adminRestSession.post(
            "/changes/", new NewStyleChangeInput(project.get(), "master", "Test change"));
    r.assertBadRequest();
  }

  @Test
  public void post_noCompatibility_acceptsProject() throws Exception {
    RestResponse r =
        adminRestSession.post("/changes/", new ChangeInput(project.get(), "master", "Test change"));
    r.assertCreated();
  }

  /** Helper to serialize a JSON entity with 'repository' instead of 'project' */
  private static class NewStyleChangeInput {
    @SuppressWarnings("unused")
    private String repository;

    @SuppressWarnings("unused")
    private String branch;

    @SuppressWarnings("unused")
    private String subject;

    private NewStyleChangeInput(String repository, String branch, String subject) {
      this.repository = repository;
      this.branch = branch;
      this.subject = subject;
    }
  }
}
