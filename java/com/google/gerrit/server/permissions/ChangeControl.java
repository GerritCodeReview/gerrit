// Copyright (C) 2009 The Android Open Source Project
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

import static com.google.gerrit.server.permissions.DefaultPermissionMappings.labelPermissionName;
import static com.google.gerrit.server.permissions.LabelPermission.ForUser.ON_BEHALF_OF;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRange;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.conditions.BooleanCondition;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.PermissionBackend.ForChange;
import com.google.gerrit.server.query.change.ChangeData;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Access control management for a user accessing a single change. */
class ChangeControl {
  private final RefControl refControl;
  private final ChangeData changeData;

  ChangeControl(RefControl refControl, ChangeData changeData) {
    this.refControl = refControl;
    this.changeData = changeData;
  }

  ForChange asForChange() {
    return new ForChangeImpl();
  }

  private CurrentUser getUser() {
    return refControl.getUser();
  }

  private ProjectControl getProjectControl() {
    return refControl.getProjectControl();
  }

  private Change getChange() {
    return changeData.change();
  }

  /** Can this user see this change? */
  private boolean isVisible(ChangeData cd) {
    if (getChange().isPrivate() && !isPrivateVisible(cd)) {
      return false;
    }
    return refControl.isVisible();
  }

  /** Can this user abandon this change? */
  private boolean canAbandon() {
    return isOwner() // owner (aka creator) of the change can abandon
        || refControl.isOwner() // branch owner can abandon
        || getProjectControl().isOwner() // project owner can abandon
        || refControl.canPerform(Permission.ABANDON) // user can abandon a specific ref
        || getProjectControl().isAdmin();
  }

  /** Can this user rebase this change? */
  private boolean canRebase() {
    return (isOwner() || refControl.canSubmit(isOwner()) || refControl.canRebase())
        && refControl.asForRef().testOrFalse(RefPermission.CREATE_CHANGE);
  }

  /** Can this user restore this change? */
  private boolean canRestore() {
    // Anyone who can abandon the change can restore it, as long as they can create changes.
    return canAbandon() && refControl.asForRef().testOrFalse(RefPermission.CREATE_CHANGE);
  }

  /** Can this user revert this change? */
  private boolean canRevert() {
    return (refControl.canRevert())
        && refControl.asForRef().testOrFalse(RefPermission.CREATE_CHANGE);
  }

  /** The range of permitted values associated with a label permission. */
  private PermissionRange getRange(String permission) {
    return refControl.getRange(permission, isOwner());
  }

  /** Can this user add a patch set to this change? */
  private boolean canAddPatchSet() {
    if (!refControl.asForRef().testOrFalse(RefPermission.CREATE_CHANGE)) {
      return false;
    }
    if (isOwner()) {
      return true;
    }
    return refControl.canAddPatchSet();
  }

  /** Is this user the owner of the change? */
  private boolean isOwner() {
    if (getUser().isIdentifiedUser()) {
      Account.Id id = getUser().asIdentifiedUser().getAccountId();
      return id.equals(getChange().getOwner());
    }
    return false;
  }

  /** Is this user assigned to this change? */
  private boolean isAssignee() {
    Account.Id currentAssignee = getChange().getAssignee();
    if (currentAssignee != null && getUser().isIdentifiedUser()) {
      Account.Id id = getUser().getAccountId();
      return id.equals(currentAssignee);
    }
    return false;
  }

  /** Is this user a reviewer for the change? */
  private boolean isReviewer(ChangeData cd) {
    if (getUser().isIdentifiedUser()) {
      Collection<Account.Id> results = cd.reviewers().all();
      return results.contains(getUser().getAccountId());
    }
    return false;
  }

  /** Can this user edit the topic name? */
  private boolean canEditTopicName() {
    if (getChange().isNew()) {
      return isOwner() // owner (aka creator) of the change can edit topic
          || refControl.isOwner() // branch owner can edit topic
          || getProjectControl().isOwner() // project owner can edit topic
          || refControl.canPerform(
              Permission.EDIT_TOPIC_NAME) // user can edit topic on a specific ref
          || getProjectControl().isAdmin();
    }
    return refControl.canForceEditTopicName();
  }

  /** Can this user toggle WorkInProgress state? */
  private boolean canToggleWorkInProgressState() {
    return isOwner()
        || getProjectControl().isOwner()
        || refControl.canPerform(Permission.TOGGLE_WORK_IN_PROGRESS_STATE)
        || getProjectControl().isAdmin();
  }

