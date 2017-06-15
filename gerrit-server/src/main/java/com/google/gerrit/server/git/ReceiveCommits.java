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

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.gerrit.common.FooterConstants.CHANGE_ID;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CHANGES;
import static com.google.gerrit.server.change.HashtagsUtil.cleanupHashtag;
import static com.google.gerrit.server.git.MultiProgressMonitor.UNKNOWN;
import static com.google.gerrit.server.mail.MailUtil.getRecipientsFromFooters;
import static java.util.Comparator.comparingInt;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static org.eclipse.jgit.lib.Constants.R_HEADS;
import static org.eclipse.jgit.lib.RefDatabase.ALL;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.NOT_ATTEMPTED;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.OK;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_MISSING_OBJECT;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_NONFASTFORWARD;
import static org.eclipse.jgit.transport.ReceiveCommand.Result.REJECTED_OTHER_REASON;

import com.google.common.base.Function;
import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.Iterables;
import com.google.common.collect.LinkedListMultimap;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.MultimapBuilder;
import com.google.common.collect.Sets;
import com.google.common.collect.SortedSetMultimap;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.TimeUtil;
import com.google.gerrit.common.data.Capable;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.api.changes.HashtagsInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.api.changes.RecipientType;
import com.google.gerrit.extensions.api.changes.SubmitInput;
import com.google.gerrit.extensions.api.projects.ProjectConfigEntryType;
import com.google.gerrit.extensions.client.GeneralPreferencesInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicMap.Entry;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.reviewdb.client.Account;
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
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.account.AccountResolver;
import com.google.gerrit.server.account.Accounts;
import com.google.gerrit.server.account.AccountsUpdate;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.SetHashtagsOp;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.edit.ChangeEdit;
import com.google.gerrit.server.edit.ChangeEditUtil;
import com.google.gerrit.server.events.CommitReceivedEvent;
import com.google.gerrit.server.git.MultiProgressMonitor.Task;
import com.google.gerrit.server.git.validators.CommitValidationException;
import com.google.gerrit.server.git.validators.CommitValidationMessage;
import com.google.gerrit.server.git.validators.CommitValidators;
import com.google.gerrit.server.git.validators.RefOperationValidationException;
import com.google.gerrit.server.git.validators.RefOperationValidators;
import com.google.gerrit.server.git.validators.ValidationMessage;
import com.google.gerrit.server.index.change.ChangeIndexer;
import com.google.gerrit.server.mail.MailUtil.MailRecipients;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.NotesMigration;
import com.google.gerrit.server.patch.PatchSetInfoFactory;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.RefControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.BatchUpdateOp;
import com.google.gerrit.server.update.ChangeContext;
import com.google.gerrit.server.update.Context;
import com.google.gerrit.server.update.RepoContext;
import com.google.gerrit.server.update.RepoOnlyOp;
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
import java.io.IOException;
import java.io.StringWriter;
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
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
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

/** Receives change upload using the Git receive-pack protocol. */
public class ReceiveCommits {
  private static final Logger log = LoggerFactory.getLogger(ReceiveCommits.class);
  private static final String BYPASS_REVIEW = "bypass-review";

  public static final Pattern NEW_PATCHSET =
      Pattern.compile("^" + REFS_CHANGES + "(?:[0-9][0-9]/)?([1-9][0-9]*)(?:/new)?$");

  private static final String COMMAND_REJECTION_MESSAGE_FOOTER =
      "Please read the documentation and contact an administrator\n"
          + "if you feel the configuration is incorrect";

  private static final String SAME_CHANGE_ID_IN_MULTIPLE_CHANGES =
      "same Change-Id in multiple changes.\n"
          + "Squash the commits with the same Change-Id or "
          + "ensure Change-Ids are unique for each commit";

