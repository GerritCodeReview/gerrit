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

package com.google.gerrit.server.git;

import static com.google.gerrit.reviewdb.client.Change.INITIAL_PATCH_SET_ID;
import static com.google.gerrit.server.git.MultiProgressMonitor.UNKNOWN;
import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromApprovals;
import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromFooters;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.OK;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_MISSING_OBJECT;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_NONFASTFORWARD;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.common.ChangeHookRunner.HookResult;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.mail.CreateChangeSender;
import com.google.gerrit.server.mail.MailUtil.MailRecipients;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.util.MagicBranch;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.BatchRefUpdate;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.AdvertiseRefsHook;
import org.eclipse.jgit.transport.AdvertiseRefsHookChain;
import org.eclipse.jgit.transport.BaseReceivePack;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.Nullable;

/** Receives change upload using the Git receive-pack protocol. */
public class ReceiveCommits {
  private static final Logger log =
      LoggerFactory.getLogger(ReceiveCommits.class);

  private static final Pattern NEW_PATCHSET =
      Pattern.compile("^refs/changes/(?:[0-9][0-9]/)?([1-9][0-9]*)(?:/new)?$");

  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  private static final String COMMAND_REJECTION_MESSAGE_FOOTER =
      "Please read the documentation and contact an administrator\n"
          + "if you feel the configuration is incorrect";

  private enum Error {
        CONFIG_UPDATE("You are not allowed to perform this operation.\n"
        + "Configuration changes can only be pushed by project owners\n"
        + "who also have 'Push' rights on " + GitRepositoryManager.REF_CONFIG),
        UPDATE("You are not allowed to perform this operation.\n"
        + "To push into this reference you need 'Push' rights."),
        DELETE("You need 'Push' rights with the 'Force Push'\n"
            + "flag set to delete references."),
        DELETE_CHANGES("Cannot delete from 'refs/changes'"),
        CODE_REVIEW("You need 'Push' rights to upload code review requests.\n"
            + "Verify that you are pushing to the right branch.");

    private final String value;

    Error(String value) {
      this.value = value;
    }

    public String get() {
      return value;
    }
  }

  interface Factory {
    ReceiveCommits create(ProjectControl projectControl, Repository repository);
  }

  public interface MessageSender {
    void sendMessage(String what);
    void sendError(String what);
    void sendBytes(byte[] what);
    void sendBytes(byte[] what, int off, int len);
    void flush();
  }

  private class ReceivePackMessageSender implements MessageSender {
    @Override
    public void sendMessage(String what) {
      rp.sendMessage(what);
    }

    @Override
    public void sendError(String what) {
      rp.sendError(what);
    }

    @Override
    public void sendBytes(byte[] what) {
      sendBytes(what, 0, what.length);
    }

    @Override
    public void sendBytes(byte[] what, int off, int len) {
      try {
        rp.getMessageOutputStream().write(what, off, len);
      } catch (IOException e) {
        // Ignore write failures (matching JGit behavior).
      }
    }

    @Override
    public void flush() {
      try {
        rp.getMessageOutputStream().flush();
      } catch (IOException e) {
        // Ignore write failures (matching JGit behavior).
      }
    }
  }

  private static final Function<Exception, OrmException> ORM_EXCEPTION =
      new Function<Exception, OrmException>() {
        @Override
        public OrmException apply(Exception input) {
          if (input instanceof OrmException) {
            return (OrmException) input;
          }
          return new OrmException("Error updating database", input);
        }
      };

  private Set<Account.Id> reviewersFromCommandLine = Sets.newLinkedHashSet();
  private Set<Account.Id> ccFromCommandLine = Sets.newLinkedHashSet();

  private final IdentifiedUser currentUser;
  private final ReviewDb db;
  private final SchemaFactory<ReviewDb> schemaFactory;
  private final AccountResolver accountResolver;
  private final CmdLineParser.Factory optionParserFactory;
  private final CreateChangeSender.Factory createChangeSenderFactory;
  private final MergedSender.Factory mergedSenderFactory;
  private final ReplacePatchSetSender.Factory replacePatchSetFactory;
  private final GitReferenceUpdated gitRefUpdated;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final ChangeHooks hooks;
  private final ApprovalsUtil approvalsUtil;
  private final GitRepositoryManager repoManager;
  private final ProjectCache projectCache;
  private final String canonicalWebUrl;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final TrackingFooters trackingFooters;
  private final TagCache tagCache;
  private final ChangeInserter changeInserter;
  private final WorkQueue workQueue;
  private final ListeningExecutorService changeUpdateExector;
  private final RequestScopePropagator requestScopePropagator;
  private final SshInfo sshInfo;
  private final AllProjectsName allProjectsName;
  private final ReceiveConfig receiveConfig;

  private final ProjectControl projectControl;
  private final Project project;
  private final LabelTypes labelTypes;
  private final Repository repo;
  private final ReceivePack rp;
  private final NoteMap rejectCommits;
  private MagicBranchInput magicBranch;

  private List<CreateRequest> newChanges = Collections.emptyList();
  private final Map<Change.Id, ReplaceRequest> replaceByChange =
      new HashMap<Change.Id, ReplaceRequest>();
  private final Map<RevCommit, ReplaceRequest> replaceByCommit =
      new HashMap<RevCommit, ReplaceRequest>();

  private Map<ObjectId, Ref> refsById;
  private Map<String, Ref> allRefs;

  private final SubmoduleOp.Factory subOpFactory;

  private final List<CommitValidationMessage> messages = new ArrayList<CommitValidationMessage>();
  private ListMultimap<Error, String> errors = LinkedListMultimap.create();
  private Task newProgress;
  private Task replaceProgress;
  private Task closeProgress;
  private Task commandProgress;
  private MessageSender messageSender;
  private BatchRefUpdate batch;

  @Inject
  ReceiveCommits(final ReviewDb db,
      final SchemaFactory<ReviewDb> schemaFactory,
      final AccountResolver accountResolver,
      final CmdLineParser.Factory optionParserFactory,
      final CreateChangeSender.Factory createChangeSenderFactory,
      final MergedSender.Factory mergedSenderFactory,
      final ReplacePatchSetSender.Factory replacePatchSetFactory,
      final GitReferenceUpdated gitRefUpdated,
      final PatchSetInfoFactory patchSetInfoFactory,
      final ChangeHooks hooks,
      final ApprovalsUtil approvalsUtil,
      final ProjectCache projectCache,
      final GitRepositoryManager repoManager,
      final TagCache tagCache,
      final ChangeCache changeCache,
      final ChangeInserter changeInserter,
      final CommitValidators.Factory commitValidatorsFactory,
      @CanonicalWebUrl @Nullable final String canonicalWebUrl,
      @GerritPersonIdent final PersonIdent gerritIdent,
      final TrackingFooters trackingFooters,
      final WorkQueue workQueue,
      @ChangeUpdateExecutor ListeningExecutorService changeUpdateExector,
      final RequestScopePropagator requestScopePropagator,
      final SshInfo sshInfo,
      final AllProjectsName allProjectsName,
      ReceiveConfig config,
      @Assisted final ProjectControl projectControl,
      @Assisted final Repository repo,
      final SubmoduleOp.Factory subOpFactory) throws IOException {
    this.currentUser = (IdentifiedUser) projectControl.getCurrentUser();
    this.db = db;
    this.schemaFactory = schemaFactory;
    this.accountResolver = accountResolver;
    this.optionParserFactory = optionParserFactory;
    this.createChangeSenderFactory = createChangeSenderFactory;
    this.mergedSenderFactory = mergedSenderFactory;
    this.replacePatchSetFactory = replacePatchSetFactory;
    this.gitRefUpdated = gitRefUpdated;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.hooks = hooks;
    this.approvalsUtil = approvalsUtil;
    this.projectCache = projectCache;
    this.repoManager = repoManager;
    this.canonicalWebUrl = canonicalWebUrl;
    this.trackingFooters = trackingFooters;
    this.tagCache = tagCache;
    this.changeInserter = changeInserter;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.workQueue = workQueue;
    this.changeUpdateExector = changeUpdateExector;
    this.requestScopePropagator = requestScopePropagator;
    this.sshInfo = sshInfo;
    this.allProjectsName = allProjectsName;
    this.receiveConfig = config;

    this.projectControl = projectControl;
    this.labelTypes = projectControl.getLabelTypes();
    this.project = projectControl.getProject();
    this.repo = repo;
    this.rp = new ReceivePack(repo);
    this.rejectCommits = loadRejectCommitsMap();

    this.subOpFactory = subOpFactory;

    this.messageSender = new ReceivePackMessageSender();

    rp.setAllowCreates(true);
    rp.setAllowDeletes(true);
    rp.setAllowNonFastForwards(true);
    rp.setCheckReceivedObjects(true);

    if (!projectControl.allRefsAreVisible()) {
      rp.setCheckReferencedObjectsAreReachable(config.checkReferencedObjectsAreReachable);
      rp.setAdvertiseRefsHook(new VisibleRefFilter(tagCache, changeCache, repo, projectControl, db, false));
    }
    List<AdvertiseRefsHook> advHooks = new ArrayList<AdvertiseRefsHook>(3);
    advHooks.add(new AdvertiseRefsHook() {
      @Override
      public void advertiseRefs(BaseReceivePack rp) {
        allRefs = rp.getAdvertisedRefs();
        if (allRefs == null) {
          allRefs = rp.getRepository().getAllRefs();
        }
        rp.setAdvertisedRefs(allRefs, rp.getAdvertisedObjects());
      }

      @Override
      public void advertiseRefs(UploadPack uploadPack) {
      }
    });
    advHooks.add(rp.getAdvertiseRefsHook());
    advHooks.add(new ReceiveCommitsAdvertiseRefsHook());
    rp.setAdvertiseRefsHook(AdvertiseRefsHookChain.newChain(advHooks));
  }

