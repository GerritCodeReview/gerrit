// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import static com.google.gerrit.server.i18n.I18n.getText;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.entities.AccountGroup;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.projects.ConfigValue;
import com.google.gerrit.extensions.api.projects.ProjectInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.project.SuggestParentCandidates;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

/** Create a new project. * */
@RequiresCapability(GlobalCapability.CREATE_PROJECT)
@CommandMetaData(
    name = "create-project",
    description = "Create a new project and associated Git repository")
final class CreateProjectCommand extends SshCommand {
  @Option(
      name = "--suggest-parents",
      aliases = {"-S"},
      usage =
          "suggest parent candidates, "
              + "if this option is used all other options and arguments are ignored")
  private boolean suggestParent;

  @Option(
      name = "--owner",
      aliases = {"-o"},
      usage = "owner(s) of project")
  private List<AccountGroup.UUID> ownerIds;

  @Option(
      name = "--parent",
      aliases = {"-p"},
      metaVar = "NAME",
      usage = "parent project")
  private ProjectState newParent;

  @Option(name = "--permissions-only", usage = "create project for use only as parent")
  private boolean permissionsOnly;

  @Option(
      name = "--description",
      aliases = {"-d"},
      metaVar = "DESCRIPTION",
      usage = "description of project")
  private String projectDescription = "";

  @Option(
      name = "--submit-type",
      aliases = {"-t"},
      usage = "project submit type")
  private SubmitType submitType;

  @Option(name = "--contributor-agreements", usage = "if contributor agreement is required")
  private InheritableBoolean contributorAgreements = InheritableBoolean.INHERIT;

  @Option(name = "--signed-off-by", usage = "if signed-off-by is required")
  private InheritableBoolean signedOffBy = InheritableBoolean.INHERIT;

  @Option(name = "--content-merge", usage = "allow automatic conflict resolving within files")
  private InheritableBoolean contentMerge = InheritableBoolean.INHERIT;

  @Option(name = "--change-id", usage = "if change-id is required")
  private InheritableBoolean requireChangeID = InheritableBoolean.INHERIT;

  @Option(name = "--reject-empty-commit", usage = "if empty commits should be rejected on submit")
  private InheritableBoolean rejectEmptyCommit = InheritableBoolean.INHERIT;

  @Option(
      name = "--new-change-for-all-not-in-target",
      usage = "if a new change will be created for every commit not in target branch")
  private InheritableBoolean createNewChangeForAllNotInTarget = InheritableBoolean.INHERIT;

  @Option(
      name = "--use-contributor-agreements",
      aliases = {"--ca"},
      usage = "if contributor agreement is required")
  void setUseContributorArgreements(@SuppressWarnings("unused") boolean on) {
    contributorAgreements = InheritableBoolean.TRUE;
  }

  @Option(
      name = "--use-signed-off-by",
      aliases = {"--so"},
      usage = "if signed-off-by is required")
  void setUseSignedOffBy(@SuppressWarnings("unused") boolean on) {
    signedOffBy = InheritableBoolean.TRUE;
  }

  @Option(name = "--use-content-merge", usage = "allow automatic conflict resolving within files")
  void setUseContentMerge(@SuppressWarnings("unused") boolean on) {
    contentMerge = InheritableBoolean.TRUE;
  }

  @Option(
      name = "--require-change-id",
      aliases = {"--id"},
      usage = "if change-id is required")
  void setRequireChangeId(@SuppressWarnings("unused") boolean on) {
    requireChangeID = InheritableBoolean.TRUE;
  }

  @Option(
      name = "--create-new-change-for-all-not-in-target",
      aliases = {"--ncfa"},
      usage = "if a new change will be created for every commit not in target branch")
  void setNewChangeForAllNotInTarget(@SuppressWarnings("unused") boolean on) {
    createNewChangeForAllNotInTarget = InheritableBoolean.TRUE;
  }

  @Option(
      name = "--branch",
      aliases = {"-b"},
      metaVar = "BRANCH",
      usage = "initial branch name\n(default: master)")
  private List<String> branch;

  @Option(name = "--empty-commit", usage = "to create initial empty commit")
  private boolean createEmptyCommit;

  @Option(name = "--max-object-size-limit", usage = "max Git object size for this project")
  private String maxObjectSizeLimit;

  @Option(
      name = "--plugin-config",
      usage = "plugin configuration parameter with format '<plugin-name>.<parameter-name>=<value>'")
  private List<String> pluginConfigValues;

  @Argument(index = 0, metaVar = "NAME", usage = "name of project to be created")
  private String projectName;

  @Inject private GerritApi gApi;

  @Inject private SuggestParentCandidates suggestParentCandidates;

  @Override
  protected void run() throws Failure {
    try {
      if (!suggestParent) {
        if (projectName == null) {
          throw die("Project name is required.");
        }

        ProjectInput input = new ProjectInput();
        input.name = projectName;
        if (ownerIds != null) {
          input.owners = Lists.transform(ownerIds, AccountGroup.UUID::get);
        }
        if (newParent != null) {
          input.parent = newParent.getName();
        }
        input.permissionsOnly = permissionsOnly;
        input.description = projectDescription;
        input.submitType = submitType;
        input.useContributorAgreements = contributorAgreements;
        input.useSignedOffBy = signedOffBy;
        input.useContentMerge = contentMerge;
        input.requireChangeId = requireChangeID;
        input.createNewChangeForAllNotInTarget = createNewChangeForAllNotInTarget;
        input.branches = branch;
        input.createEmptyCommit = createEmptyCommit;
        input.maxObjectSizeLimit = maxObjectSizeLimit;
        input.rejectEmptyCommit = rejectEmptyCommit;
        if (pluginConfigValues != null) {
          input.pluginConfigValues = parsePluginConfigValues(pluginConfigValues);
        }

        gApi.projects().create(input);
      } else {
        for (Project.NameKey parent : suggestParentCandidates.getNameKeys()) {
          stdout.print(parent.get() + '\n');
        }
      }
    } catch (RestApiException err) {
      throw die(err);
    } catch (PermissionBackendException err) {
      throw new Failure(1, getText("sshd.commands.common.permissions.unavailable"), err);
    }
  }

  @VisibleForTesting
  Map<String, Map<String, ConfigValue>> parsePluginConfigValues(List<String> pluginConfigValues)
      throws UnloggedFailure {
    Map<String, Map<String, ConfigValue>> m = new HashMap<>();
    for (String pluginConfigValue : pluginConfigValues) {
      List<String> s = Splitter.on('=').splitToList(pluginConfigValue);
      List<String> s2 = Splitter.on('.').splitToList(s.get(0));
      if (s.size() != 2 || s2.size() != 2) {
        throw die(
            getText("sshd.command.create.project.invalid.plugin.config.value", pluginConfigValue));
      }

      ConfigValue value = new ConfigValue();
      String v = s.get(1);
      if (v.contains(",")) {
        value.values = Splitter.on(",").splitToList(v);
      } else {
        value.value = v;
      }
      String pluginName = s2.get(0);
      String paramName = s2.get(1);
      Map<String, ConfigValue> l = m.get(pluginName);
      if (l == null) {
        l = new HashMap<>();
        m.put(pluginName, l);
      }
      l.put(paramName, value);
    }
    return m;
  }
}
