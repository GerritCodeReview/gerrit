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

package com.google.gerrit.server.permissions;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.common.base.Preconditions.checkState;
import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CACHE_AUTOMERGE;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CONFIG;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_USERS_SELF;
import static java.util.Objects.requireNonNull;
import static java.util.stream.Collectors.toMap;

import com.google.auto.value.AutoValue;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.exceptions.StorageException;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.metrics.Counter0;
import com.google.gerrit.metrics.Description;
import com.google.gerrit.metrics.MetricMaker;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Branch;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.git.SearchingChangeCacheImpl;
import com.google.gerrit.server.git.TagCache;
import com.google.gerrit.server.git.TagMatcher;
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.permissions.PermissionBackend.RefFilterOptions;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;

class DefaultRefFilter {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();

  interface Factory {
    DefaultRefFilter create(ProjectControl projectControl);
  }

  private final TagCache tagCache;
  private final ChangeNotes.Factory changeNotesFactory;
  @Nullable private final SearchingChangeCacheImpl changeCache;
  private final GroupCache groupCache;
  private final PermissionBackend permissionBackend;
  private final ProjectControl projectControl;
  private final CurrentUser user;
  private final ProjectState projectState;
  private final PermissionBackend.ForProject permissionBackendForProject;
  private final Counter0 fullFilterCount;
  private final Counter0 skipFilterCount;
  private final boolean skipFullRefEvaluationIfAllRefsAreVisible;

  private Map<Change.Id, Branch.NameKey> visibleChanges;

  @Inject
  DefaultRefFilter(
      TagCache tagCache,
      ChangeNotes.Factory changeNotesFactory,
      @Nullable SearchingChangeCacheImpl changeCache,
      GroupCache groupCache,
      PermissionBackend permissionBackend,
      @GerritServerConfig Config config,
      MetricMaker metricMaker,
      @Assisted ProjectControl projectControl) {
    this.tagCache = tagCache;
    this.changeNotesFactory = changeNotesFactory;
    this.changeCache = changeCache;
    this.groupCache = groupCache;
    this.permissionBackend = permissionBackend;
    this.skipFullRefEvaluationIfAllRefsAreVisible =
        config.getBoolean("auth", "skipFullRefEvaluationIfAllRefsAreVisible", true);
    this.projectControl = projectControl;

    this.user = projectControl.getUser();
    this.projectState = projectControl.getProjectState();
    this.permissionBackendForProject =
        permissionBackend.user(user).project(projectState.getNameKey());
    this.fullFilterCount =
        metricMaker.newCounter(
            "permissions/ref_filter/full_filter_count",
            new Description("Rate of full ref filter operations").setRate());
    this.skipFilterCount =
        metricMaker.newCounter(
            "permissions/ref_filter/skip_filter_count",
            new Description(
                    "Rate of ref filter operations where we skip full evaluation"
                        + " because the user can read all refs")
                .setRate());
  }

  /** Filters given refs and tags by visibility. */
  Map<String, Ref> filter(Map<String, Ref> refs, Repository repo, RefFilterOptions opts)
      throws PermissionBackendException {
    // See if we can get away with a single, cheap ref evaluation.
    if (refs.size() == 1) {
      String refName = Iterables.getOnlyElement(refs.values()).getName();
      if (opts.filterMeta() && isMetadata(refName)) {
        return ImmutableMap.of();
      }
      if (RefNames.isRefsChanges(refName)) {
        return canSeeSingleChangeRef(refName) ? refs : ImmutableMap.of();
      }
    }

    // Perform an initial ref filtering with all the refs the caller asked for. If we find tags that
    // we have to investigate (deferred tags) separately then perform a reachability check starting
    // from all visible branches (refs/heads/*).
    Result initialRefFilter = filterRefs(refs, repo, opts);
    Map<String, Ref> visibleRefs = initialRefFilter.visibleRefs();
    if (!initialRefFilter.deferredTags().isEmpty()) {
      Result allVisibleBranches = filterRefs(getTaggableRefsMap(repo), repo, opts);
      checkState(
          allVisibleBranches.deferredTags().isEmpty(),
          "unexpected tags found when filtering refs/heads/* " + allVisibleBranches.deferredTags());

      TagMatcher tags =
          tagCache
              .get(projectState.getNameKey())
              .matcher(tagCache, repo, allVisibleBranches.visibleRefs().values());
      for (Ref tag : initialRefFilter.deferredTags()) {
        try {
          if (tags.isReachable(tag)) {
            visibleRefs.put(tag.getName(), tag);
          }
        } catch (IOException e) {
          throw new PermissionBackendException(e);
        }
      }
    }
    return visibleRefs;
  }

