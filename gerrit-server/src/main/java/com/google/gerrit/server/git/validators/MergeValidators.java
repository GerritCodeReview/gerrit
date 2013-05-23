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

import com.google.common.collect.Lists;
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

import org.eclipse.jgit.lib.Repository;

import java.util.List;

public class MergeValidators {
  private final DynamicSet<MergeValidationListener> mergeValidationListeners;
  private final ProjectConfigValidator.Factory projectConfigValidatorFactory;

  public interface Factory {
    MergeValidators create();
  }

  @Inject
  MergeValidators(DynamicSet<MergeValidationListener> mergeValidationListeners,
      ProjectConfigValidator.Factory projectConfigValidatorFactory) {
    this.mergeValidationListeners = mergeValidationListeners;
    this.projectConfigValidatorFactory = projectConfigValidatorFactory;
  }

  public void validatePreMerge(Repository repo,
      CodeReviewCommit commit,
      ProjectState destProject,
      Branch.NameKey destBranch,
      PatchSet.Id patchSetId)
      throws MergeValidationException {
    List<MergeValidationListener> validators = Lists.newLinkedList();

    validators.add(new PluginMergeValidationListener(mergeValidationListeners));
    validators.add(projectConfigValidatorFactory.create());

    for (MergeValidationListener validator : validators) {
      validator.onPreMerge(repo, commit, destProject, destBranch, patchSetId);
    }
  }

  public static class ProjectConfigValidator implements
      MergeValidationListener {
    private final AllProjectsName allProjectsName;
    private final ReviewDb db;
    private final ProjectCache projectCache;
    private final IdentifiedUser.GenericFactory identifiedUserFactory;

    public interface Factory {
      ProjectConfigValidator create();
    }

    @Inject
    public ProjectConfigValidator(AllProjectsName allProjectsName,
        ReviewDb db, ProjectCache projectCache,
        IdentifiedUser.GenericFactory iuf) {
      this.allProjectsName = allProjectsName;
      this.db = db;
      this.projectCache = projectCache;
      this.identifiedUserFactory = iuf;
    }

    @Override
    public void onPreMerge(final Repository repo,
        final CodeReviewCommit commit,
        final ProjectState destProject,
        final Branch.NameKey destBranch,
        final PatchSet.Id patchSetId)
        throws MergeValidationException {
      if (GitRepositoryManager.REF_CONFIG.equals(destBranch.get())) {
        final Project.NameKey newParent;
        try {
          ProjectConfig cfg =
              new ProjectConfig(destProject.getProject().getNameKey());
          cfg.load(repo, commit);
          newParent = cfg.getProject().getParent(allProjectsName);
        } catch (Exception e) {
          throw new MergeValidationException(CommitMergeStatus.
              INVALID_PROJECT_CONFIGURATION);
        }
        final Project.NameKey oldParent =
            destProject.getProject().getParent(allProjectsName);
        if (oldParent == null) {
          // update of the 'All-Projects' project
          if (newParent != null) {
            throw new MergeValidationException(CommitMergeStatus.
                INVALID_PROJECT_CONFIGURATION_ROOT_PROJECT_CANNOT_HAVE_PARENT);
          }
        } else {
          if (!oldParent.equals(newParent)) {
            final PatchSetApproval psa = getSubmitter(db, patchSetId);
            if (psa == null) {
              throw new MergeValidationException(CommitMergeStatus.
                  SETTING_PARENT_PROJECT_ONLY_ALLOWED_BY_ADMIN);
            }
            final IdentifiedUser submitter =
                identifiedUserFactory.create(psa.getAccountId());
            if (!submitter.getCapabilities().canAdministrateServer()) {
              throw new MergeValidationException(CommitMergeStatus.
                  SETTING_PARENT_PROJECT_ONLY_ALLOWED_BY_ADMIN);
            }

            if (projectCache.get(newParent) == null) {
              throw new MergeValidationException(CommitMergeStatus.
                  INVALID_PROJECT_CONFIGURATION_PARENT_PROJECT_NOT_FOUND);
            }
          }
        }
      }
    }
  }

  /** Execute merge validation plug-ins */
  public static class PluginMergeValidationListener implements
      MergeValidationListener {
    private final DynamicSet<MergeValidationListener> mergeValidationListeners;

    public PluginMergeValidationListener(
        DynamicSet<MergeValidationListener> mergeValidationListeners) {
      this.mergeValidationListeners = mergeValidationListeners;
    }

    @Override
    public void onPreMerge(Repository repo,
        CodeReviewCommit commit,
        ProjectState destProject,
        Branch.NameKey destBranch,
        PatchSet.Id patchSetId)
        throws MergeValidationException {
      for (MergeValidationListener validator : mergeValidationListeners) {
        validator.onPreMerge(repo, commit, destProject, destBranch, patchSetId);
      }
    }
  }
}
