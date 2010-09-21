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
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Project.SubmitType;
import com.google.gerrit.server.project.PerformCreateProject;
import com.google.gerrit.server.project.PerformCreateProjectImpl;
import com.google.gerrit.server.project.RetrieveParentCandidates;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.List;

/** Create a new project. **/
final class CreateProject extends BaseCommand {
  @Option(name = "--name", aliases = {"-n"}, metaVar = "NAME", usage = "name of project to be created")
  private String projectName;

  @Option(name = "--suggest-parent", aliases = {"--sp"}, usage = "suggest parent candidates, " +
                                                                 "it cannot be used with other arguments combination")
  private boolean suggestParent;

  @Option(name = "--owner", aliases = {"-o"}, usage = "owner(s) of project")
  private List<AccountGroup.Id> ownerIds;

  @Option(name = "--parent", aliases = {"-p"}, metaVar = "NAME", usage = "parent project")
  private String newParent;

  @Option(name = "--permissions-only", usage = "create project for use only as parent")
  private boolean permissionsOnly;

  @Option(name = "--description", aliases = {"-d"}, metaVar = "DESC", usage = "description of project")
  private String projectDescription = "";

  @Option(name = "--submit-type", aliases = {"-t"}, usage = "project submit type\n"
      + "(default: MERGE_IF_NECESSARY)")
  private SubmitType submitType = SubmitType.MERGE_IF_NECESSARY;

  @Option(name = "--use-contributor-agreements", aliases = {"--ca"}, usage = "if contributor agreement is required")
  private boolean contributorAgreements;

  @Option(name = "--use-signed-off-by", aliases = {"--so"}, usage = "if signed-off-by is required")
  private boolean signedOffBy;

  @Option(name = "--branch", aliases = {"-b"}, metaVar = "BRANCH", usage = "initial branch name\n"
      + "(default: master)")
  private String branch = Constants.MASTER;

  @Inject
  private PerformCreateProjectImpl.Factory performCreateProject;

  @Inject
  private RetrieveParentCandidates retrieveParentCandidates;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        PrintWriter p = toPrintWriter(out);

        parseCommandLine();

        if (!suggestParent) {
          // hard-coding this, to not make "suggest-parent" option requiring "--name"
          //
          if (projectName == null) {
            p.println("fatal: Option \"--name (-n)\" is required");
            p.flush();
            return;
          }

          try {
            createProject();
          } catch (Exception e) {
            p.print("Error when trying to create project: " + e.getMessage()
                + "\n");
            p.flush();
          }
        } else {
          List<Project.NameKey> parentCandidates =
              retrieveParentCandidates.getParentCandidates();
          p.println("Parent Suggestions:");
          for (Project.NameKey parent : parentCandidates) {
            p.println(parent.get());
          }
          p.flush();
        }
      }
    });
  }

  private void createProject() throws RepositoryNotFoundException,
      OrmException, IOException, Failure {
    final PerformCreateProject perfCreateProject =
        performCreateProject.create(projectName, ownerIds, newParent,
            projectDescription, submitType, contributorAgreements, signedOffBy,
            permissionsOnly, branch);
    final StringBuilder err = perfCreateProject.createProject();

    if (err.length() > 0) {
      throw new Failure(1, err.toString());
    }
  }
}
