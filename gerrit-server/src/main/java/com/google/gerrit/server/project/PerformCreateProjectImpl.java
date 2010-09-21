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

import javax.annotation.Nullable;

/** Common class that holds the code to create projects */
public class PerformCreateProjectImpl implements PerformCreateProject {

  public interface Factory {
    PerformCreateProjectImpl create(
        @Assisted("projectName") String projectName,
        List<AccountGroup.Id> ownerIds,
        @Assisted("newParent") String newParent,
        @Assisted("projectDescription") String projectDescription,
        SubmitType submitType,
        @Assisted("contributorAgreements") boolean contributorAgreements,
        @Assisted("signedOffBy") boolean signedOffBy,
        @Assisted("permissionsOnly") boolean permissionsOnly,
        @Assisted("branch") String branch);
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

  @Inject
  @ProjectCreatorGroups
  private Set<AccountGroup.Id> projectCreatorGroups;

  @Inject
  @ProjectOwnerGroups
  private Set<AccountGroup.Id> projectOwnerGroups;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private GitRepositoryManager repoManager;

  @Inject
  private ReplicationQueue rq;

  @Inject
  private ReviewDb db;

  @Inject
  private ProjectCache projectCache;

  @Inject
  PerformCreateProjectImpl(@Assisted("projectName") String projectName,
      @Assisted @Nullable List<AccountGroup.Id> ownerIds,
      @Assisted("newParent") @Nullable final String newParent,
      @Assisted("projectDescription") final String projectDescription,
      @Assisted final SubmitType submitType,
      @Assisted("contributorAgreements") final boolean contributorAgreements,
      @Assisted("signedOffBy") final boolean signedOffBy,
      @Assisted("permissionsOnly") boolean permissionsOnly, @Assisted("branch") String branch) {
    this.projectName = projectName;
    this.ownerIds = ownerIds;
    this.newParent = newParent;
    this.projectDescription = projectDescription;
    this.submitType = submitType;
    this.contributorAgreements = contributorAgreements;
    this.signedOffBy = signedOffBy;
    this.permissionsOnly = permissionsOnly;
    this.branch = branch;
  }

  @Override
  public StringBuilder createProject() throws OrmException,
      RepositoryNotFoundException, IOException {
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
    }

    return err;
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
    if (newParent != null && !newParent.isEmpty()) {
      newProject.setParent(new Project.NameKey(newParent));
    }

    db.projects().insert(Collections.singleton(newProject));
  }

  private StringBuilder validateParameters() {
    final StringBuilder err = new StringBuilder();

    if (projectName.endsWith(".git")) {
      projectName =
          projectName.substring(0, projectName.length() - ".git".length());
    }

    if (newParent != null && !newParent.isEmpty()) {
      final ProjectState state = projectCache.get(new Project.NameKey(newParent));

      if (state == null) {
        err.append("fatal: '" + newParent+ "': not a Gerrit project");
        return err;
      }
    }

    if (!CollectionsUtil.isAnyIncludedIn(currentUser.getEffectiveGroups(), projectCreatorGroups)) {
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