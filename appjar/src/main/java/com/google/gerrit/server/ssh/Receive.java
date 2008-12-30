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
import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.git.PatchSetImporter;
import com.google.gerrit.server.GerritServer;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;

import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevSort;
import org.spearce.jgit.revwalk.RevWalk;
import org.spearce.jgit.transport.PreReceiveHook;
import org.spearce.jgit.transport.ReceiveCommand;
import org.spearce.jgit.transport.ReceivePack;
import org.spearce.jgit.transport.ReceiveCommand.Result;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Receives change upload over SSH using the Git receive-pack protocol. */
class Receive extends AbstractGitCommand {
  private static final String NEW_CHANGE = "refs/for/";
  private static final Pattern NEW_PATCHSET =
      Pattern.compile("^refs/changes/(?:[0-9][0-9]/)?([1-9][0-9]*)(?:/new)?$");

  private final Set<String> reviewerEmail = new HashSet<String>();
  private final Set<String> ccEmail = new HashSet<String>();

  private final Set<Account.Id> reviewerId = new HashSet<Account.Id>();
  private final Set<Account.Id> ccId = new HashSet<Account.Id>();

  private GerritServer server;
  private ReceivePack rp;
  private ReceiveCommand newChange;
  private Branch destBranch;

  private final Map<Change.Id, ReceiveCommand> addByChange =
      new HashMap<Change.Id, ReceiveCommand>();
  private final Map<ObjectId, Change> addByCommit =
      new HashMap<ObjectId, Change>();
  private final Map<Change.Id, Change> changeCache =
      new HashMap<Change.Id, Change>();

  @Override
  protected void runImpl() throws IOException, Failure {
    server = getGerritServer();
    lookup(reviewerId, "reviewer", reviewerEmail);
    lookup(ccId, "cc", ccEmail);

    // TODO verify user has signed a CLA for this project

    rp = new ReceivePack(repo);
    rp.setAllowCreates(true);
    rp.setAllowDeletes(true);
    rp.setAllowNonFastForwards(true);
    rp.setCheckReceivedObjects(true);
    rp.setPreReceiveHook(new PreReceiveHook() {
      public void onPreReceive(final ReceivePack arg0,
          final Collection<ReceiveCommand> commands) {
        parseCommands(commands);
        createNewChanges();
        appendPatchSets();
      }
    });
    rp.receive(in, out, err);
  }

