// Copyright (C) 2026 The Android Open Source Project
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
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestExtensions.TestCommitValidationListener;
import com.google.gerrit.extensions.api.projects.CommitFilesInput;
import com.google.gerrit.extensions.api.projects.CommitFilesInput.FileChange;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.inject.Inject;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import org.junit.Before;
import org.junit.Test;

public class PutContentIT extends AbstractDaemonTest {
  private static final String BRANCH = "master";

  @Inject private ExtensionRegistry extensionRegistry;

  @Before
  public void setUp() throws Exception {
    // Seed master with a submitted file (PushOneCommit.FILE_NAME / FILE_CONTENT).
    PushOneCommit.Result change = createChange();
    approve(change.getChangeId());
    revision(change).submit();
  }

  @Test
  public void directCreateNewFile() throws Exception {
    RestResponse r =
        adminRestSession.put(contentUrl(), write("Add new file", "new/file.txt", "hello"));
    r.assertOK();
    CommitInfo commit = newGson().fromJson(r.getReader(), CommitInfo.class);
    assertThat(commit.commit).isNotNull();
    assertThat(readFile("new/file.txt")).isEqualTo("hello");
  }

  @Test
  public void directUpdateExistingFile() throws Exception {
    RestResponse r =
        adminRestSession.put(
            contentUrl(), write("Update", PushOneCommit.FILE_NAME, "updated body"));
    r.assertOK();
    assertThat(readFile(PushOneCommit.FILE_NAME)).isEqualTo("updated body");
  }

  @Test
  public void directMultiFileWriteAndDelete() throws Exception {
    // First add a file we will later delete, plus the existing seeded file.
    adminRestSession.put(contentUrl(), write("Add doomed", "doomed.txt", "bye")).assertOK();

    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Write one, delete one";
    input.files = new HashMap<>();
    input.files.put("kept.txt", contentChange("kept"));
    input.files.put("doomed.txt", deleteChange());
    RestResponse r = adminRestSession.put(contentUrl(), input);
    r.assertOK();

    assertThat(readFile("kept.txt")).isEqualTo("kept");
    RestResponse doomed =
        adminRestSession.get(
            String.format(
                "/projects/%s/branches/%s/files/doomed.txt/content", project.get(), BRANCH));
    doomed.assertNotFound();
  }

  @Test
  public void directRenameFile() throws Exception {
    // Seed a file, then rename it in a single commit.
    adminRestSession.put(contentUrl(), write("Add original", "original.txt", "body")).assertOK();

    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Rename original.txt to renamed.txt";
    input.files = new HashMap<>();
    input.files.put("renamed.txt", renameChange("original.txt"));
    adminRestSession.put(contentUrl(), input).assertOK();

    assertThat(readFile("renamed.txt")).isEqualTo("body");
    RestResponse original =
        adminRestSession.get(
            String.format(
                "/projects/%s/branches/%s/files/original.txt/content", project.get(), BRANCH));
    original.assertNotFound();
  }

  @Test
  public void directWithMatchingBaseRevisionSucceeds() throws Exception {
    CommitFilesInput input = write("Guarded", "guarded.txt", "ok");
    input.baseRevision = branchTip();
    adminRestSession.put(contentUrl(), input).assertOK();
  }

  @Test
  public void directWithStaleBaseRevisionIsRejected() throws Exception {
    String stale = branchTip();
    // Advance the branch so `stale` is no longer the tip.
    adminRestSession.put(contentUrl(), write("Advance", "advance.txt", "x")).assertOK();

    CommitFilesInput input = write("Stale", "stale.txt", "y");
    input.baseRevision = stale;
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(409);
  }

