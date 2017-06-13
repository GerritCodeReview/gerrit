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

package com.google.gerrit.server.project;

import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.server.permissions.LabelPermission.ForUser.ON_BEHALF_OF;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.RefConfigSection;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.ChangePermissionOrLabel;
import com.google.gerrit.server.permissions.LabelPermission;
import com.google.gerrit.server.permissions.PermissionBackend.ForChange;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Access control management for a user accessing a single change. */
public class ChangeControl {
  @Singleton
  public static class GenericFactory {
    private final ProjectControl.GenericFactory projectControl;
    private final ChangeNotes.Factory notesFactory;

    @Inject
    GenericFactory(ProjectControl.GenericFactory p, ChangeNotes.Factory n) {
      projectControl = p;
      notesFactory = n;
    }

    public ChangeControl controlFor(
        ReviewDb db, Project.NameKey project, Change.Id changeId, CurrentUser user)
        throws OrmException {
      return controlFor(notesFactory.create(db, project, changeId), user);
    }

    public ChangeControl controlFor(ReviewDb db, Change change, CurrentUser user)
        throws OrmException {
      final Project.NameKey projectKey = change.getProject();
      try {
        return projectControl.controlFor(projectKey, user).controlFor(db, change);
      } catch (NoSuchProjectException e) {
        throw new NoSuchChangeException(change.getId(), e);
      } catch (IOException e) {
        // TODO: propagate this exception
        throw new NoSuchChangeException(change.getId(), e);
      }
    }

    public ChangeControl controlFor(ChangeNotes notes, CurrentUser user)
        throws NoSuchChangeException {
      try {
        return projectControl.controlFor(notes.getProjectName(), user).controlFor(notes);
      } catch (NoSuchProjectException | IOException e) {
        throw new NoSuchChangeException(notes.getChangeId(), e);
      }
    }

    public ChangeControl validateFor(ReviewDb db, Change.Id changeId, CurrentUser user)
        throws OrmException {
      return validateFor(db, notesFactory.createChecked(changeId), user);
    }

    public ChangeControl validateFor(ReviewDb db, ChangeNotes notes, CurrentUser user)
        throws OrmException {
      ChangeControl c = controlFor(notes, user);
      if (!c.isVisible(db)) {
        throw new NoSuchChangeException(c.getId());
      }
      return c;
    }
  }

  @Singleton
  public static class Factory {
    private final ChangeData.Factory changeDataFactory;
    private final ChangeNotes.Factory notesFactory;
    private final ApprovalsUtil approvalsUtil;
    private final PatchSetUtil patchSetUtil;

    @Inject
    Factory(
        ChangeData.Factory changeDataFactory,
        ChangeNotes.Factory notesFactory,
        ApprovalsUtil approvalsUtil,
        PatchSetUtil patchSetUtil) {
      this.changeDataFactory = changeDataFactory;
      this.notesFactory = notesFactory;
      this.approvalsUtil = approvalsUtil;
      this.patchSetUtil = patchSetUtil;
    }

    ChangeControl create(
        RefControl refControl, ReviewDb db, Project.NameKey project, Change.Id changeId)
        throws OrmException {
      return create(refControl, notesFactory.create(db, project, changeId));
    }

    /**
     * Create a change control for a change that was loaded from index. This method should only be
     * used when database access is harmful and potentially stale data from the index is acceptable.
     *
     * @param refControl ref control
     * @param change change loaded from secondary index
     * @return change control
     */
    ChangeControl createForIndexedChange(RefControl refControl, Change change) {
      return create(refControl, notesFactory.createFromIndexedChange(change));
    }

    ChangeControl create(RefControl refControl, ChangeNotes notes) {
      return new ChangeControl(changeDataFactory, approvalsUtil, refControl, notes, patchSetUtil);
    }
  }

