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
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.client.InheritableBoolean;
import com.google.gerrit.extensions.client.ProjectState;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "set-project", description = "Change a project's settings")
final class SetProjectCommand extends SshCommand {
  private static final Logger log = LoggerFactory.getLogger(SetProjectCommand.class);

  @Argument(index = 0, required = true, metaVar = "NAME", usage = "name of the project")
  private ProjectControl projectControl;

  @Option(
    name = "--description",
    aliases = {"-d"},
    metaVar = "DESCRIPTION",
    usage = "description of project"
  )
  private String projectDescription;

  @Option(
    name = "--submit-type",
    aliases = {"-t"},
    usage = "project submit type\n" + "(default: MERGE_IF_NECESSARY)"
  )
  private SubmitType submitType;

  @Option(name = "--contributor-agreements", usage = "if contributor agreement is required")
  private InheritableBoolean contributorAgreements;

  @Option(name = "--signed-off-by", usage = "if signed-off-by is required")
  private InheritableBoolean signedOffBy;

  @Option(name = "--content-merge", usage = "allow automatic conflict resolving within files")
  private InheritableBoolean contentMerge;

  @Option(name = "--change-id", usage = "if change-id is required")
  private InheritableBoolean requireChangeID;

  @Option(
    name = "--use-contributor-agreements",
    aliases = {"--ca"},
    usage = "if contributor agreement is required"
  )
  void setUseContributorArgreements(@SuppressWarnings("unused") boolean on) {
    contributorAgreements = InheritableBoolean.TRUE;
  }

  @Option(
    name = "--no-contributor-agreements",
    aliases = {"--nca"},
    usage = "if contributor agreement is not required"
  )
  void setNoContributorArgreements(@SuppressWarnings("unused") boolean on) {
    contributorAgreements = InheritableBoolean.FALSE;
  }

  @Option(
    name = "--use-signed-off-by",
    aliases = {"--so"},
    usage = "if signed-off-by is required"
  )
  void setUseSignedOffBy(@SuppressWarnings("unused") boolean on) {
    signedOffBy = InheritableBoolean.TRUE;
  }

  @Option(
    name = "--no-signed-off-by",
    aliases = {"--nso"},
    usage = "if signed-off-by is not required"
  )
  void setNoSignedOffBy(@SuppressWarnings("unused") boolean on) {
    signedOffBy = InheritableBoolean.FALSE;
  }

  @Option(name = "--use-content-merge", usage = "allow automatic conflict resolving within files")
  void setUseContentMerge(@SuppressWarnings("unused") boolean on) {
    contentMerge = InheritableBoolean.TRUE;
  }

  @Option(
    name = "--no-content-merge",
    usage = "don't allow automatic conflict resolving within files"
  )
  void setNoContentMerge(@SuppressWarnings("unused") boolean on) {
    contentMerge = InheritableBoolean.FALSE;
  }

  @Option(
    name = "--require-change-id",
    aliases = {"--id"},
    usage = "if change-id is required"
  )
  void setRequireChangeId(@SuppressWarnings("unused") boolean on) {
    requireChangeID = InheritableBoolean.TRUE;
  }

  @Option(
    name = "--no-change-id",
    aliases = {"--nid"},
    usage = "if change-id is not required"
  )
  void setNoChangeId(@SuppressWarnings("unused") boolean on) {
    requireChangeID = InheritableBoolean.FALSE;
  }

  @Option(
    name = "--project-state",
    aliases = {"--ps"},
    usage = "project's visibility state"
  )
  private ProjectState state;

  @Option(name = "--max-object-size-limit", usage = "max Git object size for this project")
  private String maxObjectSizeLimit;

  @Inject private MetaDataUpdate.User metaDataUpdateFactory;

  @Inject private ProjectCache projectCache;

  @Override
  protected void run() throws Failure {
    Project ctlProject = projectControl.getProject();
    Project.NameKey nameKey = ctlProject.getNameKey();
    String name = ctlProject.getName();
    final StringBuilder err = new StringBuilder();

    try (MetaDataUpdate md = metaDataUpdateFactory.create(nameKey)) {
      ProjectConfig config = ProjectConfig.read(md);
      Project project = config.getProject();

      if (requireChangeID != null) {
        project.setRequireChangeID(requireChangeID);
      }
      if (submitType != null) {
        project.setSubmitType(submitType);
      }
      if (contentMerge != null) {
        project.setUseContentMerge(contentMerge);
      }
      if (contributorAgreements != null) {
        project.setUseContributorAgreements(contributorAgreements);
      }
      if (signedOffBy != null) {
        project.setUseSignedOffBy(signedOffBy);
      }
      if (projectDescription != null) {
        project.setDescription(projectDescription);
      }
      if (state != null) {
        project.setState(state);
      }
      if (maxObjectSizeLimit != null) {
        project.setMaxObjectSizeLimit(maxObjectSizeLimit);
      }
      md.setMessage("Project settings updated");
      config.commit(md);
    } catch (RepositoryNotFoundException notFound) {
      err.append("Project ").append(name).append(" not found\n");
    } catch (IOException | ConfigInvalidException e) {
      final String msg = "Cannot update project " + name;
      log.error(msg, e);
      err.append("error: ").append(msg).append("\n");
    }
    projectCache.evict(ctlProject);

    if (err.length() > 0) {
      while (err.charAt(err.length() - 1) == '\n') {
        err.setLength(err.length() - 1);
      }
      throw die(err.toString());
    }
  }
}
