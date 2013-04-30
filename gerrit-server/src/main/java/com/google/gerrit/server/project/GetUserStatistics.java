// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.project;

import com.google.common.collect.Maps;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.AccountInfo;
import com.google.gerrit.server.account.AccountsCollection;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.revwalk.filter.RevFilter;

import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Map;

public class GetUserStatistics implements RestReadView<ProjectResource> {
  final static int LAST_30_DAYS = 30 * 24 * 60 * 60;
  final static int LAST_12_MONTHS = 365 * 24 * 60 * 60;

  private final GitRepositoryManager repoManager;
  private final AccountsCollection accounts;

  @Inject
  GetUserStatistics(GitRepositoryManager repoManager,
      AccountsCollection accounts) {
    this.repoManager = repoManager;
    this.accounts = accounts;
  }

  @Override
  public Collection<UserStatistics> apply(ProjectResource rsrc)
      throws ResourceNotFoundException, AuthException, OrmException {
    Map<AccountInfo, UserStatistics> stats = Maps.newHashMap();
    int allTimeCommitCount = 0;
    int last30DaysCommitCount = 0;
    int last12MonthsCommitCount = 0;
    Repository repo = null;
    RevWalk rw = null;
    try {
      repo = repoManager.openRepository(rsrc.getNameKey());
      rw = new RevWalk(repo);
      rw.markStart(rw.parseCommit(repo.getRef(Constants.HEAD).getObjectId()));
      for (Ref r : repo.getAllRefs().values()) {
        if (r.getName().startsWith(Constants.R_HEADS)) {
          rw.markStart(rw.parseCommit(r.getObjectId()));
        }
      }

      // ignore merge commits
      rw.setRevFilter(new RevFilter() {
        @Override
        public boolean include(RevWalk walker, RevCommit c) {
          return c.getParentCount() <= 1;
        }

        @Override
        public RevFilter clone() {
          return this;
        }
      });

      RevCommit c;
      while ((c = rw.next()) != null) {
        AccountInfo author = getAccountInfo(c.getAuthorIdent());
        UserStatistics s = stats.get(author);
        if (s == null) {
          s = new UserStatistics(author);
          stats.put(author, s);
        }
        allTimeCommitCount++;
        s.all_time.count++;
        int current = (int) new Date().getTime() / 1000;
        if ((current - c.getCommitTime()) <= LAST_12_MONTHS) {
          last12MonthsCommitCount++;
          s.last_12_months.count++;
          if ((current - c.getCommitTime()) <= LAST_30_DAYS) {
            last30DaysCommitCount++;
            s.last_30_days.count++;
          }
        }
      }
    } catch (RepositoryNotFoundException e) {
      throw new ResourceNotFoundException(rsrc.getName());
    } catch (IOException e) {
      throw new ResourceNotFoundException(rsrc.getName());
    } finally {
      if (rw != null) {
        rw.release();
      }
      if (repo != null) {
        repo.close();
      }
    }

    if (allTimeCommitCount > 0) {
      for (UserStatistics s : stats.values()) {
        s.all_time.computePercentage(allTimeCommitCount);
        s.last_12_months.computePercentage(last12MonthsCommitCount);
        s.last_30_days.computePercentage(last30DaysCommitCount);
      }
    }

    return stats.values();
  }

  private AccountInfo getAccountInfo(PersonIdent ident) throws AuthException,
      OrmException {
    IdentifiedUser author = accounts._parse(ident.getName());
    if (author == null) {
      author = accounts._parse(ident.getEmailAddress());
    }
    if (author != null) {
      return AccountInfo.parse(author.getAccount(), true);
    } else {
      return AccountInfo.parse(ident, true);
    }
  }

  private class UserStatistics {
    @SuppressWarnings("unused")
    final AccountInfo account;

    final CommitStatistics all_time = new CommitStatistics();
    final CommitStatistics last_12_months = new CommitStatistics();
    final CommitStatistics last_30_days = new CommitStatistics();

    UserStatistics(AccountInfo account) {
      this.account = account;
    }
  }

  @SuppressWarnings("unused")
  private class CommitStatistics {
    int count = 0;
    int percentage = 0;

    public void computePercentage(int totalCommitCount) {
      percentage = count * 100 / totalCommitCount;
    }
  }
}
