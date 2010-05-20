// Copyright (c) 2010, Code Aurora Forum. All rights reserved.
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

import static com.google.gerrit.reviewdb.ApprovalCategory.SUBMIT;

import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.MergeQueue;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.patch.PatchSetInfoNotAvailableException;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.workflow.CategoryFunction;
import com.google.gerrit.server.workflow.FunctionState;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;

import com.google.gwtorm.client.AtomicUpdate;

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

public class SubmitCommand extends BaseCommand {
  private static final Logger log =
      LoggerFactory.getLogger(SubmitCommand.class);

  private final Set<PatchSet.Id> patchSetIds = new HashSet<PatchSet.Id>();

  @Argument(index = 0, required = true, multiValued = true, metaVar = "{COMMIT | CHANGE,PATCHSET}", usage = "patch to submit")
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

  @Inject
  private ReviewDb db;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private MergeQueue merger;

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
  public final void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Failure {
        parseCommandLine();

        boolean ok = true;
        for (final PatchSet.Id patchSetId : patchSetIds) {
          try {
            submitOne(patchSetId);
          } catch (UnloggedFailure e) {
            ok = false;
            writeError("error: " + e.getMessage() + "\n");
          } catch (Exception e) {
            ok = false;
            writeError("fatal: internal server error while submitting "
                + patchSetId + "\n");
            log.error("internal error while submitting " + patchSetId);
          }
        }
        if (!ok) {
          throw new UnloggedFailure(1, "one or more submittals failed;"
              + " review output above");
        }
      }
    });
  }

  private void submitOne(final PatchSet.Id patchSetId)
      throws NoSuchChangeException, UnloggedFailure, OrmException,
      PatchSetInfoNotAvailableException {

    final Change.Id changeId = patchSetId.getParentKey();
    final ChangeControl changeControl =
        changeControlFactory.validateFor(changeId);
    Change change = changeControl.getChange();

    if (!patchSetId.equals(change.currentPatchSetId())) {
      throw new IllegalStateException("Patch set " + patchSetId
          + " not current");
    }
    if (change.getStatus().isClosed()) {
      throw new IllegalStateException("Change" + changeId + " is closed");
    }

    final List<PatchSetApproval> allApprovals =
        new ArrayList<PatchSetApproval>(db.patchSetApprovals().byPatchSet(
            patchSetId).toList());

    final PatchSetApproval.Key ak =
        new PatchSetApproval.Key(patchSetId, currentUser.getAccountId(), SUBMIT);
    PatchSetApproval myAction = null;
    for (final PatchSetApproval ca : allApprovals) {
      if (ak.equals(ca.getKey())) {
        myAction = ca;
        myAction.setValue((short) 1);
        myAction.setGranted();
        break;
      }
    }
    if (myAction == null) {
      myAction = new PatchSetApproval(ak, (short) 1);
      allApprovals.add(myAction);
    }

    final ApprovalType actionType =
        approvalTypes.getApprovalType(myAction.getCategoryId());
    if (actionType == null || !actionType.getCategory().isAction()) {
      throw new IllegalArgumentException(myAction.getCategoryId() + " not an action");
    }

    final FunctionState fs =
        functionStateFactory.create(change, patchSetId, allApprovals);
    for (ApprovalType c : approvalTypes.getApprovalTypes()) {
      CategoryFunction.forCategory(c.getCategory()).run(c, fs);
    }
    if (!CategoryFunction.forCategory(actionType.getCategory()).isValid(currentUser,
        actionType, fs)) {
      throw new IllegalStateException(actionType.getCategory().getName()
          + " not permitted");
    }
    fs.normalize(actionType, myAction);
    if (myAction.getValue() <= 0) {
      throw new IllegalStateException(actionType.getCategory().getName()
          + " not permitted");
    }

    db.patchSetApprovals().upsert(Collections.singleton(myAction));

    change = db.changes().atomicUpdate(changeId, new AtomicUpdate<Change>() {
      @Override
      public Change update(Change change) {
        if (change.getStatus() == Change.Status.NEW) {
          change.setStatus(Change.Status.SUBMITTED);
          ChangeUtil.updated(change);
        }
        return change;
      }
    });

    if (change.getStatus() == Change.Status.SUBMITTED) {
      merger.merge(change.getDest());
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
