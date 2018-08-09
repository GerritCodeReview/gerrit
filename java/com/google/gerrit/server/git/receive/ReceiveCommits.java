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

package com.google.gerrit.server.git.receive;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.common.FooterConstants.CHANGE_ID;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CHANGES;
import static com.google.gerrit.reviewdb.client.RefNames.isConfigRef;
import static com.google.gerrit.server.change.HashtagsUtil.cleanupHashtag;
import static com.google.gerrit.server.git.MultiProgressMonitor.UNKNOWN;
import static com.google.gerrit.server.git.receive.ReceiveConstants.COMMAND_REJECTION_MESSAGE_FOOTER;
import static com.google.gerrit.server.git.receive.ReceiveConstants.ONLY_CHANGE_OWNER_OR_PROJECT_OWNER_CAN_MODIFY_WIP;
import static com.google.gerrit.server.git.receive.ReceiveConstants.PUSH_OPTION_SKIP_VALIDATION;
import static com.google.gerrit.server.git.receive.ReceiveConstants.SAME_CHANGE_ID_IN_MULTIPLE_CHANGES;
import static com.google.gerrit.server.git.validators.CommitValidators.NEW_PATCHSET_PATTERN;
import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromFooters;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.OK;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_MISSING_OBJECT;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import com.google.auto.value.AutoValue;
import com.google.common.base.Function;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.projects.ProjectConfigEntryType;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicMap.Entry;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.PatchSetInfo;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.client.RevId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CreateGroupPermissionSyncer;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.ServerInitiated;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.AccountState;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.SetHashtagsOp;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.BanCommit;
import com.google.gerrit.server.git.ChangeReportFormatter;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.git.MergedByPushOp;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.git.ReceivePackInitializer;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.git.validators.RefOperationValidationException;
import com.google.gerrit.server.git.validators.RefOperationValidators;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.mail.MailUtil.MailRecipients;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.notedb.ReviewerStateInternal;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.PermissionDeniedException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.CreateRefControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.submit.MergeOp;
import com.google.gerrit.server.submit.MergeOpRepoManager;
import com.google.gerrit.server.submit.SubmoduleException;
import com.google.gerrit.server.submit.SubmoduleOp;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.update.RepoOnlyOp;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryHelper.Action;
import com.google.gerrit.server.update.RetryHelper.ActionType;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.MagicBranch;
import com.google.gerrit.server.util.RequestId;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.util.cli.CmdLineParser;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import com.google.inject.util.Providers;
import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.notes.NoteMap;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.eclipse.jgit.transport.ReceiveCommand;
import org.eclipse.jgit.transport.ReceiveCommand.Result;
import org.eclipse.jgit.transport.ReceivePack;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

/**
 * Receives change upload using the Git receive-pack protocol.
 *
 * <p>Conceptually, most use of Gerrit is a push of some commits to refs/for/BRANCH. However, the
 * receive-pack protocol that this is based on allows multiple ref updates to be processed at once.
 */
class ReceiveCommits {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private enum ReceiveError {
    CONFIG_UPDATE(
        "You are not allowed to perform this operation.\n"
            + "Configuration changes can only be pushed by project owners\n"
            + "who also have 'Push' rights on "
            + RefNames.REFS_CONFIG),
    UPDATE(
        "You are not allowed to perform this operation.\n"
            + "To push into this reference you need 'Push' rights."),
    DELETE(
        "You need 'Delete Reference' rights or 'Push' rights with the \n"
            + "'Force Push' flag set to delete references."),
    DELETE_CHANGES("Cannot delete from '" + REFS_CHANGES + "'"),
    CODE_REVIEW(
        "You need 'Push' rights to upload code review requests.\n"
            + "Verify that you are pushing to the right branch.");

    private final String value;

    ReceiveError(String value) {
      this.value = value;
    }

    String get() {
      return value;
    }
  }

  private static final String CANNOT_DELETE_CHANGES = "Cannot delete from '" + REFS_CHANGES + "'";
  private static final String CANNOT_DELETE_CONFIG =
      "Cannot delete project configuration from '" + RefNames.REFS_CONFIG + "'";

  interface Factory {
    ReceiveCommits create(
        ProjectState projectState,
        IdentifiedUser user,
        ReceivePack receivePack,
        AllRefsWatcher allRefsWatcher,
        SetMultimap<ReviewerStateInternal, Account.Id> extraReviewers,
        MessageSender messageSender);
  }

  private class ReceivePackMessageSender implements MessageSender {
    @Override
    public void sendMessage(String what) {
      receivePack.sendMessage(what);
    }

    @Override
    public void sendError(String what) {
      receivePack.sendError(what);
    }

    @Override
    public void sendBytes(byte[] what) {
      sendBytes(what, 0, what.length);
    }

    @Override
    public void sendBytes(byte[] what, int off, int len) {
      try {
        receivePack.getMessageOutputStream().write(what, off, len);
      } catch (IOException e) {
        // Ignore write failures (matching JGit behavior).
      }
    }

    @Override
    public void flush() {
      try {
        receivePack.getMessageOutputStream().flush();
      } catch (IOException e) {
        // Ignore write failures (matching JGit behavior).
      }
    }
  }

  private static final Function<Exception, RestApiException> INSERT_EXCEPTION =
      new Function<Exception, RestApiException>() {
        @Override
        public RestApiException apply(Exception input) {
          if (input instanceof RestApiException) {
            return (RestApiException) input;
          } else if ((input instanceof ExecutionException)
              && (input.getCause() instanceof RestApiException)) {
            return (RestApiException) input.getCause();
          }
          return new RestApiException("Error inserting change/patchset", input);
        }
      };

  // ReceiveCommits has a lot of fields, sorry. Here and in the constructor they are split up
  // somewhat, and kept sorted lexicographically within sections, except where later assignments
  // depend on previous ones.

  // Injected fields.
  private final AccountResolver accountResolver;
  private final Provider<AccountsUpdate> accountsUpdateProvider;
  private final AllProjectsName allProjectsName;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final ChangeEditUtil editUtil;
  private final ChangeIndexer indexer;
  private final ChangeInserter.Factory changeInserterFactory;
  private final ChangeNotes.Factory notesFactory;
  private final ChangeReportFormatter changeFormatter;
  private final CmdLineParser.Factory optionParserFactory;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final CreateGroupPermissionSyncer createGroupPermissionSyncer;
  private final CreateRefControl createRefControl;
  private final DynamicMap<ProjectConfigEntry> pluginConfigEntries;
  private final DynamicSet<ReceivePackInitializer> initializers;
  private final MergedByPushOp.Factory mergedByPushOpFactory;
  private final NotesMigration notesMigration;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final PatchSetUtil psUtil;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Provider<MergeOp> mergeOpProvider;
  private final Provider<MergeOpRepoManager> ormProvider;
  private final ReceiveConfig receiveConfig;
  private final RefOperationValidators.Factory refValidatorsFactory;
  private final ReplaceOp.Factory replaceOpFactory;
  private final RetryHelper retryHelper;
  private final RequestScopePropagator requestScopePropagator;
  private final ReviewDb db;
  private final Sequences seq;
  private final SetHashtagsOp.Factory hashtagsFactory;
  private final SshInfo sshInfo;
  private final SubmoduleOp.Factory subOpFactory;
  private final TagCache tagCache;

  // Assisted injected fields.
  private final AllRefsWatcher allRefsWatcher;
  private final ImmutableSetMultimap<ReviewerStateInternal, Account.Id> extraReviewers;
  private final ProjectState projectState;
  private final IdentifiedUser user;
  private final ReceivePack receivePack;

  // Immutable fields derived from constructor arguments.
  private final boolean allowPushToRefsChanges;
  private final LabelTypes labelTypes;
  private final NoteMap rejectCommits;
  private final PermissionBackend.ForProject permissions;
  private final Project project;
  private final Repository repo;

  // Collections populated during processing.
  private final List<UpdateGroupsRequest> updateGroups;
  private final List<ValidationMessage> messages;
  /** Multimap of error text to refnames that produced that error. */
  private final ListMultimap<String, String> errors;

  private final ListMultimap<String, String> pushOptions;
  private final Map<Change.Id, ReplaceRequest> replaceByChange;

  @AutoValue
  protected abstract static class ValidCommitKey {
    abstract ObjectId getObjectId();

    abstract Branch.NameKey getBranch();
  }

  private final Set<ValidCommitKey> validCommits;

  // Collections lazily populated during processing.
  private ListMultimap<Change.Id, Ref> refsByChange;
  private ListMultimap<ObjectId, Ref> refsById;

  // Other settings populated during processing.
  private MagicBranchInput magicBranch;
  private boolean newChangeForAllNotInTarget;
  private String setFullNameTo;
  private boolean setChangeAsPrivate;
  private Optional<NoteDbPushOption> noteDbPushOption;
  private Optional<Boolean> tracePushOption;

  // Handles for outputting back over the wire to the end user.
  private Task newProgress;
  private Task replaceProgress;
  private Task closeProgress;
  private MessageSender messageSender;

  @Inject
  ReceiveCommits(
      AccountResolver accountResolver,
      @ServerInitiated Provider<AccountsUpdate> accountsUpdateProvider,
      AllProjectsName allProjectsName,
      BatchUpdate.Factory batchUpdateFactory,
      @GerritServerConfig Config cfg,
      ChangeEditUtil editUtil,
      ChangeIndexer indexer,
      ChangeInserter.Factory changeInserterFactory,
      ChangeNotes.Factory notesFactory,
      DynamicItem<ChangeReportFormatter> changeFormatterProvider,
      CmdLineParser.Factory optionParserFactory,
      CommitValidators.Factory commitValidatorsFactory,
      CreateGroupPermissionSyncer createGroupPermissionSyncer,
      CreateRefControl createRefControl,
      DynamicMap<ProjectConfigEntry> pluginConfigEntries,
      DynamicSet<ReceivePackInitializer> initializers,
      MergedByPushOp.Factory mergedByPushOpFactory,
      NotesMigration notesMigration,
      PatchSetInfoFactory patchSetInfoFactory,
      PatchSetUtil psUtil,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      Provider<InternalChangeQuery> queryProvider,
      Provider<MergeOp> mergeOpProvider,
      Provider<MergeOpRepoManager> ormProvider,
      ReceiveConfig receiveConfig,
      RefOperationValidators.Factory refValidatorsFactory,
      ReplaceOp.Factory replaceOpFactory,
      RetryHelper retryHelper,
      RequestScopePropagator requestScopePropagator,
      ReviewDb db,
      Sequences seq,
      SetHashtagsOp.Factory hashtagsFactory,
      SshInfo sshInfo,
      SubmoduleOp.Factory subOpFactory,
      TagCache tagCache,
      @Assisted ProjectState projectState,
      @Assisted IdentifiedUser user,
      @Assisted ReceivePack rp,
      @Assisted AllRefsWatcher allRefsWatcher,
      @Assisted SetMultimap<ReviewerStateInternal, Account.Id> extraReviewers,
      @Nullable @Assisted MessageSender messageSender)
      throws IOException {
    // Injected fields.
    this.accountResolver = accountResolver;
    this.accountsUpdateProvider = accountsUpdateProvider;
    this.allProjectsName = allProjectsName;
    this.batchUpdateFactory = batchUpdateFactory;
    this.changeFormatter = changeFormatterProvider.get();
    this.changeInserterFactory = changeInserterFactory;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.createRefControl = createRefControl;
    this.createGroupPermissionSyncer = createGroupPermissionSyncer;
    this.db = db;
    this.editUtil = editUtil;
    this.hashtagsFactory = hashtagsFactory;
    this.indexer = indexer;
    this.initializers = initializers;
    this.mergeOpProvider = mergeOpProvider;
    this.mergedByPushOpFactory = mergedByPushOpFactory;
    this.notesFactory = notesFactory;
    this.notesMigration = notesMigration;
    this.optionParserFactory = optionParserFactory;
    this.ormProvider = ormProvider;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.permissionBackend = permissionBackend;
    this.pluginConfigEntries = pluginConfigEntries;
    this.projectCache = projectCache;
    this.psUtil = psUtil;
    this.queryProvider = queryProvider;
    this.receiveConfig = receiveConfig;
    this.refValidatorsFactory = refValidatorsFactory;
    this.replaceOpFactory = replaceOpFactory;
    this.retryHelper = retryHelper;
    this.requestScopePropagator = requestScopePropagator;
    this.seq = seq;
    this.sshInfo = sshInfo;
    this.subOpFactory = subOpFactory;
    this.tagCache = tagCache;

    // Assisted injected fields.
    this.allRefsWatcher = allRefsWatcher;
    this.extraReviewers = ImmutableSetMultimap.copyOf(extraReviewers);
    this.projectState = projectState;
    this.user = user;
    this.receivePack = rp;

    // Immutable fields derived from constructor arguments.
    allowPushToRefsChanges = cfg.getBoolean("receive", "allowPushToRefsChanges", false);
    repo = rp.getRepository();
    project = projectState.getProject();
    labelTypes = projectState.getLabelTypes();
    permissions = permissionBackend.user(user).project(project.getNameKey());
    rejectCommits = BanCommit.loadRejectCommitsMap(rp.getRepository(), rp.getRevWalk());

    // Collections populated during processing.
    errors = MultimapBuilder.linkedHashKeys().arrayListValues().build();
    messages = new ArrayList<>();
    pushOptions = LinkedListMultimap.create();
    replaceByChange = new LinkedHashMap<>();
    updateGroups = new ArrayList<>();
    validCommits = new HashSet<>();

    // Other settings populated during processing.
    newChangeForAllNotInTarget =
        projectState.is(BooleanProjectConfig.CREATE_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET);

    // Handles for outputting back over the wire to the end user.
    this.messageSender = messageSender != null ? messageSender : new ReceivePackMessageSender();
  }

