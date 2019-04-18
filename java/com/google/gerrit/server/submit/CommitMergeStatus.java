// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import static java.util.stream.Collectors.toSet;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.inject.Provider;
import java.util.List;
import java.util.Optional;

/**
 * Status codes set on {@link com.google.gerrit.server.git.CodeReviewCommit}s by {@link
 * SubmitStrategy} implementations.
 */
public enum CommitMergeStatus {
  CLEAN_MERGE("Change has been successfully merged"),

  CLEAN_PICK("Change has been successfully cherry-picked"),

  CLEAN_REBASE("Change has been successfully rebased and submitted"),

  ALREADY_MERGED(""),

  PATH_CONFLICT(
      "Change could not be merged due to a path conflict.\n"
          + "\n"
          + "Please rebase the change locally and upload the rebased commit for review."),

  REBASE_MERGE_CONFLICT(
      "Change could not be merged due to a conflict.\n"
          + "\n"
          + "Please rebase the change locally and upload the rebased commit for review."),

  SKIPPED_IDENTICAL_TREE(
      "Marking change merged without cherry-picking to branch, as the resulting commit would be empty."),

  MISSING_DEPENDENCY("Depends on change that was not submitted."),

  MANUAL_RECURSIVE_MERGE(
      "The change requires a local merge to resolve.\n"
          + "\n"
          + "Please merge (or rebase) the change locally and upload the resolution for review."),

  CANNOT_CHERRY_PICK_ROOT(
      "Cannot cherry-pick an initial commit onto an existing branch.\n"
          + "\n"
          + "Please merge the change locally and upload the merge commit for review."),

  CANNOT_REBASE_ROOT(
      "Cannot rebase an initial commit onto an existing branch.\n"
          + "\n"
          + "Please merge the change locally and upload the merge commit for review."),

  NOT_FAST_FORWARD(
      "Project policy requires all submissions to be a fast-forward.\n"
          + "\n"
          + "Please rebase the change locally and upload again for review."),

  EMPTY_COMMIT(
      "Change could not be merged because the commit is empty.\n"
          + "\n"
          + "Project policy requires all commits to contain modifications to at least one file.");

  private final String description;

  CommitMergeStatus(String description) {
    this.description = description;
  }

  public String getDescription() {
    return description;
  }

  public static String createMissingDependencyMessage(
      @Nullable CurrentUser caller,
      Provider<InternalChangeQuery> queryProvider,
      String commit,
      String otherCommit) {
    List<ChangeData> changes = queryProvider.get().enforceVisibility(true).byCommit(otherCommit);

    if (changes.isEmpty()) {
      return String.format(
          "Commit %s depends on commit %s which cannot be merged."
              + " Is the change of this commit not visible to '%s' or was it deleted?",
          commit, otherCommit, caller != null ? caller.getLoggableName() : "<user-not-available>");
    } else if (changes.size() == 1) {
      ChangeData cd = changes.get(0);
      if (cd.currentPatchSet().getRevision().get().equals(otherCommit)) {
        return String.format(
            "Commit %s depends on commit %s of change %d which cannot be merged.",
            commit, otherCommit, cd.getId().get());
      }
      Optional<PatchSet> patchSet =
          cd.patchSets().stream()
              .filter(ps -> ps.getRevision().get().equals(otherCommit))
              .findAny();
      if (patchSet.isPresent()) {
        return String.format(
            "Commit %s depends on commit %s, which is outdated patch set %d of change %d."
                + " The latest patch set is %d.",
            commit,
            otherCommit,
            patchSet.get().getId().get(),
            cd.getId().get(),
            cd.currentPatchSet().getId().get());
      }
      // should not happen, fall-back to default message
      return String.format(
          "Commit %s depends on commit %s of change %d which cannot be merged.",
          commit, otherCommit, cd.getId().get());
    } else {
      return String.format(
          "Commit %s depends on commit %s of changes %s which cannot be merged.",
          commit, otherCommit, changes.stream().map(cd -> cd.getId().get()).collect(toSet()));
    }
  }
}
