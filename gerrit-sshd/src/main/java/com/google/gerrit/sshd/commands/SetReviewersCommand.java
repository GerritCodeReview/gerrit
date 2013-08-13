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

import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.DeleteReviewer;
import com.google.gerrit.server.change.PostReviewers;
import com.google.gerrit.server.change.ReviewerResource;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@CommandMetaData(name = "set-reviewers", description = "Add or remove reviewers on a change")
public class SetReviewersCommand extends SshCommand {
  private static final Logger log =
      LoggerFactory.getLogger(SetReviewersCommand.class);

  @Option(name = "--project", aliases = "-p", usage = "project containing the change")
  private ProjectControl projectControl;

  @Option(name = "--add", aliases = {"-a"}, metaVar = "REVIEWER", usage = "user or group that should be added as reviewer")
  private List<String> toAdd = new ArrayList<String>();

  @Option(name = "--remove", aliases = {"-r"}, metaVar = "REVIEWER", usage = "user that should be removed from the reviewer list")
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
  private ReviewerResource.Factory reviewerFactory;

  @Inject
  private Provider<PostReviewers> postReviewersProvider;

  @Inject
  private Provider<DeleteReviewer> deleteReviewerProvider;

  @Inject
  private ChangeControl.Factory changeControlFactory;

  private Set<Account.Id> toRemove = new HashSet<Account.Id>();
  private Set<Change.Id> changes = new HashSet<Change.Id>();

  @Override
  protected void run() throws UnloggedFailure {
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

  private boolean modifyOne(Change.Id changeId) throws Exception {
    ChangeResource changeRsrc =
        new ChangeResource(changeControlFactory.validateFor(changeId));
    boolean ok = true;

    // Remove reviewers
    //
    DeleteReviewer delete = deleteReviewerProvider.get();
    for (Account.Id reviewer : toRemove) {
      ReviewerResource rsrc = reviewerFactory.create(changeRsrc, reviewer);
      String error = null;;
      try {
        delete.apply(rsrc, new DeleteReviewer.Input());
      } catch (ResourceNotFoundException e) {
        error = String.format("could not remove %s: not found", reviewer);
      } catch (Exception e) {
        error = String.format("could not remove %s: %s",
            reviewer, e.getMessage());
      }
      if (error != null) {
        ok = false;
        writeError("error", error);
      }
    }

    // Add reviewers
    //
    PostReviewers post = postReviewersProvider.get();
    for (String reviewer : toAdd) {
      PostReviewers.Input input = new PostReviewers.Input();
      input.reviewer = reviewer;
      String error;
      try {
        error = post.apply(changeRsrc, input).error;
      } catch (ResourceNotFoundException e) {
        error = String.format("could not add %s: not found", reviewer);
      } catch (Exception e) {
        error = String.format("could not add %s: %s", reviewer, e.getMessage());
      }
      if (error != null) {
        ok = false;
        writeError("error", error);
      }
    }

    return ok;
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
          && changeControlFactory.controlFor(change).isVisible(db)) {
        matched.add(change.getId());
      }
    } catch (NoSuchChangeException e) {
      // Ignore this change.
    } catch (OrmException e) {
      log.warn("Error reading change " + change.getId(), e);
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
