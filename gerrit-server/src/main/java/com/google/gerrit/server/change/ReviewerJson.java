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

import static com.google.gerrit.common.data.LabelValue.formatValue;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRange;
import com.google.gerrit.common.data.SubmitRecord;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.AccountInfo;
import com.google.gerrit.server.git.LabelNormalizer;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ReviewerJson {
  private final Provider<ReviewDb> db;
  private final LabelNormalizer labelNormalizer;
  private final AccountInfo.Loader.Factory accountLoaderFactory;

  @Inject
  ReviewerJson(Provider<ReviewDb> db,
      LabelNormalizer labelNormalizer,
      AccountInfo.Loader.Factory accountLoaderFactory) {
    this.db = db;
    this.labelNormalizer = labelNormalizer;
    this.accountLoaderFactory = accountLoaderFactory;
  }

  public List<ReviewerInfo> format(Collection<ReviewerResource> rsrcs)
      throws OrmException {
    List<ReviewerInfo> infos = Lists.newArrayListWithCapacity(rsrcs.size());
    AccountInfo.Loader loader = accountLoaderFactory.create(true);
    for (ReviewerResource rsrc : rsrcs) {
      ReviewerInfo info = format(rsrc, null);
      loader.put(info);
      infos.add(info);
    }
    loader.fill();
    return infos;
  }

  public List<ReviewerInfo> format(ReviewerResource rsrc) throws OrmException {
    return format(ImmutableList.<ReviewerResource> of(rsrc));
  }

  public ReviewerInfo format(ReviewerInfo out, ChangeControl ctl,
      List<PatchSetApproval> approvals) throws OrmException {
    PatchSet.Id psId = ctl.getChange().currentPatchSetId();

    if (approvals == null) {
      approvals = ChangeData.sortApprovals(db.get().patchSetApprovals()
          .byPatchSetUser(psId, out._id));
    }
    approvals = labelNormalizer.normalize(ctl, approvals);
    LabelTypes labelTypes = ctl.getLabelTypes();

    // Don't use Maps.newTreeMap(Comparator) due to OpenJDK bug 100167.
    out.approvals = new TreeMap<String,String>(labelTypes.nameComparator());
    for (PatchSetApproval ca : approvals) {
      for (PermissionRange pr : ctl.getLabelRanges()) {
        if (!pr.isEmpty()) {
          LabelType at = labelTypes.byLabel(ca.getLabelId());
          if (at != null) {
            out.approvals.put(at.getName(), formatValue(ca.getValue()));
          }
        }
      }
    }

    // Add dummy approvals for all permitted labels for the user even if they
    // do not exist in the DB.
    ChangeData cd = new ChangeData(ctl);
    PatchSet ps = cd.currentPatchSet(db);
    if (ps != null) {
      for (SubmitRecord rec :
          ctl.canSubmit(db.get(), ps, cd, true, false, true)) {
        if (rec.labels == null) {
          continue;
        }
        for (SubmitRecord.Label label : rec.labels) {
          String name = label.label;
          if (!out.approvals.containsKey(name)
              && !ctl.getRange(Permission.forLabel(name)).isEmpty()) {
            out.approvals.put(name, formatValue((short) 0));
          }
        }
      }
    }

    if (out.approvals.isEmpty()) {
      out.approvals = null;
    }

    return out;
  }

  private ReviewerInfo format(ReviewerResource rsrc,
      List<PatchSetApproval> approvals) throws OrmException {
    return format(new ReviewerInfo(rsrc.getUser().getAccountId()),
        rsrc.getUserControl(), approvals);
  }

  public static class ReviewerInfo extends AccountInfo {
    final String kind = "gerritcodereview#reviewer";
    Map<String, String> approvals;

    protected ReviewerInfo(Account.Id id) {
      super(id);
    }
  }

  public static class PostResult {
    public List<ReviewerInfo> reviewers;
    public String error;
    Boolean confirm;
  }
}
