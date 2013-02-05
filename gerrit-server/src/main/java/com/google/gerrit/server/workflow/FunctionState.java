// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.workflow;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ChangeControl;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** State passed through to a {@link CategoryFunction}. */
public class FunctionState {
  public interface Factory {
    FunctionState create(ChangeControl c, PatchSet.Id psId,
        Collection<PatchSetApproval> all);
  }

  private final ApprovalTypes approvalTypes;
  private final IdentifiedUser.GenericFactory userFactory;

  private final Map<String, Collection<PatchSetApproval>> approvals =
      new HashMap<String, Collection<PatchSetApproval>>();
  private final Map<String, Boolean> valid = new HashMap<String, Boolean>();
  private final ChangeControl callerChangeControl;
  private final Change change;

  @Inject
  FunctionState(final ApprovalTypes approvalTypes,
      final IdentifiedUser.GenericFactory userFactory,
      @Assisted final ChangeControl c, @Assisted final PatchSet.Id psId,
      @Assisted final Collection<PatchSetApproval> all) {
    this.approvalTypes = approvalTypes;
    this.userFactory = userFactory;

    callerChangeControl = c;
    change = c.getChange();

    for (final PatchSetApproval ca : all) {
      if (psId.equals(ca.getPatchSetId())) {
        Collection<PatchSetApproval> l =
            approvals.get(ca.getCategoryId().get());
        if (l == null) {
          l = new ArrayList<PatchSetApproval>();
          ApprovalType at = approvalTypes.byId(ca.getCategoryId().get());
          if (at != null) {
            // TODO: Support arbitrary labels
            approvals.put(at.getName(), l);
          }
        }
        l.add(ca);
      }
    }
  }

  List<ApprovalType> getApprovalTypes() {
    return approvalTypes.getApprovalTypes();
  }

  Change getChange() {
    return change;
  }

  public void valid(final ApprovalType at, final boolean v) {
    valid.put(id(at), v);
  }

  public boolean isValid(final ApprovalType at) {
    return isValid(at.getName());
  }

  public boolean isValid(final String labelName) {
    final Boolean b = valid.get(labelName);
    return b != null && b;
  }

  public Collection<PatchSetApproval> getApprovals(final ApprovalType at) {
    return getApprovals(at.getName());
  }

  public Collection<PatchSetApproval> getApprovals(final String labelName) {
    final Collection<PatchSetApproval> l = approvals.get(labelName);
    return l != null ? l : Collections.<PatchSetApproval> emptySet();
  }

  /**
   * Normalize the approval record down to the range permitted by the type, in
   * case the type was modified since the approval was originally granted.
   * <p>
   */
  private void applyTypeFloor(final ApprovalType at, final PatchSetApproval a) {
    final LabelValue atMin = at.getMin();

    if (atMin != null && a.getValue() < atMin.getValue()) {
      a.setValue(atMin.getValue());
    }

    final LabelValue atMax = at.getMax();
    if (atMax != null && a.getValue() > atMax.getValue()) {
      a.setValue(atMax.getValue());
    }
  }

  /**
   * Normalize the approval record to be inside the maximum range permitted by
   * the RefRights granted to groups the account is a member of.
   * <p>
   * If multiple RefRights are matched (assigned to different groups the account
   * is a member of) the lowest minValue and the highest maxValue of the union
   * of them is used.
   * <p>
   */
  private void applyRightFloor(final ApprovalType at, final PatchSetApproval a) {
    final String permission = Permission.forLabel(at.getName());
    final IdentifiedUser user = userFactory.create(a.getAccountId());
    final PermissionRange range = controlFor(user).getRange(permission);
    a.setValue((short) range.squash(a.getValue()));
  }

  private ChangeControl controlFor(CurrentUser user) {
    return callerChangeControl.forUser(user);
  }

  /** Run <code>applyTypeFloor</code>, <code>applyRightFloor</code>. */
  public void normalize(final ApprovalType at, final PatchSetApproval ca) {
    applyTypeFloor(at, ca);
    applyRightFloor(at, ca);
  }

  private static String id(final ApprovalType at) {
    return at.getId();
  }
}
