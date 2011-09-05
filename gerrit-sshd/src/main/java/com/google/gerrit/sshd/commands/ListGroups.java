// Copyright (C) 2011 The Android Open Source Project
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

import com.google.gerrit.common.data.GroupDetail;
import com.google.gerrit.common.data.GroupList;
import com.google.gerrit.common.errors.NoSuchGroupException;
import com.google.gerrit.server.account.PerformVisibleGroups;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

public class ListGroups extends BaseCommand {

  @Inject
  private PerformVisibleGroups.Factory performVisibleGroupsFactory;

  private final Set<ProjectControl> projects = new HashSet<ProjectControl>();

  @Option(name = "--project", aliases = {"-p"}, usage = "projects for which the groups should be listed")
  void addProject(final ProjectControl project) {
    projects.add(project);
  }

  @Override
  public void start(final Environment env) throws IOException {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        ListGroups.this.display();
      }
    });
  }

  private void display() throws Failure {
    final PrintWriter stdout = toPrintWriter(out);
    try {
      final PerformVisibleGroups performVisibleGroups =
          performVisibleGroupsFactory.create();
      performVisibleGroups.setProjects(projects);
      final GroupList visibleGroups =
          performVisibleGroups.getVisibleGroups();
      for (final GroupDetail groupDetail : visibleGroups.getGroups()) {
        stdout.print(groupDetail.group.getName() + "\n");
      }
    } catch (OrmException e) {
      throw die(e);
    } catch (NoSuchGroupException e) {
      throw die(e);
    } finally {
      stdout.flush();
    }
  }
}
