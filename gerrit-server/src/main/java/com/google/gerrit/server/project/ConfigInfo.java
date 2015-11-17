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

import com.google.common.base.Strings;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.common.ActionInfo;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.DynamicMap.Entry;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.extensions.webui.UiAction;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.PluginConfig;
import com.google.gerrit.server.config.PluginConfigFactory;
import com.google.gerrit.server.config.ProjectConfigEntry;
import com.google.gerrit.server.extensions.webui.UiActions;
import com.google.gerrit.server.git.TransferConfig;
import com.google.inject.util.Providers;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

public class ConfigInfo {
  public String description;
  public InheritedBooleanInfo useContributorAgreements;
  public InheritedBooleanInfo useContentMerge;
  public InheritedBooleanInfo useSignedOffBy;
  public InheritedBooleanInfo createNewChangeForAllNotInTarget;
  public InheritedBooleanInfo requireChangeId;
  public InheritedBooleanInfo enableSignedPush;
  public InheritedBooleanInfo requireSignedPush;
  public MaxObjectSizeLimitInfo maxObjectSizeLimit;
  public InheritedBooleanInfo checkImplicitMerges;
  public SubmitType submitType;
  public com.google.gerrit.extensions.client.ProjectState state;
  public Map<String, Map<String, ConfigParameterInfo>> pluginConfig;
  public Map<String, ActionInfo> actions;

  public Map<String, CommentLinkInfo> commentlinks;
  public ThemeInfo theme;

  public ConfigInfo(boolean serverEnableSignedPush,
      ProjectControl control,
      TransferConfig config,
      DynamicMap<ProjectConfigEntry> pluginConfigEntries,
      PluginConfigFactory cfgFactory,
      AllProjectsNameProvider allProjects,
      DynamicMap<RestView<ProjectResource>> views) {
    ProjectState projectState = control.getProjectState();
    Project p = control.getProject();
    this.description = Strings.emptyToNull(p.getDescription());

    InheritedBooleanInfo useContributorAgreements =
        new InheritedBooleanInfo();
    InheritedBooleanInfo useSignedOffBy = new InheritedBooleanInfo();
    InheritedBooleanInfo useContentMerge = new InheritedBooleanInfo();
    InheritedBooleanInfo requireChangeId = new InheritedBooleanInfo();
    InheritedBooleanInfo createNewChangeForAllNotInTarget =
        new InheritedBooleanInfo();
    InheritedBooleanInfo enableSignedPush = new InheritedBooleanInfo();
    InheritedBooleanInfo requireSignedPush = new InheritedBooleanInfo();
    InheritedBooleanInfo checkImplicitMerges = new InheritedBooleanInfo();

    useContributorAgreements.value = projectState.isUseContributorAgreements();
    useSignedOffBy.value = projectState.isUseSignedOffBy();
    useContentMerge.value = projectState.isUseContentMerge();
    requireChangeId.value = projectState.isRequireChangeID();
    createNewChangeForAllNotInTarget.value =
        projectState.isCreateNewChangeForAllNotInTarget();

    useContributorAgreements.configuredValue =
        p.getUseContributorAgreements();
    useSignedOffBy.configuredValue = p.getUseSignedOffBy();
    useContentMerge.configuredValue = p.getUseContentMerge();
    requireChangeId.configuredValue = p.getRequireChangeID();
    createNewChangeForAllNotInTarget.configuredValue =
        p.getCreateNewChangeForAllNotInTarget();
    enableSignedPush.configuredValue = p.getEnableSignedPush();
    requireSignedPush.configuredValue = p.getRequireSignedPush();
    checkImplicitMerges.configuredValue = p.getCheckImplicitMerges();

    ProjectState parentState = Iterables.getFirst(projectState
        .parents(), null);
    if (parentState != null) {
      useContributorAgreements.inheritedValue =
          parentState.isUseContributorAgreements();
      useSignedOffBy.inheritedValue = parentState.isUseSignedOffBy();
      useContentMerge.inheritedValue = parentState.isUseContentMerge();
      requireChangeId.inheritedValue = parentState.isRequireChangeID();
      createNewChangeForAllNotInTarget.inheritedValue =
          parentState.isCreateNewChangeForAllNotInTarget();
      enableSignedPush.inheritedValue = projectState.isEnableSignedPush();
      requireSignedPush.inheritedValue = projectState.isRequireSignedPush();
      checkImplicitMerges.inheritedValue = projectState.isCheckImplicitMerges();
    }

    this.useContributorAgreements = useContributorAgreements;
    this.useSignedOffBy = useSignedOffBy;
    this.useContentMerge = useContentMerge;
    this.requireChangeId = requireChangeId;
    this.createNewChangeForAllNotInTarget = createNewChangeForAllNotInTarget;
    if (serverEnableSignedPush) {
      this.enableSignedPush = enableSignedPush;
      this.requireSignedPush = requireSignedPush;
    }

    MaxObjectSizeLimitInfo maxObjectSizeLimit = new MaxObjectSizeLimitInfo();
    maxObjectSizeLimit.value =
        config.getEffectiveMaxObjectSizeLimit(projectState) == config
            .getMaxObjectSizeLimit() ? config
            .getFormattedMaxObjectSizeLimit() : p.getMaxObjectSizeLimit();
    maxObjectSizeLimit.configuredValue = p.getMaxObjectSizeLimit();
    maxObjectSizeLimit.inheritedValue =
        config.getFormattedMaxObjectSizeLimit();
    this.maxObjectSizeLimit = maxObjectSizeLimit;

    this.submitType = p.getSubmitType();
    this.state = p.getState() != com.google.gerrit.extensions.client.ProjectState.ACTIVE ? p.getState() : null;

    this.commentlinks = Maps.newLinkedHashMap();
    for (CommentLinkInfo cl : projectState.getCommentLinks()) {
      this.commentlinks.put(cl.name, cl);
    }

    pluginConfig =
        getPluginConfig(control.getProjectState(), pluginConfigEntries,
            cfgFactory, allProjects);

    actions = Maps.newTreeMap();
    for (UiAction.Description d : UiActions.from(
        views, new ProjectResource(control),
        Providers.of(control.getUser()))) {
      actions.put(d.getId(), new ActionInfo(d));
    }
    this.theme = projectState.getTheme();
  }

