// Copyright (C) 2014 The Android Open Source Project
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

import com.google.common.base.Function;
import com.google.common.collect.Lists;
import com.google.gerrit.extensions.common.GitPerson;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CommonConverters;
import com.google.gerrit.server.args4j.TimestampHandler;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class GetReflog implements RestReadView<BranchResource> {
  private final GitRepositoryManager repoManager;

  @Option(name = "--limit", aliases = {"-n"}, metaVar = "CNT",
      usage = "maximum number of reflog entries to list")
  public GetReflog setLimit(int limit) {
    this.limit = limit;
    return this;
  }

  @Option(name = "--from", metaVar = "TIMESTAMP",
      usage = "timestamp from which the reflog entries should be listed (UTC, format: "
          + TimestampHandler.TIMESTAMP_FORMAT + ")")
  public GetReflog setFrom(Timestamp from) {
    this.from = from;
    return this;
  }

  @Option(name = "--to", metaVar = "TIMESTAMP",
      usage = "timestamp until which the reflog entries should be listed (UTC, format: "
          + TimestampHandler.TIMESTAMP_FORMAT + ")")
  public GetReflog setTo(Timestamp to) {
    this.to = to;
    return this;
  }

  private int limit;
  private Timestamp from;
  private Timestamp to;

  @Inject
  public GetReflog(GitRepositoryManager repoManager) {
    this.repoManager = repoManager;
  }

  @Override
  public List<ReflogEntryInfo> apply(BranchResource rsrc) throws AuthException,
      ResourceNotFoundException, RepositoryNotFoundException, IOException {
    if (!rsrc.getControl().isOwner()) {
      throw new AuthException("not project owner");
    }

    Repository repo = repoManager.openRepository(rsrc.getNameKey());
    try {
      ReflogReader r = repo.getReflogReader(rsrc.getRef());
      if (r == null) {
        throw new ResourceNotFoundException(rsrc.getRef());
      }
      List<ReflogEntry> entries;
      if (from == null && to == null) {
        entries =
            limit > 0 ? r.getReverseEntries(limit) : r.getReverseEntries();
      } else {
        entries = limit > 0
            ? new ArrayList<ReflogEntry>(limit)
            : new ArrayList<ReflogEntry>();
        for (ReflogEntry e : r.getReverseEntries()) {
          Timestamp timestamp = new Timestamp(e.getWho().getWhen().getTime());
          if ((from == null || from.before(timestamp)) &&
              (to == null || to.after(timestamp))) {
            entries.add(e);
          }
          if (limit > 0 && entries.size() >= limit) {
            break;
          }
        }
      }
      return Lists.transform(entries, new Function<ReflogEntry, ReflogEntryInfo>() {
        @Override
        public ReflogEntryInfo apply(ReflogEntry e) {
          return new ReflogEntryInfo(e);
        }});
    } finally {
      repo.close();
    }
  }

  public static class ReflogEntryInfo {
    public String oldId;
    public String newId;
    public GitPerson who;
    public String comment;

    public ReflogEntryInfo(ReflogEntry e) {
      oldId = e.getOldId().getName();
      newId = e.getNewId().getName();
      who = CommonConverters.toGitPerson(e.getWho());
      comment = e.getComment();
    }
  }
}
