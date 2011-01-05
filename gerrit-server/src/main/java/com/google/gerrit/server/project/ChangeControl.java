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

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.workflow.CategoryFunction;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;


/** Access control management for a user accessing a single change. */
public class ChangeControl {
  public static class GenericFactory {
    private final ProjectControl.GenericFactory projectControl;

    @Inject
    GenericFactory(ProjectControl.GenericFactory p) {
      projectControl = p;
    }

    public ChangeControl controlFor(Change change, CurrentUser user)
        throws NoSuchChangeException {
      final Project.NameKey projectKey = change.getProject();
      try {
        return projectControl.controlFor(projectKey, user).controlFor(change);
      } catch (NoSuchProjectException e) {
        throw new NoSuchChangeException(change.getId(), e);
      }
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
        throws NoSuchChangeException {
      return validate(controlFor(id));
    }

    public ChangeControl validateFor(final Change change)
        throws NoSuchChangeException {
      return validate(controlFor(change));
    }

    private static ChangeControl validate(final ChangeControl c)
        throws NoSuchChangeException {
      if (!c.isVisible()) {
        throw new NoSuchChangeException(c.getChange().getId());
      }
      return c;
    }
  }

  private final RefControl refControl;
  private final Change change;

  ChangeControl(final RefControl r, final Change c) {
    this.refControl = r;
    this.change = c;
  }

  public ChangeControl forAnonymousUser() {
    return new ChangeControl(getRefControl().forAnonymousUser(), getChange());
  }

  public ChangeControl forUser(final CurrentUser who) {
    return new ChangeControl(getRefControl().forUser(who), getChange());
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
    return change;
  }

  /** Can this user see this change? */
  public boolean isVisible() {
    return getRefControl().isVisible();
  }

  /** Can this user abandon this change? */
  public boolean canAbandon() {
    return isOwner() // owner (aka creator) of the change can abandon
        || getRefControl().isOwner() // branch owner can abandon
        || getProjectControl().isOwner() // project owner can abandon
        || getCurrentUser().isAdministrator() // site administers are god
    ;
  }

  /** Can this user restore this change? */
  public boolean canRestore() {
    return canAbandon(); // Anyone who can abandon the change can restore it back
  }

  /** All value ranges of any allowed label permission. */
  public List<PermissionRange> getLabelRanges() {
    return getRefControl().getLabelRanges();
  }

  /** The range of permitted values associated with a label permission. */
  public PermissionRange getRange(String permission) {
    return getRefControl().getRange(permission);
  }

  /** Can this user add a patch set to this change? */
  public boolean canAddPatchSet() {
    return getRefControl().canUpload();
  }

  /** Is this user the owner of the change? */
  public boolean isOwner() {
    if (getCurrentUser() instanceof IdentifiedUser) {
      final IdentifiedUser i = (IdentifiedUser) getCurrentUser();
      return i.getAccountId().equals(change.getOwner());
    }
    return false;
  }

  /** @return true if the user is allowed to remove this reviewer. */
  public boolean canRemoveReviewer(PatchSetApproval approval) {
    if (getChange().getStatus().isOpen()) {
      // A user can always remove themselves.
      //
      if (getCurrentUser() instanceof IdentifiedUser) {
        final IdentifiedUser i = (IdentifiedUser) getCurrentUser();
        if (i.getAccountId().equals(approval.getAccountId())) {
          return true; // can remove self
        }
      }

      // The change owner may remove any zero or positive score.
      //
      if (isOwner() && 0 <= approval.getValue()) {
        return true;
      }

      // The branch owner, project owner, site admin can remove anyone.
      //
      if (getRefControl().isOwner() // branch owner
          || getProjectControl().isOwner() // project owner
          || getCurrentUser().isAdministrator()) {
        return true;
      }
    }

    return false;
  }

  /** @return {@link CanSubmitResult#OK}, or a result with an error message. */
  public CanSubmitResult canSubmit(final PatchSet.Id patchSetId) {
    if (change.getStatus().isClosed()) {
      return new CanSubmitResult("Change " + change.getId() + " is closed");
    }
    if (!patchSetId.equals(change.currentPatchSetId())) {
      return new CanSubmitResult("Patch set " + patchSetId + " is not current");
    }
    if (!getRefControl().canSubmit()) {
      return new CanSubmitResult("User does not have permission to submit");
    }
    if (!(getCurrentUser() instanceof IdentifiedUser)) {
      return new CanSubmitResult("User is not signed-in");
    }
    return CanSubmitResult.OK;
  }

  /** @return {@link CanSubmitResult#OK}, or a result with an error message. */
  public CanSubmitResult canSubmit(final PatchSet.Id patchSetId, final ReviewDb db,
        final ApprovalTypes approvalTypes,
        FunctionState.Factory functionStateFactory)
         throws OrmException {

    CanSubmitResult result = canSubmit(patchSetId);
    if (result != CanSubmitResult.OK) {
      return result;
    }

    final List<PatchSetApproval> all =
        db.patchSetApprovals().byPatchSet(patchSetId).toList();

    final FunctionState fs =
        functionStateFactory.create(change, patchSetId, all);

    for (ApprovalType c : approvalTypes.getApprovalTypes()) {
      CategoryFunction.forCategory(c.getCategory()).run(c, fs);
    }

    for (ApprovalType type : approvalTypes.getApprovalTypes()) {
      if (!fs.isValid(type)) {
        return new CanSubmitResult("Requires " + type.getCategory().getName());
      }
    }

    return CanSubmitResult.OK;
  }
}
