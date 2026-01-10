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
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.FilePathAdapter;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.project.CommitResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.kohsuke.args4j.Option;

/**
 * Lists files that differ between two commits in a project.
 *
 * <p>GET /projects/{project}/commits/{commit}/diff?base={sha1}
 *
 * <p>Returns the same format as /changes/{id}/revisions/{rev}/files to ensure identical output.
 */
public class ListDiffFiles implements RestReadView<CommitResource> {
  private final GitRepositoryManager repoManager;
  private final DiffOperations diffOperations;
  private final ProjectDiffUtils diffUtils;

  @Option(name = "--base", metaVar = "SHA1", usage = "base commit SHA1 (40 characters)")
  private String baseSha;

  @Option(name = "--name-only", usage = "return only the list of files")
  private boolean nameOnly;

  public ListDiffFiles setBase(String baseSha) {
    this.baseSha = baseSha;
    return this;
  }

  public ListDiffFiles setNameOnly(boolean nameOnly) {
    this.nameOnly = nameOnly;
    return this;
  }

  @Inject
  ListDiffFiles(
      GitRepositoryManager repoManager, DiffOperations diffOperations, ProjectDiffUtils diffUtils) {
    this.repoManager = repoManager;
    this.diffOperations = diffOperations;
    this.diffUtils = diffUtils;
  }

  @Override
  public Response<Map<String, FileInfo>> apply(CommitResource rsrc)
      throws RestApiException,
          BadRequestException,
          ResourceNotFoundException,
          ResourceConflictException,
          IOException {
    if (!nameOnly) {
      throw new BadRequestException("name-only parameter is required for listing diff files");
    }

    diffUtils.validateSha1(baseSha, "base");

    rsrc.getProjectState().checkStatePermitsRead();
    Project.NameKey project = rsrc.getProjectState().getNameKey();
    ProjectState projectState = rsrc.getProjectState();

    ObjectId baseCommitId = diffUtils.parseObjectId(baseSha, "base");
    ObjectId newCommitId = rsrc.getCommit();

    try (Repository repo = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(repo)) {
      RevCommit baseCommit = diffUtils.parseCommit(rw, baseCommitId, "base");
      RevCommit newCommit = rw.parseCommit(newCommitId);

      // Validate that commits are in ancestor/descendant relationship
      diffUtils.validateAncestorRelationship(rw, baseCommit, newCommit);

      // Walk all commits in path and verify visibility for each (critical for private changes)
      diffUtils.verifyPathVisibility(projectState, repo, rw, baseCommit, newCommit);

      // Compute the diff
      // Don't skip files due to rebase - this is a direct commit comparison, not patchset
      // comparison
      DiffOptions diffOptions =
          DiffOptions.builder().skipFilesWithAllEditsDueToRebase(false).build();
      Map<String, FileDiffOutput> fileDiffs =
          diffOperations.listModifiedFiles(project, baseCommitId, newCommitId, diffOptions);

      return Response.ok(asFileInfo(fileDiffs));
    } catch (DiffNotAvailableException e) {
      throw diffUtils.mapDiffException(e);
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
