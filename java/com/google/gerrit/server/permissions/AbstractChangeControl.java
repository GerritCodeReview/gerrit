// Copyright (C) 2025 The Android Open Source Project
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

import static com.google.gerrit.server.permissions.AbstractLabelPermission.ForUser.ON_BEHALF_OF;
import static com.google.gerrit.server.permissions.DefaultPermissionMappings.labelPermissionName;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRange;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend.ForChange;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Abstract access control management for a user accessing a single change.
 *
 * <p>Contains all logic that is common for checking permissions on an existing change and on a
 * change that is not created yet.
 */
abstract class AbstractChangeControl {
  protected final ProjectControl projectControl;
  protected final RefControl refControl;
  protected final boolean isNew;
  protected final PermissionBackend permissionBackend;

  /** Is this user the owner of the change? */
  protected final boolean isOwner;

  private Map<String, PermissionRange> labels;

  AbstractChangeControl(
      ProjectControl projectControl,
      RefControl refControl,
      PermissionBackend permissionBackend,
      boolean isNew,
      boolean isOwner) {
    this.projectControl = projectControl;
    this.refControl = refControl;
    this.permissionBackend = permissionBackend;
    this.isNew = isNew;
    this.isOwner = isOwner;
  }

  protected abstract ForChange asForChange();

  protected CurrentUser getUser() {
    return refControl.getUser();
  }

  protected boolean can(ChangePermissionOrLabel perm) throws PermissionBackendException {
    if (perm instanceof ChangePermission) {
      return can((ChangePermission) perm);
    } else if (perm instanceof AbstractLabelPermission) {
      return can((AbstractLabelPermission) perm);
    } else if (perm instanceof AbstractLabelPermission.WithValue) {
      return can((AbstractLabelPermission.WithValue) perm);
    }
    throw new PermissionBackendException(perm + " unsupported");
  }

  protected boolean can(ChangePermission perm) throws PermissionBackendException {
    try {
      return switch (perm) {
        case READ -> isVisible();
        case ABANDON -> canAbandon();
        case DELETE -> projectControl.isAdmin() || refControl.canDeleteChanges(isOwner);
        case ADD_PATCH_SET -> canAddPatchSet();
        case EDIT_DESCRIPTION -> canEditDescription();
        case EDIT_HASHTAGS -> canEditHashtags();
        case EDIT_CUSTOM_KEYED_VALUES -> canEditCustomKeyedValues();
        case EDIT_TOPIC_NAME -> canEditTopicName();
        case REBASE -> canRebase();
        case REBASE_ON_BEHALF_OF_UPLOADER -> canRebaseOnBehalfOfUploader();
        case RESTORE -> canRestore();
        case REVERT -> canRevert();
        case SUBMIT -> refControl.canSubmit(isOwner);
        case TOGGLE_WORK_IN_PROGRESS_STATE -> canToggleWorkInProgressState();
        case REMOVE_REVIEWER -> refControl.canPerform(changePermissionName(perm));
        case SUBMIT_AS ->
            permissionBackend.user(getUser()).test(GlobalPermission.RUN_AS)
                || refControl.canPerform(changePermissionName(perm));
      };
    } catch (StorageException e) {
      throw new PermissionBackendException("unavailable", e);
    }
  }

  /** Can this user see this change? */
  protected boolean isVisible() {
    // Does the user have READ permission on the destination?
    return refControl.asForRef().testOrFalse(RefPermission.READ);
  }

  /** Can this user abandon this change? */
  private boolean canAbandon() {
    return isOwner // owner (aka creator) of the change can abandon
        || refControl.isOwner() // branch owner can abandon
        || projectControl.isOwner() // project owner can abandon
        || refControl.canPerform(Permission.ABANDON) // user can abandon a specific ref
        || projectControl.isAdmin();
  }

  /** Can this user add a patch set to this change? */
  private boolean canAddPatchSet() {
    if (!refControl.asForRef().testOrFalse(RefPermission.CREATE_CHANGE)) {
      return false;
    }
    if (isOwner) {
      return true;
    }
    return refControl.canAddPatchSet();
  }

  /** Can this user edit the topic name? */
  private boolean canEditTopicName() {
    if (isNew) {
      return isOwner // owner (aka creator) of the change can edit topic
          || refControl.isOwner() // branch owner can edit topic
          || projectControl.isOwner() // project owner can edit topic
          || refControl.canPerform(
              Permission.EDIT_TOPIC_NAME) // user can edit topic on a specific ref
          || projectControl.isAdmin();
    }
    return refControl.canForceEditTopicName(isOwner);
  }

  /** Can this user edit the description? */
  private boolean canEditDescription() {
    if (isNew) {
      return isOwner // owner (aka creator) of the change can edit desc
          || refControl.isOwner() // branch owner can edit desc
          || projectControl.isOwner() // project owner can edit desc
          || projectControl.isAdmin();
    }
    return false;
  }