  /** Add reviewers for new (or updated) changes. */
  public void addReviewers(Collection<Account.Id> who) {
    reviewersFromCommandLine.addAll(who);
  }

  /** Add reviewers for new (or updated) changes. */
  public void addExtraCC(Collection<Account.Id> who) {
    ccFromCommandLine.addAll(who);
  }

  /** Set a message sender for this operation. */
  public void setMessageSender(final MessageSender ms) {
    messageSender = ms != null ? ms : new ReceivePackMessageSender();
  }

  MessageSender getMessageSender() {
    if (messageSender == null) {
      setMessageSender(null);
    }
    return messageSender;
  }

  Project getProject() {
    return project;
  }

  /** @return the ReceivePack instance to speak the native Git protocol. */
  public ReceivePack getReceivePack() {
    return rp;
  }

  /** Scan part of history and include it in the advertisement. */
  public void advertiseHistory() {
    Set<ObjectId> toInclude = new HashSet<ObjectId>();

    // Advertise some recent open changes, in case a commit is based one.
    try {
      Set<PatchSet.Id> toGet = new HashSet<PatchSet.Id>();
      for (Change change : db.changes()
          .byProjectOpenNext(project.getNameKey(), "z", 32)) {
        PatchSet.Id id = change.currentPatchSetId();
        if (id != null) {
          toGet.add(id);
        }
      }
      for (PatchSet ps : db.patchSets().get(toGet)) {
        if (ps.getRevision() != null && ps.getRevision().get() != null) {
          toInclude.add(ObjectId.fromString(ps.getRevision().get()));
        }
      }
    } catch (OrmException err) {
      log.error("Cannot list open changes of " + project.getNameKey(), err);
    }

    // Size of an additional ".have" line.
    final int haveLineLen = 4 + Constants.OBJECT_ID_STRING_LENGTH + 1 + 5 + 1;

    // Maximum number of bytes to "waste" in the advertisement with
    // a peek at this repository's current reachable history.
    final int maxExtraSize = 8192;

    // Number of recent commits to advertise immediately, hoping to
    // show a client a nearby merge base.
    final int base = 64;

    // Number of commits to skip once base has already been shown.
    final int step = 16;

    // Total number of commits to extract from the history.
    final int max = maxExtraSize / haveLineLen;

    // Scan history until the advertisement is full.
    Set<ObjectId> alreadySending = rp.getAdvertisedObjects();
    RevWalk rw = rp.getRevWalk();
    for (ObjectId haveId : alreadySending) {
      try {
        rw.markStart(rw.parseCommit(haveId));
      } catch (IOException badCommit) {
        continue;
      }
    }

    int stepCnt = 0;
    RevCommit c;
    try {
      while ((c = rw.next()) != null && toInclude.size() < max) {
        if (alreadySending.contains(c)) {
        } else if (toInclude.contains(c)) {
        } else if (c.getParentCount() > 1) {
        } else if (toInclude.size() < base) {
          toInclude.add(c);
        } else {
          stepCnt = ++stepCnt % step;
          if (stepCnt == 0) {
            toInclude.add(c);
          }
        }
      }
    } catch (IOException err) {
      log.error("Error trying to advertise history on " + project.getNameKey(), err);
    }
    rw.reset();
    rp.getAdvertisedObjects().addAll(toInclude);
  }

  /** Determine if the user can upload commits. */
  public Capable canUpload() {
    Capable result = projectControl.canPushToAtLeastOneRef();
    if (result != Capable.OK) {
      return result;
    }
    if (receiveConfig.checkMagicRefs) {
      result = MagicBranch.checkMagicBranchRefs(repo, project);
    }
    return result;
  }

  private void addMessage(String message) {
    messages.add(new CommitValidationMessage(message, false));
  }

  void addError(String error) {
    messages.add(new CommitValidationMessage(error, true));
  }

  void sendMessages() {
    for (CommitValidationMessage m : messages) {
      if (m.isError()) {
        messageSender.sendError(m.getMessage());
      } else {
        messageSender.sendMessage(m.getMessage());
      }
    }
  }

  void processCommands(final Collection<ReceiveCommand> commands,
      final MultiProgressMonitor progress) {
    newProgress = progress.beginSubTask("new", UNKNOWN);
    replaceProgress = progress.beginSubTask("updated", UNKNOWN);
    closeProgress = progress.beginSubTask("closed", UNKNOWN);
    commandProgress = progress.beginSubTask("refs", UNKNOWN);

    batch = repo.getRefDatabase().newBatchUpdate();
    batch.setRefLogIdent(rp.getRefLogIdent());
    batch.setRefLogMessage("push", true);

    parseCommands(commands);
    if (magicBranch != null && magicBranch.cmd.getResult() == NOT_ATTEMPTED) {
      newChanges = selectNewChanges();
    }
    preparePatchSetsForReplace();

    if (!batch.getCommands().isEmpty()) {
      try {
        batch.execute(rp.getRevWalk(), commandProgress);
      } catch (IOException err) {
        int cnt = 0;
        for (ReceiveCommand cmd : batch.getCommands()) {
          if (cmd.getResult() == NOT_ATTEMPTED) {
            cmd.setResult(REJECTED_OTHER_REASON, "internal server error");
            cnt++;
          }
        }
        log.error(String.format(
            "Failed to store %d refs in %s", cnt, project.getName()), err);
      }
    }

    insertChangesAndPatchSets();
    newProgress.end();
    replaceProgress.end();

    if (!errors.isEmpty()) {
      for (Error error : errors.keySet()) {
        rp.sendMessage(buildError(error, errors.get(error)));
      }
      rp.sendMessage(String.format("User: %s", displayName(currentUser)));
      rp.sendMessage(COMMAND_REJECTION_MESSAGE_FOOTER);
    }

    for (final ReceiveCommand c : commands) {
      if (c.getResult() == OK) {
        switch (c.getType()) {
          case CREATE:
            if (isHead(c)) {
              autoCloseChanges(c);
            }
            break;

          case UPDATE: // otherwise known as a fast-forward
            tagCache.updateFastForward(project.getNameKey(),
                c.getRefName(),
                c.getOldId(),
                c.getNewId());
            if (isHead(c)) {
              autoCloseChanges(c);
            }
            break;

          case UPDATE_NONFASTFORWARD:
            if (isHead(c)) {
              autoCloseChanges(c);
            }
            break;

          case DELETE:
            break;
        }

        if (isConfig(c)) {
          projectCache.evict(project);
          ProjectState ps = projectCache.get(project.getNameKey());
          repoManager.setProjectDescription(project.getNameKey(), //
              ps.getProject().getDescription());
        }

        if (!MagicBranch.isMagicBranch(c.getRefName())) {
          // We only fire gitRefUpdated for direct refs updates.
          // Events for change refs are fired when they are created.
          //
          gitRefUpdated.fire(project.getNameKey(), c.getRefName(),
              c.getOldId(), c.getNewId());
          hooks.doRefUpdatedHook(
              new Branch.NameKey(project.getNameKey(), c.getRefName()),
              c.getOldId(),
              c.getNewId(),
              currentUser.getAccount());
        }
      }
    }
    closeProgress.end();
    commandProgress.end();
    progress.end();

    Iterable<CreateRequest> created =
        Iterables.filter(newChanges, new Predicate<CreateRequest>() {
          @Override
          public boolean apply(CreateRequest input) {
            return input.created;
          }
        });
    if (!Iterables.isEmpty(created) && canonicalWebUrl != null) {
      final String url = canonicalWebUrl;
      addMessage("");
      addMessage("New Changes:");
      for (CreateRequest c : created) {
        StringBuilder m = new StringBuilder()
            .append("  ")
            .append(url)
            .append(c.change.getChangeId());
        if (c.change.getStatus() == Change.Status.DRAFT) {
          m.append(" [DRAFT]");
        }
        addMessage(m.toString());
      }
      addMessage("");
    }
  }

