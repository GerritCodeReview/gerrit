// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.project;

import static com.google.common.collect.ImmutableSet.toImmutableSet;
import static com.google.gerrit.server.project.ProjectCache.illegalState;
import static com.google.gerrit.server.update.context.RefUpdateContext.RefUpdateType.INIT_REPO;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.base.Throwables;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.common.flogger.FluentLogger;
import com.google.errorprone.annotations.CanIgnoreReturnValue;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.CachedProjectConfig;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.server.ExceptionHook;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.GerritInstanceId;
import com.google.gerrit.server.config.RepositoryConfig;
import com.google.gerrit.server.extensions.events.AbstractNoNotifyEvent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.GitRepositoryManager.Status;
import com.google.gerrit.server.git.RepositoryExistsException;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.update.RetryHelper;
import com.google.gerrit.server.update.RetryableAction.ActionType;
import com.google.gerrit.server.update.context.RefUpdateContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.transport.ReceiveCommand;

/**
 * Business logic for creating projects.
 *
 * <p>This creates the repository, the underlying configuration in {@code refs/meta/config} and
 * initializes a first commit if necessary.
 */
public class ProjectCreator {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;
  private final PluginSetContext<NewProjectCreatedListener> createdListeners;
  private final PluginSetContext<ExceptionHook> exceptionHooks;
  private final ProjectCache projectCache;
  private final GroupBackend groupBackend;
  private final MetaDataUpdate.User metaDataUpdateFactory;
  private final GitReferenceUpdated referenceUpdated;
  private final RepositoryConfig repositoryCfg;
  private final RetryHelper retryHelper;
  private final Provider<PersonIdent> serverIdent;
  private final Provider<IdentifiedUser> identifiedUser;
  private final ProjectConfig.Factory projectConfigFactory;
  private final String gerritInstanceId;

  @Inject
  ProjectCreator(
      GitRepositoryManager repoManager,
      PluginSetContext<NewProjectCreatedListener> createdListeners,
      PluginSetContext<ExceptionHook> exceptionHooks,
      ProjectCache projectCache,
      GroupBackend groupBackend,
      MetaDataUpdate.User metaDataUpdateFactory,
      GitReferenceUpdated referenceUpdated,
      RepositoryConfig repositoryCfg,
      RetryHelper retryHelper,
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      @Nullable @GerritInstanceId String gerritInstanceId,
      Provider<IdentifiedUser> identifiedUser,
      ProjectConfig.Factory projectConfigFactory) {
    this.repoManager = repoManager;
    this.createdListeners = createdListeners;
    this.exceptionHooks = exceptionHooks;
    this.projectCache = projectCache;
    this.groupBackend = groupBackend;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.referenceUpdated = referenceUpdated;
    this.repositoryCfg = repositoryCfg;
    this.retryHelper = retryHelper;
    this.serverIdent = serverIdent;
    this.gerritInstanceId = gerritInstanceId;
    this.identifiedUser = identifiedUser;
    this.projectConfigFactory = projectConfigFactory;
  }

  @CanIgnoreReturnValue
  public ProjectState createProject(CreateProjectArgs args)
      throws RestApiException, IOException, ConfigInvalidException {
    try {
      return retryHelper
          .action(
              ActionType.REPO_CREATION,
              "createProject",
              () -> {
                try (RefUpdateContext ctx = RefUpdateContext.open(INIT_REPO)) {
                  Project.NameKey nameKey = args.getProject();

                  if (args.initOnly) {
                    try (Repository repo = repoManager.openRepository(nameKey)) {
                      initProject(nameKey, repo, args);
                      return projectCache.get(nameKey).orElseThrow(illegalState(nameKey));
                    } catch (RepositoryNotFoundException notFound) {
                      throw new ResourceNotFoundException(
                          String.format("repository %s not found", nameKey), notFound);
                    }
                  }

                  try {
                    Status status = repoManager.getRepositoryStatus(nameKey);
                    if (!status.equals(Status.NON_EXISTENT)) {
                      throw new RepositoryExistsException(nameKey, "Repository status: " + status);
                    }
                    try (Repository repo = repoManager.createRepository(nameKey)) {
                      initProject(nameKey, repo, args);
                      return projectCache.get(nameKey).orElseThrow(illegalState(nameKey));
                    }
                  } catch (RepositoryExistsException e) {
                    throw new ResourceConflictException(
                        "Cannot create "
                            + nameKey.get()
                            + " because the name is already occupied by another project.",
                        e);
                  } catch (RepositoryNotFoundException badName) {
                    throw new BadRequestException("invalid project name: " + nameKey, badName);
                  } catch (RuntimeException e) {
                    if (exceptionHooks.stream()
                        .anyMatch(
                            exceptionHook ->
                                exceptionHook.tryProjectInitializationOnRepoCreationFailure(
                                    nameKey, args, e))) {
                      logger.atFine().withCause(e).log(
                          "try initializing project %s after repo creation failure", nameKey);
                      try (Repository repo = repoManager.openRepository(nameKey)) {
                        initProject(nameKey, repo, args);
                      } catch (
                          @SuppressWarnings("UnusedException")
                          RuntimeException e2) {
                        logger.atWarning().withCause(e).log(
                            "initializing project %s after repo creation failure has failed",
                            nameKey);
                        throw e;
                      }
                    }
                    throw e;
                  }
                }
              })
          .call();
    } catch (Exception e) {
      Throwables.throwIfUnchecked(e);
      Throwables.throwIfInstanceOf(e, IOException.class);
      Throwables.throwIfInstanceOf(e, ConfigInvalidException.class);
      Throwables.throwIfInstanceOf(e, RestApiException.class);
      throw new IllegalStateException(e);
    }
  }

