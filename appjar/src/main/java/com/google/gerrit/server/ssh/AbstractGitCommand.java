// Copyright 2008 Google Inc.
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

import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.git.InvalidRepositoryException;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.lib.Repository;

import java.io.IOException;

abstract class AbstractGitCommand extends AbstractCommand {
  protected Repository repo;
  protected Project proj;
  protected ReviewDb db;

  protected boolean isGerrit() {
    return getName().startsWith("gerrit-");
  }

  @Override
  protected final void run(final String[] args) throws IOException, Failure {
    final String reqName = parseCommandLine(args);
    String projectName = reqName;
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

    db = openReviewDb();
    try {
      try {
        proj = db.projects().byName(new Project.NameKey(projectName));
      } catch (OrmException e) {
        throw new Failure(1, "fatal: cannot query project database");
      }
      if (proj == null) {
        throw new Failure(1, "fatal: '" + reqName + "': not a Gerrit project");
      }

      try {
        repo = getRepositoryCache().get(proj.getName());
      } catch (InvalidRepositoryException e) {
        throw new Failure(1, "fatal: '" + reqName + "': not a git archive");
      }

      runImpl();
    } finally {
      closeDb();
    }
  }

  protected void closeDb() {
    if (db != null) {
      db.close();
      db = null;
    }
  }

  protected abstract void runImpl() throws IOException, Failure;

  protected abstract String parseCommandLine(String[] args) throws Failure;
}
