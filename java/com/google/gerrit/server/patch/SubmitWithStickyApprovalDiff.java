// Copyright (C) 2021 The Android Open Source Project
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

package com.google.gerrit.server.patch;

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.diff.DiffInfoCreator;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.git.validators.CommentCumulativeSizeValidator;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import org.eclipse.jgit.diff.DiffFormatter;
import org.eclipse.jgit.internal.JGitText;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.RawParseUtils;
import org.eclipse.jgit.util.TemporaryBuffer;

/**
 * This class is used on submit to compute the diff between the latest approved patch-set, and the
 * current submitted patch-set.
 *
 * <p>Latest approved patch-set is defined by the latest patch-set which has Code-Review label voted
 * with the maximum possible value.
 *
 * <p>If the latest approved patch-set is the same as the submitted patch-set, the diff will be
 * empty.
 *
 * <p>We exclude the magic files from the returned diff to make it shorter and more concise.
 */
public class SubmitWithStickyApprovalDiff {
  private static final int HEAP_EST_SIZE = 32 * 1024;

  private final DiffOperations diffOperations;
  private final ProjectCache projectCache;
  private final PatchScriptFactory.Factory patchScriptFactoryFactory;
  private final GitRepositoryManager repositoryManager;
  private final int maxAllowedSizeForPostSubmitDiff;

  @Inject
  SubmitWithStickyApprovalDiff(
      DiffOperations diffOperations,
      ProjectCache projectCache,
      PatchScriptFactory.Factory patchScriptFactoryFactory,
      GitRepositoryManager repositoryManager,
      @GerritServerConfig Config serverConfig) {
    this.diffOperations = diffOperations;
    this.projectCache = projectCache;
    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
    this.repositoryManager = repositoryManager;
    // (November 2021) We divide the max cumulative comment size by 10 since it's a reasonable size
    // that is large enough for all purposes but not too large to choke the change index by
    // exceeding the cumulative comment size limit (new comments are not allowed once the limit
    // is reached). At Google, the change index limit is 5MB, while the cumulative size limit is
    // set at 3MB. In this example, we can reach at most 3.3MB hence we ensure not to exceed the
    // limit of 5MB.
    // The reason we exclude the post submit diff from the cumulative comment size limit is
    // because we exclude all auto generated messages, since an argument can be made that they
    // are important enough to be posted anyway. Especially the post submit diff, which must be
    // posted (at least the short version of "the files are too long, please look at the diff").
    maxAllowedSizeForPostSubmitDiff =
        serverConfig.getInt(
                "change",
                "cumulativeCommentSizeLimit",
                CommentCumulativeSizeValidator.DEFAULT_CUMULATIVE_COMMENT_SIZE_LIMIT)
            / 10;
  }

  public String apply(ChangeNotes notes, CurrentUser currentUser)
      throws AuthException, IOException, PermissionBackendException,
          InvalidChangeOperationException {
    PatchSet currentPatchset = notes.getCurrentPatchSet();

    PatchSet.Id latestApprovedPatchsetId = getLatestApprovedPatchsetId(notes);
    if (latestApprovedPatchsetId.get() == currentPatchset.id().get()) {
      // If the latest approved patchset is the current patchset, no need to return anything.
      return "";
    }
    StringBuilder diff =
        new StringBuilder(
            String.format(
                "\n\n%d is the latest approved patch-set.\n", latestApprovedPatchsetId.get()));
    Map<String, FileDiffOutput> modifiedFiles =
        listModifiedFiles(
            notes.getProjectName(),
            currentPatchset,
            notes.getPatchSets().get(latestApprovedPatchsetId));

    // To make the message a bit more concise, we skip the magic files.
    List<FileDiffOutput> modifiedFilesList =
        modifiedFiles.values().stream()
            .filter(p -> !Patch.isMagic(p.newPath().orElse("")))
            .collect(Collectors.toList());

    if (modifiedFilesList.isEmpty()) {
      diff.append(
          "No files were changed between the latest approved patch-set and the submitted one.\n");
      return diff.toString();
    }

    diff.append("The change was submitted with unreviewed changes in the following files:\n\n");
    TemporaryBuffer.Heap buffer =
        new TemporaryBuffer.Heap(
            Math.min(HEAP_EST_SIZE, maxAllowedSizeForPostSubmitDiff),
            maxAllowedSizeForPostSubmitDiff);
    try (Repository repository = repositoryManager.openRepository(notes.getProjectName());
        DiffFormatter formatter = new DiffFormatter(buffer)) {
      formatter.setRepository(repository);
      formatter.setDetectRenames(true);
      boolean isDiffTooLarge = false;
      List<String> formatterResult = null;
      try {
        formatter.format(
            modifiedFilesList.get(0).oldCommitId(), modifiedFilesList.get(0).newCommitId());
        // This returns the diff for all the files.
        formatterResult =
            Arrays.stream(RawParseUtils.decode(buffer.toByteArray()).split("\n"))
                .collect(Collectors.toList());
      } catch (IOException e) {
        if (JGitText.get().inMemoryBufferLimitExceeded.equals(e.getMessage())) {
          isDiffTooLarge = true;
        } else {
          throw e;
        }
      }
      for (FileDiffOutput fileDiff : modifiedFilesList) {
        diff.append(
            getDiffForFile(
                notes,
                currentPatchset.id(),
                latestApprovedPatchsetId,
                fileDiff,
                currentUser,
                formatterResult,
                isDiffTooLarge));
      }
    }
    return diff.toString();
  }

