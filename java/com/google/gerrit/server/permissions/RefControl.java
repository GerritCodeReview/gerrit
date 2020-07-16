// Copyright (C) 2010 The Android Open Source Project
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

package com.google.gerrit.server.permissions;

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRange;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.PermissionRule.Action;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.logging.CallerFinder;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend.ForChange;
import com.google.gerrit.server.permissions.PermissionBackend.ForRef;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.util.MagicBranch;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/** Manages access control for Git references (aka branches, tags). */
class RefControl {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final ChangeData.Factory changeDataFactory;
  private final ProjectControl projectControl;
  private final String refName;

  /** All permissions that apply to this reference. */
  private final PermissionCollection relevant;

  private final CallerFinder callerFinder;

  // The next 4 members are cached canPerform() permissions.

  private Boolean owner;
  private Boolean canForgeAuthor;
  private Boolean canForgeCommitter;
  private Boolean isVisible;

  RefControl(
      ChangeData.Factory changeDataFactory,
      ProjectControl projectControl,
      String ref,
      PermissionCollection relevant) {
    this.changeDataFactory = changeDataFactory;
    this.projectControl = projectControl;
    this.refName = ref;
    this.relevant = relevant;
    this.callerFinder =
        CallerFinder.builder()
            .addTarget(PermissionBackend.class)
            .matchSubClasses(true)
            .matchInnerClasses(true)
            .skip(1)
            .build();
  }

  ProjectControl getProjectControl() {
    return projectControl;
  }

  CurrentUser getUser() {
    return projectControl.getUser();
  }

  /** Is this user a ref owner? */
  boolean isOwner() {
    if (owner == null) {
      if (canPerform(Permission.OWNER)) {
        owner = true;

      } else {
        owner = projectControl.isOwner();
      }
    }
    return owner;
  }

  /** Can this user see this reference exists? */
  boolean isVisible() {
    if (isVisible == null) {
      isVisible = getUser().isInternalUser() || canPerform(Permission.READ);
    }
    return isVisible;
  }

  /** @return true if this user can add a new patch set to this ref */
  boolean canAddPatchSet() {
    return projectControl
        .controlForRef(MagicBranch.NEW_CHANGE + refName)
        .canPerform(Permission.ADD_PATCH_SET);
  }

  /** @return true if this user can rebase changes on this ref */
  boolean canRebase() {
    return canPerform(Permission.REBASE);
  }

  /** @return true if this user can submit patch sets to this ref */
  boolean canSubmit(boolean isChangeOwner) {
    if (RefNames.REFS_CONFIG.equals(refName)) {
      // Always allow project owners to submit configuration changes.
      // Submitting configuration changes modifies the access control
      // rules. Allowing this to be done by a non-project-owner opens
      // a security hole enabling editing of access rules, and thus
      // granting of powers beyond submitting to the configuration.
      return projectControl.isOwner();
    }
    return canPerform(Permission.SUBMIT, isChangeOwner, false);
  }

  /** @return true if this user can force edit topic names. */
  boolean canForceEditTopicName() {
    return canPerform(Permission.EDIT_TOPIC_NAME, false, true);
  }

  /** @return true if this user can delete changes. */
  boolean canDeleteChanges(boolean isChangeOwner) {
    return canPerform(Permission.DELETE_CHANGES)
        || (isChangeOwner && canPerform(Permission.DELETE_OWN_CHANGES, isChangeOwner, false));
  }

  /** The range of permitted values associated with a label permission. */
  PermissionRange getRange(String permission) {
    return getRange(permission, false);
  }

  /** The range of permitted values associated with a label permission. */
  PermissionRange getRange(String permission, boolean isChangeOwner) {
    if (Permission.hasRange(permission)) {
      return toRange(permission, isChangeOwner);
    }
    return null;
  }

  /** True if the user has this permission. Works only for non labels. */
  boolean canPerform(String permissionName) {
    return canPerform(permissionName, false, false);
  }

  ForRef asForRef() {
    return new ForRefImpl();
  }

