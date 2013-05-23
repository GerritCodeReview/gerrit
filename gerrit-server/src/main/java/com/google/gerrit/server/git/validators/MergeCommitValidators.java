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

package com.google.gerrit.server.git.validators;

import static com.google.gerrit.server.git.MergeUtil.getSubmitter;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CommitMergeStatus;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.Repository;

import java.util.LinkedList;
import java.util.List;


public class MergeCommitValidators {
  private final DynamicSet<MergeCommitValidationListener> commitValidationListeners;
  private final Repository repo;
  private final AllProjectsName allProjectsName;
  private final ReviewDb db;
  private final PatchSet ps;
  private final ProjectCache projectCache;
  private final IdentifiedUser.GenericFactory identifiedUserFactory;

  public interface Factory {
    MergeCommitValidators create(Repository repo);
  }

  @Inject
  MergeCommitValidators(final DynamicSet<MergeCommitValidationListener> commitValidationListeners,
      @Assisted final Repository repo, final AllProjectsName allProjectsName,
      final ReviewDb db, final PatchSet ps, final ProjectCache pc,
      final IdentifiedUser.GenericFactory iuf) {
    this.commitValidationListeners = commitValidationListeners;
    this.repo = repo;
    this.allProjectsName = allProjectsName;
    this.db = db;
    this.ps = ps;
    this.projectCache = pc;
    this.identifiedUserFactory = iuf;
  }

  public void validatePreMergeCommit(final CodeReviewCommit commit, ProjectState destProject,
      final Branch.NameKey destBranch)
      throws MergeCommitValidationException {
    List<MergeCommitValidationListener> validators =
        new LinkedList<MergeCommitValidationListener>();

    validators.add(new PluginCommitValidationListener(commitValidationListeners));
    validators.add(new ProjectConfigValidator(repo, allProjectsName, db,
        ps, projectCache, identifiedUserFactory));

    for (MergeCommitValidationListener validator : validators) {
      validator.onPreMergeCommit(commit, destProject, destBranch);
    }
  }

  public static class ProjectConfigValidator implements
      MergeCommitValidationListener {
    private final Repository repo;
    private final AllProjectsName allProjectsName;
    private final ReviewDb db;
    private final PatchSet ps;
    private final ProjectCache projectCache;
    private final IdentifiedUser.GenericFactory identifiedUserFactory;

    public ProjectConfigValidator(final Repository repo, final AllProjectsName allProjectsName,
        final ReviewDb db, final PatchSet ps, final ProjectCache projectCache,
        final IdentifiedUser.GenericFactory iuf) {
      this.repo = repo;
      this.allProjectsName = allProjectsName;
      this.db = db;
      this.ps = ps;
      this.projectCache = projectCache;
      this.identifiedUserFactory = iuf;
    }

    @Override
    public void onPreMergeCommit(final CodeReviewCommit commit,
        final ProjectState destProject,
        final Branch.NameKey destBranch)
        throws MergeCommitValidationException {
      if (GitRepositoryManager.REF_CONFIG.equals(destBranch.get())) {
        final Project.NameKey newParent;
        try {
          ProjectConfig cfg =
              new ProjectConfig(destProject.getProject().getNameKey());
          cfg.load(repo, commit);
          newParent = cfg.getProject().getParent(allProjectsName);
        } catch (Exception e) {
          throw new MergeCommitValidationException(CommitMergeStatus.
              INVALID_PROJECT_CONFIGURATION);
        }
        final Project.NameKey oldParent =
            destProject.getProject().getParent(allProjectsName);
        if (oldParent == null) {
          // update of the 'All-Projects' project
          if (newParent != null) {
            throw new MergeCommitValidationException(CommitMergeStatus.
                INVALID_PROJECT_CONFIGURATION_ROOT_PROJECT_CANNOT_HAVE_PARENT);
          }
        } else {
          if (!oldParent.equals(newParent)) {
            final PatchSetApproval psa = getSubmitter(db, ps.getId());
            if (psa == null) {
              throw new MergeCommitValidationException(CommitMergeStatus.
                  SETTING_PARENT_PROJECT_ONLY_ALLOWED_BY_ADMIN);
            }
            final IdentifiedUser submitter =
                identifiedUserFactory.create(psa.getAccountId());
            if (!submitter.getCapabilities().canAdministrateServer()) {
              throw new MergeCommitValidationException(CommitMergeStatus.
                  SETTING_PARENT_PROJECT_ONLY_ALLOWED_BY_ADMIN);
            }

            if (projectCache.get(newParent) == null) {
              throw new MergeCommitValidationException(CommitMergeStatus.
                  INVALID_PROJECT_CONFIGURATION_PARENT_PROJECT_NOT_FOUND);
            }
          }
        }
      }
    }
  }

  /** Execute commit validation plug-ins */
  public static class PluginCommitValidationListener implements
      MergeCommitValidationListener {
    private final DynamicSet<MergeCommitValidationListener> commitValidationListeners;

    public PluginCommitValidationListener(
        final DynamicSet<MergeCommitValidationListener> commitValidationListeners) {
      this.commitValidationListeners = commitValidationListeners;
    }

    @Override
    public void onPreMergeCommit(final CodeReviewCommit commit,
        final ProjectState destProject,
        final Branch.NameKey destBranch)
        throws MergeCommitValidationException {
      for (MergeCommitValidationListener validator : commitValidationListeners) {
        validator.onPreMergeCommit(commit, destProject, destBranch);
      }
    }
  }
}