  void init() {
    for (ReceivePackInitializer i : initializers) {
      i.init(projectState.getNameKey(), receivePack);
    }
  }

  MessageSender getMessageSender() {
    return messageSender;
  }

  Project getProject() {
    return project;
  }

  private void addMessage(String message) {
    messages.add(new CommitValidationMessage(message, false));
  }

  void addError(String error) {
    messages.add(new CommitValidationMessage(error, true));
  }

  void sendMessages() {
    for (ValidationMessage m : messages) {
      if (m.isError()) {
        messageSender.sendError(m.getMessage());
      } else {
        messageSender.sendMessage(m.getMessage());
      }
    }
  }

  void processCommands(Collection<ReceiveCommand> commands, MultiProgressMonitor progress) {
    newProgress = progress.beginSubTask("new", UNKNOWN);
    replaceProgress = progress.beginSubTask("updated", UNKNOWN);
    closeProgress = progress.beginSubTask("closed", UNKNOWN);

    Task commandProgress = progress.beginSubTask("refs", UNKNOWN);
    commands = commands.stream().map(c -> wrapReceiveCommand(c, commandProgress)).collect(toList());
    processCommandsUnsafe(commands);
    for (ReceiveCommand cmd : commands) {
      if (cmd.getResult() == NOT_ATTEMPTED) {
        cmd.setResult(REJECTED_OTHER_REASON, "internal server error");
      }
    }
    commandProgress.end();
    progress.end();
  }

  // Process as many commands as possible, but may leave some commands in state NOT_ATTEMPTED.
  private void processCommandsUnsafe(Collection<ReceiveCommand> commands) {
    parsePushOptions();
    try (TraceContext traceContext =
        TraceContext.open()
            .addTag(RequestId.Type.RECEIVE_ID, RequestId.forProject(project.getNameKey()))) {
      if (tracePushOption.orElse(false)) {
        RequestId traceId = new RequestId();
        traceContext.forceLogging().addTag(RequestId.Type.TRACE_ID, traceId);
        addMessage(RequestId.Type.TRACE_ID.name() + ": " + traceId);
      }
      try {
        if (!projectState.getProject().getState().permitsWrite()) {
          for (ReceiveCommand cmd : commands) {
            reject(cmd, "prohibited by Gerrit: project state does not permit write");
          }
          return;
        }

        logger.atFine().log("Parsing %d commands", commands.size());

        List<ReceiveCommand> magicCommands = new ArrayList<>();
        List<ReceiveCommand> directPatchSetPushCommands = new ArrayList<>();
        List<ReceiveCommand> regularCommands = new ArrayList<>();

        for (ReceiveCommand cmd : commands) {
          if (MagicBranch.isMagicBranch(cmd.getRefName())) {
            magicCommands.add(cmd);
          } else if (isDirectChangesPush(cmd.getRefName())) {
            directPatchSetPushCommands.add(cmd);
          } else {
            regularCommands.add(cmd);
          }
        }

        int commandTypes =
            (magicCommands.isEmpty() ? 0 : 1)
                + (directPatchSetPushCommands.isEmpty() ? 0 : 1)
                + (regularCommands.isEmpty() ? 0 : 1);

        if (commandTypes > 1) {
          for (ReceiveCommand cmd : commands) {
            if (cmd.getResult() == NOT_ATTEMPTED) {
              cmd.setResult(REJECTED_OTHER_REASON, "cannot combine normal pushes and magic pushes");
            }
          }
          return;
        }

        if (!regularCommands.isEmpty()) {
          handleRegularCommands(regularCommands);
        }

        for (ReceiveCommand cmd : directPatchSetPushCommands) {
          parseDirectChangesPush(cmd);
        }

        boolean first = true;
        for (ReceiveCommand cmd : magicCommands) {
          if (first) {
            parseMagicBranch(cmd);
            first = false;
          } else {
            reject(cmd, "duplicate request");
          }
        }
      } catch (PermissionBackendException | NoSuchProjectException | IOException err) {
        logger.atSevere().withCause(err).log("Failed to process refs in %s", project.getName());
        return;
      }

      List<CreateRequest> newChanges = Collections.emptyList();
      if (magicBranch != null && magicBranch.cmd.getResult() == NOT_ATTEMPTED) {
        newChanges = selectNewAndReplacedChangesFromMagicBranch();
      }
      preparePatchSetsForReplace(newChanges);
      insertChangesAndPatchSets(newChanges);
      newProgress.end();
      replaceProgress.end();

      if (!errors.isEmpty()) {
        logger.atFine().log("Handling error conditions: %s", errors.keySet());
        for (String error : errors.keySet()) {
          receivePack.sendMessage("error: " + buildError(error, errors.get(error)));
        }
        receivePack.sendMessage(String.format("User: %s", user.getLoggableName()));
        receivePack.sendMessage(COMMAND_REJECTION_MESSAGE_FOOTER);
      }

      // Update account info with details discovered during commit walking.
      updateAccountInfo();

      closeProgress.end();
      reportMessages(newChanges);
    }
  }

  private void handleRegularCommands(List<ReceiveCommand> cmds)
      throws PermissionBackendException, IOException, NoSuchProjectException {
    for (ReceiveCommand cmd : cmds) {
      parseRegularCommand(cmd);
    }

    try (BatchUpdate bu =
            batchUpdateFactory.create(
                db, project.getNameKey(), user.materializedCopy(), TimeUtil.nowTs());
        ObjectInserter ins = repo.newObjectInserter();
        ObjectReader reader = ins.newReader();
        RevWalk rw = new RevWalk(reader)) {
      bu.setRepository(repo, rw, ins).updateChangesInParallel();
      bu.setRefLogMessage("push");

      int added = 0;
      for (ReceiveCommand cmd : cmds) {
        if (cmd.getResult() == NOT_ATTEMPTED) {
          bu.addRepoOnlyOp(new UpdateOneRefOp(cmd));
          added++;
        }
      }
      logger.atFine().log("Added %d additional ref updates", added);
      bu.execute();
    } catch (UpdateException | RestApiException e) {
      for (ReceiveCommand cmd : cmds) {
        if (cmd.getResult() == NOT_ATTEMPTED) {
          cmd.setResult(REJECTED_OTHER_REASON, "internal server error");
        }
      }
      logger.atFine().withCause(e).log("update failed:");
    }

    Set<Branch.NameKey> branches = new HashSet<>();
    for (ReceiveCommand c : cmds) {
      // Most post-update steps should happen in UpdateOneRefOp#postUpdate. The only steps that
      // should happen in this loop are things that can't happen within one BatchUpdate because
      // they involve kicking off an additional BatchUpdate.
      if (c.getResult() != OK) {
        continue;
      }
      if (isHead(c) || isConfig(c)) {
        switch (c.getType()) {
          case CREATE:
          case UPDATE:
          case UPDATE_NONFASTFORWARD:
            autoCloseChanges(c);
            branches.add(new Branch.NameKey(project.getNameKey(), c.getRefName()));
            break;

          case DELETE:
            break;
        }
      }
    }

    // Update superproject gitlinks if required.
    if (!branches.isEmpty()) {
      try (MergeOpRepoManager orm = ormProvider.get()) {
        orm.setContext(db, TimeUtil.nowTs(), user);
        SubmoduleOp op = subOpFactory.create(branches, orm);
        op.updateSuperProjects();
      } catch (SubmoduleException e) {
        logger.atSevere().withCause(e).log("Can't update the superprojects");
      }
    }
  }

  private void reportMessages(List<CreateRequest> newChanges) {
    List<CreateRequest> created =
        newChanges.stream().filter(r -> r.change != null).collect(toList());
    if (!created.isEmpty()) {
      addMessage("");
      addMessage("New Changes:");
      for (CreateRequest c : created) {
        addMessage(
            changeFormatter.newChange(
                ChangeReportFormatter.Input.builder().setChange(c.change).build()));
      }
      addMessage("");
    }

    List<ReplaceRequest> updated =
        replaceByChange
            .values()
            .stream()
            .filter(r -> r.inputCommand.getResult() == OK)
            .sorted(comparingInt(r -> r.notes.getChangeId().get()))
            .collect(toList());
    if (!updated.isEmpty()) {
      addMessage("");
      addMessage("Updated Changes:");
      boolean edit = magicBranch != null && (magicBranch.edit || magicBranch.draft);
      Boolean isPrivate = null;
      Boolean wip = null;
      if (magicBranch != null) {
        if (magicBranch.isPrivate) {
          isPrivate = true;
        } else if (magicBranch.removePrivate) {
          isPrivate = false;
        }
        if (magicBranch.workInProgress) {
          wip = true;
        } else if (magicBranch.ready) {
          wip = false;
        }
      }
      for (ReplaceRequest u : updated) {
        String subject;
        if (edit) {
          try {
            subject = receivePack.getRevWalk().parseCommit(u.newCommitId).getShortMessage();
          } catch (IOException e) {
            // Log and fall back to original change subject
            logger.atWarning().withCause(e).log("failed to get subject for edit patch set");
            subject = u.notes.getChange().getSubject();
          }
        } else {
          subject = u.info.getSubject();
        }

        if (isPrivate == null) {
          isPrivate = u.notes.getChange().isPrivate();
        }
        if (wip == null) {
          wip = u.notes.getChange().isWorkInProgress();
        }

        ChangeReportFormatter.Input input =
            ChangeReportFormatter.Input.builder()
                .setChange(u.notes.getChange())
                .setSubject(subject)
                .setIsEdit(edit)
                .setIsPrivate(isPrivate)
                .setIsWorkInProgress(wip)
                .build();
        addMessage(changeFormatter.changeUpdated(input));
      }
      addMessage("");
    }

    // TODO(xchangcheng): remove after migrating tools which are using this magic branch.
    if (magicBranch != null && magicBranch.publish) {
      addMessage("Pushing to refs/publish/* is deprecated, use refs/for/* instead.");
    }
  }

  private void insertChangesAndPatchSets(List<CreateRequest> newChanges) {
    ReceiveCommand magicBranchCmd = magicBranch != null ? magicBranch.cmd : null;
    if (magicBranchCmd != null && magicBranchCmd.getResult() != NOT_ATTEMPTED) {
      logger.atWarning().log(
          "Skipping change updates on %s because ref update failed: %s %s",
          project.getName(),
          magicBranchCmd.getResult(),
          Strings.nullToEmpty(magicBranchCmd.getMessage()));
      return;
    }

    try (BatchUpdate bu =
            batchUpdateFactory.create(
                db, project.getNameKey(), user.materializedCopy(), TimeUtil.nowTs());
        ObjectInserter ins = repo.newObjectInserter();
        ObjectReader reader = ins.newReader();
        RevWalk rw = new RevWalk(reader)) {
      bu.setRepository(repo, rw, ins).updateChangesInParallel();
      bu.setRefLogMessage("push");

      logger.atFine().log("Adding %d replace requests", newChanges.size());
      for (ReplaceRequest replace : replaceByChange.values()) {
        replace.addOps(bu, replaceProgress);
      }

      logger.atFine().log("Adding %d create requests", newChanges.size());
      for (CreateRequest create : newChanges) {
        create.addOps(bu);
      }

      logger.atFine().log("Adding %d group update requests", newChanges.size());
      updateGroups.forEach(r -> r.addOps(bu));

      logger.atFine().log("Executing batch");
      try {
        bu.execute();
      } catch (UpdateException e) {
        throw INSERT_EXCEPTION.apply(e);
      }
      if (magicBranchCmd != null) {
        magicBranchCmd.setResult(OK);
      }
      for (ReplaceRequest replace : replaceByChange.values()) {
        String rejectMessage = replace.getRejectMessage();
        if (rejectMessage == null) {
          if (replace.inputCommand.getResult() == NOT_ATTEMPTED) {
            // Not necessarily the magic branch, so need to set OK on the original value.
            replace.inputCommand.setResult(OK);
          }
        } else {
          logger.atFine().log("Rejecting due to message from ReplaceOp");
          reject(replace.inputCommand, rejectMessage);
        }
      }

    } catch (ResourceConflictException e) {
      addError(e.getMessage());
      reject(magicBranchCmd, "conflict");
    } catch (RestApiException | IOException err) {
      logger.atSevere().withCause(err).log(
          "Can't insert change/patch set for %s", project.getName());
      reject(magicBranchCmd, "internal server error: " + err.getMessage());
    }

    if (magicBranch != null && magicBranch.submit) {
      try {
        submit(newChanges, replaceByChange.values());
      } catch (ResourceConflictException e) {
        addError(e.getMessage());
        reject(magicBranchCmd, "conflict");
      } catch (RestApiException
          | OrmException
          | UpdateException
          | IOException
          | ConfigInvalidException
          | PermissionBackendException e) {
        logger.atSevere().withCause(e).log("Error submitting changes to %s", project.getName());
        reject(magicBranchCmd, "error during submit");
      }
    }
  }

