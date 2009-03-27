// Copyright (C) 2008 The Android Open Source Project
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

import static com.google.gerrit.client.reviewdb.ApprovalCategory.PUSH_HEAD;
import static com.google.gerrit.client.reviewdb.ApprovalCategory.PUSH_HEAD_CREATE;
import static com.google.gerrit.client.reviewdb.ApprovalCategory.PUSH_HEAD_REPLACE;
import static com.google.gerrit.client.reviewdb.ApprovalCategory.PUSH_HEAD_UPDATE;
import static com.google.gerrit.client.reviewdb.ApprovalCategory.PUSH_TAG;
import static com.google.gerrit.client.reviewdb.ApprovalCategory.PUSH_TAG_ANNOTATED;
import static com.google.gerrit.client.reviewdb.ApprovalCategory.PUSH_TAG_ANY;

import com.google.gerrit.client.Link;
import com.google.gerrit.client.data.ApprovalType;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountAgreement;
import com.google.gerrit.client.reviewdb.AccountExternalId;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Branch;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.ChangeApproval;
import com.google.gerrit.client.reviewdb.ChangeMessage;
import com.google.gerrit.client.reviewdb.ContributorAgreement;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.PatchSetInfo;
import com.google.gerrit.client.reviewdb.RevId;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.git.PatchSetImporter;
import com.google.gerrit.git.PushQueue;
import com.google.gerrit.server.ChangeMail;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritServer;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.OrmRunnable;
import com.google.gwtorm.client.Transaction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.PersonIdent;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevObject;
import org.spearce.jgit.revwalk.RevSort;
import org.spearce.jgit.revwalk.RevTag;
import org.spearce.jgit.revwalk.RevWalk;
import org.spearce.jgit.transport.PostReceiveHook;
import org.spearce.jgit.transport.PreReceiveHook;
import org.spearce.jgit.transport.ReceiveCommand;
import org.spearce.jgit.transport.ReceivePack;
import org.spearce.jgit.transport.ReceiveCommand.Result;
import org.spearce.jgit.transport.ReceiveCommand.Type;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.sql.Timestamp;
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

import javax.mail.MessagingException;

/** Receives change upload over SSH using the Git receive-pack protocol. */
class Receive extends AbstractGitCommand {
  private static final Logger log = LoggerFactory.getLogger(Receive.class);

  private static final String NEW_CHANGE = "refs/for/";
  private static final Pattern NEW_PATCHSET =
      Pattern.compile("^refs/changes/(?:[0-9][0-9]/)?([1-9][0-9]*)(?:/new)?$");

  private final Set<String> reviewerEmail = new HashSet<String>();
  private final Set<String> ccEmail = new HashSet<String>();

  private final Set<Account.Id> reviewerId = new HashSet<Account.Id>();
  private final Set<Account.Id> ccId = new HashSet<Account.Id>();

  private GerritServer server;
  private ReceivePack rp;
  private PersonIdent refLogIdent;
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
    refLogIdent = ChangeUtil.toReflogIdent(userAccount, getRemoteAddress());