  /** Can this user edit the description? */
  private boolean canEditDescription() {
    if (getChange().isNew()) {
      return isOwner() // owner (aka creator) of the change can edit desc
          || refControl.isOwner() // branch owner can edit desc
          || getProjectControl().isOwner() // project owner can edit desc
          || getProjectControl().isAdmin();
    }
    return false;
  }

  private boolean canEditAssignee() {
    return isOwner()
        || getProjectControl().isOwner()
        || refControl.canPerform(Permission.EDIT_ASSIGNEE)
        || isAssignee();
  }

  /** Can this user edit the hashtag name? */
  private boolean canEditHashtags() {
    return isOwner() // owner (aka creator) of the change can edit hashtags
        || refControl.isOwner() // branch owner can edit hashtags
        || getProjectControl().isOwner() // project owner can edit hashtags
        || refControl.canPerform(
            Permission.EDIT_HASHTAGS) // user can edit hashtag on a specific ref
        || getProjectControl().isAdmin();
  }

  private boolean isPrivateVisible(ChangeData cd) {
    return isOwner()
        || isReviewer(cd)
        || refControl.canPerform(Permission.VIEW_PRIVATE_CHANGES)
        || getUser().isInternalUser();
  }

  private class ForChangeImpl extends ForChange {
    private Map<String, PermissionRange> labels;
    private String resourcePath;

    private ForChangeImpl() {}

    private ChangeData changeData() {
      return changeData;
    }

    @Override
    public String resourcePath() {
      if (resourcePath == null) {
        resourcePath =
            String.format(
                "/projects/%s/+changes/%s",
                getProjectControl().getProjectState().getName(), changeData().getId().get());
      }
      return resourcePath;
    }

    @Override
    public void check(ChangePermissionOrLabel perm)
        throws AuthException, PermissionBackendException {
      if (!can(perm)) {
        throw new AuthException(perm.describeForException() + " not permitted");
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

    private boolean can(ChangePermissionOrLabel perm) throws PermissionBackendException {
      if (perm instanceof ChangePermission) {
        return can((ChangePermission) perm);
      } else if (perm instanceof LabelPermission) {
        return can((LabelPermission) perm);
      } else if (perm instanceof LabelPermission.WithValue) {
        return can((LabelPermission.WithValue) perm);
      }
      throw new PermissionBackendException(perm + " unsupported");
    }

    private boolean can(ChangePermission perm) throws PermissionBackendException {
      try {
        switch (perm) {
          case READ:
            return isVisible(changeData());
          case ABANDON:
            return canAbandon();
          case DELETE:
            return (getProjectControl().isAdmin() || (refControl.canDeleteChanges(isOwner())));
          case ADD_PATCH_SET:
            return canAddPatchSet();
          case EDIT_ASSIGNEE:
            return canEditAssignee();
          case EDIT_DESCRIPTION:
            return canEditDescription();
          case EDIT_HASHTAGS:
            return canEditHashtags();
          case EDIT_TOPIC_NAME:
            return canEditTopicName();
          case REBASE:
            return canRebase();
          case RESTORE:
            return canRestore();
          case REVERT:
            return canRevert();
          case SUBMIT:
            return refControl.canSubmit(isOwner());
          case TOGGLE_WORK_IN_PROGRESS_STATE:
            return canToggleWorkInProgressState();

          case REMOVE_REVIEWER:
          case SUBMIT_AS:
            return refControl.canPerform(changePermissionName(perm));
        }
      } catch (StorageException e) {
        throw new PermissionBackendException("unavailable", e);
      }
      throw new PermissionBackendException(perm + " unsupported");
    }

    private boolean can(LabelPermission perm) {
      return !label(labelPermissionName(perm)).isEmpty();
    }

    private boolean can(LabelPermission.WithValue perm) {
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
  }

  private static <T extends ChangePermissionOrLabel> Set<T> newSet(Collection<T> permSet) {
    if (permSet instanceof EnumSet) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Set<T> s = ((EnumSet) permSet).clone();
      s.clear();
      return s;
    }
    return Sets.newHashSetWithExpectedSize(permSet.size());
  }

  private static String changePermissionName(ChangePermission changePermission) {
    // Within this class, it's programmer error to call this method on a
    // ChangePermission that isn't associated with a permission name.
    return DefaultPermissionMappings.changePermissionName(changePermission)
        .orElseThrow(() -> new IllegalStateException("no name for " + changePermission));
  }
}
