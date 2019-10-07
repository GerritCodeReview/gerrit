// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.util;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.entities.Project;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;

public final class MagicBranch {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static final String NEW_CHANGE = "refs/for/";

  /** Extracts the destination from a ref name */
  public static String getDestBranchName(String refName) {
    return refName.substring(NEW_CHANGE.length());
  }

  /** Checks if the supplied ref name is a magic branch */
  public static boolean isMagicBranch(String refName) {
    return refName.startsWith(NEW_CHANGE);
  }

  /** Returns the ref name prefix for a magic branch, {@code null} if the branch is not magic */
  public static String getMagicRefNamePrefix(String refName) {
    if (refName.startsWith(NEW_CHANGE)) {
      return NEW_CHANGE;
    }
    return null;
  }

  /**
   * Checks if a (magic branch)/branch_name reference exists in the destination repository and only
   * returns Capable.OK if it does not match any.
   *
   * <p>These block the client from being able to even send us a pack file, as it is very unlikely
   * the user passed the --force flag and the new commit is probably not going to fast-forward the
   * branch.
   */
  public static Capable checkMagicBranchRefs(Repository repo, Project project) {
    return checkMagicBranchRef(NEW_CHANGE, repo, project);
  }

  private static Capable checkMagicBranchRef(String branchName, Repository repo, Project project) {
    List<Ref> blockingFors;
    try {
      blockingFors = repo.getRefDatabase().getRefsByPrefix(branchName);
    } catch (IOException err) {
      String projName = project.getName();
      logger.atWarning().withCause(err).log("Cannot scan refs in '%s'", projName);
      return new Capable("Server process cannot read '" + projName + "'");
    }
    if (!blockingFors.isEmpty()) {
      String projName = project.getName();
      logger.atSevere().log(
          "Repository '%s' needs the following refs removed to receive changes: %s",
          projName, blockingFors);
      return new Capable("One or more " + branchName + " names blocks change upload");
    }

    return Capable.OK;
  }

  private MagicBranch() {}
}
