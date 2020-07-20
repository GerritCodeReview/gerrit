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
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.common.flogger.LazyArgs.lazy;
import static com.google.gerrit.entities.RefNames.REFS_CHANGES;
import static com.google.gerrit.entities.RefNames.isConfigRef;
import static com.google.gerrit.git.ObjectIds.abbreviateName;
import static com.google.gerrit.server.change.HashtagsUtil.cleanupHashtag;
import static com.google.gerrit.server.git.MultiProgressMonitor.UNKNOWN;
import static com.google.gerrit.server.git.receive.ReceiveConstants.COMMAND_REJECTION_MESSAGE_FOOTER;
import static com.google.gerrit.server.git.receive.ReceiveConstants.ONLY_CHANGE_OWNER_OR_PROJECT_OWNER_CAN_MODIFY_WIP;
import static com.google.gerrit.server.git.receive.ReceiveConstants.PUSH_OPTION_SKIP_VALIDATION;
import static com.google.gerrit.server.git.receive.ReceiveConstants.SAME_CHANGE_ID_IN_MULTIPLE_CHANGES;
import static com.google.gerrit.server.git.validators.CommitValidators.NEW_PATCHSET_PATTERN;
import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromFooters;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.OK;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_MISSING_OBJECT;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSetMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.common.collect.SortedSetMultimap;
import com.google.common.collect.Streams;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.FooterConstants;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.HumanComment;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.PatchSetInfo;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.SubmissionId;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.NotifyInfo;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.projects.ProjectConfigEntryType;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.extensions.validators.CommentForValidation;
import com.google.gerrit.extensions.validators.CommentForValidation.CommentSource;
import com.google.gerrit.extensions.validators.CommentForValidation.CommentType;
import com.google.gerrit.extensions.validators.CommentValidationContext;
import com.google.gerrit.extensions.validators.CommentValidationFailure;
import com.google.gerrit.extensions.validators.CommentValidator;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.ChangeUtil;
import com.google.gerrit.server.CommentsUtil;
import com.google.gerrit.server.CreateGroupPermissionSyncer;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.PublishCommentUtil;
import com.google.gerrit.server.PublishCommentsOp;
import com.google.gerrit.server.RequestInfo;
import com.google.gerrit.server.RequestListener;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.change.SetHashtagsOp;
import com.google.gerrit.server.change.SetPrivateOp;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.git.BanCommit;
import com.google.gerrit.server.git.ChangeReportFormatter;
import com.google.gerrit.server.git.GroupCollector;
import com.google.gerrit.server.git.MergedByPushOp;
import com.google.gerrit.server.git.MultiProgressMonitor;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.git.ReceivePackInitializer;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.ValidationError;
import com.google.gerrit.server.git.validators.CommentCountValidator;
import com.google.gerrit.server.git.validators.CommentSizeValidator;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.RefOperationValidationException;
import com.google.gerrit.server.git.validators.RefOperationValidators;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.logging.Metadata;
import com.google.gerrit.server.logging.PerformanceLogContext;
import com.google.gerrit.server.logging.PerformanceLogger;
import com.google.gerrit.server.logging.RequestId;
import com.google.gerrit.server.logging.TraceContext;
import com.google.gerrit.server.logging.TraceContext.TraceTimer;
import com.google.gerrit.server.mail.MailUtil.MailRecipients;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.Sequences;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.PermissionDeniedException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.CreateRefControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.submit.MergeOp;
import com.google.gerrit.server.submit.MergeOpRepoManager;
import com.google.gerrit.server.submit.SubmoduleOp;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.update.RepoOnlyOp;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.util.LabelVote;
import com.google.gerrit.server.util.MagicBranch;
import com.google.gerrit.server.util.RequestScopePropagator;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.gerrit.server.validators.ValidationException;
import com.google.gerrit.util.cli.CmdLineParser;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.stream.Collectors;
import java.util.stream.Stream;
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
import org.eclipse.jgit.transport.ReceivePack;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

/**
 * Receives change upload using the Git receive-pack protocol.
 *
 * <p>Conceptually, most use of Gerrit is a push of some commits to refs/for/BRANCH. However, the
 * receive-pack protocol that this is based on allows multiple ref updates to be processed at once.
 * So we have to be prepared to also handle normal pushes (refs/heads/BRANCH), and legacy pushes
 * (refs/changes/CHANGE). It is hard to split this class up further, because normal pushes can also
 * result in updates to reviews, through the autoclose mechanism.
 */
class ReceiveCommits {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final String CANNOT_DELETE_CHANGES = "Cannot delete from '" + REFS_CHANGES + "'";
  private static final String CANNOT_DELETE_CONFIG =
      "Cannot delete project configuration from '" + RefNames.REFS_CONFIG + "'";
  private static final String INTERNAL_SERVER_ERROR = "internal server error";

