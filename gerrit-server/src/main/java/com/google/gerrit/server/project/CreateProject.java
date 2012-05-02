// Copyright (C) 2011 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.server.project;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.errors.ProjectCreationFailedException;
import com.google.gerrit.extensions.events.NewProjectCreatedListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.ProjectOwnerGroups;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.RepositoryCaseMismatchException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Set;


/** Common class that holds the code to create projects */
public class CreateProject {
  private static final Logger log = LoggerFactory
      .getLogger(CreateProject.class);

  public interface Factory {
    CreateProject create(CreateProjectArgs createProjectArgs);
  }

  private final Set<AccountGroup.UUID> projectOwnerGroups;
  private final IdentifiedUser currentUser;
  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated referenceUpdated;
  private final DynamicSet<NewProjectCreatedListener> createdListener;
  private final PersonIdent serverIdent;
  private CreateProjectArgs createProjectArgs;
  private ProjectCache projectCache;
  private GroupBackend groupBackend;
  private MetaDataUpdate.User metaDataUpdateFactory;

  @Inject
  CreateProject(@ProjectOwnerGroups Set<AccountGroup.UUID> pOwnerGroups,
      IdentifiedUser identifiedUser, GitRepositoryManager gitRepoManager,
      GitReferenceUpdated referenceUpdated,
      DynamicSet<NewProjectCreatedListener> createdListener,
      ReviewDb db,
      @GerritPersonIdent PersonIdent personIdent, GroupBackend groupBackend,
      MetaDataUpdate.User metaDataUpdateFactory,
      @Assisted CreateProjectArgs createPArgs, ProjectCache pCache) {
    this.projectOwnerGroups = pOwnerGroups;
    this.currentUser = identifiedUser;
    this.repoManager = gitRepoManager;
    this.referenceUpdated = referenceUpdated;
    this.createdListener = createdListener;
    this.serverIdent = personIdent;
    this.createProjectArgs = createPArgs;
    this.projectCache = pCache;
    this.groupBackend = groupBackend;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
  }

  public void createProject() throws ProjectCreationFailedException {
    validateParameters();
    final Project.NameKey nameKey = createProjectArgs.getProject();
    try {
      final String head =
          createProjectArgs.permissionsOnly ? GitRepositoryManager.REF_CONFIG
              : createProjectArgs.branch;
      final Repository repo = repoManager.createRepository(nameKey);
      try {
        NewProjectCreatedListener.Event event = new NewProjectCreatedListener.Event() {
          @Override
          public String getProjectName() {
            return nameKey.get();
          }

          @Override
          public String getHeadName() {
            return head;
          }
        };
        for (NewProjectCreatedListener l : createdListener) {
          l.onNewProjectCreated(event);
        }

        final RefUpdate u = repo.updateRef(Constants.HEAD);
        u.disableRefLog();
        u.link(head);

        createProjectConfig();

        if (!createProjectArgs.permissionsOnly
            && createProjectArgs.createEmptyCommit) {
          createEmptyCommit(repo, nameKey, createProjectArgs.branch);
        }
      } finally {
        repo.close();
      }
    } catch (RepositoryCaseMismatchException e) {
      throw new ProjectCreationFailedException("Cannot create " + nameKey.get()
          + " because the name is already occupied by another project."
          + " The other project has the same name, only spelled in a"
          + " different case.", e);
    } catch (RepositoryNotFoundException badName) {
      throw new ProjectCreationFailedException("Cannot create " + nameKey, badName);
    } catch (IllegalStateException err) {
      try {
        final Repository repo = repoManager.openRepository(nameKey);
        try {
          if (repo.getObjectDatabase().exists()) {
            throw new ProjectCreationFailedException("project \"" + nameKey + "\" exists");
          }
        } finally {
          repo.close();
        }
      } catch (RepositoryNotFoundException doesNotExist) {
        final String msg = "Cannot create " + nameKey;
        log.error(msg, err);
        throw new ProjectCreationFailedException(msg, err);
      }
    } catch (Exception e) {
      final String msg = "Cannot create " + nameKey;
      log.error(msg, e);
      throw new ProjectCreationFailedException(msg, e);
    }
  }

