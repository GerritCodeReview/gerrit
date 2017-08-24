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

import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
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

  @Inject
  CreateRefControl(PermissionBackend permissionBackend, ProjectCache projectCache) {
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
  }

  /**
   * Determines whether the user can create a new Git ref.
   *
   * @param repo repository on which user want to create
   * @param object the object the user will start the reference with
   * @param user the current identified user
   * @param branch the branch the new {@link RevObject} should be created on
   * @return {@code null} if the user specified can create a new Git ref, or a String describing why
   *     the creation is not allowed.
   * @throws PermissionBackendException on failure of permission checks
   */
  @Nullable
  public String canCreateRef(
      Repository repo, RevObject object, IdentifiedUser user, Branch.NameKey branch)
      throws PermissionBackendException, NoSuchProjectException, IOException {
    ProjectState ps = projectCache.checkedGet(branch.getParentKey());
    if (ps == null) {
      throw new NoSuchProjectException(branch.getParentKey());
    }
    if (!ps.getProject().getState().permitsWrite()) {
      return "project state does not permit write";
    }

    PermissionBackend.ForRef perm = permissionBackend.user(user).ref(branch);
    if (object instanceof RevCommit) {
      if (!testAuditLogged(perm, RefPermission.CREATE)) {
        return user.getAccountId() + " lacks permission: " + Permission.CREATE;
      }
      return canCreateCommit(repo, (RevCommit) object, ps, user, perm);
    } else if (object instanceof RevTag) {
      final RevTag tag = (RevTag) object;
      try (RevWalk rw = new RevWalk(repo)) {
        rw.parseBody(tag);
      } catch (IOException e) {
        String msg =
            String.format("RevWalk(%s) for pushing tag %s:", branch.getParentKey(), tag.name());
        log.error(msg, e);

        return "I/O exception for revwalk";
      }

      // If tagger is present, require it matches the user's email.
      //
      final PersonIdent tagger = tag.getTaggerIdent();
      if (tagger != null) {
        boolean valid;
        if (user.isIdentifiedUser()) {
          final String addr = tagger.getEmailAddress();
          valid = user.asIdentifiedUser().hasEmailAddress(addr);
        } else {
          valid = false;
        }
        if (!valid && !testAuditLogged(perm, RefPermission.FORGE_COMMITTER)) {
          return user.getAccountId() + " lacks permission: " + Permission.FORGE_COMMITTER;
        }
      }

      RevObject tagObject = tag.getObject();
      if (tagObject instanceof RevCommit) {
        String rejectReason = canCreateCommit(repo, (RevCommit) tagObject, ps, user, perm);
        if (rejectReason != null) {
          return rejectReason;
        }
      } else {
        String rejectReason = canCreateRef(repo, tagObject, user, branch);
        if (rejectReason != null) {
          return rejectReason;
        }
      }

      // If the tag has a PGP signature, allow a lower level of permission
      // than if it doesn't have a PGP signature.
      //
      RefControl refControl = ps.controlFor(user).controlForRef(branch);
      if (tag.getFullMessage().contains("-----BEGIN PGP SIGNATURE-----\n")) {
        return refControl.canPerform(Permission.CREATE_SIGNED_TAG)
            ? null
            : user.getAccountId() + " lacks permission: " + Permission.CREATE_SIGNED_TAG;
      }
      return refControl.canPerform(Permission.CREATE_TAG)
          ? null
          : user.getAccountId() + " lacks permission " + Permission.CREATE_TAG;
    }

    return null;
  }

  /**
   * Check if the user is allowed to create a new commit object if this introduces a new commit to
   * the project. If not allowed, returns a string describing why it's not allowed. The userId
   * argument is only used for the error message.
   */
  @Nullable
  private String canCreateCommit(
      Repository repo,
      RevCommit commit,
      ProjectState projectState,
      IdentifiedUser user,
      PermissionBackend.ForRef forRef)
      throws PermissionBackendException {
    if (projectState.controlFor(user).isReachableFromHeadsOrTags(repo, commit)) {
      // If the user has no push permissions, check whether the object is
      // merged into a branch or tag readable by this user. If so, they are
      // not effectively "pushing" more objects, so they can create the ref
      // even if they don't have push permission.
      return null;
    } else if (testAuditLogged(forRef, RefPermission.UPDATE)) {
      // If the user has push permissions, they can create the ref regardless
      // of whether they are pushing any new objects along with the create.
      return null;
    }
    return user.getAccountId()
        + " lacks permission "
        + Permission.PUSH
        + " for creating new commit object";
  }

  private boolean testAuditLogged(PermissionBackend.ForRef forRef, RefPermission p)
      throws PermissionBackendException {
    try {
      forRef.check(p);
    } catch (AuthException e) {
      return false;
    }
    return true;
  }
}
