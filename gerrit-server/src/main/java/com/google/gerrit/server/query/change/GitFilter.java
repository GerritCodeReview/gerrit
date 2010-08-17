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
// limitations under the License.

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.MessageRevFilter;
import org.eclipse.jgit.revwalk.filter.RevFilter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/** It filters commits in git repositories by text commit messages. */
public class GitFilter {
  public interface Factory {
    GitFilter create(String text);
  }

  private static final Logger log = LoggerFactory.getLogger(GitFilter.class);

  private final RevFilter rFilter;
  private final ReviewDb db;
  private final GitRepositoryManager repoManager;

  @Inject
  public GitFilter(final ReviewDb db,
      final ProjectControl.Factory projectControlFactory,
      final CurrentUser user, final GitRepositoryManager gRepoManager,
      @Assisted final String text) {
    this.db = db;
    this.repoManager = gRepoManager;
    this.rFilter = MessageRevFilter.create(text);
  }

  public List<String> filterByRepository(final Project.NameKey name) {
    final List<String> commitsList = new ArrayList<String>();
    try {
      ResultSet<Change> r = db.changes().byProjectLastCommit(name);
      if (r != null) {
        final List<Change> lastCommitChange = r.toList();
        if (!lastCommitChange.isEmpty()) {
          final Change change = lastCommitChange.get(0);
          final RevWalk w =
              filterCommits(name.get(), change.getKey().get(), rFilter);
          for (final RevCommit c : w) {
            commitsList.add(c.getName());
          }
          return commitsList;
        }
      }
    } catch (OrmException e) {
      log.error("Cannot query the database" + e);
    } catch (RepositoryNotFoundException e) {
      log.error("Repository \"" + name + "\" unknown.", e);
    }

    return null;
  }

  private RevWalk filterCommits(final String name, final String changeKey,
      final RevFilter rFilter) throws RepositoryNotFoundException {
    final ObjectId lastCommmitId = ObjectId.fromString(changeKey.substring(1));
    final Repository repo = repoManager.openRepository(name);
    final RevWalk rWalk = new RevWalk(repo);

    try {
      rWalk.markStart(rWalk.parseCommit(lastCommmitId));
    } catch (MissingObjectException e) {
      log.error("Commit Id \"" + lastCommmitId + "\" does not exist.", e);
    } catch (IncorrectObjectTypeException e) {
      log.error("Id \"" + lastCommmitId + "\" is not a commit.", e);
    } catch (IOException e) {
      log.error("Could not search for commit message in \"" + name
          + "\" repository.", e);
    }

    rWalk.setRevFilter(rFilter);
    rWalk.sort(RevSort.TOPO);
    rWalk.sort(RevSort.COMMIT_TIME_DESC, true);

    return rWalk;
  }
}