  private void createProjectConfig() throws IOException, ConfigInvalidException {
    final MetaDataUpdate md =
        metaDataUpdateFactory.create(createProjectArgs.getProject());
    try {
      final ProjectConfig config = ProjectConfig.read(md);
      config.load(md);

      Project newProject = config.getProject();
      newProject.setDescription(createProjectArgs.projectDescription);
      newProject.setSubmitType(createProjectArgs.submitType);
      newProject
          .setUseContributorAgreements(createProjectArgs.contributorAgreements);
      newProject.setUseSignedOffBy(createProjectArgs.signedOffBy);
      newProject.setUseContentMerge(createProjectArgs.contentMerge);
      newProject.setRequireChangeID(createProjectArgs.changeIdRequired);
      if (createProjectArgs.newParent != null) {
        newProject.setParentName(createProjectArgs.newParent.getProject()
            .getNameKey());
      }

      if (!createProjectArgs.ownerIds.isEmpty()) {
        final AccessSection all =
            config.getAccessSection(AccessSection.ALL, true);
        for (AccountGroup.UUID ownerId : createProjectArgs.ownerIds) {
          GroupDescription.Basic g = groupBackend.get(ownerId);
          if (g != null) {
            GroupReference group = config.resolve(GroupReference.forGroup(g));
            all.getPermission(Permission.OWNER, true).add(
                new PermissionRule(group));
          }
        }
      }

      md.setMessage("Created project\n");
      if (!config.commit(md)) {
        throw new IOException("Cannot create "
            + createProjectArgs.getProjectName());
      }
    } finally {
      md.close();
    }
    projectCache.onCreateProject(createProjectArgs.getProject());
    repoManager.setProjectDescription(createProjectArgs.getProject(),
        createProjectArgs.projectDescription);
    referenceUpdated.fire(createProjectArgs.getProject(),
        GitRepositoryManager.REF_CONFIG);
  }

  private void validateParameters() throws ProjectCreationFailedException {
    if (createProjectArgs.getProjectName() == null
        || createProjectArgs.getProjectName().isEmpty()) {
      throw new ProjectCreationFailedException("Project name is required");
    }

    if (createProjectArgs.getProjectName().endsWith(Constants.DOT_GIT_EXT)) {
      createProjectArgs.setProjectName(createProjectArgs.getProjectName()
          .substring(
              0,
              createProjectArgs.getProjectName().length()
                  - Constants.DOT_GIT_EXT.length()));
    }

    if (!currentUser.getCapabilities().canCreateProject()) {
      throw new ProjectCreationFailedException(String.format(
          "%s does not have \"Create Project\" capability.",
          currentUser.getUserName()));
    }

    if (createProjectArgs.ownerIds == null
        || createProjectArgs.ownerIds.isEmpty()) {
      createProjectArgs.ownerIds =
          new ArrayList<AccountGroup.UUID>(projectOwnerGroups);
    }

    while (createProjectArgs.branch.startsWith("/")) {
      createProjectArgs.branch = createProjectArgs.branch.substring(1);
    }
    if (!createProjectArgs.branch.startsWith(Constants.R_HEADS)) {
      createProjectArgs.branch = Constants.R_HEADS + createProjectArgs.branch;
    }
    if (!Repository.isValidRefName(createProjectArgs.branch)) {
      throw new ProjectCreationFailedException(String.format(
          "Branch \"%s\" is not a valid name.", createProjectArgs.branch));
    }
  }

  private void createEmptyCommit(final Repository repo,
      final Project.NameKey project, final String ref) throws IOException {
    ObjectInserter oi = repo.newObjectInserter();
    try {
      CommitBuilder cb = new CommitBuilder();
      cb.setTreeId(oi.insert(Constants.OBJ_TREE, new byte[] {}));
      cb.setAuthor(metaDataUpdateFactory.getUserPersonIdent());
      cb.setCommitter(serverIdent);
      cb.setMessage("Initial empty repository\n");

      ObjectId id = oi.insert(cb);
      oi.flush();

      RefUpdate ru = repo.updateRef(Constants.HEAD);
      ru.setNewObjectId(id);
      final Result result = ru.update();
      switch (result) {
        case NEW:
          referenceUpdated.fire(project, ref);
          break;
        default: {
          throw new IOException(result.name());
        }
      }
    } catch (IOException e) {
      log.error(
          "Cannot create empty commit for "
              + createProjectArgs.getProjectName(), e);
      throw e;
    } finally {
      oi.release();
    }
  }
}
