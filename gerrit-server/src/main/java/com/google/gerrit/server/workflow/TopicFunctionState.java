// Copyright (C) 2011 The Android Open Source Project
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
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.ChangeSet;
import com.google.gerrit.reviewdb.ChangeSetApproval;
import com.google.gerrit.reviewdb.Topic;
import com.google.gerrit.reviewdb.ApprovalCategory.Id;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefControl;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** State passed through to a {@link TopicCategoryFunction}. */
public class TopicFunctionState {
  public interface Factory {
    TopicFunctionState create(Topic c, ChangeSet.Id sId,
        Collection<ChangeSetApproval> all);
  }

  private final ApprovalTypes approvalTypes;
  private final IdentifiedUser.GenericFactory userFactory;

  private final Map<ApprovalCategory.Id, Collection<ChangeSetApproval>> approvals =
      new HashMap<ApprovalCategory.Id, Collection<ChangeSetApproval>>();
  private final Map<ApprovalCategory.Id, Boolean> valid =
      new HashMap<ApprovalCategory.Id, Boolean>();
  private final Topic topic;
  private final ProjectState project;

  @Inject
  TopicFunctionState(final ApprovalTypes approvalTypes,
      final ProjectCache projectCache,
      final IdentifiedUser.GenericFactory userFactory, final GroupCache egc,
      @Assisted final Topic t, @Assisted final ChangeSet.Id sId,
      @Assisted final Collection<ChangeSetApproval> all) {
    this.approvalTypes = approvalTypes;
    this.userFactory = userFactory;

    topic = t;
    project = projectCache.get(topic.getProject());

    for (final ChangeSetApproval ca : all) {
      if (sId.equals(ca.getSetId())) {
        Collection<ChangeSetApproval> l = approvals.get(ca.getCategoryId());
        if (l == null) {
          l = new ArrayList<ChangeSetApproval>();
          approvals.put(ca.getCategoryId(), l);
        }
        l.add(ca);
      }
    }
  }

  List<ApprovalType> getApprovalTypes() {
    return approvalTypes.getApprovalTypes();
  }

  Topic getTopic() {
    return topic;
  }

  public void valid(final ApprovalType at, final boolean v) {
    valid.put(id(at), v);
  }

  public boolean isValid(final ApprovalType at) {
    return isValid(id(at));
  }

  public boolean isValid(final ApprovalCategory.Id id) {
    final Boolean b = valid.get(id);
    return b != null && b;
  }

  public Collection<ChangeSetApproval> getApprovals(final ApprovalType at) {
    return getApprovals(id(at));
  }

  public Collection<ChangeSetApproval> getApprovals(final ApprovalCategory.Id id) {
    final Collection<ChangeSetApproval> l = approvals.get(id);
    return l != null ? l : Collections.<ChangeSetApproval> emptySet();
  }

  /**
   * Normalize the approval record down to the range permitted by the type, in
   * case the type was modified since the approval was originally granted.
   * <p>
   */
  private void applyTypeFloor(final ApprovalType at, final ChangeSetApproval a) {
    final ApprovalCategoryValue atMin = at.getMin();

    if (atMin != null && a.getValue() < atMin.getValue()) {
      a.setValue(atMin.getValue());
    }

    final ApprovalCategoryValue atMax = at.getMax();
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
  private void applyRightFloor(final ApprovalType at, final ChangeSetApproval a) {
    final ApprovalCategory category = at.getCategory();
    final String permission = Permission.forLabel(category.getLabelName());
    final IdentifiedUser user = userFactory.create(a.getAccountId());
    final PermissionRange range = controlFor(user).getRange(permission);
    a.setValue((short) range.squash(a.getValue()));
  }

  RefControl controlFor(final CurrentUser user) {
    ProjectControl pc = project.controlFor(user);
    RefControl rc = pc.controlForRef(topic.getDest().get());
    return rc;
  }

  /** Run <code>applyTypeFloor</code>, <code>applyRightFloor</code>. */
  public void normalize(final ApprovalType at, final ChangeSetApproval ca) {
    applyTypeFloor(at, ca);
    applyRightFloor(at, ca);
  }

  private static Id id(final ApprovalType at) {
    return at.getCategory().getId();
  }
}
