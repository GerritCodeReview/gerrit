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
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.errors.ProjectCreationFailedException;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.ProjectOwnerGroups;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.ReplicationQueue;
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
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.RefUpdate.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

import com.google.gerrit.server.git.RepositoryCaseMismatchException;
import com.google.gerrit.server.project.ProjectCache;


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
  private final ReplicationQueue rq;
  private final PersonIdent serverIdent;
  private CreateProjectArgs createProjectArgs;
  private Project.NameKey nameKey;
  private ProjectCache projectCache;

  @Inject
  private GroupCache groupCache;

  @Inject
  private MetaDataUpdate.User metaDataUpdateFactory;

  @Inject
  CreateProject(@ProjectOwnerGroups Set<AccountGroup.UUID> pOwnerGroups,
      IdentifiedUser identifiedUser, GitRepositoryManager gitRepoManager,
      ReplicationQueue replicateq, ReviewDb db,
      @GerritPersonIdent PersonIdent personIdent,
      @Assisted CreateProjectArgs createPArgs, ProjectCache pCache) {
    this.projectOwnerGroups = pOwnerGroups;
    this.currentUser = identifiedUser;
    this.repoManager = gitRepoManager;
    this.rq = replicateq;
    this.serverIdent = personIdent;
    this.createProjectArgs = createPArgs;
    this.projectCache = pCache;
  }

  public void createProject() throws ProjectCreationFailedException {
    final StringBuilder err = validateParameters();

    if (err.length() == 0) {
      try {
        nameKey = new Project.NameKey(createProjectArgs.getProjectName());

        final String head =
            createProjectArgs.isPermissionsOnly()
                ? GitRepositoryManager.REF_CONFIG : createProjectArgs
                    .getBranch();
        final Repository repo = repoManager.createRepository(nameKey);
        try {
          rq.replicateNewProject(nameKey, head);

          final RefUpdate u = repo.updateRef(Constants.HEAD);
          u.disableRefLog();
          u.link(head);

          createProjectConfig();

          if (!createProjectArgs.isPermissionsOnly()
              && createProjectArgs.isCreateEmptyCommit()) {
            createEmptyCommit(repo, nameKey, createProjectArgs.getBranch());
          }
        } finally {
          repo.close();
        }
      } catch (RepositoryCaseMismatchException ee) {
        throw new ProjectCreationFailedException("Cannot create \"" + nameKey
            + "\" because the name is already occupied by another project. "
            + "The other project has the same name, only spelled in "
            + "a  different case.", ee);
      } catch (RepositoryNotFoundException e) {
        throw new ProjectCreationFailedException("Repository not exists", e);
      } catch (IOException e) {
        throw new ProjectCreationFailedException("IO error", e);
      } catch (ConfigInvalidException e) {
        throw new ProjectCreationFailedException("Configuration error", e);
      } catch (IllegalStateException e) {
        try {
          Repository repo = repoManager.openRepository(nameKey);
          try {
            if (repo.getObjectDatabase().exists()) {
              throw new ProjectCreationFailedException("Project \"" + nameKey
                  + "\" exists", e);
            }
          } finally {
            repo.close();
          }
        } catch (RepositoryNotFoundException er) {
          throw new ProjectCreationFailedException("Cannot create \"" + nameKey
              + "\"", er);
        }
      }
    } else {
      throw new ProjectCreationFailedException("validation error:" + err);
    }
  }

  private void createProjectConfig() throws IOException, ConfigInvalidException {
    final MetaDataUpdate md = metaDataUpdateFactory.create(nameKey);
    try {
      final ProjectConfig config = ProjectConfig.read(md);
      config.load(md);

      Project newProject = config.getProject();
      newProject.setDescription(createProjectArgs.getProjectDescription());
      newProject.setSubmitType(createProjectArgs.getSubmitType());
      newProject.setUseContributorAgreements(createProjectArgs
          .isContributorAgreements());
      newProject.setUseSignedOffBy(createProjectArgs.isSignedOffBy());
      newProject.setUseContentMerge(createProjectArgs.isContentMerge());
      newProject.setRequireChangeID(createProjectArgs.isChangeIdRequired());
      if (createProjectArgs.getNewParent() != null) {
        newProject.setParentName(createProjectArgs.getNewParent().getProject()
            .getName());
      }

      if (!createProjectArgs.getOwnerIds().isEmpty()) {
        final AccessSection all =
            config.getAccessSection(AccessSection.ALL, true);
        for (AccountGroup.UUID ownerId : createProjectArgs.getOwnerIds()) {
          AccountGroup accountGroup = groupCache.get(ownerId);
          GroupReference group = config.resolve(accountGroup);
          all.getPermission(Permission.OWNER, true).add(
              new PermissionRule(group));
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
    projectCache.onCreateProject(nameKey);
    repoManager.setProjectDescription(nameKey,
        createProjectArgs.getProjectDescription());
    rq.scheduleUpdate(nameKey, GitRepositoryManager.REF_CONFIG);
  }

  private StringBuilder validateParameters() {
    final StringBuilder err = new StringBuilder();

    if (createProjectArgs.getProjectName() == null
        || createProjectArgs.getProjectName().isEmpty()) {
      err.append("fatal: Argument NAME is required");
    }

    if (createProjectArgs.getProjectName().contains(" ")) {
      err.append("project name should not contain whitespaces");
    }

    if (createProjectArgs.getProjectName().endsWith(Constants.DOT_GIT_EXT)) {
      createProjectArgs.setProjectName(createProjectArgs.getProjectName()
          .substring(
              0,
              createProjectArgs.getProjectName().length()
                  - Constants.DOT_GIT_EXT.length()));
    }

    if (createProjectArgs.getNewParent() != null
        && !createProjectArgs.getNewParent().getProject().getName().isEmpty()) {
      final ProjectState state =
          projectCache.get(new Project.NameKey(createProjectArgs.getNewParent()
              .getProject().getName()));

      if (state == null) {
        err.append("fatal: '" + createProjectArgs.getNewParent()
            + "': not a Gerrit project");
        return err;
      }
    }

    if (!currentUser.getCapabilities().canCreateProject()) {
      err.append("fatal: Not permitted to create "
          + createProjectArgs.getProjectName());
      return err;
    }

    if (createProjectArgs.getOwnerIds() != null
        && !createProjectArgs.getOwnerIds().isEmpty()) {
      createProjectArgs.setOwnerIds(new ArrayList<AccountGroup.UUID>(
          new HashSet<AccountGroup.UUID>(createProjectArgs.getOwnerIds())));;
    } else {
      createProjectArgs.setOwnerIds(new ArrayList<AccountGroup.UUID>(
          projectOwnerGroups));
    }

    while (createProjectArgs.getBranch().startsWith("/")) {
      createProjectArgs.setBranch(createProjectArgs.getBranch().substring(1));
    }
    if (!createProjectArgs.getBranch().startsWith(Constants.R_HEADS)) {
      createProjectArgs.setBranch(Constants.R_HEADS
          + createProjectArgs.getBranch());
    }
    if (!Repository.isValidRefName(createProjectArgs.getBranch())) {
      err.append("branch \"" + createProjectArgs.getBranch()
          + "\" is not a valid name");
    }

    return err;
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
          rq.scheduleUpdate(project, ref);
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
