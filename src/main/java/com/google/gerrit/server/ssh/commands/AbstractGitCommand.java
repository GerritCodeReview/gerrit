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

import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.ssh.BaseCommand;
import com.google.inject.Inject;

import org.kohsuke.args4j.Argument;
import org.spearce.jgit.errors.RepositoryNotFoundException;
import org.spearce.jgit.lib.Repository;

import java.io.IOException;

abstract class AbstractGitCommand extends BaseCommand {
  @Argument(index = 0, metaVar = "PROJECT.git", required = true, usage = "project name")
  private String reqProjName;

  @Inject
  protected GerritServer server;

  @Inject
  private IdentifiedUser currentUser;

  @Inject
  private ProjectCache projectCache;

  protected Repository repo;
  protected ProjectState cachedProj;
  protected Project proj;

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
    String projectName = reqProjName;
    if (projectName.endsWith(".git")) {
      // Be nice and drop the trailing ".git" suffix, which we never keep
      // in our database, but clients might mistakenly provide anyway.
      //
      projectName = projectName.substring(0, projectName.length() - 4);
    }
    if (projectName.startsWith("/")) {
      // Be nice and drop the leading "/" if supplied by an absolute path.
      // We don't have a file system hierarchy, just a flat namespace in
      // the database's Project entities. We never encode these with a
      // leading '/' but users might accidentally include them in Git URLs.
      //
      projectName = projectName.substring(1);
    }

    cachedProj = projectCache.get(new Project.NameKey(projectName));
    if (cachedProj == null) {
      throw new Failure(1, "fatal: '" + reqProjName + "': not a Gerrit project");
    }

    proj = cachedProj.getProject();
    if (!canPerform(ApprovalCategory.READ, (short) 1)) {
      throw new Failure(1, "fatal: '" + reqProjName + "': unknown project",
          new SecurityException("Account lacks Read permission"));
    }

    try {
      repo = server.openRepository(proj.getName());
    } catch (RepositoryNotFoundException e) {
      throw new Failure(1, "fatal: '" + reqProjName + "': not a git archive", e);
    }
    try {
      runImpl();
    } finally {
      repo.close();
    }
  }

  protected boolean canPerform(final ApprovalCategory.Id actionId,
      final short val) {
    return cachedProj.controlFor(currentUser).canPerform(actionId, val);
  }

  protected abstract void runImpl() throws IOException, Failure;
}
