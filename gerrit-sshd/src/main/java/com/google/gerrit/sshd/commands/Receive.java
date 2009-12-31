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

package com.google.gerrit.sshd.commands;

import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD_REPLACE;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_HEAD_UPDATE;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_TAG;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_TAG_ANNOTATED;
import static com.google.gerrit.reviewdb.ApprovalCategory.PUSH_TAG_ANY;

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.common.data.ApprovalType;
import com.google.gerrit.common.data.ApprovalTypes;
import com.google.gerrit.common.errors.NoSuchAccountException;
import com.google.gerrit.reviewdb.AbstractAgreement;
import com.google.gerrit.reviewdb.Account;
import com.google.gerrit.reviewdb.AccountAgreement;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.AccountGroupAgreement;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Branch;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ChangeMessage;
import com.google.gerrit.reviewdb.ContributorAgreement;
import com.google.gerrit.reviewdb.PatchSet;
import com.google.gerrit.reviewdb.PatchSetAncestor;
import com.google.gerrit.reviewdb.PatchSetApproval;
import com.google.gerrit.reviewdb.PatchSetInfo;
import com.google.gerrit.reviewdb.RevId;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.Nullable;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gerrit.server.mail.CreateChangeSender;
import com.google.gerrit.server.mail.EmailException;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gwtorm.client.AtomicUpdate;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevFlag;
import org.eclipse.jgit.revwalk.RevFlagSet;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevTag;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PostReceiveHook;
import org.eclipse.jgit.transport.PreReceiveHook;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.transport.ReceiveCommand.Type;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.PrintWriter;
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



/** Receives change upload over SSH using the Git receive-pack protocol. */
final class Receive extends AbstractGitCommand {
  private static final Logger log = LoggerFactory.getLogger(Receive.class);

  private static final String NEW_CHANGE = "refs/for/";
  private static final Pattern NEW_PATCHSET =
      Pattern.compile("^refs/changes/(?:[0-9][0-9]/)?([1-9][0-9]*)(?:/new)?$");

  private static final FooterKey REVIEWED_BY = new FooterKey("Reviewed-by");
  private static final FooterKey TESTED_BY = new FooterKey("Tested-by");
  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  private final Set<Account.Id> reviewerId = new HashSet<Account.Id>();
  private final Set<Account.Id> ccId = new HashSet<Account.Id>();

  @Option(name = "--reviewer", aliases = {"--re"}, multiValued = true, metaVar = "EMAIL", usage = "request reviewer for change(s)")
  void addReviewer(final Account.Id id) {
    reviewerId.add(id);
  }

  @Option(name = "--cc", aliases = {}, multiValued = true, metaVar = "EMAIL", usage = "CC user on change(s)")
  void addCC(final Account.Id id) {
    ccId.add(id);
  }

  @Inject
  private IdentifiedUser.GenericFactory identifiedUserFactory;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ReviewDb db;

  @Inject
  private ApprovalTypes approvalTypes;

  @Inject
  private AccountResolver accountResolver;

  @Inject
  private CreateChangeSender.Factory createChangeSenderFactory;

  @Inject
  private MergedSender.Factory mergedSenderFactory;

  @Inject
  private ReplacePatchSetSender.Factory replacePatchSetFactory;

  @Inject
  private ReplicationQueue replication;

  @Inject
  private PatchSetInfoFactory patchSetInfoFactory;

  @Inject
  @CanonicalWebUrl
  @Nullable
  private String canonicalWebUrl;

  @Inject
  @GerritPersonIdent
  private PersonIdent gerritIdent;

  private ReceivePack rp;
  private PersonIdent refLogIdent;
  private ReceiveCommand newChange;
  private Branch.NameKey destBranch;

  private final List<Change.Id> allNewChanges = new ArrayList<Change.Id>();
  private final Map<Change.Id, ReplaceRequest> replaceByChange =
      new HashMap<Change.Id, ReplaceRequest>();
  private final Map<RevCommit, ReplaceRequest> replaceByCommit =
      new HashMap<RevCommit, ReplaceRequest>();

  private Map<ObjectId, Ref> refsById;

  protected boolean canUpload() {
    return canPerform(ApprovalCategory.READ, (short) 2);
  }

