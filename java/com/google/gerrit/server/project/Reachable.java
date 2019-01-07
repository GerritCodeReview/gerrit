// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.NameKey;
import com.google.gerrit.server.change.IncludedInResolver;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Report whether a commit is reachable from a set of commits. This is used for checking if a user
 * has read permissions on a commit.
 */
@Singleton
public class Reachable {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PermissionBackend permissionBackend;

  @Inject
  Reachable(PermissionBackend permissionBackend) {
    this.permissionBackend = permissionBackend;
  }

  /** @return true if a commit is reachable from a given set of refs. */
  public boolean fromRefs(NameKey project, Repository repo, RevCommit commit, List<Ref> refs) {
    try (RevWalk rw = new RevWalk(repo)) {
      Map<String, Ref> filtered =
          permissionBackend
              .currentUser()
              .project(project)
              .filter(refs, repo, RefFilterOptions.builder().setFilterTagsSeparately(true).build());
      return IncludedInResolver.includedInAny(repo, rw, commit, filtered.values());
    } catch (IOException | PermissionBackendException e) {
      logger.atSevere().withCause(e).log(
          "Cannot verify permissions to commit object %s in repository %s", commit.name(), project);
      return false;
    }
  }

  /** @return true if a commit is reachable from a repo's branches and tags. */
  boolean fromHeadsOrTags(Project.NameKey project, Repository repo, RevCommit commit) {
    try {
      List<Ref> refs = repo.getRefDatabase().getRefsByPrefix(Constants.R_HEADS, Constants.R_TAGS);
      return fromRefs(project, repo, commit, refs);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Cannot verify permissions to commit object %s in repository %s", commit.name(), project);
      return false;
    }
  }
}
