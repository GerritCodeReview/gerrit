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

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.State;
import com.google.gerrit.reviewdb.client.Project.SubmitType;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.RequiresCapability;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
final class SetProjectCommand extends SshCommand {
  private static final Logger log = LoggerFactory
      .getLogger(SetProjectCommand.class);

  @Argument(index = 0, required = true, metaVar = "NAME", usage = "name of the project")
  private ProjectControl projectControl;

  @Option(name = "--description", aliases = {"-d"}, metaVar = "DESCRIPTION", usage = "description of project")
  private String projectDescription;

  @Option(name = "--submit-type", aliases = {"-t"}, usage = "project submit type\n"
      + "(default: MERGE_IF_NECESSARY)")
  private SubmitType submitType;

  @Option(name = "--use-contributor-agreements", aliases = {"--ca"}, usage = "if contributor agreement is required")
  private Boolean contributorAgreements;

  @Option(name = "--use-signed-off-by", aliases = {"--so"}, usage = "if signed-off-by is required")
  private Boolean signedOffBy;

  @Option(name = "--use-content-merge", usage = "allow automatic conflict resolving within files")
  private Boolean contentMerge;

  @Option(name = "--require-change-id", aliases = {"--id"}, usage = "if change-id is required")
  private Boolean requireChangeID;

  @Option(name = "--project-state", aliases = {"--ps"}, usage = "project's visibility state")
  private State state;

  @Inject
  private MetaDataUpdate.User metaDataUpdateFactory;

  @Inject
  private ProjectCache projectCache;

  @Override
  protected void run() throws Failure {
    Project ctlProject = projectControl.getProject();
    Project.NameKey nameKey = ctlProject.getNameKey();
    String name = ctlProject.getName();
    final StringBuilder err = new StringBuilder();

    try {
      MetaDataUpdate md = metaDataUpdateFactory.create(nameKey);
      try {
        ProjectConfig config = ProjectConfig.read(md);
        Project project = config.getProject();

        project.setRequireChangeID(requireChangeID != null ? requireChangeID
            : project.isRequireChangeID());

        project.setSubmitType(submitType != null ? submitType : project
            .getSubmitType());

        project.setUseContentMerge(contentMerge != null ? contentMerge
            : project.isUseContentMerge());

        project.setUseContributorAgreements(contributorAgreements != null
            ? contributorAgreements : project.isUseContributorAgreements());

        project.setUseSignedOffBy(signedOffBy != null ? signedOffBy : project
            .isUseSignedOffBy());

        project.setDescription(projectDescription != null ? projectDescription
            : project.getDescription());

        project.setState(state != null ? state : project.getState());

        md.setMessage("Project settings updated");
        if (!config.commit(md)) {
          err.append("error: Could not update project " + name + "\n");
        }
      } finally {
        md.close();
      }
    } catch (RepositoryNotFoundException notFound) {
      err.append("error: Project " + name + " not found\n");
    } catch (IOException e) {
      final String msg = "Cannot update project " + name;
      log.error(msg, e);
      err.append("error: " + msg + "\n");
    } catch (ConfigInvalidException e) {
      final String msg = "Cannot update project " + name;
      log.error(msg, e);
      err.append("error: " + msg + "\n");
    }
    projectCache.evict(ctlProject);

    if (err.length() > 0) {
      while (err.charAt(err.length() - 1) == '\n') {
        err.setLength(err.length() - 1);
      }
      throw new UnloggedFailure(1, err.toString());
    }
  }
}
