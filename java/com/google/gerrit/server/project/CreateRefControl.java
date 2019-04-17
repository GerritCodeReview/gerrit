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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;

/** Manages access control for creating Git references (aka branches, tags). */
@Singleton
public class CreateRefControl {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final Reachable reachable;

  @Inject
  CreateRefControl(
      PermissionBackend permissionBackend, ProjectCache projectCache, Reachable reachable) {
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.reachable = reachable;
  }

  /**
   * Checks whether the {@link CurrentUser} can create a new Git ref.
   *
   * @param user the user performing the operation
   * @param repo repository on which user want to create
   * @param branch the branch the new {@link RevObject} should be created on
   * @param object the object the user will start the reference with
   * @throws AuthException if creation is denied; the message explains the denial.
   * @throws PermissionBackendException on failure of permission checks.
   * @throws ResourceConflictException if the project state does not permit the operation
   */
  public void checkCreateRef(
      Provider<? extends CurrentUser> user,
      Repository repo,
      Branch.NameKey branch,
      RevObject object)
      throws AuthException, PermissionBackendException, NoSuchProjectException, IOException,
          ResourceConflictException {
    ProjectState ps = projectCache.checkedGet(branch.project());
    if (ps == null) {
      throw new NoSuchProjectException(branch.project());
    }
    ps.checkStatePermitsWrite();

    PermissionBackend.ForRef perm = permissionBackend.user(user.get()).ref(branch);
    if (object instanceof RevCommit) {
      perm.check(RefPermission.CREATE);
      checkCreateCommit(repo, (RevCommit) object, ps.getNameKey(), perm);
    } else if (object instanceof RevTag) {
      RevTag tag = (RevTag) object;
      try (RevWalk rw = new RevWalk(repo)) {
        rw.parseBody(tag);
      } catch (IOException e) {
        logger.atSevere().withCause(e).log("RevWalk(%s) parsing %s:", branch.project(), tag.name());
        throw e;
      }

      // If tagger is present, require it matches the user's email.
      PersonIdent tagger = tag.getTaggerIdent();
      if (tagger != null
          && (!user.get().isIdentifiedUser()
              || !user.get().asIdentifiedUser().hasEmailAddress(tagger.getEmailAddress()))) {
        perm.check(RefPermission.FORGE_COMMITTER);
      }

      RevObject target = tag.getObject();
      if (target instanceof RevCommit) {
        checkCreateCommit(repo, (RevCommit) target, ps.getNameKey(), perm);
      } else {
        checkCreateRef(user, repo, branch, target);
      }

      // If the tag has a PGP signature, allow a lower level of permission
      // than if it doesn't have a PGP signature.
      PermissionBackend.ForRef forRef = permissionBackend.user(user.get()).ref(branch);
      if (tag.getFullMessage().contains("-----BEGIN PGP SIGNATURE-----\n")) {
        forRef.check(RefPermission.CREATE_SIGNED_TAG);
      } else {
        forRef.check(RefPermission.CREATE_TAG);
      }
    }
  }

  /**
   * Check if the user is allowed to create a new commit object if this creation would introduce a
   * new commit to the repository.
   */
  private void checkCreateCommit(
      Repository repo, RevCommit commit, Project.NameKey project, PermissionBackend.ForRef forRef)
      throws AuthException, PermissionBackendException, IOException {
    try {
      // If the user has update (push) permission, they can create the ref regardless
      // of whether they are pushing any new objects along with the create.
      forRef.check(RefPermission.UPDATE);
      return;
    } catch (AuthException denied) {
      // Fall through to check reachability.
    }
    if (reachable.fromRefs(
        project,
        repo,
        commit,
        repo.getRefDatabase().getRefsByPrefix(Constants.R_HEADS, Constants.R_TAGS))) {
      // If the user has no push permissions, check whether the object is
      // merged into a branch or tag readable by this user. If so, they are
      // not effectively "pushing" more objects, so they can create the ref
      // even if they don't have push permission.
      return;
    }

    AuthException e =
        new AuthException(
            String.format(
                "%s for creating new commit object not permitted",
                RefPermission.UPDATE.describeForException()));
    e.setAdvice(
        String.format(
            "use a SHA1 visible to you, or get %s permission on the ref",
            RefPermission.UPDATE.describeForException()));
    throw e;
  }
}
