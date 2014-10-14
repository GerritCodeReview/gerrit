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

import com.google.common.collect.Lists;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.RefConfigSection;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.common.data.SubmitTypeRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.assistedinject.AssistedInject;

import com.googlecode.prolog_cafe.lang.SymbolTerm;
import com.googlecode.prolog_cafe.lang.Term;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Collection;
import java.util.List;


/** Access control management for a user accessing a single change. */
public class ChangeControl {
  private static final Logger log = LoggerFactory
      .getLogger(ChangeControl.class);

  public static class GenericFactory {
    private final ProjectControl.GenericFactory projectControl;
    private final Provider<ReviewDb> db;

    @Inject
    GenericFactory(ProjectControl.GenericFactory p, Provider<ReviewDb> d) {
      projectControl = p;
      db = d;
    }

    public ChangeControl controlFor(Change change, CurrentUser user)
        throws NoSuchChangeException {
      final Project.NameKey projectKey = change.getProject();
      try {
        return projectControl.controlFor(projectKey, user).controlFor(change);
      } catch (NoSuchProjectException e) {
        throw new NoSuchChangeException(change.getId(), e);
      } catch (IOException e) {
        // TODO: propagate this exception
        throw new NoSuchChangeException(change.getId(), e);
      }
    }

    public ChangeControl controlFor(Change.Id id, CurrentUser user)
        throws NoSuchChangeException {
      final Change change;
      try {
        change = db.get().changes().get(id);
        if (change == null) {
          throw new NoSuchChangeException(id);
        }
      } catch (OrmException e) {
        throw new NoSuchChangeException(id, e);
      }
      return controlFor(change, user);
    }

    public ChangeControl validateFor(Change change, CurrentUser user)
        throws NoSuchChangeException, OrmException {
      ChangeControl c = controlFor(change, user);
      if (!c.isVisible(db.get())) {
        throw new NoSuchChangeException(c.getChange().getId());
      }
      return c;
    }

    public ChangeControl validateFor(Change.Id id, CurrentUser user)
        throws NoSuchChangeException, OrmException {
      ChangeControl c = controlFor(id, user);
      if (!c.isVisible(db.get())) {
        throw new NoSuchChangeException(c.getChange().getId());
      }
      return c;
    }
  }

  public static class Factory {
    private final ProjectControl.Factory projectControl;
    private final Provider<ReviewDb> db;

    @Inject
    Factory(final ProjectControl.Factory p, final Provider<ReviewDb> d) {
      projectControl = p;
      db = d;
    }

    public ChangeControl controlFor(final Change.Id id)
        throws NoSuchChangeException {
      final Change change;
      try {
        change = db.get().changes().get(id);
        if (change == null) {
          throw new NoSuchChangeException(id);
        }
      } catch (OrmException e) {
        throw new NoSuchChangeException(id, e);
      }
      return controlFor(change);
    }

    public ChangeControl controlFor(final Change change)
        throws NoSuchChangeException {
      try {
        final Project.NameKey projectKey = change.getProject();
        return projectControl.validateFor(projectKey).controlFor(change);
      } catch (NoSuchProjectException e) {
        throw new NoSuchChangeException(change.getId(), e);
      }
    }

    public ChangeControl validateFor(final Change.Id id)
        throws NoSuchChangeException, OrmException {
      return validate(controlFor(id), db.get());
    }

    public ChangeControl validateFor(final Change change)
        throws NoSuchChangeException, OrmException {
      return validate(controlFor(change), db.get());
    }

    private static ChangeControl validate(final ChangeControl c, final ReviewDb db)
        throws NoSuchChangeException, OrmException{
      if (!c.isVisible(db)) {
        throw new NoSuchChangeException(c.getChange().getId());
      }
      return c;
    }
  }

  interface AssistedFactory {
    ChangeControl create(RefControl refControl, Change change);
    ChangeControl create(RefControl refControl, ChangeNotes notes);
  }