  private void insertChangesAndPatchSets() {
    int replaceCount = 0;
    int okToInsert = 0;

    for (Map.Entry<Change.Id, ReplaceRequest> e : replaceByChange.entrySet()) {
      ReplaceRequest replace = e.getValue();
      if (magicBranch != null && replace.inputCommand == magicBranch.cmd) {
        replaceCount++;

        if (replace.cmd != null && replace.cmd.getResult() == OK) {
          okToInsert++;
        }
      } else if (replace.cmd != null && replace.cmd.getResult() == OK) {
        try {
          if (replace.insertPatchSet().checkedGet() != null) {
            replace.inputCommand.setResult(OK);
          }
        } catch (IOException err) {
          reject(replace.inputCommand, "internal server error");
          log.error(String.format(
              "Cannot add patch set to %d of %s",
              e.getKey().get(), project.getName()), err);
        } catch (OrmException err) {
          reject(replace.inputCommand, "internal server error");
          log.error(String.format(
              "Cannot add patch set to %d of %s",
              e.getKey().get(), project.getName()), err);
        }
      } else if (replace.inputCommand.getResult() == NOT_ATTEMPTED) {
        reject(replace.inputCommand, "internal server error");
      }
    }

    if (magicBranch == null || magicBranch.cmd.getResult() != NOT_ATTEMPTED) {
      // refs/for/ or refs/drafts/ not used, or it already failed earlier.
      // No need to continue.
      return;
    }

    for (CreateRequest create : newChanges) {
      if (create.cmd.getResult() == OK) {
        okToInsert++;
      }
    }

    if (okToInsert != replaceCount + newChanges.size()) {
      // One or more new references failed to create. Assume the
      // system isn't working correctly anymore and abort.
      reject(magicBranch.cmd, "internal server error");
      log.error(String.format(
          "Only %d of %d new change refs created in %s; aborting",
          okToInsert, replaceCount + newChanges.size(), project.getName()));
      return;
    }

    try {
      List<CheckedFuture<?, OrmException>> futures = Lists.newArrayList();
      for (ReplaceRequest replace : replaceByChange.values()) {
        if (magicBranch != null && replace.inputCommand == magicBranch.cmd) {
          futures.add(replace.insertPatchSet());
        }
      }

      for (CreateRequest create : newChanges) {
        futures.add(create.insertChange());
      }

      for (CheckedFuture<?, OrmException> f : futures) {
        f.checkedGet();
      }
      magicBranch.cmd.setResult(OK);
    } catch (OrmException err) {
      log.error("Can't insert changes for " + project.getName(), err);
      reject(magicBranch.cmd, "internal server error");
    } catch (IOException err) {
      log.error("Can't read commits for " + project.getName(), err);
      reject(magicBranch.cmd, "internal server error");
    }
  }

  private String buildError(Error error, List<String> branches) {
    StringBuilder sb = new StringBuilder();
    if (branches.size() == 1) {
      sb.append("Branch ").append(branches.get(0)).append(":\n");
      sb.append(error.get());
      return sb.toString();
    }
    sb.append("Branches");
    String delim = " ";
    for (String branch : branches) {
      sb.append(delim).append(branch);
      delim = ", ";
    }
    return sb.append(":\n").append(error.get()).toString();
  }

  private static String displayName(IdentifiedUser user) {
    String displayName = user.getUserName();
    if (displayName == null) {
      displayName = user.getAccount().getPreferredEmail();
    }
    return displayName;
  }

  private void parseCommands(final Collection<ReceiveCommand> commands) {
    for (final ReceiveCommand cmd : commands) {
      if (cmd.getResult() != NOT_ATTEMPTED) {
        // Already rejected by the core receive process.
        //
        continue;
      }

      if (!Repository.isValidRefName(cmd.getRefName())
          || cmd.getRefName().contains("//")) {
        reject(cmd, "not valid ref");
        continue;
      }

      HookResult result = hooks.doRefUpdateHook(project, cmd.getRefName(),
                              currentUser.getAccount(), cmd.getOldId(),
                              cmd.getNewId());

      if (result != null) {
        final String message = result.toString().trim();
        if (result.getExitValue() != 0) {
          reject(cmd, message);
          continue;
        }
        rp.sendMessage(message);
      }

      if (MagicBranch.isMagicBranch(cmd.getRefName())) {
        parseMagicBranch(cmd);
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
          break;

        case UPDATE:
          parseUpdate(cmd);
          break;

        case DELETE:
          parseDelete(cmd);
          break;

        case UPDATE_NONFASTFORWARD:
          parseRewind(cmd);
          break;

        default:
          reject(cmd);
          continue;
      }

      if (cmd.getResult() != NOT_ATTEMPTED) {
        continue;
      }

      if (isConfig(cmd)) {
        if (!projectControl.isOwner()) {
          reject(cmd, "not project owner");
          continue;
        }

        switch (cmd.getType()) {
          case CREATE:
          case UPDATE:
          case UPDATE_NONFASTFORWARD:
            try {
              ProjectConfig cfg = new ProjectConfig(project.getNameKey());
              cfg.load(repo, cmd.getNewId());
              if (!cfg.getValidationErrors().isEmpty()) {
                addError("Invalid project configuration:");
                for (ValidationError err : cfg.getValidationErrors()) {
                  addError("  " + err.getMessage());
                }
                reject(cmd, "invalid project configuration");
                log.error("User " + currentUser.getUserName()
                    + " tried to push invalid project configuration "
                    + cmd.getNewId().name() + " for " + project.getName());
                continue;
              }
              Project.NameKey newParent = cfg.getProject().getParent(allProjectsName);
              Project.NameKey oldParent = project.getParent(allProjectsName);
              if (oldParent == null) {
                // update of the 'All-Projects' project
                if (newParent != null) {
                  reject(cmd, "invalid project configuration: root project cannot have parent");
                  continue;
                }
              } else {
                if (!oldParent.equals(newParent)
                    && !currentUser.getCapabilities().canAdministrateServer()) {
                  reject(cmd, "invalid project configuration: only Gerrit admin can set parent");
                  continue;
                }

                if (projectCache.get(newParent) == null) {
                  reject(cmd, "invalid project configuration: parent does not exist");
                  continue;
                }
              }
            } catch (Exception e) {
              reject(cmd, "invalid project configuration");
              log.error("User " + currentUser.getUserName()
                  + " tried to push invalid project configuration "
                  + cmd.getNewId().name() + " for " + project.getName(), e);
              continue;
            }
            break;

          case DELETE:
            break;

          default:
            reject(cmd);
            continue;
        }
      }
    }
  }