  private Map<String, Map<String, ConfigParameterInfo>> getPluginConfig(
      ProjectState project, DynamicMap<ProjectConfigEntry> pluginConfigEntries,
      PluginConfigFactory cfgFactory, AllProjectsNameProvider allProjects) {
    TreeMap<String, Map<String, ConfigParameterInfo>> pluginConfig = new TreeMap<>();
    for (Entry<ProjectConfigEntry> e : pluginConfigEntries) {
      ProjectConfigEntry configEntry = e.getProvider().get();
      PluginConfig cfg =
          cfgFactory.getFromProjectConfig(project, e.getPluginName());
      String configuredValue = cfg.getString(e.getExportName());
      ConfigParameterInfo p = new ConfigParameterInfo();
      p.displayName = configEntry.getDisplayName();
      p.description = configEntry.getDescription();
      p.warning = configEntry.getWarning(project);
      p.type = configEntry.getType();
      p.permittedValues = configEntry.getPermittedValues();
      p.editable = configEntry.isEditable(project) ? true : null;
      if (configEntry.isInheritable()
          && !allProjects.get().equals(project.getProject().getNameKey())) {
        PluginConfig cfgWithInheritance =
            cfgFactory.getFromProjectConfigWithInheritance(project,
                e.getPluginName());
        p.inheritable = true;
        p.value = configEntry.onRead(project,
            cfgWithInheritance.getString(e.getExportName(),
                configEntry.getDefaultValue()));
        p.configuredValue = configuredValue;
        p.inheritedValue = getInheritedValue(project, cfgFactory, e);
      } else {
        if (configEntry.getType() == ProjectConfigEntry.Type.ARRAY) {
          p.values = configEntry.onRead(project,
              Arrays.asList(cfg.getStringList(e.getExportName())));
        } else {
          p.value = configEntry.onRead(project, configuredValue != null
              ? configuredValue
              : configEntry.getDefaultValue());
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

  private String getInheritedValue(ProjectState project,
      PluginConfigFactory cfgFactory, Entry<ProjectConfigEntry> e) {
    ProjectConfigEntry configEntry = e.getProvider().get();
    ProjectState parent = Iterables.getFirst(project.parents(), null);
    String inheritedValue = configEntry.getDefaultValue();
    if (parent != null) {
      PluginConfig parentCfgWithInheritance =
          cfgFactory.getFromProjectConfigWithInheritance(parent,
              e.getPluginName());
      inheritedValue =
          parentCfgWithInheritance.getString(e.getExportName(),
              configEntry.getDefaultValue());
    }
    return inheritedValue;
  }

  public static class InheritedBooleanInfo {
    public Boolean value;
    public InheritableBoolean configuredValue;
    public Boolean inheritedValue;
  }

  public static class MaxObjectSizeLimitInfo {
    public String value;
    public String configuredValue;
    public String inheritedValue;
  }

  public static class ConfigParameterInfo {
    public String displayName;
    public String description;
    public String warning;
    public ProjectConfigEntry.Type type;
    public String value;
    public Boolean editable;
    public Boolean inheritable;
    public String configuredValue;
    public String inheritedValue;
    public List<String> permittedValues;
    public List<String> values;
  }
}
