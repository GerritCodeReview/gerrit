package com.google.gerrit.acceptance.rest.change;

import static com.google.common.truth.Truth.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.entities.Change;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.restapi.change.GetPatch;
import com.google.gerrit.server.restapi.change.GetPrompt;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public class GetPromptIT extends AbstractDaemonTest {

  @Test
  public void returnsPatchContent_whenCommandIsPatchContent() throws Exception {
    Change.Id changeId = createChange().getChange().getId();

    RestResponse r =
        adminRestSession.get(
            "/changes/" + changeId + "/revisions/current/ai_prompt?command=patch_content");
    r.assertOK();

    String text = r.getEntityContent();
    assertThat(text).isNotNull();
    assertThat(text).startsWith(")]}'\n" + "\"From ");
  }

  @Test
  public void returnsCodeReviewPromptTemplate_whenCommandIsCodeReview() throws Exception {
    Path siteDir =
        createPromptFile(
            "code_review_prompt_template.txt", "This is the code review template with {{Patch}}");
    SitePaths sitePaths = new SitePaths(siteDir);
    GetPatch getPatch = fetchPatchContent();
    GetPrompt getPrompt = new GetPrompt(sitePaths, getPatch);
    getPrompt.command = "code_review";

    String response = getPrompt.apply(mock(RevisionResource.class)).value();

    assertThat(response).contains("FAKE_PATCH_CONTENT");
    assertThat(response).doesNotContain("{{Patch}}");
  }

  @Test
  public void returnsCommitMessagePromptTemplate_whenCommandCommitMessage() throws Exception {
    Path siteDir =
        createPromptFile(
            "commit_message_prompt_template.txt",
            "This is the commit message template with {{Patch}}");
    SitePaths sitePaths = new SitePaths(siteDir);
    GetPatch getPatch = fetchPatchContent();
    GetPrompt getPrompt = new GetPrompt(sitePaths, getPatch);
    getPrompt.command = "commit_message";

    String response = getPrompt.apply(mock(RevisionResource.class)).value();

    assertThat(response).contains("FAKE_PATCH_CONTENT");
    assertThat(response).doesNotContain("{{Patch}}");
  }

  private static GetPatch fetchPatchContent()
      throws BadRequestException,
          ResourceConflictException,
          IOException,
          ResourceNotFoundException {
    GetPatch getPatch = mock(GetPatch.class);
    BinaryResult fakePatch = BinaryResult.create("FAKE_PATCH_CONTENT");
    when(getPatch.apply(any())).thenReturn(Response.ok(fakePatch));
    return getPatch;
  }

  private static Path createPromptFile(String file, String csq) throws IOException {
    Path siteDir = Files.createTempDirectory("gerrit_site");
    Path etcDir = siteDir.resolve("etc");
    Files.createDirectories(etcDir);

    Path templateFile = etcDir.resolve(file);
    Files.writeString(templateFile, csq);
    return siteDir;
  }

  @Test
  public void returnsBadRequest_whenCommandMissing() throws Exception {
    Change.Id changeId = createChange().getChange().getId();

    RestResponse r = adminRestSession.get("/changes/" + changeId + "/revisions/current/ai_prompt");
    r.assertBadRequest();
  }

  @Test
  public void returnsBadRequest_whenCommandInvalid() throws Exception {
    Change.Id changeId = createChange().getChange().getId();

    RestResponse r =
        adminRestSession.get("/changes/" + changeId + "/revisions/current/ai_prompt?command=FOO");
    r.assertBadRequest();
    assertThat(r.getEntityContent()).contains("Invalid command value");
  }
}
