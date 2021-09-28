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

package com.google.gerrit.server.change;

import static com.google.common.collect.ImmutableList.toImmutableList;
import static java.util.stream.Collectors.toList;

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

public class IncludedInRefs {
  protected final GitRepositoryManager repoManager;
  protected final PermissionBackend permissionBackend;

  @Inject
  IncludedInRefs(GitRepositoryManager repoManager, PermissionBackend permissionBackend) {
    this.repoManager = repoManager;
    this.permissionBackend = permissionBackend;
  }

  public Collection<String> apply(
      Project.NameKey project, ObjectId commitId, List<String> refsNames)
      throws ResourceConflictException, BadRequestException, IOException,
          PermissionBackendException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk revWalk = new RevWalk(repo)) {
      revWalk.setRetainBody(false);
      RevCommit revCommit;
      try {
        revCommit = revWalk.parseCommit(commitId);
      } catch (IncorrectObjectTypeException err) {
        throw new BadRequestException(err.getMessage());
      } catch (MissingObjectException err) {
        throw new ResourceConflictException(err.getMessage());
      }

      RefDatabase refDb = repo.getRefDatabase();
      List<Ref> refs = new ArrayList<>();
      for (String refName : refsNames) {
        try {
          Ref ref = refDb.exactRef(refName);
          if (ref != null) {
            refs.add(ref);
          }
        } catch (IOException e) {
        }
      }

      // Filter refs according to their visibility by the user
      refs = filterReadableRefs(project, refs, repo);
      return IncludedInResolver.resolve(repo, revWalk, revCommit, refs).stream()
          .map(Ref::getName)
          .collect(toImmutableList());
    }
  }

  /**
   * Filter readable refs according to the caller's refs visibility.
   *
   * @param project specific Gerrit project.
   * @param inputRefs a list of refs
   * @param repo repository opened for the Gerrit project.
   * @return list of visible refs to the caller
   */
  private List<Ref> filterReadableRefs(
      Project.NameKey project, List<Ref> inputRefs, Repository repo)
      throws PermissionBackendException {
    PermissionBackend.ForProject perm = permissionBackend.currentUser().project(project);
    return perm.filter(inputRefs, repo, RefFilterOptions.defaults()).stream().collect(toList());
  }
}