  private final ChangeData.Factory changeDataFactory;
  private final ApprovalsUtil approvalsUtil;
  private final RefControl refControl;
  private final ChangeNotes notes;
  private final PatchSetUtil patchSetUtil;

  ChangeControl(
      ChangeData.Factory changeDataFactory,
      ApprovalsUtil approvalsUtil,
      RefControl refControl,
      ChangeNotes notes,
      PatchSetUtil patchSetUtil) {
    this.changeDataFactory = changeDataFactory;
    this.approvalsUtil = approvalsUtil;
    this.refControl = refControl;
    this.notes = notes;
    this.patchSetUtil = patchSetUtil;
  }

  public ChangeControl forUser(CurrentUser who) {
    if (getUser().equals(who)) {
      return this;
    }
    return new ChangeControl(
        changeDataFactory, approvalsUtil, getRefControl().forUser(who), notes, patchSetUtil);
  }

  public RefControl getRefControl() {
    return refControl;
  }

  public CurrentUser getUser() {
    return getRefControl().getUser();
  }

  public ProjectControl getProjectControl() {
    return getRefControl().getProjectControl();
  }

  public Project getProject() {
    return getProjectControl().getProject();
  }

  public Change.Id getId() {
    return notes.getChangeId();
  }

  public Change getChange() {
    return notes.getChange();
  }

  public ChangeNotes getNotes() {
    return notes;
  }

  /** Can this user see this change? */
  public boolean isVisible(ReviewDb db) throws OrmException {
    return isVisible(db, null);
  }

  /** Can this user see this change? */
  public boolean isVisible(ReviewDb db, @Nullable ChangeData cd) throws OrmException {
    if (getChange().isPrivate() && !isPrivateVisible(db, cd)) {
      return false;
    }
    if (getChange().getStatus() == Change.Status.DRAFT && !isDraftVisible(db, cd)) {
      return false;
    }
    return getRefControl().isVisible();
  }

  /** Can this user see the given patchset? */
  public boolean isPatchVisible(PatchSet ps, ReviewDb db) throws OrmException {
    if (ps != null && ps.isDraft() && !isDraftVisible(db, null)) {
      return false;
    }
    return isVisible(db);
  }

  /** Can this user see the given patchset? */
  public boolean isPatchVisible(PatchSet ps, ChangeData cd) throws OrmException {
    checkArgument(
        cd.getId().equals(ps.getId().getParentKey()), "%s not for change %s", ps, cd.getId());
    if (ps.isDraft() && !isDraftVisible(cd.db(), cd)) {
      return false;
    }
    return isVisible(cd.db());
  }

  /** Can this user abandon this change? */
  private boolean canAbandon(ReviewDb db) throws OrmException {
    return (isOwner() // owner (aka creator) of the change can abandon
            || getRefControl().isOwner() // branch owner can abandon
            || getProjectControl().isOwner() // project owner can abandon
            || getUser().getCapabilities().isAdmin_DoNotUse() // site administers are god
            || getRefControl().canAbandon() // user can abandon a specific ref
        )
        && !isPatchSetLocked(db);
  }

  /** Can this user publish this draft change or any draft patch set of this change? */
  public boolean canPublish(ReviewDb db) throws OrmException {
    return (isOwner() || getRefControl().canPublishDrafts()) && isVisible(db);
  }

  /** Can this user delete this change or any patch set of this change? */
  public boolean canDelete(ReviewDb db, Change.Status status) throws OrmException {
    if (!isVisible(db)) {
      return false;
    }

    switch (status) {
      case DRAFT:
        return isOwner()
            || getRefControl().canDeleteDrafts()
            || getUser().getCapabilities().isAdmin_DoNotUse();
      case NEW:
      case ABANDONED:
        return (isOwner() && getRefControl().canDeleteOwnChanges())
            || getUser().getCapabilities().isAdmin_DoNotUse();
      case MERGED:
      default:
        return false;
    }
  }

