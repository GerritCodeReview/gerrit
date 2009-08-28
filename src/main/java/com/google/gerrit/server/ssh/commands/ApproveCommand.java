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

package com.google.gerrit.server.ssh.commands;

import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.data.ApprovalTypes;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetApproval;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.pgm.CmdLineParser;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.CommentSender;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.CommentSender.Factory;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.ssh.BaseCommand;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ApproveCommand extends BaseCommand {
  static {
    CmdLineParser.registerHandler(PatchSet.Id.class, PatchSetIdHandler.class);
  }

  @Override
  protected final CmdLineParser newCmdLineParser() {
    final CmdLineParser parser = new CmdLineParser(this);
    for (CmdOption c : optionList) {
      parser.addOption(c, c);
    }
    return parser;
  }

  private static final int CMD_ERR = 3;

  @Argument(index = 0, required = true, usage = "Patch set to approve")
  private PatchSet.Id patchSetId;
  @Option(name = "--message", aliases = "-m", usage = "Message to put on change/patchset", metaVar = "MESSAGE")
  private String changeComment;
  @Inject
  private ReviewDb db;
  @Inject
  private IdentifiedUser currentUser;
  @Inject
  private Factory commentSenderFactory;
  @Inject
  private PatchSetInfoFactory patchSetInfoFactory;
  @Inject
  private ApprovalTypes approvalTypes;
  @Inject
  private ChangeControl.Factory changeControlFactory;
  @Inject
  private FunctionState.Factory functionStateFactory;


  private List<CmdOption> optionList;

  @Override
  public final void start() throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        getApprovalNames();
        parseCommandLine();

        final Transaction txn = db.beginTransaction();

        final PatchSet ps = db.patchSets().get(patchSetId);

        if (ps == null) {
          throw new UnloggedFailure(CMD_ERR, "Invalid patchset id");
        }

        final Change.Id cid = ps.getId().getParentKey();
        final ChangeControl control = changeControlFactory.validateFor(cid);
        final Change c = control.getChange();

        if (c.getStatus().isClosed()) {
          throw new UnloggedFailure(CMD_ERR, "Change is closed.");
        }

        StringBuffer sb = new StringBuffer();
        sb.append("Patch Set ");
        sb.append(patchSetId.get());
        sb.append(": ");

        for (CmdOption co : optionList) {
          ApprovalCategory.Id category =
              new ApprovalCategory.Id(co.approvalKey());
          PatchSetApproval.Key psaKey =
              new PatchSetApproval.Key(patchSetId, currentUser
                  .getAccountId(), category);
          PatchSetApproval psa = db.patchSetApprovals().get(psaKey);

          Short score = co.value();

          if (score != null) {
            addApproval(psaKey, score, c, co, txn);
          } else {
            if (psa == null) {
                score = 0;
                addApproval(psaKey, score, c, co, txn);
            } else {
              score = psa.getValue();
            }
          }

          String message =
            db.approvalCategoryValues().get(
                new ApprovalCategoryValue.Id(category, score)).getName();
          sb.append(" " + message + ";");
        }

        sb.deleteCharAt(sb.length() - 1);
        sb.append("\n\n");

        if (changeComment != null) {
          sb.append(changeComment);
        }

        String uuid = ChangeUtil.messageUUID(db);
        ChangeMessage cm =
            new ChangeMessage(new ChangeMessage.Key(cid, uuid), currentUser
                .getAccountId());
        cm.setMessage(sb.toString());
        db.changeMessages().insert(Collections.singleton(cm), txn);
        ChangeUtil.updated(c);
        db.changes().update(Collections.singleton(c), txn);
        txn.commit();
        sendMail(c, c.currentPatchSetId(), cm);
      }
    });
  }

  private void sendMail(final Change c, final PatchSet.Id psid,
      final ChangeMessage message) throws PatchSetInfoNotAvailableException,
      EmailException, OrmException {
    PatchSet ps = db.patchSets().get(psid);
    final CommentSender cm;
    cm = commentSenderFactory.create(c);
    cm.setFrom(currentUser.getAccountId());
    cm.setPatchSet(ps, patchSetInfoFactory.get(psid));
    cm.setChangeMessage(message);
    cm.setReviewDb(db);
    cm.send();
  }

  private void addApproval(final PatchSetApproval.Key psaKey,
      final Short score, final Change c, final CmdOption co,
      final Transaction txn) throws OrmException,
      UnloggedFailure {
    PatchSetApproval psa = db.patchSetApprovals().get(psaKey);
    boolean insert = false;

    if (psa == null) {
      insert = true;
      psa = new PatchSetApproval(psaKey, score);
    }

    final List<PatchSetApproval> approvals = Collections.emptyList();
    final FunctionState fs =
      functionStateFactory.create(c, patchSetId, approvals);
    psa.setValue(score);
    fs.normalize(
        approvalTypes.getApprovalType(psa.getCategoryId()), psa);
    if (score != psa.getValue()) {
      throw new UnloggedFailure(CMD_ERR, co.name() + "=" + co.value()
          + " not permitted");
    }

    psa.setGranted();

    if (insert) {
      db.patchSetApprovals().insert(Collections.singleton(psa), txn);
    } else {
      db.patchSetApprovals().update(Collections.singleton(psa), txn);
    }
  }

  private void getApprovalNames() throws OrmException {
    optionList = new ArrayList<CmdOption>();

    for (ApprovalType type : approvalTypes.getApprovalTypes()) {
      String usage = "";
      final ApprovalCategory category = type.getCategory();
      usage = "Score for " + category.getName() + "\n";

      for (ApprovalCategoryValue v : type.getValues()) {
        usage +=
            String.format("%4d", v.getValue()) + "  -  " + v.getName() + "\n";
      }

      optionList.add(
          new CmdOption(
              "--" + category.getName().toLowerCase().replace(' ', '-'), usage,
              category.getId().get(), type.getMin().getValue(),
              type.getMax().getValue(), category.getName()));
    }
  }
}
