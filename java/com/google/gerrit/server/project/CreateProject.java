// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import com.google.gerrit.common.ProjectUtil;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ProjectInfo;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.TopLevelResource;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.ProjectOwnerGroupsProvider;
import com.google.gerrit.server.config.RepositoryConfig;
import com.google.gerrit.server.extensions.events.AbstractNoNotifyEvent;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.RepositoryCaseMismatchException;
import com.google.gerrit.server.group.GroupsCollection;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.validators.ProjectCreationValidationListener;
import com.google.gerrit.server.validators.ValidationException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiresCapability(GlobalCapability.CREATE_PROJECT)
public class CreateProject implements RestModifyView<TopLevelResource, ProjectInput> {
  public interface Factory {
    CreateProject create(String name);
  }

  private static final Logger log = LoggerFactory.getLogger(CreateProject.class);

  private final Provider<ProjectsCollection> projectsCollection;
  private final Provider<GroupsCollection> groupsCollection;
  private final DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners;
  private final ProjectJson json;
  private final GitRepositoryManager repoManager;
  private final DynamicSet<NewProjectCreatedListener> createdListeners;
  private final ProjectCache projectCache;
  private final GroupBackend groupBackend;
  private final ProjectOwnerGroupsProvider.Factory projectOwnerGroups;
  private final MetaDataUpdate.User metaDataUpdateFactory;
  private final GitReferenceUpdated referenceUpdated;
  private final RepositoryConfig repositoryCfg;
  private final PersonIdent serverIdent;
  private final Provider<IdentifiedUser> identifiedUser;
  private final Provider<PutConfig> putConfig;
  private final AllProjectsName allProjects;
  private final String name;

  @Inject
  CreateProject(
      Provider<ProjectsCollection> projectsCollection,
      Provider<GroupsCollection> groupsCollection,
      ProjectJson json,
      DynamicSet<ProjectCreationValidationListener> projectCreationValidationListeners,
      GitRepositoryManager repoManager,
      DynamicSet<NewProjectCreatedListener> createdListeners,
      ProjectCache projectCache,
      GroupBackend groupBackend,
      ProjectOwnerGroupsProvider.Factory projectOwnerGroups,
      MetaDataUpdate.User metaDataUpdateFactory,
      GitReferenceUpdated referenceUpdated,
      RepositoryConfig repositoryCfg,
      @GerritPersonIdent PersonIdent serverIdent,
      Provider<IdentifiedUser> identifiedUser,
      Provider<PutConfig> putConfig,
      AllProjectsName allProjects,
      @Assisted String name) {
    this.projectsCollection = projectsCollection;
    this.groupsCollection = groupsCollection;
    this.projectCreationValidationListeners = projectCreationValidationListeners;
    this.json = json;
    this.repoManager = repoManager;
    this.createdListeners = createdListeners;
    this.projectCache = projectCache;
    this.groupBackend = groupBackend;
    this.projectOwnerGroups = projectOwnerGroups;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.referenceUpdated = referenceUpdated;
    this.repositoryCfg = repositoryCfg;
    this.serverIdent = serverIdent;
    this.identifiedUser = identifiedUser;
    this.putConfig = putConfig;
    this.allProjects = allProjects;
    this.name = name;
  }

  @Override
  public Response<ProjectInfo> apply(TopLevelResource resource, ProjectInput input)
      throws BadRequestException, UnprocessableEntityException, ResourceConflictException,
          ResourceNotFoundException, IOException, ConfigInvalidException,
          PermissionBackendException {
    if (input == null) {
      input = new ProjectInput();
    }
    if (input.name != null && !name.equals(input.name)) {
      throw new BadRequestException("name must match URL");
    }

    CreateProjectArgs args = new CreateProjectArgs();
    args.setProjectName(ProjectUtil.stripGitSuffix(name));

    String parentName =
        MoreObjects.firstNonNull(Strings.emptyToNull(input.parent), allProjects.get());
    args.newParent = projectsCollection.get().parse(parentName, false).getNameKey();
    args.createEmptyCommit = input.createEmptyCommit;
    args.permissionsOnly = input.permissionsOnly;
    args.projectDescription = Strings.emptyToNull(input.description);
    args.submitType = input.submitType;
    args.branch = normalizeBranchNames(input.branches);
    if (input.owners == null || input.owners.isEmpty()) {
      args.ownerIds = new ArrayList<>(projectOwnerGroups.create(args.getProject()).get());
    } else {
      args.ownerIds = Lists.newArrayListWithCapacity(input.owners.size());
      for (String owner : input.owners) {
        args.ownerIds.add(groupsCollection.get().parse(owner).getGroupUUID());
      }
    }
    args.contributorAgreements =
        MoreObjects.firstNonNull(input.useContributorAgreements, InheritableBoolean.INHERIT);
    args.signedOffBy = MoreObjects.firstNonNull(input.useSignedOffBy, InheritableBoolean.INHERIT);
    args.contentMerge =
        input.submitType == SubmitType.FAST_FORWARD_ONLY
            ? InheritableBoolean.FALSE
            : MoreObjects.firstNonNull(input.useContentMerge, InheritableBoolean.INHERIT);
    args.newChangeForAllNotInTarget =
        MoreObjects.firstNonNull(
            input.createNewChangeForAllNotInTarget, InheritableBoolean.INHERIT);
    args.changeIdRequired =
        MoreObjects.firstNonNull(input.requireChangeId, InheritableBoolean.INHERIT);
    try {
      args.maxObjectSizeLimit = ProjectConfig.validMaxObjectSizeLimit(input.maxObjectSizeLimit);
    } catch (ConfigInvalidException e) {
      throw new BadRequestException(e.getMessage());
    }

    for (ProjectCreationValidationListener l : projectCreationValidationListeners) {
      try {
        l.validateNewProject(args);
      } catch (ValidationException e) {
        throw new ResourceConflictException(e.getMessage(), e);
      }
    }

    ProjectState projectState = createProject(args);
    if (input.pluginConfigValues != null) {
      ConfigInput in = new ConfigInput();
      in.pluginConfigValues = input.pluginConfigValues;
      putConfig.get().apply(projectState, in);
    }

    return Response.created(json.format(projectState));
  }

