// Copyright (C) 2025 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,//
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.gerrit.server.restapi.change;

import static com.google.common.truth.Truth.assertThat;
import static com.google.gerrit.testing.GerritJUnit.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.SitePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import com.google.gerrit.server.git.GitRepositoryManager;
import org.junit.Test;

public class GetAiPromptTest {

  private static class FakeGetPatch extends GetPatch {
    FakeGetPatch(GitRepositoryManager repoManager) {
      super(repoManager);
    }

    @Override
    public Response<BinaryResult> apply(RevisionResource rsrc) {
      return Response.ok(BinaryResult.create("FAKE_PATCH_CONTENT"));
    }
  }

  @Test
  public void returnsPatchContent_whenCommandIsPatchContent() throws Exception {
    Path siteDir = Files.createTempDirectory("gerrit_site");
    SitePaths sitePaths = new SitePaths(siteDir);
    GetAiPrompt getAiPrompt = new GetAiPrompt(sitePaths, new FakeGetPatch(mock(GitRepositoryManager.class)));
    getAiPrompt.command = "patch_content";

    String response = getAiPrompt.apply(mock(RevisionResource.class)).value();

    assertThat(response).isNotNull();
    assertThat(response.contains("FAKE_PATCH_CONTENT")).isTrue();
  }


  @Test
  public void returnsCodeReviewPromptTemplate_whenCommandIsCodeReview() throws Exception {
    Path siteDir = createPromptFile(
        "code_review_prompt_template.txt", "This is the code review template with {{Patch}}");
    SitePaths sitePaths = new SitePaths(siteDir);

    GetAiPrompt getAiPrompt = new GetAiPrompt(sitePaths, new FakeGetPatch(mock(GitRepositoryManager.class)));
    getAiPrompt.command = "code_review";

    String response = getAiPrompt.apply(mock(RevisionResource.class)).value();

    assertThat(response).contains("FAKE_PATCH_CONTENT");
    assertThat(response).doesNotContain("{{Patch}}");
  }

  @Test
  public void returnsCommitMessagePromptTemplate_whenCommandIsCommitMessage() throws Exception {
    Path siteDir = createPromptFile(
        "commit_message_prompt_template.txt", "This is the commit message template with {{Patch}}");
    SitePaths sitePaths = new SitePaths(siteDir);
    GetAiPrompt getAiPrompt = new GetAiPrompt(sitePaths, new FakeGetPatch(mock(GitRepositoryManager.class)));
    getAiPrompt.command = "commit_message";

    String response = getAiPrompt.apply(mock(RevisionResource.class)).value();

    assertThat(response).contains("FAKE_PATCH_CONTENT");
    assertThat(response).doesNotContain("{{Patch}}");
  }

  @Test
  public void returnsBadRequest_whenCommandMissing() throws Exception {
    Path siteDir = Files.createTempDirectory("gerrit_site");
    SitePaths sitePaths = new SitePaths(siteDir);

    GetAiPrompt getAiPrompt = new GetAiPrompt(sitePaths, new FakeGetPatch(mock(GitRepositoryManager.class)));

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class,
            () -> getAiPrompt.apply(mock(RevisionResource.class)));

    assertThat(thrown).hasMessageThat().contains("Missing required parameter: command");
  }

  @Test
  public void returnsBadRequest_whenCommandInvalid() throws Exception {
    Path siteDir = Files.createTempDirectory("gerrit_site");
    SitePaths sitePaths = new SitePaths(siteDir);
    GetAiPrompt getAiPrompt = new GetAiPrompt(sitePaths, new FakeGetPatch(mock(GitRepositoryManager.class)));
    getAiPrompt.command = "invalid_command";

    String response = getAiPrompt.apply(mock(RevisionResource.class)).value();
    assertThat(response).contains("Invalid command value. Valid values are:");
  }

  private static Path createPromptFile(String file, String content) throws IOException {
    Path siteDir = Files.createTempDirectory("gerrit_site");
    Path etcDir = siteDir.resolve("etc");
    Files.createDirectories(etcDir);

    Files.writeString(etcDir.resolve(file), content);
    return siteDir;
  }
}
