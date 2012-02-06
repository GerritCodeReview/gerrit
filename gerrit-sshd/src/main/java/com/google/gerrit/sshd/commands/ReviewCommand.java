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

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.data.ReviewResult;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.changedetail.AbandonChange;
import com.google.gerrit.server.changedetail.PublishDraft;
import com.google.gerrit.server.changedetail.RestoreChange;
import com.google.gerrit.server.changedetail.Submit;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.patch.PublishComments;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.InvalidChangeOperationException;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class ReviewCommand extends BaseCommand {
  private static final Logger log =
      LoggerFactory.getLogger(ReviewCommand.class);

  @Override
  protected final CmdLineParser newCmdLineParser() {
    final CmdLineParser parser = super.newCmdLineParser();
    for (ApproveOption c : optionList) {
      parser.addOption(c, c);
    }
    return parser;
  }

  private final Set<PatchSet.Id> patchSetIds = new HashSet<PatchSet.Id>();

  @Argument(index = 0, required = true, multiValued = true, metaVar = "{COMMIT | CHANGE,PATCHSET}", usage = "patch to review")
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

  @Option(name = "--abandon", usage = "abandon the patch set")
  private boolean abandonChange;

  @Option(name = "--restore", usage = "restore an abandoned the patch set")
  private boolean restoreChange;

  @Option(name = "--submit", aliases = "-s", usage = "submit the patch set")
  private boolean submitChange;

  @Option(name = "--force-message", usage = "publish the message, "
      + "even if the label score cannot be applied due to change being closed")
  private boolean forceMessage = false;

  @Option(name = "--publish", usage = "publish a draft patch set")
  private boolean publishPatchSet;

  @Option(name = "--delete", usage = "delete a draft patch set")
  private boolean deleteDraftPatchSet;

  @Inject
  private ReviewDb db;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ApprovalTypes approvalTypes;

  @Inject
  private ChangeControl.Factory changeControlFactory;

  @Inject
  private AbandonChange.Factory abandonChangeFactory;

  @Inject
  private FunctionState.Factory functionStateFactory;

  @Inject
  private PublishComments.Factory publishCommentsFactory;

  @Inject
  private PublishDraft.Factory publishDraftFactory;

  @Inject
  private RestoreChange.Factory restoreChangeFactory;

  @Inject
  private GitRepositoryManager gitManager;

  @Inject
  private ReplicationQueue replication;

  @Inject
  private Submit.Factory submitFactory;

  @Inject
  private PatchSetInfoFactory patchSetInfoFactory;

  private List<ApproveOption> optionList;

  @Override
  public final void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Failure {
        initOptionList();
        parseCommandLine();
        if (abandonChange) {
          if (restoreChange) {
            throw error("abandon and restore actions are mutually exclusive");
          }
          if (submitChange) {
            throw error("abandon and submit actions are mutually exclusive");
          }
          if (publishPatchSet) {
            throw error("abandon and publish actions are mutually exclusive");
          }
          if (deleteDraftPatchSet) {
            throw error("abandon and delete actions are mutually exclusive");
          }
        }
        if (publishPatchSet) {
          if (restoreChange) {
            throw error("publish and restore actions are mutually exclusive");
          }
          if (submitChange) {
            throw error("publish and submit actions are mutually exclusive");
          }
          if (deleteDraftPatchSet) {
            throw error("publish and delete actions are mutually exclusive");
          }
        }

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
            log.error("internal error while approving " + patchSetId, e);
          }
        }

        if (!ok) {
          throw new UnloggedFailure(1, "one or more approvals failed;"
              + " review output above");
        }

      }
    });
  }

  private void approveOne(final PatchSet.Id patchSetId) throws
      NoSuchChangeException, OrmException, EmailException, Failure {

    final Change.Id changeId = patchSetId.getParentKey();

    ChangeControl changeControl = changeControlFactory.validateFor(changeId);

    if (changeComment == null) {
      changeComment = "";
    }

    Set<ApprovalCategoryValue.Id> aps = new HashSet<ApprovalCategoryValue.Id>();
    for (ApproveOption ao : optionList) {
      Short v = ao.value();
      if (v != null) {
        assertScoreIsAllowed(patchSetId, changeControl, ao, v);
        aps.add(new ApprovalCategoryValue.Id(ao.getCategoryId(), v));
      }
    }

    try {
      publishCommentsFactory.create(patchSetId, changeComment, aps, forceMessage).call();

      if (abandonChange) {
        ReviewResult result = abandonChangeFactory.create(
            patchSetId, changeComment).call();
        handleReviewResultErrors(result);
      } else if (restoreChange) {
        ReviewResult result = restoreChangeFactory.create(
            patchSetId, changeComment).call();
        handleReviewResultErrors(result);
      }
      if (submitChange) {
        ReviewResult result = submitFactory.create(patchSetId).call();
        handleReviewResultErrors(result);
      }
    } catch (InvalidChangeOperationException e) {
      throw error(e.getMessage());
    } catch (IllegalStateException e) {
      throw error(e.getMessage());
    }

    if (publishPatchSet) {
      ReviewResult result = publishDraftFactory.create(patchSetId).call();
      handleReviewResultErrors(result);
    } else if (deleteDraftPatchSet) {
      if (changeControl.canDelete(db)) {
        try {
          ChangeUtil.deleteDraftPatchSet(patchSetId, gitManager, replication, patchSetInfoFactory, db);
        } catch (PatchSetInfoNotAvailableException e) {
          throw error("Error retrieving draft patchset: " + patchSetId);
        } catch (IOException e) {
          throw error("Error deleting draft patchset: " + patchSetId);
        }
      } else {
        throw error("Not permitted to delete draft patchset");
      }
    }
  }

  private void handleReviewResultErrors(final ReviewResult result) {
    for (ReviewResult.Error resultError : result.getErrors()) {
      String errMsg = "error: (change " + result.getChangeId() + ") ";
      switch (resultError.getType()) {
        case ABANDON_NOT_PERMITTED:
          errMsg += "not permitted to abandon change";
          break;
        case RESTORE_NOT_PERMITTED:
          errMsg += "not permitted to restore change";
          break;
        case SUBMIT_NOT_PERMITTED:
          errMsg += "not permitted to submit change";
          break;
        case SUBMIT_NOT_READY:
          errMsg += "approvals or dependencies lacking";
          break;
        case CHANGE_IS_CLOSED:
          errMsg += "change is closed";
          break;
        case PUBLISH_NOT_PERMITTED:
          errMsg += "not permitted to publish change";
          break;
        case RULE_ERROR:
          errMsg += "rule error";
          break;
        default:
          errMsg += "failure in review";
      }
      if (resultError.getMessage() != null) {
        errMsg += ": " + resultError.getMessage();
      }
      writeError(errMsg);
    }
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

  private void assertScoreIsAllowed(final PatchSet.Id patchSetId,
      final ChangeControl changeControl, ApproveOption ao, Short v)
      throws UnloggedFailure {
    final PatchSetApproval psa =
        new PatchSetApproval(new PatchSetApproval.Key(patchSetId, currentUser
            .getAccountId(), ao.getCategoryId()), v);
    final FunctionState fs =
        functionStateFactory.create(changeControl, patchSetId,
            Collections.<PatchSetApproval> emptyList());
    psa.setValue(v);
    fs.normalize(approvalTypes.byId(psa.getCategoryId()), psa);
    if (v != psa.getValue()) {
      throw error(ao.name() + "=" + ao.value() + " not permitted");
    }
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
