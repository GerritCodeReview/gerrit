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

package com.google.gerrit.server.ssh.commands;

import static com.google.gerrit.client.reviewdb.ApprovalCategory.READ;

import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.ssh.BaseCommand;
import com.google.gwtorm.client.OrmException;
import com.google.inject.Inject;

import java.io.PrintWriter;

final class ListProjects extends BaseCommand {
  @Inject
  private ReviewDb db;

  @Inject
  private IdentifiedUser currentUser;

  @Override
  public void start() {
    startThread(new CommandRunnable() {
      @Override
      public void run() throws Exception {
        parseCommandLine();
        ListProjects.this.display();
      }
    });
  }

  private void display() throws Failure {
    final PrintWriter stdout = toPrintWriter(out);
    try {
      final ProjectCache cache = Common.getProjectCache();
      for (final Project p : db.projects().all()) {
        if (ProjectRight.WILD_PROJECT.equals(p.getId())) {
          // This project "doesn't exist". At least not as a repository.
          //
          continue;
        }

        final ProjectCache.Entry e = cache.get(p.getId());
        if (e != null && currentUser.canPerform(e, READ, (short) 1)) {
          stdout.print(p.getName());
          stdout.println();
        }
      }
    } catch (OrmException e) {
      throw new Failure(1, "fatal: database error", e);
    } finally {
      stdout.flush();
    }
  }
}
