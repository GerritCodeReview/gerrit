// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.common.collect.ImmutableList;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.AbstractAdvertiseRefsHook;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;

/**
 * Wrapper around {@link com.google.gerrit.server.permissions.PermissionBackend.ForProject} that
 * implements {@link org.eclipse.jgit.transport.AdvertiseRefsHook}.
 */
public class DefaultAdvertiseRefsHook extends AbstractAdvertiseRefsHook {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  private final PermissionBackend.ForProject perm;
  private final PermissionBackend.RefFilterOptions opts;

  public DefaultAdvertiseRefsHook(
      PermissionBackend.ForProject perm, PermissionBackend.RefFilterOptions opts) {
    this.perm = perm;
    this.opts = opts;
  }

  @Override
  protected Map<String, Ref> getAdvertisedRefs(Repository repo, RevWalk revWalk)
      throws ServiceMayNotContinueException {
    logger.atFine().log("ref filter options = %s", opts);
    try {
      List<String> prefixes =
          !opts.prefixes().isEmpty() ? opts.prefixes() : ImmutableList.of(RefDatabase.ALL);
      return perm.filter(
          repo.getRefDatabase().getRefsByPrefix(prefixes.toArray(new String[0])), repo, opts);
    } catch (IOException | PermissionBackendException e) {
      ServiceMayNotContinueException ex = new ServiceMayNotContinueException();
      ex.initCause(e);
      throw ex;
    }
  }
}
