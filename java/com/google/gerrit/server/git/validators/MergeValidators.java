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
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.PatchSet;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.api.projects.ProjectConfigEntryType;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountProperties;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.git.CodeReviewCommit;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;

/**
 * Collection of validators that run inside Gerrit before a change is submitted. The main purpose is
 * to ensure that NoteDb data is mutated in a controlled way.
 *
 * <p>The difference between this and {@link OnSubmitValidators} is that this validates the original
 * commit. Depending on the {@link com.google.gerrit.server.submit.SubmitStrategy} that the project
 * chooses, the resulting commit in the repo might differ from this original commit. In case you
 * want to validate the resulting commit, use {@link OnSubmitValidators}
 */
public class MergeValidators {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PluginSetContext<MergeValidationListener> mergeValidationListeners;
  private final ProjectConfigValidator.Factory projectConfigValidatorFactory;
  private final AccountMergeValidator.Factory accountValidatorFactory;
  private final GroupMergeValidator.Factory groupValidatorFactory;

  public interface Factory {
    MergeValidators create();
  }

  @Inject
  MergeValidators(
      PluginSetContext<MergeValidationListener> mergeValidationListeners,
      ProjectConfigValidator.Factory projectConfigValidatorFactory,
      AccountMergeValidator.Factory accountValidatorFactory,
      GroupMergeValidator.Factory groupValidatorFactory) {
    this.mergeValidationListeners = mergeValidationListeners;
    this.projectConfigValidatorFactory = projectConfigValidatorFactory;
    this.accountValidatorFactory = accountValidatorFactory;
    this.groupValidatorFactory = groupValidatorFactory;
  }

  /**
   * Runs all validators and throws a {@link MergeValidationException} for the first validator that
   * failed. Only the first violation is propagated and processing is stopped thereafter.
   */
  public void validatePreMerge(
      Repository repo,
      CodeReviewCommit commit,
      ProjectState destProject,
      BranchNameKey destBranch,
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

  /** Validator for any commits to {@code refs/meta/config}. */
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
    private static final String SET_BY_OWNER =
        "Change contains a project configuration that changes the parent"
            + " project.\n"
            + "The change must be submitted by a Gerrit administrator or the project owner.";

    private final AllProjectsName allProjectsName;
    private final AllUsersName allUsersName;
    private final ProjectCache projectCache;
    private final PermissionBackend permissionBackend;
    private final DynamicMap<ProjectConfigEntry> pluginConfigEntries;
    private final ProjectConfig.Factory projectConfigFactory;
    private final boolean allowProjectOwnersToChangeParent;

    public interface Factory {
      ProjectConfigValidator create();
    }

    @Inject
    public ProjectConfigValidator(
        AllProjectsName allProjectsName,
        AllUsersName allUsersName,
        ProjectCache projectCache,
        PermissionBackend permissionBackend,
        DynamicMap<ProjectConfigEntry> pluginConfigEntries,
        ProjectConfig.Factory projectConfigFactory,
        @GerritServerConfig Config config) {
      this.allProjectsName = allProjectsName;
      this.allUsersName = allUsersName;
      this.projectCache = projectCache;
      this.permissionBackend = permissionBackend;
      this.pluginConfigEntries = pluginConfigEntries;
      this.projectConfigFactory = projectConfigFactory;
      this.allowProjectOwnersToChangeParent =
          config.getBoolean("receive", "allowProjectOwnersToChangeParent", false);
    }

    @Override
    public void onPreMerge(
        final Repository repo,
        final CodeReviewCommit commit,
        final ProjectState destProject,
        final BranchNameKey destBranch,
        final PatchSet.Id patchSetId,
        IdentifiedUser caller)
        throws MergeValidationException {
      if (RefNames.REFS_CONFIG.equals(destBranch.branch())) {
        final Project.NameKey newParent;
        try {
          ProjectConfig cfg = projectConfigFactory.create(destProject.getNameKey());
          cfg.load(destProject.getNameKey(), repo, commit);
          newParent = cfg.getProject().getParent(allProjectsName);
          final Project.NameKey oldParent = destProject.getProject().getParent(allProjectsName);
          if (oldParent == null) {
            // update of the 'All-Projects' project
            if (newParent != null) {
              throw new MergeValidationException(ROOT_NO_PARENT);
            }
          } else {
            if (!oldParent.equals(newParent)) {
              if (!allowProjectOwnersToChangeParent) {
                try {
                  permissionBackend.user(caller).check(GlobalPermission.ADMINISTRATE_SERVER);
                } catch (AuthException e) {
                  throw new MergeValidationException(SET_BY_ADMIN, e);
                } catch (PermissionBackendException e) {
                  logger.atWarning().withCause(e).log("Cannot check ADMINISTRATE_SERVER");
                  throw new MergeValidationException("validation unavailable");
                }
              } else {
                try {
                  permissionBackend
                      .user(caller)
                      .project(destProject.getNameKey())
                      .check(ProjectPermission.WRITE_CONFIG);
                } catch (AuthException e) {
                  throw new MergeValidationException(SET_BY_OWNER, e);
                } catch (PermissionBackendException e) {
                  logger.atWarning().withCause(e).log("Cannot check WRITE_CONFIG");
                  throw new MergeValidationException("validation unavailable");
                }
              }
              if (allUsersName.equals(destProject.getNameKey())
                  && !allProjectsName.equals(newParent)) {
                throw new MergeValidationException(
                    String.format(
                        " %s must inherit from %s", allUsersName.get(), allProjectsName.get()));
              }
              if (!projectCache.get(newParent).isPresent()) {
                throw new MergeValidationException(PARENT_NOT_FOUND);
              }
            }
          }

          for (Extension<ProjectConfigEntry> e : pluginConfigEntries) {
            PluginConfig.Update pluginCfg = cfg.getPluginConfig(e.getPluginName());
            ProjectConfigEntry configEntry = e.getProvider().get();

            String value = pluginCfg.getString(e.getExportName());
            String oldValue =
                destProject.getPluginConfig(e.getPluginName()).getString(e.getExportName());

            if ((!Objects.equals(value, oldValue)) && !configEntry.isEditable(destProject)) {
              throw new MergeValidationException(PLUGIN_VALUE_NOT_EDITABLE);
            }

            if (ProjectConfigEntryType.LIST.equals(configEntry.getType())
                && value != null
                && !configEntry.getPermittedValues().contains(value)) {
              throw new MergeValidationException(PLUGIN_VALUE_NOT_PERMITTED);
            }
          }
        } catch (ConfigInvalidException | IOException e) {
          throw new MergeValidationException(INVALID_CONFIG, e);
        }
      }
    }
  }