  private String getDiffForFile(
      ChangeNotes notes,
      PatchSet.Id currentPatchsetId,
      PatchSet.Id latestApprovedPatchsetId,
      FileDiffOutput fileDiffOutput,
      CurrentUser currentUser,
      @Nullable List<String> formatterResult,
      boolean isDiffTooLarge)
      throws AuthException, InvalidChangeOperationException, IOException,
          PermissionBackendException {
    StringBuilder diff =
        new StringBuilder(
            String.format(
                "```\nThe name of the file: %s\nInsertions: %d, Deletions: %d.\n\n",
                fileDiffOutput.newPath().isPresent()
                    ? fileDiffOutput.newPath().get()
                    : fileDiffOutput.oldPath().get(),
                fileDiffOutput.insertions(),
                fileDiffOutput.deletions()));
    DiffPreferencesInfo diffPreferencesInfo = createDefaultDiffPreferencesInfo();
    PatchScriptFactory patchScriptFactory =
        patchScriptFactoryFactory.create(
            notes,
            fileDiffOutput.newPath().isPresent()
                ? fileDiffOutput.newPath().get()
                : fileDiffOutput.oldPath().get(),
            latestApprovedPatchsetId,
            currentPatchsetId,
            diffPreferencesInfo,
            currentUser);
    PatchScript patchScript = null;
    try {
      // TODO(paiking): we can get rid of this call to optimize by checking the diff for renames.
      patchScript = patchScriptFactory.call();
    } catch (LargeObjectException exception) {
      diff.append("The file content is too large for showing the full diff. \n\n");
      return diff.toString();
    }
    if (patchScript.getChangeType() == ChangeType.RENAMED) {
      diff.append(
          String.format(
              "The file %s was renamed to %s\n",
              fileDiffOutput.oldPath().get(), fileDiffOutput.newPath().get()));
    }
    if (isDiffTooLarge) {
      diff.append("The diff is too large to show. Please review the diff.");
      diff.append("\n```\n");
      return diff.toString();
    }
    // This filters only the file we need.
    // TODO(paiking): we can make this more efficient by mapping the files to their respective
    //  diffs prior to this method, such that we need to go over the diff only once.
    diff.append(getDiffForFile(patchScript, formatterResult));
    // This line (and the ``` above) are useful for formatting in the web UI.
    diff.append("\n```\n");
    return diff.toString();
  }

  /**
   * Show patch set as unified difference for a specific file. We on purpose are not using {@link
   * DiffInfoCreator} since we'd like to get the original git/JGit style diff.
   */
  public String getDiffForFile(PatchScript patchScript, List<String> formatterResult) {
    // only return information about the current file, and not about files that are not
    // relevant. DiffFormatter returns other potential files because of rebases, which we can
    // ignore.
    List<String> modifiedFormatterResult = new ArrayList<>();
    int indexOfFormatterResult = 0;
    while (formatterResult.size() > indexOfFormatterResult
        && !formatterResult
            .get(indexOfFormatterResult)
            .equals(
                String.format(
                    "diff --git a/%s b/%s",
                    patchScript.getOldName() != null
                        ? patchScript.getOldName()
                        : patchScript.getNewName(),
                    patchScript.getNewName()))) {
      indexOfFormatterResult++;
    }
    // remove non user friendly information.
    while (formatterResult.size() > indexOfFormatterResult
        && !formatterResult.get(indexOfFormatterResult).startsWith("@@")) {
      indexOfFormatterResult++;
    }
    for (; indexOfFormatterResult < formatterResult.size(); indexOfFormatterResult++) {
      if (formatterResult.get(indexOfFormatterResult).startsWith("diff --git")) {
        break;
      }
      modifiedFormatterResult.add(formatterResult.get(indexOfFormatterResult));
    }
    if (modifiedFormatterResult.size() == 0) {
      // This happens for diffs that are just renames, but we already account for renames.
      return "";
    }
    return modifiedFormatterResult.stream()
        .filter(s -> !s.equals("\\ No newline at end of file"))
        .collect(Collectors.joining("\n"));
  }

  private DiffPreferencesInfo createDefaultDiffPreferencesInfo() {
    DiffPreferencesInfo diffPreferencesInfo = new DiffPreferencesInfo();
    diffPreferencesInfo.ignoreWhitespace = Whitespace.IGNORE_NONE;
    diffPreferencesInfo.intralineDifference = true;
    return diffPreferencesInfo;
  }

  private PatchSet.Id getLatestApprovedPatchsetId(ChangeNotes notes) {
    ProjectState projectState =
        projectCache.get(notes.getProjectName()).orElseThrow(illegalState(notes.getProjectName()));
    PatchSet.Id maxPatchSetId = PatchSet.id(notes.getChangeId(), 1);
    for (PatchSetApproval patchSetApproval : notes.getApprovals().values()) {
      if (!patchSetApproval.label().equals(LabelId.CODE_REVIEW)) {
        continue;
      }
      Optional<LabelType> lt =
          projectState.getLabelTypes(notes).byLabel(patchSetApproval.labelId());
      if (!lt.isPresent() || !lt.get().isMaxPositive(patchSetApproval)) {
        continue;
      }
      if (patchSetApproval.patchSetId().get() > maxPatchSetId.get()) {
        maxPatchSetId = patchSetApproval.patchSetId();
      }
    }
    return maxPatchSetId;
  }

  /**
   * Gets the list of modified files between the two latest patch-sets. Can be used to compute
   * difference in files between those two patch-sets.
   */
  private Map<String, FileDiffOutput> listModifiedFiles(
      Project.NameKey project, PatchSet ps, PatchSet priorPatchSet) {
    try {
      return diffOperations.listModifiedFiles(project, priorPatchSet.commitId(), ps.commitId());
    } catch (DiffNotAvailableException ex) {
      throw new StorageException(
          "failed to compute difference in files, so won't post diff messsage on submit although "
              + "the latest approved patch-set was not the same as the submitted patch-set.",
          ex);
    }
  }
}
