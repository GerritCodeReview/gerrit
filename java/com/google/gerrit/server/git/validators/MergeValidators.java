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

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import com.google.gerrit.config.AllProjectsName;
import com.google.gerrit.config.AllUsersName;
import com.google.gerrit.extensions.api.projects.ProjectConfigEntryType;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicMap.Entry;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountProperties;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.List;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class MergeValidators {
  private static final Logger log = LoggerFactory.getLogger(MergeValidators.class);

  private final DynamicSet<MergeValidationListener> mergeValidationListeners;
  private final ProjectConfigValidator.Factory projectConfigValidatorFactory;
  private final AccountMergeValidator.Factory accountValidatorFactory;
  private final GroupMergeValidator.Factory groupValidatorFactory;

  public interface Factory {
    MergeValidators create();
  }

  @Inject
  MergeValidators(
      DynamicSet<MergeValidationListener> mergeValidationListeners,
      ProjectConfigValidator.Factory projectConfigValidatorFactory,
      AccountMergeValidator.Factory accountValidatorFactory,
      GroupMergeValidator.Factory groupValidatorFactory) {
    this.mergeValidationListeners = mergeValidationListeners;
    this.projectConfigValidatorFactory = projectConfigValidatorFactory;
    this.accountValidatorFactory = accountValidatorFactory;
    this.groupValidatorFactory = groupValidatorFactory;
  }

  public void validatePreMerge(
      Repository repo,
      CodeReviewCommit commit,
      ProjectState destProject,
      Branch.NameKey destBranch,
      PatchSet.Id patchSetId,
      IdentifiedUser caller)
      throws MergeValidationException {
    List<MergeValidationListener> validators =
        ImmutableList.of(
            new PluginMergeValidationListener(mergeValidationListeners),
            projectConfigValidatorFactory.create(),
            accountValidatorFactory.create(),
            groupValidatorFactory.create());

    for (MergeValidationListener validator : validators) {
      validator.onPreMerge(repo, commit, destProject, destBranch, patchSetId, caller);
    }
  }

  public static class ProjectConfigValidator implements MergeValidationListener {
    private static final String INVALID_CONFIG =
        "Change contains an invalid project configuration.";
    private static final String PARENT_NOT_FOUND =
        "Change contains an invalid project configuration:\nParent project does not exist.";
    private static final String PLUGIN_VALUE_NOT_EDITABLE =
        "Change contains an invalid project configuration:\n"
            + "One of the plugin configuration parameters is not editable.";
    private static final String PLUGIN_VALUE_NOT_PERMITTED =
        "Change contains an invalid project configuration:\n"
            + "One of the plugin configuration parameters has a value that is not"
            + " permitted.";
    private static final String ROOT_NO_PARENT =
        "Change contains an invalid project configuration:\n"
            + "The root project cannot have a parent.";
    private static final String SET_BY_ADMIN =
        "Change contains a project configuration that changes the parent"
            + " project.\n"
            + "The change must be submitted by a Gerrit administrator.";

    private final AllProjectsName allProjectsName;
    private final AllUsersName allUsersName;
    private final ProjectCache projectCache;
    private final PermissionBackend permissionBackend;
    private final DynamicMap<ProjectConfigEntry> pluginConfigEntries;

    public interface Factory {
      ProjectConfigValidator create();
    }

    @Inject
    public ProjectConfigValidator(
        AllProjectsName allProjectsName,
        AllUsersName allUsersName,
        ProjectCache projectCache,
        PermissionBackend permissionBackend,
        DynamicMap<ProjectConfigEntry> pluginConfigEntries) {
      this.allProjectsName = allProjectsName;
      this.allUsersName = allUsersName;
      this.projectCache = projectCache;
      this.permissionBackend = permissionBackend;
      this.pluginConfigEntries = pluginConfigEntries;
    }

    @Override
    public void onPreMerge(
        final Repository repo,
        final CodeReviewCommit commit,
        final ProjectState destProject,
        final Branch.NameKey destBranch,
        final PatchSet.Id patchSetId,
        IdentifiedUser caller)
        throws MergeValidationException {
      if (RefNames.REFS_CONFIG.equals(destBranch.get())) {
        final Project.NameKey newParent;
        try {
          ProjectConfig cfg = new ProjectConfig(destProject.getNameKey());
          cfg.load(repo, commit);
          newParent = cfg.getProject().getParent(allProjectsName);
          final Project.NameKey oldParent = destProject.getProject().getParent(allProjectsName);
          if (oldParent == null) {
            // update of the 'All-Projects' project
            if (newParent != null) {
              throw new MergeValidationException(ROOT_NO_PARENT);
            }
          } else {
            if (!oldParent.equals(newParent)) {
              try {
                permissionBackend.user(caller).check(GlobalPermission.ADMINISTRATE_SERVER);
              } catch (AuthException e) {
                throw new MergeValidationException(SET_BY_ADMIN);
              } catch (PermissionBackendException e) {
                log.warn("Cannot check ADMINISTRATE_SERVER", e);
                throw new MergeValidationException("validation unavailable");
              }
              if (allUsersName.equals(destProject.getNameKey())
                  && !allProjectsName.equals(newParent)) {
                throw new MergeValidationException(
                    String.format(
                        " %s must inherit from %s", allUsersName.get(), allProjectsName.get()));
              }
              if (projectCache.get(newParent) == null) {
                throw new MergeValidationException(PARENT_NOT_FOUND);
              }
            }
          }

          for (Entry<ProjectConfigEntry> e : pluginConfigEntries) {
            PluginConfig pluginCfg = cfg.getPluginConfig(e.getPluginName());
            ProjectConfigEntry configEntry = e.getProvider().get();

            String value = pluginCfg.getString(e.getExportName());
            String oldValue =
                destProject
                    .getConfig()
                    .getPluginConfig(e.getPluginName())
                    .getString(e.getExportName());

            if ((value == null ? oldValue != null : !value.equals(oldValue))
                && !configEntry.isEditable(destProject)) {
              throw new MergeValidationException(PLUGIN_VALUE_NOT_EDITABLE);
            }

            if (ProjectConfigEntryType.LIST.equals(configEntry.getType())
                && value != null
                && !configEntry.getPermittedValues().contains(value)) {
              throw new MergeValidationException(PLUGIN_VALUE_NOT_PERMITTED);
            }
          }
        } catch (ConfigInvalidException | IOException e) {
          throw new MergeValidationException(INVALID_CONFIG);
        }
      }
    }
  }

  /** Execute merge validation plug-ins */
  public static class PluginMergeValidationListener implements MergeValidationListener {
    private final DynamicSet<MergeValidationListener> mergeValidationListeners;

    public PluginMergeValidationListener(
        DynamicSet<MergeValidationListener> mergeValidationListeners) {
      this.mergeValidationListeners = mergeValidationListeners;
    }

    @Override
    public void onPreMerge(
        Repository repo,
        CodeReviewCommit commit,
        ProjectState destProject,
        Branch.NameKey destBranch,
        PatchSet.Id patchSetId,
        IdentifiedUser caller)
        throws MergeValidationException {
      for (MergeValidationListener validator : mergeValidationListeners) {
        validator.onPreMerge(repo, commit, destProject, destBranch, patchSetId, caller);
      }
    }
  }

  public static class AccountMergeValidator implements MergeValidationListener {
    public interface Factory {
      AccountMergeValidator create();
    }

    private final Provider<ReviewDb> dbProvider;
    private final AllUsersName allUsersName;
    private final ChangeData.Factory changeDataFactory;
    private final AccountValidator accountValidator;

    @Inject
    public AccountMergeValidator(
        Provider<ReviewDb> dbProvider,
        AllUsersName allUsersName,
        ChangeData.Factory changeDataFactory,
        AccountValidator accountValidator) {
      this.dbProvider = dbProvider;
      this.allUsersName = allUsersName;
      this.changeDataFactory = changeDataFactory;
      this.accountValidator = accountValidator;
    }

    @Override
    public void onPreMerge(
        Repository repo,
        CodeReviewCommit commit,
        ProjectState destProject,
        Branch.NameKey destBranch,
        PatchSet.Id patchSetId,
        IdentifiedUser caller)
        throws MergeValidationException {
      Account.Id accountId = Account.Id.fromRef(destBranch.get());
      if (!allUsersName.equals(destProject.getNameKey()) || accountId == null) {
        return;
      }

      ChangeData cd =
          changeDataFactory.create(
              dbProvider.get(), destProject.getProject().getNameKey(), patchSetId.getParentKey());
      try {
        if (!cd.currentFilePaths().contains(AccountProperties.ACCOUNT_CONFIG)) {
          return;
        }
      } catch (IOException | OrmException e) {
        log.error("Cannot validate account update", e);
        throw new MergeValidationException("account validation unavailable");
      }

      try (RevWalk rw = new RevWalk(repo)) {
        List<String> errorMessages = accountValidator.validate(accountId, repo, rw, null, commit);
        if (!errorMessages.isEmpty()) {
          throw new MergeValidationException(
              "invalid account configuration: " + Joiner.on("; ").join(errorMessages));
        }
      } catch (IOException e) {
        log.error("Cannot validate account update", e);
        throw new MergeValidationException("account validation unavailable");
      }
    }
  }

  public static class GroupMergeValidator implements MergeValidationListener {
    public interface Factory {
      GroupMergeValidator create();
    }

    private final AllUsersName allUsersName;

    @Inject
    public GroupMergeValidator(AllUsersName allUsersName) {
      this.allUsersName = allUsersName;
    }

    @Override
    public void onPreMerge(
        Repository repo,
        CodeReviewCommit commit,
        ProjectState destProject,
        Branch.NameKey destBranch,
        PatchSet.Id patchSetId,
        IdentifiedUser caller)
        throws MergeValidationException {
      // Groups are stored inside the 'All-Users' repository.
      if (!allUsersName.equals(destProject.getNameKey())
          || !RefNames.isGroupRef(destBranch.get())) {
        return;
      }

      throw new MergeValidationException("group update not allowed");
    }
  }
}
