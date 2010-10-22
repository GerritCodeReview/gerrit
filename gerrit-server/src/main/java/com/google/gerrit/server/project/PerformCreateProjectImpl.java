// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.common.CollectionsUtil;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.Project.SubmitType;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.ProjectCreatorGroups;
import com.google.gerrit.server.config.ProjectOwnerGroups;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReplicationQueue;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Common class that holds the code to create projects */
public class PerformCreateProjectImpl implements PerformCreateProject {

  public interface Factory {
    PerformCreateProjectImpl create(CreateProjectArgs createProjectArgs);
  }

  private String projectName;
  private List<AccountGroup.Id> ownerIds;
  private final String newParent;
  private final String projectDescription;
  private final SubmitType submitType;
  private final boolean contributorAgreements;
  private final boolean signedOffBy;
  private final boolean permissionsOnly;
  private String branch;
  private final boolean contentMerge;
  private final boolean changeIdRequired;

  private final Set<AccountGroup.Id> projectCreatorGroups;
  private final Set<AccountGroup.Id> projectOwnerGroups;
  private final IdentifiedUser currentUser;
  private final GitRepositoryManager repoManager;
  private final ReplicationQueue rq;
  private final ReviewDb db;
  private final ProjectCache projectCache;

  @Inject
  PerformCreateProjectImpl(
      @ProjectCreatorGroups Set<AccountGroup.Id> projectCreatorGroups,
      @ProjectOwnerGroups Set<AccountGroup.Id> projectOwnerGroups,
      IdentifiedUser currentUser, GitRepositoryManager repoManager,
      ReplicationQueue rq, ReviewDb db, ProjectCache projectCache,
      @Assisted CreateProjectArgs createProjectArgs) {
    this.projectCreatorGroups = projectCreatorGroups;
    this.projectOwnerGroups = projectOwnerGroups;
    this.currentUser = currentUser;
    this.repoManager = repoManager;
    this.rq = rq;
    this.db = db;
    this.projectCache = projectCache;

    this.projectName = createProjectArgs.getProjectName();
    this.ownerIds = createProjectArgs.getOwnerIds();
    this.newParent = createProjectArgs.getNewParent();
    this.projectDescription = createProjectArgs.getProjectDescription();
    this.submitType = createProjectArgs.getSubmitType();
    this.contributorAgreements = createProjectArgs.isContributorAgreements();
    this.signedOffBy = createProjectArgs.isSignedOffBy();
    this.permissionsOnly = createProjectArgs.isPermissionsOnly();
    this.branch = createProjectArgs.getBranch();
    this.contentMerge = createProjectArgs.isContentMerge();
    this.changeIdRequired = createProjectArgs.isChangeIdRequired();
  }

  @Override
  public void createProject() throws OrmException, RepositoryNotFoundException,
      IOException, CreateProjectParamsException {
    final StringBuilder err = validateParameters();

    if (err.length() == 0) {
      if (!permissionsOnly) {
        final Repository repo = repoManager.createRepository(projectName);
        try {
          repo.create(true);

          final RefUpdate u = repo.updateRef(Constants.HEAD);
          u.disableRefLog();
          u.link(branch);

          repoManager.setProjectDescription(projectName, projectDescription);
        } finally {
          repo.close();
        }
      }

      createProjectDB();

      if (!permissionsOnly) {
        rq.replicateNewProject(new Project.NameKey(projectName), branch);
      }
    } else {
      throw new CreateProjectParamsException(err.toString());
    }
  }

  private void createProjectDB() throws OrmException {
    final Project.NameKey newProjectNameKey = new Project.NameKey(projectName);

    List<RefRight> access = new ArrayList<RefRight>();
    for (AccountGroup.Id ownerId : ownerIds) {
      final RefRight.Key prk =
          new RefRight.Key(newProjectNameKey, new RefRight.RefPattern(
              RefRight.ALL), ApprovalCategory.OWN, ownerId);
      final RefRight pr = new RefRight(prk);
      pr.setMaxValue((short) 1);
      pr.setMinValue((short) 1);
      access.add(pr);
    }
    db.refRights().insert(access);

    final Project newProject = new Project(newProjectNameKey);
    newProject.setDescription(projectDescription);
    newProject.setSubmitType(submitType);
    newProject.setUseContributorAgreements(contributorAgreements);
    newProject.setUseSignedOffBy(signedOffBy);
    newProject.setRequireChangeID(changeIdRequired);
    newProject.setUseContentMerge(contentMerge);
    if (newParent != null && !newParent.isEmpty()) {
      newProject.setParent(new Project.NameKey(newParent));
    }

    db.projects().insert(Collections.singleton(newProject));
  }

  private StringBuilder validateParameters() {
    final StringBuilder err = new StringBuilder();

    if (projectName.contains(" ")) {
      err.append("project name should not contain whitespaces");
    }

    if (projectName.endsWith(".git")) {
      projectName =
          projectName.substring(0, projectName.length() - ".git".length());
    }

    if (newParent != null && !newParent.isEmpty()) {
      final ProjectState state =
          projectCache.get(new Project.NameKey(newParent));

      if (state == null) {
        err.append("fatal: '" + newParent + "': not a Gerrit project");
        return err;
      }
    }

    if (!CollectionsUtil.isAnyIncludedIn(currentUser.getEffectiveGroups(),
        projectCreatorGroups)) {
      err.append("fatal: Not permitted to create " + projectName);
      return err;
    }

    if (ownerIds != null && !ownerIds.isEmpty()) {
      ownerIds =
          new ArrayList<AccountGroup.Id>(new HashSet<AccountGroup.Id>(ownerIds));
    } else {
      ownerIds = new ArrayList<AccountGroup.Id>(projectOwnerGroups);
    }

    while (branch.startsWith("/")) {
      branch = branch.substring(1);
    }
    if (!branch.startsWith(Constants.R_HEADS)) {
      branch = Constants.R_HEADS + branch;
    }
    if (!Repository.isValidRefName(branch)) {
      err.append("branch \"" + branch + "\" is not a valid name");
    }

    return err;
  }
}
