// Copyright 2008 Google Inc.
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

package com.google.gerrit.server.ssh;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.git.InvalidRepositoryException;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.transport.PreReceiveHook;
import org.spearce.jgit.transport.ReceiveCommand;
import org.spearce.jgit.transport.ReceivePack;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Receives change upload over SSH using the Git receive-pack protocol. */
class Receive extends AbstractCommand {
  private static final String NEW_CHANGE = "refs/changes/new";
  private static final Pattern NEW_PATCHSET =
      Pattern.compile("^refs/changes/([0-9][0-9])/([0-9]*\\1)/new$");

  private final Set<String> reviewerEmail = new HashSet<String>();
  private final Set<String> ccEmail = new HashSet<String>();

  private final Set<Account.Id> reviewerId = new HashSet<Account.Id>();
  private final Set<Account.Id> ccId = new HashSet<Account.Id>();

  private ReceiveCommand newChange;
  private final Map<Change.Id, ReceiveCommand> addByChange =
      new HashMap<Change.Id, ReceiveCommand>();
  private final Map<ObjectId, Change> addByCommit =
      new HashMap<ObjectId, Change>();
  private final Map<Change.Id, Change> changeCache =
      new HashMap<Change.Id, Change>();

  private Repository repo;
  private Project proj;

  private boolean isGerrit() {
    return getName().startsWith("gerrit-");
  }

  @Override
  protected void run(final String[] args) throws IOException, Failure {
    final String reqName = parseCommandLine(args);
    String projectName = reqName;
    if (projectName.endsWith(".git")) {
      // Be nice and drop the trailing ".git" suffix, which we never keep
      // in our database, but clients might mistakenly provide anyway.
      //
      projectName = projectName.substring(0, projectName.length() - 4);
    }
    if (projectName.startsWith("/")) {
      // Be nice and drop the leading "/" if supplied by an absolute path.
      // We don't have a file system hierarchy, just a flat namespace in
      // the database's Project entities. We never encode these with a
      // leading '/' but users might accidentally include them in Git URLs.
      //
      projectName = projectName.substring(1);
    }

    final ReviewDb db = openReviewDb();
    try {
      try {
        proj = db.projects().byName(new Project.NameKey(projectName));
      } catch (OrmException e) {
        throw new Failure(1, "fatal: cannot query project database");
      }
      if (proj == null) {
        throw new Failure(1, "fatal: '" + reqName + "': not a Gerrit project");
      }

      try {
        repo = getRepositoryCache().get(proj.getName());
      } catch (InvalidRepositoryException e) {
        throw new Failure(1, "fatal: '" + reqName + "': not a git archive");
      }

      lookup(db, reviewerId, "reviewer", reviewerEmail);
      lookup(db, ccId, "cc", ccEmail);

      // TODO verify user has signed a CLA for this project

      final ReceivePack rp = new ReceivePack(repo);
      rp.setAllowCreates(true);
      rp.setAllowDeletes(false);
      rp.setAllowNonFastForwards(false);
      rp.setCheckReceivedObjects(true);
      rp.setPreReceiveHook(new PreReceiveHook() {
        public void onPreReceive(final ReceivePack arg0,
            final Collection<ReceiveCommand> commands) {
          parseCommands(db, commands);

          if (newChange != null) {
            // TODO create new change records
            newChange.setResult(ReceiveCommand.Result.OK);
          }
          for (Map.Entry<Change.Id, ReceiveCommand> e : addByChange.entrySet()) {
            // TODO Append new commits to existing changes
            e.getValue().setResult(ReceiveCommand.Result.OK);
          }
        }
      });
      rp.receive(in, out, err);
    } finally {
      db.close();
    }
  }

  private void lookup(final ReviewDb db, final Set<Account.Id> accountIds,
      final String addressType, final Set<String> emails) throws Failure {
    final StringBuilder errors = new StringBuilder();
    try {
      for (final String email : emails) {
        final List<Account> who =
            db.accounts().byPreferredEmail(email).toList();
        if (who.size() == 1) {
          accountIds.add(who.get(0).getId());
        } else if (who.size() == 0) {
          errors.append("fatal: " + addressType + " " + email
              + " is not registered on Gerrit\n");
        } else {
          errors.append("fatal: " + addressType + " " + email
              + " matches more than one account on Gerrit\n");
        }
      }
    } catch (OrmException err) {
      throw new Failure(1, "fatal: cannot lookup reviewers, database is down");
    }
    if (errors.length() > 0) {
      throw new Failure(1, errors.toString());
    }
  }

  private String parseCommandLine(final String[] args) throws Failure {
    int argi = 0;
    if (isGerrit()) {
      for (; argi < args.length - 1; argi++) {
        final int eq = args[argi].indexOf('=');
        final String opt, val;
        if (eq < 0) {
          opt = args[argi];
          val = "";
        } else {
          opt = args[argi].substring(0, eq);
          val = args[argi].substring(eq + 1);
        }

        if (opt.equals("--reviewer")) {
          reviewerEmail.add(val);
          continue;
        }
        if (opt.equals("--cc")) {
          ccEmail.add(val);
          continue;
        }
        break;
      }
    }
    if (argi != args.length - 1) {
      throw usage();
    }
    return args[argi];
  }

  private Failure usage() {
    final StringBuilder m = new StringBuilder();
    m.append("usage: ");
    m.append(getName());
    if (isGerrit()) {
      m.append(" [--reviewer=email]*");
      m.append(" [--cc=email]*");
    }
    m.append(" '/project.git'");
    return new Failure(1, m.toString());
  }

  private void parseCommands(final ReviewDb db,
      final Collection<ReceiveCommand> commands) {
    for (final ReceiveCommand cmd : commands) {
      if (cmd.getResult() != ReceiveCommand.Result.NOT_ATTEMPTED) {
        // Already rejected by the core receive process.
        //
        continue;
      }

      if (cmd.getType() != ReceiveCommand.Type.CREATE) {
        // We only permit creates for refs which don't exist.
        //
        reject(cmd);
        continue;
      }

      if (NEW_CHANGE.equals(cmd.getRefName())) {
        // Permit exactly one new change request per push.
        //
        if (newChange != null) {
          reject(cmd, "duplicate request");
          continue;
        }

        newChange = cmd;
        continue;
      }

      final Matcher m = NEW_PATCHSET.matcher(cmd.getRefName());
      if (m.matches()) {
        // The referenced change must exist and must still be open.
        //
        final Change.Id changeId = Change.Id.fromString(m.group(2));
        final Change changeEnt;
        try {
          changeEnt = db.changes().get(changeId);
        } catch (OrmException e) {
          reject(cmd, "database error");
          continue;
        }
        if (changeEnt == null) {
          reject(cmd, "change " + changeId.get() + " not found");
          continue;
        }
        if (changeEnt.getStatus().isClosed()) {
          reject(cmd, "change " + changeId.get() + " closed");
          continue;
        }

        if (addByChange.containsKey(changeId)) {
          reject(cmd, "duplicate request");
          continue;
        }
        if (addByCommit.containsKey(cmd.getNewId())) {
          reject(cmd, "duplicate request");
          continue;
        }

        addByChange.put(changeId, cmd);
        addByCommit.put(cmd.getNewId(), changeEnt);
        changeCache.put(changeId, changeEnt);
        continue;
      }

      // Everything else is bogus as far as we are concerned.
      //
      reject(cmd);
    }
  }

  private static void reject(final ReceiveCommand cmd) {
    reject(cmd, "prohibited by Gerrit");
  }

  private static void reject(final ReceiveCommand cmd, final String why) {
    cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, why);
  }
}