  interface Factory {
    ReceiveCommits create(
        ProjectState projectState,
        IdentifiedUser user,
        ReceivePack receivePack,
        Repository repository,
        AllRefsWatcher allRefsWatcher,
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

  private static RestApiException asRestApiException(Exception e) {
    if (e instanceof RestApiException) {
      return (RestApiException) e;
    } else if ((e instanceof ExecutionException) && (e.getCause() instanceof RestApiException)) {
      return (RestApiException) e.getCause();
    }
    return RestApiException.wrap("Error inserting change/patchset", e);
  }

  // ReceiveCommits has a lot of fields, sorry. Here and in the constructor they are split up
  // somewhat, and kept sorted lexicographically within sections, except where later assignments
  // depend on previous ones.

  // Injected fields.
  private final AccountResolver accountResolver;
  private final AllProjectsName allProjectsName;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final ChangeEditUtil editUtil;
  private final ChangeIndexer indexer;
  private final ChangeInserter.Factory changeInserterFactory;
  private final ChangeNotes.Factory notesFactory;
  private final ChangeReportFormatter changeFormatter;
  private final CmdLineParser.Factory optionParserFactory;
  private final CommentsUtil commentsUtil;
  private final PluginSetContext<CommentValidator> commentValidators;
  private final BranchCommitValidator.Factory commitValidatorFactory;
  private final Config config;
  private final CreateGroupPermissionSyncer createGroupPermissionSyncer;
  private final CreateRefControl createRefControl;
  private final DynamicMap<ProjectConfigEntry> pluginConfigEntries;
  private final PluginSetContext<ReceivePackInitializer> initializers;
  private final MergedByPushOp.Factory mergedByPushOpFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final PatchSetUtil psUtil;
  private final DynamicSet<PerformanceLogger> performanceLoggers;
  private final PermissionBackend permissionBackend;
  private final ProjectCache projectCache;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Provider<MergeOp> mergeOpProvider;
  private final Provider<MergeOpRepoManager> ormProvider;
  private final ReceiveConfig receiveConfig;
  private final RefOperationValidators.Factory refValidatorsFactory;
  private final ReplaceOp.Factory replaceOpFactory;
  private final PluginSetContext<RequestListener> requestListeners;
  private final PublishCommentsOp.Factory publishCommentsOp;
  private final RetryHelper retryHelper;
  private final RequestScopePropagator requestScopePropagator;
  private final Sequences seq;
  private final SetHashtagsOp.Factory hashtagsFactory;
  private final SubmoduleOp.Factory subOpFactory;
  private final TagCache tagCache;
  private final ProjectConfig.Factory projectConfigFactory;
  private final SetPrivateOp.Factory setPrivateOpFactory;

  // Assisted injected fields.
  private final ProjectState projectState;
  private final IdentifiedUser user;
  private final ReceivePack receivePack;

  // Immutable fields derived from constructor arguments.
  private final boolean allowProjectOwnersToChangeParent;
  private final LabelTypes labelTypes;
  private final NoteMap rejectCommits;
  private final PermissionBackend.ForProject permissions;
  private final Project project;
  private final Repository repo;

  // Collections populated during processing.
  private final List<UpdateGroupsRequest> updateGroups;
  private final Queue<ValidationMessage> messages;
  /** Multimap of error text to refnames that produced that error. */
  private final ListMultimap<String, String> errors;

  private final ListMultimap<String, String> pushOptions;
  private final ReceivePackRefCache receivePackRefCache;
  private final Map<Change.Id, ReplaceRequest> replaceByChange;

  // Other settings populated during processing.
  private MagicBranchInput magicBranch;
  private boolean newChangeForAllNotInTarget;
  private boolean setChangeAsPrivate;
  private Optional<NoteDbPushOption> noteDbPushOption;
  private Optional<String> tracePushOption;

  private MessageSender messageSender;
  private ReceiveCommitsResult.Builder result;
  private ImmutableMap<String, String> loggingTags;

  /** This object is for single use only. */
  private boolean used;

  @Inject
  ReceiveCommits(
      AccountResolver accountResolver,
      AllProjectsName allProjectsName,
      BatchUpdate.Factory batchUpdateFactory,
      ProjectConfig.Factory projectConfigFactory,
      @GerritServerConfig Config config,
      ChangeEditUtil editUtil,
      ChangeIndexer indexer,
      ChangeInserter.Factory changeInserterFactory,
      ChangeNotes.Factory notesFactory,
      DynamicItem<ChangeReportFormatter> changeFormatterProvider,
      CmdLineParser.Factory optionParserFactory,
      CommentsUtil commentsUtil,
      BranchCommitValidator.Factory commitValidatorFactory,
      CreateGroupPermissionSyncer createGroupPermissionSyncer,
      CreateRefControl createRefControl,
      DynamicMap<ProjectConfigEntry> pluginConfigEntries,
      PluginSetContext<ReceivePackInitializer> initializers,
      PluginSetContext<CommentValidator> commentValidators,
      MergedByPushOp.Factory mergedByPushOpFactory,
      PatchSetInfoFactory patchSetInfoFactory,
      PatchSetUtil psUtil,
      DynamicSet<PerformanceLogger> performanceLoggers,
      PermissionBackend permissionBackend,
      ProjectCache projectCache,
      Provider<InternalChangeQuery> queryProvider,
      Provider<MergeOp> mergeOpProvider,
      Provider<MergeOpRepoManager> ormProvider,
      PublishCommentsOp.Factory publishCommentsOp,
      ReceiveConfig receiveConfig,
      RefOperationValidators.Factory refValidatorsFactory,
      ReplaceOp.Factory replaceOpFactory,
      PluginSetContext<RequestListener> requestListeners,
      RetryHelper retryHelper,
      RequestScopePropagator requestScopePropagator,
      Sequences seq,
      SetHashtagsOp.Factory hashtagsFactory,
      SubmoduleOp.Factory subOpFactory,
      TagCache tagCache,
      SetPrivateOp.Factory setPrivateOpFactory,
      @Assisted ProjectState projectState,
      @Assisted IdentifiedUser user,
      @Assisted ReceivePack rp,
      @Assisted Repository repository,
      @Assisted AllRefsWatcher allRefsWatcher,
      @Nullable @Assisted MessageSender messageSender)
      throws IOException {
    // Injected fields.
    this.accountResolver = accountResolver;
    this.allProjectsName = allProjectsName;
    this.batchUpdateFactory = batchUpdateFactory;
    this.changeFormatter = changeFormatterProvider.get();
    this.changeInserterFactory = changeInserterFactory;
    this.commentsUtil = commentsUtil;
    this.commentValidators = commentValidators;
    this.commitValidatorFactory = commitValidatorFactory;
    this.config = config;
    this.createRefControl = createRefControl;
    this.createGroupPermissionSyncer = createGroupPermissionSyncer;
    this.editUtil = editUtil;
    this.hashtagsFactory = hashtagsFactory;
    this.indexer = indexer;
    this.initializers = initializers;
    this.mergeOpProvider = mergeOpProvider;
    this.mergedByPushOpFactory = mergedByPushOpFactory;
    this.notesFactory = notesFactory;
    this.optionParserFactory = optionParserFactory;
    this.ormProvider = ormProvider;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.permissionBackend = permissionBackend;
    this.pluginConfigEntries = pluginConfigEntries;
    this.projectCache = projectCache;
    this.psUtil = psUtil;
    this.performanceLoggers = performanceLoggers;
    this.publishCommentsOp = publishCommentsOp;
    this.queryProvider = queryProvider;
    this.receiveConfig = receiveConfig;
    this.refValidatorsFactory = refValidatorsFactory;
    this.replaceOpFactory = replaceOpFactory;
    this.requestListeners = requestListeners;
    this.retryHelper = retryHelper;
    this.requestScopePropagator = requestScopePropagator;
    this.seq = seq;
    this.subOpFactory = subOpFactory;
    this.tagCache = tagCache;
    this.projectConfigFactory = projectConfigFactory;
    this.setPrivateOpFactory = setPrivateOpFactory;

    // Assisted injected fields.
    this.projectState = projectState;
    this.user = user;
    this.receivePack = rp;
    // This repository instance in unwrapped, while the repository instance in
    // receivePack.getRepo() is wrapped in PermissionAwareRepository instance.
    this.repo = repository;

    // Immutable fields derived from constructor arguments.
    project = projectState.getProject();
    labelTypes = projectState.getLabelTypes();
    permissions = permissionBackend.user(user).project(project.getNameKey());
    rejectCommits = BanCommit.loadRejectCommitsMap(repo, rp.getRevWalk());

    // Collections populated during processing.
    errors = MultimapBuilder.linkedHashKeys().arrayListValues().build();
    messages = new ConcurrentLinkedQueue<>();
    pushOptions = LinkedListMultimap.create();
    replaceByChange = new LinkedHashMap<>();
    updateGroups = new ArrayList<>();

    used = false;

    this.allowProjectOwnersToChangeParent =
        config.getBoolean("receive", "allowProjectOwnersToChangeParent", false);

    // Other settings populated during processing.
    newChangeForAllNotInTarget =
        projectState.is(BooleanProjectConfig.CREATE_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET);

    // Handles for outputting back over the wire to the end user.
    this.messageSender = messageSender != null ? messageSender : new ReceivePackMessageSender();
    this.result = ReceiveCommitsResult.builder();
    this.loggingTags = ImmutableMap.of();

    // TODO(hiesel): Make this decision implicit once vetted
    boolean useRefCache = config.getBoolean("receive", "enableInMemoryRefCache", true);
    receivePackRefCache =
        useRefCache
            ? ReceivePackRefCache.withAdvertisedRefs(() -> allRefsWatcher.getAllRefs())
            : ReceivePackRefCache.noCache(receivePack.getRepository().getRefDatabase());
  }

  void init() {
    initializers.runEach(i -> i.init(projectState.getNameKey(), receivePack));
  }

  MessageSender getMessageSender() {
    return messageSender;
  }

  Project getProject() {
    return project;
  }

  private void addMessage(String message, ValidationMessage.Type type) {
    messages.add(new CommitValidationMessage(message, type));
  }

  private void addMessage(String message) {
    messages.add(new CommitValidationMessage(message, ValidationMessage.Type.OTHER));
  }

  private void addError(String error) {
    addMessage(error, ValidationMessage.Type.ERROR);
  }

  /**
   * Sends all messages which have been collected while processing the push to the client.
   *
   * <p><strong>Attention:</strong>{@link AsyncReceiveCommits} may call this method while {@link
   * #processCommands(Collection, MultiProgressMonitor)} is still running (if the execution of
   * processCommands takes too long and AsyncReceiveCommits gets a timeout). This means that local
   * variables that are accessed in this method must be thread-safe (otherwise we may hit a {@link
   * java.util.ConcurrentModificationException} if we read a variable here that at the same time is
   * updated by the background thread that still executes processCommands).
   */
  void sendMessages() {
    try (TraceContext traceContext =
        TraceContext.newTrace(
            loggingTags.containsKey(RequestId.Type.TRACE_ID.name()),
            loggingTags.get(RequestId.Type.TRACE_ID.name()),
            (tagName, traceId) -> {})) {
      loggingTags.forEach((tagName, tagValue) -> traceContext.addTag(tagName, tagValue));

      for (ValidationMessage m : messages) {
        String msg = m.getType().getPrefix() + m.getMessage();
        logger.atFine().log("Sending message: %s", msg);

        // Avoid calling sendError which will add its own error: prefix.
        messageSender.sendMessage(msg);
      }
    }
  }

  ReceiveCommitsResult processCommands(
      Collection<ReceiveCommand> commands, MultiProgressMonitor progress) throws StorageException {
    checkState(!used, "Tried to re-use a ReceiveCommits objects that is single-use only");
    parsePushOptions();
    int commandCount = commands.size();
    try (TraceContext traceContext =
            TraceContext.newTrace(
                tracePushOption.isPresent(),
                tracePushOption.orElse(null),
                (tagName, traceId) -> addMessage(tagName + ": " + traceId));
        TraceTimer traceTimer =
            newTimer("processCommands", Metadata.builder().resourceCount(commandCount));
        PerformanceLogContext performanceLogContext =
            new PerformanceLogContext(config, performanceLoggers)) {
      RequestInfo requestInfo =
          RequestInfo.builder(RequestInfo.RequestType.GIT_RECEIVE, user, traceContext)
              .project(project.getNameKey())
              .build();
      requestListeners.runEach(l -> l.onRequest(requestInfo));
      traceContext.addTag(RequestId.Type.RECEIVE_ID, new RequestId(project.getNameKey().get()));

      // Log the push options here, rather than in parsePushOptions(), so that they are included
      // into the trace if tracing is enabled.
      logger.atFine().log("push options: %s", receivePack.getPushOptions());

      Task commandProgress = progress.beginSubTask("refs", UNKNOWN);
      commands =
          commands.stream().map(c -> wrapReceiveCommand(c, commandProgress)).collect(toList());
      processCommandsUnsafe(commands, progress);
      rejectRemaining(commands, INTERNAL_SERVER_ERROR);

      // This sends error messages before the 'done' string of the progress monitor is sent.
      // Currently, the test framework relies on this ordering to understand if pushes completed
      // successfully.
      sendErrorMessages();

      commandProgress.end();
      progress.end();
      loggingTags = traceContext.getTags();
      logger.atFine().log("Processing commands done.");
    }
    return result.build();
  }

  // Process as many commands as possible, but may leave some commands in state NOT_ATTEMPTED.
  private void processCommandsUnsafe(
      Collection<ReceiveCommand> commands, MultiProgressMonitor progress) {
    logger.atFine().log("Calling user: %s", user.getLoggableName());
    logger.atFine().log("Groups: %s", user.getEffectiveGroups().getKnownGroups());

    if (!projectState.getProject().getState().permitsWrite()) {
      for (ReceiveCommand cmd : commands) {
        reject(cmd, "prohibited by Gerrit: project state does not permit write");
      }
      return;
    }

    logger.atFine().log("Parsing %d commands", commands.size());

    List<ReceiveCommand> magicCommands = new ArrayList<>();
    List<ReceiveCommand> regularCommands = new ArrayList<>();

    for (ReceiveCommand cmd : commands) {
      if (MagicBranch.isMagicBranch(cmd.getRefName())) {
        magicCommands.add(cmd);
      } else {
        regularCommands.add(cmd);
      }
    }

    if (!magicCommands.isEmpty() && !regularCommands.isEmpty()) {
      rejectRemaining(commands, "cannot combine normal pushes and magic pushes");
      return;
    }

    try {
      if (!regularCommands.isEmpty()) {
        handleRegularCommands(regularCommands, progress);
        return;
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

    Task newProgress = progress.beginSubTask("new", UNKNOWN);
    Task replaceProgress = progress.beginSubTask("updated", UNKNOWN);

    List<CreateRequest> newChanges = Collections.emptyList();
    try {
      if (magicBranch != null && magicBranch.cmd.getResult() == NOT_ATTEMPTED) {
        try {
          newChanges = selectNewAndReplacedChangesFromMagicBranch(newProgress);
        } catch (IOException e) {
          throw new StorageException("Failed to select new changes in " + project.getName(), e);
        }
      }

      // Commit validation has already happened, so any changes without Change-Id are for the
      // deprecated feature.
      warnAboutMissingChangeId(newChanges);
      preparePatchSetsForReplace(newChanges);
      insertChangesAndPatchSets(newChanges, replaceProgress);
    } finally {
      newProgress.end();
      replaceProgress.end();
    }

    queueSuccessMessages(newChanges);

    logger.atFine().log(
        "Command results: %s",
        lazy(() -> commands.stream().map(ReceiveCommits::commandToString).collect(joining(","))));
  }

  private void sendErrorMessages() {
    if (!errors.isEmpty()) {
      logger.atFine().log("Handling error conditions: %s", errors.keySet());
      for (String error : errors.keySet()) {
        receivePack.sendMessage("error: " + buildError(error, errors.get(error)));
      }
      receivePack.sendMessage(String.format("User: %s", user.getLoggableName()));
      receivePack.sendMessage(COMMAND_REJECTION_MESSAGE_FOOTER);
    }
  }

  private void handleRegularCommands(List<ReceiveCommand> cmds, MultiProgressMonitor progress)
      throws PermissionBackendException, IOException, NoSuchProjectException {
    try (TraceTimer traceTimer =
        newTimer("handleRegularCommands", Metadata.builder().resourceCount(cmds.size()))) {
      result.magicPush(false);
      for (ReceiveCommand cmd : cmds) {
        parseRegularCommand(cmd);
      }

      try (BatchUpdate bu =
              batchUpdateFactory.create(
                  project.getNameKey(), user.materializedCopy(), TimeUtil.nowTs());
          ObjectInserter ins = repo.newObjectInserter();
          ObjectReader reader = ins.newReader();
          RevWalk rw = new RevWalk(reader)) {
        bu.setRepository(repo, rw, ins);
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
        throw new StorageException(e);
      }

      Set<BranchNameKey> branches = new HashSet<>();
      for (ReceiveCommand c : cmds) {
        // Most post-update steps should happen in UpdateOneRefOp#postUpdate. The only steps that
        // should happen in this loops are things that can't happen within one BatchUpdate because
        // they involve kicking off an additional BatchUpdate.
        if (c.getResult() != OK) {
          continue;
        }
        if (isHead(c) || isConfig(c)) {
          switch (c.getType()) {
            case CREATE:
            case UPDATE:
            case UPDATE_NONFASTFORWARD:
              Task closeProgress = progress.beginSubTask("closed", UNKNOWN);
              autoCloseChanges(c, closeProgress);
              closeProgress.end();
              branches.add(BranchNameKey.create(project.getNameKey(), c.getRefName()));
              break;

            case DELETE:
              break;
          }
        }
      }

      // Update superproject gitlinks if required.
      if (!branches.isEmpty()) {
        try (MergeOpRepoManager orm = ormProvider.get()) {
          orm.setContext(TimeUtil.nowTs(), user, NotifyResolver.Result.none());
          SubmoduleOp op = subOpFactory.create(branches, orm);
          op.updateSuperProjects();
        } catch (RestApiException e) {
          logger.atWarning().withCause(e).log("Can't update the superprojects");
        }
      }
    }
  }

  /** Appends messages for successful change creation/updates. */
  private void queueSuccessMessages(List<CreateRequest> newChanges) {
    // adjacency list for commit => parent
    Map<String, String> adjList = new HashMap<>();
    for (CreateRequest cr : newChanges) {
      String parent = cr.commit.getParentCount() == 0 ? null : cr.commit.getParent(0).name();
      adjList.put(cr.commit.name(), parent);
    }
    for (ReplaceRequest rr : replaceByChange.values()) {
      String parent = null;
      if (rr.revCommit != null) {
        parent = rr.revCommit.getParentCount() == 0 ? null : rr.revCommit.getParent(0).name();
      }
      adjList.put(rr.newCommitId.name(), parent);
    }

    // get commits that are not parents
    Set<String> leafs = new TreeSet<>(adjList.keySet());
    leafs.removeAll(adjList.values());
    // go backwards from the last commit to its parent(s)
    Set<String> ordered = new LinkedHashSet<>();
    for (String leaf : leafs) {
      if (ordered.contains(leaf)) {
        continue;
      }
      while (leaf != null) {
        if (!ordered.contains(leaf)) {
          ordered.add(leaf);
        }
        leaf = adjList.get(leaf);
      }
    }
    // reverse the order to start with earliest commit
    List<String> orderedCommits = new ArrayList<>(ordered);
    Collections.reverse(orderedCommits);

    Map<String, CreateRequest> created =
        newChanges.stream()
            .filter(r -> r.change != null)
            .collect(Collectors.toMap(r -> r.commit.name(), r -> r));
    Map<String, ReplaceRequest> updated =
        replaceByChange.values().stream()
            .filter(r -> r.inputCommand.getResult() == OK)
            .collect(Collectors.toMap(r -> r.newCommitId.name(), r -> r));

    if (created.isEmpty() && updated.isEmpty()) {
      return;
    }

    addMessage("");
    addMessage("SUCCESS");
    addMessage("");

    boolean edit = false;
    Boolean isPrivate = null;
    Boolean wip = null;
    if (!updated.isEmpty()) {
      edit = magicBranch != null && magicBranch.edit;
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
    }

    for (String commit : orderedCommits) {
      if (created.get(commit) != null) {
        addCreatedMessage(created.get(commit));
      } else if (updated.get(commit) != null) {
        addReplacedMessage(updated.get(commit), edit, isPrivate, wip);
      }
    }
    addMessage("");
  }

  private void addCreatedMessage(CreateRequest c) {
    addMessage(
        changeFormatter.newChange(
            ChangeReportFormatter.Input.builder().setChange(c.change).build()));
  }

  private void addReplacedMessage(ReplaceRequest u, boolean edit, Boolean isPrivate, Boolean wip) {
    String subject;
    if (edit) {
      subject =
          u.revCommit == null ? u.notes.getChange().getSubject() : u.revCommit.getShortMessage();
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

  private void insertChangesAndPatchSets(List<CreateRequest> newChanges, Task replaceProgress) {
    try (TraceTimer traceTimer =
        newTimer(
            "insertChangesAndPatchSets", Metadata.builder().resourceCount(newChanges.size()))) {
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
                  project.getNameKey(), user.materializedCopy(), TimeUtil.nowTs());
          ObjectInserter ins = repo.newObjectInserter();
          ObjectReader reader = ins.newReader();
          RevWalk rw = new RevWalk(reader)) {
        bu.setRepository(repo, rw, ins);
        bu.setRefLogMessage("push");
        if (magicBranch != null) {
          bu.setNotify(magicBranch.getNotifyForNewChange());
        }

        logger.atFine().log("Adding %d replace requests", newChanges.size());
        for (ReplaceRequest replace : replaceByChange.values()) {
          replace.addOps(bu, replaceProgress);
          if (magicBranch != null) {
            bu.setNotifyHandling(replace.ontoChange, magicBranch.getNotifyHandling(replace.notes));
            if (magicBranch.shouldPublishComments()) {
              bu.addOp(
                  replace.notes.getChangeId(),
                  publishCommentsOp.create(replace.psId, project.getNameKey()));
            }
          }
        }

        logger.atFine().log("Adding %d create requests", newChanges.size());
        for (CreateRequest create : newChanges) {
          create.addOps(bu);
        }

        logger.atFine().log("Adding %d group update requests", newChanges.size());
        updateGroups.forEach(r -> r.addOps(bu));

        logger.atFine().log("Executing batch");

        try {
          retryHelper
              .changeUpdate(
                  "insertChangesAndPatchSets",
                  () -> {
                    bu.execute();
                    return null;
                  })
              .call();
        } catch (Exception e) {
          throw asRestApiException(e);
        }

        replaceByChange.values().stream()
            .forEach(
                req ->
                    result.addChange(ReceiveCommitsResult.ChangeStatus.REPLACED, req.ontoChange));
        newChanges.stream()
            .forEach(
                req -> result.addChange(ReceiveCommitsResult.ChangeStatus.CREATED, req.changeId));

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
      } catch (BadRequestException | UnprocessableEntityException | AuthException e) {
        logger.atFine().withCause(e).log("Rejecting due to client error");
        reject(magicBranchCmd, e.getMessage());
      } catch (RestApiException | IOException e) {
        throw new StorageException("Can't insert change/patch set for " + project.getName(), e);
      }

      if (magicBranch != null && magicBranch.submit) {
        try {
          submit(newChanges, replaceByChange.values());
        } catch (ResourceConflictException e) {
          addError(e.getMessage());
          reject(magicBranchCmd, "conflict");
        } catch (RestApiException
            | StorageException
            | UpdateException
            | IOException
            | ConfigInvalidException
            | PermissionBackendException e) {
          logger.atSevere().withCause(e).log("Error submitting changes to %s", project.getName());
          reject(magicBranchCmd, "error during submit");
        }
      }
    }
  }

  private String buildError(String error, List<String> branches) {
    StringBuilder sb = new StringBuilder();
    if (branches.size() == 1) {
      String branch = branches.get(0);
      sb.append("branch ").append(branch).append(":\n");
      // As of 2020, there are still many git-review <1.27 installations in the wild.
      // These users will see failures as their old git-review assumes that
      // `refs/publish/...` is still magic, which it isn't. As Gerrit's default error messages are
      // misleading for these users, we hint them at upgrading their git-review.
      if (branch.startsWith("refs/publish/")) {
        sb.append("If you are using git-review, update to at least git-review 1.27. Otherwise:\n");
      }
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
      // CmdLineParser behavior used by MagicBranchInput.
      String value = Iterables.getLast(noteDbValues);
      noteDbPushOption = NoteDbPushOption.parse(value);
      if (!noteDbPushOption.isPresent()) {
        addError("Invalid value in -o " + NoteDbPushOption.OPTION_NAME + "=" + value);
      }
    } else {
      noteDbPushOption = Optional.of(NoteDbPushOption.DISALLOW);
    }

    List<String> traceValues = pushOptions.get("trace");
    if (!traceValues.isEmpty()) {
      tracePushOption = Optional.of(Iterables.getLast(traceValues));
    } else {
      tracePushOption = Optional.empty();
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
    try (TraceTimer traceTimer = newTimer("parseRegularCommand")) {
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
        validateConfigPush(cmd);
      }
    }
  }

  /** Validates a push to refs/meta/config, and reject the command if it fails. */
  private void validateConfigPush(ReceiveCommand cmd) throws PermissionBackendException {
    try (TraceTimer traceTimer = newTimer("validateConfigPush")) {
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
            ProjectConfig cfg = projectConfigFactory.create(project.getNameKey());
            cfg.load(project.getNameKey(), receivePack.getRevWalk(), cmd.getNewId());
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
                if (allowProjectOwnersToChangeParent) {
                  try {
                    permissionBackend
                        .user(user)
                        .project(project.getNameKey())
                        .check(ProjectPermission.WRITE_CONFIG);
                  } catch (AuthException e) {
                    reject(
                        cmd, "invalid project configuration: only project owners can set parent");
                    return;
                  }
                } else {
                  try {
                    permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);
                  } catch (AuthException e) {
                    reject(cmd, "invalid project configuration: only Gerrit admin can set parent");
                    return;
                  }
                }
              }

              if (!projectCache.get(newParent).isPresent()) {
                reject(cmd, "invalid project configuration: parent does not exist");
                return;
              }
            }
            validatePluginConfig(cmd, cfg);
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
      }
    }
  }

