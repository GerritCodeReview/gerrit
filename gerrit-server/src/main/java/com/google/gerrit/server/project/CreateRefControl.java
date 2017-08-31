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

import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.inject.Provider;
import java.io.IOException;
import javax.inject.Inject;
import javax.inject.Singleton;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages access control for creating Git references (aka branches, tags). */
@Singleton
public class CreateRefControl {
  private static final Logger log = LoggerFactory.getLogger(CreateRefControl.class);

  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final Provider<CurrentUser> user;

  @Inject
  CreateRefControl(
      PermissionBackend permissionBackend, ProjectCache projectCache, Provider<CurrentUser> user) {
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.user = user;
  }

  /**
   * Checks whether the {@link CurrentUser} can create a new Git ref.
   *
   * @param repo repository on which user want to create
   * @param branch the branch the new {@link RevObject} should be created on
   * @param object the object the user will start the reference with
   * @throws AuthException if creation is denied; the message explains the denial.
   * @throws PermissionBackendException on failure of permission checks.
   */
  public void checkCreateRef(Repository repo, Branch.NameKey branch, RevObject object)
      throws AuthException, PermissionBackendException, NoSuchProjectException, IOException {
    ProjectState ps = projectCache.checkedGet(branch.getParentKey());
    if (ps == null) {
      throw new NoSuchProjectException(branch.getParentKey());
    }
    if (!ps.getProject().getState().permitsWrite()) {
      throw new AuthException("project state does not permit write");
    }

    PermissionBackend.ForRef perm = permissionBackend.user(user).ref(branch);
    if (object instanceof RevCommit) {
      perm.check(RefPermission.CREATE);
      checkCreateCommit(repo, (RevCommit) object, ps, perm);
    } else if (object instanceof RevTag) {
      RevTag tag = (RevTag) object;
      try (RevWalk rw = new RevWalk(repo)) {
        rw.parseBody(tag);
      } catch (IOException e) {
        log.error(String.format("RevWalk(%s) parsing %s:", branch.getParentKey(), tag.name()), e);
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
        checkCreateCommit(repo, (RevCommit) target, ps, perm);
      } else {
        checkCreateRef(repo, branch, target);
      }

      // If the tag has a PGP signature, allow a lower level of permission
      // than if it doesn't have a PGP signature.
      RefControl refControl = ps.controlFor(user.get()).controlForRef(branch);
      if (tag.getFullMessage().contains("-----BEGIN PGP SIGNATURE-----\n")) {
        if (!refControl.canPerform(Permission.CREATE_SIGNED_TAG)) {
          throw new AuthException(Permission.CREATE_SIGNED_TAG + " not permitted");
        }
      } else if (!refControl.canPerform(Permission.CREATE_TAG)) {
        throw new AuthException(Permission.CREATE_TAG + " not permitted");
      }
    }
  }

  /**
   * Check if the user is allowed to create a new commit object if this introduces a new commit to
   * the project. If not allowed, returns a string describing why it's not allowed. The userId
   * argument is only used for the error message.
   */
  private void checkCreateCommit(
      Repository repo,
      RevCommit commit,
      ProjectState projectState,
      PermissionBackend.ForRef forRef)
      throws AuthException, PermissionBackendException {
    try {
      // If the user has update (push) permission, they can create the ref regardless
      // of whether they are pushing any new objects along with the create.
      forRef.check(RefPermission.UPDATE);
      return;
    } catch (AuthException denied) {
      // Fall through to check reachability.
    }

    if (projectState.controlFor(user.get()).isReachableFromHeadsOrTags(repo, commit)) {
      // If the user has no push permissions, check whether the object is
      // merged into a branch or tag readable by this user. If so, they are
      // not effectively "pushing" more objects, so they can create the ref
      // even if they don't have push permission.
      return;
    }

    throw new AuthException(
        String.format(
            "%s for creating new commit object not permitted",
            RefPermission.UPDATE.describeForException()));
  }
}