  private String buildError(String error, List<String> branches) {
    StringBuilder sb = new StringBuilder();
    if (branches.size() == 1) {
      sb.append("branch ").append(branches.get(0)).append(":\n");
      sb.append(error);
      return sb.toString();
    }
    sb.append("branches ").append(Joiner.on(", ").join(branches));
    return sb.append(":\n").append(error).toString();
  }

  /** Parses push options specified as "git push -o OPTION" */
  private void parsePushOptions() {
    List<String> optionList = receivePack.getPushOptions();
    if (optionList != null) {
      for (String option : optionList) {
        int e = option.indexOf('=');
        if (e > 0) {
          pushOptions.put(option.substring(0, e), option.substring(e + 1));
        } else {
          pushOptions.put(option, "");
        }
      }
    }

    List<String> noteDbValues = pushOptions.get("notedb");
    if (!noteDbValues.isEmpty()) {
      // These semantics for duplicates/errors are somewhat arbitrary and may not match e.g. the
      // CommandLineParser behavior used by MagicBranchInput.
      String value = noteDbValues.get(noteDbValues.size() - 1);
      noteDbPushOption = NoteDbPushOption.parse(value);
      if (!noteDbPushOption.isPresent()) {
        addError("Invalid value in -o " + NoteDbPushOption.OPTION_NAME + "=" + value);
      }
    } else {
      noteDbPushOption = Optional.of(NoteDbPushOption.DISALLOW);
    }

    List<String> traceValues = pushOptions.get("trace");
    if (!traceValues.isEmpty()) {
      String value = traceValues.get(traceValues.size() - 1);
      tracePushOption = Optional.of(value.isEmpty() || Boolean.parseBoolean(value));
    } else {
      tracePushOption = Optional.empty();
    }
  }

  private static boolean isDirectChangesPush(String refname) {
    Matcher m = NEW_PATCHSET_PATTERN.matcher(refname);
    return m.matches();
  }

  private void parseDirectChangesPush(ReceiveCommand cmd) {
    Matcher m = NEW_PATCHSET_PATTERN.matcher(cmd.getRefName());
    checkArgument(m.matches());

    if (allowPushToRefsChanges) {
      // The referenced change must exist and must still be open.
      Change.Id changeId = Change.Id.parse(m.group(1));
      parseReplaceCommand(cmd, changeId);
    } else {
      reject(cmd, "upload to refs/changes not allowed");
    }
  }

  // Wrap ReceiveCommand so the progress counter works automatically.
  private ReceiveCommand wrapReceiveCommand(ReceiveCommand cmd, Task progress) {
    String refname = cmd.getRefName();

    if (projectState.isAllUsers() && RefNames.REFS_USERS_SELF.equals(cmd.getRefName())) {
      refname = RefNames.refsUsers(user.getAccountId());
      logger.atFine().log("Swapping out command for %s to %s", RefNames.REFS_USERS_SELF, refname);
    }

    // We must also update the original, because callers may inspect it afterwards to decide if
    // the command went through or not.
    return new ReceiveCommand(cmd.getOldId(), cmd.getNewId(), refname, cmd.getType()) {
      @Override
      public void setResult(Result s, String m) {
        if (getResult() == NOT_ATTEMPTED) { // Only report the progress update once.
          progress.update(1);
        }
        // Counter intuitively, we don't check that results == NOT_ATTEMPTED here.
        // This is so submit-on-push can still reject the update if the change is created
        // successfully
        // (status OK) but the submit failed (merge failed: REJECTED_OTHER_REASON).
        super.setResult(s, m);
        cmd.setResult(s, m);
      }
    };
  }

