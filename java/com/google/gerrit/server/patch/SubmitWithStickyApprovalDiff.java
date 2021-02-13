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

import com.google.gerrit.common.data.PatchScript;
import com.google.gerrit.entities.LabelId;
import com.google.gerrit.entities.Patch;
import com.google.gerrit.entities.Patch.ChangeType;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.DiffPreferencesInfo;
import com.google.gerrit.extensions.client.DiffPreferencesInfo.Whitespace;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.prettify.common.SparseFileContent.Accessor;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.LargeObjectException;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;
import org.eclipse.jgit.diff.Edit;

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
  private final ProjectCache projectCache;
  private final PatchScriptFactory.Factory patchScriptFactoryFactory;
  private final PatchListCache patchListCache;

  @Inject
  SubmitWithStickyApprovalDiff(
      ProjectCache projectCache,
      PatchScriptFactory.Factory patchScriptFactoryFactory,
      PatchListCache patchListCache) {
    this.projectCache = projectCache;
    this.patchScriptFactoryFactory = patchScriptFactoryFactory;
    this.patchListCache = patchListCache;
  }

  public String apply(ChangeNotes notes, CurrentUser currentUser)
      throws AuthException, IOException, PermissionBackendException,
          InvalidChangeOperationException {
    // In some submit strategies, the current patch-set doesn't exist yet as it's being created
    // during the submit. Hence, we assign the current patch-set to be the last existing patch-set.
    PatchSet currentPatchset =
        notes.getPatchSets().values().stream()
            .max((p1, p2) -> p1.id().get() - p2.id().get())
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        String.format(
                            "change %s can't load any patchset", notes.getChangeId().toString())));

    PatchSet.Id latestApprovedPatchsetId = getLatestApprovedPatchsetId(notes);
    if (latestApprovedPatchsetId.get() == currentPatchset.id().get()) {
      // If the latest approved patchset is the current patchset, no need to return anything.
      return "";
    }
    String diff =
        String.format("\n\n%d is the latest approved patch-set.\n", latestApprovedPatchsetId.get());
    PatchList patchList =
        getPatchList(
            notes.getProjectName(),
            currentPatchset,
            notes.getPatchSets().get(latestApprovedPatchsetId));

    // To make the message a bit more concise, we skip the magic files.
    List<PatchListEntry> patchListEntryList =
        patchList.getPatches().stream()
            .filter(p -> !Patch.isMagic(p.getNewName()))
            .collect(Collectors.toList());

    if (patchListEntryList.isEmpty()) {
      diff +=
          "No files were changed between the latest approved patch-set and the submitted one.\n";
      return diff;
    }

    diff += "The change was submitted with unreviewed changes in the following files:\n\n";

    for (PatchListEntry patchListEntry : patchListEntryList) {
      diff +=
          getDiffForFile(
              notes, currentPatchset.id(), latestApprovedPatchsetId, patchListEntry, currentUser);
    }
    return diff;
  }

  private String getDiffForFile(
      ChangeNotes notes,
      PatchSet.Id currentPatchsetId,
      PatchSet.Id latestApprovedPatchsetId,
      PatchListEntry patchListEntry,
      CurrentUser currentUser)
      throws AuthException, InvalidChangeOperationException, IOException,
          PermissionBackendException {
    String diff =
        String.format(
            "The name of the file: %s\nInsertions: %d, Deletions: %d.\n\n",
            patchListEntry.getNewName(),
            patchListEntry.getInsertions(),
            patchListEntry.getDeletions());
    DiffPreferencesInfo diffPreferencesInfo = createDefaultDiffPreferencesInfo();
    PatchScriptFactory patchScriptFactory =
        patchScriptFactoryFactory.create(
            notes,
            patchListEntry.getNewName(),
            latestApprovedPatchsetId,
            currentPatchsetId,
            diffPreferencesInfo,
            currentUser);
    PatchScript patchScript = null;
    try {
      patchScript = patchScriptFactory.call();
    } catch (LargeObjectException exception) {
      diff += "The file content is too large for showing the full diff. \n\n";
      return diff;
    }
    if (patchScript.getChangeType() == ChangeType.RENAMED) {
      diff +=
          String.format(
              "The file %s was renamed to %s\n",
              patchListEntry.getOldName(), patchListEntry.getNewName());
    }
    Accessor fileA = patchScript.getA().createAccessor();
    Accessor fileB = patchScript.getB().createAccessor();
    boolean editsExist = false;
    if (patchScript.getEdits().stream().anyMatch(e -> e.getType() != Edit.Type.EMPTY)) {
      diff += "```\n";
      editsExist = true;
    }
    for (Edit edit : patchScript.getEdits()) {
      diff += getDiffForEdit(fileA, fileB, edit);
    }
    if (editsExist) {
      diff += "```\n";
    }
    return diff;
  }

  private String getDiffForEdit(Accessor fileA, Accessor fileB, Edit edit) {
    String diff = "";
    Edit.Type type = edit.getType();
    switch (type) {
      case INSERT:
        diff += String.format("@@ +%d:%d @@\n", edit.getBeginB(), edit.getEndB());
        diff += getModifiedLines(fileB, edit.getBeginB(), edit.getEndB(), '+');
        diff += "\n";
        break;
      case DELETE:
        diff += String.format("@@ -%d:%d @@\n", edit.getBeginA(), edit.getEndA());
        diff += getModifiedLines(fileA, edit.getBeginA(), edit.getEndA(), '-');
        diff += "\n";
        break;
      case REPLACE:
        diff +=
            String.format(
                "@@ -%d:%d, +%d:%d @@\n",
                edit.getBeginA(), edit.getEndA(), edit.getBeginB(), edit.getEndB());
        diff += getModifiedLines(fileA, edit.getBeginA(), edit.getEndA(), '-');
        diff += getModifiedLines(fileB, edit.getBeginB(), edit.getEndB(), '+');
        diff += "\n";
        break;
      case EMPTY:
        // do nothing since there is no change here.
    }
    return diff;
  }

  private String getModifiedLines(Accessor file, int begin, int end, char modificationType) {
    String diff = "";
    for (int i = begin; i < end; i++) {
      diff += String.format("%c  %s\n", modificationType, file.get(i));
    }
    return diff;
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
      if (!projectState
          .getLabelTypes(notes)
          .byLabel(patchSetApproval.labelId())
          .isMaxPositive(patchSetApproval)) {
        continue;
      }
      if (patchSetApproval.patchSetId().get() > maxPatchSetId.get()) {
        maxPatchSetId = patchSetApproval.patchSetId();
      }
    }
    return maxPatchSetId;
  }

  /**
   * Gets the {@link PatchList} between the two latest patch-sets. Can be used to compute difference
   * in files between those two patch-sets .
   */
  private PatchList getPatchList(Project.NameKey project, PatchSet ps, PatchSet priorPatchSet) {
    PatchListKey key =
        PatchListKey.againstCommit(priorPatchSet.commitId(), ps.commitId(), Whitespace.IGNORE_NONE);
    try {
      return patchListCache.get(key, project);
    } catch (PatchListNotAvailableException ex) {
      throw new StorageException(
          "failed to compute difference in files, so won't post diff messsage on submit although "
              + "the latest approved patch-set was not the same as the submitted patch-set.",
          ex);
    }
  }
}
