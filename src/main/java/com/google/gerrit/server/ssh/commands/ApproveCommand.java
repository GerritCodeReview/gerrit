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
import com.google.gerrit.client.reviewdb.RevId;
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
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.ssh.BaseCommand;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.Transaction;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ApproveCommand extends BaseCommand {
  @Override
  protected final CmdLineParser newCmdLineParser() {
    final CmdLineParser parser = super.newCmdLineParser();
    for (ApproveOption c : optionList) {
      parser.addOption(c, c);
    }
    return parser;
  }

  @Argument(index = 0, required = true, metaVar = "{COMMIT | CHANGE,PATCHSET}", usage = "patch to approve")
  private String patchIdentity;

  @Option(name = "--project", aliases = "-p", usage = "project containing the patch set")
  private ProjectControl projectControl;

  @Option(name = "--message", aliases = "-m", usage = "cover message to publish on change", metaVar = "MESSAGE")
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

  private List<ApproveOption> optionList;

  @Override
  public final void start() {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        initOptionList();
        parseCommandLine();

        final PatchSet.Id patchSetId = parsePatchSetId();
        final Change.Id changeId = patchSetId.getParentKey();
        final ChangeControl changeControl =
            changeControlFactory.validateFor(changeId);
        final Change change = changeControl.getChange();

        if (change.getStatus().isClosed()) {
          throw error("change " + changeId + " is closed");
        }
        if (!inProject(change)) {
          throw error("change " + changeId + " not in project "
              + projectControl.getProject().getName());
        }

        final Transaction txn = db.beginTransaction();
        final StringBuffer msgBuf = new StringBuffer();
        msgBuf.append("Patch Set ");
        msgBuf.append(patchSetId.get());
        msgBuf.append(": ");

        for (ApproveOption co : optionList) {
          final ApprovalCategory.Id category = co.getCategoryId();
          PatchSetApproval.Key psaKey =
              new PatchSetApproval.Key(patchSetId, currentUser.getAccountId(),
                  category);
          PatchSetApproval psa = db.patchSetApprovals().get(psaKey);

          Short score = co.value();

          if (score != null) {
            addApproval(psaKey, score, change, co, txn);
          } else {
            if (psa == null) {
              score = 0;
              addApproval(psaKey, score, change, co, txn);
            } else {
              score = psa.getValue();
            }
          }

          String message =
              db.approvalCategoryValues().get(
                  new ApprovalCategoryValue.Id(category, score)).getName();
          msgBuf.append(" " + message + ";");
        }

        msgBuf.deleteCharAt(msgBuf.length() - 1);
        msgBuf.append("\n\n");

        if (changeComment != null) {
          msgBuf.append(changeComment);
        }

        String uuid = ChangeUtil.messageUUID(db);
        ChangeMessage cm =
            new ChangeMessage(new ChangeMessage.Key(changeId, uuid),
                currentUser.getAccountId());
        cm.setMessage(msgBuf.toString());
        db.changeMessages().insert(Collections.singleton(cm), txn);
        ChangeUtil.updated(change);
        db.changes().update(Collections.singleton(change), txn);
        txn.commit();
        sendMail(change, change.currentPatchSetId(), cm);
      }
    });
  }

  private PatchSet.Id parsePatchSetId() throws UnloggedFailure, OrmException {
    // By commit?
    //
    if (patchIdentity.matches("^([0-9a-fA-F]{4," + RevId.LEN + "})$")) {
      final RevId id = new RevId(patchIdentity);
      final ResultSet<PatchSet> patches;
      if (id.isComplete()) {
        patches = db.patchSets().byRevision(id);
      } else {
        patches = db.patchSets().byRevisionRange(id, id.max());
      }

      final Set<PatchSet.Id> matches = new HashSet<PatchSet.Id>();
      for (final PatchSet ps : patches) {
        final Change change = db.changes().get(ps.getId().getParentKey());
        if (inProject(change)) {
          matches.add(ps.getId());
        }
      }

      switch (matches.size()) {
        case 1:
          return matches.iterator().next();
        case 0:
          throw error("\"" + patchIdentity + "\" no such patch set");
        default:
          throw error("\"" + patchIdentity + "\" matches multiple patch sets");
      }
    }

    // By older style change,patchset?
    //
    if (patchIdentity.matches("^[1-9][0-9]*,[1-9][0-9]$")) {
      final PatchSet.Id patchSetId;
      try {
        patchSetId = PatchSet.Id.parse(patchIdentity);
      } catch (IllegalArgumentException e) {
        throw error("\"" + patchIdentity + "\" is not a valid patch set");
      }
      if (db.patchSets().get(patchSetId) == null) {
        throw error("\"" + patchIdentity + "\" no such patch set");
      }
      return patchSetId;
    }

    throw error("\"" + patchIdentity + "\" is not a valid patch set");
  }

  private boolean inProject(final Change change) {
    if (projectControl == null) {
      // No --project option, so they want every project.
      return true;
    }
    return projectControl.getProject().getNameKey().equals(change.getProject());
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
      final Short score, final Change c, final ApproveOption co,
      final Transaction txn) throws OrmException, UnloggedFailure {
    PatchSetApproval psa = db.patchSetApprovals().get(psaKey);
    boolean insert = false;

    if (psa == null) {
      insert = true;
      psa = new PatchSetApproval(psaKey, score);
    }

    final List<PatchSetApproval> approvals = Collections.emptyList();
    final FunctionState fs =
        functionStateFactory.create(c, psaKey.getParentKey(), approvals);
    psa.setValue(score);
    fs.normalize(approvalTypes.getApprovalType(psa.getCategoryId()), psa);
    if (score != psa.getValue()) {
      throw error(co.name() + "=" + co.value() + " not permitted");
    }

    psa.setGranted();

    if (insert) {
      db.patchSetApprovals().insert(Collections.singleton(psa), txn);
    } else {
      db.patchSetApprovals().update(Collections.singleton(psa), txn);
    }
  }

  private void initOptionList() {
    optionList = new ArrayList<ApproveOption>();

    for (ApprovalType type : approvalTypes.getApprovalTypes()) {
      String usage = "";
      final ApprovalCategory category = type.getCategory();
      usage = "score for " + category.getName() + "\n";

      for (ApprovalCategoryValue v : type.getValues()) {
        usage +=
            String.format("%3s", ApproveOption.format(v.getValue())) + ": "
                + v.getName() + "\n";
      }

      final String name =
          "--" + category.getName().toLowerCase().replace(' ', '-');
      optionList.add(new ApproveOption(name, usage, type));
    }
  }

  private static UnloggedFailure error(final String msg) {
    return new UnloggedFailure(1, msg);
  }
}
