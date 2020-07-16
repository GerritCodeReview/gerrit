// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.server.change;

import static com.google.gerrit.entities.LabelValue.formatValue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Address;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.SubmitRecord;
import com.google.gerrit.extensions.api.changes.ReviewerInfo;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.account.AccountLoader;
import com.google.gerrit.server.permissions.LabelPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.SubmitRuleEvaluator;
import com.google.gerrit.server.project.SubmitRuleOptions;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.Collection;
import java.util.List;
import java.util.TreeMap;

@Singleton
public class ReviewerJson {
  private final PermissionBackend permissionBackend;
  private final ChangeData.Factory changeDataFactory;
  private final ApprovalsUtil approvalsUtil;
  private final AccountLoader.Factory accountLoaderFactory;
  private final SubmitRuleEvaluator submitRuleEvaluator;

  @Inject
  ReviewerJson(
      PermissionBackend permissionBackend,
      ChangeData.Factory changeDataFactory,
      ApprovalsUtil approvalsUtil,
      AccountLoader.Factory accountLoaderFactory,
      SubmitRuleEvaluator.Factory submitRuleEvaluatorFactory) {
    this.permissionBackend = permissionBackend;
    this.changeDataFactory = changeDataFactory;
    this.approvalsUtil = approvalsUtil;
    this.accountLoaderFactory = accountLoaderFactory;
    submitRuleEvaluator = submitRuleEvaluatorFactory.create(SubmitRuleOptions.defaults());
  }

  public List<ReviewerInfo> format(Collection<ReviewerResource> rsrcs)
      throws PermissionBackendException {
    List<ReviewerInfo> infos = Lists.newArrayListWithCapacity(rsrcs.size());
    AccountLoader loader = accountLoaderFactory.create(true);
    ChangeData cd = null;
    for (ReviewerResource rsrc : rsrcs) {
      if (cd == null || !cd.getId().equals(rsrc.getChangeId())) {
        cd = changeDataFactory.create(rsrc.getChangeResource().getNotes());
      }
      ReviewerInfo info;
      if (rsrc.isByEmail()) {
        Address address = rsrc.getReviewerByEmail();
        info = ReviewerInfo.byEmail(address.name(), address.email());
      } else {
        Account.Id reviewerAccountId = rsrc.getReviewerUser().getAccountId();
        info = format(new ReviewerInfo(reviewerAccountId.get()), reviewerAccountId, cd);
        loader.put(info);
      }
      infos.add(info);
    }
    loader.fill();
    return infos;
  }

  public List<ReviewerInfo> format(ReviewerResource rsrc) throws PermissionBackendException {
    return format(ImmutableList.of(rsrc));
  }

  public ReviewerInfo format(ReviewerInfo out, Account.Id reviewerAccountId, ChangeData cd)
      throws PermissionBackendException {
    PatchSet.Id psId = cd.change().currentPatchSetId();
    return format(
        out,
        reviewerAccountId,
        cd,
        approvalsUtil.byPatchSetUser(cd.notes(), psId, reviewerAccountId, null, null));
  }

  public ReviewerInfo format(
      ReviewerInfo out,
      Account.Id reviewerAccountId,
      ChangeData cd,
      Iterable<PatchSetApproval> approvals)
      throws PermissionBackendException {
    LabelTypes labelTypes = cd.getLabelTypes();

    out.approvals = new TreeMap<>(labelTypes.nameComparator());
    for (PatchSetApproval ca : approvals) {
      LabelType at = labelTypes.byLabel(ca.labelId());
      if (at != null) {
        out.approvals.put(at.getName(), formatValue(ca.value()));
      }
    }

    // Add dummy approvals for all permitted labels for the user even if they
    // do not exist in the DB.
    PatchSet ps = cd.currentPatchSet();
    if (ps != null) {
      PermissionBackend.ForChange perm = permissionBackend.absentUser(reviewerAccountId).change(cd);

      for (SubmitRecord rec : submitRuleEvaluator.evaluate(cd)) {
        if (rec.labels == null) {
          continue;
        }
        for (SubmitRecord.Label label : rec.labels) {
          String name = label.label;
          LabelType type = labelTypes.byLabel(name);
          if (out.approvals.containsKey(name) || type == null) {
            continue;
          }

          try {
            perm.check(new LabelPermission(type));
            out.approvals.put(name, formatValue((short) 0));
          } catch (AuthException e) {
            // Do nothing.
          }
        }
      }
    }

    if (out.approvals.isEmpty()) {
      out.approvals = null;
    }

    return out;
  }
}
