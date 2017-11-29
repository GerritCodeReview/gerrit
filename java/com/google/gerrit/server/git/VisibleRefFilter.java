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

package com.google.gerrit.server.git;

import static com.google.common.base.Preconditions.checkNotNull;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CACHE_AUTOMERGE;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CHANGES;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CONFIG;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_USERS_SELF;
import static java.util.stream.Collectors.toMap;

import com.google.gerrit.common.Nullable;
import com.google.gerrit.extensions.restapi.AuthException;
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
import com.google.gerrit.server.group.InternalGroup;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.notedb.ChangeNotes.Factory.ChangeNotesResult;
import com.google.gerrit.server.permissions.ChangePermission;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.permissions.RefPermission;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.index.change.ChangeData;
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
import java.util.Objects;
import java.util.stream.Stream;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefDatabase;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.lib.SymbolicRef;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.AbstractAdvertiseRefsHook;
import org.eclipse.jgit.transport.ServiceMayNotContinueException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class VisibleRefFilter extends AbstractAdvertiseRefsHook {
  private static final Logger log = LoggerFactory.getLogger(VisibleRefFilter.class);

  public interface Factory {
    VisibleRefFilter create(ProjectState projectState, Repository git);
  }

  private final TagCache tagCache;
  private final ChangeNotes.Factory changeNotesFactory;
  @Nullable private final SearchingChangeCacheImpl changeCache;
  private final Provider<ReviewDb> db;
  private final Provider<CurrentUser> user;
  private final GroupCache groupCache;
  private final PermissionBackend permissionBackend;
  private final PermissionBackend.ForProject perm;
  private final ProjectState projectState;
  private final Repository git;
  private boolean showMetadata = true;
  private String userEditPrefix;
  private Map<Change.Id, Branch.NameKey> visibleChanges;

  @Inject
  VisibleRefFilter(
      TagCache tagCache,
      ChangeNotes.Factory changeNotesFactory,
      @Nullable SearchingChangeCacheImpl changeCache,
      Provider<ReviewDb> db,
      Provider<CurrentUser> user,
      GroupCache groupCache,
      PermissionBackend permissionBackend,
      @Assisted ProjectState projectState,
      @Assisted Repository git) {
    this.tagCache = tagCache;
    this.changeNotesFactory = changeNotesFactory;
    this.changeCache = changeCache;
    this.db = db;
    this.user = user;
    this.groupCache = groupCache;
    this.permissionBackend = permissionBackend;
    this.perm =
        permissionBackend.user(user).database(db).project(projectState.getProject().getNameKey());
    this.projectState = projectState;
    this.git = git;
  }

  /** Show change references. Default is {@code true}. */
  public VisibleRefFilter setShowMetadata(boolean show) {
    showMetadata = show;
    return this;
  }

  public Map<String, Ref> filter(Map<String, Ref> refs, boolean filterTagsSeparately) {
    if (projectState.isAllUsers()) {
      refs = addUsersSelfSymref(refs);
    }

    PermissionBackend.WithUser withUser = permissionBackend.user(user);
    PermissionBackend.ForProject forProject = withUser.project(projectState.getNameKey());
    if (!projectState.isAllUsers()) {
      if (checkProjectPermission(forProject, ProjectPermission.READ)) {
        return refs;
      } else if (checkProjectPermission(forProject, ProjectPermission.READ_NO_CONFIG)) {
        return fastHideRefsMetaConfig(refs);
      }
    }

    boolean viewMetadata;
    boolean isAdmin;
    Account.Id userId;
    IdentifiedUser identifiedUser;
    if (user.get().isIdentifiedUser()) {
      viewMetadata = withUser.testOrFalse(GlobalPermission.ACCESS_DATABASE);
      isAdmin = withUser.testOrFalse(GlobalPermission.ADMINISTRATE_SERVER);
      identifiedUser = user.get().asIdentifiedUser();
      userId = identifiedUser.getAccountId();
      userEditPrefix = RefNames.refsEditPrefix(userId);
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
      if (name.startsWith(REFS_CACHE_AUTOMERGE) || (!showMetadata && isMetadata(name))) {
        continue;
      } else if (RefNames.isRefsEdit(name)) {
        // Edits are visible only to the owning user, if change is visible.
        if (viewMetadata || visibleEdit(name)) {
          result.put(name, ref);
        }
      } else if ((changeId = Change.Id.fromRef(name)) != null) {
        // Change ref is visible only if the change is visible.
        if (viewMetadata || visible(changeId)) {
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
        // If its a tag, consider it later.
        if (ref.getObjectId() != null) {
          deferredTags.add(ref);
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
    if (!deferredTags.isEmpty() && (!result.isEmpty() || filterTagsSeparately)) {
      TagMatcher tags =
          tagCache
              .get(projectState.getNameKey())
              .matcher(
                  tagCache,
                  git,
                  filterTagsSeparately ? filter(git.getAllRefs()).values() : result.values());
      for (Ref tag : deferredTags) {
        if (tags.isReachable(tag)) {
          result.put(tag.getName(), tag);
        }
      }
    }

    return result;
  }

  private Map<String, Ref> fastHideRefsMetaConfig(Map<String, Ref> refs) {
    if (refs.containsKey(REFS_CONFIG) && !canReadRef(REFS_CONFIG)) {
      Map<String, Ref> r = new HashMap<>(refs);
      r.remove(REFS_CONFIG);
      return r;
    }
    return refs;
  }

  private Map<String, Ref> addUsersSelfSymref(Map<String, Ref> refs) {
    if (user.get().isIdentifiedUser()) {
      Ref r = refs.get(RefNames.refsUsers(user.get().getAccountId()));
      if (r != null) {
        SymbolicRef s = new SymbolicRef(REFS_USERS_SELF, r);
        refs = new HashMap<>(refs);
        refs.put(s.getName(), s);
      }
    }
    return refs;
  }

  @Override
  protected Map<String, Ref> getAdvertisedRefs(Repository repository, RevWalk revWalk)
      throws ServiceMayNotContinueException {
    try {
      return filter(repository.getRefDatabase().getRefs(RefDatabase.ALL));
    } catch (ServiceMayNotContinueException e) {
      throw e;
    } catch (IOException e) {
      ServiceMayNotContinueException ex = new ServiceMayNotContinueException();
      ex.initCause(e);
      throw ex;
    }
  }

  private Map<String, Ref> filter(Map<String, Ref> refs) {
    return filter(refs, false);
  }

  private boolean visible(Change.Id changeId) {
    if (visibleChanges == null) {
      if (changeCache == null) {
        visibleChanges = visibleChangesByScan();
      } else {
        visibleChanges = visibleChangesBySearch();
      }
    }
    return visibleChanges.containsKey(changeId);
  }

  private boolean visibleEdit(String name) {
    Change.Id id = Change.Id.fromEditRefPart(name);
    // Initialize if it wasn't yet
    if (visibleChanges == null) {
      visible(id);
    }
    if (id == null) {
      return false;
    }
    if (userEditPrefix != null && name.startsWith(userEditPrefix) && visible(id)) {
      return true;
    }
    if (visibleChanges.containsKey(id)) {
      try {
        // Default to READ_PRIVATE_CHANGES as there is no special permission for reading edits.
        perm.ref(visibleChanges.get(id).get()).check(RefPermission.READ_PRIVATE_CHANGES);
        return true;
      } catch (PermissionBackendException | AuthException e) {
        return false;
      }
    }
    return false;
  }

  private Map<Change.Id, Branch.NameKey> visibleChangesBySearch() {
    Project.NameKey project = projectState.getNameKey();
    try {
      Map<Change.Id, Branch.NameKey> visibleChanges = new HashMap<>();
      for (ChangeData cd : changeCache.getChangeData(db.get(), project)) {
        ChangeNotes notes = changeNotesFactory.createFromIndexedChange(cd.change());
        if (perm.indexedChange(cd, notes).test(ChangePermission.READ)) {
          visibleChanges.put(cd.getId(), cd.change().getDest());
        }
      }
      return visibleChanges;
    } catch (OrmException | PermissionBackendException e) {
      log.error(
          "Cannot load changes for project " + project + ", assuming no changes are visible", e);
      return Collections.emptyMap();
    }
  }

  private Map<Change.Id, Branch.NameKey> visibleChangesByScan() {
    Project.NameKey p = projectState.getNameKey();
    Stream<ChangeNotesResult> s;
    try {
      s = changeNotesFactory.scan(git, db.get(), p);
    } catch (IOException e) {
      log.error("Cannot load changes for project " + p + ", assuming no changes are visible", e);
      return Collections.emptyMap();
    }
    return s.map(r -> toNotes(p, r))
        .filter(Objects::nonNull)
        .collect(toMap(n -> n.getChangeId(), n -> n.getChange().getDest()));
  }

  @Nullable
  private ChangeNotes toNotes(Project.NameKey p, ChangeNotesResult r) {
    if (r.error().isPresent()) {
      log.warn("Failed to load change " + r.id() + " in " + p, r.error().get());
      return null;
    }
    try {
      if (perm.change(r.notes()).test(ChangePermission.READ)) {
        return r.notes();
      }
    } catch (PermissionBackendException e) {
      log.warn("Failed to check permission for " + r.id() + " in " + p, e);
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

  private boolean canReadRef(String ref) {
    try {
      perm.ref(ref).check(RefPermission.READ);
      return true;
    } catch (AuthException e) {
      return false;
    } catch (PermissionBackendException e) {
      log.error("unable to check permissions", e);
      return false;
    }
  }

  private boolean checkProjectPermission(
      PermissionBackend.ForProject forProject, ProjectPermission perm) {
    try {
      forProject.check(perm);
    } catch (AuthException e) {
      return false;
    } catch (PermissionBackendException e) {
      log.error(
          String.format(
              "Can't check permission for user %s on project %s",
              user.get(), projectState.getName()),
          e);
      return false;
    }
    return true;
  }

  private boolean isGroupOwner(
      InternalGroup group, @Nullable IdentifiedUser user, boolean isAdmin) {
    checkNotNull(group);

    // Keep this logic in sync with GroupControl#isOwner().
    return isAdmin
        || (user != null && user.getEffectiveGroups().contains(group.getOwnerGroupUUID()));
  }
}
