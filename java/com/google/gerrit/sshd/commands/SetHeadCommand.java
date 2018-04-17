// Copyright (C) 2015 The Android Open Source Project
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

import com.google.gerrit.extensions.api.projects.HeadInput;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.project.ProjectAccessor;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.project.SetHead;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.gerrit.sshd.SshCommand;
import com.google.inject.Inject;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@CommandMetaData(name = "set-head", description = "Change HEAD reference for a project")
public class SetHeadCommand extends SshCommand {

  @Argument(index = 0, required = true, metaVar = "NAME", usage = "name of the project")
  private ProjectState project;

  @Option(name = "--new-head", required = true, metaVar = "REF", usage = "new HEAD reference")
  private String newHead;

  private final ProjectAccessor.Factory projectAccessorFactory;
  private final SetHead setHead;

  @Inject
  SetHeadCommand(ProjectAccessor.Factory projectAccessorFactory, SetHead setHead) {
    this.projectAccessorFactory = projectAccessorFactory;
    this.setHead = setHead;
  }

  @Override
  protected void run() throws Exception {
    HeadInput input = new HeadInput();
    input.ref = newHead;
    try {
      setHead.apply(new ProjectResource(projectAccessorFactory.create(project), user), input);
    } catch (UnprocessableEntityException e) {
      throw die(e);
    }
  }
}