  private void parseCreate(final ReceiveCommand cmd) {
    RevObject obj;
    try {
      obj = rp.getRevWalk().parseAny(cmd.getNewId());
    } catch (IOException err) {
      log.error("Invalid object " + cmd.getNewId().name() + " for "
          + cmd.getRefName() + " creation", err);
      reject(cmd, "invalid object");
      return;
    }

    if (isHead(cmd) && !isCommit(cmd)) {
      return;
    }

    RefControl ctl = projectControl.controlForRef(cmd.getRefName());
    if (ctl.canCreate(rp.getRevWalk(), obj)) {
      validateNewCommits(ctl, cmd);
      batch.addCommand(cmd);
    } else {
      reject(cmd);
    }
  }

  private void parseUpdate(final ReceiveCommand cmd) {
    RefControl ctl = projectControl.controlForRef(cmd.getRefName());
    if (ctl.canUpdate()) {
      if (isHead(cmd) && !isCommit(cmd)) {
        return;
      }

      validateNewCommits(ctl, cmd);
      batch.addCommand(cmd);
    } else {
      if (GitRepositoryManager.REF_CONFIG.equals(ctl.getRefName())) {
        errors.put(Error.CONFIG_UPDATE, GitRepositoryManager.REF_CONFIG);
      } else {
        errors.put(Error.UPDATE, ctl.getRefName());
      }
      reject(cmd);
    }
  }

  private boolean isCommit(final ReceiveCommand cmd) {
    RevObject obj;
    try {
      obj = rp.getRevWalk().parseAny(cmd.getNewId());
    } catch (IOException err) {
      log.error("Invalid object " + cmd.getNewId().name() + " for "
          + cmd.getRefName(), err);
      reject(cmd, "invalid object");
      return false;
    }

    if (obj instanceof RevCommit) {
      return true;
    } else {
      reject(cmd, "not a commit");
      return false;
    }
  }

  private void parseDelete(final ReceiveCommand cmd) {
    RefControl ctl = projectControl.controlForRef(cmd.getRefName());
    if (ctl.getRefName().startsWith("refs/changes/")) {
      errors.put(Error.DELETE_CHANGES, ctl.getRefName());
      reject(cmd, "cannot delete changes");
    } else if (ctl.canDelete()) {
      batch.addCommand(cmd);
    } else {
      if (GitRepositoryManager.REF_CONFIG.equals(ctl.getRefName())) {
        reject(cmd, "cannot delete project configuration");
      } else {
        errors.put(Error.DELETE, ctl.getRefName());
        reject(cmd, "cannot delete references");
      }
    }
  }

  private void parseRewind(final ReceiveCommand cmd) {
    RevCommit newObject;
    try {
      newObject = rp.getRevWalk().parseCommit(cmd.getNewId());
    } catch (IncorrectObjectTypeException notCommit) {
      newObject = null;
    } catch (IOException err) {
      log.error("Invalid object " + cmd.getNewId().name() + " for "
          + cmd.getRefName() + " forced update", err);
      reject(cmd, "invalid object");
      return;
    }

    RefControl ctl = projectControl.controlForRef(cmd.getRefName());
    if (newObject != null) {
      validateNewCommits(ctl, cmd);
      if (cmd.getResult() != NOT_ATTEMPTED) {
        return;
      }
    }

    if (ctl.canForceUpdate()) {
      batch.setAllowNonFastForwards(true).addCommand(cmd);
    } else {
      cmd.setResult(REJECTED_NONFASTFORWARD, " need '"
          + PermissionRule.FORCE_PUSH + "' privilege.");
    }
  }

  private static class MagicBranchInput {
    private static final Splitter COMMAS = Splitter.on(',').omitEmptyStrings();

    final ReceiveCommand cmd;
    Branch.NameKey dest;
    RefControl ctl;
    Set<Account.Id> reviewer = Sets.newLinkedHashSet();
    Set<Account.Id> cc = Sets.newLinkedHashSet();

    @Option(name = "--topic", metaVar = "NAME", usage = "attach topic to changes")
    String topic;

    @Option(name = "--draft", usage = "mark new/update changes as draft")
    boolean draft;

    @Option(name = "-r", metaVar = "EMAIL", usage = "add reviewer to changes")
    void reviewer(Account.Id id) {
      reviewer.add(id);
    }

    @Option(name = "--cc", metaVar = "EMAIL", usage = "notify user by CC")
    void cc(Account.Id id) {
      cc.add(id);
    }

    @Option(name = "--publish", usage = "publish new/updated changes")
    void publish(boolean publish) {
      draft = !publish;
    }

    MagicBranchInput(ReceiveCommand cmd) {
      this.cmd = cmd;
      this.draft = cmd.getRefName().startsWith(MagicBranch.NEW_DRAFT_CHANGE);
    }

    boolean isDraft() {
      return draft;
    }

    MailRecipients getMailRecipients() {
      return new MailRecipients(reviewer, cc);
    }

    String parse(CmdLineParser clp, Repository repo, Set<String> refs)
        throws CmdLineException {
      String ref = MagicBranch.getDestBranchName(cmd.getRefName());
      if (!ref.startsWith(Constants.R_REFS)) {
        ref = Constants.R_HEADS + ref;
      }

      int optionStart = ref.indexOf('%');
      if (0 < optionStart) {
        ListMultimap<String, String> options = LinkedListMultimap.create();
        for (String s : COMMAS.split(ref.substring(optionStart + 1))) {
          int e = s.indexOf('=');
          if (0 < e) {
            options.put(s.substring(0, e), s.substring(e + 1));
          } else {
            options.put(s, "");
          }
        }
        clp.parseOptionMap(options);
        ref = ref.substring(0, optionStart);
      }

      // Split the destination branch by branch and topic. The topic
      // suffix is entirely optional, so it might not even exist.
      String head = readHEAD(repo);
      int split = ref.length();
      for (;;) {
        String name = ref.substring(0, split);
        if (refs.contains(name) || name.equals(head)) {
          break;
        }

        split = name.lastIndexOf('/', split - 1);
        if (split <= Constants.R_REFS.length()) {
          return ref;
        }
      }
      if (split < ref.length()) {
        topic = Strings.emptyToNull(ref.substring(split + 1));
      }
      return ref.substring(0, split);
    }
  }

  private void parseMagicBranch(final ReceiveCommand cmd) {
    // Permit exactly one new change request per push.
    if (magicBranch != null) {
      reject(cmd, "duplicate request");
      return;
    }

    magicBranch = new MagicBranchInput(cmd);
    magicBranch.reviewer.addAll(reviewersFromCommandLine);
    magicBranch.cc.addAll(ccFromCommandLine);

    String ref;
    CmdLineParser clp = optionParserFactory.create(magicBranch);
    try {
      ref = magicBranch.parse(clp, repo, rp.getAdvertisedRefs().keySet());
    } catch (CmdLineException e) {
      if (!clp.wasHelpRequestedByOption()) {
        reject(cmd, e.getMessage());
        return;
      }
      ref = null; // never happen
    }
    if (clp.wasHelpRequestedByOption()) {
      StringWriter w = new StringWriter();
      w.write("\nHelp for refs/for/branch:\n\n");
      clp.printUsage(w, null);
      addMessage(w.toString());
      reject(cmd, "see help");
      return;
    }
    if (!rp.getAdvertisedRefs().containsKey(ref) && !ref.equals(readHEAD(repo))) {
      if (ref.startsWith(Constants.R_HEADS)) {
        String n = ref.substring(Constants.R_HEADS.length());
        reject(cmd, "branch " + n + " not found");
      } else {
        reject(cmd, ref + " not found");
      }
      return;
    }

    magicBranch.dest = new Branch.NameKey(project.getNameKey(), ref);
    magicBranch.ctl = projectControl.controlForRef(ref);
    if (!magicBranch.ctl.canUpload()) {
      errors.put(Error.CODE_REVIEW, ref);
      reject(cmd, "cannot upload review");
      return;
    }

    // Validate that the new commits are connected with the target
    // branch.  If they aren't, we want to abort. We do this check by
    // looking to see if we can compute a merge base between the new
    // commits and the target branch head.
    //
    try {
      final RevWalk walk = rp.getRevWalk();
      final RevCommit tip = walk.parseCommit(magicBranch.cmd.getNewId());
      Ref targetRef = rp.getAdvertisedRefs().get(magicBranch.ctl.getRefName());
      if (targetRef == null || targetRef.getObjectId() == null) {
        // The destination branch does not yet exist. Assume the
        // history being sent for review will start it and thus
        // is "connected" to the branch.
        return;
      }
      final RevCommit h = walk.parseCommit(targetRef.getObjectId());
      final RevFilter oldRevFilter = walk.getRevFilter();
      try {
        walk.reset();
        walk.setRevFilter(RevFilter.MERGE_BASE);
        walk.markStart(tip);
        walk.markStart(h);
        if (walk.next() == null) {
          reject(magicBranch.cmd, "no common ancestry");
          return;
        }
      } finally {
        walk.reset();
        walk.setRevFilter(oldRevFilter);
      }
    } catch (IOException e) {
      magicBranch.cmd.setResult(REJECTED_MISSING_OBJECT);
      log.error("Invalid pack upload; one or more objects weren't sent", e);
      return;
    }
  }

