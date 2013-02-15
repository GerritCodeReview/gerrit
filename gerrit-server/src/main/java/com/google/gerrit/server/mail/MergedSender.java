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

package com.google.gerrit.server.mail;

import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.common.errors.EmailException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountProjectWatch.NotifyType;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.HashMap;
import java.util.Map;

/** Send notice about a change successfully merged. */
public class MergedSender extends ReplyToChangeSender {
  public static interface Factory {
    public MergedSender create(ChangeControl change);
  }

  private final LabelTypes labelTypes;

  @Inject
  public MergedSender(EmailArguments ea, @Assisted ChangeControl c) {
    super(ea, c.getChange(), "merged");
    labelTypes = c.getLabelTypes();
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    ccAllApprovals();
    bccStarredBy();
    includeWatchers(NotifyType.ALL_COMMENTS);
    includeWatchers(NotifyType.SUBMITTED_CHANGES);
  }

  @Override
  protected void formatChange() throws EmailException {
    appendText(velocifyFile("Merged.vm"));
  }

  public String getApprovals() {
    try {
      final Map<Account.Id, Map<ApprovalCategory.Id, PatchSetApproval>> pos =
          new HashMap<Account.Id, Map<ApprovalCategory.Id, PatchSetApproval>>();

      final Map<Account.Id, Map<ApprovalCategory.Id, PatchSetApproval>> neg =
          new HashMap<Account.Id, Map<ApprovalCategory.Id, PatchSetApproval>>();

      for (PatchSetApproval ca : args.db.get().patchSetApprovals()
          .byPatchSet(patchSet.getId())) {
        if (ca.getValue() > 0) {
          insert(pos, ca);
        } else if (ca.getValue() < 0) {
          insert(neg, ca);
        }
      }

      return format("Approvals", pos) + format("Objections", neg);
    } catch (OrmException err) {
      // Don't list the approvals
    }
    return "";
  }

  private String format(final String type,
      final Map<Account.Id, Map<ApprovalCategory.Id, PatchSetApproval>> list) {
    StringBuilder txt = new StringBuilder();
    if (list.isEmpty()) {
      return "";
    }
    txt.append(type + ":\n");
    for (final Map.Entry<Account.Id, Map<ApprovalCategory.Id, PatchSetApproval>> ent : list
        .entrySet()) {
      final Map<ApprovalCategory.Id, PatchSetApproval> l = ent.getValue();
      txt.append("  ");
      txt.append(getNameFor(ent.getKey()));
      txt.append(": ");
      boolean first = true;
      for (LabelType lt : labelTypes.getLabelTypes()) {
        final PatchSetApproval ca = l.get(lt.getId());
        if (ca == null) {
          continue;
        }

        if (first) {
          first = false;
        } else {
          txt.append("; ");
        }

        final LabelValue v = lt.getValue(ca);
        if (v != null) {
          txt.append(v.getText());
        } else {
          txt.append(lt.getName());
          txt.append("=");
          if (ca.getValue() > 0) {
            txt.append("+");
          }
          txt.append("" + ca.getValue());
        }
      }
      txt.append("\n");
    }
    txt.append("\n");
    return txt.toString();
  }

  private void insert(
      final Map<Account.Id, Map<ApprovalCategory.Id, PatchSetApproval>> list,
      final PatchSetApproval ca) {
    Map<ApprovalCategory.Id, PatchSetApproval> m = list.get(ca.getAccountId());
    if (m == null) {
      m = new HashMap<ApprovalCategory.Id, PatchSetApproval>();
      list.put(ca.getAccountId(), m);
    }
    m.put(ca.getCategoryId(), ca);
  }
}
