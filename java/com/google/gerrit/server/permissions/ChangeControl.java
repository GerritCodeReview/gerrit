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

import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.permissions.LabelPermission.ForUser.ON_BEHALF_OF;

import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.PermissionBackend.ForChange;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/** Access control management for a user accessing a single change. */
class ChangeControl {
  @Singleton
  static class Factory {
    private final ChangeData.Factory changeDataFactory;
    private final ChangeNotes.Factory notesFactory;

    @Inject
    Factory(ChangeData.Factory changeDataFactory, ChangeNotes.Factory notesFactory) {
      this.changeDataFactory = changeDataFactory;
      this.notesFactory = notesFactory;
    }

    ChangeControl create(
        RefControl refControl, ReviewDb db, Project.NameKey project, Change.Id changeId)
        throws OrmException {
      return create(refControl, notesFactory.create(db, project, changeId));
    }

    ChangeControl create(RefControl refControl, ChangeNotes notes) {
      return new ChangeControl(changeDataFactory, refControl, notes);
    }
  }

  private final ChangeData.Factory changeDataFactory;
  private final RefControl refControl;
  private final ChangeNotes notes;

  private ChangeControl(
      ChangeData.Factory changeDataFactory, RefControl refControl, ChangeNotes notes) {
    this.changeDataFactory = changeDataFactory;
    this.refControl = refControl;
    this.notes = notes;
  }

  ForChange asForChange(@Nullable ChangeData cd, @Nullable Provider<ReviewDb> db) {
    return new ForChangeImpl(cd, db);
  }

  private ChangeControl forUser(CurrentUser who) {
    if (getUser().equals(who)) {
      return this;
    }
    return new ChangeControl(changeDataFactory, refControl.forUser(who), notes);
  }

  private CurrentUser getUser() {
    return refControl.getUser();
  }

  private ProjectControl getProjectControl() {
    return refControl.getProjectControl();
  }

  private Change getChange() {
    return notes.getChange();
  }

  /** Can this user see this change? */
  private boolean isVisible(ReviewDb db, @Nullable ChangeData cd) throws OrmException {
    if (getChange().isPrivate() && !isPrivateVisible(db, cd)) {
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
    Account.Id currentAssignee = notes.getChange().getAssignee();
    if (currentAssignee != null && getUser().isIdentifiedUser()) {
      Account.Id id = getUser().getAccountId();
      return id.equals(currentAssignee);
    }
    return false;
  }

  /** Is this user a reviewer for the change? */
  private boolean isReviewer(ReviewDb db, @Nullable ChangeData cd) throws OrmException {
    if (getUser().isIdentifiedUser()) {
      cd = cd != null ? cd : changeDataFactory.create(db, notes);
      Collection<Account.Id> results = cd.reviewers().all();
      return results.contains(getUser().getAccountId());
    }
    return false;
  }

  /** Can this user edit the topic name? */
  private boolean canEditTopicName() {
    if (getChange().getStatus().isOpen()) {
      return isOwner() // owner (aka creator) of the change can edit topic
          || refControl.isOwner() // branch owner can edit topic
          || getProjectControl().isOwner() // project owner can edit topic
          || refControl.canPerform(
              Permission.EDIT_TOPIC_NAME) // user can edit topic on a specific ref
          || getProjectControl().isAdmin();
    }
    return refControl.canForceEditTopicName();
  }

  /** Can this user edit the description? */
  private boolean canEditDescription() {
    if (getChange().getStatus().isOpen()) {
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

  private boolean isPrivateVisible(ReviewDb db, ChangeData cd) throws OrmException {
    return isOwner()
        || isReviewer(db, cd)
        || refControl.canPerform(Permission.VIEW_PRIVATE_CHANGES)
        || getUser().isInternalUser();
  }

  private class ForChangeImpl extends ForChange {
    private ChangeData cd;
    private Map<String, PermissionRange> labels;
    private String resourcePath;

    ForChangeImpl(@Nullable ChangeData cd, @Nullable Provider<ReviewDb> db) {
      this.cd = cd;
      this.db = db;
    }

    private ReviewDb db() {
      if (db != null) {
        return db.get();
      } else if (cd != null) {
        return cd.db();
      } else {
        return null;
      }
    }

    private ChangeData changeData() {
      if (cd == null) {
        ReviewDb reviewDb = db();
        checkState(reviewDb != null, "need ReviewDb");
        cd = changeDataFactory.create(reviewDb, notes);
      }
      return cd;
    }

    @Override
    public CurrentUser user() {
      return getUser();
    }

    @Override
    public ForChange user(CurrentUser user) {
      return user().equals(user) ? this : forUser(user).asForChange(cd, db);
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
            return isVisible(db(), changeData());
          case ABANDON:
            return canAbandon();
          case DELETE:
            return (isOwner() && refControl.canPerform(Permission.DELETE_OWN_CHANGES))
                || getProjectControl().isAdmin();
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
          case SUBMIT:
            return refControl.canSubmit(isOwner());

          case REMOVE_REVIEWER:
          case SUBMIT_AS:
            return refControl.canPerform(perm.permissionName().get());
        }
      } catch (OrmException e) {
        throw new PermissionBackendException("unavailable", e);
      }
      throw new PermissionBackendException(perm + " unsupported");
    }

    private boolean can(LabelPermission perm) {
      return !label(perm.permissionName().get()).isEmpty();
    }

    private boolean can(LabelPermission.WithValue perm) {
      PermissionRange r = label(perm.permissionName().get());
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
}