  /**
   * Filters refs by visibility. Returns tags where visibility can't be trivially computed
   * separately for later ref-walk-based visibility computation. Tags where visibility is trivial to
   * compute will be returned as part of {@link Result#visibleRefs()}.
   */
  Result filterRefs(Map<String, Ref> refs, Repository repo, RefFilterOptions opts)
      throws PermissionBackendException {
    if (projectState.isAllUsers()) {
      refs = addUsersSelfSymref(refs);
    }

    // TODO(hiesel): Remove when optimization is done.
    boolean hasReadOnRefsStar =
        checkProjectPermission(permissionBackendForProject, ProjectPermission.READ);
    if (skipFullRefEvaluationIfAllRefsAreVisible && !projectState.isAllUsers()) {
      if (projectState.statePermitsRead() && hasReadOnRefsStar) {
        skipFilterCount.increment();
        return new AutoValue_DefaultRefFilter_Result(refs, ImmutableList.of());
      } else if (projectControl.allRefsAreVisible(ImmutableSet.of(RefNames.REFS_CONFIG))) {
        skipFilterCount.increment();
        return new AutoValue_DefaultRefFilter_Result(
            fastHideRefsMetaConfig(refs), ImmutableList.of());
      }
    }
    fullFilterCount.increment();

    boolean viewMetadata;
    boolean isAdmin;
    Account.Id userId;
    IdentifiedUser identifiedUser;
    PermissionBackend.WithUser withUser = permissionBackend.user(user);
    if (user.isIdentifiedUser()) {
      viewMetadata = withUser.testOrFalse(GlobalPermission.ACCESS_DATABASE);
      isAdmin = withUser.testOrFalse(GlobalPermission.ADMINISTRATE_SERVER);
      identifiedUser = user.asIdentifiedUser();
      userId = identifiedUser.getAccountId();
    } else {
      viewMetadata = false;
      isAdmin = false;
      userId = null;
      identifiedUser = null;
    }

    Map<String, Ref> result = new HashMap<>();
    List<Ref> deferredTags = new ArrayList<>();
    for (Ref ref : refs.values()) {
      String name = ref.getName();
      Change.Id changeId;
      Account.Id accountId;
      AccountGroup.UUID accountGroupUuid;
      if (name.startsWith(REFS_CACHE_AUTOMERGE) || (opts.filterMeta() && isMetadata(name))) {
        continue;
      } else if (RefNames.isRefsEdit(name)) {
        // Edits are visible only to the owning user, if change is visible.
        if (viewMetadata || visibleEdit(repo, name)) {
          result.put(name, ref);
        }
      } else if ((changeId = Change.Id.fromRef(name)) != null) {
        // Change ref is visible only if the change is visible.
        if (viewMetadata || visible(repo, changeId)) {
          result.put(name, ref);
        }
      } else if ((accountId = Account.Id.fromRef(name)) != null) {
        // Account ref is visible only to the corresponding account.
        if (viewMetadata || (accountId.equals(userId) && canReadRef(name))) {
          result.put(name, ref);
        }
      } else if ((accountGroupUuid = AccountGroup.UUID.fromRef(name)) != null) {
        // Group ref is visible only to the corresponding owner group.
        InternalGroup group = groupCache.get(accountGroupUuid).orElse(null);
        if (viewMetadata
            || (group != null
                && isGroupOwner(group, identifiedUser, isAdmin)
                && canReadRef(name))) {
          result.put(name, ref);
        }
      } else if (isTag(ref)) {
        if (hasReadOnRefsStar) {
          // The user has READ on refs/*. This is the broadest permission one can assign. There is
          // no way to grant access to (specific) tags in Gerrit, so we have to assume that these
          // users can see all tags because there could be tags that aren't reachable by any visible
          // ref while the user can see all non-Gerrit refs. This matches Gerrit's historic
          // behavior.
          // This makes it so that these users could see commits that they can't see otherwise
          // (e.g. a private change ref) if a tag was attached to it. Tags are meant to be used on
          // the regular Git tree that users interact with, not on any of the Gerrit trees, so this
          // is a negligible risk.
          result.put(name, ref);
        } else {
          // If its a tag, consider it later.
          if (ref.getObjectId() != null) {
            deferredTags.add(ref);
          }
        }
      } else if (name.startsWith(RefNames.REFS_SEQUENCES)) {
        // Sequences are internal database implementation details.
        if (viewMetadata) {
          result.put(name, ref);
        }
      } else if (projectState.isAllUsers()
          && (name.equals(RefNames.REFS_EXTERNAL_IDS) || name.equals(RefNames.REFS_GROUPNAMES))) {
        // The notes branches with the external IDs / group names must not be exposed to normal
        // users.
        if (viewMetadata) {
          result.put(name, ref);
        }
      } else if (canReadRef(ref.getLeaf().getName())) {
        // Use the leaf to lookup the control data. If the reference is
        // symbolic we want the control around the final target. If its
        // not symbolic then getLeaf() is a no-op returning ref itself.
        result.put(name, ref);
      } else if (isRefsUsersSelf(ref)) {
        // viewMetadata allows to see all account refs, hence refs/users/self should be included as
        // well
        if (viewMetadata) {
          result.put(name, ref);
        }
      }
    }
    return new AutoValue_DefaultRefFilter_Result(result, deferredTags);
  }

