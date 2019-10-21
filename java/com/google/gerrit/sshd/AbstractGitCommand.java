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

package com.google.gerrit.sshd;

import com.google.gerrit.entities.Project;
import com.google.gerrit.server.AccessPath;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.sshd.SshScope.Context;
import com.google.inject.Inject;
import java.io.IOException;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.channel.ChannelSession;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Argument;

public abstract class AbstractGitCommand extends BaseCommand {
  private static final String GIT_PROTOCOL = "GIT_PROTOCOL";

  @Argument(index = 0, metaVar = "PROJECT.git", required = true, usage = "project name")
  protected ProjectState projectState;

  @Inject private SshScope sshScope;

  @Inject private GitRepositoryManager repoManager;

  @Inject private SshSession session;

  @Inject private SshScope.Context context;

  @Inject private IdentifiedUser.GenericFactory userFactory;

  protected Repository repo;
  protected Project.NameKey projectName;
  protected Project project;
  protected String[] extraParameters;

  @Override
  public void start(ChannelSession channel, Environment env) {
    String gitProtocol = env.getEnv().get(GIT_PROTOCOL);
    if (gitProtocol != null) {
      extraParameters = gitProtocol.split(":");
    }

    Context ctx = context.subContext(newSession(), context.getCommandLine());
    final Context old = sshScope.set(ctx);
    try {
      startThread(
          new ProjectCommandRunnable() {
            @Override
            public void executeParseCommand() throws Exception {
              parseCommandLine();
            }

            @Override
            public void run() throws Exception {
              AbstractGitCommand.this.service();
            }

            @Override
            public Project.NameKey getProjectName() {
              Project project = projectState.getProject();
              return project.getNameKey();
            }
          },
          AccessPath.GIT);
    } finally {
      sshScope.set(old);
    }
  }

  private SshSession newSession() {
    SshSession n =
        new SshSession(
            session,
            session.getRemoteAddress(),
            userFactory.create(session.getRemoteAddress(), user.getAccountId()));
    return n;
  }

  private void service() throws IOException, PermissionBackendException, Failure {
    project = projectState.getProject();
    projectName = project.getNameKey();

    try {
      repo = repoManager.openRepository(projectName);
    } catch (RepositoryNotFoundException e) {
      throw new Failure(1, "fatal: '" + project.getName() + "': not a git archive", e);
    }

    try {
      runImpl();
    } finally {
      repo.close();
    }
  }

  protected abstract void runImpl() throws IOException, PermissionBackendException, Failure;
}
