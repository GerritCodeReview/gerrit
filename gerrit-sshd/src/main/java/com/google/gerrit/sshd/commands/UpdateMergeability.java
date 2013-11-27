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
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.server.change.MergeabilityChecker;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Option;

@RequiresCapability(GlobalCapability.ADMINISTRATE_SERVER)
@CommandMetaData(name = "update-mergeability", description = "Update mergeability flag for a specific ref")
public class UpdateMergeability extends SshCommand {

  @Option(name = "--project", aliases = {"-p"}, metaVar = "NAME", usage = "project")
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
