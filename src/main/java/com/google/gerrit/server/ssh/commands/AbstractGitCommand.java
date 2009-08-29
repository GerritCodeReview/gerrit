// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.ssh.commands;

import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.ssh.BaseCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.spearce.jgit.errors.RepositoryNotFoundException;
import org.spearce.jgit.lib.Repository;

import java.io.IOException;

abstract class AbstractGitCommand extends BaseCommand {
  @Argument(index = 0, metaVar = "PROJECT.git", required = true, usage = "project name")
  protected ProjectControl projectControl;

  @Inject
  protected GerritServer server;

  protected Repository repo;
  protected Project project;

  @Override
  public void start() {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        AbstractGitCommand.this.service();
      }
    });
  }

  private void service() throws IOException, Failure {
    project = projectControl.getProjectState().getProject();

    final String name = project.getName();
    try {
      repo = server.openRepository(name);
    } catch (RepositoryNotFoundException e) {
      throw new Failure(1, "fatal: '" + name + "': not a git archive", e);
    }

    try {
      runImpl();
    } finally {
      repo.close();
    }
  }

  protected abstract void runImpl() throws IOException, Failure;
}