  /*
   * Interpret a normal push.
   */
  private void parseRegularCommand(ReceiveCommand cmd)
      throws PermissionBackendException, NoSuchProjectException, IOException {
    if (cmd.getResult() != NOT_ATTEMPTED) {
      // Already rejected by the core receive process.
      logger.atFine().log("Already processed by core: %s %s", cmd.getResult(), cmd);
      return;
    }

    if (!Repository.isValidRefName(cmd.getRefName()) || cmd.getRefName().contains("//")) {
      reject(cmd, "not valid ref");
      return;
    }
    if (RefNames.isNoteDbMetaRef(cmd.getRefName())) {
      // Reject pushes to NoteDb refs without a special option and permission. Note that this
      // prohibition doesn't depend on NoteDb being enabled in any way, since all sites will
      // migrate to NoteDb eventually, and we don't want garbage data waiting there when the
      // migration finishes.
      logger.atFine().log(
          "%s NoteDb ref %s with %s=%s",
          cmd.getType(), cmd.getRefName(), NoteDbPushOption.OPTION_NAME, noteDbPushOption);
      if (!Optional.of(NoteDbPushOption.ALLOW).equals(noteDbPushOption)) {
        // Only reject this command, not the whole push. This supports the use case of "git clone
        // --mirror" followed by "git push --mirror", when the user doesn't really intend to clone
        // or mirror the NoteDb data; there is no single refspec that describes all refs *except*
        // NoteDb refs.
        reject(
            cmd,
            "NoteDb update requires -o "
                + NoteDbPushOption.OPTION_NAME
                + "="
                + NoteDbPushOption.ALLOW.value());
        return;
      }
      try {
        permissionBackend.user(user).check(GlobalPermission.ACCESS_DATABASE);
      } catch (AuthException e) {
        reject(cmd, "NoteDb update requires access database permission");
        return;
      }
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
        reject(cmd, "prohibited by Gerrit: unknown command type " + cmd.getType());
        return;
    }

    if (cmd.getResult() != NOT_ATTEMPTED) {
      return;
    }

    if (isConfig(cmd)) {
      logger.atFine().log("Processing %s command", cmd.getRefName());
      try {
        permissions.check(ProjectPermission.WRITE_CONFIG);
      } catch (AuthException e) {
        reject(
            cmd,
            String.format(
                "must be either project owner or have %s permission",
                ProjectPermission.WRITE_CONFIG.describeForException()));
        return;
      }

      switch (cmd.getType()) {
        case CREATE:
        case UPDATE:
        case UPDATE_NONFASTFORWARD:
          try {
            ProjectConfig cfg = new ProjectConfig(project.getNameKey());
            cfg.load(receivePack.getRevWalk(), cmd.getNewId());
            if (!cfg.getValidationErrors().isEmpty()) {
              addError("Invalid project configuration:");
              for (ValidationError err : cfg.getValidationErrors()) {
                addError("  " + err.getMessage());
              }
              reject(cmd, "invalid project configuration");
              logger.atSevere().log(
                  "User %s tried to push invalid project configuration %s for %s",
                  user.getLoggableName(), cmd.getNewId().name(), project.getName());
              return;
            }
            Project.NameKey newParent = cfg.getProject().getParent(allProjectsName);
            Project.NameKey oldParent = project.getParent(allProjectsName);
            if (oldParent == null) {
              // update of the 'All-Projects' project
              if (newParent != null) {
                reject(cmd, "invalid project configuration: root project cannot have parent");
                return;
              }
            } else {
              if (!oldParent.equals(newParent)) {
                try {
                  permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);
                } catch (AuthException e) {
                  reject(cmd, "invalid project configuration: only Gerrit admin can set parent");
                  return;
                }
              }

              if (projectCache.get(newParent) == null) {
                reject(cmd, "invalid project configuration: parent does not exist");
                return;
              }
            }

            for (Entry<ProjectConfigEntry> e : pluginConfigEntries) {
              PluginConfig pluginCfg = cfg.getPluginConfig(e.getPluginName());
              ProjectConfigEntry configEntry = e.getProvider().get();
              String value = pluginCfg.getString(e.getExportName());
              String oldValue =
                  projectState
                      .getConfig()
                      .getPluginConfig(e.getPluginName())
                      .getString(e.getExportName());
              if (configEntry.getType() == ProjectConfigEntryType.ARRAY) {
                oldValue =
                    Arrays.stream(
                            projectState
                                .getConfig()
                                .getPluginConfig(e.getPluginName())
                                .getStringList(e.getExportName()))
                        .collect(joining("\n"));
              }

              if ((value == null ? oldValue != null : !value.equals(oldValue))
                  && !configEntry.isEditable(projectState)) {
                reject(
                    cmd,
                    String.format(
                        "invalid project configuration: Not allowed to set parameter"
                            + " '%s' of plugin '%s' on project '%s'.",
                        e.getExportName(), e.getPluginName(), project.getName()));
                continue;
              }

              if (ProjectConfigEntryType.LIST.equals(configEntry.getType())
                  && value != null
                  && !configEntry.getPermittedValues().contains(value)) {
                reject(
                    cmd,
                    String.format(
                        "invalid project configuration: The value '%s' is "
                            + "not permitted for parameter '%s' of plugin '%s'.",
                        value, e.getExportName(), e.getPluginName()));
              }
            }
          } catch (Exception e) {
            reject(cmd, "invalid project configuration");
            logger.atSevere().withCause(e).log(
                "User %s tried to push invalid project configuration %s for %s",
                user.getLoggableName(), cmd.getNewId().name(), project.getName());
            return;
          }
          break;

        case DELETE:
          break;

        default:
          reject(
              cmd,
              "prohibited by Gerrit: don't know how to handle config update of type "
                  + cmd.getType());
          return;
      }
    }
  }

  private void parseCreate(ReceiveCommand cmd)
      throws PermissionBackendException, NoSuchProjectException, IOException {
    RevObject obj;
    try {
      obj = receivePack.getRevWalk().parseAny(cmd.getNewId());
    } catch (IOException err) {
      logger.atSevere().withCause(err).log(
          "Invalid object %s for %s creation", cmd.getNewId().name(), cmd.getRefName());
      reject(cmd, "invalid object");
      return;
    }
    logger.atFine().log("Creating %s", cmd);

    if (isHead(cmd) && !isCommit(cmd)) {
      return;
    }

    Branch.NameKey branch = new Branch.NameKey(project.getName(), cmd.getRefName());
    try {
      // Must pass explicit user instead of injecting a provider into CreateRefControl, since
      // Provider<CurrentUser> within ReceiveCommits will always return anonymous.
      createRefControl.checkCreateRef(Providers.of(user), receivePack.getRepository(), branch, obj);
    } catch (AuthException denied) {
      rejectProhibited(cmd, denied);
      return;
    } catch (ResourceConflictException denied) {
      reject(cmd, "prohibited by Gerrit: " + denied.getMessage());
      return;
    }

    if (validRefOperation(cmd)) {
      validateNewCommits(new Branch.NameKey(project.getNameKey(), cmd.getRefName()), cmd);
    }
  }

  private void parseUpdate(ReceiveCommand cmd) throws PermissionBackendException {
    logger.atFine().log("Updating %s", cmd);
    Optional<AuthException> err = checkRefPermission(cmd, RefPermission.UPDATE);
    if (!err.isPresent()) {
      if (isHead(cmd) && !isCommit(cmd)) {
        reject(cmd, "head must point to commit");
        return;
      }
      if (validRefOperation(cmd)) {
        validateNewCommits(new Branch.NameKey(project.getNameKey(), cmd.getRefName()), cmd);
      }
    } else {
      rejectProhibited(cmd, err.get());
    }
  }

  private boolean isCommit(ReceiveCommand cmd) {
    RevObject obj;
    try {
      obj = receivePack.getRevWalk().parseAny(cmd.getNewId());
    } catch (IOException err) {
      logger.atSevere().withCause(err).log(
          "Invalid object %s for %s", cmd.getNewId().name(), cmd.getRefName());
      reject(cmd, "invalid object");
      return false;
    }

    if (obj instanceof RevCommit) {
      return true;
    }
    reject(cmd, "not a commit");
    return false;
  }

  private void parseDelete(ReceiveCommand cmd) throws PermissionBackendException {
    logger.atFine().log("Deleting %s", cmd);
    if (cmd.getRefName().startsWith(REFS_CHANGES)) {
      errors.put(CANNOT_DELETE_CHANGES, cmd.getRefName());
      reject(cmd, "cannot delete changes");
    } else if (isConfigRef(cmd.getRefName())) {
      errors.put(CANNOT_DELETE_CONFIG, cmd.getRefName());
      reject(cmd, "cannot delete project configuration");
    }

    Optional<AuthException> err = checkRefPermission(cmd, RefPermission.DELETE);
    if (!err.isPresent()) {
      validRefOperation(cmd);

    } else {
      rejectProhibited(cmd, err.get());
    }
  }

  private void parseRewind(ReceiveCommand cmd) throws PermissionBackendException {
    RevCommit newObject;
    try {
      newObject = receivePack.getRevWalk().parseCommit(cmd.getNewId());
    } catch (IncorrectObjectTypeException notCommit) {
      newObject = null;
    } catch (IOException err) {
      logger.atSevere().withCause(err).log(
          "Invalid object %s for %s forced update", cmd.getNewId().name(), cmd.getRefName());
      reject(cmd, "invalid object");
      return;
    }
    logger.atFine().log("Rewinding %s", cmd);

    if (newObject != null) {
      validateNewCommits(new Branch.NameKey(project.getNameKey(), cmd.getRefName()), cmd);
      if (cmd.getResult() != NOT_ATTEMPTED) {
        return;
      }
    }

    Optional<AuthException> err = checkRefPermission(cmd, RefPermission.FORCE_UPDATE);
    if (!err.isPresent()) {
      validRefOperation(cmd);
    } else {
      rejectProhibited(cmd, err.get());
    }
  }

  private Optional<AuthException> checkRefPermission(ReceiveCommand cmd, RefPermission perm)
      throws PermissionBackendException {
    return checkRefPermission(permissions.ref(cmd.getRefName()), perm);
  }

  private Optional<AuthException> checkRefPermission(
      PermissionBackend.ForRef forRef, RefPermission perm) throws PermissionBackendException {
    try {
      forRef.check(perm);
      return Optional.empty();
    } catch (AuthException e) {
      return Optional.of(e);
    }
  }

  private void rejectProhibited(ReceiveCommand cmd, AuthException err) {
    err.getAdvice().ifPresent(a -> errors.put(a, cmd.getRefName()));
    reject(cmd, prohibited(err, cmd.getRefName()));
  }

  private static String prohibited(AuthException e, String alreadyDisplayedResource) {
    String msg = e.getMessage();
    if (e instanceof PermissionDeniedException) {
      PermissionDeniedException pde = (PermissionDeniedException) e;
      if (pde.getResource().isPresent()
          && pde.getResource().get().equals(alreadyDisplayedResource)) {
        // Avoid repeating resource name if exactly the given name was already displayed by the
        // generic git push machinery.
        msg = PermissionDeniedException.MESSAGE_PREFIX + pde.describePermission();
      }
    }
    return "prohibited by Gerrit: " + msg;
  }

  static class MagicBranchInput {
    private static final Splitter COMMAS = Splitter.on(',').omitEmptyStrings();

    final ReceiveCommand cmd;
    final LabelTypes labelTypes;
    final NotesMigration notesMigration;
    private final boolean defaultPublishComments;
    Branch.NameKey dest;
    PermissionBackend.ForRef perm;
    Set<Account.Id> reviewer = Sets.newLinkedHashSet();
    Set<Account.Id> cc = Sets.newLinkedHashSet();
    Map<String, Short> labels = new HashMap<>();
    String message;
    List<RevCommit> baseCommit;
    CmdLineParser cmdLineParser;
    Set<String> hashtags = new HashSet<>();

    @Option(name = "--trace", metaVar = "NAME", usage = "enable tracing")
    boolean trace;

    @Option(name = "--base", metaVar = "BASE", usage = "merge base of changes")
    List<ObjectId> base;

    @Option(name = "--topic", metaVar = "NAME", usage = "attach topic to changes")
    String topic;

    @Option(
        name = "--draft",
        usage =
            "Will be removed. Before that, this option will be mapped to '--private'"
                + "for new changes and '--edit' for existing changes")
    boolean draft;

    boolean publish;

    @Option(name = "--private", usage = "mark new/updated change as private")
    boolean isPrivate;

    @Option(name = "--remove-private", usage = "remove privacy flag from updated change")
    boolean removePrivate;

    @Option(
        name = "--wip",
        aliases = {"-work-in-progress"},
        usage = "mark change as work in progress")
    boolean workInProgress;

    @Option(name = "--ready", usage = "mark change as ready")
    boolean ready;

    @Option(
        name = "--edit",
        aliases = {"-e"},
        usage = "upload as change edit")
    boolean edit;

    @Option(name = "--submit", usage = "immediately submit the change")
    boolean submit;

    @Option(name = "--merged", usage = "create single change for a merged commit")
    boolean merged;

    @Option(name = "--publish-comments", usage = "publish all draft comments on updated changes")
    private boolean publishComments;

    @Option(
        name = "--no-publish-comments",
        aliases = {"--np"},
        usage = "do not publish draft comments")
    private boolean noPublishComments;

    @Option(
        name = "--notify",
        usage =
            "Notify handling that defines to whom email notifications "
                + "should be sent. Allowed values are NONE, OWNER, "
                + "OWNER_REVIEWERS, ALL. If not set, the default is ALL.")
    private NotifyHandling notify;

    @Option(name = "--notify-to", metaVar = "USER", usage = "user that should be notified")
    List<Account.Id> tos = new ArrayList<>();

    @Option(name = "--notify-cc", metaVar = "USER", usage = "user that should be CC'd")
    List<Account.Id> ccs = new ArrayList<>();

    @Option(name = "--notify-bcc", metaVar = "USER", usage = "user that should be BCC'd")
    List<Account.Id> bccs = new ArrayList<>();

    @Option(
        name = "--reviewer",
        aliases = {"-r"},
        metaVar = "EMAIL",
        usage = "add reviewer to changes")
    void reviewer(Account.Id id) {
      reviewer.add(id);
    }

    @Option(name = "--cc", metaVar = "EMAIL", usage = "notify user by CC")
    void cc(Account.Id id) {
      cc.add(id);
    }

    @Option(
        name = "--label",
        aliases = {"-l"},
        metaVar = "LABEL+VALUE",
        usage = "label(s) to assign (defaults to +1 if no value provided")
    void addLabel(String token) throws CmdLineException {
      LabelVote v = LabelVote.parse(token);
      try {
        LabelType.checkName(v.label());
        ApprovalsUtil.checkLabel(labelTypes, v.label(), v.value());
      } catch (BadRequestException e) {
        throw cmdLineParser.reject(e.getMessage());
      }
      labels.put(v.label(), v.value());
    }

    @Option(
        name = "--message",
        aliases = {"-m"},
        metaVar = "MESSAGE",
        usage = "Comment message to apply to the review")
    void addMessage(String token) {
      // Many characters have special meaning in the context of a git ref.
      //
      // Clients can use underscores to represent spaces.
      message = token.replace("_", " ");
      try {
        // Other characters can be represented using percent-encoding.
        message = URLDecoder.decode(message, UTF_8.name());
      } catch (IllegalArgumentException e) {
        // Ignore decoding errors; leave message as percent-encoded.
      } catch (UnsupportedEncodingException e) {
        // This shouldn't happen; surely URLDecoder recognizes UTF-8.
        throw new IllegalStateException(e);
      }
    }

    @Option(
        name = "--hashtag",
        aliases = {"-t"},
        metaVar = "HASHTAG",
        usage = "add hashtag to changes")
    void addHashtag(String token) throws CmdLineException {
      if (!notesMigration.readChanges()) {
        throw cmdLineParser.reject("cannot add hashtags; noteDb is disabled");
      }
      String hashtag = cleanupHashtag(token);
      if (!hashtag.isEmpty()) {
        hashtags.add(hashtag);
      }
      // TODO(dpursehouse): validate hashtags
    }

    MagicBranchInput(
        IdentifiedUser user,
        ReceiveCommand cmd,
        LabelTypes labelTypes,
        NotesMigration notesMigration) {
      this.cmd = cmd;
      this.draft = cmd.getRefName().startsWith(MagicBranch.NEW_DRAFT_CHANGE);
      this.publish = cmd.getRefName().startsWith(MagicBranch.NEW_PUBLISH_CHANGE);
      this.labelTypes = labelTypes;
      this.notesMigration = notesMigration;
      GeneralPreferencesInfo prefs = user.state().getGeneralPreferences();
      this.defaultPublishComments =
          prefs != null
              ? firstNonNull(user.state().getGeneralPreferences().publishCommentsOnPush, false)
              : false;
    }

    MailRecipients getMailRecipients() {
      return new MailRecipients(reviewer, cc);
    }

    ListMultimap<RecipientType, Account.Id> getAccountsToNotify() {
      ListMultimap<RecipientType, Account.Id> accountsToNotify =
          MultimapBuilder.hashKeys().arrayListValues().build();
      accountsToNotify.putAll(RecipientType.TO, tos);
      accountsToNotify.putAll(RecipientType.CC, ccs);
      accountsToNotify.putAll(RecipientType.BCC, bccs);
      return accountsToNotify;
    }

    boolean shouldPublishComments() {
      if (publishComments) {
        return true;
      } else if (noPublishComments) {
        return false;
      }
      return defaultPublishComments;
    }

    /**
     * returns the destination ref of the magic branch, and populates options in the cmdLineParser.
     */
    String parse(Repository repo, Set<String> refs, ListMultimap<String, String> pushOptions)
        throws CmdLineException {
      String ref = RefNames.fullName(MagicBranch.getDestBranchName(cmd.getRefName()));

      ListMultimap<String, String> options = LinkedListMultimap.create(pushOptions);

      // Process and lop off the "%OPTION" suffix.
      int optionStart = ref.indexOf('%');
      if (0 < optionStart) {
        for (String s : COMMAS.split(ref.substring(optionStart + 1))) {
          int e = s.indexOf('=');
          if (0 < e) {
            options.put(s.substring(0, e), s.substring(e + 1));
          } else {
            options.put(s, "");
          }
        }
        ref = ref.substring(0, optionStart);
      }

      if (!options.isEmpty()) {
        cmdLineParser.parseOptionMap(options);
      }

      // We accept refs/for/BRANCHNAME/TOPIC. Since we don't know
      // for sure where the branch ends and the topic starts, look
      // backward for a split that works. This behavior has not been
      // documented and should probably be deprecated.
      String head = readHEAD(repo);
      int split = ref.length();
      for (; ; ) {
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

    NotifyHandling getNotify() {
      if (notify != null) {
        return notify;
      }
      if (workInProgress) {
        return NotifyHandling.OWNER;
      }
      return NotifyHandling.ALL;
    }

    NotifyHandling getNotify(ChangeNotes notes) {
      if (notify != null) {
        return notify;
      }
      if (workInProgress || (!ready && notes.getChange().isWorkInProgress())) {
        return NotifyHandling.OWNER;
      }
      return NotifyHandling.ALL;
    }
  }

  /**
   * Parse the magic branch data (refs/for/BRANCH/OPTIONALTOPIC%OPTIONS) into the magicBranch
   * member.
   *
   * <p>Assumes we are handling a magic branch here.
   */
  private void parseMagicBranch(ReceiveCommand cmd) throws PermissionBackendException {
    logger.atFine().log("Found magic branch %s", cmd.getRefName());
    MagicBranchInput magicBranch = new MagicBranchInput(user, cmd, labelTypes, notesMigration);
    magicBranch.reviewer.addAll(extraReviewers.get(ReviewerStateInternal.REVIEWER));
    magicBranch.cc.addAll(extraReviewers.get(ReviewerStateInternal.CC));

    String ref;
    magicBranch.cmdLineParser = optionParserFactory.create(magicBranch);

    try {
      ref = magicBranch.parse(repo, receivePack.getAdvertisedRefs().keySet(), pushOptions);
    } catch (CmdLineException e) {
      if (!magicBranch.cmdLineParser.wasHelpRequestedByOption()) {
        logger.atFine().log("Invalid branch syntax");
        reject(cmd, e.getMessage());
        return;
      }
      ref = null; // never happens
    }

    if (magicBranch.topic != null && magicBranch.topic.length() > ChangeUtil.TOPIC_MAX_LENGTH) {
      reject(
          cmd, String.format("topic length exceeds the limit (%d)", ChangeUtil.TOPIC_MAX_LENGTH));
    }

    if (magicBranch.cmdLineParser.wasHelpRequestedByOption()) {
      StringWriter w = new StringWriter();
      w.write("\nHelp for refs/for/branch:\n\n");
      magicBranch.cmdLineParser.printUsage(w, null);
      addMessage(w.toString());
      reject(cmd, "see help");
      return;
    }
    if (projectState.isAllUsers() && RefNames.REFS_USERS_SELF.equals(ref)) {
      logger.atFine().log("Handling %s", RefNames.REFS_USERS_SELF);
      ref = RefNames.refsUsers(user.getAccountId());
    }
    if (!receivePack.getAdvertisedRefs().containsKey(ref)
        && !ref.equals(readHEAD(repo))
        && !ref.equals(RefNames.REFS_CONFIG)) {
      logger.atFine().log("Ref %s not found", ref);
      if (ref.startsWith(Constants.R_HEADS)) {
        String n = ref.substring(Constants.R_HEADS.length());
        reject(cmd, "branch " + n + " not found");
      } else {
        reject(cmd, ref + " not found");
      }
      return;
    }

    magicBranch.dest = new Branch.NameKey(project.getNameKey(), ref);
    magicBranch.perm = permissions.ref(ref);

    Optional<AuthException> err = checkRefPermission(magicBranch.perm, RefPermission.CREATE_CHANGE);
    if (err.isPresent()) {
      rejectProhibited(cmd, err.get());
      return;
    }
    if (!projectState.statePermitsWrite()) {
      reject(cmd, "project state does not permit write");
      return;
    }

    // TODO(davido): Remove legacy support for drafts magic branch option
    // after repo-tool supports private and work-in-progress changes.
    if (magicBranch.draft && !receiveConfig.allowDrafts) {
      errors.put(ReceiveError.CODE_REVIEW.get(), ref);
      reject(cmd, "draft workflow is disabled");
      return;
    }

    if (magicBranch.isPrivate && magicBranch.removePrivate) {
      reject(cmd, "the options 'private' and 'remove-private' are mutually exclusive");
      return;
    }

    boolean privateByDefault =
        projectCache.get(project.getNameKey()).is(BooleanProjectConfig.PRIVATE_BY_DEFAULT);
    setChangeAsPrivate =
        magicBranch.draft
            || magicBranch.isPrivate
            || (privateByDefault && !magicBranch.removePrivate);

    if (receiveConfig.disablePrivateChanges && setChangeAsPrivate) {
      reject(cmd, "private changes are disabled");
      return;
    }

    if (magicBranch.workInProgress && magicBranch.ready) {
      reject(cmd, "the options 'wip' and 'ready' are mutually exclusive");
      return;
    }
    if (magicBranch.publishComments && magicBranch.noPublishComments) {
      reject(
          cmd, "the options 'publish-comments' and 'no-publish-comments' are mutually exclusive");
      return;
    }

    if (magicBranch.submit) {
      err = checkRefPermission(magicBranch.perm, RefPermission.UPDATE_BY_SUBMIT);
      if (err.isPresent()) {
        rejectProhibited(cmd, err.get());
        return;
      }
    }

    RevWalk walk = receivePack.getRevWalk();
    RevCommit tip;
    try {
      tip = walk.parseCommit(magicBranch.cmd.getNewId());
      logger.atFine().log("Tip of push: %s", tip.name());
    } catch (IOException ex) {
      magicBranch.cmd.setResult(REJECTED_MISSING_OBJECT);
      logger.atSevere().withCause(ex).log("Invalid pack upload; one or more objects weren't sent");
      return;
    }

    String destBranch = magicBranch.dest.get();
    try {
      if (magicBranch.merged) {
        if (magicBranch.base != null) {
          reject(cmd, "cannot use merged with base");
          return;
        }
        RevCommit branchTip = readBranchTip(cmd, magicBranch.dest);
        if (branchTip == null) {
          return; // readBranchTip already rejected cmd.
        }
        if (!walk.isMergedInto(tip, branchTip)) {
          reject(cmd, "not merged into branch");
          return;
        }
      }

      // If tip is a merge commit, or the root commit or
      // if %base or %merged was specified, ignore newChangeForAllNotInTarget.
      if (tip.getParentCount() > 1
          || magicBranch.base != null
          || magicBranch.merged
          || tip.getParentCount() == 0) {
        logger.atFine().log("Forcing newChangeForAllNotInTarget = false");
        newChangeForAllNotInTarget = false;
      }

      if (magicBranch.base != null) {
        logger.atFine().log("Handling %%base: %s", magicBranch.base);
        magicBranch.baseCommit = Lists.newArrayListWithCapacity(magicBranch.base.size());
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
            logger.atWarning().withCause(e).log(
                "Project %s cannot read %s", project.getName(), id.name());
            reject(cmd, "internal server error");
            return;
          }
        }
      } else if (newChangeForAllNotInTarget) {
        RevCommit branchTip = readBranchTip(cmd, magicBranch.dest);
        if (branchTip == null) {
          return; // readBranchTip already rejected cmd.
        }
        magicBranch.baseCommit = Collections.singletonList(branchTip);
        logger.atFine().log("Set baseCommit = %s", magicBranch.baseCommit.get(0).name());
      }
    } catch (IOException ex) {
      logger.atWarning().withCause(ex).log(
          "Error walking to %s in project %s", destBranch, project.getName());
      reject(cmd, "internal server error");
      return;
    }

    if (validateConnected(magicBranch.cmd, magicBranch.dest, tip)) {
      this.magicBranch = magicBranch;
    }
  }

  // Validate that the new commits are connected with the target
  // branch.  If they aren't, we want to abort. We do this check by
  // looking to see if we can compute a merge base between the new
  // commits and the target branch head.
  private boolean validateConnected(ReceiveCommand cmd, Branch.NameKey dest, RevCommit tip) {
    RevWalk walk = receivePack.getRevWalk();
    try {
      Ref targetRef = receivePack.getAdvertisedRefs().get(dest.get());
      if (targetRef == null || targetRef.getObjectId() == null) {
        // The destination branch does not yet exist. Assume the
        // history being sent for review will start it and thus
        // is "connected" to the branch.
        logger.atFine().log("Branch is unborn");

        // This is not an error condition.
        return true;
      }

      RevCommit h = walk.parseCommit(targetRef.getObjectId());
      logger.atFine().log("Current branch tip: %s", h.name());
      RevFilter oldRevFilter = walk.getRevFilter();
      try {
        walk.reset();
        walk.setRevFilter(RevFilter.MERGE_BASE);
        walk.markStart(tip);
        walk.markStart(h);
        if (walk.next() == null) {
          reject(magicBranch.cmd, "no common ancestry");
          return false;
        }
      } finally {
        walk.reset();
        walk.setRevFilter(oldRevFilter);
      }
    } catch (IOException e) {
      magicBranch.cmd.setResult(REJECTED_MISSING_OBJECT);
      logger.atSevere().withCause(e).log("Invalid pack upload; one or more objects weren't sent");
      return false;
    }
    return true;
  }

  private static String readHEAD(Repository repo) {
    try {
      return repo.getFullBranch();
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Cannot read HEAD symref");
      return null;
    }
  }

  private RevCommit readBranchTip(ReceiveCommand cmd, Branch.NameKey branch) throws IOException {
    Ref r = allRefs().get(branch.get());
    if (r == null) {
      reject(cmd, branch.get() + " not found");
      return null;
    }
    return receivePack.getRevWalk().parseCommit(r.getObjectId());
  }

  // Handle an upload to refs/changes/XX/CHANGED-NUMBER.
  private void parseReplaceCommand(ReceiveCommand cmd, Change.Id changeId) {
    logger.atFine().log("Parsing replace command");
    if (cmd.getType() != ReceiveCommand.Type.CREATE) {
      reject(cmd, "invalid usage");
      return;
    }

    RevCommit newCommit;
    try {
      newCommit = receivePack.getRevWalk().parseCommit(cmd.getNewId());
      logger.atFine().log("Replacing with %s", newCommit);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Cannot parse %s as commit", cmd.getNewId().name());
      reject(cmd, "invalid commit");
      return;
    }

    Change changeEnt;
    try {
      changeEnt = notesFactory.createChecked(db, project.getNameKey(), changeId).getChange();
    } catch (NoSuchChangeException e) {
      logger.atSevere().withCause(e).log("Change not found %s", changeId);
      reject(cmd, "change " + changeId + " not found");
      return;
    } catch (OrmException e) {
      logger.atSevere().withCause(e).log("Cannot lookup existing change %s", changeId);
      reject(cmd, "database error");
      return;
    }
    if (!project.getNameKey().equals(changeEnt.getProject())) {
      reject(cmd, "change " + changeId + " does not belong to project " + project.getName());
      return;
    }

    logger.atFine().log("Replacing change %s", changeEnt.getId());
    requestReplace(cmd, true, changeEnt, newCommit);
  }

  private boolean requestReplace(
      ReceiveCommand cmd, boolean checkMergedInto, Change change, RevCommit newCommit) {
    if (change.getStatus().isClosed()) {
      reject(
          cmd,
          changeFormatter.changeClosed(
              ChangeReportFormatter.Input.builder().setChange(change).build()));
      return false;
    }

    ReplaceRequest req = new ReplaceRequest(change.getId(), newCommit, cmd, checkMergedInto);
    if (replaceByChange.containsKey(req.ontoChange)) {
      reject(cmd, "duplicate request");
      return false;
    }
    replaceByChange.put(req.ontoChange, req);
    return true;
  }

  private List<CreateRequest> selectNewAndReplacedChangesFromMagicBranch() {
    logger.atFine().log("Finding new and replaced changes");
    List<CreateRequest> newChanges = new ArrayList<>();

    ListMultimap<ObjectId, Ref> existing = changeRefsById();
    GroupCollector groupCollector =
        GroupCollector.create(changeRefsById(), db, psUtil, notesFactory, project.getNameKey());

    try {
      RevCommit start = setUpWalkForSelectingChanges();
      if (start == null) {
        return Collections.emptyList();
      }

      LinkedHashMap<RevCommit, ChangeLookup> pending = new LinkedHashMap<>();
      Set<Change.Key> newChangeIds = new HashSet<>();
      int maxBatchChanges = receiveConfig.getEffectiveMaxBatchChangesLimit(user);
      int total = 0;
      int alreadyTracked = 0;
      boolean rejectImplicitMerges =
          start.getParentCount() == 1
              && projectCache
                  .get(project.getNameKey())
                  .is(BooleanProjectConfig.REJECT_IMPLICIT_MERGES)
              // Don't worry about implicit merges when creating changes for
              // already-merged commits; they're already in history, so it's too
              // late.
              && !magicBranch.merged;
      Set<RevCommit> mergedParents;
      if (rejectImplicitMerges) {
        mergedParents = new HashSet<>();
      } else {
        mergedParents = null;
      }

      for (; ; ) {
        RevCommit c = receivePack.getRevWalk().next();
        if (c == null) {
          break;
        }
        total++;
        receivePack.getRevWalk().parseBody(c);
        String name = c.name();
        groupCollector.visit(c);
        Collection<Ref> existingRefs = existing.get(c);

        if (rejectImplicitMerges) {
          Collections.addAll(mergedParents, c.getParents());
          mergedParents.remove(c);
        }

        boolean commitAlreadyTracked = !existingRefs.isEmpty();
        if (commitAlreadyTracked) {
          alreadyTracked++;
          // Corner cases where an existing commit might need a new group:
          // A) Existing commit has a null group; wasn't assigned during schema
          //    upgrade, or schema upgrade is performed on a running server.
          // B) Let A<-B<-C, then:
          //      1. Push A to refs/heads/master
          //      2. Push B to refs/for/master
          //      3. Force push A~ to refs/heads/master
          //      4. Push C to refs/for/master.
          //      B will be in existing so we aren't replacing the patch set. It
          //      used to have its own group, but now needs to to be changed to
          //      A's group.
          // C) Commit is a PatchSet of a pre-existing change uploaded with a
          //    different target branch.
          for (Ref ref : existingRefs) {
            updateGroups.add(new UpdateGroupsRequest(ref, c));
          }
          if (!(newChangeForAllNotInTarget || magicBranch.base != null)) {
            continue;
          }
        }

        List<String> idList = c.getFooterLines(CHANGE_ID);
        if (!idList.isEmpty()) {
          pending.put(c, new ChangeLookup(c, new Change.Key(idList.get(idList.size() - 1).trim())));
        } else {
          pending.put(c, new ChangeLookup(c));
        }
        int n = pending.size() + newChanges.size();
        if (maxBatchChanges != 0 && n > maxBatchChanges) {
          logger.atFine().log("%d changes exceeds limit of %d", n, maxBatchChanges);
          reject(
              magicBranch.cmd,
              "the number of pushed changes in a batch exceeds the max limit " + maxBatchChanges);
          return Collections.emptyList();
        }

        if (commitAlreadyTracked) {
          boolean changeExistsOnDestBranch = false;
          for (ChangeData cd : pending.get(c).destChanges) {
            if (cd.change().getDest().equals(magicBranch.dest)) {
              changeExistsOnDestBranch = true;
              break;
            }
          }
          if (changeExistsOnDestBranch) {
            continue;
          }

          logger.atFine().log("Creating new change for %s even though it is already tracked", name);
        }

        if (!validCommit(receivePack.getRevWalk(), magicBranch.dest, magicBranch.cmd, c, null)) {
          // Not a change the user can propose? Abort as early as possible.
          logger.atFine().log("Aborting early due to invalid commit");
          return Collections.emptyList();
        }

        // Don't allow merges to be uploaded in commit chain via all-not-in-target
        if (newChangeForAllNotInTarget && c.getParentCount() > 1) {
          reject(
              magicBranch.cmd,
              "Pushing merges in commit chains with 'all not in target' is not allowed,\n"
                  + "to override please set the base manually");
          logger.atFine().log("Rejecting merge commit %s with newChangeForAllNotInTarget", name);
          // TODO(dborowitz): Should we early return here?
        }

        if (idList.isEmpty()) {
          newChanges.add(new CreateRequest(c, magicBranch.dest.get()));
          continue;
        }
      }
      logger.atFine().log(
          "Finished initial RevWalk with %d commits total: %d already"
              + " tracked, %d new changes with no Change-Id, and %d deferred"
              + " lookups",
          total, alreadyTracked, newChanges.size(), pending.size());

      if (rejectImplicitMerges) {
        rejectImplicitMerges(mergedParents);
      }

      for (Iterator<ChangeLookup> itr = pending.values().iterator(); itr.hasNext(); ) {
        ChangeLookup p = itr.next();
        if (p.changeKey == null) {
          continue;
        }

        if (newChangeIds.contains(p.changeKey)) {
          logger.atFine().log("Multiple commits with Change-Id %s", p.changeKey);
          reject(magicBranch.cmd, SAME_CHANGE_ID_IN_MULTIPLE_CHANGES);
          return Collections.emptyList();
        }

        List<ChangeData> changes = p.destChanges;
        if (changes.size() > 1) {
          logger.atFine().log(
              "Multiple changes in branch %s with Change-Id %s: %s",
              magicBranch.dest,
              p.changeKey,
              changes.stream().map(cd -> cd.getId().toString()).collect(joining()));
          // WTF, multiple changes in this branch have the same key?
          // Since the commit is new, the user should recreate it with
          // a different Change-Id. In practice, we should never see
          // this error message as Change-Id should be unique per branch.
          //
          reject(magicBranch.cmd, p.changeKey.get() + " has duplicates");
          return Collections.emptyList();
        }

        if (changes.size() == 1) {
          // Schedule as a replacement to this one matching change.
          //

          RevId currentPs = changes.get(0).currentPatchSet().getRevision();
          // If Commit is already current PatchSet of target Change.
          if (p.commit.name().equals(currentPs.get())) {
            if (pending.size() == 1) {
              // There are no commits left to check, all commits in pending were already
              // current PatchSet of the corresponding target changes.
              reject(magicBranch.cmd, "commit(s) already exists (as current patchset)");
            } else {
              // Commit is already current PatchSet.
              // Remove from pending and try next commit.
              itr.remove();
              continue;
            }
          }
          if (requestReplace(magicBranch.cmd, false, changes.get(0).change(), p.commit)) {
            continue;
          }
          return Collections.emptyList();
        }

        if (changes.size() == 0) {
          if (!isValidChangeId(p.changeKey.get())) {
            reject(magicBranch.cmd, "invalid Change-Id");
            return Collections.emptyList();
          }

          // In case the change look up from the index failed,
          // double check against the existing refs
          if (foundInExistingRef(existing.get(p.commit))) {
            if (pending.size() == 1) {
              reject(magicBranch.cmd, "commit(s) already exists (as current patchset)");
              return Collections.emptyList();
            }
            itr.remove();
            continue;
          }
          newChangeIds.add(p.changeKey);
        }
        newChanges.add(new CreateRequest(p.commit, magicBranch.dest.get()));
      }
      logger.atFine().log(
          "Finished deferred lookups with %d updates and %d new changes",
          replaceByChange.size(), newChanges.size());
    } catch (IOException e) {
      // Should never happen, the core receive process would have
      // identified the missing object earlier before we got control.
      //
      magicBranch.cmd.setResult(REJECTED_MISSING_OBJECT);
      logger.atSevere().withCause(e).log("Invalid pack upload; one or more objects weren't sent");
      return Collections.emptyList();
    } catch (OrmException e) {
      logger.atSevere().withCause(e).log("Cannot query database to locate prior changes");
      reject(magicBranch.cmd, "database error");
      return Collections.emptyList();
    }

    if (newChanges.isEmpty() && replaceByChange.isEmpty()) {
      reject(magicBranch.cmd, "no new changes");
      return Collections.emptyList();
    }
    if (!newChanges.isEmpty() && magicBranch.edit) {
      reject(magicBranch.cmd, "edit is not supported for new changes");
      return newChanges;
    }

    try {
      SortedSetMultimap<ObjectId, String> groups = groupCollector.getGroups();
      List<Integer> newIds = seq.nextChangeIds(newChanges.size());
      for (int i = 0; i < newChanges.size(); i++) {
        CreateRequest create = newChanges.get(i);
        create.setChangeId(newIds.get(i));
        create.groups = ImmutableList.copyOf(groups.get(create.commit));
      }
      for (ReplaceRequest replace : replaceByChange.values()) {
        replace.groups = ImmutableList.copyOf(groups.get(replace.newCommitId));
      }
      for (UpdateGroupsRequest update : updateGroups) {
        update.groups = ImmutableList.copyOf((groups.get(update.commit)));
      }
      logger.atFine().log("Finished updating groups from GroupCollector");
    } catch (OrmException e) {
      logger.atSevere().withCause(e).log("Error collecting groups for changes");
      reject(magicBranch.cmd, "internal server error");
    }
    return newChanges;
  }

  private boolean foundInExistingRef(Collection<Ref> existingRefs) throws OrmException {
    for (Ref ref : existingRefs) {
      ChangeNotes notes =
          notesFactory.create(db, project.getNameKey(), Change.Id.fromRef(ref.getName()));
      Change change = notes.getChange();
      if (change.getDest().equals(magicBranch.dest)) {
        logger.atFine().log("Found change %s from existing refs.", change.getKey());
        // Reindex the change asynchronously, ignoring errors.
        @SuppressWarnings("unused")
        Future<?> possiblyIgnoredError = indexer.indexAsync(project.getNameKey(), change.getId());
        return true;
      }
    }
    return false;
  }

  private RevCommit setUpWalkForSelectingChanges() throws IOException {
    RevWalk rw = receivePack.getRevWalk();
    RevCommit start = rw.parseCommit(magicBranch.cmd.getNewId());

    rw.reset();
    rw.sort(RevSort.TOPO);
    rw.sort(RevSort.REVERSE, true);
    receivePack.getRevWalk().markStart(start);
    if (magicBranch.baseCommit != null) {
      markExplicitBasesUninteresting();
    } else if (magicBranch.merged) {
      logger.atFine().log("Marking parents of merged commit %s uninteresting", start.name());
      for (RevCommit c : start.getParents()) {
        rw.markUninteresting(c);
      }
    } else {
      markHeadsAsUninteresting(rw, magicBranch.dest != null ? magicBranch.dest.get() : null);
    }
    return start;
  }

  private void markExplicitBasesUninteresting() throws IOException {
    logger.atFine().log("Marking %d base commits uninteresting", magicBranch.baseCommit.size());
    for (RevCommit c : magicBranch.baseCommit) {
      receivePack.getRevWalk().markUninteresting(c);
    }
    Ref targetRef = allRefs().get(magicBranch.dest.get());
    if (targetRef != null) {
      logger.atFine().log(
          "Marking target ref %s (%s) uninteresting",
          magicBranch.dest.get(), targetRef.getObjectId().name());
      receivePack
          .getRevWalk()
          .markUninteresting(receivePack.getRevWalk().parseCommit(targetRef.getObjectId()));
    }
  }

  private void rejectImplicitMerges(Set<RevCommit> mergedParents) throws IOException {
    if (!mergedParents.isEmpty()) {
      Ref targetRef = allRefs().get(magicBranch.dest.get());
      if (targetRef != null) {
        RevWalk rw = receivePack.getRevWalk();
        RevCommit tip = rw.parseCommit(targetRef.getObjectId());
        boolean containsImplicitMerges = true;
        for (RevCommit p : mergedParents) {
          containsImplicitMerges &= !rw.isMergedInto(p, tip);
        }

        if (containsImplicitMerges) {
          rw.reset();
          for (RevCommit p : mergedParents) {
            rw.markStart(p);
          }
          rw.markUninteresting(tip);
          RevCommit c;
          while ((c = rw.next()) != null) {
            rw.parseBody(c);
            messages.add(
                new CommitValidationMessage(
                    "ERROR: Implicit Merge of "
                        + c.abbreviate(7).name()
                        + " "
                        + c.getShortMessage(),
                    false));
          }
          reject(magicBranch.cmd, "implicit merges detected");
        }
      }
    }
  }

  // Mark all branch tips as uninteresting in the given revwalk,
  // so we get only the new commits when walking rw.
  private void markHeadsAsUninteresting(RevWalk rw, @Nullable String forRef) {
    int i = 0;
    for (Ref ref : allRefs().values()) {
      if ((ref.getName().startsWith(R_HEADS) || ref.getName().equals(forRef))
          && ref.getObjectId() != null) {
        try {
          rw.markUninteresting(rw.parseCommit(ref.getObjectId()));
          i++;
        } catch (IOException e) {
          logger.atWarning().withCause(e).log(
              "Invalid ref %s in %s", ref.getName(), project.getName());
        }
      }
    }
    logger.atFine().log("Marked %d heads as uninteresting", i);
  }

  private static boolean isValidChangeId(String idStr) {
    return idStr.matches("^I[0-9a-fA-F]{40}$") && !idStr.matches("^I00*$");
  }

  private class ChangeLookup {
    final RevCommit commit;
    final Change.Key changeKey;
    final List<ChangeData> destChanges;

    ChangeLookup(RevCommit c, Change.Key key) throws OrmException {
      commit = c;
      changeKey = key;
      destChanges = queryProvider.get().byBranchKey(magicBranch.dest, key);
    }

    ChangeLookup(RevCommit c) throws OrmException {
      commit = c;
      destChanges = queryProvider.get().byBranchCommit(magicBranch.dest, c.getName());
      changeKey = null;
    }
  }

  private class CreateRequest {
    final RevCommit commit;
    private final String refName;

    Change.Id changeId;
    ReceiveCommand cmd;
    ChangeInserter ins;
    List<String> groups = ImmutableList.of();

    Change change;

    CreateRequest(RevCommit commit, String refName) {
      this.commit = commit;
      this.refName = refName;
    }

    private void setChangeId(int id) {
      possiblyOverrideWorkInProgress();

      changeId = new Change.Id(id);
      ins =
          changeInserterFactory
              .create(changeId, commit, refName)
              .setTopic(magicBranch.topic)
              .setPrivate(setChangeAsPrivate)
              .setWorkInProgress(magicBranch.workInProgress)
              // Changes already validated in validateNewCommits.
              .setValidate(false);

      if (magicBranch.merged) {
        ins.setStatus(Change.Status.MERGED);
      }
      cmd = new ReceiveCommand(ObjectId.zeroId(), commit, ins.getPatchSetId().toRefName());
      if (receivePack.getPushCertificate() != null) {
        ins.setPushCertificate(receivePack.getPushCertificate().toTextWithSignature());
      }
    }

    private void possiblyOverrideWorkInProgress() {
      // When wip or ready explicitly provided, leave it as is.
      if (magicBranch.workInProgress || magicBranch.ready) {
        return;
      }
      magicBranch.workInProgress =
          projectState.is(BooleanProjectConfig.WORK_IN_PROGRESS_BY_DEFAULT)
              || firstNonNull(user.state().getGeneralPreferences().workInProgressByDefault, false);
    }

    private void addOps(BatchUpdate bu) throws RestApiException {
      checkState(changeId != null, "must call setChangeId before addOps");
      try {
        RevWalk rw = receivePack.getRevWalk();
        rw.parseBody(commit);
        final PatchSet.Id psId = ins.setGroups(groups).getPatchSetId();
        Account.Id me = user.getAccountId();
        List<FooterLine> footerLines = commit.getFooterLines();
        MailRecipients recipients = new MailRecipients();
        Map<String, Short> approvals = new HashMap<>();
        checkNotNull(magicBranch);
        recipients.add(magicBranch.getMailRecipients());
        approvals = magicBranch.labels;
        recipients.add(getRecipientsFromFooters(accountResolver, footerLines));
        recipients.remove(me);
        StringBuilder msg =
            new StringBuilder(
                ApprovalsUtil.renderMessageWithApprovals(
                    psId.get(), approvals, Collections.<String, PatchSetApproval>emptyMap()));
        msg.append('.');
        if (!Strings.isNullOrEmpty(magicBranch.message)) {
          msg.append("\n").append(magicBranch.message);
        }

        bu.insertChange(
            ins.setReviewers(recipients.getReviewers())
                .setExtraCC(recipients.getCcOnly())
                .setApprovals(approvals)
                .setMessage(msg.toString())
                .setNotify(magicBranch.getNotify())
                .setAccountsToNotify(magicBranch.getAccountsToNotify())
                .setRequestScopePropagator(requestScopePropagator)
                .setSendMail(true)
                .setPatchSetDescription(magicBranch.message));
        if (!magicBranch.hashtags.isEmpty()) {
          // Any change owner is allowed to add hashtags when creating a change.
          bu.addOp(
              changeId,
              hashtagsFactory.create(new HashtagsInput(magicBranch.hashtags)).setFireEvent(false));
        }
        if (!Strings.isNullOrEmpty(magicBranch.topic)) {
          bu.addOp(
              changeId,
              new BatchUpdateOp() {
                @Override
                public boolean updateChange(ChangeContext ctx) {
                  ctx.getUpdate(psId).setTopic(magicBranch.topic);
                  return true;
                }
              });
        }
        bu.addOp(
            changeId,
            new BatchUpdateOp() {
              @Override
              public boolean updateChange(ChangeContext ctx) {
                change = ctx.getChange();
                return false;
              }
            });
        bu.addOp(changeId, new ChangeProgressOp(newProgress));
      } catch (Exception e) {
        throw INSERT_EXCEPTION.apply(e);
      }
    }
  }

  private void submit(Collection<CreateRequest> create, Collection<ReplaceRequest> replace)
      throws OrmException, RestApiException, UpdateException, IOException, ConfigInvalidException,
          PermissionBackendException {
    Map<ObjectId, Change> bySha = Maps.newHashMapWithExpectedSize(create.size() + replace.size());
    for (CreateRequest r : create) {
      checkNotNull(r.change, "cannot submit new change %s; op may not have run", r.changeId);
      bySha.put(r.commit, r.change);
    }
    for (ReplaceRequest r : replace) {
      bySha.put(r.newCommitId, r.notes.getChange());
    }
    Change tipChange = bySha.get(magicBranch.cmd.getNewId());
    checkNotNull(
        tipChange, "tip of push does not correspond to a change; found these changes: %s", bySha);
    logger.atFine().log(
        "Processing submit with tip change %s (%s)", tipChange.getId(), magicBranch.cmd.getNewId());
    try (MergeOp op = mergeOpProvider.get()) {
      op.merge(db, tipChange, user, false, new SubmitInput(), false);
    }
  }

  private void preparePatchSetsForReplace(List<CreateRequest> newChanges) {
    try {
      readChangesForReplace();
      for (Iterator<ReplaceRequest> itr = replaceByChange.values().iterator(); itr.hasNext(); ) {
        ReplaceRequest req = itr.next();
        if (req.inputCommand.getResult() == NOT_ATTEMPTED) {
          req.validate(false);
        }
      }
    } catch (OrmException err) {
      logger.atSevere().withCause(err).log(
          "Cannot read database before replacement for project %s", project.getName());
      for (ReplaceRequest req : replaceByChange.values()) {
        if (req.inputCommand.getResult() == NOT_ATTEMPTED) {
          req.inputCommand.setResult(REJECTED_OTHER_REASON, "internal server error");
        }
      }
    } catch (IOException | PermissionBackendException err) {
      logger.atSevere().withCause(err).log(
          "Cannot read repository before replacement for project %s", project.getName());
      for (ReplaceRequest req : replaceByChange.values()) {
        if (req.inputCommand.getResult() == NOT_ATTEMPTED) {
          req.inputCommand.setResult(REJECTED_OTHER_REASON, "internal server error");
        }
      }
    }
    logger.atFine().log("Read %d changes to replace", replaceByChange.size());

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
    Collection<ChangeNotes> allNotes =
        notesFactory.create(
            db, replaceByChange.values().stream().map(r -> r.ontoChange).collect(toList()));
    for (ChangeNotes notes : allNotes) {
      replaceByChange.get(notes.getChangeId()).notes = notes;
    }
  }

  private class ReplaceRequest {
    final Change.Id ontoChange;
    final ObjectId newCommitId;
    final ReceiveCommand inputCommand;
    final boolean checkMergedInto;
    ChangeNotes notes;
    BiMap<RevCommit, PatchSet.Id> revisions;
    PatchSet.Id psId;
    ReceiveCommand prev;
    ReceiveCommand cmd;
    PatchSetInfo info;
    private PatchSet.Id priorPatchSet;
    List<String> groups = ImmutableList.of();
    private ReplaceOp replaceOp;

    ReplaceRequest(
        Change.Id toChange, RevCommit newCommit, ReceiveCommand cmd, boolean checkMergedInto) {
      this.ontoChange = toChange;
      this.newCommitId = newCommit.copy();
      this.inputCommand = checkNotNull(cmd);
      this.checkMergedInto = checkMergedInto;

      revisions = HashBiMap.create();
      for (Ref ref : refs(toChange)) {
        try {
          revisions.forcePut(
              receivePack.getRevWalk().parseCommit(ref.getObjectId()),
              PatchSet.Id.fromRef(ref.getName()));
        } catch (IOException err) {
          logger.atWarning().withCause(err).log(
              "Project %s contains invalid change ref %s", project.getName(), ref.getName());
        }
      }
    }

    /**
     * Validate the new patch set commit for this change.
     *
     * <p><strong>Side effects:</strong>
     *
     * <ul>
     *   <li>May add error or warning messages to the progress monitor
     *   <li>Will reject {@code cmd} prior to returning false
     *   <li>May reset {@code receivePack.getRevWalk()}; do not call in the middle of a walk.
     * </ul>
     *
     * @param autoClose whether the caller intends to auto-close the change after adding a new patch
     *     set.
     * @return whether the new commit is valid
     * @throws IOException
     * @throws OrmException
     * @throws PermissionBackendException
     */
    boolean validate(boolean autoClose)
        throws IOException, OrmException, PermissionBackendException {
      if (!autoClose && inputCommand.getResult() != NOT_ATTEMPTED) {
        return false;
      } else if (notes == null) {
        reject(inputCommand, "change " + ontoChange + " not found");
        return false;
      }

      Change change = notes.getChange();
      priorPatchSet = change.currentPatchSetId();
      if (!revisions.containsValue(priorPatchSet)) {
        reject(inputCommand, "change " + ontoChange + " missing revisions");
        return false;
      }

      RevCommit newCommit = receivePack.getRevWalk().parseCommit(newCommitId);
      RevCommit priorCommit = revisions.inverse().get(priorPatchSet);

      // Not allowed to create a new patch set if the current patch set is locked.
      if (psUtil.isPatchSetLocked(notes)) {
        reject(inputCommand, "cannot add patch set to " + ontoChange + ".");
        return false;
      }

      try {
        permissions.change(notes).database(db).check(ChangePermission.ADD_PATCH_SET);
      } catch (AuthException no) {
        reject(inputCommand, "cannot add patch set to " + ontoChange + ".");
        return false;
      }

      if (!projectState.statePermitsWrite()) {
        reject(inputCommand, "cannot add patch set to " + ontoChange + ".");
        return false;
      }
      if (change.getStatus().isClosed()) {
        reject(inputCommand, "change " + ontoChange + " closed");
        return false;
      } else if (revisions.containsKey(newCommit)) {
        reject(inputCommand, "commit already exists (in the change)");
        return false;
      }

      for (Ref r : receivePack.getRepository().getRefDatabase().getRefsByPrefix("refs/changes")) {
        if (r.getObjectId().equals(newCommit)) {
          reject(inputCommand, "commit already exists (in the project)");
          return false;
        }
      }

      for (RevCommit prior : revisions.keySet()) {
        // Don't allow a change to directly depend upon itself. This is a
        // very common error due to users making a new commit rather than
        // amending when trying to address review comments.
        if (receivePack.getRevWalk().isMergedInto(prior, newCommit)) {
          reject(inputCommand, SAME_CHANGE_ID_IN_MULTIPLE_CHANGES);
          return false;
        }
      }

      if (!validCommit(
          receivePack.getRevWalk(), change.getDest(), inputCommand, newCommit, change)) {
        return false;
      }
      receivePack.getRevWalk().parseBody(priorCommit);

      // Don't allow the same tree if the commit message is unmodified
      // or no parents were updated (rebase), else warn that only part
      // of the commit was modified.
      if (newCommit.getTree().equals(priorCommit.getTree())) {
        boolean messageEq =
            Objects.equals(newCommit.getFullMessage(), priorCommit.getFullMessage());
        boolean parentsEq = parentsEqual(newCommit, priorCommit);
        boolean authorEq = authorEqual(newCommit, priorCommit);
        ObjectReader reader = receivePack.getRevWalk().getObjectReader();

        if (messageEq && parentsEq && authorEq && !autoClose) {
          addMessage(
              String.format(
                  "warning: no changes between prior commit %s and new commit %s",
                  reader.abbreviate(priorCommit).name(), reader.abbreviate(newCommit).name()));
        } else {
          StringBuilder msg = new StringBuilder();
          msg.append("warning: ").append(reader.abbreviate(newCommit).name());
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

      if (magicBranch != null
          && (magicBranch.workInProgress || magicBranch.ready)
          && magicBranch.workInProgress != change.isWorkInProgress()
          && !user.getAccountId().equals(change.getOwner())) {
        boolean hasWriteConfigPermission = false;
        try {
          permissions.check(ProjectPermission.WRITE_CONFIG);
          hasWriteConfigPermission = true;
        } catch (AuthException e) {
          // Do nothing.
        }

        if (!hasWriteConfigPermission) {
          try {
            permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);
          } catch (AuthException e1) {
            reject(inputCommand, ONLY_CHANGE_OWNER_OR_PROJECT_OWNER_CAN_MODIFY_WIP);
            return false;
          }
        }
      }

      if (magicBranch != null && (magicBranch.edit || magicBranch.draft)) {
        return newEdit();
      }

      newPatchSet();
      return true;
    }

    private boolean newEdit() {
      psId = notes.getChange().currentPatchSetId();
      Optional<ChangeEdit> edit = null;

      try {
        edit = editUtil.byChange(notes, user);
      } catch (AuthException | IOException e) {
        logger.atSevere().withCause(e).log("Cannot retrieve edit");
        return false;
      }

      if (edit.isPresent()) {
        if (edit.get().getBasePatchSet().getId().equals(psId)) {
          // replace edit
          cmd =
              new ReceiveCommand(edit.get().getEditCommit(), newCommitId, edit.get().getRefName());
        } else {
          // delete old edit ref on rebase
          prev =
              new ReceiveCommand(
                  edit.get().getEditCommit(), ObjectId.zeroId(), edit.get().getRefName());
          createEditCommand();
        }
      } else {
        createEditCommand();
      }

      return true;
    }

    private void createEditCommand() {
      // create new edit
      cmd =
          new ReceiveCommand(
              ObjectId.zeroId(),
              newCommitId,
              RefNames.refsEdit(user.getAccountId(), notes.getChangeId(), psId));
    }

    private void newPatchSet() throws IOException, OrmException {
      RevCommit newCommit = receivePack.getRevWalk().parseCommit(newCommitId);
      psId =
          ChangeUtil.nextPatchSetIdFromAllRefsMap(allRefs(), notes.getChange().currentPatchSetId());
      info = patchSetInfoFactory.get(receivePack.getRevWalk(), newCommit, psId);
      cmd = new ReceiveCommand(ObjectId.zeroId(), newCommitId, psId.toRefName());
    }

    void addOps(BatchUpdate bu, @Nullable Task progress) throws IOException {
      if (magicBranch != null && (magicBranch.edit || magicBranch.draft)) {
        bu.addOp(notes.getChangeId(), new ReindexOnlyOp());
        if (prev != null) {
          bu.addRepoOnlyOp(new UpdateOneRefOp(prev));
        }
        bu.addRepoOnlyOp(new UpdateOneRefOp(cmd));
        return;
      }
      RevWalk rw = receivePack.getRevWalk();
      // TODO(dborowitz): Move to ReplaceOp#updateRepo.
      RevCommit newCommit = rw.parseCommit(newCommitId);
      rw.parseBody(newCommit);

      RevCommit priorCommit = revisions.inverse().get(priorPatchSet);
      replaceOp =
          replaceOpFactory
              .create(
                  projectState,
                  notes.getChange().getDest(),
                  checkMergedInto,
                  priorPatchSet,
                  priorCommit,
                  psId,
                  newCommit,
                  info,
                  groups,
                  magicBranch,
                  receivePack.getPushCertificate())
              .setRequestScopePropagator(requestScopePropagator);
      bu.addOp(notes.getChangeId(), replaceOp);
      if (progress != null) {
        bu.addOp(notes.getChangeId(), new ChangeProgressOp(progress));
      }
    }

    String getRejectMessage() {
      return replaceOp != null ? replaceOp.getRejectMessage() : null;
    }
  }

  private class UpdateGroupsRequest {
    private final PatchSet.Id psId;
    private final RevCommit commit;
    List<String> groups = ImmutableList.of();

    UpdateGroupsRequest(Ref ref, RevCommit commit) {
      this.psId = checkNotNull(PatchSet.Id.fromRef(ref.getName()));
      this.commit = commit;
    }

    private void addOps(BatchUpdate bu) {
      bu.addOp(
          psId.getParentKey(),
          new BatchUpdateOp() {
            @Override
            public boolean updateChange(ChangeContext ctx) throws OrmException {
              PatchSet ps = psUtil.get(ctx.getDb(), ctx.getNotes(), psId);
              List<String> oldGroups = ps.getGroups();
              if (oldGroups == null) {
                if (groups == null) {
                  return false;
                }
              } else if (sameGroups(oldGroups, groups)) {
                return false;
              }
              psUtil.setGroups(ctx.getDb(), ctx.getUpdate(psId), ps, groups);
              return true;
            }
          });
    }

    private boolean sameGroups(List<String> a, List<String> b) {
      return Sets.newHashSet(a).equals(Sets.newHashSet(b));
    }
  }

  private class UpdateOneRefOp implements RepoOnlyOp {
    private final ReceiveCommand cmd;

    private UpdateOneRefOp(ReceiveCommand cmd) {
      this.cmd = checkNotNull(cmd);
    }

    @Override
    public void updateRepo(RepoContext ctx) throws IOException {
      ctx.addRefUpdate(cmd);
    }

    @Override
    public void postUpdate(Context ctx) {
      String refName = cmd.getRefName();
      if (cmd.getType() == ReceiveCommand.Type.UPDATE) { // aka fast-forward
        logger.atFine().log("Updating tag cache on fast-forward of %s", cmd.getRefName());
        tagCache.updateFastForward(project.getNameKey(), refName, cmd.getOldId(), cmd.getNewId());
      }
      if (isConfig(cmd)) {
        logger.atFine().log("Reloading project in cache");
        try {
          projectCache.evict(project);
        } catch (IOException e) {
          logger.atWarning().withCause(e).log(
              "Cannot evict from project cache, name key: %s", project.getName());
        }
        ProjectState ps = projectCache.get(project.getNameKey());
        try {
          logger.atFine().log("Updating project description");
          repo.setGitwebDescription(ps.getProject().getDescription());
        } catch (IOException e) {
          logger.atWarning().withCause(e).log("cannot update description of %s", project.getName());
        }
        if (allProjectsName.equals(project.getNameKey())) {
          try {
            createGroupPermissionSyncer.syncIfNeeded();
          } catch (IOException | ConfigInvalidException e) {
            logger.atSevere().withCause(e).log("Can't sync create group permissions");
          }
        }
      }
    }
  }

  private static class ReindexOnlyOp implements BatchUpdateOp {
    @Override
    public boolean updateChange(ChangeContext ctx) {
      // Trigger reindexing even though change isn't actually updated.
      return true;
    }
  }

  private List<Ref> refs(Change.Id changeId) {
    return refsByChange().get(changeId);
  }

  private void initChangeRefMaps() {
    if (refsByChange == null) {
      int estRefsPerChange = 4;
      refsById = MultimapBuilder.hashKeys().arrayListValues().build();
      refsByChange =
          MultimapBuilder.hashKeys(allRefs().size() / estRefsPerChange)
              .arrayListValues(estRefsPerChange)
              .build();
      for (Ref ref : allRefs().values()) {
        ObjectId obj = ref.getObjectId();
        if (obj != null) {
          PatchSet.Id psId = PatchSet.Id.fromRef(ref.getName());
          if (psId != null) {
            refsById.put(obj, ref);
            refsByChange.put(psId.getParentKey(), ref);
          }
        }
      }
    }
  }

  private ListMultimap<Change.Id, Ref> refsByChange() {
    initChangeRefMaps();
    return refsByChange;
  }

  private ListMultimap<ObjectId, Ref> changeRefsById() {
    initChangeRefMaps();
    return refsById;
  }

  static boolean parentsEqual(RevCommit a, RevCommit b) {
    if (a.getParentCount() != b.getParentCount()) {
      return false;
    }
    for (int i = 0; i < a.getParentCount(); i++) {
      if (!a.getParent(i).equals(b.getParent(i))) {
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

    return Objects.equals(aAuthor.getName(), bAuthor.getName())
        && Objects.equals(aAuthor.getEmailAddress(), bAuthor.getEmailAddress());
  }

  // Run RefValidators on the command. If any validator fails, the command status is set to
  // REJECTED, and the return value is 'false'
  private boolean validRefOperation(ReceiveCommand cmd) {
    RefOperationValidators refValidators = refValidatorsFactory.create(getProject(), user, cmd);

    try {
      messages.addAll(refValidators.validateForRefOperation());
    } catch (RefOperationValidationException e) {
      messages.addAll(Lists.newArrayList(e.getMessages()));
      reject(cmd, e.getMessage());
      return false;
    }

    return true;
  }

  private void validateNewCommits(Branch.NameKey branch, ReceiveCommand cmd)
      throws PermissionBackendException {
    if (!RefNames.REFS_CONFIG.equals(cmd.getRefName())
        && !(MagicBranch.isMagicBranch(cmd.getRefName())
            || NEW_PATCHSET_PATTERN.matcher(cmd.getRefName()).matches())
        && pushOptions.containsKey(PUSH_OPTION_SKIP_VALIDATION)) {
      if (projectState.is(BooleanProjectConfig.USE_SIGNED_OFF_BY)) {
        reject(cmd, "requireSignedOffBy prevents option " + PUSH_OPTION_SKIP_VALIDATION);
        return;
      }

      Optional<AuthException> err =
          checkRefPermission(permissions.ref(branch.get()), RefPermission.SKIP_VALIDATION);
      if (err.isPresent()) {
        rejectProhibited(cmd, err.get());
        return;
      }
      if (!Iterables.isEmpty(rejectCommits)) {
        reject(cmd, "reject-commits prevents " + PUSH_OPTION_SKIP_VALIDATION);
      }
      logger.atFine().log("Short-circuiting new commit validation");
      return;
    }

    boolean missingFullName = Strings.isNullOrEmpty(user.getAccount().getFullName());
    RevWalk walk = receivePack.getRevWalk();
    walk.reset();
    walk.sort(RevSort.NONE);
    try {
      RevObject parsedObject = walk.parseAny(cmd.getNewId());
      if (!(parsedObject instanceof RevCommit)) {
        return;
      }
      ListMultimap<ObjectId, Ref> existing = changeRefsById();
      walk.markStart((RevCommit) parsedObject);
      markHeadsAsUninteresting(walk, cmd.getRefName());
      int limit = receiveConfig.maxBatchCommits;
      int n = 0;
      for (RevCommit c; (c = walk.next()) != null; ) {
        if (++n > limit) {
          logger.atFine().log("Number of new commits exceeds limit of %d", limit);
          reject(
              cmd,
              String.format(
                  "more than %d commits, and %s not set", limit, PUSH_OPTION_SKIP_VALIDATION));
          return;
        }
        if (existing.keySet().contains(c)) {
          continue;
        } else if (!validCommit(walk, branch, cmd, c, null)) {
          break;
        }

        if (missingFullName && user.hasEmailAddress(c.getCommitterIdent().getEmailAddress())) {
          logger.atFine().log("Will update full name of caller");
          setFullNameTo = c.getCommitterIdent().getName();
          missingFullName = false;
        }
      }
      logger.atFine().log("Validated %d new commits", n);
    } catch (IOException err) {
      cmd.setResult(REJECTED_MISSING_OBJECT);
      logger.atSevere().withCause(err).log("Invalid pack upload; one or more objects weren't sent");
    }
  }

  private boolean validCommit(
      RevWalk rw, Branch.NameKey branch, ReceiveCommand cmd, ObjectId id, @Nullable Change change)
      throws IOException {
    PermissionBackend.ForRef perm = permissions.ref(branch.get());

    ValidCommitKey key = new AutoValue_ReceiveCommits_ValidCommitKey(id.copy(), branch);
    if (validCommits.contains(key)) {
      return true;
    }

    RevCommit c = rw.parseCommit(id);
    rw.parseBody(c);

    try (CommitReceivedEvent receiveEvent =
        new CommitReceivedEvent(cmd, project, branch.get(), rw.getObjectReader(), c, user)) {
      boolean isMerged =
          magicBranch != null
              && cmd.getRefName().equals(magicBranch.cmd.getRefName())
              && magicBranch.merged;
      CommitValidators validators =
          isMerged
              ? commitValidatorsFactory.forMergedCommits(
                  project.getNameKey(), perm, user.asIdentifiedUser())
              : commitValidatorsFactory.forReceiveCommits(
                  perm, branch, user.asIdentifiedUser(), sshInfo, repo, rw, change);
      messages.addAll(validators.validate(receiveEvent));
    } catch (CommitValidationException e) {
      logger.atFine().log("Commit validation failed on %s", c.name());
      messages.addAll(e.getMessages());
      reject(cmd, e.getMessage());
      return false;
    }
    validCommits.add(key);
    return true;
  }

  private void autoCloseChanges(ReceiveCommand cmd) {
    logger.atFine().log("Starting auto-closing of changes");
    String refName = cmd.getRefName();
    checkState(
        !MagicBranch.isMagicBranch(refName),
        "shouldn't be auto-closing changes on magic branch %s",
        refName);
    // TODO(dborowitz): Combine this BatchUpdate with the main one in
    // insertChangesAndPatchSets.
    try {
      retryHelper.execute(
          updateFactory -> {
            try (BatchUpdate bu =
                    updateFactory.create(db, projectState.getNameKey(), user, TimeUtil.nowTs());
                ObjectInserter ins = repo.newObjectInserter();
                ObjectReader reader = ins.newReader();
                RevWalk rw = new RevWalk(reader)) {
              bu.setRepository(repo, rw, ins).updateChangesInParallel();
              // TODO(dborowitz): Teach BatchUpdate to ignore missing changes.

              RevCommit newTip = rw.parseCommit(cmd.getNewId());
              Branch.NameKey branch = new Branch.NameKey(project.getNameKey(), refName);

              rw.reset();
              rw.markStart(newTip);
              if (!ObjectId.zeroId().equals(cmd.getOldId())) {
                rw.markUninteresting(rw.parseCommit(cmd.getOldId()));
              }

              ListMultimap<ObjectId, Ref> byCommit = changeRefsById();
              Map<Change.Key, ChangeNotes> byKey = null;
              List<ReplaceRequest> replaceAndClose = new ArrayList<>();

              int existingPatchSets = 0;
              int newPatchSets = 0;
              COMMIT:
              for (RevCommit c; (c = rw.next()) != null; ) {
                rw.parseBody(c);

                for (Ref ref : byCommit.get(c.copy())) {
                  PatchSet.Id psId = PatchSet.Id.fromRef(ref.getName());
                  Optional<ChangeNotes> notes = getChangeNotes(psId.getParentKey());
                  if (notes.isPresent() && notes.get().getChange().getDest().equals(branch)) {
                    existingPatchSets++;
                    bu.addOp(
                        psId.getParentKey(),
                        mergedByPushOpFactory.create(requestScopePropagator, psId, refName));
                    continue COMMIT;
                  }
                }

                for (String changeId : c.getFooterLines(CHANGE_ID)) {
                  if (byKey == null) {
                    byKey = executeIndexQuery(() -> openChangesByKeyByBranch(branch));
                  }

                  ChangeNotes onto = byKey.get(new Change.Key(changeId.trim()));
                  if (onto != null) {
                    newPatchSets++;
                    // Hold onto this until we're done with the walk, as the call to
                    // req.validate below calls isMergedInto which resets the walk.
                    ReplaceRequest req = new ReplaceRequest(onto.getChangeId(), c, cmd, false);
                    req.notes = onto;
                    replaceAndClose.add(req);
                    continue COMMIT;
                  }
                }
              }

              for (ReplaceRequest req : replaceAndClose) {
                Change.Id id = req.notes.getChangeId();
                if (!req.validate(true)) {
                  logger.atFine().log("Not closing %s because validation failed", id);
                  continue;
                }
                req.addOps(bu, null);
                bu.addOp(
                    id,
                    mergedByPushOpFactory
                        .create(requestScopePropagator, req.psId, refName)
                        .setPatchSetProvider(req.replaceOp::getPatchSet));
                bu.addOp(id, new ChangeProgressOp(closeProgress));
              }

              logger.atFine().log(
                  "Auto-closing %s changes with existing patch sets and %s with new patch sets",
                  existingPatchSets, newPatchSets);
              bu.execute();
            } catch (IOException | OrmException | PermissionBackendException e) {
              logger.atSevere().withCause(e).log("Failed to auto-close changes");
            }
            return null;
          },
          // Use a multiple of the default timeout to account for inner retries that may otherwise
          // eat up the whole timeout so that no time is left to retry this outer action.
          RetryHelper.options()
              .timeout(retryHelper.getDefaultTimeout(ActionType.CHANGE_UPDATE).multipliedBy(5))
              .build());
    } catch (RestApiException e) {
      logger.atSevere().withCause(e).log("Can't insert patchset");
    } catch (UpdateException e) {
      logger.atSevere().withCause(e).log("Failed to auto-close changes");
    }
  }

  private Optional<ChangeNotes> getChangeNotes(Change.Id changeId) throws OrmException {
    try {
      return Optional.of(notesFactory.createChecked(db, project.getNameKey(), changeId));
    } catch (NoSuchChangeException e) {
      return Optional.empty();
    }
  }

  private <T> T executeIndexQuery(Action<T> action) throws OrmException {
    try {
      return retryHelper.execute(ActionType.INDEX_QUERY, action, OrmException.class::isInstance);
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, OrmException.class);
      throw new OrmException(e);
    }
  }

  private void updateAccountInfo() {
    if (setFullNameTo == null) {
      return;
    }
    logger.atFine().log("Updating full name of caller");
    try {
      Optional<AccountState> accountState =
          accountsUpdateProvider
              .get()
              .update(
                  "Set Full Name on Receive Commits",
                  user.getAccountId(),
                  (a, u) -> {
                    if (Strings.isNullOrEmpty(a.getAccount().getFullName())) {
                      u.setFullName(setFullNameTo);
                    }
                  });
      accountState
          .map(AccountState::getAccount)
          .ifPresent(a -> user.getAccount().setFullName(a.getFullName()));
    } catch (OrmException | IOException | ConfigInvalidException e) {
      logger.atWarning().withCause(e).log("Failed to update full name of caller");
    }
  }

  private Map<Change.Key, ChangeNotes> openChangesByKeyByBranch(Branch.NameKey branch)
      throws OrmException {
    Map<Change.Key, ChangeNotes> r = new HashMap<>();
    for (ChangeData cd : queryProvider.get().byBranchOpen(branch)) {
      try {
        r.put(cd.change().getKey(), cd.notes());
      } catch (NoSuchChangeException e) {
        // Ignore deleted change
      }
    }
    return r;
  }

  // allRefsWatcher hooks into the protocol negotation to get a list of all known refs.
  // This is used as a cache of ref -> sha1 values, and to build an inverse index
  // of (change => list of refs) and a (SHA1 => refs).
  private Map<String, Ref> allRefs() {
    return allRefsWatcher.getAllRefs();
  }

  private void reject(ReceiveCommand cmd, String why) {
    cmd.setResult(REJECTED_OTHER_REASON, why);
  }

  private static boolean isHead(ReceiveCommand cmd) {
    return cmd.getRefName().startsWith(Constants.R_HEADS);
  }

  private static boolean isConfig(ReceiveCommand cmd) {
    return cmd.getRefName().equals(RefNames.REFS_CONFIG);
  }
}