  private static String readHEAD(Repository repo) {
    try {
      return repo.getFullBranch();
    } catch (IOException e) {
      log.error("Cannot read HEAD symref", e);
      return null;
    }
  }

  /**
   * Loads a list of commits to reject from {@code refs/meta/reject-commits}.
   *
   * @return NoteMap of commits to be rejected, null if there are none.
   * @throws IOException the map cannot be loaded.
   */
  private NoteMap loadRejectCommitsMap() throws IOException {
    try {
      Ref ref = repo.getRef(GitRepositoryManager.REF_REJECT_COMMITS);
      if (ref == null) {
        return NoteMap.newEmptyMap();
      }

      RevWalk rw = rp.getRevWalk();
      RevCommit map = rw.parseCommit(ref.getObjectId());
      return NoteMap.read(rw.getObjectReader(), map);
    } catch (IOException badMap) {
      throw new IOException("Cannot load "
          + GitRepositoryManager.REF_REJECT_COMMITS, badMap);
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
      reject(cmd, "change " + changeId + " does not belong to project " + project.getName());
      return;
    }

    requestReplace(cmd, true, changeEnt, newCommit);
  }

  private boolean requestReplace(final ReceiveCommand cmd,
      final boolean checkMergedInto, final Change change,
      final RevCommit newCommit) {
    if (change.getStatus().isClosed()) {
      reject(cmd, "change " + change.getId() + " closed");
      return false;
    }

    final ReplaceRequest req =
        new ReplaceRequest(change.getId(), newCommit, cmd, checkMergedInto);
    if (replaceByChange.containsKey(req.ontoChange)) {
      reject(cmd, "duplicate request");
      return false;
    }
    if (replaceByCommit.containsKey(req.newCommit)) {
      reject(cmd, "duplicate request");
      return false;
    }
    replaceByChange.put(req.ontoChange, req);
    replaceByCommit.put(req.newCommit, req);
    return true;
  }

  private List<CreateRequest> selectNewChanges() {
    final List<CreateRequest> newChanges = Lists.newArrayList();
    final RevWalk walk = rp.getRevWalk();
    walk.reset();
    walk.sort(RevSort.TOPO);
    walk.sort(RevSort.REVERSE, true);
    try {
      Set<ObjectId> existing = Sets.newHashSet();
      walk.markStart(walk.parseCommit(magicBranch.cmd.getNewId()));
      markHeadsAsUninteresting(
          walk,
          existing,
          magicBranch.ctl != null ? magicBranch.ctl.getRefName() : null);

      List<ChangeLookup> pending = Lists.newArrayList();
      final Set<Change.Key> newChangeIds = new HashSet<Change.Key>();
      for (;;) {
        final RevCommit c = walk.next();
        if (c == null) {
          break;
        }
        if (existing.contains(c) || replaceByCommit.containsKey(c)) {
          // This commit was already scheduled to replace an existing PatchSet.
          //
          continue;
        }

        if (!validCommit(magicBranch.ctl, magicBranch.cmd, c)) {
          // Not a change the user can propose? Abort as early as possible.
          //
          return Collections.emptyList();
        }

        Change.Key changeKey = new Change.Key("I" + c.name());
        final List<String> idList = c.getFooterLines(CHANGE_ID);
        if (idList.isEmpty()) {
          newChanges.add(new CreateRequest(c, changeKey));
          continue;
        }

        final String idStr = idList.get(idList.size() - 1).trim();
        if (idStr.matches("^I00*$")) {
          // Reject this invalid line from EGit.
          reject(magicBranch.cmd, "invalid Change-Id");
          return Collections.emptyList();
        }

        changeKey = new Change.Key(idStr);
        pending.add(new ChangeLookup(c, changeKey));
      }

      for (ChangeLookup p : pending) {
        if (newChangeIds.contains(p.changeKey)) {
          reject(magicBranch.cmd, "squash commits first");
          return Collections.emptyList();
        }

        List<Change> changes = p.changes.toList();
        if (changes.size() > 1) {
          // WTF, multiple changes in this project have the same key?
          // Since the commit is new, the user should recreate it with
          // a different Change-Id. In practice, we should never see
          // this error message as Change-Id should be unique.
          //
          reject(magicBranch.cmd, p.changeKey.get() + " has duplicates");
          return Collections.emptyList();
        }

        if (changes.size() == 1) {
          // Schedule as a replacement to this one matching change.
          //
          if (requestReplace(magicBranch.cmd, false, changes.get(0), p.commit)) {
            continue;
          } else {
            return Collections.emptyList();
          }
        }

        if (changes.size() == 0) {
          if (!isValidChangeId(p.changeKey.get())) {
            reject(magicBranch.cmd, "invalid Change-Id");
            return Collections.emptyList();
          }

          newChangeIds.add(p.changeKey);
        }
        newChanges.add(new CreateRequest(p.commit, p.changeKey));
      }
    } catch (IOException e) {
      // Should never happen, the core receive process would have
      // identified the missing object earlier before we got control.
      //
      magicBranch.cmd.setResult(REJECTED_MISSING_OBJECT);
      log.error("Invalid pack upload; one or more objects weren't sent", e);
      return Collections.emptyList();
    } catch (OrmException e) {
      log.error("Cannot query database to locate prior changes", e);
      reject(magicBranch.cmd, "database error");
      return Collections.emptyList();
    }

    if (newChanges.isEmpty() && replaceByChange.isEmpty()) {
      reject(magicBranch.cmd, "no new changes");
      return Collections.emptyList();
    }
    for (CreateRequest create : newChanges) {
      batch.addCommand(create.cmd);
    }
    return newChanges;
  }

  private void markHeadsAsUninteresting(
      final RevWalk walk,
      Set<ObjectId> existing,
      @Nullable String forRef) {
    for (Ref ref : allRefs.values()) {
      if (ref.getObjectId() == null) {
        continue;
      } else if (ref.getName().startsWith("refs/changes/")) {
        existing.add(ref.getObjectId());
      } else if (ref.getName().startsWith(R_HEADS)
          || (forRef != null && forRef.equals(ref.getName()))) {
        try {
          walk.markUninteresting(walk.parseCommit(ref.getObjectId()));
        } catch (IOException e) {
          log.warn(String.format("Invalid ref %s in %s",
              ref.getName(), project.getName()), e);
          continue;
        }
      }
    }
  }

  private static boolean isValidChangeId(String idStr) {
    return idStr.matches("^I[0-9a-fA-F]{40}$") && !idStr.matches("^I00*$");
  }

  private class ChangeLookup {
    final RevCommit commit;
    final Change.Key changeKey;
    final ResultSet<Change> changes;

    ChangeLookup(RevCommit c, Change.Key key) throws OrmException {
      commit = c;
      changeKey = key;
      changes = db.changes().byBranchKey(magicBranch.dest, key);
    }
  }

  private class CreateRequest {
    final RevCommit commit;
    final Change change;
    final PatchSet ps;
    final ReceiveCommand cmd;
    private final PatchSetInfo info;
    boolean created;

