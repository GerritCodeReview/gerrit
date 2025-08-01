// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.restapi.change;

import static com.google.common.base.MoreObjects.firstNonNull;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.CHANGE_MODIFICATION;
import static org.eclipse.jgit.lib.Constants.SIGNED_OFF_BY_TAG;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.common.UsedAt;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.entities.converter.ChangeInputProtoConverter;
import com.google.gerrit.exceptions.InvalidMergeStrategyException;
import com.google.gerrit.exceptions.MergeWithConflictsNotSupportedException;
import com.google.gerrit.extensions.api.accounts.AccountInput;
import com.google.gerrit.extensions.api.changes.NotifyHandling;
import com.google.gerrit.extensions.client.ChangeStatus;
import com.google.gerrit.extensions.client.ListChangesOption;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ChangeInfo;
import com.google.gerrit.extensions.common.ChangeInput;
import com.google.gerrit.extensions.common.MergeInput;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestCollectionModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.proto.Entities;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.PatchSetUtil;
import com.google.gerrit.server.Sequences;
import com.google.gerrit.server.change.ChangeFinder;
import com.google.gerrit.server.change.ChangeInserter;
import com.google.gerrit.server.change.ChangeJson;
import com.google.gerrit.server.change.ChangeResource;
import com.google.gerrit.server.change.NotifyResolver;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CodeReviewCommit.CodeReviewRevWalk;
import com.google.gerrit.server.git.CommitUtil;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MergeUtil;
import com.google.gerrit.server.git.MergeUtilFactory;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.patch.ApplyPatchUtil;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ContributorAgreementsChecker;
import com.google.gerrit.server.project.InvalidChangeOperationException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.restapi.change.CreateChange.CommitTreeSupplier;
import com.google.gerrit.server.restapi.project.CommitsCollection;
import com.google.gerrit.server.restapi.project.ProjectsCollection;
import com.google.gerrit.server.update.BatchUpdate;
import com.google.gerrit.server.update.UpdateException;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.gerrit.server.util.CommitMessageUtil;
import com.google.gerrit.server.util.time.TimeUtil;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.annotations.NonNull;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.InvalidObjectIdException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.NoMergeBaseException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.ObjectReader;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.TreeFormatter;
import org.eclipse.jgit.patch.PatchApplier;
import org.eclipse.jgit.patch.PatchApplier.Result.Error;
import org.eclipse.jgit.revwalk.FooterKey;
import org.eclipse.jgit.revwalk.FooterLine;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.util.ChangeIdUtil;

