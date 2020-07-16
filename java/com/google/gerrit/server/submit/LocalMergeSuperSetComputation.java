// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.submit;

import static com.google.common.base.Preconditions.checkState;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableListMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.entities.BranchNameKey;
import com.google.gerrit.entities.Project;
import com.google.gerrit.entities.SubmitTypeRecord;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.client.SubmitType;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.ChangeIsVisibleToPredicate;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gerrit.server.submit.MergeOpRepoManager.OpenRepo;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevSort;

/**
 * Default implementation of MergeSuperSet that does the computation of the merge super set
 * sequentially on the local Gerrit instance.
 */
public class LocalMergeSuperSetComputation implements MergeSuperSetComputation {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  public static class Module extends AbstractModule {
    @Override
    protected void configure() {
      DynamicItem.bind(binder(), MergeSuperSetComputation.class)
          .to(LocalMergeSuperSetComputation.class);
    }
  }

  @AutoValue
  abstract static class QueryKey {
    private static QueryKey create(BranchNameKey branch, Iterable<String> hashes) {
      return new AutoValue_LocalMergeSuperSetComputation_QueryKey(
          branch, ImmutableSet.copyOf(hashes));
    }

    abstract BranchNameKey branch();

    abstract ImmutableSet<String> hashes();
  }

  private final PermissionBackend permissionBackend;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Map<QueryKey, ImmutableList<ChangeData>> queryCache;
  private final Map<BranchNameKey, Optional<RevCommit>> heads;
  private final ProjectCache projectCache;
  private final ChangeIsVisibleToPredicate.Factory changeIsVisibleToPredicateFactory;

  @Inject
  LocalMergeSuperSetComputation(
      PermissionBackend permissionBackend,
      Provider<InternalChangeQuery> queryProvider,
      ProjectCache projectCache,
      ChangeIsVisibleToPredicate.Factory changeIsVisibleToPredicateFactory) {
    this.projectCache = projectCache;
    this.permissionBackend = permissionBackend;
    this.queryProvider = queryProvider;
    this.queryCache = new HashMap<>();
    this.heads = new HashMap<>();
    this.changeIsVisibleToPredicateFactory = changeIsVisibleToPredicateFactory;
  }

  @Override
  public ChangeSet completeWithoutTopic(
      MergeOpRepoManager orm, ChangeSet changeSet, CurrentUser user)
      throws IOException, PermissionBackendException {
    Collection<ChangeData> visibleChanges = new ArrayList<>();
    Collection<ChangeData> nonVisibleChanges = new ArrayList<>();

    // For each target branch we run a separate rev walk to find open changes
    // reachable from changes already in the merge super set.
    ImmutableListMultimap<BranchNameKey, ChangeData> bc =
        byBranch(Iterables.concat(changeSet.changes(), changeSet.nonVisibleChanges()));
    for (BranchNameKey b : bc.keySet()) {
      OpenRepo or = getRepo(orm, b.project());
      List<RevCommit> visibleCommits = new ArrayList<>();
      List<RevCommit> nonVisibleCommits = new ArrayList<>();
      for (ChangeData cd : bc.get(b)) {
        boolean visible = isVisible(changeSet, cd, user);

        if (submitType(cd) == SubmitType.CHERRY_PICK) {
          if (visible) {
            visibleChanges.add(cd);
          } else {
            nonVisibleChanges.add(cd);
          }

          continue;
        }

        // Get the underlying git commit object
        RevCommit commit = or.rw.parseCommit(cd.currentPatchSet().commitId());

        // Always include the input, even if merged. This allows
        // SubmitStrategyOp to correct the situation later, assuming it gets
        // returned by byCommitsOnBranchNotMerged below.
        if (visible) {
          visibleCommits.add(commit);
        } else {
          nonVisibleCommits.add(commit);
        }
      }

      Set<String> visibleHashes =
          walkChangesByHashes(visibleCommits, Collections.emptySet(), or, b);
      Set<String> nonVisibleHashes = walkChangesByHashes(nonVisibleCommits, visibleHashes, or, b);

      ChangeSet partialSet =
          byCommitsOnBranchNotMerged(or, b, visibleHashes, nonVisibleHashes, user);
      Iterables.addAll(visibleChanges, partialSet.changes());
      Iterables.addAll(nonVisibleChanges, partialSet.nonVisibleChanges());
    }

    return new ChangeSet(visibleChanges, nonVisibleChanges);
  }

