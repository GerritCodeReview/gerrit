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

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project.SubmitType;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.CreateProjectArgs;
import com.google.gerrit.server.project.CreateProjectParamsException;
import com.google.gerrit.server.project.PerformCreateProject;
import com.google.gerrit.server.project.PerformCreateProjectImpl;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.List;

/** Create a new project. **/
final class CreateProject extends BaseCommand {
  @Option(name = "--name", aliases = {"-n"}, metaVar = "NAME", usage = "name of project to be created (deprecated option)")
  void setProjectNameFromOption(String name) {
    if (projectName != null) {
      throw new IllegalArgumentException("NAME already supplied");
    } else {
      projectName = name;
    }
  }

  @Option(name = "--owner", aliases = {"-o"}, usage = "owner(s) of project")
  private List<AccountGroup.UUID> ownerIds;

  @Option(name = "--parent", aliases = {"-p"}, metaVar = "NAME", usage = "parent project")
  private String newParent;

  @Option(name = "--permissions-only", usage = "create project for use only as parent")
  private boolean permissionsOnly;

  @Option(name = "--description", aliases = {"-d"}, metaVar = "DESCRIPTION", usage = "description of project")
  private String projectDescription = "";

  @Option(name = "--submit-type", aliases = {"-t"}, usage = "project submit type\n"
      + "(default: MERGE_IF_NECESSARY)")
  private SubmitType submitType = SubmitType.MERGE_IF_NECESSARY;

  @Option(name = "--use-contributor-agreements", aliases = {"--ca"}, usage = "if contributor agreement is required")
  private boolean contributorAgreements;

  @Option(name = "--use-signed-off-by", aliases = {"--so"}, usage = "if signed-off-by is required")
  private boolean signedOffBy;

  @Option(name = "--use-content-merge", usage = "allow automatic conflict resolving within files")
  private boolean contentMerge;

  @Option(name = "--require-change-id", aliases = {"--id"}, usage = "if change-id is required")
  private boolean requireChangeID;

  @Option(name = "--branch", aliases = {"-b"}, metaVar = "BRANCH", usage = "initial branch name\n"
      + "(default: master)")
  private String branch = Constants.MASTER;

  @Option(name = "--empty-commit", usage = "to create initial empty commit")
  private boolean createEmptyCommit;

  private String projectName;
  @Argument(index = 0, metaVar="NAME", usage="name of project to be created")
  void setProjectNameFromArgument(String name) {
    if (projectName != null) {
      throw new IllegalArgumentException("--name already supplied");
    } else {
      projectName = name;
    }
  }

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private PerformCreateProjectImpl.Factory performCreateProject;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        if (!currentUser.getCapabilities().canCreateProject()) {
          String msg = String.format(
            "fatal: %s does not have \"Create Project\" capability.",
            currentUser.getUserName());
          throw new UnloggedFailure(BaseCommand.STATUS_NOT_ADMIN, msg);
        }

        parseCommandLine();

        try {
          createProject();
        } catch (CreateProjectParamsException cppe) {
          throw new Failure(1, cppe.getMessage(), cppe);
        } catch (Exception err) {
          throw new Failure(1, "fatal: Cannot create " + projectName, err);
        }
      }
    });
  }

  private void createProject() throws RepositoryNotFoundException, IOException,
      Failure, ConfigInvalidException, CreateProjectParamsException {
    final CreateProjectArgs args = new CreateProjectArgs();
    args.setProjectName(projectName);
    args.setOwnerIds(ownerIds);
    args.setNewParent(newParent);
    args.setPermissionsOnly(permissionsOnly);
    args.setProjectDescription(projectDescription);
    args.setSubmitType(submitType);
    args.setContributorAgreements(contributorAgreements);
    args.setSignedOffBy(signedOffBy);
    args.setContentMerge(contentMerge);
    args.setChangeIdRequired(requireChangeID);
    args.setBranch(branch);
    args.setCreateEmptyCommit(createEmptyCommit);

    final PerformCreateProject perfCreateProject =
        performCreateProject.create(args);
    perfCreateProject.createProject();
  }
}