@Singleton
public class CreateChange
    implements RestCollectionModifyView<TopLevelResource, ChangeResource, ChangeInput> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private static final ChangeInputProtoConverter CHANGE_INPUT_PROTO_CONVERTER =
      ChangeInputProtoConverter.INSTANCE;

  private final BatchUpdate.Factory updateFactory;
  private final String anonymousCowardName;
  private final GitRepositoryManager gitManager;
  private final Sequences seq;
  private final ZoneId serverZoneId;
  private final PermissionBackend permissionBackend;
  private final Provider<CurrentUser> user;
  private final ProjectsCollection projectsCollection;
  private final CommitsCollection commits;
  private final ChangeInserter.Factory changeInserterFactory;
  private final ChangeJson.Factory jsonFactory;
  private final ChangeFinder changeFinder;
  private final Provider<InternalChangeQuery> queryProvider;
  private final PatchSetUtil psUtil;
  private final MergeUtilFactory mergeUtilFactory;
  private final SubmitType submitType;
  private final NotifyResolver notifyResolver;
  private final ContributorAgreementsChecker contributorAgreements;
  private final boolean disablePrivateChanges;
  private final boolean useDiff3;

  @Inject
  CreateChange(
      BatchUpdate.Factory updateFactory,
      @AnonymousCowardName String anonymousCowardName,
      GitRepositoryManager gitManager,
      Sequences seq,
      @GerritPersonIdent PersonIdent myIdent,
      PermissionBackend permissionBackend,
      Provider<CurrentUser> user,
      ProjectsCollection projectsCollection,
      CommitsCollection commits,
      ChangeInserter.Factory changeInserterFactory,
      ChangeJson.Factory json,
      ChangeFinder changeFinder,
      Provider<InternalChangeQuery> queryProvider,
      PatchSetUtil psUtil,
      @GerritServerConfig Config config,
      MergeUtilFactory mergeUtilFactory,
      NotifyResolver notifyResolver,
      ContributorAgreementsChecker contributorAgreements) {
    this.updateFactory = updateFactory;
    this.anonymousCowardName = anonymousCowardName;
    this.gitManager = gitManager;
    this.seq = seq;
    this.serverZoneId = myIdent.getZoneId();
    this.permissionBackend = permissionBackend;
    this.user = user;
    this.projectsCollection = projectsCollection;
    this.commits = commits;
    this.changeInserterFactory = changeInserterFactory;
    this.jsonFactory = json;
    this.changeFinder = changeFinder;
    this.queryProvider = queryProvider;
    this.psUtil = psUtil;
    this.submitType = config.getEnum("project", null, "submitType", SubmitType.MERGE_IF_NECESSARY);
    this.disablePrivateChanges = config.getBoolean("change", null, "disablePrivateChanges", false);
    this.mergeUtilFactory = mergeUtilFactory;
    this.notifyResolver = notifyResolver;
    this.contributorAgreements = contributorAgreements;
    this.useDiff3 =
        config.getBoolean(
            "change", /* subsection= */ null, "diff3ConflictView", /* defaultValue= */ false);
  }

  @Override
  public Response<ChangeInfo> apply(TopLevelResource parent, ChangeInput input)
      throws IOException,
          InvalidChangeOperationException,
          RestApiException,
          UpdateException,
          PermissionBackendException,
          ConfigInvalidException {
    if (Strings.isNullOrEmpty(input.project)) {
      throw new BadRequestException("project must be non-empty");
    }

    return execute(updateFactory, input, projectsCollection.parse(input.project));
  }

  @UsedAt(UsedAt.Project.GOOGLE)
  @FunctionalInterface
  public interface CommitTreeSupplier {
    @NonNull
    ObjectId get(
        Repository repo, ObjectInserter oi, ObjectReader or, ChangeInput input, RevCommit mergeTip)
        throws IOException, RestApiException;
  }

  /**
   * Creates the changes in the given project, using the proto representation of ChangeInput -
   * {@link com.google.gerrit.proto.Entities.ChangeInput}.
   */
  @UsedAt(UsedAt.Project.GOOGLE)
  public Response<ChangeInfo> execute(
      BatchUpdate.Factory updateFactory,
      Entities.ChangeInput input,
      CommitTreeSupplier commitTreeSupplier)
      throws IOException,
          RestApiException,
          UpdateException,
          PermissionBackendException,
          ConfigInvalidException {
    return execute(
        updateFactory,
        CHANGE_INPUT_PROTO_CONVERTER.fromProto(input),
        projectsCollection.parse(input.getProject()),
        Optional.of(commitTreeSupplier));
  }

  /**
   * Creates the changes in the given project, using the java-class representation of ChangeInput -
   * {@link com.google.gerrit.extensions.common.ChangeInput}. This is public for reuse in the
   * project API.
   */
  public Response<ChangeInfo> execute(
      BatchUpdate.Factory updateFactory, ChangeInput input, ProjectResource projectResource)
      throws IOException,
          RestApiException,
          UpdateException,
          PermissionBackendException,
          ConfigInvalidException {
    return execute(updateFactory, input, projectResource, Optional.empty());
  }

  private Response<ChangeInfo> execute(
      BatchUpdate.Factory updateFactory,
      ChangeInput input,
      ProjectResource projectResource,
      Optional<CommitTreeSupplier> commitTreeSupplier)
      throws IOException,
          RestApiException,
          UpdateException,
          PermissionBackendException,
          ConfigInvalidException {
    if (!user.get().isIdentifiedUser()) {
      throw new AuthException("Authentication required");
    }

    ProjectState projectState = projectResource.getProjectState();
    projectState.checkStatePermitsWrite();

    IdentifiedUser me = user.get().asIdentifiedUser();
    checkAndSanitizeChangeInput(input, me, commitTreeSupplier);

    Project.NameKey project = projectResource.getNameKey();
    contributorAgreements.check(project, user.get());

    checkRequiredPermissions(project, input.branch, input.author);

    ChangeInfo newChange =
        createNewChange(input, me, projectState, updateFactory, commitTreeSupplier);
    return Response.created(newChange);
  }

  /**
   * Checks and sanitizes the user input, e.g. check whether the input is legal; clean the input so
   * that it meets the requirement for creating a change; set a field based on the global configs,
   * etc.
   *
   * @param input the {@code ChangeInput} from the request. Note this method modify the {@code
   *     ChangeInput} object so that it can be reused directly by follow-up code.
   * @param me the user who sent the current request to create a change.
   * @throws BadRequestException if the input is not legal.
   */
  private void checkAndSanitizeChangeInput(
      ChangeInput input, IdentifiedUser me, Optional<CommitTreeSupplier> commitTreeSupplier)
      throws RestApiException, PermissionBackendException, IOException {
    if (Strings.isNullOrEmpty(input.branch)) {
      throw new BadRequestException("branch must be non-empty");
    }
    input.branch = RefNames.fullName(input.branch);
    if (!isBranchAllowed(input.branch)) {
      throw new BadRequestException(
          "Cannot create a change on ref "
              + input.branch
              + ". Gerrit internal refs and refs/tags/* are not allowed.");
    }

    String subject = Strings.nullToEmpty(input.subject);
    subject = CommitMessageUtil.dropComments(subject).trim();
    if (subject.isEmpty()) {
      throw new BadRequestException("commit message must be non-empty");
    }
    input.subject = subject;

    Optional<String> changeId = CommitMessageUtil.getChangeIdFromCommitMessageFooter(input.subject);
    if (changeId.isPresent()) {
      if (!queryProvider
          .get()
          .setLimit(1)
          .byBranchKey(
              BranchNameKey.create(input.project, input.branch), Change.key(changeId.get()))
          .isEmpty()) {
        throw new ResourceConflictException(
            String.format(
                "A change with Change-Id %s already exists for this branch.", changeId.get()));
      }
    }

    if (input.topic != null) {
      input.topic = Strings.emptyToNull(input.topic.trim());
    }

    if (input.status != null && input.status != ChangeStatus.NEW) {
      throw new BadRequestException("unsupported change status");
    }

    if (input.baseChange != null && input.baseCommit != null) {
      throw new BadRequestException("only provide one of base_change or base_commit");
    }

    ProjectResource projectResource = projectsCollection.parse(input.project);
    // Checks whether the change to be created should be a private change.
    boolean privateByDefault =
        projectResource.getProjectState().is(BooleanProjectConfig.PRIVATE_BY_DEFAULT);
    boolean isPrivate = input.isPrivate == null ? privateByDefault : input.isPrivate;
    if (isPrivate && disablePrivateChanges) {
      throw new MethodNotAllowedException("private changes are disabled");
    }
    input.isPrivate = isPrivate;

    ProjectState projectState = projectResource.getProjectState();

    if (input.workInProgress == null) {
      if (projectState.is(BooleanProjectConfig.WORK_IN_PROGRESS_BY_DEFAULT)) {
        input.workInProgress = true;
      } else {
        input.workInProgress =
            firstNonNull(me.state().generalPreferences().workInProgressByDefault, false);
      }
    }

    if (input.merge != null) {
      if (!(submitType.equals(SubmitType.MERGE_ALWAYS)
          || submitType.equals(SubmitType.MERGE_IF_NECESSARY))) {
        throw new BadRequestException("Submit type: " + submitType + " is not supported");
      }
    }

    if (input.merge != null && input.patch != null) {
      throw new BadRequestException("Only one of `merge` and `patch` arguments can be set.");
    }

    if ((input.merge != null || input.patch != null) && commitTreeSupplier.isPresent()) {
      throw new BadRequestException(
          "`CommitTreeSupplier` cannot be provided along with `merge` or `patch` arguments");
    }

    if (input.author != null
        && (Strings.isNullOrEmpty(input.author.email)
            || Strings.isNullOrEmpty(input.author.name))) {
      throw new BadRequestException("Author must specify name and email");
    }
  }

  /** Changes are allowed to be created on any ref that is not Gerrit internal or a tag ref. */
  private boolean isBranchAllowed(String branch) {
    return !RefNames.isGerritRef(branch) && !branch.startsWith(RefNames.REFS_TAGS);
  }

  private void checkRequiredPermissions(
      Project.NameKey project, String refName, @Nullable AccountInput author)
      throws ResourceNotFoundException, AuthException, PermissionBackendException {
    PermissionBackend.ForRef forRef = permissionBackend.currentUser().project(project).ref(refName);
    if (!forRef.test(RefPermission.READ)) {
      throw new ResourceNotFoundException(String.format("ref %s not found", refName));
    }
    forRef.check(RefPermission.CREATE_CHANGE);
    if (author != null) {
      forRef.check(RefPermission.FORGE_AUTHOR);
    }
  }

  private ChangeInfo createNewChange(
      ChangeInput input,
      IdentifiedUser me,
      ProjectState projectState,
      BatchUpdate.Factory updateFactory,
      Optional<CommitTreeSupplier> commitTreeSupplier)
      throws RestApiException,
          PermissionBackendException,
          IOException,
          ConfigInvalidException,
          UpdateException {
    try (RefUpdateContext ctx = RefUpdateContext.open(CHANGE_MODIFICATION)) {
      logger.atFine().log(
          "Creating new change for target branch %s in project %s"
              + " (new branch = %s, base change = %s, base commit = %s)",
          input.branch,
          projectState.getName(),
          input.newBranch,
          input.baseChange,
          input.baseCommit);

      try (Repository git = gitManager.openRepository(projectState.getNameKey());
          ObjectInserter oi = git.newObjectInserter();
          ObjectReader reader = oi.newReader();
          CodeReviewRevWalk rw = CodeReviewCommit.newRevWalk(reader)) {
        PatchSet basePatchSet = null;
        List<String> groups = Collections.emptyList();

        if (input.baseChange != null) {
          ChangeNotes baseChange = getBaseChange(input.baseChange);
          basePatchSet = psUtil.current(baseChange);
          groups = basePatchSet.groups();
          logger.atFine().log("base patch set = %s (groups = %s)", basePatchSet.id(), groups);
        }

        ObjectId parentCommit =
            getParentCommit(
                git,
                rw,
                input.branch,
                input.newBranch,
                basePatchSet,
                input.baseCommit,
                input.merge);
        logger.atFine().log(
            "parent commit = %s", parentCommit != null ? parentCommit.name() : "NULL");

        RevCommit mergeTip = parentCommit == null ? null : rw.parseCommit(parentCommit);

        Instant now = TimeUtil.now();

        PersonIdent committer = me.newCommitterIdent(now, serverZoneId);
        PersonIdent author =
            input.author == null
                ? committer
                : new PersonIdent(input.author.name, input.author.email, now, serverZoneId);

        String commitMessage = getCommitMessage(projectState, input.subject, me);

        CodeReviewCommit c;
        boolean hasGitConflicts = false;
        if (input.merge != null) {
          // create a merge commit
          c =
              newMergeCommit(
                  git,
                  oi,
                  rw,
                  projectState,
                  mergeTip,
                  input.merge,
                  author,
                  committer,
                  commitMessage);
          if (!c.getFilesWithGitConflicts().isEmpty()) {
            logger.atFine().log(
                "merge commit has conflicts in the following files: %s",
                c.getFilesWithGitConflicts());
          }
        } else if (input.patch != null) {
          // create a commit with the given patch.
          if (mergeTip == null) {
            throw new BadRequestException("Cannot apply patch on top of an empty tree.");
          }
          PatchApplier.Result applyResult =
              ApplyPatchUtil.applyPatch(git, oi, input.patch, mergeTip);
          ObjectId treeId = applyResult.getTreeId();
          logger.atFine().log("tree ID after applying patch: %s", treeId.name());
          String appliedPatchCommitMessage =
              getCommitMessage(
                  projectState,
                  ApplyPatchUtil.buildCommitMessage(
                      input.subject,
                      ImmutableList.of(),
                      input.patch,
                      ApplyPatchUtil.getResultPatch(git, reader, mergeTip, rw.lookupTree(treeId)),
                      applyResult.getErrors()),
                  me);
          c =
              rw.parseCommit(
                  CommitUtil.createCommitWithTree(
                      oi,
                      author,
                      committer,
                      ImmutableList.of(mergeTip),
                      appliedPatchCommitMessage,
                      treeId));
          hasGitConflicts = applyResult.getErrors().stream().anyMatch(Error::isGitConflict);
        } else if (commitTreeSupplier.isPresent()) {
          c =
              createCommitWithSuppliedTree(
                  git,
                  oi,
                  reader,
                  rw,
                  mergeTip,
                  input,
                  commitTreeSupplier.get(),
                  author,
                  committer,
                  commitMessage);

        } else {
          // create an empty commit.
          c = createEmptyCommit(oi, rw, author, committer, mergeTip, commitMessage);
        }
        // Flush inserter so that commit becomes visible to validators
        logger.atFine().log("flushing inserter %s", oi);
        oi.flush();

        Change.Id changeId = Change.id(seq.nextChangeId());
        ChangeInserter ins = changeInserterFactory.create(changeId, c, input.branch);
        ins.setMessage(messageForNewChange(ins.getPatchSetId(), c));
        ins.setTopic(input.topic);
        ins.setPrivate(input.isPrivate);
        ins.setWorkInProgress(input.workInProgress || !c.getFilesWithGitConflicts().isEmpty());
        ins.setGroups(groups);
        c.getConflicts().ifPresent(ins::setConflicts);

        if (input.validationOptions != null) {
          ImmutableListMultimap.Builder<String, String> validationOptions =
              ImmutableListMultimap.builder();
          input
              .validationOptions
              .entrySet()
              .forEach(e -> validationOptions.put(e.getKey(), e.getValue()));
          ins.setValidationOptions(validationOptions.build());
        }

        if (input.customKeyedValues != null) {
          ImmutableMap.Builder<String, String> customKeyedValues = ImmutableMap.builder();
          input
              .customKeyedValues
              .entrySet()
              .forEach(e -> customKeyedValues.put(e.getKey(), e.getValue()));
          ins.setCustomKeyedValues(customKeyedValues.build());
        }

        try (BatchUpdate bu = updateFactory.create(projectState.getNameKey(), me, now)) {
          bu.setRepository(git, rw, oi);
          bu.setNotify(
              notifyResolver.resolve(
                  firstNonNull(input.notify, NotifyHandling.ALL), input.notifyDetails));
          bu.insertChange(ins);
          bu.execute();
        }
        List<ListChangesOption> opts = input.responseFormatOptions;
        if (opts == null) {
          opts = ImmutableList.of();
        }
        ChangeInfo changeInfo = jsonFactory.create(opts).format(ins.getChange());
        changeInfo.containsGitConflicts =
            (!c.getFilesWithGitConflicts().isEmpty() || hasGitConflicts) ? true : null;
        return changeInfo;
      } catch (InvalidMergeStrategyException | MergeWithConflictsNotSupportedException e) {
        throw new BadRequestException(e.getMessage());
      }
    }
  }

  private ChangeNotes getBaseChange(String baseChange)
      throws UnprocessableEntityException, PermissionBackendException {
    List<ChangeNotes> notes = changeFinder.find(baseChange);
    if (notes.size() != 1) {
      throw new UnprocessableEntityException("Base change not found: " + baseChange);
    }
    ChangeNotes change = Iterables.getOnlyElement(notes);
    try {
      permissionBackend.currentUser().change(change).check(ChangePermission.READ);
    } catch (AuthException e) {
      throw new UnprocessableEntityException("Read not permitted for " + baseChange, e);
    }

    return change;
  }

  @Nullable
  private ObjectId getParentCommit(
      Repository repo,
      RevWalk revWalk,
      String inputBranch,
      @Nullable Boolean newBranch,
      @Nullable PatchSet basePatchSet,
      @Nullable String baseCommit,
      @Nullable MergeInput mergeInput)
      throws BadRequestException,
          IOException,
          UnprocessableEntityException,
          ResourceConflictException {
    if (basePatchSet != null) {
      return basePatchSet.commitId();
    }

    Ref destRef = repo.getRefDatabase().exactRef(inputBranch);
    ObjectId parentCommit;
    if (baseCommit != null) {
      try {
        parentCommit = ObjectId.fromString(baseCommit);
      } catch (InvalidObjectIdException e) {
        throw new UnprocessableEntityException(
            String.format("Base %s doesn't represent a valid SHA-1", baseCommit), e);
      }

      RevCommit parentRevCommit;
      try {
        parentRevCommit = revWalk.parseCommit(parentCommit);
      } catch (MissingObjectException e) {
        throw new UnprocessableEntityException(
            String.format("Base %s doesn't exist", baseCommit), e);
      }

      if (destRef == null) {
        throw new BadRequestException("Destination branch does not exist");
      }
      RevCommit destRefRevCommit = revWalk.parseCommit(destRef.getObjectId());

      if (!revWalk.isMergedInto(parentRevCommit, destRefRevCommit)) {
        throw new BadRequestException(
            String.format("Commit %s doesn't exist on ref %s", baseCommit, inputBranch));
      }
    } else {
      if (destRef != null) {
        if (Boolean.TRUE.equals(newBranch)) {
          throw new ResourceConflictException(
              String.format("Branch %s already exists.", inputBranch));
        }
        parentCommit = destRef.getObjectId();
      } else {
        if (Boolean.TRUE.equals(newBranch)) {
          if (mergeInput != null) {
            throw new BadRequestException("Cannot create merge: destination branch does not exist");
          }
          parentCommit = null;
        } else {
          throw new BadRequestException(
              String.format("Destination branch does not exist %s", inputBranch));
        }
      }
    }

    return parentCommit;
  }

  private boolean shouldAddSignedOffByTag(
      ProjectState projectState, IdentifiedUser me, String commitMessage) {
    if (FooterLine.fromMessage(commitMessage).stream()
        .anyMatch(footer -> footer.matches(FooterKey.SIGNED_OFF_BY))) {
      return false;
    }
    if (projectState.is(BooleanProjectConfig.USE_SIGNED_OFF_BY)) {
      return true;
    }
    return Boolean.TRUE.equals(me.state().generalPreferences().signedOffBy);
  }

  private String getCommitMessage(ProjectState projectState, String subject, IdentifiedUser me) {
    // Add a Change-Id line if there isn't already one
    String commitMessage = subject;
    if (ChangeIdUtil.indexOfChangeId(commitMessage, "\n") == -1) {
      ObjectId id = CommitMessageUtil.generateChangeId();
      commitMessage = ChangeIdUtil.insertId(commitMessage, id);
    }

    if (this.shouldAddSignedOffByTag(projectState, me, commitMessage)) {
      commitMessage =
          Joiner.on("\n")
              .join(
                  commitMessage.trim(),
                  String.format(
                      "%s%s",
                      SIGNED_OFF_BY_TAG, me.state().account().getNameEmail(anonymousCowardName)));
    }

    return commitMessage;
  }

  private static CodeReviewCommit createEmptyCommit(
      ObjectInserter oi,
      CodeReviewRevWalk rw,
      PersonIdent authorIdent,
      PersonIdent committerIdent,
      @Nullable RevCommit mergeTip,
      String commitMessage)
      throws IOException {
    logger.atFine().log("Creating empty commit (mergeTip = %s)", mergeTip);
    ObjectId treeId = mergeTip == null ? emptyTreeId(oi) : mergeTip.getTree().getId();
    logger.atFine().log("Tree ID of empty commit: %s", treeId.name());
    List<RevCommit> parents = mergeTip == null ? ImmutableList.of() : ImmutableList.of(mergeTip);
    CodeReviewCommit commit =
        rw.parseCommit(
            CommitUtil.createCommitWithTree(
                oi, authorIdent, committerIdent, parents, commitMessage, treeId));
    commit.setNoConflictsForNonMergeCommit();
    return commit;
  }

  private static CodeReviewCommit createCommitWithSuppliedTree(
      Repository repo,
      ObjectInserter oi,
      ObjectReader or,
      CodeReviewRevWalk rw,
      RevCommit mergeTip,
      ChangeInput input,
      CommitTreeSupplier commitTreeSupplier,
      PersonIdent authorIdent,
      PersonIdent committerIdent,
      String commitMessage)
      throws IOException, RestApiException {
    if (mergeTip == null) {
      throw new BadRequestException("`CommitTreeSupplier` cannot be used on top of an empty tree.");
    }
    ObjectId treeId = commitTreeSupplier.get(repo, oi, or, input, mergeTip);
    return rw.parseCommit(
        CommitUtil.createCommitWithTree(
            oi, authorIdent, committerIdent, ImmutableList.of(mergeTip), commitMessage, treeId));
  }

  private static ObjectId emptyTreeId(ObjectInserter inserter) throws IOException {
    return inserter.insert(new TreeFormatter());
  }

  private CodeReviewCommit newMergeCommit(
      Repository repo,
      ObjectInserter oi,
      CodeReviewRevWalk rw,
      ProjectState projectState,
      RevCommit mergeTip,
      MergeInput merge,
      PersonIdent authorIdent,
      PersonIdent committerIdent,
      String commitMessage)
      throws RestApiException, IOException {
    logger.atFine().log(
        "Creating merge commit: source = %s, strategy = %s, allowConflicts = %s",
        merge.source, merge.strategy, merge.allowConflicts);

    if (Strings.isNullOrEmpty(merge.source)) {
      throw new BadRequestException("merge.source must be non-empty");
    }

    RevCommit sourceCommit = MergeUtil.resolveCommit(repo, rw, merge.source);
    if (merge.sourceBranch != null) {
      Ref ref = repo.findRef(merge.sourceBranch);
      logger.atFine().log("checking visibility for branch %s", merge.sourceBranch);
      if (ref == null || !commits.canRead(projectState, repo, sourceCommit, ref)) {
        throw new BadRequestException("do not have read permission for: " + merge.source);
      }
    } else if (!commits.canRead(projectState, repo, sourceCommit)) {
      throw new BadRequestException("do not have read permission for: " + merge.source);
    }

    MergeUtil mergeUtil = mergeUtilFactory.create(projectState);
    // default merge strategy from project settings
    String mergeStrategy =
        firstNonNull(Strings.emptyToNull(merge.strategy), mergeUtil.mergeStrategyName());
    logger.atFine().log("merge strategy = %s", mergeStrategy);

    try {
      CodeReviewCommit mergeCommit =
          MergeUtil.createMergeCommit(
              oi,
              repo.getConfig(),
              mergeTip,
              sourceCommit,
              mergeStrategy,
              merge.allowConflicts,
              authorIdent,
              committerIdent,
              commitMessage,
              rw,
              this.useDiff3);
      logger.atFine().log("tree ID of merge commit: %s", mergeCommit.getTree().getId().name());
      return mergeCommit;
    } catch (NoMergeBaseException e) {
      throw new ResourceConflictException(
          String.format("Cannot create merge commit: %s", e.getMessage()), e);
    }
  }

  private static String messageForNewChange(PatchSet.Id patchSetId, CodeReviewCommit commit) {
    StringBuilder stringBuilder =
        new StringBuilder(String.format("Uploaded patch set %s.", patchSetId.get()));

    if (!commit.getFilesWithGitConflicts().isEmpty()) {
      stringBuilder.append("\n\nThe following files contain Git conflicts:\n");
      commit.getFilesWithGitConflicts().stream()
          .sorted()
          .forEach(filePath -> stringBuilder.append("* ").append(filePath).append("\n"));
    }

    return stringBuilder.toString();
  }
}
