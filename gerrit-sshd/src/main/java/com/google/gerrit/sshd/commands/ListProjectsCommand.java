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

import com.google.gerrit.server.project.ListProjects;
import com.google.gerrit.sshd.BaseCommand;
import com.google.gerrit.sshd.CommandMetaData;
import com.google.inject.Inject;

import org.apache.sshd.server.Environment;

@CommandMetaData(name = "ls-projects", descr = "List projects visible to the caller")
final class ListProjectsCommand extends BaseCommand {
  @Inject
  private ListProjects impl;

  @Override
  public void start(final Environment env) {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine(impl);
        if (!impl.getFormat().isJson()) {
          if (impl.isShowTree() && (impl.getShowBranch() != null)) {
            throw new UnloggedFailure(1, "fatal: --tree and --show-branch options are not compatible.");
          }
          if (impl.isShowTree() && impl.isShowDescription()) {
            throw new UnloggedFailure(1, "fatal: --tree and --description options are not compatible.");
          }
        }
        impl.display(out);
      }
    });
  }
}
