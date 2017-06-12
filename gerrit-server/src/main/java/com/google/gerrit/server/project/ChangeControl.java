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

import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.RefConfigSection;
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
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

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
        throws NoSuchChangeException, OrmException {
      return controlFor(notesFactory.create(db, project, changeId), user);
    }

    public ChangeControl controlFor(ReviewDb db, Change change, CurrentUser user)
        throws NoSuchChangeException, OrmException {
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
        throws NoSuchChangeException, OrmException {
      return validateFor(db, notesFactory.createChecked(changeId), user);
    }

    public ChangeControl validateFor(ReviewDb db, ChangeNotes notes, CurrentUser user)
        throws NoSuchChangeException, OrmException {
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

  public ChangeControl forUser(final CurrentUser who) {
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
    if (getChange().getStatus() == Change.Status.DRAFT && !isDraftVisible(db, cd)) {
      return false;
    }
    return isRefVisible();
  }

  /** Can the user see this change? Does not account for draft status */
  public boolean isRefVisible() {
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
  public boolean canAbandon(ReviewDb db) throws OrmException {
    return (isOwner() // owner (aka creator) of the change can abandon
            || getRefControl().isOwner() // branch owner can abandon
            || getProjectControl().isOwner() // project owner can abandon
            || getUser().getCapabilities().canAdministrateServer() // site administers are god
            || getRefControl().canAbandon() // user can abandon a specific ref
        )
        && !isPatchSetLocked(db);
  }

  /** Can this user change the destination branch of this change to the new ref? */
  public boolean canMoveTo(String ref, ReviewDb db) throws OrmException {
    return getProjectControl().controlForRef(ref).canUpload() && canAbandon(db);
  }

  /** Can this user publish this draft change or any draft patch set of this change? */
  public boolean canPublish(final ReviewDb db) throws OrmException {
    return (isOwner() || getRefControl().canPublishDrafts()) && isVisible(db);
  }

  /** Can this user delete this change or any patch set of this change? */
  public boolean canDelete(ReviewDb db, Change.Status status) throws OrmException {
    if (!isVisible(db)) {
      return false;
    }

    switch (status) {
      case DRAFT:
        return (isOwner() || getRefControl().canDeleteDrafts());
      case NEW:
      case ABANDONED:
        return isAdmin();
      case MERGED:
      default:
        return false;
    }
  }

  /** Can this user rebase this change? */
  public boolean canRebase(ReviewDb db) throws OrmException {
    return (isOwner() || getRefControl().canSubmit(isOwner()) || getRefControl().canRebase())
        && !isPatchSetLocked(db);
  }

  /** Can this user restore this change? */
  public boolean canRestore(ReviewDb db) throws OrmException {
    return canAbandon(db) // Anyone who can abandon the change can restore it back
        && getRefControl().canUpload(); // as long as you can upload too
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
  public boolean canAddPatchSet(ReviewDb db) throws OrmException {
    if (!getRefControl().canUpload()
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
  public boolean isOwner() {
    if (getUser().isIdentifiedUser()) {
      Account.Id id = getUser().asIdentifiedUser().getAccountId();
      return id.equals(getChange().getOwner());
    }
    return false;
  }

  /** Is this user assigned to this change? */
  public boolean isAssignee() {
    Account.Id currentAssignee = notes.getChange().getAssignee();
    if (currentAssignee != null && getUser().isIdentifiedUser()) {
      Account.Id id = getUser().getAccountId();
      return id.equals(currentAssignee);
    }
    return false;
  }

  /** Is this user a reviewer for the change? */
  public boolean isReviewer(ReviewDb db) throws OrmException {
    return isReviewer(db, null);
  }

  /** Is this user a reviewer for the change? */
  public boolean isReviewer(ReviewDb db, @Nullable ChangeData cd) throws OrmException {
    if (getUser().isIdentifiedUser()) {
      Collection<Account.Id> results = changeData(db, cd).reviewers().all();
      return results.contains(getUser().getAccountId());
    }
    return false;
  }

  public boolean isAdmin() {
    return getUser().getCapabilities().canAdministrateServer();
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
          || getUser().getCapabilities().canAdministrateServer()) {
        return true;
      }
    }

    return false;
  }

  /** Can this user edit the topic name? */
  public boolean canEditTopicName() {
    if (getChange().getStatus().isOpen()) {
      return isOwner() // owner (aka creator) of the change can edit topic
          || getRefControl().isOwner() // branch owner can edit topic
          || getProjectControl().isOwner() // project owner can edit topic
          || getUser().getCapabilities().canAdministrateServer() // site administers are god
          || getRefControl().canEditTopicName() // user can edit topic on a specific ref
      ;
    }
    return getRefControl().canForceEditTopicName();
  }

  public boolean canEditAssignee() {
    return isOwner()
        || getProjectControl().isOwner()
        || getRefControl().canEditAssignee()
        || isAssignee();
  }

  /** Can this user edit the hashtag name? */
  public boolean canEditHashtags() {
    return isOwner() // owner (aka creator) of the change can edit hashtags
        || getRefControl().isOwner() // branch owner can edit hashtags
        || getProjectControl().isOwner() // project owner can edit hashtags
        || getUser().getCapabilities().canAdministrateServer() // site administers are god
        || getRefControl().canEditHashtags(); // user can edit hashtag on a specific ref
  }

  public boolean canSubmit() {
    return getRefControl().canSubmit(isOwner());
  }

  public boolean canSubmitAs() {
    return getRefControl().canSubmitAs();
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
}