  private boolean canUpload() {
    return projectControl.controlForRef("refs/for/" + refName).canPerform(Permission.PUSH);
  }

  boolean canRevert() {
    return canPerform(Permission.REVERT);
  }

  /** @return true if this user can submit merge patch sets to this ref */
  private boolean canUploadMerges() {
    return projectControl.controlForRef("refs/for/" + refName).canPerform(Permission.PUSH_MERGE);
  }

  /** @return true if the user can update the reference as a fast-forward. */
  private boolean canUpdate() {
    if (RefNames.REFS_CONFIG.equals(refName) && !projectControl.isOwner()) {
      // Pushing requires being at least project owner, in addition to push.
      // Pushing configuration changes modifies the access control
      // rules. Allowing this to be done by a non-project-owner opens
      // a security hole enabling editing of access rules, and thus
      // granting of powers beyond pushing to the configuration.

      // On the AllProjects project the owner access right cannot be assigned,
      // this why for the AllProjects project we allow administrators to push
      // configuration changes if they have push without being project owner.
      if (!(projectControl.getProjectState().isAllProjects() && projectControl.isAdmin())) {
        return false;
      }
    }
    return canPerform(Permission.PUSH);
  }

  /** @return true if the user can rewind (force push) the reference. */
  private boolean canForceUpdate() {
    if (canPushWithForce()) {
      return true;
    }

    switch (getUser().getAccessPath()) {
      case GIT:
        return false;

      case REST_API:
      case SSH_COMMAND:
      case UNKNOWN:
      case WEB_BROWSER:
      default:
        return (isOwner() && !isBlocked(Permission.PUSH, false, true)) || projectControl.isAdmin();
    }
  }

  private boolean canPushWithForce() {
    if (RefNames.REFS_CONFIG.equals(refName) && !projectControl.isOwner()) {
      // Pushing requires being at least project owner, in addition to push.
      // Pushing configuration changes modifies the access control
      // rules. Allowing this to be done by a non-project-owner opens
      // a security hole enabling editing of access rules, and thus
      // granting of powers beyond pushing to the configuration.
      return false;
    }
    return canPerform(Permission.PUSH, false, true);
  }

  /**
   * Determines whether the user can delete the Git ref controlled by this object.
   *
   * @return {@code true} if the user specified can delete a Git ref.
   */
  private boolean canDelete() {
    switch (getUser().getAccessPath()) {
      case GIT:
        return canPushWithForce() || canPerform(Permission.DELETE);

      case REST_API:
      case SSH_COMMAND:
      case UNKNOWN:
      case WEB_BROWSER:
      default:
        return
        // We allow owner to delete refs even if they have no force-push rights. We forbid
        // it if force push is blocked, though. See commit 40bd5741026863c99bea13eb5384bd27855c5e1b
        (isOwner() && !isBlocked(Permission.PUSH, false, true))
            || canPushWithForce()
            || canPerform(Permission.DELETE)
            || projectControl.isAdmin();
    }
  }

  /** @return true if this user can forge the author line in a commit. */
  private boolean canForgeAuthor() {
    if (canForgeAuthor == null) {
      canForgeAuthor = canPerform(Permission.FORGE_AUTHOR);
    }
    return canForgeAuthor;
  }

  /** @return true if this user can forge the committer line in a commit. */
  private boolean canForgeCommitter() {
    if (canForgeCommitter == null) {
      canForgeCommitter = canPerform(Permission.FORGE_COMMITTER);
    }
    return canForgeCommitter;
  }

  /** @return true if this user can forge the server on the committer line. */
  private boolean canForgeGerritServerIdentity() {
    return canPerform(Permission.FORGE_SERVER);
  }

  private static boolean isAllow(PermissionRule pr, boolean withForce) {
    return pr.getAction() == Action.ALLOW && (pr.getForce() || !withForce);
  }

  private static boolean isBlock(PermissionRule pr, boolean withForce) {
    // BLOCK with force specified is a weaker rule than without.
    return pr.getAction() == Action.BLOCK && (!pr.getForce() || withForce);
  }