  /** Validator that calls to plugins that provide additional validators. */
  public static class PluginMergeValidationListener implements MergeValidationListener {
    private final PluginSetContext<MergeValidationListener> mergeValidationListeners;

    public PluginMergeValidationListener(
        PluginSetContext<MergeValidationListener> mergeValidationListeners) {
      this.mergeValidationListeners = mergeValidationListeners;
    }

    @Override
    public void onPreMerge(
        Repository repo,
        CodeReviewCommit commit,
        ProjectState destProject,
        BranchNameKey destBranch,
        PatchSet.Id patchSetId,
        IdentifiedUser caller)
        throws MergeValidationException {
      mergeValidationListeners.runEach(
          l -> l.onPreMerge(repo, commit, destProject, destBranch, patchSetId, caller),
          MergeValidationException.class);
    }
  }

  public static class AccountMergeValidator implements MergeValidationListener {
    public interface Factory {
      AccountMergeValidator create();
    }

    private final AllUsersName allUsersName;
    private final ChangeData.Factory changeDataFactory;
    private final AccountValidator accountValidator;

    @Inject
    public AccountMergeValidator(
        AllUsersName allUsersName,
        ChangeData.Factory changeDataFactory,
        AccountValidator accountValidator) {
      this.allUsersName = allUsersName;
      this.changeDataFactory = changeDataFactory;
      this.accountValidator = accountValidator;
    }

    @Override
    public void onPreMerge(
        Repository repo,
        CodeReviewCommit commit,
        ProjectState destProject,
        BranchNameKey destBranch,
        PatchSet.Id patchSetId,
        IdentifiedUser caller)
        throws MergeValidationException {
      Account.Id accountId = Account.Id.fromRef(destBranch.branch());
      if (!allUsersName.equals(destProject.getNameKey()) || accountId == null) {
        return;
      }

      ChangeData cd =
          changeDataFactory.create(destProject.getProject().getNameKey(), patchSetId.changeId());
      try {
        if (!cd.currentFilePaths().contains(AccountProperties.ACCOUNT_CONFIG)) {
          return;
        }
      } catch (StorageException e) {
        logger.atSevere().withCause(e).log("Cannot validate account update");
        throw new MergeValidationException("account validation unavailable");
      }

      try (RevWalk rw = new RevWalk(repo)) {
        List<String> errorMessages = accountValidator.validate(accountId, repo, rw, null, commit);
        if (!errorMessages.isEmpty()) {
          throw new MergeValidationException(
              "invalid account configuration: " + Joiner.on("; ").join(errorMessages));
        }
      } catch (IOException e) {
        logger.atSevere().withCause(e).log("Cannot validate account update");
        throw new MergeValidationException("account validation unavailable");
      }
    }
  }

  /** Validator to ensure that group refs are not mutated. */
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
        BranchNameKey destBranch,
        PatchSet.Id patchSetId,
        IdentifiedUser caller)
        throws MergeValidationException {
      // Groups are stored inside the 'All-Users' repository.
      if (!allUsersName.equals(destProject.getNameKey())
          || !RefNames.isGroupRef(destBranch.branch())) {
        return;
      }

      throw new MergeValidationException("group update not allowed");
    }
  }
}
