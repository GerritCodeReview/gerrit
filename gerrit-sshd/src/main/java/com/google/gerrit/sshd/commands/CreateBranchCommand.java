package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.CreateBranch;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.io.IOException;

/** Create a new branch. **/
@RequiresCapability(GlobalCapability.CREATE_BRANCH)
@CommandMetaData(name = "create-branch", description = "Create a new branch")
final public class CreateBranchCommand extends SshCommand {

  @Option(name = "--revision", aliases = {"-r"}, metaVar = "REVISION", usage = "base revision of the new branch")
  private String revision;

  @Argument(index = 0, required = true, metaVar = "PROJECT", usage = "name of the project")
  private ProjectControl project;

  @Argument(index = 1, required = true, metaVar = "NAME", usage = "name of branch to be created")
  private String ref;

  @Inject
  private CreateBranch.Factory createBranchFactory;

  @Override
  protected void run() throws UnloggedFailure, Failure, Exception {
    try {
      ProjectResource projectResource = new ProjectResource(project);
      CreateBranch.Input input = new CreateBranch.Input();
      input.ref = ref;
      input.revision = revision;
      createBranchFactory.create(ref).apply(projectResource, input);
    } catch (IOException e) {
      throw new RestApiException("Cannot create branch", e);
    }
  }

}
