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

import com.google.common.base.MoreObjects;
import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.gerrit.common.data.GroupDescription;
import com.google.gerrit.extensions.api.projects.CommentLinkInfo;
import com.google.gerrit.extensions.api.projects.ConfigInfo;
import com.google.gerrit.extensions.api.projects.ProjectConfigEntryType;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.common.GroupInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicMap.Entry;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroup.UUID;
import com.google.gerrit.reviewdb.client.BooleanProjectConfig;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.account.GroupBackend;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.extensions.webui.UiActions;
import com.google.gerrit.server.git.TransferConfig;
import com.google.gerrit.server.project.BooleanProjectConfigTransformations;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

public class ConfigInfoImpl extends ConfigInfo {
  @SuppressWarnings("deprecation")
  public ConfigInfoImpl(
      boolean serverEnableSignedPush,
      ProjectState projectState,
      CurrentUser user,
      TransferConfig config,
      DynamicMap<ProjectConfigEntry> pluginConfigEntries,
      PluginConfigFactory cfgFactory,
      AllProjectsName allProjects,
      UiActions uiActions,
      DynamicMap<RestView<ProjectResource>> views,
      GroupBackend groupBackend) {
    Project p = projectState.getProject();
    this.description = Strings.emptyToNull(p.getDescription());

    ProjectState parentState = Iterables.getFirst(projectState.parents(), null);
    for (BooleanProjectConfig cfg : BooleanProjectConfig.values()) {
      InheritedBooleanInfo info = new InheritedBooleanInfo();
      info.configuredValue = p.getBooleanConfig(cfg);
      if (parentState != null) {
        info.inheritedValue = parentState.is(cfg);
      }
      BooleanProjectConfigTransformations.set(cfg, this, info);
    }

    if (!serverEnableSignedPush) {
      this.enableSignedPush = null;
      this.requireSignedPush = null;
    }

    MaxObjectSizeLimitInfo maxObjectSizeLimit = new MaxObjectSizeLimitInfo();
    maxObjectSizeLimit.value =
        config.getEffectiveMaxObjectSizeLimit(projectState) == config.getMaxObjectSizeLimit()
            ? config.getFormattedMaxObjectSizeLimit()
            : p.getMaxObjectSizeLimit();
    maxObjectSizeLimit.configuredValue = p.getMaxObjectSizeLimit();
    maxObjectSizeLimit.inheritedValue = config.getFormattedMaxObjectSizeLimit();
    this.maxObjectSizeLimit = maxObjectSizeLimit;

    this.defaultSubmitType = new SubmitTypeInfo();
    this.defaultSubmitType.value = projectState.getSubmitType();
    this.defaultSubmitType.configuredValue =
        MoreObjects.firstNonNull(
            projectState.getConfig().getProject().getConfiguredSubmitType(),
            Project.DEFAULT_SUBMIT_TYPE);
    ProjectState parent =
        projectState.isAllProjects() ? projectState : projectState.parents().get(0);
    this.defaultSubmitType.inheritedValue = parent.getSubmitType();

    this.submitType = this.defaultSubmitType.value;

    this.state =
        p.getState() != com.google.gerrit.extensions.client.ProjectState.ACTIVE
            ? p.getState()
            : null;

    this.commentlinks = new LinkedHashMap<>();
    for (CommentLinkInfo cl : projectState.getCommentLinks()) {
      this.commentlinks.put(cl.name, cl);
    }

    pluginConfig = getPluginConfig(projectState, pluginConfigEntries, cfgFactory, allProjects);

    actions = new TreeMap<>();
    for (UiAction.Description d : uiActions.from(views, new ProjectResource(projectState, user))) {
      actions.put(d.getId(), new ActionInfo(d));
    }
    this.theme = projectState.getTheme();

    this.extensionPanelNames = projectState.getConfig().getExtensionPanelSections();

    owners = new ArrayList<>();
    Set<UUID> owners = projectState.getOwners();
    for (AccountGroup.UUID owner : owners) {
      GroupInfo group = loadGroup(groupBackend, owner);
      if (group != null) {
        this.owners.add(group);
      }
    }
  }

  private GroupInfo loadGroup(GroupBackend groupBackend, AccountGroup.UUID id) {
    GroupDescription.Basic basic = groupBackend.get(id);
    if (basic == null) {
      return null;
    }

    GroupInfo group = new GroupInfo();
    // The UI only needs name + URL, so don't populate other fields to avoid leaking data
    // about groups invisible to the user.
    group.name = basic.getName();
    group.url = basic.getUrl();
    return group;
  }

  private Map<String, Map<String, ConfigParameterInfo>> getPluginConfig(
      ProjectState project,
      DynamicMap<ProjectConfigEntry> pluginConfigEntries,
      PluginConfigFactory cfgFactory,
      AllProjectsName allProjects) {
    TreeMap<String, Map<String, ConfigParameterInfo>> pluginConfig = new TreeMap<>();
    for (Entry<ProjectConfigEntry> e : pluginConfigEntries) {
      ProjectConfigEntry configEntry = e.getProvider().get();
      PluginConfig cfg = cfgFactory.getFromProjectConfig(project, e.getPluginName());
      String configuredValue = cfg.getString(e.getExportName());
      ConfigParameterInfo p = new ConfigParameterInfo();
      p.displayName = configEntry.getDisplayName();
      p.description = configEntry.getDescription();
      p.warning = configEntry.getWarning(project);
      p.type = configEntry.getType();
      p.permittedValues = configEntry.getPermittedValues();
      p.editable = configEntry.isEditable(project) ? true : null;
      if (configEntry.isInheritable() && !allProjects.equals(project.getNameKey())) {
        PluginConfig cfgWithInheritance =
            cfgFactory.getFromProjectConfigWithInheritance(project, e.getPluginName());
        p.inheritable = true;
        p.value =
            configEntry.onRead(
                project,
                cfgWithInheritance.getString(e.getExportName(), configEntry.getDefaultValue()));
        p.configuredValue = configuredValue;
        p.inheritedValue = getInheritedValue(project, cfgFactory, e);
      } else {
        if (configEntry.getType() == ProjectConfigEntryType.ARRAY) {
          p.values =
              configEntry.onRead(project, Arrays.asList(cfg.getStringList(e.getExportName())));
        } else {
          p.value =
              configEntry.onRead(
                  project,
                  configuredValue != null ? configuredValue : configEntry.getDefaultValue());
        }
      }
      Map<String, ConfigParameterInfo> pc = pluginConfig.get(e.getPluginName());
      if (pc == null) {
        pc = new TreeMap<>();
        pluginConfig.put(e.getPluginName(), pc);
      }
      pc.put(e.getExportName(), p);
    }
    return !pluginConfig.isEmpty() ? pluginConfig : null;
  }

  private String getInheritedValue(
      ProjectState project, PluginConfigFactory cfgFactory, Entry<ProjectConfigEntry> e) {
    ProjectConfigEntry configEntry = e.getProvider().get();
    ProjectState parent = Iterables.getFirst(project.parents(), null);
    String inheritedValue = configEntry.getDefaultValue();
    if (parent != null) {
      PluginConfig parentCfgWithInheritance =
          cfgFactory.getFromProjectConfigWithInheritance(parent, e.getPluginName());
      inheritedValue =
          parentCfgWithInheritance.getString(e.getExportName(), configEntry.getDefaultValue());
    }
    return inheritedValue;
  }
}
