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

package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.ChangeHookRunner;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.mail.CommentSender;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ApproveCommand extends BaseCommand {
  private static final Logger log =
      LoggerFactory.getLogger(ApproveCommand.class);

  @Override
  protected final CmdLineParser newCmdLineParser() {
    final CmdLineParser parser = super.newCmdLineParser();
    for (ApproveOption c : optionList) {
      parser.addOption(c, c);
    }
    return parser;
  }

  private final Set<PatchSet.Id> patchSetIds = new HashSet<PatchSet.Id>();

  @Argument(index = 0, required = true, multiValued = true, metaVar = "{COMMIT | CHANGE,PATCHSET}", usage = "patch to approve")
  void addPatchSetId(final String token) {
    try {
      patchSetIds.addAll(parsePatchSetId(token));
    } catch (UnloggedFailure e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    } catch (OrmException e) {
      throw new IllegalArgumentException("database error", e);
    }
  }

  @Option(name = "--project", aliases = "-p", usage = "project containing the patch set")
  private ProjectControl projectControl;

  @Option(name = "--message", aliases = "-m", usage = "cover message to publish on change", metaVar = "MESSAGE")
  private String changeComment;

  @Inject
  private ReviewDb db;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private CommentSender.Factory commentSenderFactory;

  @Inject
  private PatchSetInfoFactory patchSetInfoFactory;

  @Inject
  private ApprovalTypes approvalTypes;

  @Inject
  private ChangeControl.Factory changeControlFactory;

  @Inject
  private FunctionState.Factory functionStateFactory;

  @Inject
  private ChangeHookRunner hooks;

  private List<ApproveOption> optionList;

  @Override
  public final void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Failure {
        initOptionList();
        parseCommandLine();

        boolean ok = true;
        for (final PatchSet.Id patchSetId : patchSetIds) {
          try {
            approveOne(patchSetId);
          } catch (UnloggedFailure e) {
            ok = false;
            writeError("error: " + e.getMessage() + "\n");
          } catch (Exception e) {
            ok = false;
            writeError("fatal: internal server error while approving "
                + patchSetId + "\n");
            log.error("internal error while approving " + patchSetId);
          }
        }
        if (!ok) {
          throw new UnloggedFailure(1, "one or more approvals failed;"
              + " review output above");
        }
      }
    });
  }

  private void approveOne(final PatchSet.Id patchSetId)
      throws NoSuchChangeException, UnloggedFailure, OrmException,
      PatchSetInfoNotAvailableException, EmailException {
    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl changeControl =
        changeControlFactory.validateFor(changeId);
    final Change change = changeControl.getChange();

    final StringBuffer msgBuf = new StringBuffer();
    msgBuf.append("Patch Set ");
    msgBuf.append(patchSetId.get());
    msgBuf.append(": ");

    final Map<ApprovalCategory.Id, ApprovalCategoryValue.Id> approvalsMap =
        new HashMap<ApprovalCategory.Id, ApprovalCategoryValue.Id>();

    if (change.getStatus().isOpen()) {
      for (ApproveOption co : optionList) {
        final ApprovalCategory.Id category = co.getCategoryId();
        PatchSetApproval.Key psaKey =
            new PatchSetApproval.Key(patchSetId, currentUser.getAccountId(),
                category);
        PatchSetApproval psa = db.patchSetApprovals().get(psaKey);

        Short score = co.value();

        if (score != null) {
          addApproval(psaKey, score, change, co);
        } else {
          if (psa == null) {
            score = 0;
            addApproval(psaKey, score, change, co);
          } else {
            score = psa.getValue();
          }
        }

        final ApprovalCategoryValue.Id val =
            new ApprovalCategoryValue.Id(category, score);

        String message = db.approvalCategoryValues().get(val).getName();
        msgBuf.append(" " + message + ";");
        approvalsMap.put(category, val);
      }
    }

    msgBuf.deleteCharAt(msgBuf.length() - 1);
    msgBuf.append("\n\n");

    if (changeComment != null) {
      msgBuf.append(changeComment);
    }

    String uuid = ChangeUtil.messageUUID(db);
    ChangeMessage cm =
        new ChangeMessage(new ChangeMessage.Key(changeId, uuid), currentUser
            .getAccountId());
    cm.setMessage(msgBuf.toString());
    db.changeMessages().insert(Collections.singleton(cm));

    ChangeUtil.touch(change, db);
    sendMail(change, change.currentPatchSetId(), cm);

    hooks.doCommentAddedHook(change, currentUser.getAccount(), db.patchSets()
        .get(patchSetId), changeComment, approvalsMap);
  }

  private Set<PatchSet.Id> parsePatchSetId(final String patchIdentity)
      throws UnloggedFailure, OrmException {
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
          return matches;
        case 0:
          throw error("\"" + patchIdentity + "\" no such patch set");
        default:
          throw error("\"" + patchIdentity + "\" matches multiple patch sets");
      }
    }

    // By older style change,patchset?
    //
    if (patchIdentity.matches("^[1-9][0-9]*,[1-9][0-9]*$")) {
      final PatchSet.Id patchSetId;
      try {
        patchSetId = PatchSet.Id.parse(patchIdentity);
      } catch (IllegalArgumentException e) {
        throw error("\"" + patchIdentity + "\" is not a valid patch set");
      }
      if (db.patchSets().get(patchSetId) == null) {
        throw error("\"" + patchIdentity + "\" no such patch set");
      }
      if (projectControl != null) {
        final Change change = db.changes().get(patchSetId.getParentKey());
        if (!inProject(change)) {
          throw error("change " + change.getId() + " not in project "
              + projectControl.getProject().getName());
        }
      }
      return Collections.singleton(patchSetId);
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
      final Short score, final Change c, final ApproveOption co)
      throws OrmException, UnloggedFailure {
    final PatchSetApproval psa = new PatchSetApproval(psaKey, score);
    final List<PatchSetApproval> approvals = Collections.emptyList();
    final FunctionState fs =
        functionStateFactory.create(c, psaKey.getParentKey(), approvals);
    psa.setValue(score);
    fs.normalize(approvalTypes.getApprovalType(psa.getCategoryId()), psa);
    if (score != psa.getValue()) {
      throw error(co.name() + "=" + co.value() + " not permitted");
    }

    psa.setGranted();
    db.patchSetApprovals().upsert(Collections.singleton(psa));
  }

  private void initOptionList() {
    optionList = new ArrayList<ApproveOption>();

    for (ApprovalType type : approvalTypes.getApprovalTypes()) {
      String usage = "";
      final ApprovalCategory category = type.getCategory();
      usage = "score for " + category.getName() + "\n";

      for (ApprovalCategoryValue v : type.getValues()) {
        usage += v.format() + "\n";
      }

      final String name =
          "--" + category.getName().toLowerCase().replace(' ', '-');
      optionList.add(new ApproveOption(name, usage, type));
    }
  }

  private void writeError(final String msg) {
    try {
      err.write(msg.getBytes(ENC));
    } catch (IOException e) {
    }
  }

  private static UnloggedFailure error(final String msg) {
    return new UnloggedFailure(1, msg);
  }
}