  private ProjectState createProject(CreateProjectArgs args)
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

        return projectCache.get(nameKey);
      }
    } catch (RepositoryCaseMismatchException e) {
      throw new ResourceConflictException(
          "Cannot create "
              + nameKey.get()
              + " because the name is already occupied by another project."
              + " The other project has the same name, only spelled in a"
              + " different case.");
    } catch (RepositoryNotFoundException badName) {
      throw new BadRequestException("invalid project name: " + nameKey);
    } catch (ConfigInvalidException e) {
      String msg = "Cannot create " + nameKey;
      log.error(msg, e);
      throw e;
    }
  }

  private void createProjectConfig(CreateProjectArgs args)
      throws IOException, ConfigInvalidException {
    try (MetaDataUpdate md = metaDataUpdateFactory.create(args.getProject())) {
      ProjectConfig config = ProjectConfig.read(md);

      Project newProject = config.getProject();
      newProject.setDescription(args.projectDescription);
      newProject.setSubmitType(
          MoreObjects.firstNonNull(
              args.submitType, repositoryCfg.getDefaultSubmitType(args.getProject())));
      newProject.setUseContributorAgreements(args.contributorAgreements);
      newProject.setUseSignedOffBy(args.signedOffBy);
      newProject.setUseContentMerge(args.contentMerge);
      newProject.setCreateNewChangeForAllNotInTarget(args.newChangeForAllNotInTarget);
      newProject.setRequireChangeID(args.changeIdRequired);
      newProject.setMaxObjectSizeLimit(args.maxObjectSizeLimit);
      if (args.newParent != null) {
        newProject.setParentName(args.newParent);
      }

      if (!args.ownerIds.isEmpty()) {
        AccessSection all = config.getAccessSection(AccessSection.ALL, true);
        for (AccountGroup.UUID ownerId : args.ownerIds) {
          GroupDescription.Basic g = groupBackend.get(ownerId);
          if (g != null) {
            GroupReference group = config.resolve(GroupReference.forGroup(g));
            all.getPermission(Permission.OWNER, true).add(new PermissionRule(group));
          }
        }
      }

      md.setMessage("Created project\n");
      config.commit(md);
      md.getRepository().setGitwebDescription(args.projectDescription);
    }
    projectCache.onCreateProject(args.getProject());
  }

  private List<String> normalizeBranchNames(List<String> branches) throws BadRequestException {
    if (branches == null || branches.isEmpty()) {
      return Collections.singletonList(Constants.R_HEADS + Constants.MASTER);
    }

    List<String> normalizedBranches = new ArrayList<>();
    for (String branch : branches) {
      while (branch.startsWith("/")) {
        branch = branch.substring(1);
      }
      branch = RefNames.fullName(branch);
      if (!Repository.isValidRefName(branch)) {
        throw new BadRequestException(String.format("Branch \"%s\" is not a valid name.", branch));
      }
      if (!normalizedBranches.contains(branch)) {
        normalizedBranches.add(branch);
      }
    }
    return normalizedBranches;
  }

  private void createEmptyCommits(Repository repo, Project.NameKey project, List<String> refs)
      throws IOException {
    try (ObjectInserter oi = repo.newObjectInserter()) {
      CommitBuilder cb = new CommitBuilder();
      cb.setTreeId(oi.insert(Constants.OBJ_TREE, new byte[] {}));
      cb.setAuthor(metaDataUpdateFactory.getUserPersonIdent());
      cb.setCommitter(serverIdent);
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
                project, ru, ReceiveCommand.Type.CREATE, identifiedUser.get().getAccount());
            break;
          case FAST_FORWARD:
          case FORCED:
          case IO_FAILURE:
          case LOCK_FAILURE:
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
      log.error("Cannot create empty commit for " + project.get(), e);
      throw e;
    }
  }

  private void fire(Project.NameKey name, String head) {
    if (!createdListeners.iterator().hasNext()) {
      return;
    }

    Event event = new Event(name, head);
    for (NewProjectCreatedListener l : createdListeners) {
      try {
        l.onNewProjectCreated(event);
      } catch (RuntimeException e) {
        log.warn("Failure in NewProjectCreatedListener", e);
      }
    }
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
