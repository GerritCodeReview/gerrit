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
import com.google.gerrit.extensions.restapi.RestApiException;
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
import com.google.gerrit.server.project.FileResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

/**
 * Gets the diff for a specific file between two commits.
 *
 * <p>GET /projects/{project}/commits/{commit}/files/{file}/diff?base={sha1}
 *
 * <p>Returns the same format as /changes/{id}/revisions/{rev}/files/{file}/diff to ensure identical
 * output.
 */
public class GetDiffFile implements RestReadView<FileResource> {

  private final GitRepositoryManager repoManager;
  private final DiffOperations diffOperations;
  private final Provider<PatchScriptBuilder> patchScriptBuilderProvider;
  private final ProjectDiffUtils diffUtils;

  @Option(name = "--base", metaVar = "SHA1", usage = "base commit SHA1 (40 characters)")
  private String baseSha;

  @Option(name = "--whitespace")
  private Whitespace whitespace;

  @Option(name = "--intraline")
  private boolean intraline;

  public GetDiffFile setBase(String baseSha) {
    this.baseSha = baseSha;
    return this;
  }

  @Inject
  GetDiffFile(
      GitRepositoryManager repoManager,
      DiffOperations diffOperations,
      Provider<PatchScriptBuilder> patchScriptBuilderProvider,
      ProjectDiffUtils diffUtils) {
    this.repoManager = repoManager;
    this.diffOperations = diffOperations;
    this.patchScriptBuilderProvider = patchScriptBuilderProvider;
    this.diffUtils = diffUtils;
  }

  @Override
  public Response<DiffInfo> apply(FileResource rsrc)
      throws RestApiException,
          BadRequestException,
          ResourceNotFoundException,
          ResourceConflictException,
          IOException {
    diffUtils.validateSha1(baseSha, "base");

    ProjectState projectState = rsrc.getProjectState();
    projectState.checkStatePermitsRead();
    Project.NameKey project = projectState.getNameKey();
    String filePath = rsrc.getPath();

    ObjectId baseCommitId = diffUtils.parseObjectId(baseSha, "base");
    ObjectId newCommitId = rsrc.getRev();

    Whitespace ws = whitespace != null ? whitespace : Whitespace.IGNORE_LEADING_AND_TRAILING;

    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit baseCommit = diffUtils.parseCommit(rw, baseCommitId, "base");
      RevCommit newCommit = rw.parseCommit(newCommitId);

      // Validate that commits are in ancestor/descendant relationship
      diffUtils.validateAncestorRelationship(rw, baseCommit, newCommit);

      // Verify visibility of all commits in the path
      diffUtils.verifyPathVisibility(projectState, repo, rw, baseCommit, newCommit);

      // Get the file diff
      // Don't skip files due to rebase - this is a direct commit comparison, not patchset
      // comparison
      DiffOptions diffOptions =
          DiffOptions.builder().skipFilesWithAllEditsDueToRebase(false).build();
      FileDiffOutput fileDiffOutput =
          diffOperations.getModifiedFile(
              project, baseCommitId, newCommitId, filePath, ws, diffOptions);

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
      throw diffUtils.mapDiffException(e);
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