  private PermissionRange toRange(String permissionName, boolean isChangeOwner) {
    int blockAllowMin = Integer.MIN_VALUE, blockAllowMax = Integer.MAX_VALUE;

    projectLoop:
    for (List<Permission> ps : relevant.getBlockRules(permissionName)) {
      boolean blockFound = false;
      int projectBlockAllowMin = Integer.MIN_VALUE, projectBlockAllowMax = Integer.MAX_VALUE;

      for (Permission p : ps) {
        if (p.getExclusiveGroup()) {
          for (PermissionRule pr : p.getRules()) {
            if (pr.getAction() == Action.ALLOW && projectControl.match(pr, isChangeOwner)) {
              // exclusive override, usually for a more specific ref.
              continue projectLoop;
            }
          }
        }

        for (PermissionRule pr : p.getRules()) {
          if (pr.getAction() == Action.BLOCK && projectControl.match(pr, isChangeOwner)) {
            projectBlockAllowMin = pr.getMin() + 1;
            projectBlockAllowMax = pr.getMax() - 1;
            blockFound = true;
          }
        }

        if (blockFound) {
          for (PermissionRule pr : p.getRules()) {
            if (pr.getAction() == Action.ALLOW && projectControl.match(pr, isChangeOwner)) {
              projectBlockAllowMin = pr.getMin();
              projectBlockAllowMax = pr.getMax();
              break;
            }
          }
          break;
        }
      }

      blockAllowMin = Math.max(projectBlockAllowMin, blockAllowMin);
      blockAllowMax = Math.min(projectBlockAllowMax, blockAllowMax);
    }

    int voteMin = 0, voteMax = 0;
    for (PermissionRule pr : relevant.getAllowRules(permissionName)) {
      if (pr.getAction() == PermissionRule.Action.ALLOW
          && projectControl.match(pr, isChangeOwner)) {
        // For votes, contrary to normal permissions, we aggregate all applicable rules.
        voteMin = Math.min(voteMin, pr.getMin());
        voteMax = Math.max(voteMax, pr.getMax());
      }
    }

    return new PermissionRange(
        permissionName, Math.max(voteMin, blockAllowMin), Math.min(voteMax, blockAllowMax));
  }

  private boolean isBlocked(String permissionName, boolean isChangeOwner, boolean withForce) {
    // Permissions are ordered by (more general project, more specific ref). Because Permission
    // does not have back pointers, we can't tell what ref-pattern or project each permission comes
    // from.
    List<List<Permission>> downwardPerProject = relevant.getBlockRules(permissionName);

    projectLoop:
    for (List<Permission> projectRules : downwardPerProject) {
      boolean overrideFound = false;
      for (Permission p : projectRules) {
        // If this is an exclusive ALLOW, then block rules from the same project are ignored.
        if (p.getExclusiveGroup()) {
          for (PermissionRule pr : p.getRules()) {
            if (isAllow(pr, withForce) && projectControl.match(pr, isChangeOwner)) {
              overrideFound = true;
              break;
            }
          }
        }
        if (overrideFound) {
          // Found an exclusive override, nothing further to do in this project.
          continue projectLoop;
        }

        boolean blocked = false;
        for (PermissionRule pr : p.getRules()) {
          if (!withForce && pr.getForce()) {
            // force on block rule only applies to withForce permission.
            continue;
          }

          if (isBlock(pr, withForce) && projectControl.match(pr, isChangeOwner)) {
            blocked = true;
            break;
          }
        }

        if (blocked) {
          // ALLOW in the same AccessSection (ie. in the same Permission) overrides the BLOCK.
          for (PermissionRule pr : p.getRules()) {
            if (isAllow(pr, withForce) && projectControl.match(pr, isChangeOwner)) {
              blocked = false;
              break;
            }
          }
        }

        if (blocked) {
          return true;
        }
      }
    }

    return false;
  }

