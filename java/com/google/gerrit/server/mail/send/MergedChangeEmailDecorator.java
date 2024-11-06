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

import com.google.auto.factory.AutoFactory;
import com.google.auto.factory.Provided;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.LabelType;
import com.google.gerrit.entities.LabelTypes;
import com.google.gerrit.entities.LabelValue;
import com.google.gerrit.entities.NotifyConfig.NotifyType;
import com.google.gerrit.entities.PatchSetApproval;
import com.google.gerrit.exceptions.EmailException;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.mail.send.ChangeEmail.ChangeEmailDecorator;
import com.google.gerrit.server.patch.filediff.FileDiffOutput;
import java.util.List;
import java.util.Optional;

/** Send notice about a change successfully merged. */
@AutoFactory
public class MergedChangeEmailDecorator implements ChangeEmailDecorator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  protected OutgoingEmail email;
  protected ChangeEmail changeEmail;
  protected LabelTypes labelTypes;
  protected final EmailArguments args;
  protected final Optional<String> stickyApprovalDiff;
  // This is only used in google-internal override.
  // It is helpful to keep this here, for bringing internal override into
  // upstream later
  protected final List<FileDiffOutput> modifiedFiles;

  public MergedChangeEmailDecorator(
      @Provided EmailArguments args,
      Optional<String> stickyApprovalDiff,
      List<FileDiffOutput> modifiedFiles) {
    this.args = args;
    this.stickyApprovalDiff = stickyApprovalDiff;
    this.modifiedFiles = modifiedFiles;
  }

  @Override
  public void init(OutgoingEmail email, ChangeEmail changeEmail) {
    this.email = email;
    this.changeEmail = changeEmail;
    changeEmail.markAsReply();
    labelTypes = changeEmail.getChangeData().getLabelTypes();

    // We want to send the submit email even if the "send only when in attention set" is enabled.
    changeEmail.setEmailOnlyAttentionSetIfEnabled(false);

    NotifyResolver.Result notify = email.getNotify();
    if (!stickyApprovalDiff.isEmpty() && !notify.handling().equals(NotifyHandling.ALL)) {
      logger.atFine().log(
          "Requested to notify %s, but for change submission with sticky approval diff,"
              + " Notify=ALL is enforced.",
          notify.handling().name());
      email.setNotify(NotifyResolver.Result.create(NotifyHandling.ALL, notify.accounts()));
    }
  }

  protected String getApprovals() {
    try {
      Table<Account.Id, String, PatchSetApproval> pos = HashBasedTable.create();
      Table<Account.Id, String, PatchSetApproval> neg = HashBasedTable.create();
      for (PatchSetApproval ca :
          args.approvalsUtil.byPatchSet(
              changeEmail.getChangeData().notes(), changeEmail.getPatchSet().id())) {
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
      txt.append(email.getNameFor(id));
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
  public void populateEmailContent() throws EmailException {
    email.addSoyEmailDataParam("approvals", getApprovals());
    if (stickyApprovalDiff.isPresent()) {
      email.addSoyEmailDataParam("stickyApprovalDiff", stickyApprovalDiff.get());
      email.addSoyEmailDataParam(
          "stickyApprovalDiffHtml", ChangeEmail.getDiffTemplateData(stickyApprovalDiff.get()));
    }

    changeEmail.addAuthors(RecipientType.TO);
    changeEmail.ccAllApprovals();
    changeEmail.bccStarredBy();
    changeEmail.includeWatchers(NotifyType.ALL_COMMENTS);
    changeEmail.includeWatchers(NotifyType.SUBMITTED_CHANGES);

    email.appendText(email.textTemplate("Merged"));

    if (email.useHtml()) {
      email.appendHtml(email.soyHtmlTemplate("MergedHtml"));
    }
  }
}