    CreateRequest(RevCommit c, Change.Key changeKey) throws OrmException {
      commit = c;

      change = new Change(changeKey,
          new Change.Id(db.nextChangeId()),
          currentUser.getAccountId(),
          magicBranch.dest);
      change.setTopic(magicBranch.topic);

      ps = new PatchSet(new PatchSet.Id(change.getId(), INITIAL_PATCH_SET_ID));
      ps.setCreatedOn(change.getCreatedOn());
      ps.setUploader(change.getOwner());
      ps.setRevision(toRevId(c));

      if (magicBranch.isDraft()) {
        change.setStatus(Change.Status.DRAFT);
        ps.setDraft(true);
      }

      info = patchSetInfoFactory.get(c, ps.getId());
      change.setCurrentPatchSet(info);
      ChangeUtil.updated(change);
      cmd = new ReceiveCommand(ObjectId.zeroId(), c, ps.getRefName());
    }

    CheckedFuture<Void, OrmException> insertChange() throws IOException {
      rp.getRevWalk().parseBody(commit);

      final Thread caller = Thread.currentThread();
      ListenableFuture<Void> future = changeUpdateExector.submit(
          requestScopePropagator.wrap(new Callable<Void>() {
        @Override
        public Void call() throws OrmException {
          if (caller == Thread.currentThread()) {
            insertChange(db);
          } else {
            ReviewDb db = schemaFactory.open();
            try {
              insertChange(db);
            } finally {
              db.close();
            }
          }
          synchronized (newProgress) {
            newProgress.update(1);
          }
          return null;
        }
      }));
      return Futures.makeChecked(future, ORM_EXCEPTION);
    }

    private void insertChange(ReviewDb db) throws OrmException {
      final Account.Id me = currentUser.getAccountId();
      final List<FooterLine> footerLines = commit.getFooterLines();
      final MailRecipients recipients = new MailRecipients();
      if (magicBranch != null) {
        recipients.add(magicBranch.getMailRecipients());
      }
      recipients.add(getRecipientsFromFooters(accountResolver, ps, footerLines));
      recipients.remove(me);

      changeInserter.insertChange(db, change, ps, commit, labelTypes, info,
          recipients.getReviewers());

      created = true;

      workQueue.getDefaultQueue()
          .submit(requestScopePropagator.wrap(new Runnable() {
        @Override
        public void run() {
          try {
            CreateChangeSender cm =
                createChangeSenderFactory.create(change);
            cm.setFrom(me);
            cm.setPatchSet(ps, info);
            cm.addReviewers(recipients.getReviewers());
            cm.addExtraCC(recipients.getCcOnly());
            cm.send();
          } catch (Exception e) {
            log.error("Cannot send email for new change " + change.getId(), e);
          }
        }

        @Override
        public String toString() {
          return "send-email newchange";
        }
      }));
    }
  }

  private void preparePatchSetsForReplace() {
    try {
      readChangesForReplace();
      readPatchSetsForReplace();
      for (ReplaceRequest req : replaceByChange.values()) {
        if (req.inputCommand.getResult() == NOT_ATTEMPTED) {
          req.validate(false);
        }
      }
    } catch (OrmException err) {
      log.error("Cannot read database before replacement", err);
      for (ReplaceRequest req : replaceByChange.values()) {
        if (req.inputCommand.getResult() == NOT_ATTEMPTED) {
          req.inputCommand.setResult(REJECTED_OTHER_REASON, "internal server error");
        }
      }
    } catch (IOException err) {
      log.error("Cannot read repository before replacement", err);
      for (ReplaceRequest req : replaceByChange.values()) {
        if (req.inputCommand.getResult() == NOT_ATTEMPTED) {
          req.inputCommand.setResult(REJECTED_OTHER_REASON, "internal server error");
        }
      }
    }

    for (ReplaceRequest req : replaceByChange.values()) {
      if (req.inputCommand.getResult() == NOT_ATTEMPTED && req.cmd != null) {
        batch.addCommand(req.cmd);
      }
    }

    if (magicBranch != null && magicBranch.cmd.getResult() != NOT_ATTEMPTED) {
      // Cancel creations tied to refs/for/ or refs/drafts/ command.
      for (ReplaceRequest req : replaceByChange.values()) {
        if (req.inputCommand == magicBranch.cmd && req.cmd != null) {
          req.cmd.setResult(Result.REJECTED_OTHER_REASON, "aborted");
        }
      }
      for (CreateRequest req : newChanges) {
        req.cmd.setResult(Result.REJECTED_OTHER_REASON, "aborted");
      }
    }
  }

  private void readChangesForReplace() throws OrmException {
    List<CheckedFuture<Change, OrmException>> futures =
        Lists.newArrayListWithCapacity(replaceByChange.size());
    for (ReplaceRequest request : replaceByChange.values()) {
      futures.add(db.changes().getAsync(request.ontoChange));
    }
    for (CheckedFuture<Change, OrmException> f : futures) {
      Change c = f.checkedGet();
      if (c != null) {
        replaceByChange.get(c.getId()).change = c;
      }
    }
  }

  private void readPatchSetsForReplace() throws OrmException {
    Map<Change.Id, ResultSet<PatchSet>> results = Maps.newHashMap();
    for (ReplaceRequest request : replaceByChange.values()) {
      Change.Id id = request.ontoChange;
      results.put(id, db.patchSets().byChange(id));
    }
    for (ReplaceRequest req : replaceByChange.values()) {
      req.patchSets = results.get(req.ontoChange).toList();
    }
  }

  private class ReplaceRequest {
    final Change.Id ontoChange;
    final RevCommit newCommit;
    final ReceiveCommand inputCommand;
    final boolean checkMergedInto;
    Change change;
    ChangeControl changeCtl;
    List<PatchSet> patchSets;
    PatchSet newPatchSet;
    ReceiveCommand cmd;
    PatchSetInfo info;
    ChangeMessage msg;
    String mergedIntoRef;
    private PatchSet.Id priorPatchSet;

    ReplaceRequest(final Change.Id toChange, final RevCommit newCommit,
        final ReceiveCommand cmd, final boolean checkMergedInto) {
      this.ontoChange = toChange;
      this.newCommit = newCommit;
      this.inputCommand = cmd;
      this.checkMergedInto = checkMergedInto;
    }

    boolean validate(boolean autoClose) throws IOException {
      if (!autoClose && inputCommand.getResult() != NOT_ATTEMPTED) {
        return false;
      }

      if (change == null || patchSets == null) {
        reject(inputCommand, "change " + ontoChange + " not found");
        return false;
      }

      if (change.getStatus().isClosed()) {
        reject(inputCommand, "change " + ontoChange + " closed");
        return false;
      }

      changeCtl = projectControl.controlFor(change);
      if (!changeCtl.canAddPatchSet()) {
        reject(inputCommand, "cannot replace " + ontoChange);
        return false;
      }

      rp.getRevWalk().parseBody(newCommit);

      if (!validCommit(changeCtl.getRefControl(), inputCommand, newCommit)) {
        return false;
      }

      priorPatchSet = change.currentPatchSetId();
      for (final PatchSet ps : patchSets) {
        if (ps.getRevision() == null) {
          log.warn("Patch set " + ps.getId() + " has no revision");
          reject(inputCommand, "change state corrupt");
          return false;
        }

        final String revIdStr = ps.getRevision().get();
        final ObjectId commitId;
        try {
          commitId = ObjectId.fromString(revIdStr);
        } catch (IllegalArgumentException e) {
          log.warn("Invalid revision in " + ps.getId() + ": " + revIdStr);
          reject(inputCommand, "change state corrupt");
          return false;
        }

        try {
          final RevCommit prior = rp.getRevWalk().parseCommit(commitId);

          // Don't allow the same commit to appear twice on the same change
          //
          if (newCommit == prior) {
            reject(inputCommand, "commit already exists");
            return false;
          }

          // Don't allow a change to directly depend upon itself. This is a
          // very common error due to users making a new commit rather than
          // amending when trying to address review comments.
          //
          if (rp.getRevWalk().isMergedInto(prior, newCommit)) {
            reject(inputCommand, "squash commits first");
            return false;
          }

          // Don't allow the same tree if the commit message is unmodified
          // or no parents were updated (rebase), else warn that only part
          // of the commit was modified.
          //
          if (priorPatchSet.equals(ps.getId()) && newCommit.getTree() == prior.getTree()) {
            rp.getRevWalk().parseBody(prior);
            final boolean messageEq =
                eq(newCommit.getFullMessage(), prior.getFullMessage());
            final boolean parentsEq = parentsEqual(newCommit, prior);
            final boolean authorEq = authorEqual(newCommit, prior);

            if (messageEq && parentsEq && authorEq && !autoClose) {
              reject(inputCommand, "no changes made");
              return false;
            } else {
              ObjectReader reader = rp.getRevWalk().getObjectReader();
              StringBuilder msg = new StringBuilder();
              msg.append("(W) ");
              msg.append(reader.abbreviate(newCommit).name());
              msg.append(":");
              msg.append(" no files changed");
              if (!authorEq) {
                msg.append(", author changed");
              }
              if (!messageEq) {
                msg.append(", message updated");
              }
              if (!parentsEq) {
                msg.append(", was rebased");
              }
              addMessage(msg.toString());
            }
          }
        } catch (IOException e) {
          log.error("Change " + change.getId() + " missing " + revIdStr, e);
          reject(inputCommand, "change state corrupt");
          return false;
        }
      }

      PatchSet.Id id =
          ChangeUtil.nextPatchSetId(allRefs, change.currentPatchSetId());
      newPatchSet = new PatchSet(id);
      newPatchSet.setCreatedOn(new Timestamp(System.currentTimeMillis()));
      newPatchSet.setUploader(currentUser.getAccountId());
      newPatchSet.setRevision(toRevId(newCommit));
      if (magicBranch != null && magicBranch.isDraft()) {
        newPatchSet.setDraft(true);
      }
      info = patchSetInfoFactory.get(newCommit, newPatchSet.getId());
      cmd = new ReceiveCommand(
          ObjectId.zeroId(),
          newCommit,
          newPatchSet.getRefName());
      return true;
    }

