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

import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.patch.DiffNotAvailableException;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

/** Utility class for common diff operations in the project REST API. */
@Singleton
public class ProjectDiffUtils {
  private static final int SHA1_LENGTH = 40;

  private final CommitsCollection commitsCollection;

  @Inject
  ProjectDiffUtils(CommitsCollection commitsCollection) {
    this.commitsCollection = commitsCollection;
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

  public void validateAncestorRelationship(RevWalk rw, RevCommit oldCommit, RevCommit newCommit)
      throws BadRequestException, IOException {
    boolean oldIsAncestorOfNew = rw.isMergedInto(oldCommit, newCommit);
    boolean newIsAncestorOfOld = rw.isMergedInto(newCommit, oldCommit);

    if (!oldIsAncestorOfNew && !newIsAncestorOfOld) {
      throw new BadRequestException(
          String.format(
              "Commits %s and %s are not in ancestor/descendant relationship",
              oldCommit.name(), newCommit.name()));
    }
  }

  public void verifyPathVisibility(
      ProjectState projectState,
      Repository repo,
      RevWalk rw,
      RevCommit oldCommit,
      RevCommit newCommit)
      throws ResourceNotFoundException, IOException {
    // Check visibility of both endpoints
    if (!commitsCollection.canRead(projectState, repo, oldCommit)
        || !commitsCollection.canRead(projectState, repo, newCommit)) {
      throw new ResourceNotFoundException("Commit not visible");
    }

    // Determine topological order to walk from descendant to ancestor
    rw.reset();
    boolean oldIsAncestorOfNew = rw.isMergedInto(oldCommit, newCommit);
    boolean newIsAncestorOfOld = rw.isMergedInto(newCommit, oldCommit);

    RevCommit descendant;
    RevCommit ancestor;
    if (oldIsAncestorOfNew) {
      descendant = newCommit;
      ancestor = oldCommit;
    } else if (newIsAncestorOfOld) {
      descendant = oldCommit;
      ancestor = newCommit;
    } else {
      // Commits are not related, visibility check covers endpoints but no path exists.
      // This is usually caught by validateAncestorRelationship.
      return;
    }

    // Walk all commits between descendant and ancestor and check visibility
    rw.reset();
    rw.markStart(descendant);
    rw.markUninteresting(ancestor);

    for (RevCommit commit : rw) {
      if (!commitsCollection.canRead(projectState, repo, commit)) {
        throw new ResourceNotFoundException("Commit not visible");
      }
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
