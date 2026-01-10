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

import com.google.common.base.MoreObjects;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.common.DiffInfo;
import com.google.gerrit.extensions.common.DiffWebLinkInfo;
import com.google.gerrit.extensions.common.WebLinkInfo;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.diff.DiffInfoCreator;
import com.google.gerrit.server.diff.DiffSide;
import com.google.gerrit.server.diff.DiffWebLinksProvider;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.PatchScriptBuilder;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.project.DiffFileResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

/**
 * Gets the diff for a specific file between two commits.
 *
 * <p>GET /projects/{project}/diff/{file}?old={sha1}&new={sha1}
 *
 * <p>Returns the same format as /changes/{id}/revisions/{rev}/files/{file}/diff to ensure identical
 * output.
 */
public class GetDiffFile implements RestReadView<DiffFileResource> {
  private static final int SHA1_LENGTH = 40;

  private final GitRepositoryManager repoManager;
  private final DiffOperations diffOperations;
  private final Provider<PatchScriptBuilder> patchScriptBuilderProvider;
  private final CommitsCollection commitsCollection;

  @Option(name = "--old", metaVar = "SHA1", usage = "old commit SHA1 (40 characters)")
  private String oldSha;

  @Option(name = "--new", metaVar = "SHA1", usage = "new commit SHA1 (40 characters)")
  private String newSha;

  @Option(name = "--whitespace")
  private Whitespace whitespace;

  @Option(name = "--intraline")
  private boolean intraline;

  public GetDiffFile setOld(String oldSha) {
    this.oldSha = oldSha;
    return this;
  }

  public GetDiffFile setNew(String newSha) {
    this.newSha = newSha;
    return this;
  }

  @Inject
  GetDiffFile(
      GitRepositoryManager repoManager,
      DiffOperations diffOperations,
      Provider<PatchScriptBuilder> patchScriptBuilderProvider,
      CommitsCollection commitsCollection) {
    this.repoManager = repoManager;
    this.diffOperations = diffOperations;
    this.patchScriptBuilderProvider = patchScriptBuilderProvider;
    this.commitsCollection = commitsCollection;
  }

  @Override
  public Response<DiffInfo> apply(DiffFileResource rsrc)
      throws BadRequestException,
          ResourceNotFoundException,
          ResourceConflictException,
          IOException {
    validateSha1(oldSha, "old");
    validateSha1(newSha, "new");

    ProjectState projectState = rsrc.getProjectState();
    projectState.checkStatePermitsRead();
    Project.NameKey project = projectState.getNameKey();
    String filePath = rsrc.getPath();

    ObjectId oldCommitId = parseObjectId(oldSha, "old");
    ObjectId newCommitId = parseObjectId(newSha, "new");

    Whitespace ws = whitespace != null ? whitespace : Whitespace.IGNORE_LEADING_AND_TRAILING;

    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit oldCommit = parseCommit(rw, oldCommitId, "old");
      RevCommit newCommit = parseCommit(rw, newCommitId, "new");

      // Validate that commits are in ancestor/descendant relationship
      validateAncestorRelationship(rw, oldCommit, newCommit);

      // Verify visibility of all commits in the path
      verifyPathVisibility(projectState, repo, rw, oldCommit, newCommit);

      // Get the file diff
      // Don't skip files due to rebase - this is a direct commit comparison, not patchset
      // comparison
      DiffOptions diffOptions =
          DiffOptions.builder().skipFilesWithAllEditsDueToRebase(false).build();
      FileDiffOutput fileDiffOutput =
          diffOperations.getModifiedFile(
              project, oldCommitId, newCommitId, filePath, ws, diffOptions);

      // Convert to PatchScript
      DiffPreferencesInfo prefs = new DiffPreferencesInfo();
      prefs.ignoreWhitespace = ws;
      prefs.intralineDifference = intraline;

      PatchScriptBuilder builder = patchScriptBuilderProvider.get();
      builder.setDiffPrefs(prefs);
      PatchScript ps = builder.toPatchScript(repo, fileDiffOutput);

      // Create DiffInfo
      // For project-level diff, we don't have change context for web links
      DiffWebLinksProvider emptyLinksProvider = new EmptyDiffWebLinksProvider();
      DiffInfoCreator diffInfoCreator =
          new DiffInfoCreator(projectState, emptyLinksProvider, intraline);

      DiffSide sideA =
          DiffSide.create(
              ps.getFileInfoA(),
              MoreObjects.firstNonNull(ps.getOldName(), ps.getNewName()),
              DiffSide.Type.SIDE_A);
      DiffSide sideB = DiffSide.create(ps.getFileInfoB(), ps.getNewName(), DiffSide.Type.SIDE_B);

      DiffInfo result = diffInfoCreator.create(ps, sideA, sideB);
      return Response.ok(result);
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
    rw.reset();
    rw.markStart(newCommit);
    rw.markUninteresting(oldCommit);

    for (RevCommit commit : rw) {
      if (!commitsCollection.canRead(projectState, repo, commit)) {
        throw new ResourceNotFoundException("Commit not visible");
      }
    }
  }

  /** Empty implementation since project-level diff has no change context for web links. */
  private static class EmptyDiffWebLinksProvider implements DiffWebLinksProvider {
    @Override
    public ImmutableList<DiffWebLinkInfo> getDiffLinks() {
      return ImmutableList.of();
    }

    @Override
    public ImmutableList<WebLinkInfo> getEditWebLinks() {
      return ImmutableList.of();
    }

    @Override
    public ImmutableList<WebLinkInfo> getFileWebLinks(DiffSide.Type type) {
      return ImmutableList.of();
    }
  }
}