    CheckedFuture<PatchSet.Id, OrmException> insertPatchSet()
        throws IOException {
      rp.getRevWalk().parseBody(newCommit);

      final Thread caller = Thread.currentThread();
      ListenableFuture<PatchSet.Id> future = changeUpdateExector.submit(
          requestScopePropagator.wrap(new Callable<PatchSet.Id>() {
        @Override
        public PatchSet.Id call() throws OrmException {
          try {
            if (caller == Thread.currentThread()) {
              return insertPatchSet(db);
            } else {
              ReviewDb db = schemaFactory.open();
              try {
                return insertPatchSet(db);
              } finally {
                db.close();
              }
            }
          } finally {
            synchronized (replaceProgress) {
              replaceProgress.update(1);
            }
          }
        }
      }));
      return Futures.makeChecked(future, ORM_EXCEPTION);
    }

    PatchSet.Id insertPatchSet(ReviewDb db) throws OrmException {
      final Account.Id me = currentUser.getAccountId();
      final List<FooterLine> footerLines = newCommit.getFooterLines();
      final MailRecipients recipients = new MailRecipients();
      if (magicBranch != null) {
        recipients.add(magicBranch.getMailRecipients());
      }
      recipients.add(getRecipientsFromFooters(accountResolver, newPatchSet, footerLines));
      recipients.remove(me);

      db.changes().beginTransaction(change.getId());
      try {
        change = db.changes().get(change.getId());
        if (change == null || change.getStatus().isClosed()) {
          reject(inputCommand, "change is closed");
          return null;
        }

        ChangeUtil.insertAncestors(db, newPatchSet.getId(), newCommit);
        db.patchSets().insert(Collections.singleton(newPatchSet));

        if (checkMergedInto) {
          final Ref mergedInto = findMergedInto(change.getDest().get(), newCommit);
          mergedIntoRef = mergedInto != null ? mergedInto.getName() : null;
        }

        final MailRecipients oldRecipients =
            getRecipientsFromApprovals(ApprovalsUtil.copyLabels(
                db, labelTypes, priorPatchSet, newPatchSet.getId()));
        approvalsUtil.addReviewers(db, labelTypes, change, newPatchSet, info,
            recipients.getReviewers(), oldRecipients.getAll());
        recipients.add(oldRecipients);

        msg =
            new ChangeMessage(new ChangeMessage.Key(change.getId(), ChangeUtil
                .messageUUID(db)), me, newPatchSet.getCreatedOn(), newPatchSet.getId());
        msg.setMessage("Uploaded patch set " + newPatchSet.getPatchSetId() + ".");
        db.changeMessages().insert(Collections.singleton(msg));
        if (change.currentPatchSetId().equals(priorPatchSet)) {
          ChangeUtil.updateTrackingIds(db, change, trackingFooters, footerLines);
        }

        if (mergedIntoRef == null) {
          // Change should be new, so it can go through review again.
          //
          change =
              db.changes().atomicUpdate(change.getId(), new AtomicUpdate<Change>() {
                @Override
                public Change update(Change change) {
                  if (change.getStatus().isClosed()) {
                    return null;
                  }

                  if (!change.currentPatchSetId().equals(priorPatchSet)) {
                    return change;
                  }

                  if (magicBranch != null && magicBranch.topic != null) {
                    change.setTopic(magicBranch.topic);
                  }
                  if (change.getStatus() == Change.Status.DRAFT && newPatchSet.isDraft()) {
                    // Leave in draft status.
                  } else {
                    change.setStatus(Change.Status.NEW);
                  }
                  change.setLastSha1MergeTested(null);
                  change.setCurrentPatchSet(info);
                  ChangeUtil.updated(change);
                  return change;
                }
              });
          if (change == null) {
            db.patchSets().delete(Collections.singleton(newPatchSet));
            db.changeMessages().delete(Collections.singleton(msg));
            reject(inputCommand, "change is closed");
            return null;
          }
        }

        db.commit();
      } finally {
        db.rollback();
      }

      if (mergedIntoRef != null) {
        // Change was already submitted to a branch, close it.
        //
        markChangeMergedByPush(db, this);
      }

      if (cmd.getResult() == NOT_ATTEMPTED) {
        cmd.execute(rp);
      }
      gitRefUpdated.fire(project.getNameKey(), newPatchSet.getRefName(),
          ObjectId.zeroId(), newCommit);
      hooks.doPatchsetCreatedHook(change, newPatchSet, db);
      if (mergedIntoRef != null) {
        hooks.doChangeMergedHook(
            change, currentUser.getAccount(), newPatchSet, db);
      }
      workQueue.getDefaultQueue()
          .submit(requestScopePropagator.wrap(new Runnable() {
        @Override
        public void run() {
          try {
            ReplacePatchSetSender cm =
                replacePatchSetFactory.create(change);
            cm.setFrom(me);
            cm.setPatchSet(newPatchSet, info);
            cm.setChangeMessage(msg);
            cm.addReviewers(recipients.getReviewers());
            cm.addExtraCC(recipients.getCcOnly());
            cm.send();
          } catch (Exception e) {
            log.error("Cannot send email for new patch set " + newPatchSet.getId(), e);
          }
          if (mergedIntoRef != null) {
            sendMergedEmail(ReplaceRequest.this);
          }
        }

        @Override
        public String toString() {
          return "send-email newpatchset";
        }
      }));
      return newPatchSet.getId();
    }
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

  static boolean authorEqual(RevCommit a, RevCommit b) {
    PersonIdent aAuthor = a.getAuthorIdent();
    PersonIdent bAuthor = b.getAuthorIdent();

    if (aAuthor == null && bAuthor == null) {
      return true;
    } else if (aAuthor == null || bAuthor == null) {
      return false;
    }

    return eq(aAuthor.getName(), bAuthor.getName())
        && eq(aAuthor.getEmailAddress(), bAuthor.getEmailAddress());
  }