  private void initProject(Project.NameKey nameKey, Repository repo, CreateProjectArgs args)
      throws IOException, ConfigInvalidException, ResourceConflictException {
    projectCache.evict(nameKey);
    createHead(repo, args);
    createProjectConfig(repo, args);

    if (!args.permissionsOnly && args.createEmptyCommit) {
      createEmptyCommits(repo, nameKey, args.branch);
    }
  }

  private void createHead(Repository repo, CreateProjectArgs args)
      throws IOException, ResourceConflictException {
    Ref head = repo.exactRef(Constants.HEAD);
    if (head != null) {
      if (head.getTarget().getName().equals(args.getHead())) {
        logger.atFine().log("Skip creation of HEAD because it already exists");
        return;
      }

      throw new ResourceConflictException("conflicting HEAD already exists");
    }

    RefUpdate u = repo.updateRef(Constants.HEAD);
    u.disableRefLog();
    u.link(args.getHead());
  }

  private void createProjectConfig(Repository repo, CreateProjectArgs args)
      throws IOException, ConfigInvalidException, ResourceConflictException {
    Optional<CachedProjectConfig> currentConfig =
        repo.exactRef("refs/meta/config") != null
            ? Optional.of(
                projectCache
                    .get(args.getProject())
                    .orElseThrow(illegalState(args.getProject()))
                    .getConfig())
            : Optional.empty();

    RevCommit configRevCommit = null;
    try (MetaDataUpdate md = metaDataUpdateFactory.create(args.getProject())) {
      ProjectConfig config = projectConfigFactory.read(md);

      config.updateProject(
          newProject -> {
            newProject.setDescription(Strings.nullToEmpty(args.projectDescription));
            newProject.setSubmitType(
                MoreObjects.firstNonNull(
                    args.submitType, repositoryCfg.getDefaultSubmitType(args.getProject())));
            newProject.setBooleanConfig(
                BooleanProjectConfig.USE_CONTRIBUTOR_AGREEMENTS, args.contributorAgreements);
            newProject.setBooleanConfig(BooleanProjectConfig.USE_SIGNED_OFF_BY, args.signedOffBy);
            newProject.setBooleanConfig(BooleanProjectConfig.USE_CONTENT_MERGE, args.contentMerge);
            newProject.setBooleanConfig(
                BooleanProjectConfig.CREATE_NEW_CHANGE_FOR_ALL_NOT_IN_TARGET,
                args.newChangeForAllNotInTarget);
            newProject.setBooleanConfig(
                BooleanProjectConfig.REQUIRE_CHANGE_ID, args.changeIdRequired);
            newProject.setBooleanConfig(
                BooleanProjectConfig.REJECT_EMPTY_COMMIT, args.rejectEmptyCommit);
            newProject.setMaxObjectSizeLimit(args.maxObjectSizeLimit);
            newProject.setBooleanConfig(
                BooleanProjectConfig.ENABLE_SIGNED_PUSH, args.enableSignedPush);
            newProject.setBooleanConfig(
                BooleanProjectConfig.REQUIRE_SIGNED_PUSH, args.requireSignedPush);
            if (args.newParent != null) {
              newProject.setParent(args.newParent);
            }
          });

      if (!args.ownerIds.isEmpty()) {
        config.upsertAccessSection(
            AccessSection.ALL,
            all -> {
              for (AccountGroup.UUID ownerId : args.ownerIds) {
                GroupDescription.Basic g = groupBackend.get(ownerId);
                if (g != null) {
                  GroupReference group = config.resolve(GroupReference.forGroup(g));
                  all.upsertPermission(Permission.OWNER).add(PermissionRule.builder(group));
                }
              }
            });
      }

      if (currentConfig.isPresent()) {
        if (currentConfig.get().equals(config.getCacheable())) {
          logger.atFine().log("Skip creation of project config because it already exists");
          return;
        }
        throw new ResourceConflictException("conflicting project config already exists");
      }

      configRevCommit = config.commit(md, false);
      md.getRepository().setGitwebDescription(args.projectDescription);
    } finally {
      if (configRevCommit != null) {
        fireEvents(args.getProject(), args.getHead(), configRevCommit);
      }
    }
    projectCache.onCreateProject(args.getProject());
  }

