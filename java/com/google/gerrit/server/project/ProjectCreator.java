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

import static com.google.gerrit.server.project.ProjectCache.illegalState;

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.AccessSection;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.BooleanProjectConfig;
import com.google.gerrit.entities.GroupDescription;
import com.google.gerrit.entities.GroupReference;
import com.google.gerrit.entities.Permission;
import com.google.gerrit.entities.PermissionRule;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.git.LockFailureException;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.RepositoryConfig;
import com.google.gerrit.server.extensions.events.AbstractNoNotifyEvent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.RepositoryCaseMismatchException;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectInserter;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.eclipse.jgit.lib.Repository;
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
  private final ProjectCache projectCache;
  private final GroupBackend groupBackend;
  private final MetaDataUpdate.User metaDataUpdateFactory;
  private final GitReferenceUpdated referenceUpdated;
  private final RepositoryConfig repositoryCfg;
  private final Provider<PersonIdent> serverIdent;
  private final Provider<IdentifiedUser> identifiedUser;
  private final ProjectConfig.Factory projectConfigFactory;

  @Inject
  ProjectCreator(
      GitRepositoryManager repoManager,
      PluginSetContext<NewProjectCreatedListener> createdListeners,
      ProjectCache projectCache,
      GroupBackend groupBackend,
      MetaDataUpdate.User metaDataUpdateFactory,
      GitReferenceUpdated referenceUpdated,
      RepositoryConfig repositoryCfg,
      @GerritPersonIdent Provider<PersonIdent> serverIdent,
      Provider<IdentifiedUser> identifiedUser,
      ProjectConfig.Factory projectConfigFactory) {
    this.repoManager = repoManager;
    this.createdListeners = createdListeners;
    this.projectCache = projectCache;
    this.groupBackend = groupBackend;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.referenceUpdated = referenceUpdated;
    this.repositoryCfg = repositoryCfg;
    this.serverIdent = serverIdent;
    this.identifiedUser = identifiedUser;
    this.projectConfigFactory = projectConfigFactory;
  }

  public ProjectState createProject(CreateProjectArgs args)
      throws BadRequestException, ResourceConflictException, IOException, ConfigInvalidException {
    final Project.NameKey nameKey = args.getProject();
    try {
      final String head = args.permissionsOnly ? RefNames.REFS_CONFIG : args.branch.get(0);
      try (Repository repo = repoManager.openRepository(nameKey)) {
        if (repo.getObjectDatabase().exists()) {
          throw new ResourceConflictException("project \"" + nameKey + "\" exists");
        }
      } catch (RepositoryNotFoundException e) {
        // It does not exist, safe to ignore.
      }
      try (Repository repo = repoManager.createRepository(nameKey)) {
        RefUpdate u = repo.updateRef(Constants.HEAD);
        u.disableRefLog();
        u.link(head);

        createProjectConfig(args);

        if (!args.permissionsOnly && args.createEmptyCommit) {
          createEmptyCommits(repo, nameKey, args.branch);
        }

        fire(nameKey, head);

        return projectCache.get(nameKey).orElseThrow(illegalState(nameKey));
      }
    } catch (RepositoryCaseMismatchException e) {
      throw new ResourceConflictException(
          "Cannot create "
              + nameKey.get()
              + " because the name is already occupied by another project."
              + " The other project has the same name, only spelled in a"
              + " different case.",
          e);
    } catch (RepositoryNotFoundException badName) {
      throw new BadRequestException("invalid project name: " + nameKey, badName);
    } catch (ConfigInvalidException e) {
      String msg = "Cannot create " + nameKey;
      logger.atSevere().withCause(e).log(msg);
      throw e;
    }
  }

  private void createProjectConfig(CreateProjectArgs args)
      throws IOException, ConfigInvalidException {
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

      md.setMessage("Created project\n");
      config.commit(md);
      md.getRepository().setGitwebDescription(args.projectDescription);
    }
    projectCache.onCreateProject(args.getProject());
  }

  private void createEmptyCommits(Repository repo, Project.NameKey project, List<String> refs)
      throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      CommitBuilder cb = new CommitBuilder();
      cb.setTreeId(oi.insert(Constants.OBJ_TREE, new byte[] {}));
      cb.setAuthor(metaDataUpdateFactory.getUserPersonIdent());
      cb.setCommitter(serverIdent.get());
      cb.setMessage("Initial empty repository\n");

      ObjectId id = oi.insert(cb);
      oi.flush();

      for (String ref : refs) {
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

  private void fire(Project.NameKey name, String head) {
    if (createdListeners.isEmpty()) {
      return;
    }

    ProjectCreator.Event event = new ProjectCreator.Event(name, head);
    createdListeners.runEach(l -> l.onNewProjectCreated(event));
  }

  static class Event extends AbstractNoNotifyEvent implements NewProjectCreatedListener.Event {
    private final Project.NameKey name;
    private final String head;

    Event(Project.NameKey name, String head) {
      this.name = name;
      this.head = head;
    }

    @Override
    public String getProjectName() {
      return name.get();
    }

    @Override
    public String getHeadName() {
      return head;
    }
  }
}
