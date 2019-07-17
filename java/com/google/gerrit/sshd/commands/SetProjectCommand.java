// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.base.Strings;
import com.google.gerrit.extensions.api.projects.ConfigInput;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.project.PutConfig;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@CommandMetaData(name = "set-project", description = "Change a project's settings")
final class SetProjectCommand extends SshCommand {
  @Argument(index = 0, required = true, metaVar = "NAME", usage = "name of the project")
  private ProjectState projectState;

  @Option(
      name = "--description",
      aliases = {"-d"},
      metaVar = "DESCRIPTION",
      usage = "description of project")
  private String projectDescription;

  @Option(
      name = "--submit-type",
      aliases = {"-t"},
      usage = "project submit type\n(default: MERGE_IF_NECESSARY)")
  private SubmitType submitType;

  @Option(name = "--contributor-agreements", usage = "if contributor agreement is required")
  private InheritableBoolean contributorAgreements;

  @Option(name = "--signed-off-by", usage = "if signed-off-by is required")
  private InheritableBoolean signedOffBy;

  @Option(name = "--content-merge", usage = "allow automatic conflict resolving within files")
  private InheritableBoolean contentMerge;

  @Option(
      name = "--use-contributor-agreements",
      aliases = {"--ca"},
      usage = "if contributor agreement is required")
  void setUseContributorArgreements(@SuppressWarnings("unused") boolean on) {
    contributorAgreements = InheritableBoolean.TRUE;
  }

  @Option(
      name = "--no-contributor-agreements",
      aliases = {"--nca"},
      usage = "if contributor agreement is not required")
  void setNoContributorArgreements(@SuppressWarnings("unused") boolean on) {
    contributorAgreements = InheritableBoolean.FALSE;
  }

  @Option(
      name = "--use-signed-off-by",
      aliases = {"--so"},
      usage = "if signed-off-by is required")
  void setUseSignedOffBy(@SuppressWarnings("unused") boolean on) {
    signedOffBy = InheritableBoolean.TRUE;
  }

  @Option(
      name = "--no-signed-off-by",
      aliases = {"--nso"},
      usage = "if signed-off-by is not required")
  void setNoSignedOffBy(@SuppressWarnings("unused") boolean on) {
    signedOffBy = InheritableBoolean.FALSE;
  }

  @Option(name = "--use-content-merge", usage = "allow automatic conflict resolving within files")
  void setUseContentMerge(@SuppressWarnings("unused") boolean on) {
    contentMerge = InheritableBoolean.TRUE;
  }

  @Option(
      name = "--no-content-merge",
      usage = "don't allow automatic conflict resolving within files")
  void setNoContentMerge(@SuppressWarnings("unused") boolean on) {
    contentMerge = InheritableBoolean.FALSE;
  }

  @Option(
      name = "--project-state",
      aliases = {"--ps"},
      usage = "project's visibility state")
  private com.google.gerrit.extensions.client.ProjectState state;

  @Option(name = "--max-object-size-limit", usage = "max Git object size for this project")
  private String maxObjectSizeLimit;

  @Inject private PutConfig putConfig;

  @Override
  protected void run() throws Failure {
    ConfigInput configInput = new ConfigInput();
    configInput.submitType = submitType;
    configInput.useContentMerge = contentMerge;
    configInput.useContributorAgreements = contributorAgreements;
    configInput.useSignedOffBy = signedOffBy;
    configInput.state = state;
    configInput.maxObjectSizeLimit = maxObjectSizeLimit;
    // Description is different to other parameters, null won't result in
    // keeping the existing description, it would delete it.
    if (Strings.emptyToNull(projectDescription) != null) {
      configInput.description = projectDescription;
    } else {
      configInput.description = projectState.getProject().getDescription();
    }

    try {
      putConfig.apply(new ProjectResource(projectState, user), configInput);
    } catch (RestApiException | PermissionBackendException e) {
      throw die(e);
    }
  }
}