  @Test
  public void directNoOpIsRejected() throws Exception {
    // Writing the existing content unchanged produces no tree change.
    RestResponse r =
        adminRestSession.put(
            contentUrl(), write("No-op", PushOneCommit.FILE_NAME, PushOneCommit.FILE_CONTENT));
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directWithoutPushPermissionIsForbidden() throws Exception {
    RestResponse r = userRestSession.put(contentUrl(), write("Nope", "x.txt", "x"));
    assertThat(r.getStatusCode()).isEqualTo(403);
  }

  @Test
  public void directWithNonexistentBaseRevisionIsRejected() throws Exception {
    CommitFilesInput input = write("Bad base", "bad.txt", "y");
    input.baseRevision = "0123456789012345678901234567890123456789";
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(409);
  }

  @Test
  public void reviewCreatesChangeWithoutMovingBranch() throws Exception {
    String before = branchTip();
    RestResponse r = adminRestSession.put(reviewUrl(), write("For review", "review.txt", "please"));
    assertThat(r.getStatusCode()).isEqualTo(201);
    ChangeInfo info = newGson().fromJson(r.getReader(), ChangeInfo.class);
    assertThat(info.changeId).isNotNull();
    assertThat(branchTip()).isEqualTo(before);
  }

  @Test
  public void reviewWithNonexistentBaseRevisionIsRejected() throws Exception {
    CommitFilesInput input = write("Bad base", "bad.txt", "y");
    input.baseRevision = "0123456789012345678901234567890123456789";
    RestResponse r = adminRestSession.put(reviewUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(409);
  }

  @Test
  public void reviewWithMalformedBaseRevisionIsRejected() throws Exception {
    CommitFilesInput input = write("Bad base", "bad.txt", "y");
    input.baseRevision = "deadbeef";
    RestResponse r = adminRestSession.put(reviewUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directCommitInvokesCommitValidators() throws Exception {
    // A registered plugin commit validator must see the direct commit, so the endpoint
    // cannot be used to bypass commit-validation policy.
    TestCommitValidationListener listener = new TestCommitValidationListener();
    try (Registration unused = extensionRegistry.newRegistration().add(listener)) {
      adminRestSession.put(contentUrl(), write("Validated", "validated.txt", "x")).assertOK();
      assertThat(listener.receiveEvent).isNotNull();
      assertThat(listener.receiveEvent.refName).isEqualTo("refs/heads/" + BRANCH);
    }
  }

  @Test
  public void directCommitRejectedByCommitValidator() throws Exception {
    CommitValidationListener rejecting =
        new CommitValidationListener() {
          @Override
          public List<CommitValidationMessage> onCommitReceived(CommitReceivedEvent receiveEvent)
              throws CommitValidationException {
            throw new CommitValidationException("blocked by test validator");
          }
        };
    try (Registration unused = extensionRegistry.newRegistration().add(rejecting)) {
      RestResponse r = adminRestSession.put(contentUrl(), write("Nope", "blocked.txt", "x"));
      assertThat(r.getStatusCode()).isEqualTo(409);
    }
  }

  private String contentUrl() {
    return String.format("/projects/%s/branches/%s/content", project.get(), BRANCH);
  }

  private String reviewUrl() {
    return contentUrl() + ":review";
  }

  private static FileChange contentChange(String content) {
    FileChange fc = new FileChange();
    fc.content = Base64.getEncoder().encodeToString(content.getBytes(UTF_8));
    return fc;
  }

  private static FileChange deleteChange() {
    FileChange fc = new FileChange();
    fc.delete = true;
    return fc;
  }

  private static FileChange renameChange(String from) {
    FileChange fc = new FileChange();
    fc.renameFrom = from;
    return fc;
  }

  private static CommitFilesInput write(String message, String path, String content) {
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = message;
    input.files = new HashMap<>();
    input.files.put(path, contentChange(content));
    return input;
  }

  private String readFile(String path) throws Exception {
    return gApi.projects().name(project.get()).branch(BRANCH).file(path).asString();
  }

  private String branchTip() throws Exception {
    return gApi.projects().name(project.get()).branch(BRANCH).get().revision;
  }
}
