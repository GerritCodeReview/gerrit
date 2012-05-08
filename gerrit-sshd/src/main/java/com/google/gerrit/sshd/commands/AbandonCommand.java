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

import com.google.gerrit.common.data.ReviewResult;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.changedetail.AbandonChange;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class AbandonCommand extends BaseCommand {
  private static final Logger log =
      LoggerFactory.getLogger(ReviewCommand.class);

  private PatchSet.Id patchSetId;
  @Argument(index = 0, required = true, multiValued = true, metaVar = "{COMMIT | CHANGE,PATCHSET}", usage = "patch to review")
  void setPatchSetId(final String token) {
    try {
      patchSetId = parsePatchSetId(token);
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
  private AbandonChange.Factory abandonChangeFactory;

  @Override
  public final void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Failure {
        parseCommandLine();

        try {
          final ReviewResult result = abandonChangeFactory.create(
              patchSetId, changeComment).call();
          for (ReviewResult.Error resultError : result.getErrors()) {
            switch (resultError.getType()) {
              case ABANDON_NOT_PERMITTED:
                throw error("not permitted to abandon change");
              default:
                throw error("failure in review");
            }
          }
        } catch (InvalidChangeOperationException e) {
          throw error(e.getMessage());
        } catch (NoSuchChangeException e) {
          throw error("no such change " + patchSetId.getParentKey().get());
        } catch (Exception e) {
          log.error("internal error while abandoning " + patchSetId, e);
          throw error("fatal: internal server error while abandoning "
                      + patchSetId);
        }
      }
    });
  }

  private PatchSet.Id parsePatchSetId(final String patchIdentity)
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

      PatchSet.Id match = null;
      for (final PatchSet ps : patches) {
        final Change change = db.changes().get(ps.getId().getParentKey());
        if (inProject(change)) {
          if (match == null) {
            match = ps.getId();
          } else {
            throw error("\"" + patchIdentity
                        + "\" matches multiple patch sets");
          }
        }
      }
      if (match == null) {
        throw error("\"" + patchIdentity + "\" no such patch set");
      }
      return match;
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

  private UnloggedFailure error(final String msg) {
    return new UnloggedFailure(1, msg);
  }

}
