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

import static com.google.gerrit.reviewdb.client.RefNames.REFS_CHANGES;
import static com.google.gerrit.server.git.MultiProgressMonitor.UNKNOWN;
import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromFooters;
import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromReviewers;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.RefDatabase.ALL;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.OK;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_MISSING_OBJECT;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_NONFASTFORWARD;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.util.concurrent.CheckedFuture;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.gerrit.common.ChangeHookRunner.HookResult;
import com.google.gerrit.common.ChangeHooks;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicMap.Entry;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalCopier;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeMessagesUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountCache;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeKind;
import com.google.gerrit.server.change.ChangeKindCache;
import com.google.gerrit.server.change.ChangesCollection;
import com.google.gerrit.server.change.MergeabilityChecker;
import com.google.gerrit.server.change.RevisionResource;
import com.google.gerrit.server.change.Submit;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.index.ChangeIndexer;
import com.google.gerrit.server.mail.CreateChangeSender;
import com.google.gerrit.server.mail.MailUtil.MailRecipients;
import com.google.gerrit.server.mail.MergedSender;
import com.google.gerrit.server.mail.ReplacePatchSetSender;
import com.google.gerrit.server.notedb.ChangeUpdate;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.MagicBranch;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.server.util.TimeUtil;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gwtorm.server.AtomicUpdate;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
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
import org.eclipse.jgit.transport.RefFilter;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.eclipse.jgit.transport.UploadPack;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Receives change upload using the Git receive-pack protocol. */
public class ReceiveCommits {
  private static final Logger log =
      LoggerFactory.getLogger(ReceiveCommits.class);

  public static final Pattern NEW_PATCHSET = Pattern.compile(
      "^" + REFS_CHANGES + "(?:[0-9][0-9]/)?([1-9][0-9]*)(?:/new)?$");

  private static final FooterKey CHANGE_ID = new FooterKey("Change-Id");

  private static final String COMMAND_REJECTION_MESSAGE_FOOTER =
      "Please read the documentation and contact an administrator\n"
          + "if you feel the configuration is incorrect";

  private enum Error {
        CONFIG_UPDATE("You are not allowed to perform this operation.\n"
        + "Configuration changes can only be pushed by project owners\n"
        + "who also have 'Push' rights on " + RefNames.REFS_CONFIG),
        UPDATE("You are not allowed to perform this operation.\n"
        + "To push into this reference you need 'Push' rights."),
        DELETE("You need 'Push' rights with the 'Force Push'\n"
            + "flag set to delete references."),
        DELETE_CHANGES("Cannot delete from '" + REFS_CHANGES + "'"),
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

  private static final Function<Exception, InsertException> INSERT_EXCEPTION =
      new Function<Exception, InsertException>() {
        @Override
        public InsertException apply(Exception input) {
          if (input instanceof OrmException) {
            return new InsertException("ORM error", input);
          }
          if (input instanceof IOException) {
            return new InsertException("IO error", input);
          }
          return new InsertException("Error inserting change/patchset", input);
        }
      };

  private Set<Account.Id> reviewersFromCommandLine = Sets.newLinkedHashSet();
  private Set<Account.Id> ccFromCommandLine = Sets.newLinkedHashSet();

  private final IdentifiedUser currentUser;
  private final ReviewDb db;
  private final ChangeData.Factory changeDataFactory;
  private final ChangeUpdate.Factory updateFactory;
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
  private final ApprovalCopier approvalCopier;
  private final ChangeMessagesUtil cmUtil;
  private final GitRepositoryManager repoManager;
  private final ProjectCache projectCache;
  private final String canonicalWebUrl;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final TagCache tagCache;
  private final AccountCache accountCache;
  private final ChangesCollection changes;
  private final ChangeInserter.Factory changeInserterFactory;
  private final WorkQueue workQueue;
  private final ListeningExecutorService changeUpdateExector;
  private final RequestScopePropagator requestScopePropagator;
  private final ChangeIndexer indexer;
  private final MergeabilityChecker mergeabilityChecker;
  private final SshInfo sshInfo;
  private final AllProjectsName allProjectsName;
  private final ReceiveConfig receiveConfig;
  private final ChangeKindCache changeKindCache;

  private final ProjectControl projectControl;
  private final Project project;
  private final LabelTypes labelTypes;
  private final Repository repo;
  private final ReceivePack rp;
  private final NoteMap rejectCommits;
  private MagicBranchInput magicBranch;

  private List<CreateRequest> newChanges = Collections.emptyList();
  private final Map<Change.Id, ReplaceRequest> replaceByChange =
      new HashMap<>();
  private final Map<RevCommit, ReplaceRequest> replaceByCommit =
      new HashMap<>();
  private final Set<RevCommit> validCommits = new HashSet<>();

  private ListMultimap<Change.Id, Ref> refsByChange;
  private SetMultimap<ObjectId, Ref> refsById;
  private Map<String, Ref> allRefs;

  private final SubmoduleOp.Factory subOpFactory;
  private final Provider<Submit> submitProvider;
  private final MergeQueue mergeQueue;
  private final DynamicMap<ProjectConfigEntry> pluginConfigEntries;

