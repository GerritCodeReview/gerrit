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

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.ExtensionRegistry;
import com.google.gerrit.acceptance.ExtensionRegistry.Registration;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.TestExtensions.TestCommitValidationListener;
import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.extensions.api.projects.CommitFilesInput;
import com.google.gerrit.extensions.api.projects.CommitFilesInput.FileChange;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.common.CommitInfo;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.events.RefReceivedEvent;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationListener;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.RefOperationValidationListener;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.validators.ValidationException;
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
  public void directWithMalformedBaseRevisionIsRejected() throws Exception {
    CommitFilesInput input = write("Bad base", "bad.txt", "y");
    input.baseRevision = "deadbeef";
    RestResponse r = adminRestSession.put(contentUrl(), input);
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

  @Test
  public void directCollidingPathsIsRejected() throws Exception {
    // Seed a file, then rename it while also writing the same source path: both touch src.txt.
    adminRestSession.put(contentUrl(), write("Add src", "src.txt", "body")).assertOK();

    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Collide";
    input.files = new HashMap<>();
    input.files.put("dst.txt", renameChange("src.txt"));
    input.files.put("src.txt", contentChange("rewritten"));
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directEmptyRenameFromIsRejected() throws Exception {
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Bad rename";
    input.files = new HashMap<>();
    input.files.put("dst.txt", renameChange(""));
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directRenameToSamePathIsRejected() throws Exception {
    adminRestSession.put(contentUrl(), write("Add self", "self.txt", "body")).assertOK();

    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Rename to self";
    input.files = new HashMap<>();
    input.files.put("self.txt", renameChange("self.txt"));
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directBlankCommitMessageIsRejected() throws Exception {
    RestResponse r = adminRestSession.put(contentUrl(), write("   ", "blankmsg.txt", "x"));
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directMissingCommitMessageIsRejected() throws Exception {
    CommitFilesInput input = new CommitFilesInput();
    input.files = new HashMap<>();
    input.files.put("nomsg.txt", contentChange("x"));
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directNullBodyIsRejected() throws Exception {
    RestResponse r =
        adminRestSession.putRaw(
            contentUrl(), RawInputUtil.create("null".getBytes(UTF_8), "application/json"));
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directUnsupportedFileModeIsRejected() throws Exception {
    // 644 is a well-formed octal value but not one of the git file modes Gerrit supports; it is
    // rejected by the shared downstream validation (Patch.FileMode), not a local list.
    FileChange unsupported = contentChange("x");
    unsupported.fileMode = 644;
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Unsupported mode";
    input.files = new HashMap<>();
    input.files.put("unsupported.txt", unsupported);
    assertThat(adminRestSession.put(contentUrl(), input).getStatusCode()).isEqualTo(400);

    // A value with non-octal digits is rejected at the boundary (400) rather than causing a 500.
    FileChange nonOctal = contentChange("x");
    nonOctal.fileMode = 8;
    CommitFilesInput input2 = new CommitFilesInput();
    input2.commitMessage = "Non-octal mode";
    input2.files = new HashMap<>();
    input2.files.put("nonoctal.txt", nonOctal);
    assertThat(adminRestSession.put(contentUrl(), input2).getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directExecutableFileModeSucceeds() throws Exception {
    FileChange executable = contentChange("#!/bin/sh\n");
    executable.fileMode = 100755;
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Executable";
    input.files = new HashMap<>();
    input.files.put("run.sh", executable);
    adminRestSession.put(contentUrl(), input).assertOK();
    assertThat(readFile("run.sh")).isEqualTo("#!/bin/sh\n");
  }

  @Test
  public void directWriteToRefsMetaConfigIsForbidden() throws Exception {
    String url =
        String.format("/projects/%s/branches/%s/content", project.get(), "refs%2Fmeta%2Fconfig");
    RestResponse r = adminRestSession.put(url, write("Nope", "project.config", "x"));
    assertThat(r.getStatusCode()).isEqualTo(403);
  }

  @Test
  public void directMissingFilesIsRejected() throws Exception {
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "No files";
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directEmptyFilesIsRejected() throws Exception {
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Empty files";
    input.files = new HashMap<>();
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directInvalidBase64ContentIsRejected() throws Exception {
    FileChange fc = new FileChange();
    fc.content = "@@@@"; // not valid base64
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Bad base64";
    input.files = new HashMap<>();
    input.files.put("bad.txt", fc);
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directMultipleOperationsOnEntryIsRejected() throws Exception {
    FileChange fc = contentChange("x");
    fc.delete = true; // content + delete on one entry
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Ambiguous";
    input.files = new HashMap<>();
    input.files.put("ambiguous.txt", fc);
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directEmptyPathIsRejected() throws Exception {
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Empty path";
    input.files = new HashMap<>();
    input.files.put("", contentChange("x"));
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directWriteToReadOnlyProjectIsRejected() throws Exception {
    try (ProjectConfigUpdate u = updateProject(project)) {
      u.getConfig().updateProject(p -> p.setState(ProjectState.READ_ONLY));
      u.save();
    }
    RestResponse r = adminRestSession.put(contentUrl(), write("Read only", "ro.txt", "x"));
    assertThat(r.getStatusCode()).isEqualTo(409);
  }

  @Test
  public void directCommitRejectedByRefOperationValidator() throws Exception {
    TestRefOperationValidationListener listener = new TestRefOperationValidationListener();
    listener.doReject = true;
    try (Registration unused = extensionRegistry.newRegistration().add(listener)) {
      RestResponse r = adminRestSession.put(contentUrl(), write("Nope", "refblocked.txt", "x"));
      assertThat(r.getStatusCode()).isEqualTo(409);
    }
  }

  @Test
  public void commitFilesViaJavaApi() throws Exception {
    CommitFilesInput input = write("Via Java API", "api.txt", "x");
    CommitInfo commit = gApi.projects().name(project.get()).branch(BRANCH).commitFiles(input);
    assertThat(commit.subject).isEqualTo("Via Java API");
    assertThat(branchTip()).isEqualTo(commit.commit);
  }

  @Test
  public void validationOptionsReachCommitValidator() throws Exception {
    TestCommitValidationListener listener = new TestCommitValidationListener();
    try (Registration unused = extensionRegistry.newRegistration().add(listener)) {
      CommitFilesInput input = write("With options", "commitopt.txt", "x");
      input.validationOptions = ImmutableMap.of("key", "value");
      adminRestSession.put(contentUrl(), input).assertOK();
      assertThat(listener.receiveEvent.pushOptions).containsExactly("key", "value");
    }
  }

  @Test
  public void validationOptionsReachRefOperationValidator() throws Exception {
    TestRefOperationValidationListener listener = new TestRefOperationValidationListener();
    try (Registration unused = extensionRegistry.newRegistration().add(listener)) {
      CommitFilesInput input = write("With options", "refopt.txt", "x");
      input.validationOptions = ImmutableMap.of("key", "value");
      adminRestSession.put(contentUrl(), input).assertOK();
      assertThat(listener.refReceivedEvent.pushOptions).containsExactly("key", "value");
    }
  }

  @Test
  public void directDeleteMissingPathInMixedBatchIsRejected() throws Exception {
    // A real write plus a delete of a path that does not exist: the delete would otherwise be
    // silently dropped while the write succeeds.
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Delete missing";
    input.files = new HashMap<>();
    input.files.put("real.txt", contentChange("real"));
    input.files.put("ghost.txt", deleteChange());
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directRenameMissingSourceInMixedBatchIsRejected() throws Exception {
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Rename missing";
    input.files = new HashMap<>();
    input.files.put("kept.txt", contentChange("kept"));
    input.files.put("moved.txt", renameChange("ghost-src.txt"));
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directRenameFromDirectoryIsRejected() throws Exception {
    // A directory source would produce a malformed tree entry (500) if it reached the rename
    // modification; it must be rejected up front with a 400.
    adminRestSession.put(contentUrl(), write("Add nested", "dir/a.txt", "body")).assertOK();
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Rename dir";
    input.files = new HashMap<>();
    input.files.put("moved", renameChange("dir"));
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directDeleteDirectoryIsRejected() throws Exception {
    adminRestSession.put(contentUrl(), write("Add nested", "ddir/a.txt", "body")).assertOK();
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Delete dir";
    input.files = new HashMap<>();
    input.files.put("ddir", deleteChange());
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directFileModeOnDeleteIsRejected() throws Exception {
    FileChange fc = deleteChange();
    fc.fileMode = 100644;
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Delete with mode";
    input.files = new HashMap<>();
    input.files.put(PushOneCommit.FILE_NAME, fc);
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directFileModeOnRenameIsRejected() throws Exception {
    adminRestSession.put(contentUrl(), write("Add rm src", "rm-src.txt", "body")).assertOK();
    FileChange fc = renameChange("rm-src.txt");
    fc.fileMode = 100644;
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Rename with mode";
    input.files = new HashMap<>();
    input.files.put("rm-dst.txt", fc);
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  @Test
  public void directSymlinkFileModeSucceeds() throws Exception {
    FileChange symlink = contentChange("target/path.txt");
    symlink.fileMode = 120000;
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Symlink";
    input.files = new HashMap<>();
    input.files.put("link.txt", symlink);
    adminRestSession.put(contentUrl(), input).assertOK();
    assertThat(readFile("link.txt")).isEqualTo("target/path.txt");
  }

  @Test
  public void directGitlinkFileModeIsRejected() throws Exception {
    FileChange gitlink = contentChange("0123456789012345678901234567890123456789");
    gitlink.fileMode = 160000;
    CommitFilesInput input = new CommitFilesInput();
    input.commitMessage = "Gitlink";
    input.files = new HashMap<>();
    input.files.put("sub", gitlink);
    RestResponse r = adminRestSession.put(contentUrl(), input);
    assertThat(r.getStatusCode()).isEqualTo(400);
  }

  private String contentUrl() {
    return String.format("/projects/%s/branches/%s/content", project.get(), BRANCH);
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

  private static class TestRefOperationValidationListener
      implements RefOperationValidationListener {
    boolean doReject;
    RefReceivedEvent refReceivedEvent;

    @Override
    public List<ValidationMessage> onRefOperation(RefReceivedEvent refReceivedEvent)
        throws ValidationException {
      this.refReceivedEvent = refReceivedEvent;
      if (doReject) {
        throw new ValidationException("rejected by test ref validator");
      }
      return ImmutableList.of();
    }
  }
}