  /** True if the user has this permission. */
  private boolean canPerform(String permissionName, boolean isChangeOwner, boolean withForce) {
    if (isBlocked(permissionName, isChangeOwner, withForce)) {
      logger.atFine().log(
          "'%s' cannot perform '%s' with force=%s on project '%s' for ref '%s'"
              + " because this permission is blocked (caller: %s)",
          getUser().getLoggableName(),
          permissionName,
          withForce,
          projectControl.getProject().getName(),
          refName,
          callerFinder.findCallerLazy());
      return false;
    }

    for (PermissionRule pr : relevant.getAllowRules(permissionName)) {
      if (isAllow(pr, withForce) && projectControl.match(pr, isChangeOwner)) {
        logger.atFine().log(
            "'%s' can perform '%s' with force=%s on project '%s' for ref '%s' (caller: %s)",
            getUser().getLoggableName(),
            permissionName,
            withForce,
            projectControl.getProject().getName(),
            refName,
            callerFinder.findCallerLazy());
        return true;
      }
    }

    logger.atFine().log(
        "'%s' cannot perform '%s' with force=%s on project '%s' for ref '%s' (caller: %s)",
        getUser().getLoggableName(),
        permissionName,
        withForce,
        projectControl.getProject().getName(),
        refName,
        callerFinder.findCallerLazy());
    return false;
  }

  private class ForRefImpl extends ForRef {
    private String resourcePath;

    @Override
    public String resourcePath() {
      if (resourcePath == null) {
        resourcePath =
            String.format(
                "/projects/%s/+refs/%s", getProjectControl().getProjectState().getName(), refName);
      }
      return resourcePath;
    }

    @Override
    public ForChange change(ChangeData cd) {
      try {
        return getProjectControl().controlFor(cd).asForChange();
      } catch (StorageException e) {
        return FailedPermissionBackend.change("unavailable", e);
      }
    }

    @Override
    public ForChange change(ChangeNotes notes) {
      Project.NameKey project = getProjectControl().getProject().getNameKey();
      Change change = notes.getChange();
      checkArgument(
          project.equals(change.getProject()),
          "expected change in project %s, not %s",
          project,
          change.getProject());
      // Having ChangeNotes means it's OK to load values from NoteDb if needed.
      // ChangeData.Factory will allow lazyLoading
      return getProjectControl().controlFor(changeDataFactory.create(notes)).asForChange();
    }

    @Override
    public void check(RefPermission perm) throws AuthException, PermissionBackendException {
      if (!can(perm)) {
        PermissionDeniedException pde = new PermissionDeniedException(perm, refName);
        switch (perm) {
          case UPDATE:
            if (refName.equals(RefNames.REFS_CONFIG)) {
              pde.setAdvice(
                  "Configuration changes can only be pushed by project owners\n"
                      + "who also have 'Push' rights on "
                      + RefNames.REFS_CONFIG);
            } else {
              pde.setAdvice("To push into this reference you need 'Push' rights.");
            }
            break;
          case DELETE:
            pde.setAdvice(
                "You need 'Delete Reference' rights or 'Push' rights with the \n"
                    + "'Force Push' flag set to delete references.");
            break;
          case CREATE_CHANGE:
            // This is misleading in the default permission backend, since "create change" on a
            // branch is encoded as "push" on refs/for/DESTINATION.
            pde.setAdvice(
                "You need 'Create Change' rights to upload code review requests.\n"
                    + "Verify that you are pushing to the right branch.");
            break;
          case CREATE:
            pde.setAdvice("You need 'Create' rights to create new references.");
            break;
          case CREATE_SIGNED_TAG:
            pde.setAdvice("You need 'Create Signed Tag' rights to push a signed tag.");
            break;
          case CREATE_TAG:
            pde.setAdvice("You need 'Create Tag' rights to push a normal tag.");
            break;
          case FORCE_UPDATE:
            pde.setAdvice(
                "You need 'Push' rights with 'Force' flag set to do a non-fastforward push.");
            break;
          case FORGE_AUTHOR:
            pde.setAdvice(
                "You need 'Forge Author' rights to push commits with another user as author.");
            break;
          case FORGE_COMMITTER:
            pde.setAdvice(
                "You need 'Forge Committer' rights to push commits with another user as committer.");
            break;
          case FORGE_SERVER:
            pde.setAdvice(
                "You need 'Forge Server' rights to push merge commits authored by the server.");
            break;
          case MERGE:
            pde.setAdvice(
                "You need 'Push Merge' in addition to 'Push' rights to push merge commits.");
            break;

          case READ:
            pde.setAdvice("You need 'Read' rights to fetch or clone this ref.");
            break;

          case READ_CONFIG:
            pde.setAdvice("You need 'Read' rights on refs/meta/config to see the configuration.");
            break;
          case READ_PRIVATE_CHANGES:
            pde.setAdvice("You need 'Read Private Changes' to see private changes.");
            break;
          case SET_HEAD:
            pde.setAdvice("You need 'Set HEAD' rights to set the default branch.");
            break;
          case SKIP_VALIDATION:
            pde.setAdvice(
                "You need 'Forge Author', 'Forge Server', 'Forge Committer'\n"
                    + "and 'Push Merge' rights to skip validation.");
            break;
          case UPDATE_BY_SUBMIT:
            pde.setAdvice(
                "You need 'Submit' rights on refs/for/ to submit changes during change upload.");
            break;

          case WRITE_CONFIG:
            pde.setAdvice("You need 'Write' rights on refs/meta/config.");
            break;
        }
        throw pde;
      }
    }

