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
  @GerritConfig(name = "rest.repositoryProjectCompatibility", value = "PROJECT_AND_REPOSITORY")
  public void get_projectAndRepository_containsRepository() throws Exception {
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
  public void get_projectOnly_doesNotContainRepository() throws Exception {
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
  @GerritConfig(name = "rest.repositoryProjectCompatibility", value = "REPOSITORY_ONLY")
  public void get_repositoryOnly_containsRepository() throws Exception {
    // Get a ChangeInfo which has a project field. If compatibility is enabled, there should also be
    // a repository field.
    String changeId = createChange().getChangeId();

    RestResponse r = adminRestSession.get("/changes/" + changeId + "/detail");
    r.assertOK();

    JsonObject json = newGson().fromJson(r.getReader(), JsonElement.class).getAsJsonObject();
    assertThat(json.has("project")).isFalse();
    assertThat(json.has("repository")).isTrue();
    assertThat(json.get("repository").getAsString()).isEqualTo(project.get());
  }

  @Test
  public void get_withQueryParameter_acceptsRepository() throws Exception {
    // Args4J doesn't support conditional aliases, so repository is accepted regardless of the
    // setting.
    RestResponse r = adminRestSession.get("/access/?repository=" + project.get());
    r.assertOK();
  }

  @Test
  public void get_withQueryParameter_acceptsProject() throws Exception {
    RestResponse r = adminRestSession.get("/access/?project=" + project.get());
    r.assertOK();
  }

  @Test
  @GerritConfig(name = "rest.repositoryProjectCompatibility", value = "PROJECT_AND_REPOSITORY")
  public void post_projectAndRepository_acceptsRepository() throws Exception {
    RestResponse r =
        adminRestSession.post(
            "/changes/", new NewStyleChangeInput(project.get(), "master", "Test change"));
    r.assertCreated();
  }

  @Test
  @GerritConfig(name = "rest.repositoryProjectCompatibility", value = "PROJECT_AND_REPOSITORY")
  public void post_projectAndRepository_acceptsProject() throws Exception {
    RestResponse r =
        adminRestSession.post("/changes/", new ChangeInput(project.get(), "master", "Test change"));
    r.assertCreated();
  }

  @Test
  @GerritConfig(name = "rest.repositoryProjectCompatibility", value = "REPOSITORY_ONLY")
  public void post_repositoryOnly_acceptsRepository() throws Exception {
    RestResponse r =
        adminRestSession.post(
            "/changes/", new NewStyleChangeInput(project.get(), "master", "Test change"));
    r.assertCreated();
  }

  @Test
  @GerritConfig(name = "rest.repositoryProjectCompatibility", value = "REPOSITORY_ONLY")
  public void post_repositoryOnly_rejectsProject() throws Exception {
    RestResponse r =
        adminRestSession.post(
            "/changes/", new ChangeInput(project.get(), "master", "Test change"));
    r.assertNotFound();
  }

  @Test
  public void post_projectOnly_rejectsRepository() throws Exception {
    RestResponse r =
        adminRestSession.post(
            "/changes/", new NewStyleChangeInput(project.get(), "master", "Test change"));
    r.assertBadRequest();
  }

  @Test
  public void post_projectOnly_acceptsProject() throws Exception {
    RestResponse r =
        adminRestSession.post("/changes/", new ChangeInput(project.get(), "master", "Test change"));
    r.assertCreated();
  }

  @Test
  @GerritConfig(name = "rest.repositoryProjectCompatibility", value = "PROJECT_AND_REPOSITORY")
  public void getOnRepositoriesRootCollection_projectAndRepository_succeeds() throws Exception {
    RestResponse r = adminRestSession.get("/repositories/" + project.get());
    r.assertOK();
  }

  @Test
  @GerritConfig(name = "rest.repositoryProjectCompatibility", value = "PROJECT_AND_REPOSITORY")
  public void getOnProjectsRootCollection_projectAndRepository_succeeds() throws Exception {
    RestResponse r = adminRestSession.get("/projects/" + project.get());
    r.assertOK();
  }

  @Test
  public void getOnRepositoriesRootCollection_projectOnly_succeeds() throws Exception {
    RestResponse r = adminRestSession.get("/repositories/" + project.get());
    r.assertNotFound();
  }

  @Test
  public void getOnProjectsRootCollection_projectOnly_succeeds() throws Exception {
    RestResponse r = adminRestSession.get("/projects/" + project.get());
    r.assertOK();
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
