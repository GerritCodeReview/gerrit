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
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class RightRule {
  private final ReviewDb db;

  public RightRule(final ReviewDb d) {
    db = d;
  }

  public Set<ApprovalCategory.Id> apply(final Project.NameKey projectName,
      final Collection<ChangeApproval> approvals) throws OrmException {
    final Project project = db.projects().get(projectName);
    final Project.Id projectId = project != null ? project.getId() : null;
    return apply(projectId, approvals);
  }

  public Set<ApprovalCategory.Id> apply(final Project.Id projectId,
      final Collection<ChangeApproval> approvals) throws OrmException {
    final Map<ApprovalCategory.Id, Collection<ProjectRight>> rights;
    final Map<ApprovalCategory.Id, ChangeApproval> max;

    rights = loadRights(projectId);
    max = new HashMap<ApprovalCategory.Id, ChangeApproval>();
    for (final ChangeApproval a : approvals) {
      normalize(rights, a);
      final ChangeApproval m = max.get(a.getCategoryId());
      if (m == null || m.getValue() < a.getValue()) {
        max.put(a.getCategoryId(), a);
      }
    }

    final Set<ApprovalCategory.Id> missing = new HashSet<ApprovalCategory.Id>();
    for (final ApprovalType at : Common.getGerritConfig().getApprovalTypes()) {
      final ChangeApproval m = max.get(at.getCategory().getId());
      final ApprovalCategoryValue n = at.getMax();
      if (m == null || n == null || m.getValue() < n.getValue()) {
        missing.add(at.getCategory().getId());
      }
    }
    return missing;
  }

  public void normalize(final Project.NameKey projectName,
      final Collection<ChangeApproval> approvals, final Transaction txn)
      throws OrmException {
    final Project project = db.projects().get(projectName);
    final Project.Id projectId = project != null ? project.getId() : null;
    normalize(projectId, approvals, txn);
  }

  public void normalize(final Project.Id projectId,
      final Collection<ChangeApproval> approvals, final Transaction txn)
      throws OrmException {
    final Map<ApprovalCategory.Id, Collection<ProjectRight>> rights;

    rights = loadRights(projectId);
    for (final ChangeApproval a : approvals) {
      if (normalize(rights, a)) {
        db.changeApprovals().update(Collections.singleton(a), txn);
      }
    }
  }

  private boolean normalize(
      final Map<ApprovalCategory.Id, Collection<ProjectRight>> rights,
      final ChangeApproval a) {
    short min = 0, max = 0;
    final Collection<ProjectRight> l = rights.get(a.getCategoryId());
    if (l != null) {
      final Set<AccountGroup.Id> gs =
          Common.getGroupCache().getGroups(a.getAccountId());
      for (final ProjectRight r : l) {
        if (gs.contains(r.getAccountGroupId())) {
          min = (short) Math.min(min, r.getMinValue());
          max = (short) Math.max(max, r.getMaxValue());
        }
      }
    }
    if (a.getValue() < min) {
      a.setValue(min);
      return true;
    } else if (a.getValue() > max) {
      a.setValue(max);
      return true;
    } else {
      return false;
    }
  }

  private Map<ApprovalCategory.Id, Collection<ProjectRight>> loadRights(
      final Project.Id projectId) throws OrmException {
    final Map<ApprovalCategory.Id, Collection<ProjectRight>> rights =
        new HashMap<ApprovalCategory.Id, Collection<ProjectRight>>();
    if (projectId != null) {
      loadRights(rights, projectId);
    }
    loadRights(rights, ProjectRight.WILD_PROJECT);
    return rights;
  }

  private void loadRights(
      final Map<ApprovalCategory.Id, Collection<ProjectRight>> rights,
      final Project.Id projectId) throws OrmException {
    for (final ProjectRight p : db.projectRights().byProject(projectId)) {
      Collection<ProjectRight> l = rights.get(p.getApprovalCategoryId());
      if (l == null) {
        l = new ArrayList<ProjectRight>();
        rights.put(p.getApprovalCategoryId(), l);
      }
      l.add(p);
    }
  }
}
