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

package com.google.gerrit.server.submitrequirement.predicate;

import static com.google.gerrit.server.query.change.SubmitRequirementChangeQueryBuilder.SUBMODULE_UPDATE_HAS_ARG;

import com.google.gerrit.entities.Patch.FileMode;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.patch.DiffOperations;
import com.google.gerrit.server.patch.DiffOptions;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.SubmitRequirementPredicate;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Submit requirement predicate that returns true if the diff of the latest patchset against the
 * parent number identified by {@link #base} has a submodule modified file, that is, a .gitmodules
 * or a git link file.
 */
public class HasSubmoduleUpdatePredicate extends SubmitRequirementPredicate {
  private static final String GIT_MODULES_FILE = ".gitmodules";

  private final DiffOperations diffOperations;
  private final GitRepositoryManager repoManager;
  private final int base;

  public interface Factory {
    HasSubmoduleUpdatePredicate create(int base);
  }

  @Inject
  HasSubmoduleUpdatePredicate(
      DiffOperations diffOperations, GitRepositoryManager repoManager, @Assisted int base) {
    super("has", SUBMODULE_UPDATE_HAS_ARG);
    this.diffOperations = diffOperations;
    this.repoManager = repoManager;
    this.base = base;
  }

  @Override
  public boolean match(ChangeData cd) {
    try {
      try (Repository repo = repoManager.openRepository(cd.project());
          RevWalk rw = new RevWalk(repo)) {
        RevCommit revCommit = rw.parseCommit(cd.currentPatchSet().commitId());
        if (base > revCommit.getParentCount()) {
          return false;
        }
      }
      Map<String, FileDiffOutput> diffList =
          diffOperations.listModifiedFilesAgainstParent(
              cd.project(), cd.currentPatchSet().commitId(), base, DiffOptions.DEFAULTS);
      return diffList.values().stream().anyMatch(HasSubmoduleUpdatePredicate::isGitLink);
    } catch (DiffNotAvailableException e) {
      throw new StorageException(
          String.format(
              "Failed to evaluate the diff for commit %s against parent number %d",
              cd.currentPatchSet().commitId(), base),
          e);
    } catch (IOException e) {
      throw new StorageException(
          String.format("Failed to open repo for project %s", cd.project()), e);
    }
  }

  /**
   * Return true if the modified file is a {@link #GIT_MODULES_FILE} or a git link regardless of if
   * the modification type is add, remove or modify.
   */
  private static boolean isGitLink(FileDiffOutput fileDiffOutput) {
    Optional<String> oldPath = fileDiffOutput.oldPath();
    Optional<String> newPath = fileDiffOutput.newPath();
    Optional<FileMode> oldMode = fileDiffOutput.oldMode();
    Optional<FileMode> newMode = fileDiffOutput.newMode();

    return (oldPath.isPresent() && oldPath.get().equals(GIT_MODULES_FILE))
        || (newPath.isPresent() && newPath.get().equals(GIT_MODULES_FILE))
        || (oldMode.isPresent() && oldMode.get().equals(FileMode.GITLINK))
        || (newMode.isPresent() && newMode.get().equals(FileMode.GITLINK));
  }

  @Override
  public int getCost() {
    return 1;
  }
}
