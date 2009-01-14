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

import com.google.gerrit.client.Link;
import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountAgreement;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ApprovalCategoryValue;
import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gerrit.client.reviewdb.ContactInformation;
import com.google.gerrit.client.reviewdb.ContributorAgreement;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.git.PatchSetImporter;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritServer;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.Transaction;

import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.PersonIdent;
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
import java.io.OutputStreamWriter;
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

  private final Set<String> myEmails = new HashSet<String>();
  private final List<Change.Id> allNewChanges = new ArrayList<Change.Id>();

  private final Map<Change.Id, ReceiveCommand> addByChange =
      new HashMap<Change.Id, ReceiveCommand>();
  private final Map<ObjectId, Change> addByCommit =
      new HashMap<ObjectId, Change>();
  private final Map<Change.Id, Change> changeCache =
      new HashMap<Change.Id, Change>();

  @Override
  protected void runImpl() throws IOException, Failure {
    server = getGerritServer();
    if (Common.getGerritConfig().isUseContributorAgreements()
        && proj.isUseContributorAgreements()) {
      verifyActiveContributorAgreement();
    }
    loadMyEmails();
    lookup(reviewerId, "reviewer", reviewerEmail);
    lookup(ccId, "cc", ccEmail);

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

    if (!allNewChanges.isEmpty() && server.getCanonicalURL() != null) {
      // Make sure there isn't anything buffered; we want to give the
      // push client a chance to display its status report before we
      // show our own messages on standard error.
      //
      out.flush();

      final String url = server.getCanonicalURL();
      final OutputStreamWriter msg = new OutputStreamWriter(err, "UTF-8");
      msg.write("\nNew Changes:\n");
      for (final Change.Id c : allNewChanges) {
        msg.write("  " + url + "Gerrit#" + Link.toChange(c) + "\n");
      }
      msg.write('\n');
      msg.flush();
    }
  }

  private void verifyActiveContributorAgreement() throws Failure {
    AccountAgreement bestAgreement = null;
    ContributorAgreement bestCla = null;
    try {
      for (final AccountAgreement a : db.accountAgreements().byAccount(
          userAccount.getId()).toList()) {
        final ContributorAgreement cla =
            db.contributorAgreements().get(a.getAgreementId());
        if (cla == null) {
          continue;
        }

        bestAgreement = a;
        bestCla = cla;
        break;
      }
    } catch (OrmException e) {
      throw new Failure(1, "database error");
    }

    if (bestCla != null && !bestCla.isActive()) {
      final StringBuilder msg = new StringBuilder();
      msg.append("\nfatal: ");
      msg.append(bestCla.getShortName());
      msg.append(" contributor agreement is expired.\n");
      if (server.getCanonicalURL() != null) {
        msg.append("\nPlease complete a new agreement");
        msg.append(":\n\n  ");
        msg.append(server.getCanonicalURL());
        msg.append("Gerrit#");
        msg.append(Link.SETTINGS_AGREEMENTS);
        msg.append("\n");
      }
      msg.append("\n");
      throw new Failure(1, msg.toString());
    }

    if (bestCla != null && bestCla.isRequireContactInformation()) {
      final ContactInformation info = userAccount.getContactInformation();
      boolean fail = false;
      fail |= missing(userAccount.getFullName());
      fail |= missing(userAccount.getPreferredEmail());
      fail |= info == null || missing(info.getAddress());

      if (fail) {
        final StringBuilder msg = new StringBuilder();
        msg.append("\nfatal: ");
        msg.append(bestCla.getShortName());
        msg.append(" contributor agreement requires");
        msg.append(" current contact information.\n");
        if (server.getCanonicalURL() != null) {
          msg.append("\nPlease review your contact information");
          msg.append(":\n\n  ");
          msg.append(server.getCanonicalURL());
          msg.append("Gerrit#");
          msg.append(Link.SETTINGS_CONTACT);
          msg.append("\n");
        }
        msg.append("\n");
        throw new Failure(1, msg.toString());
      }
    }

    if (bestAgreement != null) {
      switch (bestAgreement.getStatus()) {
        case VERIFIED:
          return;
        case REJECTED:
          throw new Failure(1, "\nfatal: " + bestCla.getShortName()
              + " contributor agreement was rejected."
              + "\n       (rejected on " + bestAgreement.getReviewedOn()
              + ")\n");
        case NEW:
          throw new Failure(1, "\nfatal: " + bestCla.getShortName()
              + " contributor agreement is still pending review.\n");
      }
    }

    final StringBuilder msg = new StringBuilder();
    msg.append("\nfatal: A Contributor Agreement"
        + " must be completed before uploading");
    if (server.getCanonicalURL() != null) {
      msg.append(":\n\n  ");
      msg.append(server.getCanonicalURL());
      msg.append("Gerrit#");
      msg.append(Link.SETTINGS_AGREEMENTS);
      msg.append("\n");
    } else {
      msg.append(".");
    }
    msg.append("\n");
    throw new Failure(1, msg.toString());
  }

  private static boolean missing(final String value) {
    return value == null || value.trim().equals("");
  }

  private void loadMyEmails() throws Failure {
    try {
      for (final AccountExternalId id : db.accountExternalIds().byAccount(
          userAccount.getId())) {
        if (id.getEmailAddress() != null && id.getEmailAddress().length() > 0) {
          myEmails.add(id.getEmailAddress());
        }
      }
    } catch (OrmException e) {
      throw new Failure(1, "database error");
    }
  }

  private void lookup(final Set<Account.Id> accountIds,
      final String addressType, final Set<String> emails) throws Failure {
    final StringBuilder errors = new StringBuilder();
    try {
      for (final String email : emails) {
        final Account who = Account.find(db, email);
        if (who != null) {
          accountIds.add(who.getId());
        } else {
          errors.append("fatal: " + addressType + " " + email
              + " is not registered on Gerrit\n");
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
    if (newChange == null
        || newChange.getResult() != ReceiveCommand.Result.NOT_ATTEMPTED) {
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
        if (!validCommitter(newChange, c)) {
          return;
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
    final PatchSet ps = new PatchSet(change.newPatchSetId());
    final PatchSetImporter imp = new PatchSetImporter(db, repo, c, ps, true);
    imp.setTransaction(txn);
    imp.run();
    change.setCurrentPatchSet(imp.getPatchSetInfo());
    db.changes().insert(Collections.singleton(change));

    for (final ApprovalType t : Common.getGerritConfig().getApprovalTypes()) {
      final ApprovalCategoryValue v = t.getMax();
      if (v != null) {
        db.changeApprovals().insert(
            Collections.singleton(new ChangeApproval(new ChangeApproval.Key(
                change.getId(), userAccount.getId(), v.getCategoryId()), v
                .getValue())));
      }
    }
    txn.commit();

    final RefUpdate ru = repo.updateRef(ps.getRefName());
    ru.setForceUpdate(true);
    ru.setNewObjectId(c);
    ru.update(walk);

    allNewChanges.add(change.getId());
  }

  private void appendPatchSets() {
    for (Map.Entry<Change.Id, ReceiveCommand> e : addByChange.entrySet()) {
      final ReceiveCommand cmd = e.getValue();
      final Change.Id changeId = e.getKey();
      final Change change = changeCache.get(changeId);
      try {
        appendPatchSet(change, cmd);

        if (cmd.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
          cmd.setResult(ReceiveCommand.Result.OK);
        }
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
    if (!validCommitter(cmd, c)) {
      return;
    }
    final Transaction txn = db.beginTransaction();
    final PatchSet ps = new PatchSet(change.newPatchSetId());
    final PatchSetImporter imp = new PatchSetImporter(db, repo, c, ps, true);
    imp.setTransaction(txn);
    imp.run();

    final Set<ApprovalCategory.Id> have = new HashSet<ApprovalCategory.Id>();
    for (final ChangeApproval a : db.changeApprovals().byChange(change.getId())) {
      if (userAccount.getId().equals(a.getAccountId())) {
        // Leave my own approvals alone.
        //
        have.add(a.getCategoryId());

      } else if (a.getValue() > 0) {
        a.clear();
        db.changeApprovals().update(Collections.singleton(a), txn);
      }
    }
    for (final ApprovalType t : Common.getGerritConfig().getApprovalTypes()) {
      final ApprovalCategoryValue v = t.getMax();
      if (!have.contains(t.getCategory().getId()) && v != null) {
        db.changeApprovals().insert(
            Collections.singleton(new ChangeApproval(new ChangeApproval.Key(
                change.getId(), userAccount.getId(), v.getCategoryId()), v
                .getValue())), txn);
      }
    }

    final ChangeMessage m =
        new ChangeMessage(new ChangeMessage.Key(change.getId(), ChangeUtil
            .messageUUID(db)), getAccountId());
    m.setMessage("Uploaded patch set " + ps.getPatchSetId() + ".");
    db.changeMessages().insert(Collections.singleton(m), txn);

    change.setStatus(Change.Status.NEW);
    change.setCurrentPatchSet(imp.getPatchSetInfo());
    change.updated();
    db.changes().update(Collections.singleton(change), txn);
    txn.commit();

    final RefUpdate ru = repo.updateRef(ps.getRefName());
    ru.setForceUpdate(true);
    ru.setNewObjectId(c);
    ru.update(rp.getRevWalk());
  }

  private boolean validCommitter(final ReceiveCommand cmd, final RevCommit c) {
    // Require that committer matches the uploader.
    final PersonIdent committer = c.getCommitterIdent();
    final String email = committer.getEmailAddress();
    if (myEmails.contains(email)) {
      return true;
    } else {
      reject(cmd, "invalid committer " + email);
      return false;
    }
  }

  private static void reject(final ReceiveCommand cmd) {
    reject(cmd, "prohibited by Gerrit");
  }

  private static void reject(final ReceiveCommand cmd, final String why) {
    cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, why);
  }
}
