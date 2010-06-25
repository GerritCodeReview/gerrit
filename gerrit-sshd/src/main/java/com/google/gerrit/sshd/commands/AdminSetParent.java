// Copyright (C) 2010 The Android Open Source Project
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
// limitations under the License

package com.google.gerrit.sshd.commands;

import com.google.gerrit.common.errors.OperationNotExecutedException;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.PerformSetParent;
import com.google.gerrit.server.project.PerformSetParentImpl;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.AdminCommand;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

import java.util.ArrayList;
import java.util.List;

@AdminCommand
final class AdminSetParent extends BaseCommand {
  @Option(name = "--parent", aliases = {"-p"}, metaVar = "NAME", usage = "new parent project")
  private ProjectControl newParent;

  @Argument(index = 0, required = true, multiValued = true, metaVar = "NAME", usage = "projects to modify")
  private List<ProjectControl> children = new ArrayList<ProjectControl>();

  @Inject
  private PerformSetParentImpl.Factory performSetParent;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        updateParents();
      }
    });
  }

  private void updateParents() throws OrmException, UnloggedFailure {
    NameKey parentNameKey = null;
    if (newParent != null && newParent.getProject() != null) {
      parentNameKey = newParent.getProject().getNameKey();
    }

    final List<Project.NameKey> childrenNameKey = new ArrayList<Project.NameKey>();
    for (ProjectControl pc: children) {
      childrenNameKey.add(pc.getProject().getNameKey());
    }

    final PerformSetParent perfSetParent = performSetParent.create(parentNameKey, childrenNameKey);
    try {
      perfSetParent.setParent();
    } catch (OperationNotExecutedException e) {
      throw new UnloggedFailure(1, e.getMessage());
    } catch (NoSuchProjectException e){
      throw new UnloggedFailure(1, e.getMessage());
    }
  }
}