  private void createEmptyCommits(Repository repo, Project.NameKey project, List<String> refs)
      throws IOException, ResourceConflictException {
    List<Ref> existingRefs = repo.getRefDatabase().getRefsByPrefix(Constants.R_HEADS);
    if (!existingRefs.isEmpty()) {
      if (Sets.symmetricDifference(
              existingRefs.stream().map(Ref::getName).collect(toImmutableSet()),
              ImmutableSet.copyOf(refs))
          .isEmpty()) {
        logger.atFine().log("Skip creation of branches since they already exist");
        return;
      }
      throw new ResourceConflictException(String.format("conflicting branches already exists"));
    }

    try (ObjectInserter oi = repo.newObjectInserter()) {
      CommitBuilder cb = new CommitBuilder();
      cb.setTreeId(oi.insert(Constants.OBJ_TREE, new byte[] {}));
      cb.setAuthor(metaDataUpdateFactory.getUserPersonIdent());
      cb.setCommitter(serverIdent.get());
      cb.setMessage("Initial empty repository\n");

      ObjectId id = oi.insert(cb);
      oi.flush();

      for (String ref : refs) {
        if (repo.exactRef(ref) != null) {
          logger.atFine().log("Skip creation of branch %s because it already exists", ref);
          continue;
        }

        RefUpdate ru = repo.updateRef(ref);
        ru.setNewObjectId(id);
        Result result = ru.update();
        switch (result) {
          case NEW:
            referenceUpdated.fire(
                project, ru, ReceiveCommand.Type.CREATE, identifiedUser.get().state());
            break;
          case LOCK_FAILURE:
            throw new LockFailureException(String.format("Failed to create ref \"%s\"", ref), ru);
          case FAST_FORWARD:
          case FORCED:
          case IO_FAILURE:
          case NOT_ATTEMPTED:
          case NO_CHANGE:
          case REJECTED:
          case REJECTED_CURRENT_BRANCH:
          case RENAMED:
          case REJECTED_MISSING_OBJECT:
          case REJECTED_OTHER_REASON:
          default:
            {
              throw new IOException(
                  String.format("Failed to create ref \"%s\": %s", ref, result.name()));
            }
        }
      }
    } catch (IOException e) {
      logger.atSevere().withCause(e).log("Cannot create empty commit for %s", project.get());
      throw e;
    }
  }

  private void fireEvents(Project.NameKey name, String head, ObjectId configNewObjectId) {
    if (!createdListeners.isEmpty()) {
      ProjectCreator.Event event = new ProjectCreator.Event(name, head, gerritInstanceId);
      createdListeners.runEach(l -> l.onNewProjectCreated(event));
    }

    referenceUpdated.fire(
        name,
        RefNames.REFS_CONFIG,
        ObjectId.zeroId(),
        configNewObjectId,
        identifiedUser.get().state());
  }

  static class Event extends AbstractNoNotifyEvent implements NewProjectCreatedListener.Event {
    private final Project.NameKey name;
    private final String head;
    private final String gerritInstanceId;

    Event(Project.NameKey name, String head, @Nullable String gerritInstanceId) {
      this.name = name;
      this.head = head;
      this.gerritInstanceId = gerritInstanceId;
    }

    @Override
    public String getProjectName() {
      return name.get();
    }

    @Override
    public String getHeadName() {
      return head;
    }

    @Override
    public String getInstanceId() {
      return gerritInstanceId;
    }
  }
}
