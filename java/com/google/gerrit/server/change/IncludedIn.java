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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.common.collect.ImmutableSortedSet.toImmutableSortedSet;
import static java.util.Comparator.naturalOrder;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.ListMultimap;
import com.google.common.collect.Lists;
import com.google.common.collect.MultimapBuilder;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.Project;
import com.google.gerrit.extensions.api.changes.IncludedInInfo;
import com.google.gerrit.extensions.config.ExternalIncludedIn;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceConflictException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.plugincontext.PluginSetContext;
import com.google.gerrit.server.plugincontext.PluginSetEntryContext;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

@Singleton
public class IncludedIn {
  private final GitRepositoryManager repoManager;
  private final PermissionBackend permissionBackend;
  private final PluginSetContext<ExternalIncludedIn> externalIncludedIn;
  private final PluginSetContext<FilterIncludedIn> filterIncludedIn;

  @Inject
  IncludedIn(
      GitRepositoryManager repoManager,
      PermissionBackend permissionBackend,
      PluginSetContext<ExternalIncludedIn> externalIncludedIn,
      PluginSetContext<FilterIncludedIn> filterIncludedIn) {
    this.repoManager = repoManager;
    this.permissionBackend = permissionBackend;
    this.externalIncludedIn = externalIncludedIn;
    this.filterIncludedIn = filterIncludedIn;
  }

  public IncludedInInfo apply(Project.NameKey project, String revisionId)
      throws RestApiException, IOException, PermissionBackendException {
    return apply(project, null, revisionId);
  }

  public IncludedInInfo apply(
      Project.NameKey project, @Nullable Change.Id changeId, String revisionId)
      throws RestApiException, IOException, PermissionBackendException {
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

      RefDatabase refDb = r.getRefDatabase();
      List<Ref> tags = refDb.getRefsByPrefix(Constants.R_TAGS);
      List<Ref> branches = refDb.getRefsByPrefix(Constants.R_HEADS);
      List<Ref> allTagsAndBranches = Lists.newArrayListWithCapacity(tags.size() + branches.size());
      allTagsAndBranches.addAll(tags);
      allTagsAndBranches.addAll(branches);

      Set<String> allMatchingTagsAndBranches =
          rw.getMergedInto(rev, IncludedInUtil.getSortedRefs(allTagsAndBranches, rw)).stream()
              .map(Ref::getName)
              .collect(Collectors.toSet());

      // Filter branches and tags according to their visbility by the user
      Stream<String> filteredBranchesStream =
          sortedShortNames(
              filterReadableRefs(
                  project, getMatchingRefNames(allMatchingTagsAndBranches, branches)));
      Stream<String> filteredTagsStream =
          sortedShortNames(
              filterReadableRefs(project, getMatchingRefNames(allMatchingTagsAndBranches, tags)));
      for (PluginSetEntryContext<FilterIncludedIn> pluginFilter : filterIncludedIn) {
        filteredBranchesStream =
            filteredBranchesStream.filter(pluginFilter.get().getBranchFilter(project, rev));
        filteredTagsStream =
            filteredTagsStream.filter(pluginFilter.get().getTagFilter(project, rev));
      }
      ImmutableSortedSet<String> filteredBranches =
          filteredBranchesStream.collect(toImmutableSortedSet(naturalOrder()));
      ImmutableSortedSet<String> filteredTags =
          filteredTagsStream.collect(toImmutableSortedSet(naturalOrder()));

      ListMultimap<String, String> external = MultimapBuilder.hashKeys().arrayListValues().build();
      externalIncludedIn.runEach(
          ext -> {
            ListMultimap<String, String> extIncludedIns =
                ext.getIncludedIn(
                    project.get(), changeId.get(), rev.name(), filteredBranches, filteredTags);
            if (extIncludedIns != null) {
              external.putAll(extIncludedIns);
            }
          });

      return new IncludedInInfo(
          filteredBranches, filteredTags, (!external.isEmpty() ? external.asMap() : null));
    }
  }

  /**
   * Filter readable branches or tags according to the caller's refs visibility.
   *
   * @param project specific Gerrit project.
   * @param inputRefs a list of branches (in short name) as strings
   */
  private ImmutableList<String> filterReadableRefs(
      Project.NameKey project, ImmutableList<Ref> inputRefs)
      throws IOException, PermissionBackendException {
    PermissionBackend.ForProject perm = permissionBackend.currentUser().project(project);
    try (Repository repo = repoManager.openRepository(project)) {
      return perm.filter(inputRefs, repo, RefFilterOptions.defaults()).stream()
          .map(Ref::getName)
          .collect(toImmutableList());
    }
  }

  /**
   * Returns the short names of refs which are as well in the matchingRefs list as well as in the
   * allRef list.
   */
  private static ImmutableList<Ref> getMatchingRefNames(
      Set<String> matchingRefs, Collection<Ref> allRefs) {
    return allRefs.stream()
        .filter(r -> matchingRefs.contains(r.getName()))
        .distinct()
        .collect(toImmutableList());
  }

  private Stream<String> sortedShortNames(Collection<String> refs) {
    return refs.stream().map(Repository::shortenRefName).sorted(naturalOrder());
  }
}
