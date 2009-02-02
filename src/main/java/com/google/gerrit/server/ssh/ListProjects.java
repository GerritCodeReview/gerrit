// Copyright 2009 Google Inc.
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

package com.google.gerrit.server.ssh;

import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gwtorm.client.OrmException;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

class ListProjects extends AbstractCommand {
  @Override
  protected void run(final String[] args) throws IOException, Failure {
    if (args.length != 0) {
      throw new Failure(1, "usage: " + getName());
    }

    final PrintWriter stdout =
        new PrintWriter(
            new BufferedWriter(new OutputStreamWriter(out, "UTF-8")));
    final ReviewDb db = openReviewDb();
    try {
      final ProjectCache cache = Common.getProjectCache();
      for (final Project p : db.projects().all()) {
        if (ProjectRight.WILD_PROJECT.equals(p.getId())) {
          // This project "doesn't exist". At least not as a repository.
          //
          continue;
        }

        final ProjectCache.Entry e = cache.get(p.getId());
        if (e != null && canRead(e)) {
          stdout.print(p.getName());
          stdout.println();
        }
      }
    } catch (OrmException e) {
      throw new Failure(1, "fatal: database error", e);
    } finally {
      db.close();
    }
    stdout.flush();
  }
}
