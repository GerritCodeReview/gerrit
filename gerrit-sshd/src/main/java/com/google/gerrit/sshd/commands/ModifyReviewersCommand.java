// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.common.data.ReviewerResult;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.patch.AddReviewer;
import com.google.gerrit.server.patch.RemoveReviewer;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.Set;

public class ModifyReviewersCommand extends BaseCommand {
  private static final Logger log =
      LoggerFactory.getLogger(ModifyReviewersCommand.class);

  @Option(name = "--project", aliases = "-p", usage = "project containing the change")
  private ProjectControl projectControl;

  @Option(name = "--add", aliases = {"-a"}, metaVar = "EMAIL", usage = "reviewer to add")
  void optionAdd(Account.Id who) {
    toAdd.add(who);
  }

  @Option(name = "--remove", aliases = {"-r"}, metaVar = "EMAIL", usage = "reviewer to remove")
  void optionRemove(Account.Id who) {
    toRemove.add(who);
  }

  @Argument(index = 0, required = true, multiValued = true, metaVar = "COMMIT", usage = "changes to modify")
  void addChange(String token) {
    try {
      changes.addAll(parseChangeId(token));
    } catch (UnloggedFailure e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    } catch (OrmException e) {
      throw new IllegalArgumentException("database is down", e);
    }
  }

  @Inject
  private ReviewDb db;

  @Inject
  private AddReviewer.Factory addReviewerFactory;

  @Inject
  private RemoveReviewer.Factory removeReviewerFactory;

  @Inject
  private ChangeControl.Factory changeControlFactory;

  private Set<Account.Id> toAdd = new HashSet<Account.Id>();
  private Set<Account.Id> toRemove = new HashSet<Account.Id>();
  private Set<Change.Id> changes = new HashSet<Change.Id>();

  @Override
  public final void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Failure {
        parseCommandLine();

        boolean ok = true;
        for (Change.Id changeId : changes) {
          try {
            ok &= modifyOne(changeId);
          } catch (Exception err) {
            ok = false;
            log.error("Error updating reviewers on change " + changeId, err);
            writeError("fatal", "internal error while updating " + changeId);
          }
        }

        if (!ok) {
          throw error("fatal: one or more updates failed; review output above");
        }
      }
    });
  }

  private boolean modifyOne(Change.Id changeId) throws Exception {
    changeControlFactory.validateFor(changeId);

    ReviewerResult result;
    boolean ok = true;

    // Remove reviewers
    //
    result = removeReviewerFactory.create(changeId, toRemove).call();
    ok &= result.getErrors().isEmpty();
    for (ReviewerResult.Error resultError : result.getErrors()) {
      String message;
      switch (resultError.getType()) {
        case REMOVE_NOT_PERMITTED:
          message = "not permitted to remove {0} from {1}";
          break;
        case COULD_NOT_REMOVE:
          message = "could not remove {0} from {1}";
          break;
        default:
          message = "could not remove {0}: {2}";
      }
      writeError("error", MessageFormat.format(message,
          resultError.getName(), changeId, resultError.getType()));
    }

    // Add reviewers
    //
    result =
        addReviewerFactory.create(changeId, stringSet(toAdd), false).call();
    ok &= result.getErrors().isEmpty();
    for (ReviewerResult.Error resultError : result.getErrors()) {
      String message;
      switch (resultError.getType()) {
        case REVIEWER_NOT_FOUND:
          message = "account {0} not found";
          break;
        case ACCOUNT_INACTIVE:
          message = "account {0} inactive";
          break;
        case CHANGE_NOT_VISIBLE:
          message = "change {1} not visible to {0}";
          break;
        default:
          message = "could not add {0}: {2}";
      }
      writeError("error", MessageFormat.format(message,
          resultError.getName(), changeId, resultError.getType()));
    }

    return ok;
  }

  private static Set<String> stringSet(Set<Account.Id> ids) {
    Set<String> res = new HashSet<String>();
    for (Account.Id id : ids) {
      res.add(Integer.toString(id.get()));
    }
    return res;
  }

  private Set<Change.Id> parseChangeId(String idstr)
      throws UnloggedFailure, OrmException {
    Set<Change.Id> matched = new HashSet<Change.Id>(4);
    boolean isCommit = idstr.matches("^([0-9a-fA-F]{4," + RevId.LEN + "})$");

    // By newer style changeKey?
    //
    boolean changeKeyParses = false;
    if (idstr.matches("^I[0-9a-fA-F]*$")) {
      Change.Key key;
      try {
        key = Change.Key.parse(idstr);
        changeKeyParses = true;
      } catch (IllegalArgumentException e) {
        key = null;
        changeKeyParses = false;
      }

      if (changeKeyParses) {
        for (Change change : db.changes().byKeyRange(key, key.max())) {
          matchChange(matched, change);
        }
      }
    }

    // By commit?
    //
    if (isCommit) {
      RevId id = new RevId(idstr);
      ResultSet<PatchSet> patches;
      if (id.isComplete()) {
        patches = db.patchSets().byRevision(id);
      } else {
        patches = db.patchSets().byRevisionRange(id, id.max());
      }

      for (PatchSet ps : patches) {
        matchChange(matched, ps.getId().getParentKey());
      }
    }

    // By older style changeId?
    //
    boolean changeIdParses = false;
    if (idstr.matches("^[1-9][0-9]*$")) {
      Change.Id id;
      try {
        id = Change.Id.parse(idstr);
        changeIdParses = true;
      } catch (IllegalArgumentException e) {
        id = null;
        changeIdParses = false;
      }

      if (changeIdParses) {
        matchChange(matched, id);
      }
    }

    if (!changeKeyParses && !isCommit && !changeIdParses) {
      throw error("\"" + idstr + "\" is not a valid change");
    }

    switch (matched.size()) {
      case 0:
        throw error("\"" + idstr + "\" no such change");

      case 1:
        return matched;

      default:
        throw error("\"" + idstr + "\" matches multiple changes");
    }
  }

  private void matchChange(Set<Change.Id> matched, Change.Id changeId) {
    if (changeId != null && !matched.contains(changeId)) {
      try {
        matchChange(matched, db.changes().get(changeId));
      } catch (OrmException e) {
        log.warn("Error reading change " + changeId, e);
      }
    }
  }

  private void matchChange(Set<Change.Id> matched, Change change) {
    try {
      if (change != null
          && inProject(change)
          && changeControlFactory.controlFor(change).isVisible()) {
        matched.add(change.getId());
      }
    } catch (NoSuchChangeException e) {
      // Ignore this change.
    }
  }

  private boolean inProject(Change change) {
    if (projectControl != null) {
      return projectControl.getProject().getNameKey().equals(change.getProject());
    } else {
      // No --project option, so they want every project.
      return true;
    }
  }

  private void writeError(String type, String msg) {
    try {
      err.write((type + ": " + msg + "\n").getBytes(ENC));
    } catch (IOException e) {
    }
  }

  private static UnloggedFailure error(String msg) {
    return new UnloggedFailure(1, msg);
  }
}
