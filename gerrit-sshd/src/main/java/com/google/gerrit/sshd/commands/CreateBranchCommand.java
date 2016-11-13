// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.projects.BranchInput;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import org.kohsuke.args4j.Argument;

/** Create a new branch. * */
@CommandMetaData(name = "create-branch", description = "Create a new branch")
public final class CreateBranchCommand extends SshCommand {

  @Argument(index = 0, required = true, metaVar = "PROJECT", usage = "name of the project")
  private ProjectControl project;

  @Argument(index = 1, required = true, metaVar = "NAME", usage = "name of branch to be created")
  private String name;

  @Argument(
    index = 2,
    required = true,
    metaVar = "REVISION",
    usage = "base revision of the new branch"
  )
  private String revision;

  @Inject GerritApi gApi;

  @Override
  protected void run() throws UnloggedFailure {
    try {
      BranchInput in = new BranchInput();
      in.revision = revision;
      gApi.projects().name(project.getProject().getNameKey().get()).branch(name).create(in);
    } catch (RestApiException e) {
      throw die(e);
    }
  }
}
