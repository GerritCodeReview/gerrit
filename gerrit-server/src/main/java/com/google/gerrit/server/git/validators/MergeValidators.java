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

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicMap.Entry;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.ApprovalsUtil;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.git.CommitMergeStatus;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
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
    private final ApprovalsUtil approvalsUtil;
    private final DynamicMap<ProjectConfigEntry> pluginConfigEntries;

    public interface Factory {
      ProjectConfigValidator create();
    }

    @Inject
    public ProjectConfigValidator(AllProjectsName allProjectsName,
        ReviewDb db, ProjectCache projectCache,
        IdentifiedUser.GenericFactory iuf,
        ApprovalsUtil approvalsUtil,
        DynamicMap<ProjectConfigEntry> pluginConfigEntries) {
      this.allProjectsName = allProjectsName;
      this.db = db;
      this.projectCache = projectCache;
      this.identifiedUserFactory = iuf;
      this.approvalsUtil = approvalsUtil;
      this.pluginConfigEntries = pluginConfigEntries;
    }

    @Override
    public void onPreMerge(final Repository repo,
        final CodeReviewCommit commit,
        final ProjectState destProject,
        final Branch.NameKey destBranch,
        final PatchSet.Id patchSetId)
        throws MergeValidationException {
      if (RefNames.REFS_CONFIG.equals(destBranch.get())) {
        final Project.NameKey newParent;
        try {
          ProjectConfig cfg =
              new ProjectConfig(destProject.getProject().getNameKey());
          cfg.load(repo, commit);
          newParent = cfg.getProject().getParent(allProjectsName);
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
              PatchSetApproval psa =
                  approvalsUtil.getSubmitter(db, commit.notes(), patchSetId);
              if (psa == null) {
                throw new MergeValidationException(CommitMergeStatus.SETTING_PARENT_PROJECT_NOT_ALLOWED);
              }
              final IdentifiedUser submitter =
                  identifiedUserFactory.create(psa.getAccountId());
              if (!submitter.getCapabilities().canChangeParent()) {
                throw new MergeValidationException(CommitMergeStatus.SETTING_PARENT_PROJECT_NOT_ALLOWED);
              }

              if (projectCache.get(newParent) == null) {
                throw new MergeValidationException(CommitMergeStatus.
                    INVALID_PROJECT_CONFIGURATION_PARENT_PROJECT_NOT_FOUND);
              }
            }
          }

          for (Entry<ProjectConfigEntry> e : pluginConfigEntries) {
            PluginConfig pluginCfg = cfg.getPluginConfig(e.getPluginName());
            ProjectConfigEntry configEntry = e.getProvider().get();

            String value = pluginCfg.getString(e.getExportName());
            String oldValue = destProject.getConfig()
                .getPluginConfig(e.getPluginName())
                .getString(e.getExportName());

            if ((value == null ? oldValue != null : !value.equals(oldValue)) &&
                !configEntry.isEditable(destProject)) {
              throw new MergeValidationException(CommitMergeStatus.
                  INVALID_PROJECT_CONFIGURATION_PLUGIN_VALUE_NOT_EDITABLE);
            }

            if (ProjectConfigEntry.Type.LIST.equals(configEntry.getType())
                && value != null && !configEntry.getPermittedValues().contains(value)) {
              throw new MergeValidationException(CommitMergeStatus.
                  INVALID_PROJECT_CONFIGURATION_PLUGIN_VALUE_NOT_PERMITTED);
            }
          }
        } catch (ConfigInvalidException | IOException e) {
          throw new MergeValidationException(CommitMergeStatus.
              INVALID_PROJECT_CONFIGURATION);
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