    @Override
    public Set<RefPermission> test(Collection<RefPermission> permSet)
        throws PermissionBackendException {
      EnumSet<RefPermission> ok = EnumSet.noneOf(RefPermission.class);
      for (RefPermission perm : permSet) {
        if (can(perm)) {
          ok.add(perm);
        }
      }
      return ok;
    }

    @Override
    public BooleanCondition testCond(RefPermission perm) {
      return new PermissionBackendCondition.ForRef(this, perm, getUser());
    }

    private boolean can(RefPermission perm) throws PermissionBackendException {
      switch (perm) {
        case READ:
          return isVisible();
        case CREATE:
          // TODO This isn't an accurate test.
          return canPerform(refPermissionName(perm));
        case DELETE:
          return canDelete();
        case UPDATE:
          return canUpdate();
        case FORCE_UPDATE:
          return canForceUpdate();
        case SET_HEAD:
          return projectControl.isOwner();

        case FORGE_AUTHOR:
          return canForgeAuthor();
        case FORGE_COMMITTER:
          return canForgeCommitter();
        case FORGE_SERVER:
          return canForgeGerritServerIdentity();
        case MERGE:
          return canUploadMerges();

        case CREATE_CHANGE:
          return canUpload();

        case CREATE_TAG:
        case CREATE_SIGNED_TAG:
          return canPerform(refPermissionName(perm));

        case UPDATE_BY_SUBMIT:
          return projectControl.controlForRef(MagicBranch.NEW_CHANGE + refName).canSubmit(true);

        case READ_PRIVATE_CHANGES:
          return canPerform(Permission.VIEW_PRIVATE_CHANGES);

        case READ_CONFIG:
          return projectControl
              .controlForRef(RefNames.REFS_CONFIG)
              .canPerform(RefPermission.READ.name());
        case WRITE_CONFIG:
          return isOwner();

        case SKIP_VALIDATION:
          return canForgeAuthor()
              && canForgeCommitter()
              && canForgeGerritServerIdentity()
              && canUploadMerges();
      }
      throw new PermissionBackendException(perm + " unsupported");
    }
  }

  private static String refPermissionName(RefPermission refPermission) {
    // Within this class, it's programmer error to call this method on a
    // RefPermission that isn't associated with a permission name.
    return DefaultPermissionMappings.refPermissionName(refPermission)
        .orElseThrow(() -> new IllegalStateException("no name for " + refPermission));
  }
}
