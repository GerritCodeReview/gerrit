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

package com.google.gerrit.server.restapi.project;

import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.api.projects.ConfigValue;
import com.google.gerrit.extensions.api.projects.NotifyConfigInput;
import com.google.gerrit.extensions.api.projects.ProjectConfigEntryType;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.index.query.QueryParseException;
import com.google.gerrit.mail.Address;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.EnableSignedPush;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.account.ProjectWatches.NotifyType;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.extensions.webui.UiActions;
import com.google.gerrit.server.git.NotifyConfig;
import com.google.gerrit.server.git.meta.MetaDataUpdate;
import com.google.gerrit.server.group.GroupResolver;
import com.google.gerrit.server.mail.send.OutgoingEmailValidator;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.BooleanProjectConfigTransformations;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectConfig;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.ProjectState.Factory;
import com.google.gerrit.server.query.change.ChangeQueryBuilder;
import com.google.gerrit.server.restapi.group.GroupJson;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

@Singleton
public class PutConfig implements RestModifyView<ProjectResource, ConfigInput> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private static final Pattern PARAMETER_NAME_PATTERN =
      Pattern.compile("^[a-zA-Z0-9]+[a-zA-Z0-9-]*$");

  private final boolean serverEnableSignedPush;
  private final Provider<MetaDataUpdate.User> metaDataUpdateFactory;
  private final ProjectCache projectCache;
  private final ProjectState.Factory projectStateFactory;
  private final DynamicMap<ProjectConfigEntry> pluginConfigEntries;
  private final PluginConfigFactory cfgFactory;
  private final AllProjectsName allProjects;
  private final UiActions uiActions;
  private final DynamicMap<RestView<ProjectResource>> views;
  private final Provider<CurrentUser> user;
  private final PermissionBackend permissionBackend;
  private final ProjectConfig.Factory projectConfigFactory;
  private final OutgoingEmailValidator outgoingEmailValidator;
  private final ChangeQueryBuilder changeQueryBuilder;
  private final GroupResolver groupResolver;
  private final GroupBackend groupBackend;
  private final GroupJson groupJson;

  @Inject
  PutConfig(
      @EnableSignedPush boolean serverEnableSignedPush,
      Provider<MetaDataUpdate.User> metaDataUpdateFactory,
      ProjectCache projectCache,
      Factory projectStateFactory,
      DynamicMap<ProjectConfigEntry> pluginConfigEntries,
      PluginConfigFactory cfgFactory,
      AllProjectsName allProjects,
      UiActions uiActions,
      DynamicMap<RestView<ProjectResource>> views,
      Provider<CurrentUser> user,
      PermissionBackend permissionBackend,
      ProjectConfig.Factory projectConfigFactory,
      OutgoingEmailValidator outgoingEmailValidator,
      ChangeQueryBuilder changeQueryBuilder,
      GroupResolver groupResolver,
      GroupBackend groupBackend,
      GroupJson groupJson) {
    this.serverEnableSignedPush = serverEnableSignedPush;
    this.metaDataUpdateFactory = metaDataUpdateFactory;
    this.projectCache = projectCache;
    this.projectStateFactory = projectStateFactory;
    this.pluginConfigEntries = pluginConfigEntries;
    this.cfgFactory = cfgFactory;
    this.allProjects = allProjects;
    this.uiActions = uiActions;
    this.views = views;
    this.user = user;
    this.permissionBackend = permissionBackend;
    this.projectConfigFactory = projectConfigFactory;
    this.outgoingEmailValidator = outgoingEmailValidator;
    this.changeQueryBuilder = changeQueryBuilder;
    this.groupResolver = groupResolver;
    this.groupBackend = groupBackend;
    this.groupJson = groupJson;
  }

  @Override
  public Response<ConfigInfo> apply(ProjectResource rsrc, ConfigInput input)
      throws RestApiException, PermissionBackendException {
    permissionBackend
        .currentUser()
        .project(rsrc.getNameKey())
        .check(ProjectPermission.WRITE_CONFIG);
    return Response.ok(apply(rsrc.getProjectState(), input));
  }

  public ConfigInfo apply(ProjectState projectState, ConfigInput input)
      throws ResourceNotFoundException, BadRequestException, ResourceConflictException,
          UnprocessableEntityException {
    Project.NameKey projectName = projectState.getNameKey();
    if (input == null) {
      throw new BadRequestException("config is required");
    }

    try (MetaDataUpdate md = metaDataUpdateFactory.get().create(projectName)) {
      ProjectConfig projectConfig = projectConfigFactory.read(md);
      Project p = projectConfig.getProject();

      p.setDescription(Strings.emptyToNull(input.description));

      for (BooleanProjectConfig cfg : BooleanProjectConfig.values()) {
        InheritableBoolean val = BooleanProjectConfigTransformations.get(cfg, input);
        if (val != null) {
          p.setBooleanConfig(cfg, val);
        }
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
        setPluginConfigValues(projectState, projectConfig, input.pluginConfigValues);
      }
      if (input.notifyConfigUpdates != null) {
        if (input.notifyConfigUpdates.notifyConfigsRemovals != null) {
          Collection<String> notifyConfigsNames = projectConfig.getNotifyConfigNames();
          for (String notifyConfigsName : input.notifyConfigUpdates.notifyConfigsRemovals) {
            if (!notifyConfigsNames.contains(notifyConfigsName)) {
              throw new BadRequestException(
                  String.format(
                      "NotifyConfig %s doesn't exist and could not be removed.",
                      notifyConfigsName));
            }
          }
          input.notifyConfigUpdates.notifyConfigsRemovals.stream()
              .forEach(projectConfig::removeNotifyConfig);
        }
        if (input.notifyConfigUpdates.notifyConfigsAdditions != null) {
          for (NotifyConfigInput notifyConfig : input.notifyConfigUpdates.notifyConfigsAdditions) {
            if (notifyConfig.name == null || notifyConfig.name.trim().equals("")) {
              throw new BadRequestException("Name of the Notify Config must not be empty");
            }
            if (projectConfig.getNotifyConfigNames().contains(notifyConfig.name)) {
              throw new BadRequestException(
                  String.format(
                      "Name of the Notify Config must be unique. %s already exists.",
                      notifyConfig.name));
            }
            if (notifyConfig.emails.stream()
                .anyMatch(email -> outgoingEmailValidator.isValid(email) == false)) {
              throw new BadRequestException(
                  String.format("NotifyConfig %s has an invalid email", notifyConfig.name));
            }
            if (notifyConfig.filter != null && !notifyConfig.filter.equals("*")) {
              try {
                changeQueryBuilder.parse(
                    notifyConfig.filter); // asserts that filter is legal, if not, throws
                // QueryParseException.
              } catch (QueryParseException err) {
                throw new BadRequestException(
                    String.format(
                        "NotifyConfig %s has an invalid filter %s: %s",
                        notifyConfig.name, notifyConfig.filter, err.getMessage()));
              }
            }
            if (notifyConfig.groupIds.isEmpty() && notifyConfig.emails.isEmpty()) {
              throw new BadRequestException(
                  "Configuration should not be empty. Must contain at least one email address or one group.");
            }
            NotifyConfig toInsert = new NotifyConfig();
            toInsert.setName(notifyConfig.name);
            toInsert.setHeader(notifyConfig.header);
            toInsert.setFilter(notifyConfig.filter);
            EnumSet<NotifyType> notifyTypes = EnumSet.noneOf(NotifyType.class);
            if (toBoolean(notifyConfig.notifyAbandonedChanges)) {
              notifyTypes.add(NotifyType.ABANDONED_CHANGES);
            }
            if (toBoolean(notifyConfig.notifyAllComments)) {
              notifyTypes.add(NotifyType.ALL_COMMENTS);
            }
            if (toBoolean(notifyConfig.notifyNewChanges)) {
              notifyTypes.add(NotifyType.NEW_CHANGES);
            }
            if (toBoolean(notifyConfig.notifyNewPatchSets)) {
              notifyTypes.add(NotifyType.NEW_PATCHSETS);
            }
            if (toBoolean(notifyConfig.notifySubmittedChanges)) {
              notifyTypes.add(NotifyType.SUBMITTED_CHANGES);
            }
            toInsert.setTypes(notifyTypes);
            for (String email : notifyConfig.emails) {
              toInsert.addEmail(new Address(notifyConfig.name, email));
            }
            for (String id : notifyConfig.groupIds) {
              try {
                GroupDescription.Basic group = groupResolver.parse(id);
                toInsert.addEmail(new GroupReference(group.getGroupUUID(), group.getName()));
              } catch (UnprocessableEntityException ex) {
                throw new UnprocessableEntityException(
                    ex.getMessage()
                        + ". NotifyConfig with the non existing group is "
                        + notifyConfig.name);
              }
            }
            projectConfig.putNotifyConfig(toInsert.getName(), toInsert);
          }
        }
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
        logger.atWarning().withCause(e).log("Failed to update config of project %s.", projectName);
        throw new ResourceConflictException("Cannot update " + projectName);
      }
      ProjectState state = projectStateFactory.create(projectConfigFactory.read(md));
      return new ConfigInfoImpl(
          serverEnableSignedPush,
          state,
          user.get(),
          pluginConfigEntries,
          cfgFactory,
          allProjects,
          uiActions,
          views,
          groupBackend,
          groupJson);
    } catch (RepositoryNotFoundException notFound) {
      throw new ResourceNotFoundException(projectName.get());
    } catch (ConfigInvalidException err) {
      throw new ResourceConflictException("Cannot read project " + projectName, err);
    } catch (IOException err) {
      throw new ResourceConflictException("Cannot update project " + projectName, err);
    }
  }

  private boolean toBoolean(Boolean b) {
    return b == null ? false : b;
  }

  private void setPluginConfigValues(
      ProjectState projectState,
      ProjectConfig projectConfig,
      Map<String, Map<String, ConfigValue>> pluginConfigValues)
      throws BadRequestException {
    for (Map.Entry<String, Map<String, ConfigValue>> e : pluginConfigValues.entrySet()) {
      String pluginName = e.getKey();
      PluginConfig cfg = projectConfig.getPluginConfig(pluginName);
      for (Map.Entry<String, ConfigValue> v : e.getValue().entrySet()) {
        ProjectConfigEntry projectConfigEntry = pluginConfigEntries.get(pluginName, v.getKey());
        if (projectConfigEntry != null) {
          if (!PARAMETER_NAME_PATTERN.matcher(v.getKey()).matches()) {
            // TODO check why we have this restriction
            logger.atWarning().log(
                "Parameter name '%s' must match '%s'",
                v.getKey(), PARAMETER_NAME_PATTERN.pattern());
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
                    logger.atWarning().log(
                        "The type '%s' of parameter '%s' is not supported.",
                        projectConfigEntry.getType().name(), v.getKey());
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
              parameterName, pluginName, projectState.getName()));
    }
  }
}