  /**
   * validates a push to refs/meta/config for plugin configuration, and rejects the push if it
   * fails.
   */
  private void validatePluginConfig(ReceiveCommand cmd, ProjectConfig cfg) {
    for (Extension<ProjectConfigEntry> e : pluginConfigEntries) {
      PluginConfig pluginCfg = cfg.getPluginConfig(e.getPluginName()).asPluginConfig();
      ProjectConfigEntry configEntry = e.getProvider().get();
      String value = pluginCfg.getString(e.getExportName());
      String oldValue =
          projectState.getConfig().getPluginConfig(e.getPluginName()).getString(e.getExportName());
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
  }

  private void parseCreate(ReceiveCommand cmd)
      throws PermissionBackendException, NoSuchProjectException, IOException {
    try (TraceTimer traceTimer = newTimer("parseCreate")) {
      RevObject obj;
      try {
        obj = receivePack.getRevWalk().parseAny(cmd.getNewId());
      } catch (IOException e) {
        throw new StorageException(
            String.format(
                "Invalid object %s for %s creation", cmd.getNewId().name(), cmd.getRefName()),
            e);
      }
      logger.atFine().log("Creating %s", cmd);

      if (isHead(cmd) && !isCommit(cmd)) {
        return;
      }

      BranchNameKey branch = BranchNameKey.create(project.getName(), cmd.getRefName());
      try {
        // Must pass explicit user instead of injecting a provider into CreateRefControl, since
        // Provider<CurrentUser> within ReceiveCommits will always return anonymous.
        createRefControl.checkCreateRef(
            Providers.of(user), receivePack.getRepository(), branch, obj);
      } catch (AuthException denied) {
        rejectProhibited(cmd, denied);
        return;
      } catch (ResourceConflictException denied) {
        reject(cmd, "prohibited by Gerrit: " + denied.getMessage());
        return;
      }

      if (validRefOperation(cmd)) {
        validateRegularPushCommits(
            BranchNameKey.create(project.getNameKey(), cmd.getRefName()), cmd);
      }
    }
  }

  private void parseUpdate(ReceiveCommand cmd) throws PermissionBackendException {
    try (TraceTimer traceTimer = TraceContext.newTimer("parseUpdate")) {
      logger.atFine().log("Updating %s", cmd);
      Optional<AuthException> err = checkRefPermission(cmd, RefPermission.UPDATE);
      if (!err.isPresent()) {
        if (isHead(cmd) && !isCommit(cmd)) {
          reject(cmd, "head must point to commit");
          return;
        }
        if (validRefOperation(cmd)) {
          validateRegularPushCommits(
              BranchNameKey.create(project.getNameKey(), cmd.getRefName()), cmd);
        }
      } else {
        rejectProhibited(cmd, err.get());
      }
    }
  }

  private boolean isCommit(ReceiveCommand cmd) {
    RevObject obj;
    try {
      obj = receivePack.getRevWalk().parseAny(cmd.getNewId());
    } catch (IOException e) {
      throw new StorageException(
          String.format(
              "Invalid object %s for %s creation", cmd.getNewId().name(), cmd.getRefName()),
          e);
    }

    if (obj instanceof RevCommit) {
      return true;
    }
    reject(cmd, "not a commit");
    return false;
  }

  private void parseDelete(ReceiveCommand cmd) throws PermissionBackendException {
    try (TraceTimer traceTimer = newTimer("parseDelete")) {
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
  }

  private void parseRewind(ReceiveCommand cmd) throws PermissionBackendException {
    try (TraceTimer traceTimer = newTimer("parseRewind")) {
      try {
        receivePack.getRevWalk().parseCommit(cmd.getNewId());
      } catch (IOException e) {
        throw new StorageException(
            String.format(
                "Invalid object %s for %s creation", cmd.getNewId().name(), cmd.getRefName()),
            e);
      }
      logger.atFine().log("Rewinding %s", cmd);

      if (!validRefOperation(cmd)) {
        return;
      }
      validateRegularPushCommits(BranchNameKey.create(project.getNameKey(), cmd.getRefName()), cmd);
      if (cmd.getResult() != NOT_ATTEMPTED) {
        return;
      }

      Optional<AuthException> err = checkRefPermission(cmd, RefPermission.FORCE_UPDATE);
      if (err.isPresent()) {
        rejectProhibited(cmd, err.get());
      }
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

    private final IdentifiedUser user;
    private final ProjectState projectState;
    private final boolean defaultPublishComments;

    final ReceiveCommand cmd;
    final LabelTypes labelTypes;
    /**
     * Draft comments are published with the commit iff {@code --publish-comments} is set. All
     * drafts are withheld (overriding the option) if at least one of the following conditions are
     * met:
     *
     * <ul>
     *   <li>Installed {@link CommentValidator} plugins reject one or more draft comments.
     *   <li>One or more comments exceed the maximum comment size (see {@link
     *       CommentSizeValidator}).
     *   <li>The maximum number of comments would be exceeded (see {@link CommentCountValidator}).
     * </ul>
     */
    private boolean withholdComments = false;

    BranchNameKey dest;
    PermissionBackend.ForRef perm;
    Set<String> reviewer = Sets.newLinkedHashSet();
    Set<String> cc = Sets.newLinkedHashSet();
    Map<String, Short> labels = new HashMap<>();
    String message;
    List<RevCommit> baseCommit;
    CmdLineParser cmdLineParser;
    Set<String> hashtags = new HashSet<>();

    @Option(name = "--trace", metaVar = "NAME", usage = "enable tracing")
    String trace;

    @Option(name = "--base", metaVar = "BASE", usage = "merge base of changes")
    List<ObjectId> base;

    @Option(name = "--topic", metaVar = "NAME", usage = "attach topic to changes")
    String topic;

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
    private NotifyHandling notifyHandling;

    @Option(
        name = "--notify-to",
        metaVar = "USER",
        usage = "user that should be notified one time by email")
    List<Account.Id> notifyTo = new ArrayList<>();

    @Option(
        name = "--notify-cc",
        metaVar = "USER",
        usage = "user that should be CC'd one time by email")
    List<Account.Id> notifyCc = new ArrayList<>();

    @Option(
        name = "--notify-bcc",
        metaVar = "USER",
        usage = "user that should be BCC'd one time by email")
    List<Account.Id> notifyBcc = new ArrayList<>();

    @Option(
        name = "--reviewer",
        aliases = {"-r"},
        metaVar = "REVIEWER",
        usage = "add reviewer to changes")
    void reviewer(String str) {
      reviewer.add(str);
    }

    @Option(name = "--cc", metaVar = "CC", usage = "add CC to changes")
    void cc(String str) {
      cc.add(str);
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
    void addHashtag(String token) {
      String hashtag = cleanupHashtag(token);
      if (!hashtag.isEmpty()) {
        hashtags.add(hashtag);
      }
    }

    @UsedAt(UsedAt.Project.GOOGLE)
    @Option(name = "--create-cod-token", usage = "create a token for consistency-on-demand")
    private boolean createCodToken;

    MagicBranchInput(
        IdentifiedUser user, ProjectState projectState, ReceiveCommand cmd, LabelTypes labelTypes) {
      this.user = user;
      this.projectState = projectState;
      this.cmd = cmd;
      this.labelTypes = labelTypes;
      GeneralPreferencesInfo prefs = user.state().generalPreferences();
      this.defaultPublishComments =
          prefs != null
              ? firstNonNull(user.state().generalPreferences().publishCommentsOnPush, false)
              : false;
    }

    /**
     * Get reviewer strings from magic branch options, combined with additional recipients computed
     * from some other place.
     *
     * <p>The set of reviewers on a change includes strings passed explicitly via options as well as
     * account IDs computed from the commit message itself.
     *
     * @param additionalRecipients recipients parsed from the commit.
     * @return set of reviewer strings to pass to {@code ReviewerAdder}.
     */
    ImmutableSet<String> getCombinedReviewers(MailRecipients additionalRecipients) {
      return getCombinedReviewers(reviewer, additionalRecipients.getReviewers());
    }

    /**
     * Get CC strings from magic branch options, combined with additional recipients computed from
     * some other place.
     *
     * <p>The set of CCs on a change includes strings passed explicitly via options as well as
     * account IDs computed from the commit message itself.
     *
     * @param additionalRecipients recipients parsed from the commit.
     * @return set of CC strings to pass to {@code ReviewerAdder}.
     */
    ImmutableSet<String> getCombinedCcs(MailRecipients additionalRecipients) {
      return getCombinedReviewers(cc, additionalRecipients.getCcOnly());
    }

    private static ImmutableSet<String> getCombinedReviewers(
        Set<String> strings, Set<Account.Id> ids) {
      return Streams.concat(strings.stream(), ids.stream().map(Account.Id::toString))
          .collect(toImmutableSet());
    }

    void setWithholdComments(boolean withholdComments) {
      this.withholdComments = withholdComments;
    }

    boolean shouldPublishComments() {
      if (withholdComments) {
        // Validation messages of type WARNING have already been added, now withhold the comments.
        return false;
      }
      if (publishComments) {
        return true;
      }
      if (noPublishComments) {
        return false;
      }
      return defaultPublishComments;
    }

    /**
     * returns the destination ref of the magic branch, and populates options in the cmdLineParser.
     */
    String parse(ListMultimap<String, String> pushOptions) throws CmdLineException {
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
      return ref;
    }

    public boolean shouldSetWorkInProgressOnNewChanges() {
      // When wip or ready explicitly provided, leave it as is.
      if (workInProgress) {
        return true;
      }
      if (ready) {
        return false;
      }

      return projectState.is(BooleanProjectConfig.WORK_IN_PROGRESS_BY_DEFAULT)
          || firstNonNull(user.state().generalPreferences().workInProgressByDefault, false);
    }

    NotifyResolver.Result getNotifyForNewChange() {
      return NotifyResolver.Result.create(
          firstNonNull(
              notifyHandling,
              shouldSetWorkInProgressOnNewChanges() ? NotifyHandling.OWNER : NotifyHandling.ALL),
          ImmutableSetMultimap.<RecipientType, Account.Id>builder()
              .putAll(RecipientType.TO, notifyTo)
              .putAll(RecipientType.CC, notifyCc)
              .putAll(RecipientType.BCC, notifyBcc)
              .build());
    }

    NotifyHandling getNotifyHandling(ChangeNotes notes) {
      requireNonNull(notes);
      if (notifyHandling != null) {
        return notifyHandling;
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
  private void parseMagicBranch(ReceiveCommand cmd) throws PermissionBackendException, IOException {
    try (TraceTimer traceTimer = newTimer("parseMagicBranch")) {
      logger.atFine().log("Found magic branch %s", cmd.getRefName());
      MagicBranchInput magicBranch = new MagicBranchInput(user, projectState, cmd, labelTypes);

      String ref;
      magicBranch.cmdLineParser = optionParserFactory.create(magicBranch);

      try {
        ref = magicBranch.parse(pushOptions);
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
      // Pushing changes for review usually requires that the target branch exists, but there is an
      // exception for the branch to which HEAD points to and for refs/meta/config. Pushing for
      // review to these branches is allowed even if the branch does not exist yet. This allows to
      // push initial code for review to an empty repository and to review an initial project
      // configuration.
      if (receivePackRefCache.exactRef(ref) == null
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

      magicBranch.dest = BranchNameKey.create(project.getNameKey(), ref);
      magicBranch.perm = permissions.ref(ref);

      Optional<AuthException> err =
          checkRefPermission(magicBranch.perm, RefPermission.CREATE_CHANGE);
      if (err.isPresent()) {
        rejectProhibited(cmd, err.get());
        return;
      }

      if (magicBranch.isPrivate && magicBranch.removePrivate) {
        reject(cmd, "the options 'private' and 'remove-private' are mutually exclusive");
        return;
      }

      boolean privateByDefault =
          projectCache
              .get(project.getNameKey())
              .orElseThrow(illegalState(project.getNameKey()))
              .is(BooleanProjectConfig.PRIVATE_BY_DEFAULT);
      setChangeAsPrivate =
          magicBranch.isPrivate || (privateByDefault && !magicBranch.removePrivate);

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
        logger.atSevere().withCause(ex).log(
            "Invalid pack upload; one or more objects weren't sent");
        return;
      }

      String destBranch = magicBranch.dest.branch();
      try {
        if (magicBranch.merged) {
          if (magicBranch.base != null) {
            reject(cmd, "cannot use merged with base");
            return;
          }
          Ref refTip = receivePackRefCache.exactRef(magicBranch.dest.branch());
          if (refTip == null) {
            reject(cmd, magicBranch.dest.branch() + " not found");
            return;
          }
          RevCommit branchTip = receivePack.getRevWalk().parseCommit(refTip.getObjectId());
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
              throw new StorageException(
                  String.format("Project %s cannot read %s", project.getName(), id.name()), e);
            }
          }
        } else if (newChangeForAllNotInTarget) {
          Ref refTip = receivePackRefCache.exactRef(magicBranch.dest.branch());
          if (refTip != null) {
            RevCommit branchTip = receivePack.getRevWalk().parseCommit(refTip.getObjectId());
            magicBranch.baseCommit = Collections.singletonList(branchTip);
            logger.atFine().log("Set baseCommit = %s", magicBranch.baseCommit.get(0).name());
          } else {
            // The target branch does not exist. Usually pushing changes for review requires that
            // the
            // target branch exists, but there is an exception for the branch to which HEAD points
            // to
            // and for refs/meta/config. Pushing for review to these branches is allowed even if the
            // branch does not exist yet. This allows to push initial code for review to an empty
            // repository and to review an initial project configuration.
            if (!ref.equals(readHEAD(repo)) && !ref.equals(RefNames.REFS_CONFIG)) {
              reject(cmd, magicBranch.dest.branch() + " not found");
              return;
            }
          }
        }
      } catch (IOException e) {
        throw new StorageException(
            String.format("Error walking to %s in project %s", destBranch, project.getName()), e);
      }

      if (validateConnected(magicBranch.cmd, magicBranch.dest, tip)) {
        this.magicBranch = magicBranch;
        this.result.magicPush(true);
      }
    }
  }

  // Validate that the new commits are connected with the target
  // branch.  If they aren't, we want to abort. We do this check by
  // looking to see if we can compute a merge base between the new
  // commits and the target branch head.
  private boolean validateConnected(ReceiveCommand cmd, BranchNameKey dest, RevCommit tip) {
    try (TraceTimer traceTimer =
        newTimer("validateConnected", Metadata.builder().branchName(dest.branch()))) {
      RevWalk walk = receivePack.getRevWalk();
      try {
        Ref targetRef = receivePackRefCache.exactRef(dest.branch());
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
            reject(cmd, "no common ancestry");
            return false;
          }
        } finally {
          walk.reset();
          walk.setRevFilter(oldRevFilter);
        }
      } catch (IOException e) {
        cmd.setResult(REJECTED_MISSING_OBJECT);
        logger.atSevere().withCause(e).log("Invalid pack upload; one or more objects weren't sent");
        return false;
      }
      return true;
    }
  }

  private static String readHEAD(Repository repo) {
    try {
      String head = repo.getFullBranch();
      logger.atFine().log("HEAD = %s", head);
      return head;
    } catch (IOException e) {
      throw new StorageException("Cannot read HEAD symref", e);
    }
  }

  /**
   * Update an existing change. If draft comments are to be published, these are validated and may
   * be withheld.
   *
   * @return True if the command succeeded, false if it was rejected.
   */
  private boolean requestReplaceAndValidateComments(
      ReceiveCommand cmd, boolean checkMergedInto, Change change, RevCommit newCommit)
      throws IOException {
    try (TraceTimer traceTimer = newTimer("requestReplaceAndValidateComments")) {
      if (change.isClosed()) {
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

      if (magicBranch != null && magicBranch.shouldPublishComments()) {
        List<HumanComment> drafts =
            commentsUtil.draftByChangeAuthor(
                notesFactory.createChecked(change), user.getAccountId());
        ImmutableList<CommentForValidation> draftsForValidation =
            drafts.stream()
                .map(
                    comment ->
                        CommentForValidation.create(
                            CommentSource.HUMAN,
                            comment.lineNbr > 0
                                ? CommentType.INLINE_COMMENT
                                : CommentType.FILE_COMMENT,
                            comment.message,
                            comment.message.length()))
                .collect(toImmutableList());
        CommentValidationContext ctx =
            CommentValidationContext.create(change.getChangeId(), change.getProject().get());
        ImmutableList<CommentValidationFailure> commentValidationFailures =
            PublishCommentUtil.findInvalidComments(ctx, commentValidators, draftsForValidation);
        magicBranch.setWithholdComments(!commentValidationFailures.isEmpty());
        commentValidationFailures.forEach(
            failure ->
                addMessage(
                    "Comment validation failure: " + failure.getMessage(),
                    ValidationMessage.Type.WARNING));
      }

      replaceByChange.put(req.ontoChange, req);
      return true;
    }
  }

  private void warnAboutMissingChangeId(List<CreateRequest> newChanges) {
    for (CreateRequest create : newChanges) {
      try {
        receivePack.getRevWalk().parseBody(create.commit);
      } catch (IOException e) {
        throw new StorageException("Can't parse commit", e);
      }
      List<String> idList = create.commit.getFooterLines(FooterConstants.CHANGE_ID);

      if (idList.isEmpty()) {
        messages.add(
            new ValidationMessage("warning: pushing without Change-Id is deprecated", false));
        break;
      }
    }
  }

  private List<CreateRequest> selectNewAndReplacedChangesFromMagicBranch(Task newProgress)
      throws IOException {
    try (TraceTimer traceTimer = newTimer("selectNewAndReplacedChangesFromMagicBranch")) {
      logger.atFine().log("Finding new and replaced changes");
      List<CreateRequest> newChanges = new ArrayList<>();

      GroupCollector groupCollector =
          GroupCollector.create(receivePackRefCache, psUtil, notesFactory, project.getNameKey());

      BranchCommitValidator validator =
          commitValidatorFactory.create(projectState, magicBranch.dest, user);

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
                    .orElseThrow(illegalState(project.getNameKey()))
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
          Collection<Ref> existingRefs =
              receivePackRefCache.tipsFromObjectId(c, RefNames.REFS_CHANGES);

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
            existingRefs.stream()
                .map(r -> PatchSet.Id.fromRef(r.getName()))
                .filter(Objects::nonNull)
                .forEach(i -> updateGroups.add(new UpdateGroupsRequest(i, c)));
            if (!(newChangeForAllNotInTarget || magicBranch.base != null)) {
              continue;
            }
          }

          List<String> idList = c.getFooterLines(FooterConstants.CHANGE_ID);
          if (!idList.isEmpty()) {
            pending.put(c, lookupByChangeKey(c, Change.key(idList.get(idList.size() - 1).trim())));
          } else {
            pending.put(c, lookupByCommit(c));
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

            logger.atFine().log(
                "Creating new change for %s even though it is already tracked", name);
          }

          BranchCommitValidator.Result validationResult =
              validator.validateCommit(
                  receivePack.getRevWalk().getObjectReader(),
                  magicBranch.cmd,
                  c,
                  magicBranch.merged,
                  rejectCommits,
                  null);
          messages.addAll(validationResult.messages());
          if (!validationResult.isValid()) {
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
            newChanges.add(new CreateRequest(c, magicBranch.dest.branch(), newProgress));
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

            ObjectId currentPs = changes.get(0).currentPatchSet().commitId();
            // If Commit is already current PatchSet of target Change.
            if (p.commit.equals(currentPs)) {
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
            if (requestReplaceAndValidateComments(
                magicBranch.cmd, false, changes.get(0).change(), p.commit)) {
              continue;
            }
            return Collections.emptyList();
          }

          if (changes.isEmpty()) {
            if (!isValidChangeId(p.changeKey.get())) {
              reject(magicBranch.cmd, "invalid Change-Id");
              return Collections.emptyList();
            }

            // In case the change look up from the index failed,
            // double check against the existing refs
            if (foundInExistingRef(
                receivePackRefCache.tipsFromObjectId(p.commit, RefNames.REFS_CHANGES))) {
              if (pending.size() == 1) {
                reject(magicBranch.cmd, "commit(s) already exists (as current patchset)");
                return Collections.emptyList();
              }
              itr.remove();
              continue;
            }
            newChangeIds.add(p.changeKey);
          }
          newChanges.add(new CreateRequest(p.commit, magicBranch.dest.branch(), newProgress));
        }
        logger.atFine().log(
            "Finished deferred lookups with %d updates and %d new changes",
            replaceByChange.size(), newChanges.size());
      } catch (IOException e) {
        // Should never happen, the core receive process would have
        // identified the missing object earlier before we got control.
        throw new StorageException("Invalid pack upload; one or more objects weren't sent", e);
      }

      if (newChanges.isEmpty() && replaceByChange.isEmpty()) {
        reject(magicBranch.cmd, "no new changes");
        return Collections.emptyList();
      }
      if (!newChanges.isEmpty() && magicBranch.edit) {
        reject(magicBranch.cmd, "edit is not supported for new changes");
        return newChanges;
      }

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
      return newChanges;
    }
  }

  private boolean foundInExistingRef(Collection<Ref> existingRefs) {
    try (TraceTimer traceTimer = newTimer("foundInExistingRef")) {
      for (Ref ref : existingRefs) {
        ChangeNotes notes =
            notesFactory.create(project.getNameKey(), Change.Id.fromRef(ref.getName()));
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
  }

  private RevCommit setUpWalkForSelectingChanges() throws IOException {
    try (TraceTimer traceTimer = newTimer("setUpWalkForSelectingChanges")) {
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
        markHeadsAsUninteresting(rw, magicBranch.dest != null ? magicBranch.dest.branch() : null);
      }
      return start;
    }
  }

  private void markExplicitBasesUninteresting() throws IOException {
    try (TraceTimer traceTimer = newTimer("markExplicitBasesUninteresting")) {
      logger.atFine().log("Marking %d base commits uninteresting", magicBranch.baseCommit.size());
      for (RevCommit c : magicBranch.baseCommit) {
        receivePack.getRevWalk().markUninteresting(c);
      }
      Ref targetRef = receivePackRefCache.exactRef(magicBranch.dest.branch());
      if (targetRef != null) {
        logger.atFine().log(
            "Marking target ref %s (%s) uninteresting",
            magicBranch.dest.branch(), targetRef.getObjectId().name());
        receivePack
            .getRevWalk()
            .markUninteresting(receivePack.getRevWalk().parseCommit(targetRef.getObjectId()));
      }
    }
  }

  private void rejectImplicitMerges(Set<RevCommit> mergedParents) throws IOException {
    try (TraceTimer traceTimer = newTimer("rejectImplicitMerges")) {
      if (!mergedParents.isEmpty()) {
        Ref targetRef = receivePackRefCache.exactRef(magicBranch.dest.branch());
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
                      "Implicit Merge of "
                          + abbreviateName(c, rw.getObjectReader())
                          + " "
                          + c.getShortMessage(),
                      ValidationMessage.Type.ERROR));
            }
            reject(magicBranch.cmd, "implicit merges detected");
          }
        }
      }
    }
  }

  // Mark all branch tips as uninteresting in the given revwalk,
  // so we get only the new commits when walking rw.
  private void markHeadsAsUninteresting(RevWalk rw, @Nullable String forRef) throws IOException {
    try (TraceTimer traceTimer =
        newTimer("markHeadsAsUninteresting", Metadata.builder().branchName(forRef))) {
      int i = 0;
      for (Ref ref :
          Iterables.concat(
              receivePackRefCache.byPrefix(R_HEADS),
              Collections.singletonList(receivePackRefCache.exactRef(forRef)))) {
        if (ref != null && ref.getObjectId() != null) {
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
  }

  private static boolean isValidChangeId(String idStr) {
    return idStr.matches("^I[0-9a-fA-F]{40}$") && !idStr.matches("^I00*$");
  }

  private static class ChangeLookup {
    final RevCommit commit;

    @Nullable final Change.Key changeKey;
    final List<ChangeData> destChanges;

    ChangeLookup(RevCommit c, @Nullable Change.Key key, final List<ChangeData> destChanges) {
      this.commit = c;
      this.changeKey = key;
      this.destChanges = destChanges;
    }
  }

  private ChangeLookup lookupByChangeKey(RevCommit c, Change.Key key) {
    try (TraceTimer traceTimer = newTimer("lookupByChangeKey")) {
      return new ChangeLookup(c, key, queryProvider.get().byBranchKey(magicBranch.dest, key));
    }
  }

  private ChangeLookup lookupByCommit(RevCommit c) {
    try (TraceTimer traceTimer = newTimer("lookupByCommit")) {
      return new ChangeLookup(
          c, null, queryProvider.get().byBranchCommit(magicBranch.dest, c.getName()));
    }
  }

  /** Represents a commit for which a Change should be created. */
  private class CreateRequest {
    final RevCommit commit;
    final Task progress;
    final String refName;

    Change.Id changeId;
    ReceiveCommand cmd;
    ChangeInserter ins;
    List<String> groups = ImmutableList.of();

    Change change;

    CreateRequest(RevCommit commit, String refName, Task progress) {
      this.commit = commit;
      this.refName = refName;
      this.progress = progress;
    }

    private void setChangeId(int id) {
      try (TraceTimer traceTimer = newTimer(CreateRequest.class, "setChangeId")) {
        changeId = Change.id(id);
        ins =
            changeInserterFactory
                .create(changeId, commit, refName)
                .setTopic(magicBranch.topic)
                .setPrivate(setChangeAsPrivate)
                .setWorkInProgress(magicBranch.shouldSetWorkInProgressOnNewChanges())
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
    }

    private void addOps(BatchUpdate bu) throws RestApiException {
      try (TraceTimer traceTimer = newTimer(CreateRequest.class, "addOps")) {
        checkState(changeId != null, "must call setChangeId before addOps");
        try {
          RevWalk rw = receivePack.getRevWalk();
          rw.parseBody(commit);
          final PatchSet.Id psId = ins.setGroups(groups).getPatchSetId();
          Account.Id me = user.getAccountId();
          List<FooterLine> footerLines = commit.getFooterLines();
          requireNonNull(magicBranch);

          // TODO(dborowitz): Support reviewers by email from footers? Maybe not: kernel developers
          // with AOSP accounts already complain about these notifications, and that would make it
          // worse. Might be better to get rid of the feature entirely:
          // https://groups.google.com/d/topic/repo-discuss/tIFxY7L4DXk/discussion
          MailRecipients fromFooters = getRecipientsFromFooters(accountResolver, footerLines);
          fromFooters.remove(me);

          Map<String, Short> approvals = magicBranch.labels;
          StringBuilder msg =
              new StringBuilder(
                  ApprovalsUtil.renderMessageWithApprovals(
                      psId.get(), approvals, Collections.emptyMap()));
          msg.append('.');
          if (!Strings.isNullOrEmpty(magicBranch.message)) {
            msg.append("\n").append(magicBranch.message);
          }

          bu.setNotify(magicBranch.getNotifyForNewChange());
          bu.insertChange(
              ins.setReviewersAndCcsAsStrings(
                      magicBranch.getCombinedReviewers(fromFooters),
                      magicBranch.getCombinedCcs(fromFooters))
                  .setApprovals(approvals)
                  .setMessage(msg.toString())
                  .setRequestScopePropagator(requestScopePropagator)
                  .setSendMail(true)
                  .setPatchSetDescription(magicBranch.message));
          if (!magicBranch.hashtags.isEmpty()) {
            // Any change owner is allowed to add hashtags when creating a change.
            bu.addOp(
                changeId,
                hashtagsFactory
                    .create(new HashtagsInput(magicBranch.hashtags))
                    .setFireEvent(false));
          }
          if (!Strings.isNullOrEmpty(magicBranch.topic)) {
            bu.addOp(changeId, new SetTopicOp(magicBranch.topic));
          }
          bu.addOp(
              changeId,
              new BatchUpdateOp() {
                @Override
                public boolean updateChange(ChangeContext ctx) {
                  CreateRequest.this.change = ctx.getChange();
                  return false;
                }
              });
          bu.addOp(changeId, new ChangeProgressOp(progress));
        } catch (Exception e) {
          throw asRestApiException(e);
        }
      }
    }
  }

  private void submit(Collection<CreateRequest> create, Collection<ReplaceRequest> replace)
      throws RestApiException, UpdateException, IOException, ConfigInvalidException,
          PermissionBackendException {
    try (TraceTimer traceTimer = newTimer("submit")) {
      Map<ObjectId, Change> bySha = Maps.newHashMapWithExpectedSize(create.size() + replace.size());
      for (CreateRequest r : create) {
        requireNonNull(
            r.change,
            () -> String.format("cannot submit new change %s; op may not have run", r.changeId));
        bySha.put(r.commit, r.change);
      }
      for (ReplaceRequest r : replace) {
        bySha.put(r.newCommitId, r.notes.getChange());
      }
      Change tipChange = bySha.get(magicBranch.cmd.getNewId());
      requireNonNull(
          tipChange,
          () ->
              String.format(
                  "tip of push does not correspond to a change; found these changes: %s", bySha));
      logger.atFine().log(
          "Processing submit with tip change %s (%s)",
          tipChange.getId(), magicBranch.cmd.getNewId());
      try (MergeOp op = mergeOpProvider.get()) {
        SubmitInput submitInput = new SubmitInput();
        submitInput.notify = magicBranch.notifyHandling;
        submitInput.notifyDetails = new HashMap<>();
        submitInput.notifyDetails.put(
            RecipientType.TO,
            new NotifyInfo(magicBranch.notifyTo.stream().map(Object::toString).collect(toList())));
        submitInput.notifyDetails.put(
            RecipientType.CC,
            new NotifyInfo(magicBranch.notifyCc.stream().map(Object::toString).collect(toList())));
        submitInput.notifyDetails.put(
            RecipientType.BCC,
            new NotifyInfo(magicBranch.notifyBcc.stream().map(Object::toString).collect(toList())));
        op.merge(tipChange, user, false, submitInput, false);
      }
    }
  }

  private void preparePatchSetsForReplace(List<CreateRequest> newChanges) {
    try (TraceTimer traceTimer =
        newTimer(
            "preparePatchSetsForReplace", Metadata.builder().resourceCount(newChanges.size()))) {
      try {
        readChangesForReplace();
        for (ReplaceRequest req : replaceByChange.values()) {
          if (req.inputCommand.getResult() == NOT_ATTEMPTED) {
            req.validateNewPatchSet();
          }
        }
      } catch (IOException | PermissionBackendException e) {
        throw new StorageException(
            "Cannot read repository before replacement for project " + project.getName(), e);
      }
      logger.atFine().log("Read %d changes to replace", replaceByChange.size());

      if (magicBranch != null && magicBranch.cmd.getResult() != NOT_ATTEMPTED) {
        // Cancel creations tied to refs/for/ command.
        for (ReplaceRequest req : replaceByChange.values()) {
          if (req.inputCommand == magicBranch.cmd && req.cmd != null) {
            req.cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "aborted");
          }
        }
        for (CreateRequest req : newChanges) {
          req.cmd.setResult(ReceiveCommand.Result.REJECTED_OTHER_REASON, "aborted");
        }
      }
    }
  }

  private void readChangesForReplace() {
    try (TraceTimer traceTimer = newTimer("readChangesForReplace")) {
      Collection<ChangeNotes> allNotes =
          notesFactory.create(
              replaceByChange.values().stream().map(r -> r.ontoChange).collect(toList()));
      for (ChangeNotes notes : allNotes) {
        replaceByChange.get(notes.getChangeId()).notes = notes;
      }
    }
  }

  /** Represents a commit that should be stored in a new patchset of an existing change. */
  private class ReplaceRequest {
    final Change.Id ontoChange;
    final ObjectId newCommitId;
    final ReceiveCommand inputCommand;
    final boolean checkMergedInto;
    RevCommit revCommit;
    ChangeNotes notes;
    BiMap<RevCommit, PatchSet.Id> revisions;
    PatchSet.Id psId;
    ReceiveCommand prev;
    ReceiveCommand cmd;
    PatchSetInfo info;
    PatchSet.Id priorPatchSet;
    List<String> groups = ImmutableList.of();
    ReplaceOp replaceOp;

    ReplaceRequest(
        Change.Id toChange, RevCommit newCommit, ReceiveCommand cmd, boolean checkMergedInto)
        throws IOException {
      this.ontoChange = toChange;
      this.newCommitId = newCommit.copy();
      this.inputCommand = requireNonNull(cmd);
      this.checkMergedInto = checkMergedInto;

      try {
        revCommit = receivePack.getRevWalk().parseCommit(newCommitId);
      } catch (IOException e) {
        revCommit = null;
      }
      revisions = HashBiMap.create();
      for (Ref ref : receivePackRefCache.byPrefix(RefNames.changeRefPrefix(toChange))) {
        try {
          PatchSet.Id psId = PatchSet.Id.fromRef(ref.getName());
          if (psId != null) {
            revisions.forcePut(receivePack.getRevWalk().parseCommit(ref.getObjectId()), psId);
          }
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
     * @return whether the new commit is valid
     * @throws IOException
     * @throws PermissionBackendException
     */
    boolean validateNewPatchSet() throws IOException, PermissionBackendException {
      try (TraceTimer traceTimer = newTimer("validateNewPatchSet")) {
        if (!validateNewPatchSetNoteDb()) {
          return false;
        }
        sameTreeWarning();

        if (magicBranch != null) {
          validateMagicBranchWipStatusChange();
          if (inputCommand.getResult() != NOT_ATTEMPTED) {
            return false;
          }

          if (magicBranch.edit) {
            return newEdit();
          }
        }

        newPatchSet();
        return true;
      }
    }

    boolean validateNewPatchSetForAutoClose() throws IOException, PermissionBackendException {
      if (!validateNewPatchSetNoteDb()) {
        return false;
      }

      newPatchSet();
      return true;
    }

    /** Validates the new PS against permissions and notedb status. */
    private boolean validateNewPatchSetNoteDb() throws IOException, PermissionBackendException {
      try (TraceTimer traceTimer = newTimer("validateNewPatchSetNoteDb")) {
        if (notes == null) {
          reject(inputCommand, "change " + ontoChange + " not found");
          return false;
        }

        Change change = notes.getChange();
        priorPatchSet = change.currentPatchSetId();
        if (!revisions.containsValue(priorPatchSet)) {
          logger.atWarning().log(
              "Change %d is missing revision for patch set %s"
                  + " (it has revisions for these patch sets: %s)",
              change.getChangeId(),
              priorPatchSet.getId(),
              Iterables.toString(
                  revisions.values().stream()
                      .limit(100) // Enough for "normal" changes.
                      .map(PatchSet.Id::getId)
                      .collect(Collectors.toList())));
          reject(inputCommand, "change " + ontoChange + " missing revisions");
          return false;
        }

        RevCommit newCommit = receivePack.getRevWalk().parseCommit(newCommitId);

        // Not allowed to create a new patch set if the current patch set is locked.
        if (psUtil.isPatchSetLocked(notes)) {
          reject(inputCommand, "cannot add patch set to " + ontoChange + ".");
          return false;
        }

        try {
          permissions.change(notes).check(ChangePermission.ADD_PATCH_SET);
        } catch (AuthException no) {
          reject(inputCommand, "cannot add patch set to " + ontoChange + ".");
          return false;
        }

        if (change.isClosed()) {
          reject(inputCommand, "change " + ontoChange + " closed");
          return false;
        } else if (revisions.containsKey(newCommit)) {
          reject(inputCommand, "commit already exists (in the change)");
          return false;
        }

        List<Ref> existingChangesWithSameCommit =
            receivePackRefCache.tipsFromObjectId(newCommit, RefNames.REFS_CHANGES);
        if (!existingChangesWithSameCommit.isEmpty()) {
          // TODO(hiesel, hanwen): Remove this check entirely when Gerrit requires change IDs
          //  without the option to turn that off.
          reject(
              inputCommand,
              "commit already exists (in the project): "
                  + existingChangesWithSameCommit.get(0).getName());
          return false;
        }

        try (TraceTimer traceTimer2 = newTimer("validateNewPatchSetNoteDb#isMergedInto")) {
          for (RevCommit prior : revisions.keySet()) {
            // Don't allow a change to directly depend upon itself. This is a
            // very common error due to users making a new commit rather than
            // amending when trying to address review comments.
            if (receivePack.getRevWalk().isMergedInto(prior, newCommit)) {
              reject(inputCommand, SAME_CHANGE_ID_IN_MULTIPLE_CHANGES);
              return false;
            }
          }
        }

        return true;
      }
    }

    /** Validates whether the WIP change is allowed. Rejects inputCommand if not. */
    private void validateMagicBranchWipStatusChange() throws PermissionBackendException {
      Change change = notes.getChange();
      if ((magicBranch.workInProgress || magicBranch.ready)
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
          }
        }
      }
    }

    /** prints a warning if the new PS has the same tree as the previous commit. */
    private void sameTreeWarning() throws IOException {
      try (TraceTimer traceTimer = newTimer("sameTreeWarning")) {
        RevWalk rw = receivePack.getRevWalk();
        RevCommit newCommit = rw.parseCommit(newCommitId);
        RevCommit priorCommit = revisions.inverse().get(priorPatchSet);

        if (newCommit.getTree().equals(priorCommit.getTree())) {
          rw.parseBody(newCommit);
          rw.parseBody(priorCommit);
          boolean messageEq =
              Objects.equals(newCommit.getFullMessage(), priorCommit.getFullMessage());
          boolean parentsEq = parentsEqual(newCommit, priorCommit);
          boolean authorEq = authorEqual(newCommit, priorCommit);
          ObjectReader reader = receivePack.getRevWalk().getObjectReader();

          if (messageEq && parentsEq && authorEq) {
            addMessage(
                String.format(
                    "warning: no changes between prior commit %s and new commit %s",
                    abbreviateName(priorCommit, reader), abbreviateName(newCommit, reader)));
          } else {
            StringBuilder msg = new StringBuilder();
            msg.append("warning: ").append(abbreviateName(newCommit, reader));
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
      }
    }

    /**
     * Sets cmd and prev to the ReceiveCommands for change edits. Returns false if there was a
     * failure.
     */
    private boolean newEdit() {
      try (TraceTimer traceTimer = newTimer("newEdit")) {
        psId = notes.getChange().currentPatchSetId();
        Optional<ChangeEdit> edit;

        try {
          edit = editUtil.byChange(notes, user);
        } catch (AuthException | IOException e) {
          logger.atSevere().withCause(e).log("Cannot retrieve edit");
          return false;
        }

        if (edit.isPresent()) {
          if (edit.get().getBasePatchSet().id().equals(psId)) {
            // replace edit
            cmd =
                new ReceiveCommand(
                    edit.get().getEditCommit(), newCommitId, edit.get().getRefName());
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
    }

    /** Creates a ReceiveCommand for a new edit. */
    private void createEditCommand() {
      cmd =
          new ReceiveCommand(
              ObjectId.zeroId(),
              newCommitId,
              RefNames.refsEdit(user.getAccountId(), notes.getChangeId(), psId));
    }

    /** Updates 'this' to add a new patchset. */
    private void newPatchSet() throws IOException {
      try (TraceTimer traceTimer = newTimer("newPatchSet")) {
        RevCommit newCommit = receivePack.getRevWalk().parseCommit(newCommitId);
        psId = nextPatchSetId(notes.getChange().currentPatchSetId());
        info = patchSetInfoFactory.get(receivePack.getRevWalk(), newCommit, psId);
        cmd = new ReceiveCommand(ObjectId.zeroId(), newCommitId, psId.toRefName());
      }
    }

    private PatchSet.Id nextPatchSetId(PatchSet.Id psId) throws IOException {
      PatchSet.Id next = ChangeUtil.nextPatchSetId(psId);
      while (receivePackRefCache.exactRef(next.toRefName()) != null) {
        next = ChangeUtil.nextPatchSetId(next);
      }
      return next;
    }

    void addOps(BatchUpdate bu, @Nullable Task progress) throws IOException {
      try (TraceTimer traceTimer = newTimer("addOps")) {
        if (magicBranch != null && magicBranch.edit) {
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
                    checkMergedInto ? inputCommand.getNewId().name() : null,
                    priorPatchSet,
                    priorCommit,
                    psId,
                    newCommit,
                    info,
                    groups,
                    magicBranch,
                    receivePack.getPushCertificate(),
                    notes.getChange())
                .setRequestScopePropagator(requestScopePropagator);
        bu.addOp(notes.getChangeId(), replaceOp);
        if (progress != null) {
          bu.addOp(notes.getChangeId(), new ChangeProgressOp(progress));
        }
      }
    }

    String getRejectMessage() {
      return replaceOp != null ? replaceOp.getRejectMessage() : null;
    }
  }

  private class UpdateGroupsRequest {
    final PatchSet.Id psId;
    final RevCommit commit;
    List<String> groups = ImmutableList.of();

    UpdateGroupsRequest(PatchSet.Id psId, RevCommit commit) {
      this.psId = psId;
      this.commit = commit;
    }

    private void addOps(BatchUpdate bu) {
      bu.addOp(
          psId.changeId(),
          new BatchUpdateOp() {
            @Override
            public boolean updateChange(ChangeContext ctx) {
              PatchSet ps = psUtil.get(ctx.getNotes(), psId);
              List<String> oldGroups = ps.groups();
              if (oldGroups == null) {
                if (groups == null) {
                  return false;
                }
              } else if (sameGroups(oldGroups, groups)) {
                return false;
              }
              ctx.getUpdate(psId).setGroups(groups);
              return true;
            }
          });
    }

    private boolean sameGroups(List<String> a, List<String> b) {
      return Sets.newHashSet(a).equals(Sets.newHashSet(b));
    }
  }

  private class UpdateOneRefOp implements RepoOnlyOp {
    final ReceiveCommand cmd;

    private UpdateOneRefOp(ReceiveCommand cmd) {
      this.cmd = requireNonNull(cmd);
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
        projectCache.evict(project);
        ProjectState ps =
            projectCache.get(project.getNameKey()).orElseThrow(illegalState(project.getNameKey()));
        try {
          logger.atFine().log("Updating project description");
          repo.setGitwebDescription(ps.getProject().getDescription());
        } catch (IOException e) {
          throw new StorageException("cannot update description of " + project.getName(), e);
        }
        if (allProjectsName.equals(project.getNameKey())) {
          try {
            createGroupPermissionSyncer.syncIfNeeded();
          } catch (IOException | ConfigInvalidException e) {
            throw new StorageException("cannot update description of " + project.getName(), e);
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

  private static boolean parentsEqual(RevCommit a, RevCommit b) {
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

  private static boolean authorEqual(RevCommit a, RevCommit b) {
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
    try (TraceTimer traceTimer = newTimer("validRefOperation")) {
      RefOperationValidators refValidators = refValidatorsFactory.create(getProject(), user, cmd);

      try {
        messages.addAll(refValidators.validateForRefOperation());
      } catch (RefOperationValidationException e) {
        messages.addAll(e.getMessages());
        reject(cmd, e.getMessage());
        return false;
      }

      return true;
    }
  }

  /**
   * Validates the commits that a regular push brings in.
   *
   * <p>On validation failure, the command is rejected.
   */
  private void validateRegularPushCommits(BranchNameKey branch, ReceiveCommand cmd)
      throws PermissionBackendException {
    try (TraceTimer traceTimer =
        newTimer("validateRegularPushCommits", Metadata.builder().branchName(branch.branch()))) {
      boolean skipValidation =
          !RefNames.REFS_CONFIG.equals(cmd.getRefName())
              && !(MagicBranch.isMagicBranch(cmd.getRefName())
                  || NEW_PATCHSET_PATTERN.matcher(cmd.getRefName()).matches())
              && pushOptions.containsKey(PUSH_OPTION_SKIP_VALIDATION);
      if (skipValidation) {
        if (projectState.is(BooleanProjectConfig.USE_SIGNED_OFF_BY)) {
          reject(cmd, "requireSignedOffBy prevents option " + PUSH_OPTION_SKIP_VALIDATION);
          return;
        }

        Optional<AuthException> err =
            checkRefPermission(permissions.ref(branch.branch()), RefPermission.SKIP_VALIDATION);
        if (err.isPresent()) {
          rejectProhibited(cmd, err.get());
          return;
        }
        if (!Iterables.isEmpty(rejectCommits)) {
          reject(cmd, "reject-commits prevents " + PUSH_OPTION_SKIP_VALIDATION);
        }
      }

      BranchCommitValidator validator = commitValidatorFactory.create(projectState, branch, user);
      RevWalk walk = receivePack.getRevWalk();
      walk.reset();
      walk.sort(RevSort.NONE);
      try {
        RevObject parsedObject = walk.parseAny(cmd.getNewId());
        if (!(parsedObject instanceof RevCommit)) {
          return;
        }
        walk.markStart((RevCommit) parsedObject);
        markHeadsAsUninteresting(walk, cmd.getRefName());
        int limit = receiveConfig.maxBatchCommits;
        int n = 0;
        for (RevCommit c; (c = walk.next()) != null; ) {
          // Even if skipValidation is set, we still get here when at least one plugin
          // commit validator requires to validate all commits. In this case, however,
          // we don't need to check the commit limit.
          if (++n > limit && !skipValidation) {
            logger.atFine().log("Number of new commits exceeds limit of %d", limit);
            reject(
                cmd,
                String.format(
                    "more than %d commits, and %s not set", limit, PUSH_OPTION_SKIP_VALIDATION));
            return;
          }
          if (!receivePackRefCache.tipsFromObjectId(c, RefNames.REFS_CHANGES).isEmpty()) {
            continue;
          }

          BranchCommitValidator.Result validationResult =
              validator.validateCommit(
                  walk.getObjectReader(), cmd, c, false, rejectCommits, null, skipValidation);
          messages.addAll(validationResult.messages());
          if (!validationResult.isValid()) {
            break;
          }
        }
        logger.atFine().log("Validated %d new commits", n);
      } catch (IOException err) {
        cmd.setResult(REJECTED_MISSING_OBJECT);
        logger.atSevere().withCause(err).log(
            "Invalid pack upload; one or more objects weren't sent");
      }
    }
  }

  private void autoCloseChanges(ReceiveCommand cmd, Task progress) {
    try (TraceTimer traceTimer = newTimer("autoCloseChanges")) {
      logger.atFine().log("Starting auto-closing of changes");
      String refName = cmd.getRefName();
      Set<Change.Id> ids = new HashSet<>();

      // TODO(dborowitz): Combine this BatchUpdate with the main one in
      // handleRegularCommands
      try {
        retryHelper
            .changeUpdate(
                "autoCloseChanges",
                updateFactory -> {
                  try (BatchUpdate bu =
                          updateFactory.create(projectState.getNameKey(), user, TimeUtil.nowTs());
                      ObjectInserter ins = repo.newObjectInserter();
                      ObjectReader reader = ins.newReader();
                      RevWalk rw = new RevWalk(reader)) {
                    if (ObjectId.zeroId().equals(cmd.getOldId())) {
                      // The user is creating a new branch. The branch can't contain any changes, so
                      // auto-closing doesn't apply. Exiting here early to spare any further,
                      // potentially expensive computation that loop over all commits.
                      return null;
                    }

                    bu.setRepository(repo, rw, ins);
                    // TODO(dborowitz): Teach BatchUpdate to ignore missing changes.

                    RevCommit newTip = rw.parseCommit(cmd.getNewId());
                    BranchNameKey branch = BranchNameKey.create(project.getNameKey(), refName);

                    rw.reset();
                    rw.sort(RevSort.REVERSE);
                    rw.markStart(newTip);
                    rw.markUninteresting(rw.parseCommit(cmd.getOldId()));

                    Map<Change.Key, ChangeNotes> byKey = null;
                    List<ReplaceRequest> replaceAndClose = new ArrayList<>();

                    int existingPatchSets = 0;
                    int newPatchSets = 0;
                    SubmissionId submissionId = null;
                    COMMIT:
                    for (RevCommit c; (c = rw.next()) != null; ) {
                      rw.parseBody(c);

                      // Check if change refs point to this commit. Usually there are 0-1 change
                      // refs pointing to this commit.
                      for (Ref ref :
                          receivePackRefCache.tipsFromObjectId(c.copy(), RefNames.REFS_CHANGES)) {
                        PatchSet.Id psId = PatchSet.Id.fromRef(ref.getName());
                        Optional<ChangeNotes> notes = getChangeNotes(psId.changeId());
                        if (notes.isPresent() && notes.get().getChange().getDest().equals(branch)) {
                          if (submissionId == null) {
                            submissionId = new SubmissionId(notes.get().getChange());
                          }
                          existingPatchSets++;
                          bu.addOp(
                              notes.get().getChangeId(), setPrivateOpFactory.create(false, null));
                          bu.addOp(
                              psId.changeId(),
                              mergedByPushOpFactory.create(
                                  requestScopePropagator,
                                  psId,
                                  submissionId,
                                  refName,
                                  newTip.getId().getName()));
                          continue COMMIT;
                        }
                      }

                      for (String changeId : c.getFooterLines(FooterConstants.CHANGE_ID)) {
                        if (byKey == null) {
                          byKey =
                              retryHelper
                                  .changeIndexQuery(
                                      "queryOpenChangesByKeyByBranch",
                                      q -> openChangesByKeyByBranch(q, branch))
                                  .call();
                        }

                        ChangeNotes onto = byKey.get(Change.key(changeId.trim()));
                        if (onto != null) {
                          newPatchSets++;
                          // Hold onto this until we're done with the walk, as the call to
                          // req.validate below calls isMergedInto which resets the walk.
                          ReplaceRequest req =
                              new ReplaceRequest(onto.getChangeId(), c, cmd, false);
                          req.notes = onto;
                          replaceAndClose.add(req);
                          continue COMMIT;
                        }
                      }
                    }

                    for (ReplaceRequest req : replaceAndClose) {
                      Change.Id id = req.notes.getChangeId();
                      if (!req.validateNewPatchSetForAutoClose()) {
                        logger.atFine().log("Not closing %s because validation failed", id);
                        continue;
                      }
                      if (submissionId == null) {
                        submissionId = new SubmissionId(req.notes.getChange());
                      }
                      req.addOps(bu, null);
                      bu.addOp(id, setPrivateOpFactory.create(false, null));
                      bu.addOp(
                          id,
                          mergedByPushOpFactory
                              .create(
                                  requestScopePropagator,
                                  req.psId,
                                  submissionId,
                                  refName,
                                  newTip.getId().getName())
                              .setPatchSetProvider(req.replaceOp::getPatchSet));
                      bu.addOp(id, new ChangeProgressOp(progress));
                      ids.add(id);
                    }

                    logger.atFine().log(
                        "Auto-closing %d changes with existing patch sets and %d with new patch"
                            + " sets",
                        existingPatchSets, newPatchSets);
                    bu.execute();
                  } catch (IOException | StorageException | PermissionBackendException e) {
                    throw new StorageException("Failed to auto-close changes", e);
                  }

                  // If we are here, we didn't throw UpdateException. Record the result.
                  // The ordering is indeterminate due to the HashSet; unfortunately, Change.Id
                  // doesn't
                  // fit into TreeSet.
                  ids.stream()
                      .forEach(
                          id -> result.addChange(ReceiveCommitsResult.ChangeStatus.AUTOCLOSED, id));

                  return null;
                })
            // Use a multiple of the default timeout to account for inner retries that may otherwise
            // eat up the whole timeout so that no time is left to retry this outer action.
            .defaultTimeoutMultiplier(5)
            .call();
      } catch (RestApiException e) {
        logger.atSevere().withCause(e).log("Can't insert patchset");
      } catch (UpdateException e) {
        logger.atSevere().withCause(e).log("Failed to auto-close changes");
      } finally {
        logger.atFine().log("Done auto-closing changes");
      }
    }
  }

  private Optional<ChangeNotes> getChangeNotes(Change.Id changeId) {
    try {
      return Optional.of(notesFactory.createChecked(project.getNameKey(), changeId));
    } catch (NoSuchChangeException e) {
      return Optional.empty();
    }
  }

  private Map<Change.Key, ChangeNotes> openChangesByKeyByBranch(
      InternalChangeQuery internalChangeQuery, BranchNameKey branch) {
    try (TraceTimer traceTimer =
        newTimer("openChangesByKeyByBranch", Metadata.builder().branchName(branch.branch()))) {
      Map<Change.Key, ChangeNotes> r = new HashMap<>();
      for (ChangeData cd : internalChangeQuery.byBranchOpen(branch)) {
        try {
          r.put(cd.change().getKey(), cd.notes());
        } catch (NoSuchChangeException e) {
          // Ignore deleted change
        }
      }
      return r;
    }
  }

  private TraceTimer newTimer(String name) {
    return newTimer(getClass(), name);
  }

  private TraceTimer newTimer(Class<?> clazz, String name) {
    return newTimer(clazz, name, Metadata.builder());
  }

  private TraceTimer newTimer(String name, Metadata.Builder metadataBuilder) {
    return newTimer(getClass(), name, metadataBuilder);
  }

  private TraceTimer newTimer(Class<?> clazz, String name, Metadata.Builder metadataBuilder) {
    metadataBuilder.projectName(project.getName());
    return TraceContext.newTimer(clazz.getSimpleName() + "#" + name, metadataBuilder.build());
  }

  private static void reject(ReceiveCommand cmd, String why) {
    logger.atFine().log("Rejecting command '%s': %s", cmd, why);
    cmd.setResult(REJECTED_OTHER_REASON, why);
  }

  private static void rejectRemaining(Collection<ReceiveCommand> commands, String why) {
    rejectRemaining(commands.stream(), why);
  }

  private static void rejectRemaining(Stream<ReceiveCommand> commands, String why) {
    commands.filter(cmd -> cmd.getResult() == NOT_ATTEMPTED).forEach(cmd -> reject(cmd, why));
  }

  private static boolean isHead(ReceiveCommand cmd) {
    return cmd.getRefName().startsWith(Constants.R_HEADS);
  }

  private static boolean isConfig(ReceiveCommand cmd) {
    return cmd.getRefName().equals(RefNames.REFS_CONFIG);
  }

  private static String commandToString(ReceiveCommand cmd) {
    StringBuilder b = new StringBuilder();
    b.append(cmd);
    b.append("  (").append(cmd.getResult());
    if (cmd.getMessage() != null) {
      b.append(": ").append(cmd.getMessage());
    }
    b.append(")\n");
    return b.toString();
  }

  private static class SetTopicOp implements BatchUpdateOp {

    private final String topic;

    public SetTopicOp(String topic) {
      this.topic = topic;
    }

    @Override
    public boolean updateChange(ChangeContext ctx) throws ValidationException {
      ctx.getUpdate(ctx.getChange().currentPatchSetId()).setTopic(topic);
      return true;
    }
  }
}