  /** Can this user rebase this change? */
  private boolean canRebase(ReviewDb db) throws OrmException {
    return (isOwner() || getRefControl().canSubmit(isOwner()) || getRefControl().canRebase())
        && refControl.asForRef().testOrFalse(RefPermission.CREATE_CHANGE)
        && !isPatchSetLocked(db);
  }

  /** Can this user restore this change? */
  private boolean canRestore(ReviewDb db) throws OrmException {
    // Anyone who can abandon the change can restore it, as long as they can create changes.
    return canAbandon(db) && refControl.asForRef().testOrFalse(RefPermission.CREATE_CHANGE);
  }

  /** All available label types for this change. */
  public LabelTypes getLabelTypes() {
    String destBranch = getChange().getDest().get();
    List<LabelType> all = getProjectControl().getLabelTypes().getLabelTypes();

    List<LabelType> r = Lists.newArrayListWithCapacity(all.size());
    for (LabelType l : all) {
      List<String> refs = l.getRefPatterns();
      if (refs == null) {
        r.add(l);
      } else {
        for (String refPattern : refs) {
          if (RefConfigSection.isValid(refPattern) && match(destBranch, refPattern)) {
            r.add(l);
            break;
          }
        }
      }
    }

    return new LabelTypes(r);
  }

  /** All value ranges of any allowed label permission. */
  public List<PermissionRange> getLabelRanges() {
    return getRefControl().getLabelRanges(isOwner());
  }

  /** The range of permitted values associated with a label permission. */
  public PermissionRange getRange(String permission) {
    return getRefControl().getRange(permission, isOwner());
  }

  /** Can this user add a patch set to this change? */
  private boolean canAddPatchSet(ReviewDb db) throws OrmException {
    if (!refControl.asForRef().testOrFalse(RefPermission.CREATE_CHANGE)
        || isPatchSetLocked(db)
        || !isPatchVisible(patchSetUtil.current(db, notes), db)) {
      return false;
    }
    if (isOwner()) {
      return true;
    }
    return getRefControl().canAddPatchSet();
  }