  /**
   * Returns all refs tag we regard as starting points for reachability computation for tags. In
   * general, these are all refs not managed by Gerrit excluding symbolic refs and tags.
   *
   * <p>We exclude symbolic refs because their target will be included and this will suffice for
   * computing reachability.
   */
  private static Map<String, Ref> getTaggableRefsMap(Repository repo)
      throws PermissionBackendException {
    try {
      return repo.getRefDatabase().getRefs().stream()
          .filter(
              r ->
                  !RefNames.isGerritRef(r.getName())
                      && !r.getName().startsWith(RefNames.REFS_TAGS)
                      && !r.isSymbolic())
          .collect(toMap(Ref::getName, r -> r));
    } catch (IOException e) {
      throw new PermissionBackendException(e);
    }
  }

  private Map<String, Ref> fastHideRefsMetaConfig(Map<String, Ref> refs)
      throws PermissionBackendException {
    if (refs.containsKey(REFS_CONFIG) && !canReadRef(REFS_CONFIG)) {
      Map<String, Ref> r = new HashMap<>(refs);
      r.remove(REFS_CONFIG);
      return r;
    }
    return refs;
  }

  private Map<String, Ref> addUsersSelfSymref(Map<String, Ref> refs) {
    if (user.isIdentifiedUser()) {
      Ref r = refs.get(RefNames.refsUsers(user.getAccountId()));
      if (r != null) {
        SymbolicRef s = new SymbolicRef(REFS_USERS_SELF, r);
        refs = new HashMap<>(refs);
        refs.put(s.getName(), s);
      }
    }
    return refs;
  }

  private boolean visible(Repository repo, Change.Id changeId) throws PermissionBackendException {
    if (visibleChanges == null) {
      if (changeCache == null) {
        visibleChanges = visibleChangesByScan(repo);
      } else {
        visibleChanges = visibleChangesBySearch();
      }
    }
    return visibleChanges.containsKey(changeId);
  }

  private boolean visibleEdit(Repository repo, String name) throws PermissionBackendException {
    Change.Id id = Change.Id.fromEditRefPart(name);
    // Initialize if it wasn't yet
    if (visibleChanges == null) {
      visible(repo, id);
    }
    if (id == null) {
      return false;
    }

    if (user.isIdentifiedUser()
        && name.startsWith(RefNames.refsEditPrefix(user.asIdentifiedUser().getAccountId()))
        && visible(repo, id)) {
      return true;
    }
    if (visibleChanges.containsKey(id)) {
      try {
        // Default to READ_PRIVATE_CHANGES as there is no special permission for reading edits.
        permissionBackendForProject
            .ref(visibleChanges.get(id).get())
            .check(RefPermission.READ_PRIVATE_CHANGES);
        return true;
      } catch (AuthException e) {
        return false;
      }
    }
    return false;
  }

  private Map<Change.Id, Branch.NameKey> visibleChangesBySearch()
      throws PermissionBackendException {
    Project.NameKey project = projectState.getNameKey();
    try {
      Map<Change.Id, Branch.NameKey> visibleChanges = new HashMap<>();
      for (ChangeData cd : changeCache.getChangeData(project)) {
        ChangeNotes notes = changeNotesFactory.createFromIndexedChange(cd.change());
        if (!projectState.statePermitsRead()) {
          continue;
        }
        try {
          permissionBackendForProject.indexedChange(cd, notes).check(ChangePermission.READ);
          visibleChanges.put(cd.getId(), cd.change().getDest());
        } catch (AuthException e) {
          // Do nothing.
        }
      }
      return visibleChanges;
    } catch (StorageException e) {
      logger.atSevere().withCause(e).log(
          "Cannot load changes for project %s, assuming no changes are visible", project);
      return Collections.emptyMap();
    }
  }