  private static ImmutableListMultimap<BranchNameKey, ChangeData> byBranch(
      Iterable<ChangeData> changes) {
    ImmutableListMultimap.Builder<BranchNameKey, ChangeData> builder =
        ImmutableListMultimap.builder();
    for (ChangeData cd : changes) {
      builder.put(cd.change().getDest(), cd);
    }
    return builder.build();
  }

  private OpenRepo getRepo(MergeOpRepoManager orm, Project.NameKey project) throws IOException {
    try {
      OpenRepo or = orm.getRepo(project);
      checkState(or.rw.hasRevSort(RevSort.TOPO));
      return or;
    } catch (NoSuchProjectException e) {
      throw new IOException(e);
    }
  }

  private boolean isVisible(ChangeSet changeSet, ChangeData cd, CurrentUser user)
      throws PermissionBackendException {
    boolean statePermitsRead =
        projectCache.get(cd.project()).map(ProjectState::statePermitsRead).orElse(false);
    boolean visible = statePermitsRead && changeSet.ids().contains(cd.getId());
    if (!visible) {
      return false;
    }

    try {
      permissionBackend.user(user).change(cd).check(ChangePermission.READ);
      return true;
    } catch (AuthException e) {
      // We thought the change was visible, but it isn't.
      // This can happen if the ACL changes during the
      // completeChangeSet computation, for example.
      return false;
    }
  }

  private SubmitType submitType(ChangeData cd) {
    SubmitTypeRecord str = cd.submitTypeRecord();
    if (!str.isOk()) {
      logErrorAndThrow("Failed to get submit type for " + cd.getId() + ": " + str.errorMessage);
    }
    return str.type;
  }

  private ChangeSet byCommitsOnBranchNotMerged(
      OpenRepo or,
      BranchNameKey branch,
      Set<String> visibleHashes,
      Set<String> nonVisibleHashes,
      CurrentUser user)
      throws IOException {
    List<ChangeData> potentiallyVisibleChanges =
        byCommitsOnBranchNotMerged(or, branch, visibleHashes);
    List<ChangeData> invisibleChanges =
        new ArrayList<>(byCommitsOnBranchNotMerged(or, branch, nonVisibleHashes));
    List<ChangeData> visibleChanges = new ArrayList<>(potentiallyVisibleChanges.size());
    ChangeIsVisibleToPredicate changeIsVisibleToPredicate =
        changeIsVisibleToPredicateFactory.forUser(user);
    for (ChangeData cd : potentiallyVisibleChanges) {
      if (changeIsVisibleToPredicate.match(cd)) {
        visibleChanges.add(cd);
      } else {
        invisibleChanges.add(cd);
      }
    }
    return new ChangeSet(visibleChanges, invisibleChanges);
  }

  private ImmutableList<ChangeData> byCommitsOnBranchNotMerged(
      OpenRepo or, BranchNameKey branch, Set<String> hashes) throws IOException {
    if (hashes.isEmpty()) {
      return ImmutableList.of();
    }
    QueryKey k = QueryKey.create(branch, hashes);
    if (queryCache.containsKey(k)) {
      return queryCache.get(k);
    }
    ImmutableList<ChangeData> result =
        ImmutableList.copyOf(
            queryProvider.get().byCommitsOnBranchNotMerged(or.repo, branch, hashes));
    queryCache.put(k, result);
    return result;
  }

  private Set<String> walkChangesByHashes(
      Collection<RevCommit> sourceCommits, Set<String> ignoreHashes, OpenRepo or, BranchNameKey b)
      throws IOException {
    Set<String> destHashes = new HashSet<>();
    or.rw.reset();
    markHeadUninteresting(or, b);
    for (RevCommit c : sourceCommits) {
      String name = c.name();
      if (ignoreHashes.contains(name)) {
        continue;
      }
      destHashes.add(name);
      or.rw.markStart(c);
    }
    for (RevCommit c : or.rw) {
      String name = c.name();
      if (ignoreHashes.contains(name)) {
        continue;
      }
      destHashes.add(name);
    }

    return destHashes;
  }

  private void markHeadUninteresting(OpenRepo or, BranchNameKey b) throws IOException {
    Optional<RevCommit> head = heads.get(b);
    if (head == null) {
      Ref ref = or.repo.getRefDatabase().exactRef(b.branch());
      head = ref != null ? Optional.of(or.rw.parseCommit(ref.getObjectId())) : Optional.empty();
      heads.put(b, head);
    }
    if (head.isPresent()) {
      or.rw.markUninteresting(head.get());
    }
  }

  private void logErrorAndThrow(String msg) {
    logger.atSevere().log(msg);
    throw new StorageException(msg);
  }
}
