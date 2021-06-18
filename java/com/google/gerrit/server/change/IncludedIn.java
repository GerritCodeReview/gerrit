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

package com.google.gerrit.server.change;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.RefNames;
import com.google.gerrit.extensions.api.changes.IncludedInInfo;
import com.google.gerrit.extensions.config.ExternalIncludedIn;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class IncludedIn {
  private final GitRepositoryManager repoManager;
  private final PermissionBackend permissionBackend;
  private final PluginSetContext<ExternalIncludedIn> externalIncludedIn;

  @Inject
  IncludedIn(
      GitRepositoryManager repoManager,
      PermissionBackend permissionBackend,
      PluginSetContext<ExternalIncludedIn> externalIncludedIn) {
    this.repoManager = repoManager;
    this.permissionBackend = permissionBackend;
    this.externalIncludedIn = externalIncludedIn;
  }

  public IncludedInInfo apply(Project.NameKey project, String revisionId)
      throws RestApiException, IOException {
    try (Repository r = repoManager.openRepository(project);
        RevWalk rw = new RevWalk(r)) {
      rw.setRetainBody(false);
      RevCommit rev;
      try {
        rev = rw.parseCommit(ObjectId.fromString(revisionId));
      } catch (IncorrectObjectTypeException err) {
        throw new BadRequestException(err.getMessage());
      } catch (MissingObjectException err) {
        throw new ResourceConflictException(err.getMessage());
      }

      IncludedInResolver.Result d = IncludedInResolver.resolve(r, rw, rev);
      ListMultimap<String, String> external = MultimapBuilder.hashKeys().arrayListValues().build();
      externalIncludedIn.runEach(
          ext -> {
            ListMultimap<String, String> extIncludedIns =
                ext.getIncludedIn(project.get(), rev.name(), d.tags(), d.branches());
            if (extIncludedIns != null) {
              external.putAll(extIncludedIns);
            }
          });

      return new IncludedInInfo(
          filterReadableBranches(project, d.branches()),
          d.tags(),
          (!external.isEmpty() ? external.asMap() : null));
    }
  }

  /**
   * Filter readable branches according to ref read permissions.
   *
   * @param project specific Gerrit project.
   * @param inputRefs a list of branches (in short name) as strings
   */
  private ImmutableList<String> filterReadableBranches(
      Project.NameKey project, Collection<String> inputRefs) {
    PermissionBackend.ForProject perm = permissionBackend.currentUser().project(project);
    ImmutableList.Builder<String> out = ImmutableList.builder();
    for (String ref : inputRefs) {
      try {
        // Note that we convert the input refs to their full names since IncludedInResolver
        // shortens the branch and tag names.
        perm.ref(RefNames.fullName(ref)).check(RefPermission.READ);
        out.add(ref);
      } catch (@SuppressWarnings("unused") AuthException | PermissionBackendException e) {
        // Do nothing, just skip this ref
      }
    }
    return out.build();
  }
}
