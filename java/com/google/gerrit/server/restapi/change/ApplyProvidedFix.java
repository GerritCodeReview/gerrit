// Copyright (C) 2022 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.gerrit.common.RawInputUtil;
import com.google.gerrit.entities.Comment.Range;
import com.google.gerrit.entities.FixReplacement;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.ApplyPatchInput;
import com.google.gerrit.extensions.common.ApplyProvidedFixInput;
import com.google.gerrit.extensions.common.EditInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditJson;
import com.google.gerrit.server.edit.ChangeEditModifier;
import com.google.gerrit.server.edit.CommitModification;
import com.google.gerrit.server.edit.tree.ChangeFileContentModification;
import com.google.gerrit.server.fixes.FixReplacementInterpreter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.ApplyPatchUtil;
import com.google.gerrit.server.patch.MagicFile;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.patch.PatchApplier;
import org.eclipse.jgit.patch.PatchApplier.Result.Error;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;

/** Applies a fix that is provided as part of the request body. */
@Singleton
public class ApplyProvidedFix implements RestModifyView<RevisionResource, ApplyProvidedFixInput> {
  private final GitRepositoryManager gitRepositoryManager;
  private final FixReplacementInterpreter fixReplacementInterpreter;
  private final ChangeEditModifier changeEditModifier;
  private final ChangeEditJson changeEditJson;
  private final ProjectCache projectCache;

  @Inject
  public ApplyProvidedFix(
      GitRepositoryManager gitRepositoryManager,
      FixReplacementInterpreter fixReplacementInterpreter,
      ChangeEditModifier changeEditModifier,
      ChangeEditJson changeEditJson,
      ProjectCache projectCache) {
    this.gitRepositoryManager = gitRepositoryManager;
    this.fixReplacementInterpreter = fixReplacementInterpreter;
    this.changeEditModifier = changeEditModifier;
    this.changeEditJson = changeEditJson;
    this.projectCache = projectCache;
  }

  @Override
  public Response<EditInfo> apply(
      RevisionResource revisionResource, ApplyProvidedFixInput applyProvidedFixInput)
      throws AuthException,
          BadRequestException,
          ResourceConflictException,
          IOException,
          ResourceNotFoundException,
          PermissionBackendException,
          RestApiException {
    if (applyProvidedFixInput == null) {
      throw new BadRequestException("applyProvidedFixInput is required");
    }
    if (applyProvidedFixInput.fixReplacementInfos == null) {
      throw new BadRequestException("applyProvidedFixInput.fixReplacementInfos is required");
    }
    Project.NameKey project = revisionResource.getProject();
    ProjectState projectState = projectCache.get(project).orElseThrow(illegalState(project));
    PatchSet targetPatchSet = revisionResource.getPatchSet();

    ChangeNotes changeNotes = revisionResource.getNotes();
    PatchSet originPatchSetForFix =
        applyProvidedFixInput.originalPatchsetForFix != null
                && applyProvidedFixInput.originalPatchsetForFix > 0
            ? changeNotes
                .getPatchSets()
                .get(
                    PatchSet.id(
                        revisionResource.getChange().getId(),
                        applyProvidedFixInput.originalPatchsetForFix))
            : targetPatchSet;

    List<FixReplacement> fixReplacements =
        applyProvidedFixInput.fixReplacementInfos.stream()
            .map(fix -> new FixReplacement(fix.path, new Range(fix.range), fix.replacement))
            .collect(Collectors.toList());

    try (Repository repository = gitRepositoryManager.openRepository(project)) {
      CommitModification commitModification =
          getCommitModification(
              repository, projectState, originPatchSetForFix, targetPatchSet, fixReplacements);
      ChangeEdit changeEdit =
          changeEditModifier.combineWithModifiedPatchSetTree(
              repository, changeNotes, targetPatchSet, commitModification);

      return Response.ok(changeEditJson.toEditInfo(changeEdit, false));
    } catch (InvalidChangeOperationException e) {
      throw new ResourceConflictException(e.getMessage());
    }
  }