  /** Is the current patch set locked against state changes? */
  public boolean isPatchSetLocked(ReviewDb db) throws OrmException {
    if (getChange().getStatus() == Change.Status.MERGED) {
      return false;
    }

    for (PatchSetApproval ap :
        approvalsUtil.byPatchSet(db, this, getChange().currentPatchSetId())) {
      LabelType type = getLabelTypes().byLabel(ap.getLabel());
      if (type != null
          && ap.getValue() == 1
          && type.getFunctionName().equalsIgnoreCase("PatchSetLock")) {
        return true;
      }
    }
    return false;
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
      Collection<Account.Id> results = changeData(db, cd).reviewers().all();
      return results.contains(getUser().getAccountId());
    }
    return false;
  }

  /** @return true if the user is allowed to remove this reviewer. */
  public boolean canRemoveReviewer(PatchSetApproval approval) {
    return canRemoveReviewer(approval.getAccountId(), approval.getValue());
  }

  public boolean canRemoveReviewer(Account.Id reviewer, int value) {
    if (getChange().getStatus().isOpen()) {
      // A user can always remove themselves.
      //
      if (getUser().isIdentifiedUser()) {
        if (getUser().getAccountId().equals(reviewer)) {
          return true; // can remove self
        }
      }

      // The change owner may remove any zero or positive score.
      //
      if (isOwner() && 0 <= value) {
        return true;
      }

      // Users with the remove reviewer permission, the branch owner, project
      // owner and site admin can remove anyone
      if (getRefControl().canRemoveReviewer() // has removal permissions
          || getRefControl().isOwner() // branch owner
          || getProjectControl().isOwner() // project owner
          || getUser().getCapabilities().isAdmin_DoNotUse()) {
        return true;
      }
    }

    return false;
  }

  /** Can this user edit the topic name? */
  private boolean canEditTopicName() {
    if (getChange().getStatus().isOpen()) {
      return isOwner() // owner (aka creator) of the change can edit topic
          || getRefControl().isOwner() // branch owner can edit topic
          || getProjectControl().isOwner() // project owner can edit topic
          || getUser().getCapabilities().isAdmin_DoNotUse() // site administers are god
          || getRefControl().canEditTopicName() // user can edit topic on a specific ref
      ;
    }
    return getRefControl().canForceEditTopicName();
  }

  /** Can this user edit the description? */
  private boolean canEditDescription() {
    if (getChange().getStatus().isOpen()) {
      return isOwner() // owner (aka creator) of the change can edit desc
          || getRefControl().isOwner() // branch owner can edit desc
          || getProjectControl().isOwner() // project owner can edit desc
          || getUser().getCapabilities().isAdmin_DoNotUse() // site administers are god
      ;
    }
    return false;
  }

  private boolean canEditAssignee() {
    return isOwner()
        || getProjectControl().isOwner()
        || getRefControl().canEditAssignee()
        || isAssignee();
  }

  /** Can this user edit the hashtag name? */
  private boolean canEditHashtags() {
    return isOwner() // owner (aka creator) of the change can edit hashtags
        || getRefControl().isOwner() // branch owner can edit hashtags
        || getProjectControl().isOwner() // project owner can edit hashtags
        || getUser().getCapabilities().isAdmin_DoNotUse() // site administers are god
        || getRefControl().canEditHashtags(); // user can edit hashtag on a specific ref
  }

  private boolean match(String destBranch, String refPattern) {
    return RefPatternMatcher.getMatcher(refPattern).match(destBranch, getUser());
  }

  private ChangeData changeData(ReviewDb db, @Nullable ChangeData cd) {
    return cd != null ? cd : changeDataFactory.create(db, this);
  }

  public boolean isDraftVisible(ReviewDb db, ChangeData cd) throws OrmException {
    return isOwner()
        || isReviewer(db, cd)
        || getRefControl().canViewDrafts()
        || getUser().isInternalUser();
  }

  private boolean isPrivateVisible(ReviewDb db, ChangeData cd) throws OrmException {
    return isOwner()
        || isReviewer(db, cd)
        || getRefControl().canViewPrivateChanges()
        || getUser().isInternalUser();
  }

  ForChange asForChange(@Nullable ChangeData cd, @Nullable Provider<ReviewDb> db) {
    return new ForChangeImpl(cd, db);
  }

  private class ForChangeImpl extends ForChange {
    private ChangeData cd;
    private Map<String, PermissionRange> labels;

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
        cd = changeDataFactory.create(reviewDb, ChangeControl.this);
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
            return canAbandon(db());
          case DELETE:
            return canDelete(db(), getChange().getStatus());
          case ADD_PATCH_SET:
            return canAddPatchSet(db());
          case EDIT_ASSIGNEE:
            return canEditAssignee();
          case EDIT_DESCRIPTION:
            return canEditDescription();
          case EDIT_HASHTAGS:
            return canEditHashtags();
          case EDIT_TOPIC_NAME:
            return canEditTopicName();
          case REBASE:
            return canRebase(db());
          case RESTORE:
            return canRestore(db());
          case SUBMIT:
            return getRefControl().canSubmit(isOwner());

          case REMOVE_REVIEWER: // TODO Honor specific removal filters?
          case SUBMIT_AS:
            return getRefControl().canPerform(perm.permissionName().get());
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

  static <T extends ChangePermissionOrLabel> Set<T> newSet(Collection<T> permSet) {
    if (permSet instanceof EnumSet) {
      @SuppressWarnings({"unchecked", "rawtypes"})
      Set<T> s = ((EnumSet) permSet).clone();
      s.clear();
      return s;
    }
    return Sets.newHashSetWithExpectedSize(permSet.size());
  }
}
