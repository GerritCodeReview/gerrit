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
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import javax.inject.Inject;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Manages access control for creating Git references (aka branches, tags). */
public class CreateRefControl {
  private static final Logger log = LoggerFactory.getLogger(CreateRefControl.class);

  public interface Factory {
    CreateRefControl create(RefControl refControl);
  }

  private final RefControl refControl;
  private final PermissionBackend.ForRef permForRef;

  @Inject
  CreateRefControl(PermissionBackend permissionBackend, @Assisted RefControl refControl) {
    this.refControl = refControl;
    this.permForRef =
        permissionBackend
            .user(refControl.getUser())
            .project(refControl.getProjectControl().getProject().getNameKey())
            .ref(refControl.getRefName());
  }

  /**
   * Determines whether the user can create a new Git ref.
   *
   * @param repo repository on which user want to create
   * @param object the object the user will start the reference with.
   * @return {@code null} if the user specified can create a new Git ref, or a String describing why
   *     the creation is not allowed.
   * @throws PermissionBackendException on failure of permission checks
   */
  @Nullable
  public String canCreateRef(Repository repo, RevObject object) throws PermissionBackendException {
    if (!refControl.isProjectStatePermittingWrite()) {
      return "project state does not permit write";
    }

    CurrentUser user = refControl.getUser();

    String userId = user.isIdentifiedUser() ? "account " + user.getAccountId() : "anonymous user";

    if (object instanceof RevCommit) {
      if (!testAuditLogged(RefPermission.CREATE)) {
        return userId + " lacks permission: " + Permission.CREATE;
      }
      return canCreateCommit(repo, (RevCommit) object, userId);
    } else if (object instanceof RevTag) {
      final RevTag tag = (RevTag) object;
      try (RevWalk rw = new RevWalk(repo)) {
        rw.parseBody(tag);
      } catch (IOException e) {
        String msg =
            String.format(
                "RevWalk(%s) for pushing tag %s:",
                refControl.getProjectControl().getProject().getNameKey(), tag.name());
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
        if (!valid && !testAuditLogged(RefPermission.FORGE_COMMITTER)) {
          return userId + " lacks permission: " + Permission.FORGE_COMMITTER;
        }
      }

      RevObject tagObject = tag.getObject();
      if (tagObject instanceof RevCommit) {
        String rejectReason = canCreateCommit(repo, (RevCommit) tagObject, userId);
        if (rejectReason != null) {
          return rejectReason;
        }
      } else {
        String rejectReason = canCreateRef(repo, tagObject);
        if (rejectReason != null) {
          return rejectReason;
        }
      }

      // If the tag has a PGP signature, allow a lower level of permission
      // than if it doesn't have a PGP signature.
      //
      if (tag.getFullMessage().contains("-----BEGIN PGP SIGNATURE-----\n")) {
        return refControl.canPerform(Permission.CREATE_SIGNED_TAG)
            ? null
            : userId + " lacks permission: " + Permission.CREATE_SIGNED_TAG;
      }
      return refControl.canPerform(Permission.CREATE_TAG)
          ? null
          : userId + " lacks permission " + Permission.CREATE_TAG;
    }

    return null;
  }

  /**
   * Check if the user is allowed to create a new commit object if this introduces a new commit to
   * the project. If not allowed, returns a string describing why it's not allowed. The userId
   * argument is only used for the error message.
   */
  @Nullable
  private String canCreateCommit(Repository repo, RevCommit commit, String userId)
      throws PermissionBackendException {
    if (refControl.getProjectControl().isReachableFromHeadsOrTags(repo, commit)) {
      // If the user has no push permissions, check whether the object is
      // merged into a branch or tag readable by this user. If so, they are
      // not effectively "pushing" more objects, so they can create the ref
      // even if they don't have push permission.
      return null;
    } else if (testAuditLogged(RefPermission.UPDATE)) {
      // If the user has push permissions, they can create the ref regardless
      // of whether they are pushing any new objects along with the create.
      return null;
    }
    return userId + " lacks permission " + Permission.PUSH + " for creating new commit object";
  }

  private boolean testAuditLogged(RefPermission p) throws PermissionBackendException {
    try {
      permForRef.check(p);
    } catch (AuthException e) {
      return false;
    }
    return true;
  }
}
