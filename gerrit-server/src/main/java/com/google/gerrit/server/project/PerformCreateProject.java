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

import com.google.gerrit.common.ProjectUtil;
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
import java.util.Collections;
import java.util.List;
import java.util.Set;


/** Common class that holds the code to create projects */
public class PerformCreateProject {
  private static final Logger log = LoggerFactory
      .getLogger(PerformCreateProject.class);

  public interface Factory {
    PerformCreateProject create(CreateProjectArgs createProjectArgs);
  }

  private final Set<AccountGroup.UUID> projectOwnerGroups;
  private final IdentifiedUser currentUser;
  private final GitRepositoryManager repoManager;
  private final GitReferenceUpdated referenceUpdated;
  private final DynamicSet<NewProjectCreatedListener> createdListener;
  private final PersonIdent serverIdent;
  private final CreateProjectArgs createProjectArgs;
  private final ProjectCache projectCache;
  private final GroupBackend groupBackend;
  private final MetaDataUpdate.User metaDataUpdateFactory;

  @Inject
  PerformCreateProject(@ProjectOwnerGroups Set<AccountGroup.UUID> pOwnerGroups,
      IdentifiedUser identifiedUser, GitRepositoryManager gitRepoManager,
      GitReferenceUpdated referenceUpdated,
      DynamicSet<NewProjectCreatedListener> createdListener,
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

  public Project createProject() throws ProjectCreationFailedException {
    validateParameters();
    final Project.NameKey nameKey = createProjectArgs.getProject();
    try {
      final String head =
          createProjectArgs.permissionsOnly ? GitRepositoryManager.REF_CONFIG
              : createProjectArgs.branch.get(0);
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
          createEmptyCommits(repo, nameKey, createProjectArgs.branch);
        }

        return projectCache.get(nameKey).getProject();
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
          throw err;
        } finally {
          repo.close();
        }
      } catch (IOException ioErr) {
        final String msg = "Cannot create " + nameKey;
        log.error(msg, err);
        throw new ProjectCreationFailedException(msg, ioErr);
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
      config.commit(md);
    } finally {
      md.close();
    }
    projectCache.onCreateProject(createProjectArgs.getProject());
    repoManager.setProjectDescription(createProjectArgs.getProject(),
        createProjectArgs.projectDescription);
  }

  private void validateParameters() throws ProjectCreationFailedException {
    if (createProjectArgs.getProjectName() == null
        || createProjectArgs.getProjectName().isEmpty()) {
      throw new ProjectCreationFailedException("Project name is required");
    }

    String nameWithoutSuffix = ProjectUtil.stripGitSuffix(createProjectArgs.getProjectName());
    createProjectArgs.setProjectName(nameWithoutSuffix);

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

    List<String> transformedBranches = new ArrayList<String>();
    if (createProjectArgs.branch == null ||
        createProjectArgs.branch.isEmpty()) {
      createProjectArgs.branch = Collections.singletonList(Constants.MASTER);
    }
    for (String branch : createProjectArgs.branch) {
      while (branch.startsWith("/")) {
        branch = branch.substring(1);
      }
      if (!branch.startsWith(Constants.R_HEADS)) {
        branch = Constants.R_HEADS + branch;
      }
      if (!Repository.isValidRefName(branch)) {
        throw new ProjectCreationFailedException(String.format(
            "Branch \"%s\" is not a valid name.", branch));
      }
      if (!transformedBranches.contains(branch)) {
        transformedBranches.add(branch);
      }
    }
    createProjectArgs.branch = transformedBranches;
  }

  private void createEmptyCommits(final Repository repo,
      final Project.NameKey project, final List<String> refs)
      throws IOException {
    ObjectInserter oi = repo.newObjectInserter();
    try {
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
        final Result result = ru.update();
        switch (result) {
          case NEW:
            referenceUpdated.fire(project, ru);
            break;
          default: {
            throw new IOException(String.format(
              "Failed to create ref \"%s\": %s", ref, result.name()));
          }
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
