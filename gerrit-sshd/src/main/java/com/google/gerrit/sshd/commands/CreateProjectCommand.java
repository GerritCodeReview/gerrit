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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.errors.ProjectCreationFailedException;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project.InheritableBoolean;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.project.ListProjects;
import com.google.gerrit.server.project.PerformCreateProject;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.List;

/** Create a new project. **/
@RequiresCapability(GlobalCapability.CREATE_PROJECT)
@CommandMetaData(name = "create-project", descr = "Create a new project and associated Git repository")
final class CreateProjectCommand extends SshCommand {
  @Option(name = "--name", aliases = {"-n"}, metaVar = "NAME", usage = "name of project to be created (deprecated option)")
  void setProjectNameFromOption(String name) {
    if (projectName != null) {
      throw new IllegalArgumentException("NAME already supplied");
    } else {
      projectName = name;
    }
  }

  @Option(name = "--suggest-parents", aliases = {"-S"}, usage = "suggest parent candidates, "
      + "if this option is used all other options and arguments are ignored")
  private boolean suggestParent;

  @Option(name = "--owner", aliases = {"-o"}, usage = "owner(s) of project")
  private List<AccountGroup.UUID> ownerIds;

  @Option(name = "--parent", aliases = {"-p"}, metaVar = "NAME", usage = "parent project")
  private ProjectControl newParent;

  @Option(name = "--permissions-only", usage = "create project for use only as parent")
  private boolean permissionsOnly;

  @Option(name = "--description", aliases = {"-d"}, metaVar = "DESCRIPTION", usage = "description of project")
  private String projectDescription = "";

  @Option(name = "--submit-type", aliases = {"-t"}, usage = "project submit type\n"
      + "(default: MERGE_IF_NECESSARY)")
  private SubmitType submitType = SubmitType.MERGE_IF_NECESSARY;

  @Option(name = "--contributor-agreements", usage = "if contributor agreement is required")
  private InheritableBoolean contributorAgreements = InheritableBoolean.INHERIT;

  @Option(name = "--signed-off-by", usage = "if signed-off-by is required")
  private InheritableBoolean signedOffBy = InheritableBoolean.INHERIT;

  @Option(name = "--content-merge", usage = "allow automatic conflict resolving within files")
  private InheritableBoolean contentMerge = InheritableBoolean.INHERIT;

  @Option(name = "--change-id", usage = "if change-id is required")
  private InheritableBoolean requireChangeID = InheritableBoolean.INHERIT;

  @Option(name = "--use-contributor-agreements", aliases = {"--ca"}, usage = "if contributor agreement is required")
  void setUseContributorArgreements(boolean on) {
    contributorAgreements = InheritableBoolean.TRUE;
  }

  @Option(name = "--use-signed-off-by", aliases = {"--so"}, usage = "if signed-off-by is required")
  void setUseSignedOffBy(boolean on) {
    signedOffBy = InheritableBoolean.TRUE;
  }

  @Option(name = "--use-content-merge", usage = "allow automatic conflict resolving within files")
  void setUseContentMerge(boolean on) {
    contentMerge = InheritableBoolean.TRUE;
  }

  @Option(name = "--require-change-id", aliases = {"--id"}, usage = "if change-id is required")
  void setRequireChangeId(boolean on) {
    requireChangeID = InheritableBoolean.TRUE;
  }

  @Option(name = "--branch", aliases = {"-b"}, metaVar = "BRANCH", usage = "initial branch name\n"
      + "(default: master)")
  private List<String> branch;

  @Option(name = "--empty-commit", usage = "to create initial empty commit")
  private boolean createEmptyCommit;

  private String projectName;

  @Argument(index = 0, metaVar = "NAME", usage = "name of project to be created")
  void setProjectNameFromArgument(String name) {
    if (projectName != null) {
      throw new IllegalArgumentException("--name already supplied");
    } else {
      projectName = name;
    }
  }

  @Inject
  private PerformCreateProject.Factory factory;

  @Inject
  private Provider<ListProjects> listProjectsProvider;

  @Override
  protected void run() throws Exception {
    try {
      if (!suggestParent) {
        if (projectName == null) {
          throw new UnloggedFailure(1, "fatal: Project name is required.");
        }
        final CreateProjectArgs args = new CreateProjectArgs();
        args.setProjectName(projectName);
        args.ownerIds = ownerIds;
        args.newParent = newParent;
        args.permissionsOnly = permissionsOnly;
        args.projectDescription = projectDescription;
        args.submitType = submitType;
        args.contributorAgreements = contributorAgreements;
        args.signedOffBy = signedOffBy;
        args.contentMerge = contentMerge;
        args.changeIdRequired = requireChangeID;
        args.branch = branch;
        args.createEmptyCommit = createEmptyCommit;

        final PerformCreateProject createProject = factory.create(args);
        createProject.createProject();
      } else {
        ListProjects list = listProjectsProvider.get();
        list.setFilterType(ListProjects.FilterType.PARENT_CANDIDATES);
        for (String parent : list.apply().keySet()) {
          stdout.print(parent + "\n");
        }
      }
    } catch (ProjectCreationFailedException err) {
      throw new UnloggedFailure(1, "fatal: " + err.getMessage(), err);
    }
  }
}
