// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.common.FileInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.FilePathAdapter;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

/**
 * Lists files that differ between two commits in a project.
 *
 * <p>GET /projects/{project}/diff?old={sha1}&new={sha1}
 *
 * <p>Returns the same format as /changes/{id}/revisions/{rev}/files to ensure identical output.
 */
public class ListDiffFiles implements RestReadView<ProjectResource> {
  private static final int SHA1_LENGTH = 40;

  private final GitRepositoryManager repoManager;
  private final DiffOperations diffOperations;
  private final CommitsCollection commitsCollection;

  @Option(name = "--old", metaVar = "SHA1", usage = "old commit SHA1 (40 characters)")
  private String oldSha;

  @Option(name = "--new", metaVar = "SHA1", usage = "new commit SHA1 (40 characters)")
  private String newSha;

  public ListDiffFiles setOld(String oldSha) {
    this.oldSha = oldSha;
    return this;
  }

  public ListDiffFiles setNew(String newSha) {
    this.newSha = newSha;
    return this;
  }

  @Inject
  ListDiffFiles(
      GitRepositoryManager repoManager,
      DiffOperations diffOperations,
      CommitsCollection commitsCollection) {
    this.repoManager = repoManager;
    this.diffOperations = diffOperations;
    this.commitsCollection = commitsCollection;
  }

