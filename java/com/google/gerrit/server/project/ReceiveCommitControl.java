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

package com.google.gerrit.server.project;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.concurrent.TimeUnit;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;

/**
 * Access control for a commit entering Gerrit's commit-validation pipeline.
 *
 * <p>Owns the push-permission decisions that depend on the {@link RevCommit} being pushed: whether
 * the caller may forge the author, committer, or server identity, or upload a merge commit. Like
 * {@link CreateRefControl}, it combines Git object data with a {@link PermissionBackend.ForRef} and
 * keeps the decision in the project access-control layer rather than inside the commit validators.
 * The validators translate the results into {@code CommitValidationException}s and render any
 * user-facing messages.
 */
@Singleton
public class ReceiveCommitControl {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PersonIdent gerritIdent;

  @Inject
  ReceiveCommitControl(@GerritPersonIdent PersonIdent gerritIdent) {
    this.gerritIdent = gerritIdent;
  }

  /** Whether the user may push a commit whose author identity they do not own. */
  public boolean canForgeAuthor(
      PermissionBackend.ForRef forRef, IdentifiedUser user, RevCommit commit)
      throws PermissionBackendException {
    if (user.hasEmailAddress(commit.getAuthorIdent().getEmailAddress())) {
      return true;
    }
    return forRef.test(RefPermission.FORGE_AUTHOR);
  }

  /** Whether the user may push a commit whose committer identity they do not own. */
  public boolean canForgeCommitter(
      PermissionBackend.ForRef forRef, IdentifiedUser user, RevCommit commit)
      throws PermissionBackendException {
    if (user.hasEmailAddress(commit.getCommitterIdent().getEmailAddress())) {
      return true;
    }
    return forRef.test(RefPermission.FORGE_COMMITTER);
  }

  /** Whether the committer identity may be forged on this ref. */
  public boolean canForgeCommitter(PermissionBackend.ForRef forRef)
      throws PermissionBackendException {
    return forRef.test(RefPermission.FORGE_COMMITTER);
  }

  /**
   * Whether the user may push this commit if it is a merge; non-merges are always allowed.
   *
   * <p>Direct pushes are authorized by {@code Push Merge Commit} on the destination ref. For
   * backward compatibility a grant on the {@code refs/for/} review ref also still authorizes direct
   * pushes, but that fallback is deprecated and will be removed in a future release. Pushes for
   * review are authorized by the grant on the {@code refs/for/} review ref.
   */
  public boolean canUploadMerge(
      PermissionBackend.ForRef destRef,
      PermissionBackend.ForRef reviewRef,
      boolean directPush,
      RevCommit commit)
      throws PermissionBackendException {
    if (commit.getParentCount() <= 1) {
      return true;
    }
    if (directPush) {
      if (destRef.test(RefPermission.MERGE)) {
        return true;
      }
      if (reviewRef.test(RefPermission.MERGE)) {
        logger.atWarning().atMostEvery(1, TimeUnit.HOURS).log(
            "Direct merge push to %s was authorized only via the deprecated refs/for Push Merge"
                + " Commit fallback; grant Push Merge Commit on the destination ref instead. This"
                + " fallback will be removed in a future release.",
            destRef.resourcePath());
        return true;
      }
      return false;
    }
    return reviewRef.test(RefPermission.MERGE);
  }

  /**
   * Rejects amending a merge commit authored by the Gerrit server identity unless the caller may
   * forge the server identity. No-op for commits that are not Gerrit-authored merges.
   */
  public void checkAmendedGerritMerge(PermissionBackend.ForRef forRef, RevCommit commit)
      throws AuthException, PermissionBackendException {
    PersonIdent author = commit.getAuthorIdent();
    if (commit.getParentCount() > 1
        && author.getName().equals(gerritIdent.getName())
        && author.getEmailAddress().equals(gerritIdent.getEmailAddress())) {
      forRef.check(RefPermission.FORGE_SERVER);
    }
  }
}