  private final ChangeData.Factory changeDataFactory;
  private final RefControl refControl;
  private final ChangeNotes notes;

  @AssistedInject
  ChangeControl(
      ChangeData.Factory changeDataFactory,
      ChangeNotes.Factory notesFactory,
      @Assisted RefControl refControl,
      @Assisted Change change) {
    this(changeDataFactory, refControl,
        notesFactory.create(change));
  }

  @AssistedInject
  ChangeControl(
      ChangeData.Factory changeDataFactory,
      @Assisted RefControl refControl,
      @Assisted ChangeNotes notes) {
    this.changeDataFactory = changeDataFactory;
    this.refControl = refControl;
    this.notes = notes;
  }

  public ChangeControl forUser(final CurrentUser who) {
    if (getCurrentUser().equals(who)) {
      return this;
    }
    return new ChangeControl(changeDataFactory,
        getRefControl().forUser(who), notes);
  }

  public RefControl getRefControl() {
    return refControl;
  }

  public CurrentUser getCurrentUser() {
    return getRefControl().getCurrentUser();
  }

  public ProjectControl getProjectControl() {
    return getRefControl().getProjectControl();
  }

  public Project getProject() {
    return getProjectControl().getProject();
  }

  public Change getChange() {
    return notes.getChange();
  }

  public ChangeNotes getNotes() {
    return notes;
  }

