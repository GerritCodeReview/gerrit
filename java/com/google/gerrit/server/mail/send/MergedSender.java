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

package com.google.gerrit.server.mail.send;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.NotifyConfig.NotifyType;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.entities.Project;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.exceptions.StorageException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

/** Send notice about a change successfully merged. */
public class MergedSender extends ReplyToChangeSender {
  public interface Factory {
    MergedSender create(Project.NameKey project, Change.Id changeId);
  }

  private final LabelTypes labelTypes;

  @Inject
  public MergedSender(
      EmailArguments args, @Assisted Project.NameKey project, @Assisted Change.Id changeId) {
    super(args, "merged", newChangeData(args, project, changeId));
    labelTypes = changeData.getLabelTypes();
  }

  @Override
  protected void init() throws EmailException {
    super.init();

    ccAllApprovals();
    bccStarredBy();
    includeWatchers(NotifyType.ALL_COMMENTS);
    includeWatchers(NotifyType.SUBMITTED_CHANGES);
    removeUsersThatIgnoredTheChange();
  }

  @Override
  protected void formatChange() throws EmailException {
    appendText(textTemplate("Merged"));

    if (useHtml()) {
      appendHtml(soyHtmlTemplate("MergedHtml"));
    }
  }

  public String getApprovals() {
    try {
      Table<Account.Id, String, PatchSetApproval> pos = HashBasedTable.create();
      Table<Account.Id, String, PatchSetApproval> neg = HashBasedTable.create();
      for (PatchSetApproval ca :
          args.approvalsUtil.byPatchSet(changeData.notes(), patchSet.id(), null, null)) {
        LabelType lt = labelTypes.byLabel(ca.labelId());
        if (lt == null) {
          continue;
        }
        if (ca.value() > 0) {
          pos.put(ca.accountId(), lt.getName(), ca);
        } else if (ca.value() < 0) {
          neg.put(ca.accountId(), lt.getName(), ca);
        }
      }

      return format("Approvals", pos) + format("Objections", neg);
    } catch (StorageException err) {
      // Don't list the approvals
    }
    return "";
  }

  private String format(String type, Table<Account.Id, String, PatchSetApproval> approvals) {
    StringBuilder txt = new StringBuilder();
    if (approvals.isEmpty()) {
      return "";
    }
    txt.append(type).append(":\n");
    for (Account.Id id : approvals.rowKeySet()) {
      txt.append("  ");
      txt.append(getNameFor(id));
      txt.append(": ");
      boolean first = true;
      for (LabelType lt : labelTypes.getLabelTypes()) {
        PatchSetApproval ca = approvals.get(id, lt.getName());
        if (ca == null) {
          continue;
        }

        if (first) {
          first = false;
        } else {
          txt.append("; ");
        }

        LabelValue v = lt.getValue(ca);
        if (v != null) {
          txt.append(v.getText());
        } else {
          txt.append(lt.getName());
          txt.append('=');
          txt.append(LabelValue.formatValue(ca.value()));
        }
      }
      txt.append('\n');
    }
    txt.append('\n');
    return txt.toString();
  }

  @Override
  protected void setupSoyContext() {
    super.setupSoyContext();
    soyContextEmailData.put("approvals", getApprovals());
  }
}
