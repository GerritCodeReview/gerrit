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

import static com.google.gerrit.server.project.ProjectCache.noSuchProject;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.update.RetryHelper;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
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
  private final RetryHelper retryHelper;

  @Inject
  CreateRefControl(
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      Reachable reachable,
      RetryHelper retryHelper) {
    this.permissionBackend = permissionBackend;
    this.projectCache = projectCache;
    this.reachable = reachable;
    this.retryHelper = retryHelper;
  }

  /**
   * Checks whether the {@link CurrentUser} can create a new Git ref.
   *
   * @param user the user performing the operation
   * @param repo repository on which user want to create
   * @param destBranch the branch the new {@link RevObject} should be created on
   * @param object the object the user will start the reference with
   * @param sourceBranches the source ref from which the new ref is created from
   * @throws AuthException if creation is denied; the message explains the denial.
   * @throws UnprocessableEntityException if creation depends on an object or ref that is not
   *     visible
   * @throws PermissionBackendException on failure of permission checks.
   * @throws ResourceConflictException if the project state does not permit the operation
   */
  public void checkCreateRef(
      Provider<? extends CurrentUser> user,
      Repository repo,
      BranchNameKey destBranch,
      RevObject object,
      boolean forPush,
      BranchNameKey... sourceBranches)
      throws AuthException,
          PermissionBackendException,
          NoSuchProjectException,
          IOException,
          ResourceConflictException,
          UnprocessableEntityException {
    ProjectState ps =
        projectCache.get(destBranch.project()).orElseThrow(noSuchProject(destBranch.project()));
    ps.checkStatePermitsWrite();

    PermissionBackend.ForRef perm = permissionBackend.user(user.get()).ref(destBranch);
    if (object instanceof RevCommit) {
      perm.check(RefPermission.CREATE);
      if (sourceBranches.length == 0) {
        checkCreateCommit(user, repo, (RevCommit) object, ps.getNameKey(), perm, forPush);
      } else {
        List<Ref> sourceRefs = new ArrayList<>();
        for (BranchNameKey src : sourceBranches) {
          sourceRefs.add(repo.exactRef(src.branch()));
        }
        if (reachable.fromRefs(
            destBranch.project(),
            repo,
            (RevCommit) object,
            ImmutableList.copyOf(sourceRefs),
            Optional.of(user.get()))) {
          return;
        }
        // Don't expose existence of source refs or the object to the caller
        throw new UnprocessableEntityException(
            String.format(
                "Unable to resolve object '%s'. Check that the provided source branch(es) exist"
                    + " (%s), that you are permitted to read them, and that the object is reachable"
                    + " from the source(s).",
                object, Arrays.toString(sourceBranches)));
      }
      return;
    }
    if (object instanceof RevTag) {
      RevTag tag = (RevTag) object;
      try (RevWalk rw = new RevWalk(repo)) {
        rw.parseBody(tag);
      } catch (IOException e) {
        logger.atSevere().withCause(e).log(
            "RevWalk(%s) parsing %s:", destBranch.project(), tag.name());
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
        checkCreateCommit(user, repo, (RevCommit) target, ps.getNameKey(), perm, forPush);
      } else {
        checkCreateRef(user, repo, destBranch, target, forPush);
      }

      // If the tag has a PGP signature, allow a lower level of permission
      // than if it doesn't have a PGP signature.
      PermissionBackend.ForRef forRef = permissionBackend.user(user.get()).ref(destBranch);
      if (tag.getRawGpgSignature() != null) {
        forRef.check(RefPermission.CREATE_SIGNED_TAG);
      } else {
        forRef.check(RefPermission.CREATE_TAG);
      }
      return;
    }
    throw new AuthException(
        String.format(
            "Ref creation not allowed. Object %s is neither Commit or Tag.", object.getId()));
  }

  /**
   * Check if the user is allowed to create a new commit object if this creation would introduce a
   * new commit to the repository.
   */
  private void checkCreateCommit(
      Provider<? extends CurrentUser> user,
      Repository repo,
      RevCommit commit,
      Project.NameKey project,
      PermissionBackend.ForRef forRef,
      boolean forPush)
      throws PermissionBackendException, IOException, UnprocessableEntityException {
    try {
      // If the user has UPDATE (push) permission, they can set the ref to an arbitrary commit:
      //
      //  * if they don't have access, we don't advertise the data, and a conforming git client
      //  would send the object along with the push as outcome of the negotation.
      //  * a malicious client could try to send the update without sending the object. This
      //  is prevented by JGit's ConnectivityChecker (see receive.checkReferencedObjectsAreReachable
      //  to switch off this costly check).
      //
      // Thus, when using the git command-line client, we don't need to do extra checks for users
      // with push access.
      //
      // When using the REST API, there is no negotiation, and the target commit must already be on
      // the server, so we must check that the user can see that commit.
      if (forPush) {
        // We can only shortcut for UPDATE permission. Pushing a tag (CREATE_TAG, CREATE_SIGNED_TAG)
        // can also introduce new objects. While there may not be a confidentiality problem
        // (the caller supplies the data as documented above), the permission is for creating
        // tags to existing commits.
        forRef.check(RefPermission.UPDATE);
        return;
      }
    } catch (AuthException denied) {
      // Fall through to check reachability.
    }
    if (reachable.fromRefs(
        project,
        repo,
        commit,
        repo.getRefDatabase().getRefsByPrefix(Constants.R_HEADS, Constants.R_TAGS),
        Optional.of(user.get()))) {
      // If the user has no push permissions, check whether the object is
      // merged into a branch or tag readable by this user. If so, they are
      // not effectively "pushing" more objects, so they can create the ref
      // even if they don't have push permission.
      return;
    }

    // Previous check only catches normal branches. Try PatchSet refs too. If we can create refs,
    // we're not a replica, so we can always use the change index.
    List<ChangeData> changes =
        retryHelper
            .changeIndexQuery(
                "queryChangesByProjectCommitWithLimit1",
                q -> q.enforceVisibility(true).setLimit(1).byProjectCommit(project, commit))
            .call();
    if (!changes.isEmpty()) {
      return;
    }

    // Don't expose existence of the commit to the caller
    String msg =
        String.format(
            "Unable to resolve object '%s'. Check that the object exists on the server ", commit);
    if (forPush) {
      msg +=
          String.format(
              "or get %s permission to create new commit objects.",
              RefPermission.UPDATE.describeForException());
    } else {
      msg += "and is visible to you.";
    }
    throw new UnprocessableEntityException(msg);
  }
}