  private Map<Change.Id, Branch.NameKey> visibleChangesByScan(Repository repo)
      throws PermissionBackendException {
    Project.NameKey p = projectState.getNameKey();
    ImmutableList<ChangeNotesResult> changes;
    try {
      changes = changeNotesFactory.scan(repo, p).collect(toImmutableList());
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Cannot load changes for project %s, assuming no changes are visible", p);
      return Collections.emptyMap();
    }

    Map<Change.Id, Branch.NameKey> result = Maps.newHashMapWithExpectedSize(changes.size());
    for (ChangeNotesResult notesResult : changes) {
      ChangeNotes notes = toNotes(notesResult);
      if (notes != null) {
        result.put(notes.getChangeId(), notes.getChange().getDest());
      }
    }
    return result;
  }

  @Nullable
  private ChangeNotes toNotes(ChangeNotesResult r) throws PermissionBackendException {
    if (r.error().isPresent()) {
      logger.atWarning().withCause(r.error().get()).log(
          "Failed to load change %s in %s", r.id(), projectState.getName());
      return null;
    }

    if (!projectState.statePermitsRead()) {
      return null;
    }

    try {
      permissionBackendForProject.change(r.notes()).check(ChangePermission.READ);
      return r.notes();
    } catch (AuthException e) {
      // Skip.
    }
    return null;
  }

  private boolean isMetadata(String name) {
    return RefNames.isRefsChanges(name) || RefNames.isRefsEdit(name);
  }

  private static boolean isTag(Ref ref) {
    return ref.getLeaf().getName().startsWith(Constants.R_TAGS);
  }

  private static boolean isRefsUsersSelf(Ref ref) {
    return ref.getName().startsWith(REFS_USERS_SELF);
  }

  private boolean canReadRef(String ref) throws PermissionBackendException {
    try {
      permissionBackendForProject.ref(ref).check(RefPermission.READ);
    } catch (AuthException e) {
      return false;
    }
    return projectState.statePermitsRead();
  }

  private boolean checkProjectPermission(
      PermissionBackend.ForProject forProject, ProjectPermission perm)
      throws PermissionBackendException {
    try {
      forProject.check(perm);
    } catch (AuthException e) {
      return false;
    }
    return true;
  }

  private boolean isGroupOwner(
      InternalGroup group, @Nullable IdentifiedUser user, boolean isAdmin) {
    requireNonNull(group);

    // Keep this logic in sync with GroupControl#isOwner().
    return isAdmin
        || (user != null && user.getEffectiveGroups().contains(group.getOwnerGroupUUID()));
  }

  /**
   * Returns true if the user can see the provided change ref. Uses NoteDb for evaluation, hence
   * does not suffer from the limitations documented in {@link SearchingChangeCacheImpl}.
   *
   * <p>This code lets users fetch changes that are not among the fraction of most recently modified
   * changes that {@link SearchingChangeCacheImpl} returns. This works only when Git Protocol v2
   * with refs-in-wants is used as that enables Gerrit to skip traditional advertisement of all
   * visible refs.
   */
  private boolean canSeeSingleChangeRef(String refName) throws PermissionBackendException {
    // We are treating just a single change ref. We are therefore not going through regular ref
    // filtering, but use NoteDb directly. This makes it so that we can always serve this ref
    // even if the change is not part of the set of most recent changes that
    // SearchingChangeCacheImpl returns.
    Change.Id cId = Change.Id.fromRef(refName);
    checkNotNull(cId, "invalid change id for ref %s", refName);
    ChangeNotes notes;
    try {
      notes = changeNotesFactory.create(projectState.getNameKey(), cId);
    } catch (StorageException e) {
      throw new PermissionBackendException("can't construct change notes", e);
    }
    try {
      permissionBackendForProject.change(notes).check(ChangePermission.READ);
      return true;
    } catch (AuthException e) {
      return false;
    }
  }

  @AutoValue
  abstract static class Result {
    /** Subset of the refs passed into the computation that is visible to the user. */
    abstract Map<String, Ref> visibleRefs();

    /**
     * List of tags where we couldn't figure out visibility in the first pass and need to do an
     * expensive ref walk.
     */
    abstract List<Ref> deferredTags();
  }
}
