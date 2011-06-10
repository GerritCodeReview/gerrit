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
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.mail.EmailException;
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
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class ModifyReviewersCommand extends BaseCommand {
  private static final Logger log =
      LoggerFactory.getLogger(ModifyReviewersCommand.class);

  @Argument(index = 0, required = true, multiValued = false,
            metaVar = "{CHANGE}...",
            usage = "change in which to modify reviewers")
  void addChangeIdOrEmail(final String token) {
    // Handle emails or names
    try {
      if (parseAnnotatedNameOrEmail(token)) {
        return;
      }
    } catch (UnloggedFailure e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    }

    // Handle change ids
    try {
      changeIds.addAll(parseChangeId(token));
    } catch (UnloggedFailure e) {
      throw new IllegalArgumentException(e.getMessage(), e);
    } catch (OrmException e) {
      throw new IllegalArgumentException("database error", e);
    }
  }

  private Set<Change.Id> changeIds = new HashSet<Change.Id>();
  private Set<String> reviewersToAdd = new HashSet<String>();
  private Set<String> reviewersToRemove = new HashSet<String>();

  @Argument(index = 1, required = true, multiValued = true,
            metaVar = "{EMAIL}...",
            usage = "users to add (+name@example.com) or remove " +
                    "(-name@example.com)")
  void dummyArgument(final String token) {
    addChangeIdOrEmail(token);
  }

  @Option(name = "--project", aliases = "-p",
          usage = "project containing the change")
  private ProjectControl projectControl;

  @Inject
  private ReviewDb db;

  @Inject
  private AccountResolver accountResolver;

  @Inject
  private AddReviewer.Factory addReviewerFactory;

  @Inject
  private RemoveReviewer.Factory removeReviewerFactory;

  @Inject
  private ChangeControl.Factory changeControlFactory;

  @Override
  public final void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Failure {
        parseCommandLine();

        if (changeIds.isEmpty()) {
          writeError("error: You must specify a change");
        }

        boolean ok = true;
        for (final Change.Id changeId : changeIds) {
          try {
            ok = ok && modifyOne(changeId);
          } catch (Exception e) {
            ok = false;
            log.error("internal error while modifying reviewers in " +
                      changeId, e);
            writeError("fatal: internal server error while modifying " +
                       "reviewers in " + changeId + "\n");
          }
        }

        if (!ok) {
          throw error("one or more modifications of reviewers failed; " +
                      "review output above");
        }
      }
    });
  }

  private boolean modifyOne(final Change.Id changeId) throws
      NoSuchChangeException, UnloggedFailure, OrmException, EmailException,
      Exception {

    final ChangeControl changeControl =
        changeControlFactory.validateFor(changeId);
    boolean ok = true;

    // Remove reviewers
    //
    for (final String nameOrEmail: reviewersToRemove) {
      final Account account = accountResolver.find(nameOrEmail);
      if (account == null) {
        writeError("error: reviewer does not exist \"" + nameOrEmail + "\"\n");
        continue;
      }
      Account.Id accountId = account.getId();

      final ReviewerResult result =
          removeReviewerFactory.create(changeId, accountId).call();
      for (ReviewerResult.Error resultError : result.getErrors()) {
        // COULD_NOT_REMOVE is the only error type this could be, but we check
        // for it in case a new type is added in the future with more
        // sensitive information that we do not want to reveal to the user.
        if (resultError.getType() ==
            ReviewerResult.Error.Type.COULD_NOT_REMOVE) {
          // For COULD_NOT_REMOVE, getName() contains the specific error
          // message with an id at the end
          final String message = resultError.getName();
          String regex = accountId + "$";
          if (message.matches("^.* " + regex)) {
            writeError("error: " + message.replaceAll(regex, "\"" + nameOrEmail + "\"\n"));
          } else {
            writeError("error: " + "\"" + nameOrEmail + "\" could not be removed\n");
          }
          ok = false;
        }
      }
    }

    // Add reviewers
    //
    final ReviewerResult result =
        addReviewerFactory.create(changeId, reviewersToAdd).call();
    for (ReviewerResult.Error resultError : result.getErrors()) {
      ok = false;
      String message;
      switch (resultError.getType()) {
        case ACCOUNT_NOT_FOUND:
          message = "account not found";
          break;
        case ACCOUNT_INACTIVE:
          message = "account inactive";
          break;
        case CHANGE_NOT_VISIBLE:
          message = "change is not visible to user";
          break;
        default:
          message = "could not add reviewer";
      }
      writeError("error: " + message + "\"" + resultError.getName() + "\"\n");
    }
    return ok;
  }

  private boolean parseAnnotatedNameOrEmail(final String annotatedNameOrEmail)
      throws UnloggedFailure {
    Set<String> matchingSet;
    Set<String> otherSet;
    switch (annotatedNameOrEmail.charAt(0)) {
      case '+':
        matchingSet = reviewersToAdd;
        otherSet = reviewersToRemove;
        break;
      case '-':
        matchingSet = reviewersToRemove;
        otherSet = reviewersToAdd;
        break;
      default:
        return false;
    }

    final String nameOrEmail = annotatedNameOrEmail.substring(1);
    if (otherSet.contains(nameOrEmail)) {
      throw error("You cannot both add and remove \"" + nameOrEmail + "\"");
    }
    matchingSet.add(nameOrEmail);
    return true;
  }

  private Set<Change.Id> parseChangeId(final String changeIdentity)
      throws UnloggedFailure, OrmException {
    final Set<Change.Id> matches = new HashSet<Change.Id>();
    boolean foundInOtherProject = false;
    boolean matchesChangeKey = changeIdentity.matches("^I[0-9a-fA-F]*$");
    boolean matchesCommit =
        changeIdentity.matches("^([0-9a-fA-F]{4," + RevId.LEN + "})$");
    boolean matchesChangeId = changeIdentity.matches("^[1-9][0-9]*$");

    // By newer style changeKey?
    //
    boolean changeKeyParses = matchesChangeKey;
    if (matchesChangeKey) {
      Change.Key changeKey = null;
      try {
        changeKey = Change.Key.parse(changeIdentity);
      } catch (IllegalArgumentException e) {
        changeKeyParses = false;
      }

      if (changeKeyParses) {
        final ResultSet<Change> changes =
            db.changes().byKeyRange(changeKey, changeKey.max());
        for (final Change change : changes) {
          if (inProject(change)) {
            matches.add(change.getId());
          } else {
            foundInOtherProject = true;
          }
        }
      }
    }

    // By commit?
    //
    if (matchesCommit) {
      final RevId id = new RevId(changeIdentity);
      final ResultSet<PatchSet> patches;
      if (id.isComplete()) {
        patches = db.patchSets().byRevision(id);
      } else {
        patches = db.patchSets().byRevisionRange(id, id.max());
      }

      for (final PatchSet ps : patches) {
        final Change change = db.changes().get(ps.getId().getParentKey());
        if (inProject(change)) {
          matches.add(change.getId());
        } else {
          foundInOtherProject = true;
        }
      }
    }

    // By older style changeId?
    //
    boolean changeIdParses = matchesChangeId;
    if (matchesChangeId) {
      Change.Id changeId = null;
      try {
        changeId = Change.Id.parse(changeIdentity);
      } catch (IllegalArgumentException e) {
        changeIdParses = false;
      }

      if (changeIdParses) {
        final Change change = db.changes().get(changeId);
        if (change != null) {
          if (inProject(change)) {
            matches.add(change.getId());
          } else {
            foundInOtherProject = true;
          }
        }
      }
    }

    if (!changeKeyParses && !matchesCommit && !changeIdParses) {
      throw error("\"" + changeIdentity + "\" is not a valid change");
    }

    switch (matches.size()) {
      case 1:
        return matches;
      case 0:
        if (foundInOtherProject) {
          throw error("change " + changeIdentity + " not in project "
              + projectControl.getProject().getName());
        }
        throw error("\"" + changeIdentity + "\" no such change");
      default:
    }
    throw error("\"" + changeIdentity + "\" matches multiple changes");
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
