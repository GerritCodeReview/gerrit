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

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountProjectWatch;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import java.util.HashMap;
import java.util.Map;

/** Send notice about a change successfully merged. */
public class MergedSender extends ReplyToChangeSender {
  public static interface Factory {
    public MergedSender create(Change change);
  }

  private final ApprovalTypes approvalTypes;
  private Branch.NameKey dest;

  @Inject
  public MergedSender(EmailArguments ea, ApprovalTypes at, @Assisted Change c) {
    super(ea, c, "merged");
    dest = c.getDest();
    approvalTypes = at;
  }

  public void setDest(final Branch.NameKey key) {
    dest = key;
  }

  @Override
  protected void init() {
    super.init();

    ccAllApprovals();
    bccStarredBy();
    bccWatchesNotifyAllComments();
    bccWatchesNotifySubmittedChanges();
  }

  @Override
  protected void format() {
    appendText("Change " + change.getKey().abbreviate());
    if (patchSetInfo != null && patchSetInfo.getAuthor() != null
        && patchSetInfo.getAuthor().getName() != null) {
      appendText(" by ");
      appendText(patchSetInfo.getAuthor().getName());
    }
    appendText(" submitted to ");
    appendText(dest.getShortName());
    appendText(":\n\n");
    formatChangeDetail();
    formatApprovals();
  }

  private void formatApprovals() {
    if (patchSet != null) {
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

        format("Approvals", pos);
        format("Objections", neg);
      } catch (OrmException err) {
        // Don't list the approvals
      }
    }
  }

  private void format(final String type,
      final Map<Account.Id, Map<ApprovalCategory.Id, PatchSetApproval>> list) {
    if (list.isEmpty()) {
      return;
    }
    appendText(type + ":\n");
    for (final Map.Entry<Account.Id, Map<ApprovalCategory.Id, PatchSetApproval>> ent : list
        .entrySet()) {
      final Map<ApprovalCategory.Id, PatchSetApproval> l = ent.getValue();
      appendText("  ");
      appendText(getNameFor(ent.getKey()));
      appendText(": ");
      boolean first = true;
      for (ApprovalType at : approvalTypes.getApprovalTypes()) {
        final PatchSetApproval ca = l.get(at.getCategory().getId());
        if (ca == null) {
          continue;
        }

        if (first) {
          first = false;
        } else {
          appendText("; ");
        }

        final ApprovalCategoryValue v = at.getValue(ca);
        if (v != null) {
          appendText(v.getName());
        } else {
          appendText(at.getCategory().getName());
          appendText("=");
          if (ca.getValue() > 0) {
            appendText("+");
          }
          appendText("" + ca.getValue());
        }
      }
      appendText("\n");
    }
    appendText("\n");
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

  private void bccWatchesNotifySubmittedChanges() {
    try {
      // BCC anyone else who has interest in this project's changes
      //
      for (final AccountProjectWatch w : getWatches()) {
        if (w.isNotifySubmittedChanges()) {
          add(RecipientType.BCC, w.getAccountId());
        }
      }
    } catch (OrmException err) {
      // Just don't CC everyone. Better to send a partial message to those
      // we already have queued up then to fail deliver entirely to people
      // who have a lower interest in the change.
    }
  }
}
