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

import com.google.gerrit.server.project.ListProjects.FilterType;
import com.google.gerrit.sshd.BaseCommand;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;
import org.kohsuke.args4j.Option;

import java.util.List;

final class ListProjects extends BaseCommand {
  @Inject
  private com.google.gerrit.server.project.ListProjects impl;

  @Option(name = "--show-branch", aliases = {"-b"}, multiValued = true,
      usage = "displays the sha of each project in the specified branch")
  void setBranch(List<String> branchList) {
    impl.setShowBranch(branchList);
  }

  @Option(name = "--tree", aliases = {"-t"}, usage = "displays project inheritance in a tree-like format\n" +
      "this option does not work together with the show-branch option")
  void setTree(boolean show) {
    impl.setShowTree(show);
  }

  @Option(name = "--type", usage = "type of project")
  void setType(FilterType type) {
    impl.setType(type);
  }

  @Option(name = "--description", aliases = {"-d"}, usage = "include description of project in list")
  void setDescription(boolean show) {
    impl.setShowDescription(show);
  }

  @Option(name = "--all", usage = "display all projects that are accessible by the calling user")
  void setAll(boolean show) {
    impl.setShowAll(show);
  }

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        if (impl.isShowTree() && (impl.getShowBranch() != null)) {
          throw new UnloggedFailure(1, "fatal: --tree and --show-branch options are not compatible.");
        }
        if (impl.isShowTree() && impl.isShowDescription()) {
          throw new UnloggedFailure(1, "fatal: --tree and --description options are not compatible.");
        }
        impl.display(out);
      }
    });
  }
}
