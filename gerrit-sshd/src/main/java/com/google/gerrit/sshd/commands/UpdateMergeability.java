package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.change.MergeabilityChecker;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "update-mergeability", description = "Update mergeability flag fora specific ref")
public class UpdateMergeability extends SshCommand {

  @Option(name = "--pproject", aliases = {"-p"}, metaVar = "NAME", usage = "project")
  private ProjectControl project;

  @Option(name = "--ref", aliases = {"-r"}, metaVar = "REF", usage = "ref")
  private String ref;

  @Inject
  private MergeabilityChecker checker;

  @Override
  protected void run() {
    checker.updateAndIndex(project.getProject().getNameKey(), ref);
  }
}