  @Override
  public Response<Map<String, FileInfo>> apply(ProjectResource rsrc)
      throws BadRequestException,
          ResourceNotFoundException,
          ResourceConflictException,
          IOException {
    validateSha1(oldSha, "old");
    validateSha1(newSha, "new");

    rsrc.getProjectState().checkStatePermitsRead();
    Project.NameKey project = rsrc.getNameKey();
    ProjectState projectState = rsrc.getProjectState();

    ObjectId oldCommitId = parseObjectId(oldSha, "old");
    ObjectId newCommitId = parseObjectId(newSha, "new");

    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit oldCommit = parseCommit(rw, oldCommitId, "old");
      RevCommit newCommit = parseCommit(rw, newCommitId, "new");

      // Validate that commits are in ancestor/descendant relationship
      validateAncestorRelationship(rw, oldCommit, newCommit);

      // Walk all commits in path and verify visibility for each (critical for private changes)
      verifyPathVisibility(projectState, repo, rw, oldCommit, newCommit);

      // Compute the diff
      // Don't skip files due to rebase - this is a direct commit comparison, not patchset
      // comparison
      DiffOptions diffOptions =
          DiffOptions.builder().skipFilesWithAllEditsDueToRebase(false).build();
      Map<String, FileDiffOutput> fileDiffs =
          diffOperations.listModifiedFiles(project, oldCommitId, newCommitId, diffOptions);

      return Response.ok(asFileInfo(fileDiffs));
    } catch (DiffNotAvailableException e) {
      Throwable cause = e.getCause();
      if (cause != null && !(cause instanceof NoMergeBaseException)) {
        cause = cause.getCause();
      }
      if (cause instanceof NoMergeBaseException) {
        throw new ResourceConflictException(
            String.format("Cannot create auto merge commit: %s", e.getMessage()), e);
      }
      throw new ResourceNotFoundException("Cannot compute diff: " + e.getMessage(), e);
    }
  }

  private void validateSha1(String sha, String paramName) throws BadRequestException {
    if (sha == null || sha.isEmpty()) {
      throw new BadRequestException("Missing required parameter: " + paramName);
    }
    if (sha.length() != SHA1_LENGTH) {
      throw new BadRequestException(
          String.format(
              "Parameter '%s' must be a 40-character SHA1, got %d characters",
              paramName, sha.length()));
    }
    if (!sha.matches("[0-9a-fA-F]+")) {
      throw new BadRequestException(
          String.format("Parameter '%s' must be a valid hexadecimal SHA1", paramName));
    }
  }

  private ObjectId parseObjectId(String sha, String paramName) throws BadRequestException {
    try {
      return ObjectId.fromString(sha);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Invalid SHA1 for " + paramName + ": " + sha, e);
    }
  }

  private RevCommit parseCommit(RevWalk rw, ObjectId commitId, String paramName)
      throws ResourceNotFoundException, IOException {
    try {
      return rw.parseCommit(commitId);
    } catch (MissingObjectException e) {
      throw new ResourceNotFoundException(
          String.format("Commit '%s' (%s) not found", paramName, commitId.name()), e);
    } catch (IncorrectObjectTypeException e) {
      throw new ResourceNotFoundException(
          String.format("Object '%s' (%s) is not a commit", paramName, commitId.name()), e);
    }
  }

  private void validateAncestorRelationship(RevWalk rw, RevCommit oldCommit, RevCommit newCommit)
      throws BadRequestException, IOException {
    boolean oldIsAncestorOfNew = rw.isMergedInto(oldCommit, newCommit);
    boolean newIsAncestorOfOld = rw.isMergedInto(newCommit, oldCommit);

    if (!oldIsAncestorOfNew && !newIsAncestorOfOld) {
      throw new BadRequestException(
          String.format(
              "Commits %s and %s are not in ancestor/descendant relationship",
              oldCommit.name(), newCommit.name()));
    }
  }

  private void verifyPathVisibility(
      ProjectState projectState,
      Repository repo,
      RevWalk rw,
      RevCommit oldCommit,
      RevCommit newCommit)
      throws ResourceNotFoundException, IOException {
    // Check visibility of oldCommit
    if (!commitsCollection.canRead(projectState, repo, oldCommit)) {
      throw new ResourceNotFoundException("Commit not visible");
    }

    // Walk all commits between old and new and check visibility
    // Reset the RevWalk for a fresh traversal
    rw.reset();
    rw.markStart(newCommit);
    rw.markUninteresting(oldCommit);

    for (RevCommit commit : rw) {
      if (!commitsCollection.canRead(projectState, repo, commit)) {
        // Return 404 to not reveal existence of private commits
        throw new ResourceNotFoundException("Commit not visible");
      }
    }
  }

  /**
   * Converts FileDiffOutput map to FileInfo map.
   *
   * <p>This is the same conversion logic as FileInfoJsonImpl.asFileInfo() to ensure identical
   * output format.
   */
  private Map<String, FileInfo> asFileInfo(Map<String, FileDiffOutput> fileDiffs) {
    Map<String, FileInfo> result = new HashMap<>();
    for (String path : fileDiffs.keySet()) {
      FileDiffOutput fileDiff = fileDiffs.get(path);
      FileInfo fileInfo = new FileInfo();
      fileInfo.status =
          fileDiff.changeType() != Patch.ChangeType.MODIFIED
              ? fileDiff.changeType().getCode()
              : null;
      fileInfo.oldPath = FilePathAdapter.getOldPath(fileDiff.oldPath(), fileDiff.changeType());
      fileInfo.sizeDelta = fileDiff.sizeDelta();
      fileInfo.size = fileDiff.size();
      fileInfo.oldMode =
          fileDiff.oldMode().isPresent() && !fileDiff.oldMode().get().equals(Patch.FileMode.MISSING)
              ? fileDiff.oldMode().get().getMode()
              : null;
      fileInfo.newMode =
          fileDiff.newMode().isPresent() && !fileDiff.newMode().get().equals(Patch.FileMode.MISSING)
              ? fileDiff.newMode().get().getMode()
              : null;
      fileDiff.oldSha().ifPresent(sha -> fileInfo.oldSha = sha.name());
      fileDiff.newSha().ifPresent(sha -> fileInfo.newSha = sha.name());

      if (fileDiff.patchType().get() == Patch.PatchType.BINARY) {
        fileInfo.binary = true;
      } else {
        fileInfo.linesInserted = fileDiff.insertions() > 0 ? fileDiff.insertions() : null;
        fileInfo.linesDeleted = fileDiff.deletions() > 0 ? fileDiff.deletions() : null;
      }
      result.put(path, fileInfo);
    }
    return result;
  }
}
