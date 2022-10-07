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

import static com.google.common.base.Preconditions.checkNotNull;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.flogger.FluentLogger;
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
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.util.Optional;

/** Send notice about a change successfully merged. */
public class MergedSender extends ReplyToChangeSender {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public interface Factory {
    MergedSender create(
        Project.NameKey project, Change.Id changeId, Optional<String> stickyApprovalDiff);
  }

  private final LabelTypes labelTypes;
  private final Optional<String> stickyApprovalDiff;

  @Inject
  public MergedSender(
      EmailArguments args,
      @Assisted Project.NameKey project,
      @Assisted Change.Id changeId,
      @Assisted Optional<String> stickyApprovalDiff) {
    super(args, "merged", newChangeData(args, project, changeId));
    labelTypes = changeData.getLabelTypes();
    this.stickyApprovalDiff = stickyApprovalDiff;
  }

  @Override
  public void setNotify(NotifyResolver.Result notify) {
    checkNotNull(notify);
    if (!stickyApprovalDiff.isEmpty()) {
      if (notify.handling() != NotifyHandling.ALL) {
        logger.atFine().log(
            "Requested to notify %s, but for change submission with sticky approval diff,"
                + " Notify=ALL is enforced.",
            notify.handling().name());
      }
      this.notify = NotifyResolver.Result.create(NotifyHandling.ALL, notify.accounts());
    } else {
      this.notify = notify;
    }
  }

  @Override
  protected void init() throws EmailException {
    // We want to send the submit email even if the "send only when in attention set" is enabled.
    emailOnlyAttentionSetIfEnabled = false;

    super.init();

    ccAllApprovals();
    bccStarredBy();
    includeWatchers(NotifyType.ALL_COMMENTS);
    includeWatchers(NotifyType.SUBMITTED_CHANGES);
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
      for (PatchSetApproval ca : args.approvalsUtil.byPatchSet(changeData.notes(), patchSet.id())) {
        Optional<LabelType> lt = labelTypes.byLabel(ca.labelId());
        if (!lt.isPresent()) {
          continue;
        }
        if (ca.value() > 0) {
          pos.put(ca.accountId(), lt.get().getName(), ca);
        } else if (ca.value() < 0) {
          neg.put(ca.accountId(), lt.get().getName(), ca);
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
    if (stickyApprovalDiff.isPresent()) {
      soyContextEmailData.put("stickyApprovalDiff", stickyApprovalDiff.get());
      soyContextEmailData.put(
          "stickyApprovalDiffHtml", getDiffTemplateData(stickyApprovalDiff.get()));
    }
  }
}