  private enum Error {
    CONFIG_UPDATE(
        "You are not allowed to perform this operation.\n"
            + "Configuration changes can only be pushed by project owners\n"
            + "who also have 'Push' rights on "
            + RefNames.REFS_CONFIG),
    UPDATE(
        "You are not allowed to perform this operation.\n"
            + "To push into this reference you need 'Push' rights."),
    DELETE("You need 'Push' rights with the 'Force Push'\nflag set to delete references."),
    DELETE_CHANGES("Cannot delete from '" + REFS_CHANGES + "'"),
    CODE_REVIEW(
        "You need 'Push' rights to upload code review requests.\n"
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

  private Set<Account.Id> reviewersFromCommandLine = Sets.newLinkedHashSet();
  private Set<Account.Id> ccFromCommandLine = Sets.newLinkedHashSet();

  private final IdentifiedUser user;
  private final ReviewDb db;
  private final Sequences seq;
  private final Provider<InternalChangeQuery> queryProvider;
  private final ChangeNotes.Factory notesFactory;
  private final Accounts accounts;
  private final AccountsUpdate.Server accountsUpdate;
  private final AccountResolver accountResolver;
  private final PermissionBackend permissionBackend;
  private final PermissionBackend.ForProject permissions;
  private final CmdLineParser.Factory optionParserFactory;
  private final PatchSetInfoFactory patchSetInfoFactory;
  private final PatchSetUtil psUtil;
  private final ProjectCache projectCache;
  private final String canonicalWebUrl;
  private final CommitValidators.Factory commitValidatorsFactory;
  private final RefOperationValidators.Factory refValidatorsFactory;
  private final TagCache tagCache;
  private final ChangeInserter.Factory changeInserterFactory;
  private final RequestScopePropagator requestScopePropagator;
  private final SshInfo sshInfo;
  private final AllProjectsName allProjectsName;
  private final ReceiveConfig receiveConfig;
  private final DynamicSet<ReceivePackInitializer> initializers;
  private final BatchUpdate.Factory batchUpdateFactory;
  private final SetHashtagsOp.Factory hashtagsFactory;
  private final ReplaceOp.Factory replaceOpFactory;
  private final MergedByPushOp.Factory mergedByPushOpFactory;

  private final ProjectControl projectControl;
  private final Project project;
  private final LabelTypes labelTypes;
  private final Repository repo;
  private final ReceivePack rp;
  private final NoteMap rejectCommits;
  private final RequestId receiveId;
  private MagicBranchInput magicBranch;
  private boolean newChangeForAllNotInTarget;
  private final ListMultimap<String, String> pushOptions = LinkedListMultimap.create();

  private List<CreateRequest> newChanges = Collections.emptyList();
  private final Map<Change.Id, ReplaceRequest> replaceByChange = new LinkedHashMap<>();
  private final List<UpdateGroupsRequest> updateGroups = new ArrayList<>();
  private final Set<ObjectId> validCommits = new HashSet<>();

  private ListMultimap<Change.Id, Ref> refsByChange;
  private ListMultimap<ObjectId, Ref> refsById;
  private Map<String, Ref> allRefs;

  private final SubmoduleOp.Factory subOpFactory;
  private final MergeOp.Factory mergeOpFactory;
  private final Provider<MergeOpRepoManager> ormProvider;
  private final DynamicMap<ProjectConfigEntry> pluginConfigEntries;
  private final NotesMigration notesMigration;
  private final ChangeEditUtil editUtil;
  private final ChangeIndexer indexer;

  /**
   * Actual commands to be executed, as opposed to the mix of actual and magic commands that were
   * provided over the wire.
   *
   * <p>Excludes commands executed implicitly as part of other {@link BatchUpdateOp}s, such as
   * creating patch set refs.
   */
  private final List<ReceiveCommand> actualCommands = new ArrayList<>();

  private final List<ValidationMessage> messages = new ArrayList<>();
  private ListMultimap<Error, String> errors = LinkedListMultimap.create();
  private Task newProgress;
  private Task replaceProgress;
  private Task closeProgress;
  private Task commandProgress;
  private MessageSender messageSender;

  @Inject
  ReceiveCommits(
      ReviewDb db,
      Sequences seq,
      Provider<InternalChangeQuery> queryProvider,
      ChangeNotes.Factory notesFactory,
      Accounts accounts,
      AccountsUpdate.Server accountsUpdate,
      AccountResolver accountResolver,
      PermissionBackend permissionBackend,
      CmdLineParser.Factory optionParserFactory,
      PatchSetInfoFactory patchSetInfoFactory,
      PatchSetUtil psUtil,
      ProjectCache projectCache,
      TagCache tagCache,
      VisibleRefFilter.Factory refFilterFactory,
      ChangeInserter.Factory changeInserterFactory,
      CommitValidators.Factory commitValidatorsFactory,
      RefOperationValidators.Factory refValidatorsFactory,
      @CanonicalWebUrl String canonicalWebUrl,
      RequestScopePropagator requestScopePropagator,
      SshInfo sshInfo,
      AllProjectsName allProjectsName,
      ReceiveConfig receiveConfig,
      TransferConfig transferConfig,
      DynamicSet<ReceivePackInitializer> initializers,
      Provider<LazyPostReceiveHookChain> lazyPostReceive,
      @Assisted ProjectControl projectControl,
      @Assisted Repository repo,
      SubmoduleOp.Factory subOpFactory,
      MergeOp.Factory mergeOpFactory,
      Provider<MergeOpRepoManager> ormProvider,
      DynamicMap<ProjectConfigEntry> pluginConfigEntries,
      NotesMigration notesMigration,
      ChangeEditUtil editUtil,
      ChangeIndexer indexer,
      BatchUpdate.Factory batchUpdateFactory,
      SetHashtagsOp.Factory hashtagsFactory,
      ReplaceOp.Factory replaceOpFactory,
      MergedByPushOp.Factory mergedByPushOpFactory)
      throws IOException, PermissionBackendException {
    this.user = projectControl.getUser().asIdentifiedUser();
    this.db = db;
    this.seq = seq;
    this.queryProvider = queryProvider;
    this.notesFactory = notesFactory;
    this.accounts = accounts;
    this.accountsUpdate = accountsUpdate;
    this.accountResolver = accountResolver;
    this.permissionBackend = permissionBackend;
    this.optionParserFactory = optionParserFactory;
    this.patchSetInfoFactory = patchSetInfoFactory;
    this.psUtil = psUtil;
    this.projectCache = projectCache;
    this.canonicalWebUrl = canonicalWebUrl;
    this.tagCache = tagCache;
    this.changeInserterFactory = changeInserterFactory;
    this.commitValidatorsFactory = commitValidatorsFactory;
    this.refValidatorsFactory = refValidatorsFactory;
    this.requestScopePropagator = requestScopePropagator;
    this.sshInfo = sshInfo;
    this.allProjectsName = allProjectsName;
    this.receiveConfig = receiveConfig;
    this.initializers = initializers;
    this.batchUpdateFactory = batchUpdateFactory;
    this.hashtagsFactory = hashtagsFactory;
    this.replaceOpFactory = replaceOpFactory;
    this.mergedByPushOpFactory = mergedByPushOpFactory;

    this.projectControl = projectControl;
    this.labelTypes = projectControl.getLabelTypes();
    this.project = projectControl.getProject();
    this.repo = repo;
    this.rp = new ReceivePack(repo);
    this.rejectCommits = BanCommit.loadRejectCommitsMap(repo, rp.getRevWalk());
    this.receiveId = RequestId.forProject(project.getNameKey());

    this.subOpFactory = subOpFactory;
    this.mergeOpFactory = mergeOpFactory;
    this.ormProvider = ormProvider;
    this.pluginConfigEntries = pluginConfigEntries;
    this.notesMigration = notesMigration;

    this.editUtil = editUtil;
    this.indexer = indexer;

    this.messageSender = new ReceivePackMessageSender();

    ProjectState ps = projectControl.getProjectState();

    this.newChangeForAllNotInTarget = ps.isCreateNewChangeForAllNotInTarget();
    rp.setAllowCreates(true);
    rp.setAllowDeletes(true);
    rp.setAllowNonFastForwards(true);
    rp.setRefLogIdent(user.newRefLogIdent());
    rp.setTimeout(transferConfig.getTimeout());
    rp.setMaxObjectSizeLimit(
        transferConfig.getEffectiveMaxObjectSizeLimit(projectControl.getProjectState()));
    rp.setCheckReceivedObjects(ps.getConfig().getCheckReceivedObjects());
    rp.setRefFilter(
        new RefFilter() {
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

    permissions = permissionBackend.user(user).project(project.getNameKey());
    // If the user lacks READ permission, some references may be filtered and hidden from view.
    // Check objects mentioned inside the incoming pack file are reachable from visible refs.
    try {
      permissionBackend.user(user).project(project.getNameKey()).check(ProjectPermission.READ);
    } catch (AuthException e) {
      rp.setCheckReferencedObjectsAreReachable(receiveConfig.checkReferencedObjectsAreReachable);
    }

    rp.setAdvertiseRefsHook(
        refFilterFactory.create(projectControl.getProjectState(), repo).setShowMetadata(false));
    List<AdvertiseRefsHook> advHooks = new ArrayList<>(3);
    advHooks.add(
        new AdvertiseRefsHook() {
          @Override
          public void advertiseRefs(BaseReceivePack rp) throws ServiceMayNotContinueException {
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
          public void advertiseRefs(UploadPack uploadPack) {}
        });
    advHooks.add(rp.getAdvertiseRefsHook());
    advHooks.add(
        new ReceiveCommitsAdvertiseRefsHook(
            queryProvider, projectControl.getProject().getNameKey()));
    advHooks.add(new HackPushNegotiateHook());
    rp.setAdvertiseRefsHook(AdvertiseRefsHookChain.newChain(advHooks));
    rp.setPostReceiveHook(lazyPostReceive.get());
    rp.setAllowPushOptions(true);
  }

  public void init() {
    for (ReceivePackInitializer i : initializers) {
      i.init(projectControl.getProject().getNameKey(), rp);
    }
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
  public void setMessageSender(MessageSender ms) {
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
    commandProgress = progress.beginSubTask("refs", UNKNOWN);

    try {
      parseCommands(commands);
    } catch (PermissionBackendException err) {
      for (ReceiveCommand cmd : actualCommands) {
        if (cmd.getResult() == NOT_ATTEMPTED) {
          cmd.setResult(REJECTED_OTHER_REASON, "internal server error");
        }
      }
      logError(String.format("Failed to process refs in %s", project.getName()), err);
    }
    if (magicBranch != null && magicBranch.cmd.getResult() == NOT_ATTEMPTED) {
      selectNewAndReplacedChangesFromMagicBranch();
    }
    preparePatchSetsForReplace();
    insertChangesAndPatchSets();
    newProgress.end();
    replaceProgress.end();

    if (!errors.isEmpty()) {
      logDebug("Handling error conditions: {}", errors.keySet());
      for (Error error : errors.keySet()) {
        rp.sendMessage(buildError(error, errors.get(error)));
      }
      rp.sendMessage(String.format("User: %s", displayName(user)));
      rp.sendMessage(COMMAND_REJECTION_MESSAGE_FOOTER);
    }

    Set<Branch.NameKey> branches = new HashSet<>();
    for (ReceiveCommand c : actualCommands) {
      // Most post-update steps should happen in UpdateOneRefOp#postUpdate. The only steps that
      // should happen in this loop are things that can't happen within one BatchUpdate because they
      // involve kicking off an additional BatchUpdate.
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
        orm.setContext(db, TimeUtil.nowTs(), user, receiveId);
        SubmoduleOp op = subOpFactory.create(branches, orm);
        op.updateSuperProjects(batchUpdateFactory);
      } catch (SubmoduleException e) {
        logError("Can't update the superprojects", e);
      }
    }

    closeProgress.end();
    commandProgress.end();
    progress.end();
    reportMessages();
  }

  private void reportMessages() {
    List<CreateRequest> created =
        newChanges.stream().filter(r -> r.change != null).collect(toList());
    if (!created.isEmpty()) {
      addMessage("");
      addMessage("New Changes:");
      for (CreateRequest c : created) {
        addMessage(
            formatChangeUrl(
                canonicalWebUrl,
                c.change,
                c.change.getSubject(),
                c.change.getStatus() == Change.Status.DRAFT,
                false));
      }
      addMessage("");
    }

    List<ReplaceRequest> updated =
        replaceByChange
            .values()
            .stream()
            .filter(r -> !r.skip && r.inputCommand.getResult() == OK)
            .sorted(comparingInt(r -> r.notes.getChangeId().get()))
            .collect(toList());
    if (!updated.isEmpty()) {
      addMessage("");
      addMessage("Updated Changes:");
      boolean edit = magicBranch != null && magicBranch.edit;
      for (ReplaceRequest u : updated) {
        String subject;
        if (edit) {
          try {
            subject = rp.getRevWalk().parseCommit(u.newCommitId).getShortMessage();
          } catch (IOException e) {
            // Log and fall back to original change subject
            logWarn("failed to get subject for edit patch set", e);
            subject = u.notes.getChange().getSubject();
          }
        } else {
          subject = u.info.getSubject();
        }
        addMessage(
            formatChangeUrl(
                canonicalWebUrl,
                u.notes.getChange(),
                subject,
                u.replaceOp != null && u.replaceOp.getPatchSet().isDraft(),
                edit));
      }
      addMessage("");
    }
  }

  private static String formatChangeUrl(
      String url, Change change, String subject, boolean draft, boolean edit) {
    StringBuilder m =
        new StringBuilder()
            .append("  ")
            .append(url)
            .append(change.getChangeId())
            .append(" ")
            .append(ChangeUtil.cropSubject(subject));
    if (draft) {
      m.append(" [DRAFT]");
    }
    if (edit) {
      m.append(" [EDIT]");
    }
    return m.toString();
  }

  private void insertChangesAndPatchSets() {
    ReceiveCommand magicBranchCmd = magicBranch != null ? magicBranch.cmd : null;
    if (magicBranchCmd != null && magicBranchCmd.getResult() != NOT_ATTEMPTED) {
      logWarn(
          String.format(
              "Skipping change updates on %s because ref update failed: %s %s",
              project.getName(),
              magicBranchCmd.getResult(),
              Strings.nullToEmpty(magicBranchCmd.getMessage())));
      return;
    }

    try (BatchUpdate bu =
            batchUpdateFactory.create(
                db, project.getNameKey(), user.materializedCopy(), TimeUtil.nowTs());
        ObjectInserter ins = repo.newObjectInserter();
        ObjectReader reader = ins.newReader();
        RevWalk rw = new RevWalk(reader)) {
      bu.setRepository(repo, rw, ins).updateChangesInParallel();
      bu.setRequestId(receiveId);
      bu.setRefLogMessage("push");

      logDebug("Adding {} replace requests", newChanges.size());
      for (ReplaceRequest replace : replaceByChange.values()) {
        replace.addOps(bu, replaceProgress);
      }

      logDebug("Adding {} create requests", newChanges.size());
      for (CreateRequest create : newChanges) {
        create.addOps(bu);
      }

      logDebug("Adding {} group update requests", newChanges.size());
      updateGroups.forEach(r -> r.addOps(bu));

      logDebug("Adding {} additional ref updates", actualCommands.size());
      actualCommands.forEach(c -> bu.addRepoOnlyOp(new UpdateOneRefOp(c)));

      logDebug("Executing batch");
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
          logDebug("Rejecting due to message from ReplaceOp");
          reject(replace.inputCommand, rejectMessage);
        }
      }

    } catch (ResourceConflictException e) {
      addMessage(e.getMessage());
      reject(magicBranchCmd, "conflict");
    } catch (RestApiException | IOException err) {
      logError("Can't insert change/patch set for " + project.getName(), err);
      reject(magicBranchCmd, "internal server error: " + err.getMessage());
    }

    if (magicBranch != null && magicBranch.submit) {
      try {
        submit(newChanges, replaceByChange.values());
      } catch (ResourceConflictException e) {
        addMessage(e.getMessage());
        reject(magicBranchCmd, "conflict");
      } catch (RestApiException | OrmException e) {
        logError("Error submitting changes to " + project.getName(), e);
        reject(magicBranchCmd, "error during submit");
      }
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

  private void parseCommands(Collection<ReceiveCommand> commands)
      throws PermissionBackendException {
    List<String> optionList = rp.getPushOptions();
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

    logDebug("Parsing {} commands", commands.size());
    for (ReceiveCommand cmd : commands) {
      if (cmd.getResult() != NOT_ATTEMPTED) {
        // Already rejected by the core receive process.
        logDebug("Already processed by core: {} {}", cmd.getResult(), cmd);
        continue;
      }

      if (!Repository.isValidRefName(cmd.getRefName()) || cmd.getRefName().contains("//")) {
        reject(cmd, "not valid ref");
        continue;
      }

      if (MagicBranch.isMagicBranch(cmd.getRefName())) {
        parseMagicBranch(cmd);
        continue;
      }

      if (projectControl.getProjectState().isAllUsers()
          && RefNames.REFS_USERS_SELF.equals(cmd.getRefName())) {
        String newName = RefNames.refsUsers(user.getAccountId());
        logDebug("Swapping out command for {} to {}", RefNames.REFS_USERS_SELF, newName);
        final ReceiveCommand orgCmd = cmd;
        cmd =
            new ReceiveCommand(cmd.getOldId(), cmd.getNewId(), newName, cmd.getType()) {
              @Override
              public void setResult(Result s, String m) {
                super.setResult(s, m);
                orgCmd.setResult(s, m);
              }
            };
      }

      Matcher m = NEW_PATCHSET.matcher(cmd.getRefName());
      if (m.matches()) {
        // The referenced change must exist and must still be open.
        //
        Change.Id changeId = Change.Id.parse(m.group(1));
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
          reject(cmd, "prohibited by Gerrit: unknown command type " + cmd.getType());
          continue;
      }

      if (cmd.getResult() != NOT_ATTEMPTED) {
        continue;
      }

      if (isConfig(cmd)) {
        logDebug("Processing {} command", cmd.getRefName());
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
              cfg.load(rp.getRevWalk(), cmd.getNewId());
              if (!cfg.getValidationErrors().isEmpty()) {
                addError("Invalid project configuration:");
                for (ValidationError err : cfg.getValidationErrors()) {
                  addError("  " + err.getMessage());
                }
                reject(cmd, "invalid project configuration");
                logError(
                    "User "
                        + user.getUserName()
                        + " tried to push invalid project configuration "
                        + cmd.getNewId().name()
                        + " for "
                        + project.getName());
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
                if (!oldParent.equals(newParent)) {
                  try {
                    permissionBackend.user(user).check(GlobalPermission.ADMINISTRATE_SERVER);
                  } catch (AuthException e) {
                    reject(cmd, "invalid project configuration: only Gerrit admin can set parent");
                    continue;
                  }
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
                    projectControl
                        .getProjectState()
                        .getConfig()
                        .getPluginConfig(e.getPluginName())
                        .getString(e.getExportName());
                if (configEntry.getType() == ProjectConfigEntryType.ARRAY) {
                  oldValue =
                      Arrays.stream(
                              projectControl
                                  .getProjectState()
                                  .getConfig()
                                  .getPluginConfig(e.getPluginName())
                                  .getStringList(e.getExportName()))
                          .collect(joining("\n"));
                }

                if ((value == null ? oldValue != null : !value.equals(oldValue))
                    && !configEntry.isEditable(projectControl.getProjectState())) {
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
              logError(
                  "User "
                      + user.getUserName()
                      + " tried to push invalid project configuration "
                      + cmd.getNewId().name()
                      + " for "
                      + project.getName(),
                  e);
              continue;
            }
            break;

          case DELETE:
            break;

          default:
            reject(
                cmd,
                "prohibited by Gerrit: don't know how to handle config update of type "
                    + cmd.getType());
            continue;
        }
      }
    }
  }

  private void parseCreate(ReceiveCommand cmd) throws PermissionBackendException {
    RevObject obj;
    try {
      obj = rp.getRevWalk().parseAny(cmd.getNewId());
    } catch (IOException err) {
      logError(
          "Invalid object " + cmd.getNewId().name() + " for " + cmd.getRefName() + " creation",
          err);
      reject(cmd, "invalid object");
      return;
    }
    logDebug("Creating {}", cmd);

    if (isHead(cmd) && !isCommit(cmd)) {
      return;
    }

    RefControl ctl = projectControl.controlForRef(cmd.getRefName());
    boolean ok;
    try {
      permissions.ref(cmd.getRefName()).check(RefPermission.CREATE);
      ok = true;
    } catch (AuthException err) {
      ok = false;
    }
    if (ok && ctl.canCreate(rp.getRepository(), obj)) {
      if (!validRefOperation(cmd)) {
        return;
      }
      validateNewCommits(ctl, cmd);
      actualCommands.add(cmd);
    } else {
      reject(cmd, "prohibited by Gerrit: create access denied for " + cmd.getRefName());
    }
  }

  private void parseUpdate(ReceiveCommand cmd) throws PermissionBackendException {
    logDebug("Updating {}", cmd);
    boolean ok;
    try {
      permissions.ref(cmd.getRefName()).check(RefPermission.UPDATE);
      ok = true;
    } catch (AuthException err) {
      ok = false;
    }
    if (ok) {
      if (isHead(cmd) && !isCommit(cmd)) {
        return;
      }
      if (!validRefOperation(cmd)) {
        return;
      }
      validateNewCommits(projectControl.controlForRef(cmd.getRefName()), cmd);
      actualCommands.add(cmd);
    } else {
      if (RefNames.REFS_CONFIG.equals(cmd.getRefName())) {
        errors.put(Error.CONFIG_UPDATE, RefNames.REFS_CONFIG);
      } else {
        errors.put(Error.UPDATE, cmd.getRefName());
      }
      reject(cmd, "prohibited by Gerrit: ref update access denied");
    }
  }

  private boolean isCommit(ReceiveCommand cmd) {
    RevObject obj;
    try {
      obj = rp.getRevWalk().parseAny(cmd.getNewId());
    } catch (IOException err) {
      logError("Invalid object " + cmd.getNewId().name() + " for " + cmd.getRefName(), err);
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
    logDebug("Deleting {}", cmd);
    if (cmd.getRefName().startsWith(REFS_CHANGES)) {
      errors.put(Error.DELETE_CHANGES, cmd.getRefName());
      reject(cmd, "cannot delete changes");
    } else if (canDelete(cmd)) {
      if (!validRefOperation(cmd)) {
        return;
      }
      actualCommands.add(cmd);
    } else if (RefNames.REFS_CONFIG.equals(cmd.getRefName())) {
      reject(cmd, "cannot delete project configuration");
    } else {
      errors.put(Error.DELETE, cmd.getRefName());
      reject(cmd, "cannot delete references");
    }
  }

  private boolean canDelete(ReceiveCommand cmd) throws PermissionBackendException {
    try {
      permissions.ref(cmd.getRefName()).check(RefPermission.DELETE);
      return true;
    } catch (AuthException e) {
      return false;
    }
  }

  private void parseRewind(ReceiveCommand cmd) throws PermissionBackendException {
    RevCommit newObject;
    try {
      newObject = rp.getRevWalk().parseCommit(cmd.getNewId());
    } catch (IncorrectObjectTypeException notCommit) {
      newObject = null;
    } catch (IOException err) {
      logError(
          "Invalid object " + cmd.getNewId().name() + " for " + cmd.getRefName() + " forced update",
          err);
      reject(cmd, "invalid object");
      return;
    }
    logDebug("Rewinding {}", cmd);

    RefControl ctl = projectControl.controlForRef(cmd.getRefName());
    if (newObject != null) {
      validateNewCommits(ctl, cmd);
      if (cmd.getResult() != NOT_ATTEMPTED) {
        return;
      }
    }

    boolean ok;
    try {
      permissions.ref(cmd.getRefName()).check(RefPermission.FORCE_UPDATE);
      ok = true;
    } catch (AuthException err) {
      ok = false;
    }
    if (ok) {
      if (!validRefOperation(cmd)) {
        return;
      }
      actualCommands.add(cmd);
    } else {
      cmd.setResult(
          REJECTED_NONFASTFORWARD, " need '" + PermissionRule.FORCE_PUSH + "' privilege.");
    }
  }

  static class MagicBranchInput {
    private static final Splitter COMMAS = Splitter.on(',').omitEmptyStrings();

    final ReceiveCommand cmd;
    final LabelTypes labelTypes;
    final NotesMigration notesMigration;
    private final boolean defaultPublishComments;
    Branch.NameKey dest;
    RefControl ctl;
    PermissionBackend.ForRef perm;
    Set<Account.Id> reviewer = Sets.newLinkedHashSet();
    Set<Account.Id> cc = Sets.newLinkedHashSet();
    Map<String, Short> labels = new HashMap<>();
    String message;
    List<RevCommit> baseCommit;
    CmdLineParser clp;
    Set<String> hashtags = new HashSet<>();

    @Option(name = "--base", metaVar = "BASE", usage = "merge base of changes")
    List<ObjectId> base;

    @Option(name = "--topic", metaVar = "NAME", usage = "attach topic to changes")
    String topic;

    @Option(name = "--draft", usage = "mark new/updated changes as draft")
    boolean draft;

    @Option(name = "--private", usage = "mark new/updated change as private")
    boolean isPrivate;

    @Option(name = "--remove-private", usage = "remove privacy flag from updated change")
    boolean removePrivate;

    @Option(
      name = "--wip",
      aliases = {"-work-in-progress"},
      usage = "mark change as work in progress"
    )
    boolean workInProgress;

    @Option(name = "--ready", usage = "mark change as ready")
    boolean ready;

    @Option(
      name = "--edit",
      aliases = {"-e"},
      usage = "upload as change edit"
    )
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
      usage = "do not publish draft comments"
    )
    private boolean noPublishComments;

    @Option(
      name = "--notify",
      usage =
          "Notify handling that defines to whom email notifications "
              + "should be sent. Allowed values are NONE, OWNER, "
              + "OWNER_REVIEWERS, ALL. If not set, the default is ALL."
    )
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
      usage = "add reviewer to changes"
    )
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

    @Option(
      name = "--label",
      aliases = {"-l"},
      metaVar = "LABEL+VALUE",
      usage = "label(s) to assign (defaults to +1 if no value provided"
    )
    void addLabel(String token) throws CmdLineException {
      LabelVote v = LabelVote.parse(token);
      try {
        LabelType.checkName(v.label());
        ApprovalsUtil.checkLabel(labelTypes, v.label(), v.value());
      } catch (BadRequestException e) {
        throw clp.reject(e.getMessage());
      }
      labels.put(v.label(), v.value());
    }

    @Option(
      name = "--message",
      aliases = {"-m"},
      metaVar = "MESSAGE",
      usage = "Comment message to apply to the review"
    )
    void addMessage(String token) {
      // git push does not allow spaces in refs.
      message = token.replace("_", " ");
    }

    @Option(
      name = "--hashtag",
      aliases = {"-t"},
      metaVar = "HASHTAG",
      usage = "add hashtag to changes"
    )
    void addHashtag(String token) throws CmdLineException {
      if (!notesMigration.readChanges()) {
        throw clp.reject("cannot add hashtags; noteDb is disabled");
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
      this.labelTypes = labelTypes;
      this.notesMigration = notesMigration;
      GeneralPreferencesInfo prefs = user.getAccount().getGeneralPreferencesInfo();
      this.defaultPublishComments =
          prefs != null
              ? firstNonNull(
                  user.getAccount().getGeneralPreferencesInfo().publishCommentsOnPush, false)
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

    String parse(
        CmdLineParser clp,
        Repository repo,
        Set<String> refs,
        ListMultimap<String, String> pushOptions)
        throws CmdLineException {
      String ref = RefNames.fullName(MagicBranch.getDestBranchName(cmd.getRefName()));

      ListMultimap<String, String> options = LinkedListMultimap.create(pushOptions);
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
        clp.parseOptionMap(options);
      }

      // Split the destination branch by branch and topic. The topic
      // suffix is entirely optional, so it might not even exist.
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

    public NotifyHandling getNotify() {
      if (notify != null) {
        return notify;
      }
      if (workInProgress) {
        return NotifyHandling.OWNER;
      }
      return NotifyHandling.ALL;
    }

    public NotifyHandling getNotify(ChangeNotes notes) {
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
   * Gets an unmodifiable view of the pushOptions.
   *
   * <p>The collection is empty if the client does not support push options, or if the client did
   * not send any options.
   *
   * @return an unmodifiable view of pushOptions.
   */
  @Nullable
  public ListMultimap<String, String> getPushOptions() {
    return ImmutableListMultimap.copyOf(pushOptions);
  }

  private void parseMagicBranch(ReceiveCommand cmd) throws PermissionBackendException {
    // Permit exactly one new change request per push.
    if (magicBranch != null) {
      reject(cmd, "duplicate request");
      return;
    }

    logDebug("Found magic branch {}", cmd.getRefName());
    magicBranch = new MagicBranchInput(user, cmd, labelTypes, notesMigration);
    magicBranch.reviewer.addAll(reviewersFromCommandLine);
    magicBranch.cc.addAll(ccFromCommandLine);

    String ref;
    CmdLineParser clp = optionParserFactory.create(magicBranch);
    magicBranch.clp = clp;

    try {
      ref = magicBranch.parse(clp, repo, rp.getAdvertisedRefs().keySet(), pushOptions);
    } catch (CmdLineException e) {
      if (!clp.wasHelpRequestedByOption()) {
        logDebug("Invalid branch syntax");
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
    if (projectControl.getProjectState().isAllUsers() && RefNames.REFS_USERS_SELF.equals(ref)) {
      logDebug("Handling {}", RefNames.REFS_USERS_SELF);
      ref = RefNames.refsUsers(user.getAccountId());
    }
    if (!rp.getAdvertisedRefs().containsKey(ref) && !ref.equals(readHEAD(repo))) {
      logDebug("Ref {} not found", ref);
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
    magicBranch.perm = permissions.ref(ref);
    if (projectControl.getProject().getState()
        != com.google.gerrit.extensions.client.ProjectState.ACTIVE) {
      reject(cmd, "project is read only");
      return;
    }

    if (magicBranch.draft) {
      if (!receiveConfig.allowDrafts) {
        errors.put(Error.CODE_REVIEW, ref);
        reject(cmd, "draft workflow is disabled");
        return;
      } else if (projectControl
          .controlForRef(MagicBranch.NEW_DRAFT_CHANGE + ref)
          .isBlocked(Permission.PUSH)) {
        errors.put(Error.CODE_REVIEW, ref);
        reject(cmd, "cannot upload drafts");
        return;
      }
    }

    try {
      magicBranch.perm.check(RefPermission.CREATE_CHANGE);
    } catch (AuthException denied) {
      errors.put(Error.CODE_REVIEW, ref);
      reject(cmd, denied.getMessage());
      return;
    }

    if (magicBranch.isPrivate && magicBranch.removePrivate) {
      reject(cmd, "the options 'private' and 'remove-private' are mutually exclusive");
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

    if (magicBranch.draft && magicBranch.submit) {
      reject(cmd, "cannot submit draft");
      return;
    }

    if (magicBranch.submit) {
      try {
        permissions.ref(ref).check(RefPermission.UPDATE_BY_SUBMIT);
      } catch (AuthException e) {
        reject(cmd, e.getMessage());
        return;
      }
    }

    RevWalk walk = rp.getRevWalk();
    RevCommit tip;
    try {
      tip = walk.parseCommit(magicBranch.cmd.getNewId());
      logDebug("Tip of push: {}", tip.name());
    } catch (IOException ex) {
      magicBranch.cmd.setResult(REJECTED_MISSING_OBJECT);
      logError("Invalid pack upload; one or more objects weren't sent", ex);
      return;
    }

    String destBranch = magicBranch.dest.get();
    try {
      if (magicBranch.merged) {
        if (magicBranch.draft) {
          reject(cmd, "cannot be draft & merged");
          return;
        }
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
        logDebug("Forcing newChangeForAllNotInTarget = false");
        newChangeForAllNotInTarget = false;
      }

      if (magicBranch.base != null) {
        logDebug("Handling %base: {}", magicBranch.base);
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
            logWarn(String.format("Project %s cannot read %s", project.getName(), id.name()), e);
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
        logDebug("Set baseCommit = {}", magicBranch.baseCommit.get(0).name());
      }
    } catch (IOException ex) {
      logWarn(
          String.format("Error walking to %s in project %s", destBranch, project.getName()), ex);
      reject(cmd, "internal server error");
      return;
    }

    // Validate that the new commits are connected with the target
    // branch.  If they aren't, we want to abort. We do this check by
    // looking to see if we can compute a merge base between the new
    // commits and the target branch head.
    //
    try {
      Ref targetRef = rp.getAdvertisedRefs().get(magicBranch.ctl.getRefName());
      if (targetRef == null || targetRef.getObjectId() == null) {
        // The destination branch does not yet exist. Assume the
        // history being sent for review will start it and thus
        // is "connected" to the branch.
        logDebug("Branch is unborn");
        return;
      }
      RevCommit h = walk.parseCommit(targetRef.getObjectId());
      logDebug("Current branch tip: {}", h.name());
      RevFilter oldRevFilter = walk.getRevFilter();
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
      logError("Invalid pack upload; one or more objects weren't sent", e);
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

  private RevCommit readBranchTip(ReceiveCommand cmd, Branch.NameKey branch) throws IOException {
    Ref r = allRefs.get(branch.get());
    if (r == null) {
      reject(cmd, branch.get() + " not found");
      return null;
    }
    return rp.getRevWalk().parseCommit(r.getObjectId());
  }

  private void parseReplaceCommand(ReceiveCommand cmd, Change.Id changeId) {
    logDebug("Parsing replace command");
    if (cmd.getType() != ReceiveCommand.Type.CREATE) {
      reject(cmd, "invalid usage");
      return;
    }

    RevCommit newCommit;
    try {
      newCommit = rp.getRevWalk().parseCommit(cmd.getNewId());
      logDebug("Replacing with {}", newCommit);
    } catch (IOException e) {
      logError("Cannot parse " + cmd.getNewId().name() + " as commit", e);
      reject(cmd, "invalid commit");
      return;
    }

    Change changeEnt;
    try {
      changeEnt = notesFactory.createChecked(db, project.getNameKey(), changeId).getChange();
    } catch (NoSuchChangeException e) {
      logError("Change not found " + changeId, e);
      reject(cmd, "change " + changeId + " not found");
      return;
    } catch (OrmException e) {
      logError("Cannot lookup existing change " + changeId, e);
      reject(cmd, "database error");
      return;
    }
    if (!project.getNameKey().equals(changeEnt.getProject())) {
      reject(cmd, "change " + changeId + " does not belong to project " + project.getName());
      return;
    }

    logDebug("Replacing change {}", changeEnt.getId());
    requestReplace(cmd, true, changeEnt, newCommit);
  }

  private boolean requestReplace(
      ReceiveCommand cmd, boolean checkMergedInto, Change change, RevCommit newCommit) {
    if (change.getStatus().isClosed()) {
      reject(cmd, "change " + canonicalWebUrl + change.getId() + " closed");
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

  private void selectNewAndReplacedChangesFromMagicBranch() {
    logDebug("Finding new and replaced changes");
    newChanges = new ArrayList<>();

    ListMultimap<ObjectId, Ref> existing = changeRefsById();
    GroupCollector groupCollector =
        GroupCollector.create(changeRefsById(), db, psUtil, notesFactory, project.getNameKey());

    try {
      RevCommit start = setUpWalkForSelectingChanges();
      if (start == null) {
        return;
      }

      LinkedHashMap<RevCommit, ChangeLookup> pending = new LinkedHashMap<>();
      Set<Change.Key> newChangeIds = new HashSet<>();
      int maxBatchChanges = receiveConfig.getEffectiveMaxBatchChangesLimit(user);
      int total = 0;
      int alreadyTracked = 0;
      boolean rejectImplicitMerges =
          start.getParentCount() == 1
              && projectCache.get(project.getNameKey()).isRejectImplicitMerges()
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
        RevCommit c = rp.getRevWalk().next();
        if (c == null) {
          break;
        }
        total++;
        rp.getRevWalk().parseBody(c);
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

        String idStr = !idList.isEmpty() ? idList.get(idList.size() - 1).trim() : null;

        if (idStr != null) {
          pending.put(c, new ChangeLookup(c, new Change.Key(idStr)));
        } else {
          pending.put(c, new ChangeLookup(c));
        }
        int n = pending.size() + newChanges.size();
        if (maxBatchChanges != 0 && n > maxBatchChanges) {
          logDebug("{} changes exceeds limit of {}", n, maxBatchChanges);
          reject(
              magicBranch.cmd,
              "the number of pushed changes in a batch exceeds the max limit " + maxBatchChanges);
          newChanges = Collections.emptyList();
          return;
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

          logDebug("Creating new change for {} even though it is already tracked", name);
        }

        if (!validCommit(rp.getRevWalk(), magicBranch.perm, magicBranch.ctl, magicBranch.cmd, c)) {
          // Not a change the user can propose? Abort as early as possible.
          newChanges = Collections.emptyList();
          logDebug("Aborting early due to invalid commit");
          return;
        }

        // Don't allow merges to be uploaded in commit chain via all-not-in-target
        if (newChangeForAllNotInTarget && c.getParentCount() > 1) {
          reject(
              magicBranch.cmd,
              "Pushing merges in commit chains with 'all not in target' is not allowed,\n"
                  + "to override please set the base manually");
          logDebug("Rejecting merge commit {} with newChangeForAllNotInTarget", name);
          // TODO(dborowitz): Should we early return here?
        }

        if (idList.isEmpty()) {
          newChanges.add(new CreateRequest(c, magicBranch.dest.get()));
          continue;
        }
      }
      logDebug(
          "Finished initial RevWalk with {} commits total: {} already"
              + " tracked, {} new changes with no Change-Id, and {} deferred"
              + " lookups",
          total,
          alreadyTracked,
          newChanges.size(),
          pending.size());

      if (rejectImplicitMerges) {
        rejectImplicitMerges(mergedParents);
      }

      for (Iterator<ChangeLookup> itr = pending.values().iterator(); itr.hasNext(); ) {
        ChangeLookup p = itr.next();
        if (p.changeKey == null) {
          continue;
        }

        if (newChangeIds.contains(p.changeKey)) {
          logDebug("Multiple commits with Change-Id {}", p.changeKey);
          reject(magicBranch.cmd, SAME_CHANGE_ID_IN_MULTIPLE_CHANGES);
          newChanges = Collections.emptyList();
          return;
        }

        List<ChangeData> changes = p.destChanges;
        if (changes.size() > 1) {
          logDebug(
              "Multiple changes in branch {} with Change-Id {}: {}",
              magicBranch.dest,
              p.changeKey,
              changes.stream().map(cd -> cd.getId().toString()).collect(joining()));
          // WTF, multiple changes in this branch have the same key?
          // Since the commit is new, the user should recreate it with
          // a different Change-Id. In practice, we should never see
          // this error message as Change-Id should be unique per branch.
          //
          reject(magicBranch.cmd, p.changeKey.get() + " has duplicates");
          newChanges = Collections.emptyList();
          return;
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
          newChanges = Collections.emptyList();
          return;
        }

        if (changes.size() == 0) {
          if (!isValidChangeId(p.changeKey.get())) {
            reject(magicBranch.cmd, "invalid Change-Id");
            newChanges = Collections.emptyList();
            return;
          }

          // In case the change look up from the index failed,
          // double check against the existing refs
          if (foundInExistingRef(existing.get(p.commit))) {
            if (pending.size() == 1) {
              reject(magicBranch.cmd, "commit(s) already exists (as current patchset)");
              newChanges = Collections.emptyList();
              return;
            }
            itr.remove();
            continue;
          }
          newChangeIds.add(p.changeKey);
        }
        newChanges.add(new CreateRequest(p.commit, magicBranch.dest.get()));
      }
      logDebug(
          "Finished deferred lookups with {} updates and {} new changes",
          replaceByChange.size(),
          newChanges.size());
    } catch (IOException e) {
      // Should never happen, the core receive process would have
      // identified the missing object earlier before we got control.
      //
      magicBranch.cmd.setResult(REJECTED_MISSING_OBJECT);
      logError("Invalid pack upload; one or more objects weren't sent", e);
      newChanges = Collections.emptyList();
      return;
    } catch (OrmException e) {
      logError("Cannot query database to locate prior changes", e);
      reject(magicBranch.cmd, "database error");
      newChanges = Collections.emptyList();
      return;
    }

    if (newChanges.isEmpty() && replaceByChange.isEmpty()) {
      reject(magicBranch.cmd, "no new changes");
      return;
    }
    if (!newChanges.isEmpty() && magicBranch.edit) {
      reject(magicBranch.cmd, "edit is not supported for new changes");
      return;
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
      logDebug("Finished updating groups from GroupCollector");
    } catch (OrmException e) {
      logError("Error collecting groups for changes", e);
      reject(magicBranch.cmd, "internal server error");
      return;
    }
  }

  private boolean foundInExistingRef(Collection<Ref> existingRefs) throws OrmException {
    for (Ref ref : existingRefs) {
      ChangeNotes notes =
          notesFactory.create(db, project.getNameKey(), Change.Id.fromRef(ref.getName()));
      Change change = notes.getChange();
      if (change.getDest().equals(magicBranch.dest)) {
        logDebug("Found change {} from existing refs.", change.getKey());
        // Reindex the change asynchronously, ignoring errors.
        @SuppressWarnings("unused")
        Future<?> possiblyIgnoredError = indexer.indexAsync(project.getNameKey(), change.getId());
        return true;
      }
    }
    return false;
  }

  private RevCommit setUpWalkForSelectingChanges() throws IOException {
    RevWalk rw = rp.getRevWalk();
    RevCommit start = rw.parseCommit(magicBranch.cmd.getNewId());

    rw.reset();
    rw.sort(RevSort.TOPO);
    rw.sort(RevSort.REVERSE, true);
    rp.getRevWalk().markStart(start);
    if (magicBranch.baseCommit != null) {
      markExplicitBasesUninteresting();
    } else if (magicBranch.merged) {
      logDebug("Marking parents of merged commit {} uninteresting", start.name());
      for (RevCommit c : start.getParents()) {
        rw.markUninteresting(c);
      }
    } else {
      markHeadsAsUninteresting(rw, magicBranch.ctl != null ? magicBranch.ctl.getRefName() : null);
    }
    return start;
  }

  private void markExplicitBasesUninteresting() throws IOException {
    logDebug("Marking {} base commits uninteresting", magicBranch.baseCommit.size());
    for (RevCommit c : magicBranch.baseCommit) {
      rp.getRevWalk().markUninteresting(c);
    }
    Ref targetRef = allRefs.get(magicBranch.ctl.getRefName());
    if (targetRef != null) {
      logDebug(
          "Marking target ref {} ({}) uninteresting",
          magicBranch.ctl.getRefName(),
          targetRef.getObjectId().name());
      rp.getRevWalk().markUninteresting(rp.getRevWalk().parseCommit(targetRef.getObjectId()));
    }
  }

  private void rejectImplicitMerges(Set<RevCommit> mergedParents) throws IOException {
    if (!mergedParents.isEmpty()) {
      Ref targetRef = allRefs.get(magicBranch.ctl.getRefName());
      if (targetRef != null) {
        RevWalk rw = rp.getRevWalk();
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

  private void markHeadsAsUninteresting(RevWalk rw, @Nullable String forRef) {
    int i = 0;
    for (Ref ref : allRefs.values()) {
      if ((ref.getName().startsWith(R_HEADS) || ref.getName().equals(forRef))
          && ref.getObjectId() != null) {
        try {
          rw.markUninteresting(rw.parseCommit(ref.getObjectId()));
          i++;
        } catch (IOException e) {
          logWarn(String.format("Invalid ref %s in %s", ref.getName(), project.getName()), e);
        }
      }
    }
    logDebug("Marked {} heads as uninteresting", i);
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
      changeId = new Change.Id(id);
      ins =
          changeInserterFactory
              .create(changeId, commit, refName)
              .setTopic(magicBranch.topic)
              .setPrivate(magicBranch.isPrivate)
              .setWorkInProgress(magicBranch.workInProgress)
              // Changes already validated in validateNewCommits.
              .setValidate(false);

      if (magicBranch.draft) {
        ins.setDraft(magicBranch.draft);
      } else if (magicBranch.merged) {
        ins.setStatus(Change.Status.MERGED);
      }
      cmd = new ReceiveCommand(ObjectId.zeroId(), commit, ins.getPatchSetId().toRefName());
      if (rp.getPushCertificate() != null) {
        ins.setPushCertificate(rp.getPushCertificate().toTextWithSignature());
      }
    }

    private void addOps(BatchUpdate bu) throws RestApiException {
      checkState(changeId != null, "must call setChangeId before addOps");
      try {
        RevWalk rw = rp.getRevWalk();
        rw.parseBody(commit);
        final PatchSet.Id psId = ins.setGroups(groups).getPatchSetId();
        Account.Id me = user.getAccountId();
        List<FooterLine> footerLines = commit.getFooterLines();
        MailRecipients recipients = new MailRecipients();
        Map<String, Short> approvals = new HashMap<>();
        checkNotNull(magicBranch);
        recipients.add(magicBranch.getMailRecipients());
        approvals = magicBranch.labels;
        recipients.add(
            getRecipientsFromFooters(db, accountResolver, magicBranch.draft, footerLines));
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
      throws OrmException, RestApiException {
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
    logDebug(
        "Processing submit with tip change {} ({})", tipChange.getId(), magicBranch.cmd.getNewId());
    try (MergeOp op = mergeOpFactory.create(batchUpdateFactory)) {
      op.merge(db, tipChange, user, false, new SubmitInput(), false);
    }
  }

  private void preparePatchSetsForReplace() {
    try {
      readChangesForReplace();
      for (Iterator<ReplaceRequest> itr = replaceByChange.values().iterator(); itr.hasNext(); ) {
        ReplaceRequest req = itr.next();
        if (req.inputCommand.getResult() == NOT_ATTEMPTED) {
          req.validate(false);
          if (req.skip && req.cmd == null) {
            itr.remove();
          }
        }
      }
    } catch (OrmException err) {
      logError(
          String.format(
              "Cannot read database before replacement for project %s", project.getName()),
          err);
      for (ReplaceRequest req : replaceByChange.values()) {
        if (req.inputCommand.getResult() == NOT_ATTEMPTED) {
          req.inputCommand.setResult(REJECTED_OTHER_REASON, "internal server error");
        }
      }
    } catch (IOException | PermissionBackendException err) {
      logError(
          String.format(
              "Cannot read repository before replacement for project %s", project.getName()),
          err);
      for (ReplaceRequest req : replaceByChange.values()) {
        if (req.inputCommand.getResult() == NOT_ATTEMPTED) {
          req.inputCommand.setResult(REJECTED_OTHER_REASON, "internal server error");
        }
      }
    }
    logDebug("Read {} changes to replace", replaceByChange.size());

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
    boolean skip;
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
              rp.getRevWalk().parseCommit(ref.getObjectId()), PatchSet.Id.fromRef(ref.getName()));
        } catch (IOException err) {
          logWarn(
              String.format(
                  "Project %s contains invalid change ref %s", project.getName(), ref.getName()),
              err);
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
     *   <li>May reset {@code rp.getRevWalk()}; do not call in the middle of a walk.
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

      RevCommit newCommit = rp.getRevWalk().parseCommit(newCommitId);
      RevCommit priorCommit = revisions.inverse().get(priorPatchSet);
      try {
        permissions.change(notes).database(db).check(ChangePermission.ADD_PATCH_SET);
      } catch (AuthException no) {
        String locked = ".";
        if (projectControl.controlFor(notes).isPatchSetLocked(db)) {
          locked = ". Change is patch set locked.";
        }
        reject(inputCommand, "cannot add patch set to " + ontoChange + locked);
        return false;
      }

      if (change.getStatus().isClosed()) {
        reject(inputCommand, "change " + ontoChange + " closed");
        return false;
      } else if (revisions.containsKey(newCommit)) {
        reject(inputCommand, "commit already exists (in the change)");
        return false;
      }

      for (Ref r : rp.getRepository().getRefDatabase().getRefs("refs/changes").values()) {
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
          reject(inputCommand, SAME_CHANGE_ID_IN_MULTIPLE_CHANGES);
          return false;
        }
      }

      PermissionBackend.ForRef perm = permissions.ref(change.getDest().get());
      RefControl refctl = projectControl.controlForRef(change.getDest());
      if (!validCommit(rp.getRevWalk(), perm, refctl, inputCommand, newCommit)) {
        return false;
      }
      rp.getRevWalk().parseBody(priorCommit);

      // Don't allow the same tree if the commit message is unmodified
      // or no parents were updated (rebase), else warn that only part
      // of the commit was modified.
      if (newCommit.getTree().equals(priorCommit.getTree())) {
        boolean messageEq = eq(newCommit.getFullMessage(), priorCommit.getFullMessage());
        boolean parentsEq = parentsEqual(newCommit, priorCommit);
        boolean authorEq = authorEqual(newCommit, priorCommit);
        ObjectReader reader = rp.getRevWalk().getObjectReader();

        if (messageEq && parentsEq && authorEq && !autoClose) {
          addMessage(
              String.format(
                  "(W) No changes between prior commit %s and new commit %s",
                  reader.abbreviate(priorCommit).name(), reader.abbreviate(newCommit).name()));
        } else {
          StringBuilder msg = new StringBuilder();
          msg.append("(I) ");
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

      if (magicBranch != null && magicBranch.edit) {
        return newEdit();
      }

      newPatchSet();
      return true;
    }

    private boolean newEdit() {
      psId = notes.getChange().currentPatchSetId();
      Optional<ChangeEdit> edit = null;

      try {
        edit = editUtil.byChange(projectControl.controlFor(notes));
      } catch (AuthException | IOException e) {
        logError("Cannot retrieve edit", e);
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

    private void newPatchSet() throws IOException {
      RevCommit newCommit = rp.getRevWalk().parseCommit(newCommitId);
      psId =
          ChangeUtil.nextPatchSetIdFromAllRefsMap(allRefs, notes.getChange().currentPatchSetId());
      info = patchSetInfoFactory.get(rp.getRevWalk(), newCommit, psId);
      cmd = new ReceiveCommand(ObjectId.zeroId(), newCommitId, psId.toRefName());
    }

    void addOps(BatchUpdate bu, @Nullable Task progress) throws IOException {
      if (magicBranch != null && magicBranch.edit) {
        bu.addOp(notes.getChangeId(), new ReindexOnlyOp());
        if (prev != null) {
          bu.addRepoOnlyOp(new UpdateOneRefOp(prev));
        }
        bu.addRepoOnlyOp(new UpdateOneRefOp(cmd));
        return;
      }
      RevWalk rw = rp.getRevWalk();
      // TODO(dborowitz): Move to ReplaceOp#updateRepo.
      RevCommit newCommit = rw.parseCommit(newCommitId);
      rw.parseBody(newCommit);

      RevCommit priorCommit = revisions.inverse().get(priorPatchSet);
      replaceOp =
          replaceOpFactory
              .create(
                  projectControl,
                  notes.getChange().getDest(),
                  checkMergedInto,
                  priorPatchSet,
                  priorCommit,
                  psId,
                  newCommit,
                  info,
                  groups,
                  magicBranch,
                  rp.getPushCertificate())
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
        logDebug("Updating tag cache on fast-forward of {}", cmd.getRefName());
        tagCache.updateFastForward(project.getNameKey(), refName, cmd.getOldId(), cmd.getNewId());
      }
      if (isConfig(cmd)) {
        logDebug("Reloading project in cache");
        projectCache.evict(project);
        ProjectState ps = projectCache.get(project.getNameKey());
        try {
          logDebug("Updating project description");
          repo.setGitwebDescription(ps.getProject().getDescription());
        } catch (IOException e) {
          log.warn("cannot update description of " + project.getName(), e);
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
          MultimapBuilder.hashKeys(allRefs.size() / estRefsPerChange)
              .arrayListValues(estRefsPerChange)
              .build();
      for (Ref ref : allRefs.values()) {
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

  private void validateNewCommits(RefControl ctl, ReceiveCommand cmd)
      throws PermissionBackendException {
    PermissionBackend.ForRef perm = permissions.ref(ctl.getRefName());
    if (!RefNames.REFS_CONFIG.equals(cmd.getRefName())
        && !(MagicBranch.isMagicBranch(cmd.getRefName())
            || NEW_PATCHSET.matcher(cmd.getRefName()).matches())
        && pushOptions.containsKey(BYPASS_REVIEW)) {
      try {
        perm.check(RefPermission.BYPASS_REVIEW);
        if (!Iterables.isEmpty(rejectCommits)) {
          throw new AuthException("reject-commits prevents " + BYPASS_REVIEW);
        }
        logDebug("Short-circuiting new commit validation");
      } catch (AuthException denied) {
        reject(cmd, denied.getMessage());
      }
      return;
    }

    boolean defaultName = Strings.isNullOrEmpty(user.getAccount().getFullName());
    RevWalk walk = rp.getRevWalk();
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
      int i = 0;
      for (RevCommit c; (c = walk.next()) != null; ) {
        i++;
        if (existing.keySet().contains(c)) {
          continue;
        } else if (!validCommit(walk, perm, ctl, cmd, c)) {
          break;
        }

        if (defaultName && user.hasEmailAddress(c.getCommitterIdent().getEmailAddress())) {
          try {
            Account a = accounts.get(db, user.getAccountId());
            if (a != null && Strings.isNullOrEmpty(a.getFullName())) {
              a.setFullName(c.getCommitterIdent().getName());
              accountsUpdate.create().update(db, a);
              user.getAccount().setFullName(a.getFullName());
            }
          } catch (OrmException e) {
            logWarn("Cannot default full_name", e);
          } finally {
            defaultName = false;
          }
        }
      }
      logDebug("Validated {} new commits", i);
    } catch (IOException err) {
      cmd.setResult(REJECTED_MISSING_OBJECT);
      logError("Invalid pack upload; one or more objects weren't sent", err);
    }
  }

  private boolean validCommit(
      RevWalk rw, PermissionBackend.ForRef perm, RefControl ctl, ReceiveCommand cmd, ObjectId id)
      throws IOException {

    if (validCommits.contains(id)) {
      return true;
    }

    RevCommit c = rw.parseCommit(id);
    rw.parseBody(c);

    try (CommitReceivedEvent receiveEvent =
        new CommitReceivedEvent(cmd, project, ctl.getRefName(), rw.getObjectReader(), c, user)) {
      boolean isMerged =
          magicBranch != null
              && cmd.getRefName().equals(magicBranch.cmd.getRefName())
              && magicBranch.merged;
      CommitValidators validators =
          isMerged
              ? commitValidatorsFactory.forMergedCommits(perm, ctl)
              : commitValidatorsFactory.forReceiveCommits(perm, ctl, sshInfo, repo, rw);
      messages.addAll(validators.validate(receiveEvent));
    } catch (CommitValidationException e) {
      logDebug("Commit validation failed on {}", c.name());
      messages.addAll(e.getMessages());
      reject(cmd, e.getMessage());
      return false;
    }
    validCommits.add(c.copy());
    return true;
  }

  private void autoCloseChanges(ReceiveCommand cmd) {
    logDebug("Starting auto-closing of changes");
    String refName = cmd.getRefName();
    checkState(
        !MagicBranch.isMagicBranch(refName),
        "shouldn't be auto-closing changes on magic branch %s",
        refName);
    // TODO(dborowitz): Combine this BatchUpdate with the main one in
    // insertChangesAndPatchSets.
    try (BatchUpdate bu =
            batchUpdateFactory.create(
                db, projectControl.getProject().getNameKey(), user, TimeUtil.nowTs());
        ObjectInserter ins = repo.newObjectInserter();
        ObjectReader reader = ins.newReader();
        RevWalk rw = new RevWalk(reader)) {
      bu.setRepository(repo, rw, ins).updateChangesInParallel();
      bu.setRequestId(receiveId);
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
          existingPatchSets++;
          PatchSet.Id psId = PatchSet.Id.fromRef(ref.getName());
          bu.addOp(
              psId.getParentKey(),
              mergedByPushOpFactory.create(requestScopePropagator, psId, refName));
          continue COMMIT;
        }

        for (String changeId : c.getFooterLines(CHANGE_ID)) {
          if (byKey == null) {
            byKey = openChangesByBranch(branch);
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
          logDebug("Not closing {} because validation failed", id);
          continue;
        }
        req.addOps(bu, null);
        bu.addOp(
            id,
            mergedByPushOpFactory
                .create(requestScopePropagator, req.psId, refName)
                .setPatchSetProvider(
                    new Provider<PatchSet>() {
                      @Override
                      public PatchSet get() {
                        return req.replaceOp.getPatchSet();
                      }
                    }));
        bu.addOp(id, new ChangeProgressOp(closeProgress));
      }

      logDebug(
          "Auto-closing {} changes with existing patch sets and {} with new patch sets",
          existingPatchSets,
          newPatchSets);
      bu.execute();
    } catch (RestApiException e) {
      logError("Can't insert patchset", e);
    } catch (IOException | OrmException | UpdateException | PermissionBackendException e) {
      logError("Can't scan for changes to close", e);
    }
  }

  private Map<Change.Key, ChangeNotes> openChangesByBranch(Branch.NameKey branch)
      throws OrmException {
    Map<Change.Key, ChangeNotes> r = new HashMap<>();
    for (ChangeData cd : queryProvider.get().byBranchOpen(branch)) {
      r.put(cd.change().getKey(), cd.notes());
    }
    return r;
  }

  private void reject(@Nullable ReceiveCommand cmd, String why) {
    if (cmd != null) {
      cmd.setResult(REJECTED_OTHER_REASON, why);
      commandProgress.update(1);
    }
  }

  private static boolean isHead(ReceiveCommand cmd) {
    return cmd.getRefName().startsWith(Constants.R_HEADS);
  }

  private static boolean isConfig(ReceiveCommand cmd) {
    return cmd.getRefName().equals(RefNames.REFS_CONFIG);
  }

  private void logDebug(String msg, Object... args) {
    if (log.isDebugEnabled()) {
      log.debug(receiveId + msg, args);
    }
  }

  private void logWarn(String msg, Throwable t) {
    if (log.isWarnEnabled()) {
      if (t != null) {
        log.warn(receiveId + msg, t);
      } else {
        log.warn(receiveId + msg);
      }
    }
  }

  private void logWarn(String msg) {
    logWarn(msg, null);
  }

  private void logError(String msg, Throwable t) {
    if (log.isErrorEnabled()) {
      if (t != null) {
        log.error(receiveId + msg, t);
      } else {
        log.error(receiveId + msg);
      }
    }
  }

  private void logError(String msg) {
    logError(msg, null);
  }
}
