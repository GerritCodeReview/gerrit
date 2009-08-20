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
import com.google.gerrit.client.reviewdb.PatchSet.Id;
import com.google.gerrit.pgm.CmdLineParser;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.CommentSender;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.CommentSender.Factory;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.ssh.BaseCommand;
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

  protected final CmdLineParser newCmdLineParserInstance(final Object bean) {
    CmdLineParser parser = new CmdLineParser(bean);

    for (CmdOption c : optionList) {
      parser.addOption(c, c);
    }

    return parser;
  }

  private static final int CMD_ERR = 3;

  @Argument(index = 0, required = true, usage = "Patch set to approve")
  private Id patchSetId;
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

  private List<CmdOption> optionList;

  @Override
  public final void start() throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        getApprovalNames();
        parseCommandLine();

        try {
          final Transaction txn = db.beginTransaction();

          final PatchSet ps = db.patchSets().get(patchSetId);

          if (ps == null) {
            throw new Failure(CMD_ERR, "Invalid patchset id");
          }

          final Change.Id cid = ps.getId().getParentKey();
          final Change c = db.changes().get(cid);

          if (c == null) {
            throw new Failure(CMD_ERR, "Invalid change id");
          }

          if (c.getStatus().isClosed()) {
            throw new Failure(CMD_ERR, "Change is closed.");
          }

          StringBuffer sb = new StringBuffer();
          sb.append("Patch Set: ");
          sb.append(c.currentPatchSetId().get());
          sb.append(" ");

          for (CmdOption co : optionList) {
            String message = "";
            Short score = co.value();

            ApprovalCategory.Id category =
                new ApprovalCategory.Id(co.approvalKey());
            if (co.value() != null) {
              addApproval(c, category, co.value(), txn);
            } else {
              PatchSetApproval.Key psaKey =
                  new PatchSetApproval.Key(c.currentPatchSetId(), currentUser
                      .getAccountId(), category);

              PatchSetApproval psa = db.patchSetApprovals().get(psaKey);
              if (psa == null) {
                score = null;
              } else {
                score = psa.getValue();
              }
            }

            if (score != null) {
              message =
                  db.approvalCategoryValues().get(
                      new ApprovalCategoryValue.Id(category, score)).getName();
            }

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
        } catch (OrmException e) {
          throw new Failure(CMD_ERR, "Error accessing the database\n", e);
        } catch (EmailException e) {
          throw new Failure(CMD_ERR, "Error when trying to send email\n", e);
        } catch (Exception e) {
          throw new Failure(CMD_ERR, "Received an error\n", e);
        }
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

  private void addApproval(final Change c, final ApprovalCategory.Id cat,
      final short score, final Transaction txn) throws OrmException {
    PatchSetApproval.Key psaKey =
        new PatchSetApproval.Key(c.currentPatchSetId(), currentUser
            .getAccountId(), cat);

    PatchSetApproval psa = db.patchSetApprovals().get(psaKey);

    if (psa == null) {
      psa = new PatchSetApproval(psaKey, score);
      db.patchSetApprovals().insert(Collections.singleton(psa), txn);
    } else {
      psa.setGranted();
      psa.setValue(score);
      db.patchSetApprovals().update(Collections.singleton(psa), txn);
    }
  }

  private void getApprovalNames() throws OrmException {
    optionList = new ArrayList<CmdOption>();

    for (ApprovalType type : approvalTypes.getApprovalTypes()) {
      String usage = "";
      final ApprovalCategory category = type.getCategory();
      if (category.getFunctionName().equals("MaxWithBlock")) {
        usage = "Score for " + category.getName() + "\n";

        for (ApprovalCategoryValue v : type.getValues()) {
          usage +=
              String.format("%4d", v.getValue()) + "  -  " + v.getName() + "\n";
        }
      }

      optionList.add(
          new CmdOption(
              "--" + category.getName().toLowerCase().replace(' ', '-'), usage,
              category.getId().get(), type.getMin().getValue(),
              type.getMax().getValue(), category.getName()));
    }
  }
}