  /** Can this user edit the hashtag name? */
  private boolean canEditHashtags() {
    return isOwner // owner (aka creator) of the change can edit hashtags
        || refControl.isOwner() // branch owner can edit hashtags
        || projectControl.isOwner() // project owner can edit hashtags
        || refControl.canPerform(
            Permission.EDIT_HASHTAGS) // user can edit hashtag on a specific ref
        || projectControl.isAdmin();
  }

  /** Can this user edit the custom keyed values? */
  private boolean canEditCustomKeyedValues() {
    return isOwner // owner (aka creator) of the change can edit custom keyed values
        || projectControl.isAdmin();
  }

  /** Can this user rebase this change? */
  private boolean canRebase() {
    return (isOwner || refControl.canSubmit(isOwner) || refControl.canRebase())
        && refControl.asForRef().testOrFalse(RefPermission.CREATE_CHANGE);
  }

  /**
   * Can this user rebase this change on behalf of the uploader?
   *
   * <p>This only checks the permissions of the rebaser (aka the impersonating user).
   *
   * <p>In addition rebase on behalf of the uploader requires the uploader (aka the impersonated
   * user) to have permissions to create the new patch set. These permissions need to be checked
   * separately.
   */
  private boolean canRebaseOnBehalfOfUploader() {
    return (isOwner || refControl.canSubmit(isOwner) || refControl.canRebase());
  }

  /** Can this user restore this change? */
  private boolean canRestore() {
    // Anyone who can abandon the change can restore it, as long as they can create changes.
    return canAbandon() && refControl.asForRef().testOrFalse(RefPermission.CREATE_CHANGE);
  }

  /** Can this user revert this change? */
  private boolean canRevert() {
    return refControl.canRevert() && refControl.asForRef().testOrFalse(RefPermission.CREATE_CHANGE);
  }

  /** Can this user toggle WorkInProgress state? */
  private boolean canToggleWorkInProgressState() {
    return isOwner
        || projectControl.isOwner()
        || refControl.canPerform(Permission.TOGGLE_WORK_IN_PROGRESS_STATE)
        || projectControl.isAdmin();
  }

  private boolean can(AbstractLabelPermission perm) {
    return !label(labelPermissionName(perm)).isEmpty();
  }

  private boolean can(AbstractLabelPermission.WithValue perm) throws PermissionBackendException {
    if (perm.forUser() == ON_BEHALF_OF
        && permissionBackend.user(getUser()).test(GlobalPermission.RUN_AS)) {
      return true;
    }
    PermissionRange r = label(labelPermissionName(perm));
    if (perm.forUser() == ON_BEHALF_OF && r.isEmpty()) {
      return false;
    }
    return r.contains(perm.value());
  }

  private PermissionRange label(String permission) {
    if (labels == null) {
      labels = Maps.newHashMapWithExpectedSize(4);
    }
    PermissionRange r = labels.get(permission);
    if (r == null) {
      r = getRange(permission);
      labels.put(permission, r);
    }
    return r;
  }

  /** The range of permitted values associated with a label permission. */
  private PermissionRange getRange(String permission) {
    return refControl.getRange(permission, isOwner);
  }

  protected class ForChangeImpl extends ForChange {
    @Nullable private final Change.Id changeId;

    private String resourcePath;

    protected ForChangeImpl(@Nullable Change.Id changeId) {
      this.changeId = changeId;
    }

    @Override
    public String resourcePath() {
      if (resourcePath == null) {
        resourcePath =
            String.format(
                "/projects/%s/+changes/%s",
                projectControl.getProjectState().getName(), changeId != null ? changeId.get() : 0);
      }
      return resourcePath;
    }

    @Override
    public void check(ChangePermissionOrLabel perm)
        throws AuthException, PermissionBackendException {
      if (!can(perm)) {
        throw new AuthException(
            perm.describeForException()
                + " not permitted"
                + perm.hintForException().map(hint -> " (" + hint + ")").orElse(""));
      }
    }

    @Override
    public <T extends ChangePermissionOrLabel> Set<T> test(Collection<T> permSet)
        throws PermissionBackendException {
      Set<T> ok = newSet(permSet);
      for (T perm : permSet) {
        if (can(perm)) {
          ok.add(perm);
        }
      }
      return ok;
    }

    @Override
    public BooleanCondition testCond(ChangePermissionOrLabel perm) {
      return new PermissionBackendCondition.ForChange(this, perm, getUser());
    }
  }

  protected static <T extends ChangePermissionOrLabel> Set<T> newSet(Collection<T> permSet) {
    if (permSet instanceof EnumSet) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Set<T> s = ((EnumSet) permSet).clone();
      s.clear();
      return s;
    }
    return Sets.newHashSetWithExpectedSize(permSet.size());
  }

  protected static String changePermissionName(ChangePermission changePermission) {
    // Within this class, it's programmer error to call this method on a
    // ChangePermission that isn't associated with a permission name.
    return DefaultPermissionMappings.changePermissionName(changePermission)
        .orElseThrow(() -> new IllegalStateException("no name for " + changePermission));
  }
}
