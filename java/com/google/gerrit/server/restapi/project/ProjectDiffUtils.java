// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.restapi.project;

import com.google.common.collect.ImmutableSet;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Utility class for common diff operations in the project REST API. */
@Singleton
public class ProjectDiffUtils {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final int SHA1_LENGTH = 40;

  private final CommitsCollection commitsCollection;
  private final PermissionBackend permissionBackend;

  @Inject
  ProjectDiffUtils(CommitsCollection commitsCollection, PermissionBackend permissionBackend) {
    this.commitsCollection = commitsCollection;
    this.permissionBackend = permissionBackend;
  }

  public void validateSha1(String sha, String paramName) throws BadRequestException {
    if (sha == null || sha.isEmpty()) {
      throw new BadRequestException("Missing required parameter: " + paramName);
    }
    if (sha.length() != SHA1_LENGTH) {
      throw new BadRequestException(
          String.format(
              "Parameter '%s' must be a 40-character SHA1, got %d characters",
              paramName, sha.length()));
    }
    if (!sha.matches("[0-9a-fA-F]+")) {
      throw new BadRequestException(
          String.format("Parameter '%s' must be a valid hexadecimal SHA1", paramName));
    }
  }

  public ObjectId parseObjectId(String sha, String paramName) throws BadRequestException {
    try {
      return ObjectId.fromString(sha);
    } catch (IllegalArgumentException e) {
      throw new BadRequestException("Invalid SHA1 for " + paramName + ": " + sha, e);
    }
  }

  public RevCommit parseCommit(RevWalk rw, ObjectId commitId, String paramName)
      throws ResourceNotFoundException, IOException {
    try {
      return rw.parseCommit(commitId);
    } catch (MissingObjectException e) {
      throw new ResourceNotFoundException(
          String.format("Commit '%s' (%s) not found", paramName, commitId.name()), e);
    } catch (IncorrectObjectTypeException e) {
      throw new ResourceNotFoundException(
          String.format("Object '%s' (%s) is not a commit", paramName, commitId.name()), e);
    }
  }

  /**
   * Validates that both commits are visible to the caller, are in an ancestor/descendant
   * relationship, and that every intermediate commit on the path between them is visible.
   */
  public void validateAndVerifyPath(
      ProjectState projectState,
      Repository repo,
      RevWalk rw,
      RevCommit oldCommit,
      RevCommit newCommit)
      throws BadRequestException, ResourceNotFoundException, IOException {
    // Check visibility of both endpoints first so unauthorized callers cannot probe ancestry
    // or trigger reachability checks on invisible commits.
    if (!commitsCollection.canRead(projectState, repo, oldCommit)
        || !commitsCollection.canRead(projectState, repo, newCommit)) {
      throw new ResourceNotFoundException("Commit not visible");
    }

    if (oldCommit.equals(newCommit)) {
      return;
    }

    // Determine topological order using strict DAG ancestry checks, short-circuiting on the
    // common base -> descendant case.
    RevCommit descendant;
    RevCommit ancestor;
    if (isAncestor(rw, oldCommit, newCommit)) {
      ancestor = oldCommit;
      descendant = newCommit;
    } else if (isAncestor(rw, newCommit, oldCommit)) {
      ancestor = newCommit;
      descendant = oldCommit;
    } else {
      throw new BadRequestException(
          String.format(
              "Commits %s and %s are not in ancestor/descendant relationship",
              oldCommit.name(), newCommit.name()));
    }

    // Fast path: if descendant is a direct single-parent child of ancestor, there are no
    // intermediate commits to check (both endpoints were already checked above).
    if (descendant.getParentCount() == 1 && descendant.getParent(0).equals(ancestor)) {
      return;
    }

    // Walk commits between descendant and ancestor and check visibility.
    // Mark all visible non-change refs (e.g. refs/heads/*, refs/tags/*) as uninteresting so that
    // RevWalk does not traverse down already-submitted branch history (for example when a stack
    // contains merge commits). By DAG transitivity, any commit reachable from a visible non-change
    // ref is visible to the caller, whereas unmerged commits (such as open changes on
    // refs/changes/* or commits on hidden branches) remain interesting and are checked
    // individually.
    rw.reset();
    rw.markStart(descendant);
    rw.markUninteresting(ancestor);
    markVisibleRefsUninteresting(projectState, repo, rw);

    for (RevCommit commit : rw) {
      if (commit.equals(descendant)) {
        continue;
      }
      if (!commitsCollection.canRead(projectState, repo, commit)) {
        throw new ResourceNotFoundException("Commit not visible");
      }
    }
  }

  private boolean isAncestor(RevWalk rw, RevCommit ancestor, RevCommit descendant)
      throws IOException {
    if (ancestor.equals(descendant)) {
      return true;
    }
    for (RevCommit parent : descendant.getParents()) {
      if (parent.equals(ancestor)) {
        return true;
      }
    }
    return rw.isMergedInto(ancestor, descendant);
  }

  private void markVisibleRefsUninteresting(ProjectState projectState, Repository repo, RevWalk rw)
      throws IOException {
    try {
      // Exclude all Gerrit-internal refs (refs/changes/*, refs/cache-automerge/*,
      // refs/draft-comments/*, etc.). Auto-merge commits under refs/cache-automerge/* can have
      // private changes as parents, so marking them uninteresting would skip the per-commit
      // visibility check on those parents.
      List<Ref> nonInternalRefs =
          repo.getRefDatabase()
              .getRefsByPrefixWithExclusions(
                  RefDatabase.ALL, ImmutableSet.copyOf(RefNames.GERRIT_REFS));
      Collection<Ref> visibleRefs =
          permissionBackend
              .currentUser()
              .project(projectState.getNameKey())
              .filter(nonInternalRefs, repo, RefFilterOptions.defaults());
      for (Ref ref : visibleRefs) {
        if (ref.getObjectId() == null) {
          continue;
        }
        try {
          rw.markUninteresting(rw.parseCommit(ref.getObjectId()));
        } catch (IncorrectObjectTypeException notCommit) {
          // Tags can point to blobs or trees; ignore them.
          continue;
        } catch (MissingObjectException missingObject) {
          logger.atWarning().log(
              "Reference %s in %s points to dangling object %s",
              ref.getName(), projectState.getName(), ref.getObjectId());
          continue;
        }
      }
    } catch (PermissionBackendException e) {
      logger.atWarning().withCause(e).log(
          "Cannot filter visible refs in project %s; falling back to per-commit visibility check",
          projectState.getName());
    }
  }

  /** Maps {@link DiffNotAvailableException} to appropriate {@link RestApiException}. */
  public RestApiException mapDiffException(DiffNotAvailableException e) {
    Throwable cause = e.getCause();
    if (cause != null && !(cause instanceof NoMergeBaseException)) {
      cause = cause.getCause();
    }
    if (cause instanceof NoMergeBaseException) {
      return new ResourceConflictException(
          String.format("Cannot create auto merge commit: %s", e.getMessage()), e);
    }
    return new ResourceNotFoundException("Cannot compute diff: " + e.getMessage(), e);
  }
}
