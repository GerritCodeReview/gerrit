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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CACHE_AUTOMERGE;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CHANGES;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CONFIG;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_USERS_SELF;
import static java.util.Objects.requireNonNull;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.flogger.FluentLogger;
import com.google.gerrit.common.Nullable;
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
import com.google.gerrit.reviewdb.server.ReviewDb;
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
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
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
  private final Provider<ReviewDb> db;
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
      Provider<ReviewDb> db,
      GroupCache groupCache,
      PermissionBackend permissionBackend,
      @GerritServerConfig Config config,
      MetricMaker metricMaker,
      @Assisted ProjectControl projectControl) {
    this.tagCache = tagCache;
    this.changeNotesFactory = changeNotesFactory;
    this.changeCache = changeCache;
    this.db = db;
    this.groupCache = groupCache;
    this.permissionBackend = permissionBackend;
    this.skipFullRefEvaluationIfAllRefsAreVisible =
        config.getBoolean("auth", "skipFullRefEvaluationIfAllRefsAreVisible", true);
    this.projectControl = projectControl;

    this.user = projectControl.getUser();
    this.projectState = projectControl.getProjectState();
    this.permissionBackendForProject =
        permissionBackend.user(user).database(db).project(projectState.getNameKey());
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

  Map<String, Ref> filter(Map<String, Ref> refs, Repository repo, RefFilterOptions opts)
      throws PermissionBackendException {
    if (projectState.isAllUsers()) {
      refs = addUsersSelfSymref(refs);
    }

    boolean hasReadOnRefsStar =
        checkProjectPermission(permissionBackendForProject, ProjectPermission.READ);
    if (skipFullRefEvaluationIfAllRefsAreVisible && !projectState.isAllUsers()) {
      if (projectState.statePermitsRead() && hasReadOnRefsStar) {
        skipFilterCount.increment();
        return refs;
      } else if (projectControl.allRefsAreVisible(ImmutableSet.of(RefNames.REFS_CONFIG))) {
        skipFilterCount.increment();
        return fastHideRefsMetaConfig(refs);
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

    // If we have tags that were deferred, we need to do a revision walk
    // to identify what tags we can actually reach, and what we cannot.
    //
    if (!deferredTags.isEmpty() && (!result.isEmpty() || opts.filterTagsSeparately())) {
      TagMatcher tags =
          tagCache
              .get(projectState.getNameKey())
              .matcher(
                  tagCache,
                  repo,
                  opts.filterTagsSeparately()
                      ? filter(
                              repo.getAllRefs(),
                              repo,
                              opts.toBuilder().setFilterTagsSeparately(false).build())
                          .values()
                      : result.values());
      for (Ref tag : deferredTags) {
        if (tags.isReachable(tag)) {
          result.put(tag.getName(), tag);
        }
      }
    }

    return result;
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
      for (ChangeData cd : changeCache.getChangeData(db.get(), project)) {
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
    } catch (OrmException e) {
      logger.atSevere().withCause(e).log(
          "Cannot load changes for project %s, assuming no changes are visible", project);
      return Collections.emptyMap();
    }
  }

  private Map<Change.Id, Branch.NameKey> visibleChangesByScan(Repository repo)
      throws PermissionBackendException {
    Project.NameKey p = projectState.getNameKey();
    Stream<ChangeNotesResult> s;
    try {
      s = changeNotesFactory.scan(repo, p);
    } catch (IOException e) {
      logger.atSevere().withCause(e).log(
          "Cannot load changes for project %s, assuming no changes are visible", p);
      return Collections.emptyMap();
    }

    Map<Change.Id, Branch.NameKey> result = Maps.newHashMapWithExpectedSize((int) s.count());
    for (ChangeNotesResult notesResult : s.collect(toImmutableList())) {
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
    return name.startsWith(REFS_CHANGES) || RefNames.isRefsEdit(name);
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
}
