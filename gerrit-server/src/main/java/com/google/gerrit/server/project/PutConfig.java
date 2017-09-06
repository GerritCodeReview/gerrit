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

package com.google.gerrit.server.project;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.ConfigValue;
import com.google.gerrit.extensions.api.projects.ProjectConfigEntryType;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.EnableSignedPush;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.TransferConfig;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class PutConfig implements RestModifyView<ProjectResource, ConfigInput> {
  private static final Logger log = LoggerFactory.getLogger(PutConfig.class);

  private final boolean serverEnableSignedPush;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final ProjectCache projectCache;
  private final ProjectState.Factory projectStateFactory;
  private final TransferConfig config;
  private final DynamicMap<ProjectConfigEntry> pluginConfigEntries;
  private final PluginConfigFactory cfgFactory;
  private final AllProjectsName allProjects;
  private final DynamicMap<RestView<ProjectResource>> views;
  private final Provider<CurrentUser> user;

  @Inject
  PutConfig(
      @EnableSignedPush boolean serverEnableSignedPush,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      ProjectCache projectCache,
      ProjectState.Factory projectStateFactory,
      TransferConfig config,
      DynamicMap<ProjectConfigEntry> pluginConfigEntries,
      PluginConfigFactory cfgFactory,
      AllProjectsName allProjects,
      DynamicMap<RestView<ProjectResource>> views,
      Provider<CurrentUser> user) {
    this.serverEnableSignedPush = serverEnableSignedPush;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectCache = projectCache;
    this.projectStateFactory = projectStateFactory;
    this.config = config;
    this.pluginConfigEntries = pluginConfigEntries;
    this.cfgFactory = cfgFactory;
    this.allProjects = allProjects;
    this.views = views;
    this.user = user;
  }

  @Override
  public ConfigInfo apply(ProjectResource rsrc, ConfigInput input)
      throws ResourceNotFoundException, BadRequestException, ResourceConflictException {
    if (!rsrc.getControl().isOwner()) {
      throw new ResourceNotFoundException(rsrc.getName());
    }
    return apply(rsrc.getControl(), input);
  }

  public ConfigInfo apply(ProjectControl ctrl, ConfigInput input)
      throws ResourceNotFoundException, BadRequestException, ResourceConflictException {
    Project.NameKey projectName = ctrl.getProject().getNameKey();
    if (input == null) {
      throw new BadRequestException("config is required");
    }

    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(projectName)) {
      ProjectConfig projectConfig = ProjectConfig.read(md);
      Project p = projectConfig.getProject();

      p.setDescription(Strings.emptyToNull(input.description));

      if (input.useContributorAgreements != null) {
        p.setUseContributorAgreements(input.useContributorAgreements);
      }
      if (input.useContentMerge != null) {
        p.setUseContentMerge(input.useContentMerge);
      }
      if (input.useSignedOffBy != null) {
        p.setUseSignedOffBy(input.useSignedOffBy);
      }

      if (input.createNewChangeForAllNotInTarget != null) {
        p.setCreateNewChangeForAllNotInTarget(input.createNewChangeForAllNotInTarget);
      }

      if (input.requireChangeId != null) {
        p.setRequireChangeID(input.requireChangeId);
      }

      if (serverEnableSignedPush) {
        if (input.enableSignedPush != null) {
          p.setEnableSignedPush(input.enableSignedPush);
        }
        if (input.requireSignedPush != null) {
          p.setRequireSignedPush(input.requireSignedPush);
        }
      }

      if (input.rejectImplicitMerges != null) {
        p.setRejectImplicitMerges(input.rejectImplicitMerges);
      }

      if (input.maxObjectSizeLimit != null) {
        p.setMaxObjectSizeLimit(input.maxObjectSizeLimit);
      }

      if (input.submitType != null) {
        p.setSubmitType(input.submitType);
      }

      if (input.state != null) {
        p.setState(input.state);
      }

      if (input.pluginConfigValues != null) {
        setPluginConfigValues(ctrl.getProjectState(), projectConfig, input.pluginConfigValues);
      }

      md.setMessage("Modified project settings\n");
      try {
        projectConfig.commit(md);
        projectCache.evict(projectConfig.getProject());
        md.getRepository().setGitwebDescription(p.getDescription());
      } catch (IOException e) {
        if (e.getCause() instanceof ConfigInvalidException) {
          throw new ResourceConflictException(
              "Cannot update " + projectName + ": " + e.getCause().getMessage());
        }
        log.warn(String.format("Failed to update config of project %s.", projectName), e);
        throw new ResourceConflictException("Cannot update " + projectName);
      }

      ProjectState state = projectStateFactory.create(projectConfig);
      return new ConfigInfoImpl(
          serverEnableSignedPush,
          state.controlFor(user.get()),
          config,
          pluginConfigEntries,
          cfgFactory,
          allProjects,
          views);
    } catch (RepositoryNotFoundException notFound) {
      throw new ResourceNotFoundException(projectName.get());
    } catch (ConfigInvalidException err) {
      throw new ResourceConflictException("Cannot read project " + projectName, err);
    } catch (IOException err) {
      throw new ResourceConflictException("Cannot update project " + projectName, err);
    }
  }

  private void setPluginConfigValues(
      ProjectState projectState,
      ProjectConfig projectConfig,
      Map<String, Map<String, ConfigValue>> pluginConfigValues)
      throws BadRequestException {
    for (Entry<String, Map<String, ConfigValue>> e : pluginConfigValues.entrySet()) {
      String pluginName = e.getKey();
      PluginConfig cfg = projectConfig.getPluginConfig(pluginName);
      for (Entry<String, ConfigValue> v : e.getValue().entrySet()) {
        ProjectConfigEntry projectConfigEntry = pluginConfigEntries.get(pluginName, v.getKey());
        if (projectConfigEntry != null) {
          if (!isValidParameterName(v.getKey())) {
            log.warn(
                String.format(
                    "Parameter name '%s' must match '^[a-zA-Z0-9]+[a-zA-Z0-9-]*$'", v.getKey()));
            continue;
          }
          String oldValue = cfg.getString(v.getKey());
          String value = v.getValue().value;
          if (projectConfigEntry.getType() == ProjectConfigEntryType.ARRAY) {
            List<String> l = Arrays.asList(cfg.getStringList(v.getKey()));
            oldValue = Joiner.on("\n").join(l);
            value = Joiner.on("\n").join(v.getValue().values);
          }
          if (Strings.emptyToNull(value) != null) {
            if (!value.equals(oldValue)) {
              validateProjectConfigEntryIsEditable(
                  projectConfigEntry, projectState, v.getKey(), pluginName);
              v.setValue(projectConfigEntry.preUpdate(v.getValue()));
              value = v.getValue().value;
              try {
                switch (projectConfigEntry.getType()) {
                  case BOOLEAN:
                    boolean newBooleanValue = Boolean.parseBoolean(value);
                    cfg.setBoolean(v.getKey(), newBooleanValue);
                    break;
                  case INT:
                    int newIntValue = Integer.parseInt(value);
                    cfg.setInt(v.getKey(), newIntValue);
                    break;
                  case LONG:
                    long newLongValue = Long.parseLong(value);
                    cfg.setLong(v.getKey(), newLongValue);
                    break;
                  case LIST:
                    if (!projectConfigEntry.getPermittedValues().contains(value)) {
                      throw new BadRequestException(
                          String.format(
                              "The value '%s' is not permitted for parameter '%s' of plugin '"
                                  + pluginName
                                  + "'",
                              value,
                              v.getKey()));
                    }
                    // $FALL-THROUGH$
                  case STRING:
                    cfg.setString(v.getKey(), value);
                    break;
                  case ARRAY:
                    cfg.setStringList(v.getKey(), v.getValue().values);
                    break;
                  default:
                    log.warn(
                        String.format(
                            "The type '%s' of parameter '%s' is not supported.",
                            projectConfigEntry.getType().name(), v.getKey()));
                }
              } catch (NumberFormatException ex) {
                throw new BadRequestException(
                    String.format(
                        "The value '%s' of config parameter '%s' of plugin '%s' is invalid: %s",
                        v.getValue(), v.getKey(), pluginName, ex.getMessage()));
              }
            }
          } else {
            if (oldValue != null) {
              validateProjectConfigEntryIsEditable(
                  projectConfigEntry, projectState, v.getKey(), pluginName);
              cfg.unset(v.getKey());
            }
          }
        } else {
          throw new BadRequestException(
              String.format(
                  "The config parameter '%s' of plugin '%s' does not exist.",
                  v.getKey(), pluginName));
        }
      }
    }
  }

  private static void validateProjectConfigEntryIsEditable(
      ProjectConfigEntry projectConfigEntry,
      ProjectState projectState,
      String parameterName,
      String pluginName)
      throws BadRequestException {
    if (!projectConfigEntry.isEditable(projectState)) {
      throw new BadRequestException(
          String.format(
              "Not allowed to set parameter '%s' of plugin '%s' on project '%s'.",
              parameterName, pluginName, projectState.getProject().getName()));
    }
  }

  private static boolean isValidParameterName(String name) {
    return CharMatcher.javaLetterOrDigit().or(CharMatcher.is('-')).matchesAllOf(name)
        && !name.startsWith("-");
  }
}