  @Override
  protected void runImpl() throws IOException, Failure {
    if (!canUpload()) {
      final String reqName = project.getName();
      throw new Failure(1,
          "fatal: Upload denied for project '" + reqName + "'",
          new SecurityException("Account lacks Upload permission"));
    }

    if (project.isUseContributorAgreements()) {
      verifyActiveContributorAgreement();
    }
    refLogIdent = currentUser.newRefLogIdent();

    verifyProjectVisible("reviewer", reviewerId);
    verifyProjectVisible("CC", ccId);

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
        if (newChange != null
            && newChange.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
          createNewChanges();
        }
        doReplaces();
      }
    });
    rp.setPostReceiveHook(new PostReceiveHook() {
      public void onPostReceive(final ReceivePack arg0,
          final Collection<ReceiveCommand> commands) {
        for (final ReceiveCommand c : commands) {
          if (c.getResult() == Result.OK) {
            if (isHead(c)) {
              switch (c.getType()) {
                case CREATE:
                  autoCloseChanges(c);
                  break;
                case DELETE:
                  break;
                case UPDATE:
                case UPDATE_NONFASTFORWARD:
                  autoCloseChanges(c);
                  break;
              }
            }

            if (isHead(c) || isTag(c)) {
              // We only schedule heads and tags for replication.
              // Change refs are scheduled when they are created.
              //
              replication.scheduleUpdate(project.getNameKey(), c.getRefName());
            }
          }
        }
      }
    });
    rp.receive(in, out, err);

    if (!allNewChanges.isEmpty() && canonicalWebUrl != null) {
      // Make sure there isn't anything buffered; we want to give the
      // push client a chance to display its status report before we
      // show our own messages on standard error.
      //
      out.flush();

      final String url = canonicalWebUrl;
      final PrintWriter msg = toPrintWriter(err);
      msg.write("\nNew Changes:\n");
      for (final Change.Id c : allNewChanges) {
        msg.write("  " + url + c.get() + "\n");
      }
      msg.write('\n');
      msg.flush();
    }
  }

  private void verifyProjectVisible(final String type, final Set<Account.Id> who)
      throws UnloggedFailure {
    for (final Account.Id id : who) {
      final IdentifiedUser user = identifiedUserFactory.create(id);
      if (!projectControl.forUser(user).isVisible()) {
        throw new UnloggedFailure(1, type + " "
            + user.getAccount().getFullName() + " cannot access the project");
      }
    }
  }

  private void verifyActiveContributorAgreement() throws Failure {
    AbstractAgreement bestAgreement = null;
    ContributorAgreement bestCla = null;
    try {
      OUTER: for (AccountGroup.Id groupId : currentUser.getEffectiveGroups()) {
        for (final AccountGroupAgreement a : db.accountGroupAgreements()
            .byGroup(groupId)) {
          final ContributorAgreement cla =
              db.contributorAgreements().get(a.getAgreementId());
          if (cla == null) {
            continue;
          }

          bestAgreement = a;
          bestCla = cla;
          break OUTER;
        }
      }

      if (bestAgreement == null) {
        for (final AccountAgreement a : db.accountAgreements().byAccount(
            currentUser.getAccountId()).toList()) {
          final ContributorAgreement cla =
              db.contributorAgreements().get(a.getAgreementId());
          if (cla == null) {
            continue;
          }

          bestAgreement = a;
          bestCla = cla;
          break;
        }
      }
    } catch (OrmException e) {
      throw new Failure(1, "fatal: database error", e);
    }

    if (bestCla != null && !bestCla.isActive()) {
      final StringBuilder msg = new StringBuilder();
      msg.append("\nfatal: ");
      msg.append(bestCla.getShortName());
      msg.append(" contributor agreement is expired.\n");
      if (canonicalWebUrl != null) {
        msg.append("\nPlease complete a new agreement");
        msg.append(":\n\n  ");
        msg.append(canonicalWebUrl);
        msg.append("#");
        msg.append(PageLinks.SETTINGS_AGREEMENTS);
        msg.append("\n");
      }
      msg.append("\n");
      throw new UnloggedFailure(1, msg.toString());
    }

    if (bestCla != null && bestCla.isRequireContactInformation()) {
      boolean fail = false;
      fail |= missing(currentUser.getAccount().getFullName());
      fail |= missing(currentUser.getAccount().getPreferredEmail());
      fail |= !currentUser.getAccount().isContactFiled();

      if (fail) {
        final StringBuilder msg = new StringBuilder();
        msg.append("\nfatal: ");
        msg.append(bestCla.getShortName());
        msg.append(" contributor agreement requires");
        msg.append(" current contact information.\n");
        if (canonicalWebUrl != null) {
          msg.append("\nPlease review your contact information");
          msg.append(":\n\n  ");
          msg.append(canonicalWebUrl);
          msg.append("#");
          msg.append(PageLinks.SETTINGS_CONTACT);
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
    if (canonicalWebUrl != null) {
      msg.append(":\n\n  ");
      msg.append(canonicalWebUrl);
      msg.append("#");
      msg.append(PageLinks.SETTINGS_AGREEMENTS);
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

  private Account.Id toAccountId(final String nameOrEmail) throws OrmException,
      NoSuchAccountException {
    final Account a = accountResolver.find(nameOrEmail);
    if (a == null) {
      throw new NoSuchAccountException("\"" + nameOrEmail
          + "\" is not registered");
    }
    return a.getId();
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
        parseReplaceCommand(cmd, changeId);
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
    if (projectControl.canCreateRef(cmd.getRefName())) {
      if (isTag(cmd)) {
        parseCreateTag(cmd);
      }

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
      if (!currentUser.getEmailAddresses().contains(email)) {
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
    if (isHead(cmd) && cmd.getType() == Type.DELETE
        && projectControl.canDeleteRef(cmd.getRefName())) {
      // Let the core receive process handle it

    } else if (isHead(cmd) && canPerform(PUSH_HEAD, PUSH_HEAD_REPLACE)) {
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

    if (rp.getAdvertisedRefs().containsKey(destBranchName)) {
      // We advertised the branch to the client so we know
      // the branch exists. Target this branch for the upload.
      //
      destBranch = new Branch.NameKey(project.getNameKey(), destBranchName);

    } else {
      // We didn't advertise the branch, because it doesn't exist yet.
      // Allow it anyway if HEAD is a symbolic reference to the name.
      //
      final String head;
      try {
        head = repo.getFullBranch();
      } catch (IOException e) {
        log.error("Cannot read HEAD symref", e);
        reject(cmd, "internal error");
        return;
      }

      if (head.equals(destBranchName)) {
        destBranch = new Branch.NameKey(project.getNameKey(), destBranchName);
      }
    }

    if (destBranch == null) {
      String n = destBranchName;
      if (n.startsWith(Constants.R_HEADS))
        n = n.substring(Constants.R_HEADS.length());
      reject(cmd, "branch " + n + " not found");
      return;
    }

    // Validate that the new commits are connected with the existing heads
    // or tags of this repository. If they aren't, we want to abort. We do
    // this check by coloring the tip CONNECTED and letting a RevWalk push
    // that color through the graph until it reaches at least one of our
    // already existing heads or tags. We then test to see if that color
    // made it back onto that set.
    //
    try {
      final RevWalk walk = rp.getRevWalk();

      final RevFlag SIDE_NEW = walk.newFlag("NEW");
      final RevFlag SIDE_HAVE = walk.newFlag("HAVE");
      final RevFlagSet COMMON = new RevFlagSet();
      COMMON.add(SIDE_NEW);
      COMMON.add(SIDE_HAVE);
      walk.carry(COMMON);

      walk.reset();
      walk.sort(RevSort.TOPO);
      walk.sort(RevSort.REVERSE, true);

      final RevCommit tip = walk.parseCommit(newChange.getNewId());
      tip.add(SIDE_NEW);
      walk.markStart(tip);

      boolean haveHeads = false;
      for (final Ref r : rp.getAdvertisedRefs().values()) {
        if (isHead(r) || isTag(r)) {
          try {
            final RevCommit h = walk.parseCommit(r.getObjectId());
            h.add(SIDE_HAVE);
            walk.markStart(h);
            haveHeads = true;
          } catch (IOException e) {
            continue;
          }
        }
      }

      if (haveHeads) {
        boolean isConnected = false;
        RevCommit c;
        while ((c = walk.next()) != null) {
          if (c.hasAll(COMMON)) {
            isConnected = true;
            break;
          }
        }
        if (!isConnected) {
          reject(newChange, "no common ancestry");
          return;
        }
      }
    } catch (IOException e) {
      newChange.setResult(Result.REJECTED_MISSING_OBJECT);
      log.error("Invalid pack upload; one or more objects weren't sent", e);
      return;
    }
  }

  private void parseReplaceCommand(final ReceiveCommand cmd,
      final Change.Id changeId) {
    if (cmd.getType() != ReceiveCommand.Type.CREATE) {
      reject(cmd, "invalid usage");
      return;
    }

    final RevCommit newCommit;
    try {
      newCommit = rp.getRevWalk().parseCommit(cmd.getNewId());
    } catch (IOException e) {
      log.error("Cannot parse " + cmd.getNewId().name() + " as commit", e);
      reject(cmd, "invalid commit");
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
      reject(cmd, "change " + changeId + " not found");
      return;
    }
    if (!project.getNameKey().equals(changeEnt.getProject())) {
      reject(cmd, "change " + changeId + " not found");
      return;
    }

    requestReplace(cmd, changeEnt, newCommit);
  }

  private void requestReplace(final ReceiveCommand cmd, final Change change,
      final RevCommit newCommit) {
    if (change.getStatus().isClosed()) {
      reject(cmd, "change " + change.getId() + " closed");
      return;
    }

    final ReplaceRequest req =
        new ReplaceRequest(change.getId(), newCommit, cmd);
    if (replaceByChange.containsKey(req.ontoChange)) {
      reject(cmd, "duplicate request");
      return;
    }
    if (replaceByCommit.containsKey(req.newCommit)) {
      reject(cmd, "duplicate request");
      return;
    }
    replaceByChange.put(req.ontoChange, req);
    replaceByCommit.put(req.newCommit, req);
  }

  private void createNewChanges() {
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
        if (replaceByCommit.containsKey(c)) {
          // This commit was already scheduled to replace an existing PatchSet.
          //
          continue;
        }
        if (!validCommitter(newChange, c)) {
          // Not a change the user can propose? Abort as early as possible.
          //
          return;
        }

        final List<String> idList = c.getFooterLines(CHANGE_ID);
        if (!idList.isEmpty()) {
          final String idStr = idList.get(idList.size() - 1).trim();
          final Change.Key key = new Change.Key(idStr);
          final List<Change> changes =
              db.changes().byProjectKey(project.getNameKey(), key).toList();
          if (changes.size() > 1) {
            // WTF, multiple changes in this project have the same key?
            // Since the commit is new, the user should recreate it with
            // a different Change-Id. In practice, we should never see
            // this error message as Change-Id should be unique.
            //
            reject(newChange, key.get() + " has duplicates");
            return;

          }

          if (changes.size() == 1) {
            // Schedule as a replacement to this one matching change.
            //
            requestReplace(newChange, changes.get(0), c);
            continue;
          }
        }

        toCreate.add(c);
      }
    } catch (IOException e) {
      // Should never happen, the core receive process would have
      // identified the missing object earlier before we got control.
      //
      newChange.setResult(Result.REJECTED_MISSING_OBJECT);
      log.error("Invalid pack upload; one or more objects weren't sent", e);
      return;
    } catch (OrmException e) {
      log.error("Cannot query database to locate prior changes", e);
      reject(newChange, "database error");
      return;
    }

    if (toCreate.isEmpty() && replaceByChange.isEmpty()) {
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
    walk.parseBody(c);

    final Account.Id me = currentUser.getAccountId();
    Change.Key changeKey = new Change.Key("I" + c.name());
    final Set<Account.Id> reviewers = new HashSet<Account.Id>(reviewerId);
    final Set<Account.Id> cc = new HashSet<Account.Id>(ccId);
    for (final FooterLine footerLine : c.getFooterLines()) {
      try {
        if (footerLine.matches(CHANGE_ID)) {
          final String v = footerLine.getValue().trim();
          if (v.matches("^I[0-9a-f]{8,}.*$")) {
            changeKey = new Change.Key(v);
          }
        } else if (isReviewer(footerLine)) {
          reviewers.add(toAccountId(footerLine.getValue().trim()));
        } else if (footerLine.matches(FooterKey.CC)) {
          cc.add(toAccountId(footerLine.getValue().trim()));
        }
      } catch (NoSuchAccountException e) {
        continue;
      }
    }
    reviewers.remove(me);
    cc.remove(me);
    cc.removeAll(reviewers);

    final Change change =
        new Change(changeKey, new Change.Id(db.nextChangeId()), me, destBranch);
    change.nextPatchSetId();

    final PatchSet ps = new PatchSet(change.currPatchSetId());
    ps.setCreatedOn(change.getCreatedOn());
    ps.setUploader(me);
    ps.setRevision(toRevId(c));
    insertAncestors(ps.getId(), c);
    db.patchSets().insert(Collections.singleton(ps));

    final PatchSetInfo info = patchSetInfoFactory.get(c, ps.getId());
    change.setCurrentPatchSet(info);
    ChangeUtil.updated(change);
    db.changes().insert(Collections.singleton(change));

    final Set<Account.Id> haveApprovals = new HashSet<Account.Id>();
    final List<ApprovalType> allTypes = approvalTypes.getApprovalTypes();
    haveApprovals.add(me);

    if (allTypes.size() > 0) {
      final Account.Id authorId =
          info.getAuthor() != null ? info.getAuthor().getAccount() : null;
      final Account.Id committerId =
          info.getCommitter() != null ? info.getCommitter().getAccount() : null;
      final ApprovalCategory.Id catId =
          allTypes.get(allTypes.size() - 1).getCategory().getId();
      if (authorId != null && haveApprovals.add(authorId)) {
        insertDummyApproval(change, ps.getId(), authorId, catId, db);
      }
      if (committerId != null && haveApprovals.add(committerId)) {
        insertDummyApproval(change, ps.getId(), committerId, catId, db);
      }
      for (final Account.Id reviewer : reviewers) {
        if (haveApprovals.add(reviewer)) {
          insertDummyApproval(change, ps.getId(), reviewer, catId, db);
        }
      }
    }

    final RefUpdate ru = repo.updateRef(ps.getRefName());
    ru.setNewObjectId(c);
    ru.disableRefLog();
    if (ru.update(walk) != RefUpdate.Result.NEW) {
      throw new IOException("Failed to create ref " + ps.getRefName() + " in "
          + repo.getDirectory() + ": " + ru.getResult());
    }
    replication.scheduleUpdate(project.getNameKey(), ru.getName());

    allNewChanges.add(change.getId());

    try {
      final CreateChangeSender cm;
      cm = createChangeSenderFactory.create(change);
      cm.setFrom(me);
      cm.setPatchSet(ps, info);
      cm.setReviewDb(db);
      cm.addReviewers(reviewers);
      cm.addExtraCC(cc);
      cm.send();
    } catch (EmailException e) {
      log.error("Cannot send email for new change " + change.getId(), e);
    }
  }

  private static boolean isReviewer(final FooterLine candidateFooterLine) {
    return candidateFooterLine.matches(FooterKey.SIGNED_OFF_BY)
        || candidateFooterLine.matches(FooterKey.ACKED_BY)
        || candidateFooterLine.matches(REVIEWED_BY)
        || candidateFooterLine.matches(TESTED_BY);
  }

  private void doReplaces() {
    for (final ReplaceRequest request : replaceByChange.values()) {
      try {
        doReplace(request);
      } catch (IOException err) {
        log.error("Error computing replacement patch for change "
            + request.ontoChange + ", commit " + request.newCommit.name(), err);
        reject(request.cmd, "diff error");
      } catch (OrmException err) {
        log.error("Error storing replacement patch for change "
            + request.ontoChange + ", commit " + request.newCommit.name(), err);
        reject(request.cmd, "database error");
      }
      if (request.cmd.getResult() == ReceiveCommand.Result.NOT_ATTEMPTED) {
        log.error("Replacement patch for change " + request.ontoChange
            + ", commit " + request.newCommit.name() + " wasn't attempted."
            + "  This is a bug in the receive process implementation.");
        reject(request.cmd, "internal error");
      }
    }
  }

  private PatchSet.Id doReplace(final ReplaceRequest request)
      throws IOException, OrmException {
    final RevCommit c = request.newCommit;
    rp.getRevWalk().parseBody(c);
    if (!validCommitter(request.cmd, c)) {
      return null;
    }

    final Account.Id me = currentUser.getAccountId();
    final Set<Account.Id> reviewers = new HashSet<Account.Id>(reviewerId);
    final Set<Account.Id> cc = new HashSet<Account.Id>(ccId);
    for (final FooterLine footerLine : c.getFooterLines()) {
      try {
        if (isReviewer(footerLine)) {
          reviewers.add(toAccountId(footerLine.getValue().trim()));
        } else if (footerLine.matches(FooterKey.CC)) {
          cc.add(toAccountId(footerLine.getValue().trim()));
        }
      } catch (NoSuchAccountException e) {
        continue;
      }
    }
    reviewers.remove(me);
    cc.remove(me);
    cc.removeAll(reviewers);

    final ReplaceResult result = new ReplaceResult();
    final Set<Account.Id> oldReviewers = new HashSet<Account.Id>();
    final Set<Account.Id> oldCC = new HashSet<Account.Id>();

    Change change = db.changes().get(request.ontoChange);
    if (change == null) {
      reject(request.cmd, "change " + request.ontoChange + " not found");
      return null;
    }
    if (change.getStatus().isClosed()) {
      reject(request.cmd, "change " + request.ontoChange + " closed");
      return null;
    }

    final PatchSet.Id priorPatchSet = change.currentPatchSetId();
    for (final PatchSet ps : db.patchSets().byChange(request.ontoChange)) {
      if (ps.getRevision() == null) {
        reject(request.cmd, "change state corrupt");
        return null;
      }

      final String revIdStr = ps.getRevision().get();
      final ObjectId commitId;
      try {
        commitId = ObjectId.fromString(revIdStr);
      } catch (IllegalArgumentException e) {
        log.warn("Invalid revision in " + ps.getId() + ": " + revIdStr);
        reject(request.cmd, "change state corrupt");
        return null;
      }

      try {
        final RevCommit prior = rp.getRevWalk().parseCommit(commitId);

        // Don't allow a change to directly depend upon itself. This is a
        // very common error due to users making a new commit rather than
        // amending when trying to address review comments.
        //
        if (rp.getRevWalk().isMergedInto(prior, c)) {
          reject(request.cmd, "squash commits first");
          return null;
        }

        // Don't allow the same commit to appear twice on the same change
        //
        if (c == prior) {
          reject(request.cmd, "commit already exists");
          return null;
        }

        // Don't allow the same tree if the commit message is unmodified
        // or no parents were updated (rebase), else warn that only part
        // of the commit was modified.
        //
        if (priorPatchSet.equals(ps.getId()) && c.getTree() == prior.getTree()) {
          rp.getRevWalk().parseBody(prior);
          final boolean messageEq =
              c.getFullMessage().equals(prior.getFullMessage());
          final boolean parentsEq = parentsEqual(c, prior);

          if (messageEq && parentsEq) {
            reject(request.cmd, "no changes made");
            return null;
          } else {
            err.write(Constants.encode("warning: " //
                + change.getKey().abbreviate() + ": " //
                + " no files changed, but" //
                + (!messageEq ? " message updated" : "") //
                + (!messageEq && !parentsEq ? " and" : "") //
                + (!parentsEq ? " was rebased" : "") //
                + "\n" //
            ));
          }
        }
      } catch (IOException e) {
        log.error("Change " + change.getId() + " missing " + revIdStr, e);
        reject(request.cmd, "change state corrupt");
        return null;
      }
    }

    change =
        db.changes().atomicUpdate(change.getId(), new AtomicUpdate<Change>() {
          @Override
          public Change update(Change change) {
            if (change.getStatus().isOpen()) {
              change.nextPatchSetId();
              return change;
            } else {
              return null;
            }
          }
        });
    if (change == null) {
      reject(request.cmd, "change is closed");
      return null;
    }

    final PatchSet ps = new PatchSet(change.currPatchSetId());
    ps.setCreatedOn(new Timestamp(System.currentTimeMillis()));
    ps.setUploader(currentUser.getAccountId());
    ps.setRevision(toRevId(c));
    insertAncestors(ps.getId(), c);
    db.patchSets().insert(Collections.singleton(ps));

    final Ref mergedInto = findMergedInto(change.getDest().get(), c);
    result.mergedIntoRef = mergedInto != null ? mergedInto.getName() : null;
    result.change = change;
    result.patchSet = ps;
    result.info = patchSetInfoFactory.get(c, ps.getId());

    final Account.Id authorId =
        result.info.getAuthor() != null ? result.info.getAuthor().getAccount()
            : null;
    final Account.Id committerId =
        result.info.getCommitter() != null ? result.info.getCommitter()
            .getAccount() : null;

    boolean haveAuthor = false;
    boolean haveCommitter = false;
    final Set<Account.Id> haveApprovals = new HashSet<Account.Id>();

    oldReviewers.clear();
    oldCC.clear();

    for (PatchSetApproval a : db.patchSetApprovals().byChange(change.getId())) {
      haveApprovals.add(a.getAccountId());

      if (a.getValue() != 0) {
        oldReviewers.add(a.getAccountId());
      } else {
        oldCC.add(a.getAccountId());
      }

      final ApprovalType type =
          approvalTypes.getApprovalType(a.getCategoryId());
      if (a.getPatchSetId().equals(priorPatchSet)
          && type.getCategory().isCopyMinScore() && type.isMaxNegative(a)) {
        // If there was a negative vote on the prior patch set, carry it
        // into this patch set.
        //
        db.patchSetApprovals().insert(
            Collections.singleton(new PatchSetApproval(ps.getId(), a)));
      }

      if (!haveAuthor && authorId != null && a.getAccountId().equals(authorId)) {
        haveAuthor = true;
      }
      if (!haveCommitter && committerId != null
          && a.getAccountId().equals(committerId)) {
        haveCommitter = true;
      }
    }

    final ChangeMessage msg =
        new ChangeMessage(new ChangeMessage.Key(change.getId(), ChangeUtil
            .messageUUID(db)), me, ps.getCreatedOn());
    msg.setMessage("Uploaded patch set " + ps.getPatchSetId() + ".");
    db.changeMessages().insert(Collections.singleton(msg));
    result.msg = msg;

    if (result.mergedIntoRef != null) {
      // Change was already submitted to a branch, close it.
      //
      markChangeMergedByPush(db, result);
    } else {
      // Change should be new, so it can go through review again.
      //
      change =
          db.changes().atomicUpdate(change.getId(), new AtomicUpdate<Change>() {
            @Override
            public Change update(Change change) {
              if (change.getStatus().isOpen()) {
                change.setStatus(Change.Status.NEW);
                change.setCurrentPatchSet(result.info);
                ChangeUtil.updated(change);
                return change;
              } else {
                return null;
              }
            }
          });
      if (change == null) {
        db.patchSets().delete(Collections.singleton(ps));
        db.changeMessages().delete(Collections.singleton(msg));
        reject(request.cmd, "change is closed");
        return null;
      }
    }

    final List<ApprovalType> allTypes = approvalTypes.getApprovalTypes();
    if (allTypes.size() > 0) {
      final ApprovalCategory.Id catId =
          allTypes.get(allTypes.size() - 1).getCategory().getId();
      if (authorId != null && haveApprovals.add(authorId)) {
        insertDummyApproval(result, authorId, catId, db);
      }
      if (committerId != null && haveApprovals.add(committerId)) {
        insertDummyApproval(result, committerId, catId, db);
      }
      for (final Account.Id reviewer : reviewers) {
        if (haveApprovals.add(reviewer)) {
          insertDummyApproval(result, reviewer, catId, db);
        }
      }
    }

    final RefUpdate ru = repo.updateRef(ps.getRefName());
    ru.setNewObjectId(c);
    ru.disableRefLog();
    if (ru.update(rp.getRevWalk()) != RefUpdate.Result.NEW) {
      throw new IOException("Failed to create ref " + ps.getRefName() + " in "
          + repo.getDirectory() + ": " + ru.getResult());
    }
    replication.scheduleUpdate(project.getNameKey(), ru.getName());
    request.cmd.setResult(ReceiveCommand.Result.OK);

    try {
      final ReplacePatchSetSender cm;
      cm = replacePatchSetFactory.create(result.change);
      cm.setFrom(me);
      cm.setPatchSet(ps, result.info);
      cm.setChangeMessage(result.msg);
      cm.setReviewDb(db);
      cm.addReviewers(reviewers);
      cm.addExtraCC(cc);
      cm.addReviewers(oldReviewers);
      cm.addExtraCC(oldCC);
      cm.send();
    } catch (EmailException e) {
      log.error("Cannot send email for new patch set " + ps.getId(), e);
    }
    sendMergedEmail(result);
    return result != null ? result.info.getKey() : null;
  }

  static boolean parentsEqual(RevCommit a, RevCommit b) {
    if (a.getParentCount() != b.getParentCount()) {
      return false;
    }
    for (int i = 0; i < a.getParentCount(); i++) {
      if (a.getParent(i) != b.getParent(i)) {
        return false;
      }
    }
    return true;
  }

  private void insertDummyApproval(final ReplaceResult result,
      final Account.Id forAccount, final ApprovalCategory.Id catId,
      final ReviewDb db) throws OrmException {
    insertDummyApproval(result.change, result.patchSet.getId(), forAccount,
        catId, db);
  }

  private void insertDummyApproval(final Change change, final PatchSet.Id psId,
      final Account.Id forAccount, final ApprovalCategory.Id catId,
      final ReviewDb db) throws OrmException {
    final PatchSetApproval ca =
        new PatchSetApproval(new PatchSetApproval.Key(psId, forAccount, catId),
            (short) 0);
    ca.cache(change);
    db.patchSetApprovals().insert(Collections.singleton(ca));
  }

  private Ref findMergedInto(final String first, final RevCommit commit) {
    try {
      final Map<String, Ref> all = repo.getAllRefs();
      Ref firstRef = all.get(first);
      if (firstRef != null && isMergedInto(commit, firstRef)) {
        return firstRef;
      }
      for (Ref ref : all.values()) {
        if (isHead(ref)) {
          if (isMergedInto(commit, ref)) {
            return ref;
          }
        }
      }
      return null;
    } catch (IOException e) {
      log.warn("Can't check for already submitted change", e);
      return null;
    }
  }

  private boolean isMergedInto(final RevCommit commit, final Ref ref)
      throws IOException {
    final RevWalk rw = rp.getRevWalk();
    return rw.isMergedInto(commit, rw.parseCommit(ref.getObjectId()));
  }

  private static class ReplaceRequest {
    final Change.Id ontoChange;
    final RevCommit newCommit;
    final ReceiveCommand cmd;

    ReplaceRequest(final Change.Id toChange, final RevCommit newCommit,
        final ReceiveCommand cmd) {
      this.ontoChange = toChange;
      this.newCommit = newCommit;
      this.cmd = cmd;
    }
  }

  private static class ReplaceResult {
    Change change;
    PatchSet patchSet;
    PatchSetInfo info;
    ChangeMessage msg;
    String mergedIntoRef;
  }

  private boolean validCommitter(final ReceiveCommand cmd, final RevCommit c)
      throws MissingObjectException, IOException {
    rp.getRevWalk().parseBody(c);
    final PersonIdent committer = c.getCommitterIdent();
    final PersonIdent author = c.getAuthorIdent();

    // Don't allow the user to amend a merge created by Gerrit Code Review.
    // This seems to happen all too often, due to users not paying any
    // attention to what they are doing.
    //
    if (c.getParentCount() > 1
        && author.getName().equals(gerritIdent.getName())
        && author.getEmailAddress().equals(gerritIdent.getEmailAddress())) {
      reject(cmd, "do not amend merges not made by you");
      return false;
    }

    // Require that committer matches the uploader.
    //
    if (!currentUser.getEmailAddresses().contains(committer.getEmailAddress())) {
      reject(cmd, "you are not committer " + committer.getEmailAddress());
      return false;
    }

    if (project.isUseSignedOffBy()) {
      // If the project wants Signed-off-by / Acked-by lines, verify we
      // have them for the blamable parties involved on this change.
      //
      boolean sboAuthor = false, sboCommitter = false, sboMe = false;
      for (final FooterLine footer : c.getFooterLines()) {
        if (footer.matches(FooterKey.SIGNED_OFF_BY)) {
          final String e = footer.getEmailAddress();
          if (e != null) {
            sboAuthor |= author.getEmailAddress().equals(e);
            sboCommitter |= committer.getEmailAddress().equals(e);
            sboMe |= currentUser.getEmailAddresses().contains(e);
          }
        }
      }
      if (!sboAuthor && !sboCommitter && !sboMe) {
        reject(cmd, "not Signed-off-by author/committer/uploader");
        return false;
      }
    }

    return true;
  }

  private void autoCloseChanges(final ReceiveCommand cmd) {
    final RevWalk rw = rp.getRevWalk();
    try {
      rw.reset();
      rw.markStart(rw.parseCommit(cmd.getNewId()));
      if (!ObjectId.zeroId().equals(cmd.getOldId())) {
        rw.markUninteresting(rw.parseCommit(cmd.getOldId()));
      }

      final Map<ObjectId, Ref> byCommit = changeRefsById();
      final Map<Change.Key, Change.Id> byKey = openChangesByKey();
      final List<ReplaceRequest> toClose = new ArrayList<ReplaceRequest>();
      RevCommit c;
      while ((c = rw.next()) != null) {
        final Ref ref = byCommit.get(c.copy());
        if (ref != null) {
          closeChange(cmd, PatchSet.Id.fromRef(ref.getName()), c);
          continue;
        }

        rw.parseBody(c);
        for (final String changeId : c.getFooterLines(CHANGE_ID)) {
          final Change.Id onto = byKey.get(new Change.Key(changeId.trim()));
          if (onto != null) {
            toClose.add(new ReplaceRequest(onto, c, cmd));
            break;
          }
        }
      }

      for (final ReplaceRequest req : toClose) {
        final PatchSet.Id psi = doReplace(req);
        if (psi != null) {
          closeChange(req.cmd, psi, req.newCommit);
        } else {
          log.warn("Replacement of Change-Id " + req.ontoChange
              + " with commit " + req.newCommit.name()
              + " did not import the new patch set.");
        }
      }
    } catch (IOException e) {
      log.error("Can't scan for changes to close", e);
    } catch (OrmException e) {
      log.error("Can't scan for changes to close", e);
    }
  }

  private void closeChange(final ReceiveCommand cmd, final PatchSet.Id psi,
      final RevCommit commit) throws OrmException {
    final String refName = cmd.getRefName();
    final Change.Id cid = psi.getParentKey();

    final Change change = db.changes().get(cid);
    final PatchSet ps = db.patchSets().get(psi);
    if (change == null || ps == null) {
      log.warn(project.getName() + " " + psi + " is missing");
      return;
    }

    if (change.getStatus() == Change.Status.MERGED) {
      // If its already merged, don't make further updates, it
      // might just be moving from an experimental branch into
      // a more stable branch.
      //
      return;
    }

    final ReplaceResult result = new ReplaceResult();
    result.change = change;
    result.patchSet = ps;
    result.info = patchSetInfoFactory.get(commit, psi);
    result.mergedIntoRef = refName;

    markChangeMergedByPush(db, result);
    sendMergedEmail(result);
  }

  private Map<ObjectId, Ref> changeRefsById() {
    if (refsById == null) {
      refsById = new HashMap<ObjectId, Ref>();
      for (final Ref r : repo.getAllRefs().values()) {
        if (PatchSet.isRef(r.getName())) {
          refsById.put(r.getObjectId(), r);
        }
      }
    }
    return refsById;
  }

  private Map<Change.Key, Change.Id> openChangesByKey() throws OrmException {
    final Map<Change.Key, Change.Id> r = new HashMap<Change.Key, Change.Id>();
    for (Change c : db.changes().byProjectOpenAll(project.getNameKey())) {
      r.put(c.getKey(), c.getId());
    }
    return r;
  }

  private void markChangeMergedByPush(final ReviewDb db,
      final ReplaceResult result) throws OrmException {
    final Change change = result.change;
    final String mergedIntoRef = result.mergedIntoRef;

    change.setCurrentPatchSet(result.info);
    change.setStatus(Change.Status.MERGED);
    ChangeUtil.updated(change);

    final List<PatchSetApproval> approvals =
        db.patchSetApprovals().byChange(change.getId()).toList();
    for (PatchSetApproval a : approvals) {
      a.cache(change);
    }
    db.patchSetApprovals().update(approvals);

    final StringBuilder msgBuf = new StringBuilder();
    msgBuf.append("Change has been successfully pushed");
    if (!mergedIntoRef.equals(change.getDest().get())) {
      msgBuf.append(" into ");
      if (mergedIntoRef.startsWith(Constants.R_HEADS)) {
        msgBuf.append("branch ");
        msgBuf.append(repo.shortenRefName(mergedIntoRef));
      } else {
        msgBuf.append(mergedIntoRef);
      }
    }
    msgBuf.append(".");
    final ChangeMessage msg =
        new ChangeMessage(new ChangeMessage.Key(change.getId(), ChangeUtil
            .messageUUID(db)), currentUser.getAccountId());
    msg.setMessage(msgBuf.toString());

    db.changeMessages().insert(Collections.singleton(msg));

    db.changes().atomicUpdate(change.getId(), new AtomicUpdate<Change>() {
      @Override
      public Change update(Change change) {
        if (change.getStatus().isOpen()) {
          change.setCurrentPatchSet(result.info);
          change.setStatus(Change.Status.MERGED);
          ChangeUtil.updated(change);
        }
        return change;
      }
    });
  }

  private void sendMergedEmail(final ReplaceResult result) {
    if (result != null && result.mergedIntoRef != null) {
      try {
        final MergedSender cm = mergedSenderFactory.create(result.change);
        cm.setFrom(currentUser.getAccountId());
        cm.setReviewDb(db);
        cm.setPatchSet(result.patchSet, result.info);
        cm.setDest(new Branch.NameKey(project.getNameKey(),
            result.mergedIntoRef));
        cm.send();
      } catch (EmailException e) {
        final PatchSet.Id psi = result.patchSet.getId();
        log.error("Cannot send email for submitted patch set " + psi, e);
      }
    }
  }

  private void insertAncestors(PatchSet.Id id, RevCommit src)
      throws OrmException {
    final int cnt = src.getParentCount();
    List<PatchSetAncestor> toInsert = new ArrayList<PatchSetAncestor>(cnt);
    for (int p = 0; p < cnt; p++) {
      PatchSetAncestor a;

      a = new PatchSetAncestor(new PatchSetAncestor.Id(id, p + 1));
      a.setAncestorRevision(toRevId(src.getParent(p)));
      toInsert.add(a);
    }
    db.patchSetAncestors().insert(toInsert);
  }

  private static RevId toRevId(final RevCommit src) {
    return new RevId(src.getId().name());
  }

  private boolean canPerform(final ApprovalCategory.Id actionId, final short val) {
    return projectControl.canPerform(actionId, val);
  }

  private static void reject(final ReceiveCommand cmd) {
    reject(cmd, "prohibited by Gerrit");
  }

  private static void reject(final ReceiveCommand cmd, final String why) {
    cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, why);
  }

  private static boolean isTag(final Ref ref) {
    return ref.getName().startsWith(Constants.R_TAGS);
  }

  private static boolean isTag(final ReceiveCommand cmd) {
    return cmd.getRefName().startsWith(Constants.R_TAGS);
  }

  private static boolean isHead(final Ref ref) {
    return ref.getName().startsWith(Constants.R_HEADS);
  }

  private static boolean isHead(final ReceiveCommand cmd) {
    return cmd.getRefName().startsWith(Constants.R_HEADS);
  }
}