  /** Can this user see this change? */
  public boolean isVisible(ReviewDb db) throws OrmException {
    if (getChange().getStatus() == Change.Status.DRAFT
        && !isDraftVisible(db, null)) {
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

  /** Can this user abandon this change? */
  public boolean canAbandon() {
    return isOwner() // owner (aka creator) of the change can abandon
        || getRefControl().isOwner() // branch owner can abandon
        || getProjectControl().isOwner() // project owner can abandon
        || getCurrentUser().getCapabilities().canAdministrateServer() // site administers are god
        || getRefControl().canAbandon() // user can abandon a specific ref
    ;
  }

  /** Can this user publish this draft change or any draft patch set of this change? */
  public boolean canPublish(final ReviewDb db) throws OrmException {
    return (isOwner() || getRefControl().canPublishDrafts())
        && isVisible(db);
  }

  /** Can this user delete this draft change or any draft patch set of this change? */
  public boolean canDeleteDraft(final ReviewDb db) throws OrmException {
    return (isOwner() || getRefControl().canDeleteDrafts())
        && isVisible(db);
  }

  /** Can this user rebase this change? */
  public boolean canRebase() {
    return isOwner() || getRefControl().canSubmit()
        || getRefControl().canRebase();
  }

  /** Can this user restore this change? */
  public boolean canRestore() {
    return canAbandon() // Anyone who can abandon the change can restore it back
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
          if (RefConfigSection.isValid(refPattern)
              && match(destBranch, refPattern)) {
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
  public boolean canAddPatchSet() {
    return getRefControl().canUpload();
  }

  /** Is this user the owner of the change? */
  public boolean isOwner() {
    if (getCurrentUser().isIdentifiedUser()) {
      final IdentifiedUser i = (IdentifiedUser) getCurrentUser();
      return i.getAccountId().equals(getChange().getOwner());
    }
    return false;
  }

  /** Is this user a reviewer for the change? */
  public boolean isReviewer(ReviewDb db) throws OrmException {
    return isReviewer(db, null);
  }

  /** Is this user a reviewer for the change? */
  public boolean isReviewer(ReviewDb db, @Nullable ChangeData cd)
      throws OrmException {
    if (getCurrentUser().isIdentifiedUser()) {
      Collection<Account.Id> results = changeData(db, cd).reviewers().values();
      IdentifiedUser user = (IdentifiedUser) getCurrentUser();
      return results.contains(user.getAccountId());
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
      if (getCurrentUser().isIdentifiedUser()) {
        final IdentifiedUser i = (IdentifiedUser) getCurrentUser();
        if (i.getAccountId().equals(reviewer)) {
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
          || getCurrentUser().getCapabilities().canAdministrateServer()) {
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
          || getCurrentUser().getCapabilities().canAdministrateServer() // site administers are god
          || getRefControl().canEditTopicName() // user can edit topic on a specific ref
      ;
    } else {
      return getRefControl().canForceEditTopicName();
    }
  }

  /** Can this user edit the hashtag name? */
  public boolean canEditHashtags() {
    return isOwner() // owner (aka creator) of the change can edit hashtags
          || getRefControl().isOwner() // branch owner can edit hashtags
          || getProjectControl().isOwner() // project owner can edit hashtags
          || getCurrentUser().getCapabilities().canAdministrateServer() // site administers are god
          || getRefControl().canEditHashtags(); // user can edit hashtag on a specific ref
  }


  public List<SubmitRecord> getSubmitRecords(ReviewDb db, PatchSet patchSet) {
    return canSubmit(db, patchSet, null, false, true, false);
  }

  public boolean canSubmit() {
    return getRefControl().canSubmit();
  }

  public boolean canSubmitAs() {
    return getRefControl().canSubmitAs();
  }

  public List<SubmitRecord> canSubmit(ReviewDb db, PatchSet patchSet) {
    return canSubmit(db, patchSet, null, false, false, false);
  }

  public List<SubmitRecord> canSubmit(ReviewDb db, PatchSet patchSet,
      @Nullable ChangeData cd, boolean fastEvalLabels, boolean allowClosed,
      boolean allowDraft) {
    cd = changeData(db, cd);
    try {
      return new SubmitRuleEvaluator(cd)
          .setPatchSet(patchSet)
          .setFastEvalLabels(fastEvalLabels)
          .setAllowClosed(allowClosed)
          .setAllowDraft(allowDraft)
          .canSubmit();
    } catch (OrmException e) {
      log.error("Error evaluating submit rule", e);
      return SubmitRuleEvaluator.defaultRuleError();
    }
  }

  private boolean match(String destBranch, String refPattern) {
    return RefPatternMatcher.getMatcher(refPattern).match(destBranch,
        this.getRefControl().getCurrentUser().getUserName());
  }


  public SubmitTypeRecord getSubmitTypeRecord(ReviewDb db, PatchSet patchSet) {
    return getSubmitTypeRecord(db, patchSet, null);
  }

  public SubmitTypeRecord getSubmitTypeRecord(ReviewDb db, PatchSet patchSet,
      @Nullable ChangeData cd) {
    cd = changeData(db, cd);
    try {
      if (getChange().getStatus() == Change.Status.DRAFT
          && !isDraftVisible(db, cd)) {
        return SubmitRuleEvaluator.createTypeError(
            "Patch set " + patchSet.getPatchSetId() + " not found");
      }
      if (patchSet.isDraft() && !isDraftVisible(db, cd)) {
        return SubmitRuleEvaluator.createTypeError(
            "Patch set " + patchSet.getPatchSetId() + " not found");
      }
    } catch (OrmException err) {
      String msg = "Cannot read patch set " + patchSet.getId();
      log.error(msg, err);
      return SubmitRuleEvaluator.createTypeError(msg);
    }

    try {
      return new SubmitRuleEvaluator(cd).setPatchSet(patchSet)
          .getSubmitType();
    } catch (OrmException e) {
      log.error(e.getMessage(), e);
      return SubmitRuleEvaluator.defaultTypeError();
    }
  }

  private ChangeData changeData(ReviewDb db, @Nullable ChangeData cd) {
    return cd != null ? cd : changeDataFactory.create(db, this);
  }

  public boolean isDraftVisible(ReviewDb db, ChangeData cd)
      throws OrmException {
    return isOwner() || isReviewer(db, cd) || getRefControl().canViewDrafts();
  }
}