    rp = new ReceivePack(repo);
    rp.setAllowCreates(true);
    rp.setAllowDeletes(true);
    rp.setAllowNonFastForwards(true);
    rp.setCheckReceivedObjects(true);
    rp.setRefLogIdent(refLogIdent);
    rp.setPreReceiveHook(new PreReceiveHook() {
      public void onPreReceive(final ReceivePack arg0,
          final Collection<ReceiveCommand> commands) {
        parseCommands(commands);
        createNewChanges();
        appendPatchSets();
      }
    });
    rp.setPostReceiveHook(new PostReceiveHook() {
      public void onPostReceive(final ReceivePack arg0,
          final Collection<ReceiveCommand> commands) {
        for (final ReceiveCommand c : commands) {
          if (c.getResult() == Result.OK) {
            if (isHead(c)) {
              // Make sure the branch table matches the repository
              //
              switch (c.getType()) {
                case CREATE:
                  insertBranchEntity(c);
                  break;
                case DELETE:
                  deleteBranchEntity(c);
                  break;
              }
            }

            if (isHead(c) || isTag(c)) {
              // We only schedule heads and tags for replication.
              // Change refs are scheduled when they are created.
              //
              PushQueue.scheduleUpdate(proj.getNameKey(), c.getRefName());
            }
          }
        }
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
        msg.write("  " + url + c.get() + "\n");
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
      throw new Failure(1, "fatal: database error", e);
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
      throw new UnloggedFailure(1, msg.toString());
    }

    if (bestCla != null && bestCla.isRequireContactInformation()) {
      boolean fail = false;
      fail |= missing(userAccount.getFullName());
      fail |= missing(userAccount.getPreferredEmail());
      fail |= !userAccount.isContactFiled();

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
        throw new UnloggedFailure(1, msg.toString());
      }
    }

    if (bestAgreement != null) {
      switch (bestAgreement.getStatus()) {
        case VERIFIED:
          return;
        case REJECTED:
          throw new UnloggedFailure(1, "\nfatal: " + bestCla.getShortName()
              + " contributor agreement was rejected."
              + "\n       (rejected on " + bestAgreement.getReviewedOn()
              + ")\n");
        case NEW:
          throw new UnloggedFailure(1, "\nfatal: " + bestCla.getShortName()
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
    throw new UnloggedFailure(1, msg.toString());
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
      throw new Failure(1, "fatal: database error", e);
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
    } catch (OrmException e) {
      throw new Failure(1, "fatal: database is down", e);
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

      switch (cmd.getType()) {
        case CREATE:
          parseCreate(cmd);
          continue;

        case UPDATE:
          parseUpdate(cmd);
          continue;

        case DELETE:
        case UPDATE_NONFASTFORWARD:
          parseRewindOrDelete(cmd);
          continue;
      }

      // Everything else is bogus as far as we are concerned.
      //
      reject(cmd);
    }
  }

  private void parseCreate(final ReceiveCommand cmd) {
    if (isHead(cmd) && canPerform(PUSH_HEAD, PUSH_HEAD_CREATE)) {
      // Let the core receive process handle it

    } else if (isTag(cmd) && canPerform(PUSH_TAG, (short) 1)) {
      parseCreateTag(cmd);

    } else {
      reject(cmd);
    }
  }

  private void parseCreateTag(final ReceiveCommand cmd) {
    try {
      final RevObject obj = rp.getRevWalk().parseAny(cmd.getNewId());
      if (!(obj instanceof RevTag)) {
        reject(cmd, "not annotated tag");
        return;
      }

      if (canPerform(PUSH_TAG, PUSH_TAG_ANY)) {
        // If we can push any tag, validation is sufficient at this point.
        //
        return;
      }

      final RevTag tag = (RevTag) obj;
      final PersonIdent tagger = tag.getTaggerIdent();
      if (tagger == null) {
        reject(cmd, "no tagger");
        return;
      }

      final String email = tagger.getEmailAddress();
      if (!myEmails.contains(email)) {
        reject(cmd, "invalid tagger " + email);
        return;
      }

      if (tag.getFullMessage().contains("-----BEGIN PGP SIGNATURE-----\n")) {
        // Signed tags are currently assumed valid, as we don't have a GnuPG
        // key ring to validate them against, and we might be missing the
        // necessary (but currently optional) BouncyCastle Crypto libraries.
        //
      } else if (canPerform(PUSH_TAG, PUSH_TAG_ANNOTATED)) {
        // User is permitted to push an unsigned annotated tag.
        //
      } else {
        reject(cmd, "must be signed");
        return;
      }

      // Let the core receive process handle it
      //
    } catch (IOException e) {
      log.error("Bad tag " + cmd.getRefName() + " " + cmd.getNewId().name(), e);
      reject(cmd, "invalid object");
    }
  }

  private void parseUpdate(final ReceiveCommand cmd) {
    if (isHead(cmd) && canPerform(PUSH_HEAD, PUSH_HEAD_UPDATE)) {
      // Let the core receive process handle it
    } else {
      reject(cmd);
    }
  }

  private void parseRewindOrDelete(final ReceiveCommand cmd) {
    if (isHead(cmd) && canPerform(PUSH_HEAD, PUSH_HEAD_REPLACE)) {
      // Let the core receive process handle it

    } else if (isHead(cmd) && cmd.getType() == Type.UPDATE_NONFASTFORWARD) {
      cmd.setResult(ReceiveCommand.Result.REJECTED_NONFASTFORWARD);

    } else {
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
      log.error("Cannot lookup branch " + proj + " " + destBranchName, e);
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
    if (cmd.getType() != ReceiveCommand.Type.CREATE) {
      reject(cmd, "invalid usage");
      return;
    }

    final Change changeEnt;
    try {
      changeEnt = db.changes().get(changeId);
    } catch (OrmException e) {
      log.error("Cannot lookup existing change " + changeId, e);
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
      log.error("Invalid pack upload; one or more objects weren't sent", e);
    }

    if (toCreate.isEmpty() && addByChange.isEmpty()) {
      reject(newChange, "no new changes");
      return;
    }

    for (final RevCommit c : toCreate) {
      try {
        createChange(walk, c);
      } catch (IOException e) {
        log.error("Error computing patch of commit " + c.name(), e);
        reject(newChange, "diff error");
        return;
      } catch (OrmException e) {
        log.error("Error creating change for commit " + c.name(), e);
        reject(newChange, "database error");
        return;
      }
    }
    newChange.setResult(ReceiveCommand.Result.OK);
  }

  private void createChange(final RevWalk walk, final RevCommit c)
      throws OrmException, IOException {
    final Transaction txn = db.beginTransaction();
    final Account.Id me = userAccount.getId();
    final Change change =
        new Change(new Change.Id(db.nextChangeId()), me, destBranch
            .getNameKey());
    final PatchSet ps = new PatchSet(change.newPatchSetId());
    ps.setCreatedOn(change.getCreatedOn());
    ps.setUploader(me);
    final PatchSetImporter imp = new PatchSetImporter(db, repo, c, ps, true);
    imp.setTransaction(txn);
    imp.run();
    change.setCurrentPatchSet(imp.getPatchSetInfo());
    ChangeUtil.updated(change);
    db.changes().insert(Collections.singleton(change), txn);

    final Set<Account.Id> haveApprovals = new HashSet<Account.Id>();
    final List<ApprovalType> allTypes =
        Common.getGerritConfig().getApprovalTypes();
    haveApprovals.add(me);

    if (allTypes.size() > 0) {
      final Account.Id authorId =
          imp.getPatchSetInfo().getAuthor() != null ? imp.getPatchSetInfo()
              .getAuthor().getAccount() : null;
      final Account.Id committerId =
          imp.getPatchSetInfo().getCommitter() != null ? imp.getPatchSetInfo()
              .getCommitter().getAccount() : null;
      final ApprovalCategory.Id catId =
          allTypes.get(allTypes.size() - 1).getCategory().getId();
      if (authorId != null && haveApprovals.add(authorId)) {
        db.changeApprovals().insert(
            Collections.singleton(new ChangeApproval(new ChangeApproval.Key(
                change.getId(), authorId, catId), (short) 0)), txn);
      }
      if (committerId != null && haveApprovals.add(committerId)) {
        db.changeApprovals().insert(
            Collections.singleton(new ChangeApproval(new ChangeApproval.Key(
                change.getId(), committerId, catId), (short) 0)), txn);
      }
      for (final Account.Id reviewer : reviewerId) {
        if (haveApprovals.add(reviewer)) {
          db.changeApprovals().insert(
              Collections.singleton(new ChangeApproval(new ChangeApproval.Key(
                  change.getId(), reviewer, catId), (short) 0)), txn);
        }
      }
    }

    txn.commit();

    final RefUpdate ru = repo.updateRef(ps.getRefName());
    ru.setForceUpdate(true);
    ru.setNewObjectId(c);
    ru.setRefLogIdent(refLogIdent);
    ru.setRefLogMessage("uploaded", false);
    if (ru.update(walk) != RefUpdate.Result.NEW) {
      throw new IOException("Failed to create ref " + ps.getRefName() + " in "
          + repo.getDirectory() + ": " + ru.getResult());
    }
    PushQueue.scheduleUpdate(proj.getNameKey(), ru.getName());

    allNewChanges.add(change.getId());

    try {
      final ChangeMail cm = new ChangeMail(server, change);
      cm.setFrom(me);
      cm.setPatchSet(ps, imp.getPatchSetInfo());
      cm.setReviewDb(db);
      cm.addReviewers(reviewerId);
      cm.addExtraCC(ccId);
      cm.sendNewChange();
    } catch (MessagingException e) {
      log.error("Cannot send email for new change " + change.getId(), e);
    }
  }

  private void appendPatchSets() {
    for (Map.Entry<Change.Id, ReceiveCommand> e : addByChange.entrySet()) {
      final ReceiveCommand cmd = e.getValue();
      final Change.Id changeId = e.getKey();
      try {
        appendPatchSet(changeId, cmd);
      } catch (IOException err) {
        log.error("Error computing replacement patch for change " + changeId
            + ", commit " + cmd.getNewId().name(), e);
        reject(cmd, "diff error");
      } catch (OrmException err) {
        log.error("Error storing replacement patch for change " + changeId
            + ", commit " + cmd.getNewId().name(), e);
        reject(cmd, "database error");
      }
      if (cmd.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
        log.error("Replacement patch for change " + changeId + ", commit "
            + cmd.getNewId().name() + " wasn't attempted."
            + "  This is a bug in the receive process implementation.");
        reject(cmd, "internal error");
      }
    }
  }

  private void appendPatchSet(final Change.Id changeId, final ReceiveCommand cmd)
      throws IOException, OrmException {
    final RevCommit c = rp.getRevWalk().parseCommit(cmd.getNewId());
    if (!validCommitter(cmd, c)) {
      return;
    }

    final Account.Id me = userAccount.getId();
    final ReplaceResult result;

    result = db.run(new OrmRunnable<ReplaceResult, ReviewDb>() {
      public ReplaceResult run(final ReviewDb db, final Transaction txn,
          final boolean isRetry) throws OrmException {
        final Change change;
        if (isRetry) {
          change = db.changes().get(changeId);
          if (change == null) {
            reject(cmd, "change " + changeId.get() + " not found");
            return null;
          }
          if (change.getStatus().isClosed()) {
            reject(cmd, "change " + changeId.get() + " closed");
            return null;
          }
        } else {
          change = changeCache.get(changeId);
        }

        // Don't allow the same commit to appear twice on the same change
        //
        if (!db.patchSets().byChangeRevision(changeId,
            new RevId(cmd.getNewId().name())).toList().isEmpty()) {
          reject(cmd, "patch set exists");
          return null;
        }

        final PatchSet ps = new PatchSet(change.newPatchSetId());
        ps.setCreatedOn(new Timestamp(System.currentTimeMillis()));
        ps.setUploader(userAccount.getId());
        PatchSetImporter imp = new PatchSetImporter(db, repo, c, ps, true);
        imp.setTransaction(txn);
        try {
          imp.run();
        } catch (IOException e) {
          throw new OrmException(e);
        }

        final Account.Id authorId =
            imp.getPatchSetInfo().getAuthor() != null ? imp.getPatchSetInfo()
                .getAuthor().getAccount() : null;
        final Account.Id committerId =
            imp.getPatchSetInfo().getCommitter() != null ? imp
                .getPatchSetInfo().getCommitter().getAccount() : null;

        boolean haveAuthor = false;
        boolean haveCommitter = false;
        final Set<Account.Id> haveApprovals = new HashSet<Account.Id>();
        for (ChangeApproval a : db.changeApprovals().byChange(change.getId())) {
          haveApprovals.add(a.getAccountId());
          if (!haveAuthor && authorId != null
              && a.getAccountId().equals(authorId)) {
            haveAuthor = true;
          }
          if (!haveCommitter && committerId != null
              && a.getAccountId().equals(committerId)) {
            haveCommitter = true;
          }

          if (me.equals(a.getAccountId())) {
            // Leave my own approvals alone.
            //

          } else if (a.getValue() > 0) {
            a.clear();
            db.changeApprovals().update(Collections.singleton(a), txn);
          }
        }

        final List<ApprovalType> allTypes =
            Common.getGerritConfig().getApprovalTypes();
        if (allTypes.size() > 0) {
          final ApprovalCategory.Id catId =
              allTypes.get(allTypes.size() - 1).getCategory().getId();
          if (authorId != null && haveApprovals.add(authorId)) {
            db.changeApprovals().insert(
                Collections.singleton(new ChangeApproval(
                    new ChangeApproval.Key(change.getId(), authorId, catId),
                    (short) 0)), txn);
          }
          if (committerId != null && haveApprovals.add(committerId)) {
            db.changeApprovals().insert(
                Collections.singleton(new ChangeApproval(
                    new ChangeApproval.Key(change.getId(), committerId, catId),
                    (short) 0)), txn);
          }
          for (final Account.Id reviewer : reviewerId) {
            if (haveApprovals.add(reviewer)) {
              db.changeApprovals().insert(
                  Collections.singleton(new ChangeApproval(
                      new ChangeApproval.Key(change.getId(), reviewer, catId),
                      (short) 0)), txn);
            }
          }
        }

        final ChangeMessage msg =
            new ChangeMessage(new ChangeMessage.Key(change.getId(), ChangeUtil
                .messageUUID(db)), me, ps.getCreatedOn());
        msg.setMessage("Uploaded patch set " + ps.getPatchSetId() + ".");
        db.changeMessages().insert(Collections.singleton(msg), txn);

        change.setStatus(Change.Status.NEW);
        change.setCurrentPatchSet(imp.getPatchSetInfo());
        ChangeUtil.updated(change);
        db.changes().update(Collections.singleton(change), txn);

        final ReplaceResult result = new ReplaceResult();
        result.change = change;
        result.patchSet = ps;
        result.info = imp.getPatchSetInfo();
        result.msg = msg;
        return result;
      }
    });
    if (result != null) {
      final PatchSet ps = result.patchSet;
      final RefUpdate ru = repo.updateRef(ps.getRefName());
      ru.setForceUpdate(true);
      ru.setNewObjectId(c);
      ru.setRefLogIdent(refLogIdent);
      ru.setRefLogMessage("uploaded", false);
      if (ru.update(rp.getRevWalk()) != RefUpdate.Result.NEW) {
        throw new IOException("Failed to create ref " + ps.getRefName()
            + " in " + repo.getDirectory() + ": " + ru.getResult());
      }
      PushQueue.scheduleUpdate(proj.getNameKey(), ru.getName());
      cmd.setResult(ReceiveCommand.Result.OK);

      try {
        final ChangeMail cm = new ChangeMail(server, result.change);
        cm.setFrom(me);
        cm.setPatchSet(ps, result.info);
        cm.setChangeMessage(result.msg);
        cm.setReviewDb(db);
        cm.addReviewers(reviewerId);
        cm.addExtraCC(ccId);
        cm.sendNewPatchSet();
      } catch (MessagingException e) {
        log.error("Cannot send email for new patch set " + ps.getId(), e);
      }
    }
  }

  private static class ReplaceResult {
    Change change;
    PatchSet patchSet;
    PatchSetInfo info;
    ChangeMessage msg;
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

  private void insertBranchEntity(final ReceiveCommand c) {
    try {
      final Branch.NameKey nameKey =
          new Branch.NameKey(proj.getNameKey(), c.getRefName());
      final Branch.Id idKey = new Branch.Id(db.nextBranchId());
      final Branch b = new Branch(nameKey, idKey);
      db.branches().insert(Collections.singleton(b));
    } catch (OrmException e) {
      final String msg = "database failure creating " + c.getRefName();
      log.error(msg, e);

      try {
        err.write(("remote error: " + msg + "\n").getBytes("UTF-8"));
        err.flush();
      } catch (IOException e2) {
        // Ignore errors writing to the client
      }
    }
  }

  private void deleteBranchEntity(final ReceiveCommand c) {
    try {
      final Branch.NameKey nameKey =
          new Branch.NameKey(proj.getNameKey(), c.getRefName());
      final Branch b = db.branches().get(nameKey);
      if (b != null) {
        db.branches().delete(Collections.singleton(b));
      }
    } catch (OrmException e) {
      final String msg = "database failure deleting " + c.getRefName();
      log.error(msg, e);

      try {
        err.write(("remote error: " + msg + "\n").getBytes("UTF-8"));
        err.flush();
      } catch (IOException e2) {
        // Ignore errors writing to the client
      }
    }
  }

  private static void reject(final ReceiveCommand cmd) {
    reject(cmd, "prohibited by Gerrit");
  }

  private static void reject(final ReceiveCommand cmd, final String why) {
    cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, why);
  }

  private static boolean isTag(final ReceiveCommand cmd) {
    return cmd.getRefName().startsWith(Constants.R_TAGS);
  }

  private static boolean isHead(final ReceiveCommand cmd) {
    return cmd.getRefName().startsWith(Constants.R_HEADS);
  }
}