  /**
   * Returns CommitModification for fixes and rebase it if the fix is for an older patchset.
   *
   * <p>The method creates CommitModification by applying {@code fixReplacements} to the {@code
   * basePatchSetForFix}. If the {@code targetPatchSetForFix} is different from the {@code
   * basePatchSetForFix}, CommitModification is created from the {@link
   * org.eclipse.jgit.patch.PatchApplier.Result}, after applying the patch generated from {@code
   * basePatchSetForFix} to the {@code targetPatchSetForFix}.
   *
   * <p>Note: if there is a fix for a commit message and commit messages are different in {@code
   * basePatchSetForFix} and {@code targetPatchSetForFix}, the method can't move the fix to the
   * {@code targetPatchSetForFix} and throws {@link ResourceConflictException}. This limitations
   * exists because the method uses ApplyPatchUtil which operates only on files.
   */
  private CommitModification getCommitModification(
      Repository repository,
      ProjectState projectState,
      PatchSet basePatchSetForFix,
      PatchSet targetPatchSetForFix,
      List<FixReplacement> fixReplacements)
      throws IOException, InvalidChangeOperationException, RestApiException {
    CommitModification originCommitModification =
        fixReplacementInterpreter.toCommitModification(
            repository, projectState, basePatchSetForFix.commitId(), fixReplacements);
    if (basePatchSetForFix.id().equals(targetPatchSetForFix.id())) {
      return originCommitModification;
    }
    RevCommit originCommit = repository.parseCommit(basePatchSetForFix.commitId());
    ObjectId newTreeId =
        ChangeEditModifier.createNewTree(
            repository, originCommit, originCommitModification.treeModifications());
    CommitModification.Builder resultBuilder = CommitModification.builder();
    String patch;
    try (RevWalk rw = new RevWalk(repository)) {
      ObjectId targetCommit = targetPatchSetForFix.commitId();
      if (originCommitModification.newCommitMessage().isPresent()) {
        MagicFile originCommitMessageFile =
            MagicFile.forCommitMessage(rw.getObjectReader(), originCommit);
        String originCommitMessage = originCommitMessageFile.modifiableContent();
        MagicFile targetCommitMessageFile =
            MagicFile.forCommitMessage(rw.getObjectReader(), targetCommit);
        String targetCommitMessage = targetCommitMessageFile.modifiableContent();
        if (!originCommitMessage.equals(targetCommitMessage)) {
          throw new ResourceConflictException(
              "The fix attempts to modify commit message of an older patchset, but commit message"
                  + " has been updated in a newer patchset. The fix can't be applied.");
        }
        resultBuilder.newCommitMessage(originCommitModification.newCommitMessage().get());
      }

      patch =
          ApplyPatchUtil.getResultPatch(
              repository, repository.newObjectReader(), originCommit, rw.lookupTree(newTreeId));
      PatchApplier.Result result;
      try (ObjectInserter oi = repository.newObjectInserter()) {
        ApplyPatchInput inp = new ApplyPatchInput();
        inp.patch = patch;
        // Allow conflicts for showing more precise error message.
        inp.allowConflicts = true;
        result =
            ApplyPatchUtil.applyPatch(repository, oi, inp, repository.parseCommit(targetCommit));
        if (!result.getErrors().isEmpty()) {
          String errorMessage =
              (result.getErrors().stream().anyMatch(Error::isGitConflict)
                      ? "Merge conflict while applying a fix:\n"
                      : "Error while applying a fix:\n")
                  + result.getErrors().stream()
                      .map(Error::toString)
                      .collect(Collectors.joining("\n"));
          throw new ResourceConflictException(errorMessage);
        }
        oi.flush();
        for (String path : result.getPaths()) {
          try (TreeWalk tw = TreeWalk.forPath(rw.getObjectReader(), path, result.getTreeId())) {
            ObjectLoader loader = rw.getObjectReader().open(tw.getObjectId(0));
            resultBuilder.addTreeModification(
                new ChangeFileContentModification(path, RawInputUtil.create(loader.getBytes())));
          }
        }
      }
    }
    return resultBuilder.build();
  }
}
