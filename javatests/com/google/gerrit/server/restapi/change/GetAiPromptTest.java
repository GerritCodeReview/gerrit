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
import static org.mockito.Mockito.when;

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.SitePaths;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GetAiPromptTest {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();
  Path etcDir;
  Path siteDir;
  SitePaths sitePaths;
  GetPatch getPatch;

  @Before
  public void setUp() throws Exception {
    siteDir = tempFolder.newFolder("gerrit_site").toPath();
    etcDir = tempFolder.newFolder("gerrit_site", "etc").toPath();
    sitePaths = new SitePaths(siteDir);

    getPatch = mock(GetPatch.class);
    when(getPatch.apply(any())).thenReturn(Response.ok(BinaryResult.create("FAKE_PATCH_CONTENT")));
  }

  @Test
  public void returnsPatchContentWhenCommandIsPatchContent() throws Exception {
    GetAiPrompt getAiPrompt = new GetAiPrompt(sitePaths, getPatch);
    getAiPrompt.command = "patch_content";

    String response = getAiPrompt.apply(mock(RevisionResource.class)).value();

    assertThat(response).isNotNull();
    assertThat(response.contains("FAKE_PATCH_CONTENT")).isTrue();
  }

  @Test
  public void returnsCodeReviewPromptTemplateWhenCommandIsCodeReview() throws Exception {
    createPromptFile(
        "code_review_prompt_template.txt", "This is the code review template with {{Patch}}");

    GetAiPrompt getAiPrompt = new GetAiPrompt(sitePaths, getPatch);
    getAiPrompt.command = "code_review";

    String response = getAiPrompt.apply(mock(RevisionResource.class)).value();

    assertThat(response).contains("FAKE_PATCH_CONTENT");
    assertThat(response).doesNotContain("{{Patch}}");
  }

  @Test
  public void returnsCommitMessagePromptTemplateWhenCommandIsCommitMessage() throws Exception {
    createPromptFile(
        "commit_message_prompt_template.txt", "This is the commit message template with {{Patch}}");

    GetAiPrompt getAiPrompt = new GetAiPrompt(sitePaths, getPatch);
    getAiPrompt.command = "commit_message";

    String response = getAiPrompt.apply(mock(RevisionResource.class)).value();

    assertThat(response).contains("FAKE_PATCH_CONTENT");
    assertThat(response).doesNotContain("{{Patch}}");
  }

  @Test
  public void returnsBadRequestWhenCommandMissing() throws Exception {
    GetAiPrompt getAiPrompt = new GetAiPrompt(sitePaths, getPatch);

    BadRequestException thrown =
        assertThrows(
            BadRequestException.class, () -> getAiPrompt.apply(mock(RevisionResource.class)));

    assertThat(thrown).hasMessageThat().contains("Missing required parameter: command");
  }

  @Test
  public void returnsBadRequestWhenCommandInvalid() throws Exception {
    GetAiPrompt getAiPrompt = new GetAiPrompt(sitePaths, getPatch);
    getAiPrompt.command = "invalid_command";

    String response = getAiPrompt.apply(mock(RevisionResource.class)).value();
    assertThat(response).contains("Invalid command value. Valid values are:");
  }

  private void createPromptFile(String file, String content) throws IOException {
    Files.writeString(etcDir.resolve(file), content);
  }
}