  static boolean eq(String a, String b) {
    if (a == null && b == null) {
      return true;
    } else if (a == null || b == null) {
      return false;
    } else {
      return a.equals(b);
    }
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

  private void validateNewCommits(RefControl ctl, ReceiveCommand cmd) {
    if (ctl.canForgeAuthor()
        && ctl.canForgeCommitter()
        && ctl.canForgeGerritServerIdentity()
        && ctl.canUploadMerges()
        && !projectControl.getProjectState().isUseSignedOffBy()
        && Iterables.isEmpty(rejectCommits)
        && !GitRepositoryManager.REF_CONFIG.equals(ctl.getRefName())
        && !(MagicBranch.isMagicBranch(cmd.getRefName())
            || NEW_PATCHSET.matcher(cmd.getRefName()).matches())) {
      return;
    }

    final RevWalk walk = rp.getRevWalk();
    walk.reset();
    walk.sort(RevSort.NONE);
    try {
      Set<ObjectId> existing = Sets.newHashSet();
      walk.markStart(walk.parseCommit(cmd.getNewId()));
      markHeadsAsUninteresting(walk, existing, cmd.getRefName());

      RevCommit c;
      while ((c = walk.next()) != null) {
        if (existing.contains(c)) {
          continue;
        } else if (!validCommit(ctl, cmd, c)) {
          break;
        }
      }
    } catch (IOException err) {
      cmd.setResult(REJECTED_MISSING_OBJECT);
      log.error("Invalid pack upload; one or more objects weren't sent", err);
    }
  }

  private boolean validCommit(final RefControl ctl, final ReceiveCommand cmd,
      final RevCommit c) throws MissingObjectException, IOException {

    CommitReceivedEvent receiveEvent =
        new CommitReceivedEvent(cmd, project, ctl.getRefName(), c, currentUser);
    CommitValidators commitValidators =
        commitValidatorsFactory.create(ctl, sshInfo, repo);

    try {
      messages.addAll(commitValidators.validateForReceiveCommits(receiveEvent));
    } catch (CommitValidationException e) {
      messages.addAll(e.getMessages());
      reject(cmd, e.getMessage());
      return false;
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
      final Map<Change.Key, Change.Id> byKey = openChangesByKey(
          new Branch.NameKey(project.getNameKey(), cmd.getRefName()));
      final List<ReplaceRequest> toClose = new ArrayList<ReplaceRequest>();
      RevCommit c;
      while ((c = rw.next()) != null) {
        final Ref ref = byCommit.get(c.copy());
        if (ref != null) {
          rw.parseBody(c);
          Change.Key closedChange =
              closeChange(cmd, PatchSet.Id.fromRef(ref.getName()), c);
          closeProgress.update(1);
          if (closedChange != null) {
            byKey.remove(closedChange);
          }
        }

        rw.parseBody(c);
        for (final String changeId : c.getFooterLines(CHANGE_ID)) {
          final Change.Id onto = byKey.get(new Change.Key(changeId.trim()));
          if (onto != null) {
            final ReplaceRequest req = new ReplaceRequest(onto, c, cmd, false);
            req.change = db.changes().get(onto);
            req.patchSets = db.patchSets().byChange(onto).toList();
            toClose.add(req);
            break;
          }
        }
      }

      for (final ReplaceRequest req : toClose) {
        final PatchSet.Id psi = req.validate(true)
            ? req.insertPatchSet().checkedGet()
            : null;
        if (psi != null) {
          closeChange(req.inputCommand, psi, req.newCommit);
          closeProgress.update(1);
        }
      }

      // It handles gitlinks if required.

      rw.reset();
      final RevCommit codeReviewCommit = rw.parseCommit(cmd.getNewId());

      final SubmoduleOp subOp =
          subOpFactory.create(
              new Branch.NameKey(project.getNameKey(), cmd.getRefName()),
              codeReviewCommit, rw, repo, project, new ArrayList<Change>(),
              new HashMap<Change.Id, CodeReviewCommit>());
      subOp.update();
    } catch (IOException e) {
      log.error("Can't scan for changes to close", e);
    } catch (OrmException e) {
      log.error("Can't scan for changes to close", e);
    } catch (SubmoduleException e) {
      log.error("Can't complete git links check", e);
    }
  }

  private Change.Key closeChange(final ReceiveCommand cmd, final PatchSet.Id psi,
      final RevCommit commit) throws OrmException {
    final String refName = cmd.getRefName();
    final Change.Id cid = psi.getParentKey();

    final Change change = db.changes().get(cid);
    final PatchSet ps = db.patchSets().get(psi);
    if (change == null || ps == null) {
      log.warn(project.getName() + " " + psi + " is missing");
      return null;
    }

    if (change.getStatus() == Change.Status.MERGED ||
        change.getStatus() == Change.Status.ABANDONED ||
        !change.getDest().get().equals(refName)) {
      // If it's already merged or the commit is not aimed for
      // this change's destination, don't make further updates.
      //
      return null;
    }

    ReplaceRequest result = new ReplaceRequest(cid, commit, cmd, false);
    result.change = change;
    result.newPatchSet = ps;
    result.info = patchSetInfoFactory.get(commit, psi);
    result.mergedIntoRef = refName;
    markChangeMergedByPush(db, result);
    hooks.doChangeMergedHook(
        change, currentUser.getAccount(), result.newPatchSet, db);
    sendMergedEmail(result);
    return change.getKey();
  }

  private Map<ObjectId, Ref> changeRefsById() throws IOException {
    if (refsById == null) {
      refsById = new HashMap<ObjectId, Ref>();
      for (Ref r : repo.getRefDatabase().getRefs("refs/changes/").values()) {
        if (PatchSet.isRef(r.getName())) {
          refsById.put(r.getObjectId(), r);
        }
      }
    }
    return refsById;
  }

  private Map<Change.Key, Change.Id> openChangesByKey(Branch.NameKey branch)
      throws OrmException {
    final Map<Change.Key, Change.Id> r = new HashMap<Change.Key, Change.Id>();
    for (Change c : db.changes().byBranchOpenAll(branch)) {
      r.put(c.getKey(), c.getId());
    }
    return r;
  }

  private void markChangeMergedByPush(final ReviewDb db,
      final ReplaceRequest result) throws OrmException {
    final Change change = result.change;
    final String mergedIntoRef = result.mergedIntoRef;

    change.setCurrentPatchSet(result.info);
    change.setStatus(Change.Status.MERGED);
    ChangeUtil.updated(change);

    approvalsUtil.syncChangeStatus(change);

    final StringBuilder msgBuf = new StringBuilder();
    msgBuf.append("Change has been successfully pushed");
    if (!mergedIntoRef.equals(change.getDest().get())) {
      msgBuf.append(" into ");
      if (mergedIntoRef.startsWith(Constants.R_HEADS)) {
        msgBuf.append("branch ");
        msgBuf.append(Repository.shortenRefName(mergedIntoRef));
      } else {
        msgBuf.append(mergedIntoRef);
      }
    }
    msgBuf.append(".");
    final ChangeMessage msg =
        new ChangeMessage(new ChangeMessage.Key(change.getId(), ChangeUtil
            .messageUUID(db)), currentUser.getAccountId(), result.info.getKey());
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

  private void sendMergedEmail(final ReplaceRequest result) {
    workQueue.getDefaultQueue()
        .submit(requestScopePropagator.wrap(new Runnable() {
      @Override
      public void run() {
        try {
          final MergedSender cm = mergedSenderFactory.create(result.changeCtl);
          cm.setFrom(currentUser.getAccountId());
          cm.setPatchSet(result.newPatchSet, result.info);
          cm.send();
        } catch (Exception e) {
          final PatchSet.Id psi = result.newPatchSet.getId();
          log.error("Cannot send email for submitted patch set " + psi, e);
        }
      }

      @Override
      public String toString() {
        return "send-email merged";
      }
    }));
  }

  private static RevId toRevId(final RevCommit src) {
    return new RevId(src.getId().name());
  }

  private void reject(final ReceiveCommand cmd) {
    reject(cmd, "prohibited by Gerrit");
  }

  private void reject(final ReceiveCommand cmd, final String why) {
    cmd.setResult(REJECTED_OTHER_REASON, why);
    commandProgress.update(1);
  }

  private static boolean isHead(final Ref ref) {
    return ref.getName().startsWith(Constants.R_HEADS);
  }

  private static boolean isHead(final ReceiveCommand cmd) {
    return cmd.getRefName().startsWith(Constants.R_HEADS);
  }

  private static boolean isConfig(final ReceiveCommand cmd) {
    return cmd.getRefName().equals(GitRepositoryManager.REF_CONFIG);
  }
}
