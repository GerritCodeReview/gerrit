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

import com.google.gerrit.common.data.Capable;
import com.google.gerrit.reviewdb.client.Project;
import java.io.IOException;
import java.util.Map;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class MagicBranch {
  private static final Logger log = LoggerFactory.getLogger(MagicBranch.class);

  public static final String NEW_CHANGE = "refs/for/";
  public static final String NEW_DRAFT_CHANGE = "refs/drafts/";
  public static final String NEW_PUBLISH_CHANGE = "refs/publish/";

  /** Extracts the destination from a ref name */
  public static String getDestBranchName(String refName) {
    String magicBranch = NEW_CHANGE;
    if (refName.startsWith(NEW_DRAFT_CHANGE)) {
      magicBranch = NEW_DRAFT_CHANGE;
    } else if (refName.startsWith(NEW_PUBLISH_CHANGE)) {
      magicBranch = NEW_PUBLISH_CHANGE;
    }
    return refName.substring(magicBranch.length());
  }

  /** Checks if the supplied ref name is a magic branch */
  public static boolean isMagicBranch(String refName) {
    return refName.startsWith(NEW_DRAFT_CHANGE)
        || refName.startsWith(NEW_PUBLISH_CHANGE)
        || refName.startsWith(NEW_CHANGE);
  }

  /** Returns the ref name prefix for a magic branch, {@code null} if the branch is not magic */
  public static String getMagicRefNamePrefix(String refName) {
    if (refName.startsWith(NEW_DRAFT_CHANGE)) {
      return NEW_DRAFT_CHANGE;
    }
    if (refName.startsWith(NEW_PUBLISH_CHANGE)) {
      return NEW_PUBLISH_CHANGE;
    }
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
    Capable result = checkMagicBranchRef(NEW_CHANGE, repo, project);
    if (result != Capable.OK) {
      return result;
    }
    result = checkMagicBranchRef(NEW_DRAFT_CHANGE, repo, project);
    if (result != Capable.OK) {
      return result;
    }
    result = checkMagicBranchRef(NEW_PUBLISH_CHANGE, repo, project);
    if (result != Capable.OK) {
      return result;
    }

    return Capable.OK;
  }

  private static Capable checkMagicBranchRef(String branchName, Repository repo, Project project) {
    Map<String, Ref> blockingFors;
    try {
      blockingFors = repo.getRefDatabase().getRefs(branchName);
    } catch (IOException err) {
      String projName = project.getName();
      log.warn("Cannot scan refs in '" + projName + "'", err);
      return new Capable("Server process cannot read '" + projName + "'");
    }
    if (!blockingFors.isEmpty()) {
      String projName = project.getName();
      log.error(
          "Repository '"
              + projName
              + "' needs the following refs removed to receive changes: "
              + blockingFors.keySet());
      return new Capable("One or more " + branchName + " names blocks change upload");
    }

    return Capable.OK;
  }

  private MagicBranch() {}
}
