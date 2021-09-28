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

import static java.util.Comparator.comparing;
import static java.util.stream.Collectors.toList;
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
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
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

  public Map<String, Set<String>> apply(
      Project.NameKey project, Set<String> commits, Set<String> refNames)
      throws ResourceConflictException, BadRequestException, IOException,
          PermissionBackendException, ResourceNotFoundException, AuthException {
    try (Repository repo = repoManager.openRepository(project)) {
      RefDatabase refDb = repo.getRefDatabase();
      Set<Ref> refs = new HashSet<>();
      for (String refName : refNames) {
        try {
          Ref ref = refDb.exactRef(refName);
          if (ref != null) {
            refs.add(ref);
          }
        } catch (IOException e) {
          // Ignore and continue to process rest of the refs so as to keep
          // the behavior similar to the ref not being visible to the user.
          // This will ensure that there is no information leak about the
          // ref when the ref is not visible to the user.
        }
      }
      Set<Ref> visibleRefs = filterReadableRefs(project, refs, repo);
      if (!visibleRefs.isEmpty()) {
        try (RevWalk revWalk = new RevWalk(repo)) {
          revWalk.setRetainBody(false);
          Set<RevCommit> revCommits = new HashSet<>();
          for (String commit : commits) {
            try {
              revCommits.add(revWalk.parseCommit(ObjectId.fromString(commit)));
            } catch (MissingObjectException
                | IncorrectObjectTypeException
                | IllegalArgumentException e) {
              // Ignore and continue to process the rest of the commits so as to keep
              // the behavior similar to the commit not being included in any of the
              // visible specified refs. This will ensure that there is no information
              // leak about the commit when the commit is not visible to the user.
            }
          }

          if (!revCommits.isEmpty()) {
            return commitsIncludedIn(revCommits, getSortedRefs(visibleRefs, revWalk), revWalk);
          }
        }
      }
    }
    return Collections.EMPTY_MAP;
  }

  private Map<String, Set<String>> commitsIncludedIn(
      Collection<RevCommit> commits, Collection<Ref> refs, RevWalk revWalk) throws IOException {
    Map<String, Set<String>> refsByCommit = new HashMap<>();
    for (RevCommit commit : commits) {
      List<Ref> matchingRefs = revWalk.getMergedInto(commit, refs);
      if (matchingRefs.size() > 0) {
        refsByCommit.put(
            commit.getName(), matchingRefs.stream().map(Ref::getName).collect(toSet()));
      }
    }
    return refsByCommit;
  }

  private List<Ref> getSortedRefs(Collection<Ref> refs, RevWalk revWalk) {
    return refs.stream()
        .sorted(
            comparing(
                ref -> {
                  try {
                    return revWalk.parseCommit(ref.getObjectId()).getCommitTime();
                  } catch (IOException e) {
                    // Ignore and continue to sort
                  }
                  return 0;
                }))
        .collect(toList());
  }

  /**
   * Filter readable refs according to the caller's refs visibility.
   *
   * @param project specific Gerrit project.
   * @param inputRefs a list of refs
   * @param repo repository opened for the Gerrit project.
   * @return set of visible refs to the caller
   */
  private Set<Ref> filterReadableRefs(Project.NameKey project, Set<Ref> inputRefs, Repository repo)
      throws PermissionBackendException {
    PermissionBackend.ForProject perm = permissionBackend.currentUser().project(project);
    return perm.filter(inputRefs, repo, RefFilterOptions.defaults()).stream().collect(toSet());
  }
}
