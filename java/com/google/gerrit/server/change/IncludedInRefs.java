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
import static com.google.common.collect.ImmutableMap.toImmutableMap;
import static java.util.stream.Collectors.toSet;

import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  public Map<String, Collection<String>> apply(
      Project.NameKey project, List<String> commits, List<String> refNames)
      throws ResourceConflictException, BadRequestException, IOException,
          PermissionBackendException, ResourceNotFoundException, AuthException {
    try (Repository repo = repoManager.openRepository(project);
        RevWalk revWalk = new RevWalk(repo)) {
      RefDatabase refDb = repo.getRefDatabase();
      List<Ref> refs = new ArrayList<>();
      for (String refName : refNames) {
        try {
          Ref ref = refDb.exactRef(refName);
          if (ref != null) {
            refs.add(ref);
          }
        } catch (IOException e) {
        }
      }
      // Filter refs according to their visibility by the user
      Set<Ref> visibleRefs = filterReadableRefs(project, refs, repo);
      if (!visibleRefs.isEmpty()) {
        revWalk.setRetainBody(false);
        Set<RevCommit> revCommits = new HashSet<>();
        for (String commit : commits) {
          try {
            revCommits.add(revWalk.parseCommit(ObjectId.fromString(commit)));
          } catch (MissingObjectException
              | IncorrectObjectTypeException
              | IllegalArgumentException e) {
          }
        }

        if (!revCommits.isEmpty()) {
          return IncludedInResolver.resolve(repo, revWalk, revCommits, visibleRefs).entrySet()
              .stream()
              .filter((entry) -> !entry.getValue().isEmpty())
              .collect(
                  toImmutableMap(
                      entry -> entry.getKey().getName(),
                      entry ->
                          entry.getValue().stream().map(Ref::getName).collect(toImmutableList())));
        }
      }
    }
    return Collections.EMPTY_MAP;
  }

  /**
   * Filter readable refs according to the caller's refs visibility.
   *
   * @param project specific Gerrit project.
   * @param inputRefs a list of refs
   * @param repo repository opened for the Gerrit project.
   * @return set of visible refs to the caller
   */
  private Set<Ref> filterReadableRefs(Project.NameKey project, List<Ref> inputRefs, Repository repo)
      throws PermissionBackendException {
    PermissionBackend.ForProject perm = permissionBackend.currentUser().project(project);
    return perm.filter(inputRefs, repo, RefFilterOptions.defaults()).stream().collect(toSet());
  }
}
