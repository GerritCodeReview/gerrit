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

package com.google.gerrit.server.restapi.project;

import com.google.common.collect.Lists;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.extensions.api.projects.ReflogEntryInfo;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.CommonConverters;
import com.google.gerrit.server.args4j.InstantHandler;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.BranchResource;
import com.google.inject.Inject;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.eclipse.jgit.lib.ReflogEntry;
import org.eclipse.jgit.lib.ReflogReader;
import org.eclipse.jgit.lib.Repository;
import org.kohsuke.args4j.Option;

public class GetReflog implements RestReadView<BranchResource> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final GitRepositoryManager repoManager;
  private final PermissionBackend permissionBackend;

  @Option(
      name = "--limit",
      aliases = {"-n"},
      metaVar = "CNT",
      usage = "maximum number of reflog entries to list")
  public GetReflog setLimit(int limit) {
    this.limit = limit;
    return this;
  }

  @Option(
      name = "--from",
      metaVar = "TIMESTAMP",
      usage =
          "timestamp from which the reflog entries should be listed (UTC, format: "
              + InstantHandler.TIMESTAMP_FORMAT
              + ")")
  public GetReflog setFrom(Instant from) {
    this.from = from;
    return this;
  }

  @Option(
      name = "--to",
      metaVar = "TIMESTAMP",
      usage =
          "timestamp until which the reflog entries should be listed (UTC, format: "
              + InstantHandler.TIMESTAMP_FORMAT
              + ")")
  public GetReflog setTo(Instant to) {
    this.to = to;
    return this;
  }

  private int limit;
  private Instant from;
  private Instant to;

  @Inject
  public GetReflog(GitRepositoryManager repoManager, PermissionBackend permissionBackend) {
    this.repoManager = repoManager;
    this.permissionBackend = permissionBackend;
  }

  @Override
  public Response<List<ReflogEntryInfo>> apply(BranchResource rsrc)
      throws RestApiException, IOException, PermissionBackendException {
    permissionBackend
        .user(rsrc.getUser())
        .project(rsrc.getNameKey())
        .check(ProjectPermission.READ_REFLOG);

    try (Repository repo = repoManager.openRepository(rsrc.getNameKey())) {
      ReflogReader r;
      try {
        r = repo.getReflogReader(rsrc.getRef());
      } catch (UnsupportedOperationException e) {
        String msg = "reflog not supported on repo " + rsrc.getNameKey().get();
        logger.atSevere().log("%s", msg);
        throw new MethodNotAllowedException(msg, e);
      }
      if (r == null) {
        throw new ResourceNotFoundException(rsrc.getRef());
      }
      List<ReflogEntry> entries;
      if (from == null && to == null) {
        entries = limit > 0 ? r.getReverseEntries(limit) : r.getReverseEntries();
      } else {
        entries = limit > 0 ? new ArrayList<>(limit) : new ArrayList<>();
        for (ReflogEntry e : r.getReverseEntries()) {
          Instant timestamp = e.getWho().getWhenAsInstant();
          if ((from == null || from.isBefore(timestamp)) && (to == null || to.isAfter(timestamp))) {
            entries.add(e);
          }
          if (limit > 0 && entries.size() >= limit) {
            break;
          }
        }
      }
      return Response.ok(Lists.transform(entries, this::newReflogEntryInfo));
    }
  }

  private ReflogEntryInfo newReflogEntryInfo(ReflogEntry e) {
    return new ReflogEntryInfo(
        e.getOldId().getName(),
        e.getNewId().getName(),
        CommonConverters.toGitPerson(e.getWho()),
        e.getComment());
  }
}