  private final List<CommitValidationMessage> messages = new ArrayList<>();
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
      final ChangeData.Factory changeDataFactory,
      final ChangeUpdate.Factory updateFactory,
      final AccountResolver accountResolver,
      final CmdLineParser.Factory optionParserFactory,
      final CreateChangeSender.Factory createChangeSenderFactory,
      final MergedSender.Factory mergedSenderFactory,
      final ReplacePatchSetSender.Factory replacePatchSetFactory,
      final GitReferenceUpdated gitRefUpdated,
      final PatchSetInfoFactory patchSetInfoFactory,
      final ChangeHooks hooks,
      final ApprovalsUtil approvalsUtil,
      final ApprovalCopier approvalCopier,
      final ChangeMessagesUtil cmUtil,
      final ProjectCache projectCache,
      final GitRepositoryManager repoManager,
      final TagCache tagCache,
      final AccountCache accountCache,
      final ChangeCache changeCache,
      final ChangesCollection changes,
      final ChangeInserter.Factory changeInserterFactory,
      final CommitValidators.Factory commitValidatorsFactory,
      @CanonicalWebUrl final String canonicalWebUrl,
      @GerritPersonIdent final PersonIdent gerritIdent,
      final WorkQueue workQueue,
      @ChangeUpdateExecutor ListeningExecutorService changeUpdateExector,
      final RequestScopePropagator requestScopePropagator,
      final ChangeIndexer indexer,
      final MergeabilityChecker mergeabilityChecker,
      final SshInfo sshInfo,
      final AllProjectsName allProjectsName,
      ReceiveConfig config,
      @Assisted final ProjectControl projectControl,
      @Assisted final Repository repo,
      final SubmoduleOp.Factory subOpFactory,
      final Provider<Submit> submitProvider,
      final MergeQueue mergeQueue,
      final ChangeKindCache changeKindCache,
      final DynamicMap<ProjectConfigEntry> pluginConfigEntries) throws IOException {
    this.currentUser = (IdentifiedUser) projectControl.getCurrentUser();
    this.db = db;
    this.changeDataFactory = changeDataFactory;
    this.updateFactory = updateFactory;
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
    this.approvalCopier = approvalCopier;
    this.cmUtil = cmUtil;
    this.projectCache = projectCache;
    this.repoManager = repoManager;
    this.canonicalWebUrl = canonicalWebUrl;
    this.tagCache = tagCache;
    this.accountCache = accountCache;
    this.changes = changes;
    this.changeInserterFactory = changeInserterFactory;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.workQueue = workQueue;
    this.changeUpdateExector = changeUpdateExector;
    this.requestScopePropagator = requestScopePropagator;
    this.indexer = indexer;
    this.mergeabilityChecker = mergeabilityChecker;
    this.sshInfo = sshInfo;
    this.allProjectsName = allProjectsName;
    this.receiveConfig = config;
    this.changeKindCache = changeKindCache;

    this.projectControl = projectControl;
    this.labelTypes = projectControl.getLabelTypes();
    this.project = projectControl.getProject();
    this.repo = repo;
    this.rp = new ReceivePack(repo);
    this.rejectCommits = BanCommit.loadRejectCommitsMap(repo);

    this.subOpFactory = subOpFactory;
    this.submitProvider = submitProvider;
    this.mergeQueue = mergeQueue;
    this.pluginConfigEntries = pluginConfigEntries;

    this.messageSender = new ReceivePackMessageSender();

    ProjectState ps = projectControl.getProjectState();

    rp.setAllowCreates(true);
    rp.setAllowDeletes(true);
    rp.setAllowNonFastForwards(true);
    rp.setCheckReceivedObjects(ps.getConfig().getCheckReceivedObjects());
    rp.setRefFilter(new RefFilter() {
      @Override
      public Map<String, Ref> filter(Map<String, Ref> refs) {
        Map<String, Ref> filteredRefs = Maps.newHashMapWithExpectedSize(refs.size());
        for (Map.Entry<String, Ref> e : refs.entrySet()) {
          String name = e.getKey();
          if (!name.startsWith(REFS_CHANGES)
              && !name.startsWith(RefNames.REFS_CACHE_AUTOMERGE)) {
            filteredRefs.put(name, e.getValue());
          }
        }
        return filteredRefs;
      }
    });

    if (!projectControl.allRefsAreVisible()) {
      rp.setCheckReferencedObjectsAreReachable(config.checkReferencedObjectsAreReachable);
      rp.setAdvertiseRefsHook(new VisibleRefFilter(tagCache, changeCache, repo, projectControl, db, false));
    }
    List<AdvertiseRefsHook> advHooks = new ArrayList<>(3);
    advHooks.add(new AdvertiseRefsHook() {
      @Override
      public void advertiseRefs(BaseReceivePack rp)
          throws ServiceMayNotContinueException {
        allRefs = rp.getAdvertisedRefs();
        if (allRefs == null) {
          try {
            allRefs = rp.getRepository().getRefDatabase().getRefs(ALL);
          } catch (ServiceMayNotContinueException e) {
            throw e;
          } catch (IOException e) {
            ServiceMayNotContinueException ex = new ServiceMayNotContinueException();
            ex.initCause(e);
            throw ex;
          }
        }
        rp.setAdvertisedRefs(allRefs, rp.getAdvertisedObjects());
      }

      @Override
      public void advertiseRefs(UploadPack uploadPack) {
      }
    });
    advHooks.add(rp.getAdvertiseRefsHook());
    advHooks.add(new ReceiveCommitsAdvertiseRefsHook(
        db, projectControl.getProject().getNameKey()));
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
          try {
            switch (c.getType()) {
              case CREATE:
                if (isHead(c) || isConfig(c)) {
                  autoCloseChanges(c);
                }
                break;

              case UPDATE: // otherwise known as a fast-forward
                tagCache.updateFastForward(project.getNameKey(),
                    c.getRefName(),
                    c.getOldId(),
                    c.getNewId());
                if (isHead(c) || isConfig(c)) {
                  autoCloseChanges(c);
                }
                break;

              case UPDATE_NONFASTFORWARD:
                if (isHead(c) || isConfig(c)) {
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
          } catch (NoSuchChangeException e) {
            c.setResult(REJECTED_OTHER_REASON,
                "No such change: " + e.getMessage());
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
    if (!Iterables.isEmpty(created)) {
      addMessage("");
      addMessage("New Changes:");
      for (CreateRequest c : created) {
        addMessage(formatChangeUrl(canonicalWebUrl, c.change));
      }
      addMessage("");
    }

    Iterable<ReplaceRequest> updated =
        Iterables.filter(replaceByChange.values(),
            new Predicate<ReplaceRequest>() {
              @Override
              public boolean apply(ReplaceRequest input) {
                return !input.skip && input.inputCommand.getResult() == OK;
              }
            });
    if (!Iterables.isEmpty(updated)) {
      addMessage("");
      addMessage("Updated Changes:");
      for (ReplaceRequest u : updated) {
        addMessage(formatChangeUrl(canonicalWebUrl, u.change));
      }
      addMessage("");
    }
  }

  private static String formatChangeUrl(String url, Change change) {
    StringBuilder m = new StringBuilder()
        .append("  ")
        .append(url)
        .append(change.getChangeId())
        .append(" ")
        .append(ChangeUtil.cropSubject(change.getSubject()));
    if (change.getStatus() == Change.Status.DRAFT) {
      m.append(" [DRAFT]");
    }
    return m.toString();
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
        } catch (InsertException err) {
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

    List<String> lastCreateChangeErrors = Lists.newArrayList();
    for (CreateRequest create : newChanges) {
      if (create.cmd.getResult() == OK) {
        okToInsert++;
      } else {
        String createChangeResult =
            String.format("%s %s",
                create.cmd.getResult(),
                Strings.nullToEmpty(create.cmd.getMessage())).trim();
        lastCreateChangeErrors.add(createChangeResult);
        log.error(String.format("Command %s on %s:%s not completed: %s",
            create.cmd.getType(),
            project.getName(),
            create.cmd.getRefName(),
            createChangeResult));
      }
    }

    if (okToInsert != replaceCount + newChanges.size()) {
      // One or more new references failed to create. Assume the
      // system isn't working correctly anymore and abort.
      reject(magicBranch.cmd, "Unable to create changes: "
          + Joiner.on(' ').join(lastCreateChangeErrors));
      log.error(String.format(
          "Only %d of %d new change refs created in %s; aborting",
          okToInsert, replaceCount + newChanges.size(), project.getName()));
      return;
    }

    try {
      List<CheckedFuture<?, InsertException>> futures = Lists.newArrayList();
      for (ReplaceRequest replace : replaceByChange.values()) {
        if (magicBranch != null && replace.inputCommand == magicBranch.cmd) {
          futures.add(replace.insertPatchSet());
        }
      }

      for (CreateRequest create : newChanges) {
        futures.add(create.insertChange());
      }

      for (CheckedFuture<?, InsertException> f : futures) {
        f.checkedGet();
      }
      magicBranch.cmd.setResult(OK);
    } catch (InsertException err) {
      log.error("Can't insert change/patchset for " + project.getName(), err);
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

              for (Entry<ProjectConfigEntry> e : pluginConfigEntries) {
                PluginConfig pluginCfg = cfg.getPluginConfig(e.getPluginName());
                ProjectConfigEntry configEntry = e.getProvider().get();
                String value = pluginCfg.getString(e.getExportName());
                String oldValue =
                    projectControl.getProjectState().getConfig()
                        .getPluginConfig(e.getPluginName())
                        .getString(e.getExportName());
                if (configEntry.getType() == ProjectConfigEntry.Type.ARRAY) {
                  List<String> l =
                      Arrays.asList(projectControl.getProjectState()
                          .getConfig().getPluginConfig(e.getPluginName())
                          .getStringList(e.getExportName()));
                  oldValue = Joiner.on("\n").join(l);
                }

                if ((value == null ? oldValue != null : !value.equals(oldValue)) &&
                    !configEntry.isEditable(projectControl.getProjectState())) {
                  reject(cmd, String.format(
                      "invalid project configuration: Not allowed to set parameter"
                          + " '%s' of plugin '%s' on project '%s'.",
                      e.getExportName(), e.getPluginName(), project.getName()));
                  continue;
                }

                if (ProjectConfigEntry.Type.LIST.equals(configEntry.getType())
                    && value != null && !configEntry.getPermittedValues().contains(value)) {
                  reject(cmd, String.format(
                      "invalid project configuration: The value '%s' is "
                          + "not permitted for parameter '%s' of plugin '%s'.",
                      value, e.getExportName(), e.getPluginName()));
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
    if (ctl.canCreate(rp.getRevWalk(), obj, allRefs.values().contains(obj))) {
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
      if (RefNames.REFS_CONFIG.equals(ctl.getRefName())) {
        errors.put(Error.CONFIG_UPDATE, RefNames.REFS_CONFIG);
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
    if (ctl.getRefName().startsWith(REFS_CHANGES)) {
      errors.put(Error.DELETE_CHANGES, ctl.getRefName());
      reject(cmd, "cannot delete changes");
    } else if (ctl.canDelete()) {
      batch.addCommand(cmd);
    } else {
      if (RefNames.REFS_CONFIG.equals(ctl.getRefName())) {
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
    Map<String, Short> labels = new HashMap<>();
    List<RevCommit> baseCommit;
    LabelTypes labelTypes;
    CmdLineParser clp;

    @Option(name = "--base", metaVar = "BASE", usage = "merge base of changes")
    List<ObjectId> base;

    @Option(name = "--topic", metaVar = "NAME", usage = "attach topic to changes")
    String topic;

    @Option(name = "--draft", usage = "mark new/updated changes as draft")
    boolean draft;

    @Option(name = "--submit", usage = "immediately submit the change")
    boolean submit;

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

    @Option(name = "-l", metaVar = "LABEL+VALUE",
        usage = "label(s) to assign (defaults to +1 if no value provided")
    void addLabel(final String token) throws CmdLineException {
      LabelVote v = LabelVote.parse(token);
      try {
        LabelType.checkName(v.getLabel());
        ApprovalsUtil.checkLabel(labelTypes, v.getLabel(), v.getValue());
      } catch (IllegalArgumentException e) {
        throw clp.reject(e.getMessage());
      }
      labels.put(v.getLabel(), v.getValue());
    }

    MagicBranchInput(ReceiveCommand cmd, LabelTypes labelTypes) {
      this.cmd = cmd;
      this.draft = cmd.getRefName().startsWith(MagicBranch.NEW_DRAFT_CHANGE);
      this.labelTypes = labelTypes;
    }

    boolean isDraft() {
      return draft;
    }

    boolean isSubmit() {
      return submit;
    }

    MailRecipients getMailRecipients() {
      return new MailRecipients(reviewer, cc);
    }

    Map<String, Short> getLabels() {
      return labels;
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

    void setCmdLineParser(CmdLineParser clp) {
      this.clp = clp;
    }
  }

  private void parseMagicBranch(final ReceiveCommand cmd) {
    // Permit exactly one new change request per push.
    if (magicBranch != null) {
      reject(cmd, "duplicate request");
      return;
    }

    magicBranch = new MagicBranchInput(cmd, labelTypes);
    magicBranch.reviewer.addAll(reviewersFromCommandLine);
    magicBranch.cc.addAll(ccFromCommandLine);

    String ref;
    CmdLineParser clp = optionParserFactory.create(magicBranch);
    magicBranch.setCmdLineParser(clp);
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
    if (!magicBranch.ctl.canWrite()) {
      reject(cmd, "project is read only");
      return;
    }

    if (magicBranch.isDraft()
        && (!receiveConfig.allowDrafts
            || projectControl.controlForRef("refs/drafts/" + ref)
            .isBlocked(Permission.PUSH))) {
      errors.put(Error.CODE_REVIEW, ref);
      reject(cmd, "cannot upload drafts");
      return;
    }

    if (!magicBranch.ctl.canUpload()) {
      errors.put(Error.CODE_REVIEW, ref);
      reject(cmd, "cannot upload review");
      return;
    }

    if (magicBranch.isDraft() && magicBranch.isSubmit()) {
      reject(cmd, "cannot submit draft");
      return;
    }

    if (magicBranch.isSubmit() && !projectControl.controlForRef(
        MagicBranch.NEW_CHANGE + ref).canSubmit()) {
      reject(cmd, "submit not allowed");
    }

    RevWalk walk = rp.getRevWalk();
    if (magicBranch.base != null) {
      magicBranch.baseCommit = Lists.newArrayListWithCapacity(
          magicBranch.base.size());
      for (ObjectId id : magicBranch.base) {
        try {
          magicBranch.baseCommit.add(walk.parseCommit(id));
        } catch (IncorrectObjectTypeException notCommit) {
          reject(cmd, "base must be a commit");
          return;
        } catch (MissingObjectException e) {
          reject(cmd, "base not found");
          return;
        } catch (IOException e) {
          log.warn(String.format(
              "Project %s cannot read %s",
              project.getName(), id.name()), e);
          reject(cmd, "internal server error");
          return;
        }
      }
    }

    // Validate that the new commits are connected with the target
    // branch.  If they aren't, we want to abort. We do this check by
    // looking to see if we can compute a merge base between the new
    // commits and the target branch head.
    //
    try {
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
        }
      } finally {
        walk.reset();
        walk.setRevFilter(oldRevFilter);
      }
    } catch (IOException e) {
      magicBranch.cmd.setResult(REJECTED_MISSING_OBJECT);
      log.error("Invalid pack upload; one or more objects weren't sent", e);
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
      if (magicBranch.baseCommit != null) {
        for (RevCommit c : magicBranch.baseCommit) {
          walk.markUninteresting(c);
        }
        assert magicBranch.ctl != null;
        Ref targetRef = allRefs.get(magicBranch.ctl.getRefName());
        if (targetRef != null) {
          walk.markUninteresting(walk.parseCommit(targetRef.getObjectId()));
        }
      } else {
        markHeadsAsUninteresting(
            walk,
            existing,
            magicBranch.ctl != null ? magicBranch.ctl.getRefName() : null);
      }

      List<ChangeLookup> pending = Lists.newArrayList();
      final Set<Change.Key> newChangeIds = new HashSet<>();
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
          newChanges.add(new CreateRequest(magicBranch.ctl, c, changeKey));
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
        newChanges.add(new CreateRequest(magicBranch.ctl, p.commit, p.changeKey));
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
      } else if (ref.getName().startsWith(REFS_CHANGES)) {
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
    final ReceiveCommand cmd;
    final ChangeInserter ins;
    boolean created;

    CreateRequest(RefControl ctl, RevCommit c, Change.Key changeKey)
        throws OrmException {
      commit = c;
      change = new Change(changeKey,
          new Change.Id(db.nextChangeId()),
          currentUser.getAccountId(),
          magicBranch.dest,
          TimeUtil.nowTs());
      change.setTopic(magicBranch.topic);
      ins = changeInserterFactory.create(ctl, change, c)
          .setDraft(magicBranch.isDraft());
      cmd = new ReceiveCommand(ObjectId.zeroId(), c,
          ins.getPatchSet().getRefName());
    }

    CheckedFuture<Void, InsertException> insertChange() throws IOException {
      rp.getRevWalk().parseBody(commit);

      final Thread caller = Thread.currentThread();
      ListenableFuture<Void> future = changeUpdateExector.submit(
          requestScopePropagator.wrap(new Callable<Void>() {
        @Override
        public Void call() throws OrmException, IOException {
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
      return Futures.makeChecked(future, INSERT_EXCEPTION);
    }

    private void insertChange(ReviewDb db) throws OrmException, IOException {
      final PatchSet ps = ins.getPatchSet();
      final Account.Id me = currentUser.getAccountId();
      final List<FooterLine> footerLines = commit.getFooterLines();
      final MailRecipients recipients = new MailRecipients();
      Map<String, Short> approvals = new HashMap<>();
      if (magicBranch != null) {
        recipients.add(magicBranch.getMailRecipients());
        approvals = magicBranch.getLabels();
      }
      recipients.add(getRecipientsFromFooters(accountResolver, ps, footerLines));
      recipients.remove(me);

      ChangeMessage msg =
          new ChangeMessage(new ChangeMessage.Key(change.getId(),
              ChangeUtil.messageUUID(db)), me, ps.getCreatedOn(), ps.getId());
      msg.setMessage("Uploaded patch set " + ps.getPatchSetId() + ".");

      ins
        .setReviewers(recipients.getReviewers())
        .setApprovals(approvals)
        .setMessage(msg)
        .setSendMail(false)
        .insert();
      created = true;

      workQueue.getDefaultQueue()
          .submit(requestScopePropagator.wrap(new Runnable() {
        @Override
        public void run() {
          try {
            CreateChangeSender cm =
                createChangeSenderFactory.create(change);
            cm.setFrom(me);
            cm.setPatchSet(ps, ins.getPatchSetInfo());
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

      if (magicBranch != null && magicBranch.isSubmit()) {
        submit(projectControl.controlFor(change), ps);
      }
    }
  }

  private void submit(ChangeControl changeCtl, PatchSet ps)
      throws OrmException, IOException {
    Submit submit = submitProvider.get();
    RevisionResource rsrc = new RevisionResource(changes.parse(changeCtl), ps);
    Change c;
    try {
      // Force submit even if submit rule evaluation fails.
      c = submit.submit(rsrc, currentUser, true);
    } catch (ResourceConflictException e) {
      throw new IOException(e);
    }
    if (c == null) {
      addError("Submitting change " + changeCtl.getChange().getChangeId()
          + " failed.");
    } else {
      addMessage("");
      mergeQueue.merge(c.getDest());
      c = db.changes().get(c.getId());
      switch (c.getStatus()) {
        case SUBMITTED:
          addMessage("Change " + c.getChangeId() + " submitted.");
          break;
        case MERGED:
          addMessage("Change " + c.getChangeId() + " merged.");
          break;
        case NEW:
          ChangeMessage msg = submit.getConflictMessage(rsrc);
          if (msg != null) {
            addMessage("Change " + c.getChangeId() + ": " + msg.getMessage());
            break;
          }
        default:
          addMessage("change " + c.getChangeId() + " is "
              + c.getStatus().name().toLowerCase());
      }
    }
  }

  private void preparePatchSetsForReplace() {
    try {
      readChangesForReplace();
      for (Iterator<ReplaceRequest> itr = replaceByChange.values().iterator();
          itr.hasNext();) {
        ReplaceRequest req = itr.next();
        if (req.inputCommand.getResult() == NOT_ATTEMPTED) {
          req.validate(false);
          if (req.skip && req.cmd == null) {
            itr.remove();
            replaceByCommit.remove(req.newCommit);
          }
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

  private class ReplaceRequest {
    final Change.Id ontoChange;
    final RevCommit newCommit;
    final ReceiveCommand inputCommand;
    final boolean checkMergedInto;
    Change change;
    ChangeControl changeCtl;
    BiMap<RevCommit, PatchSet.Id> revisions;
    PatchSet newPatchSet;
    ReceiveCommand cmd;
    PatchSetInfo info;
    ChangeMessage msg;
    String mergedIntoRef;
    boolean skip;
    private PatchSet.Id priorPatchSet;

    ReplaceRequest(final Change.Id toChange, final RevCommit newCommit,
        final ReceiveCommand cmd, final boolean checkMergedInto) {
      this.ontoChange = toChange;
      this.newCommit = newCommit;
      this.inputCommand = cmd;
      this.checkMergedInto = checkMergedInto;

      revisions = HashBiMap.create();
      for (Ref ref : refs(toChange)) {
        try {
          revisions.forcePut(
              rp.getRevWalk().parseCommit(ref.getObjectId()),
              PatchSet.Id.fromRef(ref.getName()));
        } catch (IOException err) {
          log.warn(String.format(
              "Project %s contains invalid change ref %s",
              project.getName(), ref.getName()), err);
        }
      }
    }

    boolean validate(boolean autoClose) throws IOException {
      if (!autoClose && inputCommand.getResult() != NOT_ATTEMPTED) {
        return false;
      } else if (change == null) {
        reject(inputCommand, "change " + ontoChange + " not found");
        return false;
      }

      priorPatchSet = change.currentPatchSetId();
      if (!revisions.containsValue(priorPatchSet)) {
        reject(inputCommand, "change " + ontoChange + " missing revisions");
        return false;
      }

      RevCommit priorCommit = revisions.inverse().get(priorPatchSet);
      if (newCommit == priorCommit) {
        // Ignore requests to make the change its current state.
        skip = true;
        reject(inputCommand, "commit already exists (as current patchset)");
        return false;
      }

      changeCtl = projectControl.controlFor(change);
      if (!changeCtl.canAddPatchSet()) {
        reject(inputCommand, "cannot replace " + ontoChange);
        return false;
      } else if (change.getStatus().isClosed()) {
        reject(inputCommand, "change " + ontoChange + " closed");
        return false;
      } else if (revisions.containsKey(newCommit)) {
        reject(inputCommand, "commit already exists (in the change)");
        return false;
      }

      for (final Ref r : rp.getRepository().getRefDatabase()
          .getRefs("refs/changes").values()) {
        if (r.getObjectId().equals(newCommit)) {
          reject(inputCommand, "commit already exists (in the project)");
          return false;
        }
      }

      for (RevCommit prior : revisions.keySet()) {
        // Don't allow a change to directly depend upon itself. This is a
        // very common error due to users making a new commit rather than
        // amending when trying to address review comments.
        if (rp.getRevWalk().isMergedInto(prior, newCommit)) {
          reject(inputCommand, "squash commits first");
          return false;
        }
      }

      rp.getRevWalk().parseBody(newCommit);
      if (!validCommit(changeCtl.getRefControl(), inputCommand, newCommit)) {
        return false;
      }
      rp.getRevWalk().parseBody(priorCommit);

      // Don't allow the same tree if the commit message is unmodified
      // or no parents were updated (rebase), else warn that only part
      // of the commit was modified.
      if (newCommit.getTree() == priorCommit.getTree()) {
        final boolean messageEq =
            eq(newCommit.getFullMessage(), priorCommit.getFullMessage());
        final boolean parentsEq = parentsEqual(newCommit, priorCommit);
        final boolean authorEq = authorEqual(newCommit, priorCommit);
        final ObjectReader reader = rp.getRevWalk().getObjectReader();

        if (messageEq && parentsEq && authorEq && !autoClose) {
          addMessage(String.format(
              "(W) No changes between prior commit %s and new commit %s",
              reader.abbreviate(priorCommit).name(),
              reader.abbreviate(newCommit).name()));
          reject(inputCommand, "no changes made");
          return false;
        } else {
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

      PatchSet.Id id =
          ChangeUtil.nextPatchSetId(allRefs, change.currentPatchSetId());
      newPatchSet = new PatchSet(id);
      newPatchSet.setCreatedOn(TimeUtil.nowTs());
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

    CheckedFuture<PatchSet.Id, InsertException> insertPatchSet()
        throws IOException {
      rp.getRevWalk().parseBody(newCommit);

      final Thread caller = Thread.currentThread();
      ListenableFuture<PatchSet.Id> future = changeUpdateExector.submit(
          requestScopePropagator.wrap(new Callable<PatchSet.Id>() {
        @Override
        public PatchSet.Id call() throws OrmException, IOException, NoSuchChangeException {
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
      return Futures.makeChecked(future, INSERT_EXCEPTION);
    }

    private ChangeMessage newChangeMessage(ReviewDb db) throws OrmException {
      msg =
          new ChangeMessage(new ChangeMessage.Key(change.getId(), ChangeUtil
              .messageUUID(db)), currentUser.getAccountId(), newPatchSet.getCreatedOn(),
              newPatchSet.getId());
      RevCommit priorCommit = revisions.inverse().get(priorPatchSet);
      ChangeKind changeKind = changeKindCache.getChangeKind(
          projectControl.getProjectState(), repo, priorCommit, newCommit);
      String message = "Uploaded patch set " + newPatchSet.getPatchSetId();
      switch (changeKind) {
        case TRIVIAL_REBASE:
          message += ": Patch Set " + priorPatchSet.get() + " was rebased";
          break;
        case NO_CODE_CHANGE:
          message += ": Commit message was updated";
          break;
        case REWORK:
        default:
          break;
      }
      msg.setMessage(message + ".");
      return msg;
    }

    PatchSet.Id insertPatchSet(ReviewDb db) throws OrmException, IOException {
      final Account.Id me = currentUser.getAccountId();
      final List<FooterLine> footerLines = newCommit.getFooterLines();
      final MailRecipients recipients = new MailRecipients();
      Map<String, Short> approvals = new HashMap<>();
      if (magicBranch != null) {
        recipients.add(magicBranch.getMailRecipients());
        approvals = magicBranch.getLabels();
      }
      recipients.add(getRecipientsFromFooters(accountResolver, newPatchSet, footerLines));
      recipients.remove(me);

      ChangeUpdate update = updateFactory.create(changeCtl, newPatchSet.getCreatedOn());
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

        ChangeData cd = changeDataFactory.create(db, changeCtl);
        MailRecipients oldRecipients =
            getRecipientsFromReviewers(cd.reviewers());
        approvalCopier.copy(db, changeCtl, newPatchSet);
        approvalsUtil.addReviewers(db, update, labelTypes, change, newPatchSet,
            info, recipients.getReviewers(), oldRecipients.getAll());
        approvalsUtil.addApprovals(db, update, labelTypes, newPatchSet, info,
            change, changeCtl, approvals);
        recipients.add(oldRecipients);

        cmUtil.addChangeMessage(db, update, newChangeMessage(db));

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

                  final List<String> idList = newCommit.getFooterLines(CHANGE_ID);
                  if (idList.isEmpty()) {
                    change.setKey(new Change.Key("I" + newCommit.name()));
                  } else {
                    change.setKey(new Change.Key(idList.get(idList.size() - 1).trim()));
                  }

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
      update.commit();

      if (mergedIntoRef != null) {
        // Change was already submitted to a branch, close it.
        //
        markChangeMergedByPush(db, this, changeCtl);
      }

      if (cmd.getResult() == NOT_ATTEMPTED) {
        cmd.execute(rp);
      }
      CheckedFuture<?, IOException> f = mergeabilityChecker.newCheck()
          .addChange(change)
          .reindex()
          .runAsync();
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
      f.checkedGet();

      if (magicBranch != null && magicBranch.isSubmit()) {
        submit(changeCtl, newPatchSet);
      }

      return newPatchSet.getId();
    }
  }

  private List<Ref> refs(Change.Id changeId) {
    if (refsByChange == null) {
      int estRefsPerChange = 4;
      refsByChange = ArrayListMultimap.create(
          allRefs.size() / estRefsPerChange,
          estRefsPerChange);
      for (Ref ref : allRefs.values()) {
        if (ref.getObjectId() != null && PatchSet.isRef(ref.getName())) {
          refsByChange.put(Change.Id.fromRef(ref.getName()), ref);
        }
      }
    }
    return refsByChange.get(changeId);
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
      final Map<String, Ref> all = repo.getRefDatabase().getRefs(ALL);
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
        && !RefNames.REFS_CONFIG.equals(ctl.getRefName())
        && !(MagicBranch.isMagicBranch(cmd.getRefName())
            || NEW_PATCHSET.matcher(cmd.getRefName()).matches())) {
      return;
    }

    boolean defaultName = Strings.isNullOrEmpty(currentUser.getAccount().getFullName());
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

        if (defaultName && currentUser.getEmailAddresses().contains(
              c.getCommitterIdent().getEmailAddress())) {
          try {
            Account a = db.accounts().get(currentUser.getAccountId());
            if (a != null && Strings.isNullOrEmpty(a.getFullName())) {
              a.setFullName(c.getCommitterIdent().getName());
              db.accounts().update(Collections.singleton(a));
              currentUser.getAccount().setFullName(a.getFullName());
              accountCache.evict(a.getId());
            }
          } catch (OrmException e) {
            log.warn("Cannot default full_name", e);
          } finally {
            defaultName = false;
          }
        }
      }
    } catch (IOException err) {
      cmd.setResult(REJECTED_MISSING_OBJECT);
      log.error("Invalid pack upload; one or more objects weren't sent", err);
    }
  }

  private boolean validCommit(final RefControl ctl, final ReceiveCommand cmd,
      final RevCommit c) throws MissingObjectException, IOException {

    if (validCommits.contains(c)) {
      return true;
    }

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
    validCommits.add(c);
    return true;
  }

  private void autoCloseChanges(final ReceiveCommand cmd) throws NoSuchChangeException {
    final RevWalk rw = rp.getRevWalk();
    try {
      rw.reset();
      rw.markStart(rw.parseCommit(cmd.getNewId()));
      if (!ObjectId.zeroId().equals(cmd.getOldId())) {
        rw.markUninteresting(rw.parseCommit(cmd.getOldId()));
      }

      final SetMultimap<ObjectId, Ref> byCommit = changeRefsById();
      final Map<Change.Key, Change.Id> byKey = openChangesByKey(
          new Branch.NameKey(project.getNameKey(), cmd.getRefName()));
      final List<ReplaceRequest> toClose = new ArrayList<>();
      RevCommit c;
      while ((c = rw.next()) != null) {
        final Set<Ref> refs = byCommit.get(c.copy());
        for (Ref ref : refs) {
          if (ref != null) {
            rw.parseBody(c);
            Change.Key closedChange =
                closeChange(cmd, PatchSet.Id.fromRef(ref.getName()), c);
            closeProgress.update(1);
            if (closedChange != null) {
              byKey.remove(closedChange);
            }
          }
        }

        rw.parseBody(c);
        for (final String changeId : c.getFooterLines(CHANGE_ID)) {
          final Change.Id onto = byKey.get(new Change.Key(changeId.trim()));
          if (onto != null) {
            final ReplaceRequest req = new ReplaceRequest(onto, c, cmd, false);
            req.change = db.changes().get(onto);
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
    } catch (InsertException e) {
      log.error("Can't insert patchset", e);
    } catch (IOException e) {
      log.error("Can't scan for changes to close", e);
    } catch (OrmException e) {
      log.error("Can't scan for changes to close", e);
    } catch (SubmoduleException e) {
      log.error("Can't complete git links check", e);
    }
  }

  private Change.Key closeChange(final ReceiveCommand cmd, final PatchSet.Id psi,
      final RevCommit commit) throws OrmException, IOException {
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
    result.changeCtl = projectControl.controlFor(change);
    result.newPatchSet = ps;
    result.info = patchSetInfoFactory.get(commit, psi);
    result.mergedIntoRef = refName;
    markChangeMergedByPush(db, result, result.changeCtl);
    hooks.doChangeMergedHook(
        change, currentUser.getAccount(), result.newPatchSet, db);
    sendMergedEmail(result);
    return change.getKey();
  }

  private SetMultimap<ObjectId, Ref> changeRefsById() throws IOException {
    if (refsById == null) {
      refsById =  HashMultimap.create();
      for (Ref r : repo.getRefDatabase().getRefs(REFS_CHANGES).values()) {
        if (PatchSet.isRef(r.getName())) {
          refsById.put(r.getObjectId(), r);
        }
      }
    }
    return refsById;
  }

  private Map<Change.Key, Change.Id> openChangesByKey(Branch.NameKey branch)
      throws OrmException {
    final Map<Change.Key, Change.Id> r = new HashMap<>();
    for (Change c : db.changes().byBranchOpenAll(branch)) {
      r.put(c.getKey(), c.getId());
    }
    return r;
  }

  private void markChangeMergedByPush(ReviewDb db, final ReplaceRequest result,
      ChangeControl control) throws OrmException, IOException {
    Change.Id id = result.change.getId();
    db.changes().beginTransaction(id);
    Change change;

    ChangeUpdate update;
    try {
      change = db.changes().atomicUpdate(id, new AtomicUpdate<Change>() {
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
      String mergedIntoRef = result.mergedIntoRef;

      StringBuilder msgBuf = new StringBuilder();
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
      ChangeMessage msg = new ChangeMessage(
          new ChangeMessage.Key(id, ChangeUtil.messageUUID(db)),
          currentUser.getAccountId(), change.getLastUpdatedOn(),
          result.info.getKey());
      msg.setMessage(msgBuf.toString());

      update = updateFactory.create(control, change.getLastUpdatedOn());

      cmUtil.addChangeMessage(db, update, msg);
      db.commit();
    } finally {
      db.rollback();
    }
    indexer.index(db, change);
    update.commit();
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
    return cmd.getRefName().equals(RefNames.REFS_CONFIG);
  }
}
