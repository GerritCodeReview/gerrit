// Copyright 2008 Google Inc.
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

package com.google.gerrit.client.workflow;

import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ApprovalCategory.Id;
import com.google.gerrit.client.rpc.Common;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/** State passed through to a {@link CategoryFunction}. */
public class FunctionState {
  private final Map<Account.Id, Set<AccountGroup.Id>> groupCache =
      new HashMap<Account.Id, Set<AccountGroup.Id>>();
  private final Map<ApprovalCategory.Id, Collection<ChangeApproval>> approvals =
      new HashMap<ApprovalCategory.Id, Collection<ChangeApproval>>();
  private final Map<ApprovalCategory.Id, Boolean> valid =
      new HashMap<ApprovalCategory.Id, Boolean>();
  private final ProjectCache.Entry project;
  private final Map<ApprovalCategory.Id, Collection<ProjectRight>> allRights =
      new HashMap<ApprovalCategory.Id, Collection<ProjectRight>>();
  private Map<ApprovalCategory.Id, Collection<ProjectRight>> projectRights;
  private Map<ApprovalCategory.Id, Collection<ProjectRight>> wildcardRights;
  private Set<ChangeApproval> modified;

  public FunctionState(final ProjectCache.Entry p,
      final Collection<ChangeApproval> all) {
    project = p;

    for (final ChangeApproval ca : all) {
      Collection<ChangeApproval> l = approvals.get(ca.getCategoryId());
      if (l == null) {
        l = new ArrayList<ChangeApproval>();
        approvals.put(ca.getCategoryId(), l);
      }
      l.add(ca);
    }
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

  public Collection<ChangeApproval> getApprovals(final ApprovalType at) {
    return getApprovals(id(at));
  }

  public Collection<ChangeApproval> getApprovals(final ApprovalCategory.Id id) {
    final Collection<ChangeApproval> l = approvals.get(id);
    return l != null ? l : Collections.<ChangeApproval> emptySet();
  }

  public void dirty(final ChangeApproval ap) {
    if (modified == null) {
      modified = new HashSet<ChangeApproval>();
    }
    modified.add(ap);
  }

  public Collection<ChangeApproval> getDirtyChangeApprovals() {
    if (modified != null) {
      return modified;
    }
    return Collections.emptySet();
  }

  public Collection<ProjectRight> getProjectRights(final ApprovalType at) {
    return getProjectRights(id(at));
  }

  public Collection<ProjectRight> getProjectRights(final ApprovalCategory.Id id) {
    if (projectRights == null) {
      projectRights = index(project.getRights());
    }
    final Collection<ProjectRight> l = projectRights.get(id);
    return l != null ? l : Collections.<ProjectRight> emptySet();
  }

  public Collection<ProjectRight> getWildcardRights(final ApprovalType at) {
    return getWildcardRights(id(at));
  }

  public Collection<ProjectRight> getWildcardRights(final ApprovalCategory.Id id) {
    if (wildcardRights == null) {
      wildcardRights = index(Common.getProjectCache().getWildcardRights());
    }
    final Collection<ProjectRight> l = wildcardRights.get(id);
    return l != null ? l : Collections.<ProjectRight> emptySet();
  }

  public Collection<ProjectRight> getAllRights(final ApprovalType at) {
    return getAllRights(id(at));
  }

  public Collection<ProjectRight> getAllRights(final ApprovalCategory.Id id) {
    Collection<ProjectRight> l = allRights.get(id);
    if (l == null) {
      l = new ArrayList<ProjectRight>();
      l.addAll(getProjectRights(id));
      l.addAll(getWildcardRights(id));
      l = Collections.unmodifiableCollection(l);
      allRights.put(id, l);
    }
    return l;
  }

  private static Map<Id, Collection<ProjectRight>> index(
      final Collection<ProjectRight> rights) {
    final HashMap<ApprovalCategory.Id, Collection<ProjectRight>> r;

    r = new HashMap<ApprovalCategory.Id, Collection<ProjectRight>>();
    for (final ProjectRight pr : rights) {
      Collection<ProjectRight> l = r.get(pr.getApprovalCategoryId());
      if (l == null) {
        l = new ArrayList<ProjectRight>();
        r.put(pr.getApprovalCategoryId(), l);
      }
      l.add(pr);
    }
    return r;
  }

  public boolean isMember(final ChangeApproval ca, final ProjectRight r) {
    return isMember(ca.getAccountId(), r.getAccountGroupId());
  }

  public boolean isMember(final Account.Id accountId,
      final AccountGroup.Id groupId) {
    return getGroups(accountId).contains(groupId);
  }

  public Set<AccountGroup.Id> getGroups(final Account.Id id) {
    Set<AccountGroup.Id> g = groupCache.get(id);
    if (g == null) {
      g = Common.getGroupCache().getGroups(id);
      groupCache.put(id, g);
    }
    return g;
  }

  /**
   * Normalize the approval record down to the range permitted by the type, in
   * case the type was modified since the approval was originally granted.
   * <p>
   * If the record's value was modified, its automatically marked as dirty.
   */
  public void applyTypeFloor(final ApprovalType at, final ChangeApproval a) {
    final ApprovalCategoryValue atMin = at.getMin();

    if (atMin != null && a.getValue() < atMin.getValue()) {
      a.setValue(atMin.getValue());
      dirty(a);
    }

    final ApprovalCategoryValue atMax = at.getMax();
    if (atMax != null && a.getValue() > atMax.getValue()) {
      a.setValue(atMax.getValue());
      dirty(a);
    }
  }

  /**
   * Normalize the approval record to be inside the maximum range permitted by
   * the ProjectRights granted to groups the account is a member of.
   * <p>
   * If multiple ProjectRights are matched (assigned to different groups the
   * account is a member of) the lowest minValue and the highest maxValue of the
   * union of them is used.
   * <p>
   * If the record's value was modified, its automatically marked as dirty.
   */
  public void applyRightFloor(final ChangeApproval a) {
    // Find the maximal range actually granted to the user.
    //
    short minAllowed = 0, maxAllowed = 0;
    for (final ProjectRight r : getAllRights(a.getCategoryId())) {
      if (isMember(a, r)) {
        minAllowed = (short) Math.min(minAllowed, r.getMinValue());
        maxAllowed = (short) Math.max(maxAllowed, r.getMaxValue());
      }
    }

    // Normalize the value into that range, returning true if we changed
    // the value.
    //
    if (a.getValue() < minAllowed) {
      a.setValue(minAllowed);
      dirty(a);

    } else if (a.getValue() > maxAllowed) {
      a.setValue(maxAllowed);
      dirty(a);
    }
  }

  /** Run <code>applyTypeFloor</code>, <code>applyRightFloor</code>. */
  public void normalize(final ApprovalType at, final ChangeApproval ca) {
    applyTypeFloor(at, ca);
    applyRightFloor(ca);
  }

  private static Id id(final ApprovalType at) {
    return at.getCategory().getId();
  }
}