  private void lookup(final Set<Account.Id> accountIds,
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

  @Override
  protected String parseCommandLine(final String[] args) throws Failure {
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

  private void parseCommands(final Collection<ReceiveCommand> commands) {
    for (final ReceiveCommand cmd : commands) {
      if (cmd.getResult() != ReceiveCommand.Result.NOT_ATTEMPTED) {
        // Already rejected by the core receive process.
        //
        continue;
      }

      if (cmd.getRefName().startsWith(NEW_CHANGE)) {
        parseNewChangeCommand(cmd);
        continue;
      }

      final Matcher m = NEW_PATCHSET.matcher(cmd.getRefName());
      if (m.matches()) {
        // The referenced change must exist and must still be open.
        //
        final Change.Id changeId = Change.Id.parse(m.group(1));
        parseNewPatchSetCommand(cmd, changeId);
        continue;
      }

      // Everything else is bogus as far as we are concerned.
      //
      reject(cmd);
    }
  }

  private void parseNewChangeCommand(final ReceiveCommand cmd) {
    // Permit exactly one new change request per push.
    //
    if (newChange != null) {
      reject(cmd, "duplicate request");
      return;
    }

    newChange = cmd;
    String destBranchName = cmd.getRefName().substring(NEW_CHANGE.length());
    if (!destBranchName.startsWith(Constants.R_REFS)) {
      destBranchName = Constants.R_HEADS + destBranchName;
    }

    try {
      destBranch =
          db.branches().get(
              new Branch.NameKey(proj.getNameKey(), destBranchName));
    } catch (OrmException e) {
      reject(cmd, "database error");
      return;
    }
    if (destBranch == null) {
      String n = destBranchName;
      if (n.startsWith(Constants.R_HEADS))
        n = n.substring(Constants.R_HEADS.length());
      reject(cmd, "branch " + n + " not found");
      return;
    }
  }

  private void parseNewPatchSetCommand(final ReceiveCommand cmd,
      final Change.Id changeId) {
    final Change changeEnt;
    try {
      changeEnt = db.changes().get(changeId);
    } catch (OrmException e) {
      reject(cmd, "database error");
      return;
    }
    if (changeEnt == null) {
      reject(cmd, "change " + changeId.get() + " not found");
      return;
    }
    if (changeEnt.getStatus().isClosed()) {
      reject(cmd, "change " + changeId.get() + " closed");
      return;
    }

    if (addByChange.containsKey(changeId)) {
      reject(cmd, "duplicate request");
      return;
    }
    if (addByCommit.containsKey(cmd.getNewId())) {
      reject(cmd, "duplicate request");
      return;
    }

    addByChange.put(changeId, cmd);
    addByCommit.put(cmd.getNewId(), changeEnt);
    changeCache.put(changeId, changeEnt);
  }

  private void createNewChanges() {
    if (newChange == null) {
      return;
    }

    final List<RevCommit> toCreate = new ArrayList<RevCommit>();
    final RevWalk walk = rp.getRevWalk();
    walk.reset();
    walk.sort(RevSort.TOPO);
    walk.sort(RevSort.REVERSE, true);
    try {
      walk.markStart(walk.parseCommit(newChange.getNewId()));
      for (final Ref r : rp.getAdvertisedRefs().values()) {
        try {
          walk.markUninteresting(walk.parseCommit(r.getObjectId()));
        } catch (IOException e) {
          continue;
        }
      }

      for (;;) {
        final RevCommit c = walk.next();
        if (c == null) {
          break;
        }
        if (addByCommit.containsKey(c.copy())) {
          // This commit is slated to replace an existing PatchSet.
          //
          continue;
        }
        toCreate.add(c);
      }
    } catch (IOException e) {
      // Should never happen, the core receive process would have
      // identified the missing object earlier before we got control.
      //
      newChange.setResult(Result.REJECTED_MISSING_OBJECT);
    }

    if (toCreate.isEmpty()) {
      reject(newChange, "no new changes");
      return;
    }

    try {
      for (final RevCommit c : toCreate) {
        createChange(walk, c);
      }
      newChange.setResult(ReceiveCommand.Result.OK);
    } catch (IOException e) {
      reject(newChange, "diff error");
    } catch (OrmException e) {
      reject(newChange, "database error");
    }
  }

  private void createChange(final RevWalk walk, final RevCommit c)
      throws OrmException, IOException {
    final Transaction txn = db.beginTransaction();
    final Change change =
        new Change(new Change.Id(db.nextChangeId()), userAccount.getId(),
            destBranch.getNameKey());
    final PatchSet ps = new PatchSet(new PatchSet.Id(change.getId(), 1));
    final PatchSetImporter imp = new PatchSetImporter(db, repo, c, ps, true);
    imp.setTransaction(txn);
    imp.run();
    change.setCurrentPatchSet(imp.getPatchSetInfo());
    db.changes().insert(Collections.singleton(change));
    txn.commit();

    final RefUpdate ru = repo.updateRef(ps.getRefName());
    ru.setForceUpdate(true);
    ru.setNewObjectId(c);
    ru.update(walk);

    final String url = server.getCanonicalURL();
    if (url != null) {
      rp.sendMessage("New change: " + url + change.getChangeId());
    }
  }

  private void appendPatchSets() {
    for (Map.Entry<Change.Id, ReceiveCommand> e : addByChange.entrySet()) {
      final ReceiveCommand cmd = e.getValue();
      final Change.Id changeId = e.getKey();
      final Change change = changeCache.get(changeId);
      try {
        appendPatchSet(change, cmd);
        cmd.setResult(ReceiveCommand.Result.OK);
      } catch (IOException err) {
        reject(cmd, "diff error");
      } catch (OrmException err) {
        reject(cmd, "database error");
      }
    }
  }

  private void appendPatchSet(final Change change, final ReceiveCommand cmd)
      throws IOException, OrmException {
    final RevCommit c = rp.getRevWalk().parseCommit(cmd.getNewId());
    final Transaction txn = db.beginTransaction();
    final PatchSet ps = new PatchSet(change.newPatchSetId());
    final PatchSetImporter imp = new PatchSetImporter(db, repo, c, ps, true);
    imp.setTransaction(txn);
    imp.run();
    change.setCurrentPatchSet(imp.getPatchSetInfo());
    change.updated();
    db.changes().update(Collections.singleton(change));
    txn.commit();

    final RefUpdate ru = repo.updateRef(ps.getRefName());
    ru.setForceUpdate(true);
    ru.setNewObjectId(c);
    ru.update(rp.getRevWalk());
  }

  private static void reject(final ReceiveCommand cmd) {
    reject(cmd, "prohibited by Gerrit");
  }

  private static void reject(final ReceiveCommand cmd, final String why) {
    cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, why);
  }
}
