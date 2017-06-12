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

import static com.google.gerrit.reviewdb.client.RefNames.REFS_CACHE_AUTOMERGE;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CHANGES;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_CONFIG;
import static com.google.gerrit.reviewdb.client.RefNames.REFS_USERS_SELF;

import com.google.common.collect.ImmutableSet;
import com.google.gerrit.common.Nullable;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.RefNames;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.notedb.ChangeNotes;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

  private final TagCache tagCache;
  private final ChangeNotes.Factory changeNotesFactory;
  @Nullable private final SearchingChangeCacheImpl changeCache;
  private final Repository db;
  private final Project.NameKey projectName;
  private final ProjectControl projectCtl;
  private final ReviewDb reviewDb;
  private final boolean showMetadata;
  private String userEditPrefix;
  private Set<Change.Id> visibleChanges;

  public VisibleRefFilter(
      TagCache tagCache,
      ChangeNotes.Factory changeNotesFactory,
      @Nullable SearchingChangeCacheImpl changeCache,
      Repository db,
      ProjectControl projectControl,
      ReviewDb reviewDb,
      boolean showMetadata) {
    this.tagCache = tagCache;
    this.changeNotesFactory = changeNotesFactory;
    this.changeCache = changeCache;
    this.db = db;
    this.projectName = projectControl.getProject().getNameKey();
    this.projectCtl = projectControl;
    this.reviewDb = reviewDb;
    this.showMetadata = showMetadata;
  }

  public Map<String, Ref> filter(Map<String, Ref> refs, boolean filterTagsSeparately) {
    if (projectCtl.getProjectState().isAllUsers()) {
      refs = addUsersSelfSymref(refs);
    }

    if (projectCtl.allRefsAreVisible(ImmutableSet.of(REFS_CONFIG))) {
      return fastHideRefsMetaConfig(refs);
    }

    Account.Id userId;
    boolean viewMetadata;
    if (projectCtl.getUser().isIdentifiedUser()) {
      IdentifiedUser user = projectCtl.getUser().asIdentifiedUser();
      userId = user.getAccountId();
      viewMetadata = user.getCapabilities().canAccessDatabase();
      userEditPrefix = RefNames.refsEditPrefix(userId);
    } else {
      userId = null;
      viewMetadata = false;
    }

    Map<String, Ref> result = new HashMap<>();
    List<Ref> deferredTags = new ArrayList<>();

    for (Ref ref : refs.values()) {
      String name = ref.getName();
      Change.Id changeId;
      Account.Id accountId;
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
        // Account ref is visible only to corresponding account.
        if (viewMetadata
            || (accountId.equals(userId) && projectCtl.controlForRef(name).isVisible())) {
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
      } else if (projectCtl.controlForRef(ref.getLeaf().getName()).isVisible()) {
        // Use the leaf to lookup the control data. If the reference is
        // symbolic we want the control around the final target. If its
        // not symbolic then getLeaf() is a no-op returning ref itself.
        result.put(name, ref);
      }
    }

    // If we have tags that were deferred, we need to do a revision walk
    // to identify what tags we can actually reach, and what we cannot.
    //
    if (!deferredTags.isEmpty() && (!result.isEmpty() || filterTagsSeparately)) {
      TagMatcher tags =
          tagCache
              .get(projectName)
              .matcher(
                  tagCache,
                  db,
                  filterTagsSeparately ? filter(db.getAllRefs()).values() : result.values());
      for (Ref tag : deferredTags) {
        if (tags.isReachable(tag)) {
          result.put(tag.getName(), tag);
        }
      }
    }

    return result;
  }

  private Map<String, Ref> fastHideRefsMetaConfig(Map<String, Ref> refs) {
    if (refs.containsKey(REFS_CONFIG) && !projectCtl.controlForRef(REFS_CONFIG).isVisible()) {
      Map<String, Ref> r = new HashMap<>(refs);
      r.remove(REFS_CONFIG);
      return r;
    }
    return refs;
  }

  private Map<String, Ref> addUsersSelfSymref(Map<String, Ref> refs) {
    if (projectCtl.getUser().isIdentifiedUser()) {
      Ref r = refs.get(RefNames.refsUsers(projectCtl.getUser().getAccountId()));
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
    return visibleChanges.contains(changeId);
  }

  private boolean visibleEdit(String name) {
    if (userEditPrefix != null && name.startsWith(userEditPrefix)) {
      Change.Id id = Change.Id.fromEditRefPart(name);
      if (id != null) {
        return visible(id);
      }
    }
    return false;
  }

  private Set<Change.Id> visibleChangesBySearch() {
    Project project = projectCtl.getProject();
    try {
      Set<Change.Id> visibleChanges = new HashSet<>();
      for (ChangeData cd : changeCache.getChangeData(reviewDb, project.getNameKey())) {
        if (projectCtl.controlForIndexedChange(cd.change()).isVisible(reviewDb, cd)) {
          visibleChanges.add(cd.getId());
        }
      }
      return visibleChanges;
    } catch (OrmException e) {
      log.error(
          "Cannot load changes for project "
              + project.getName()
              + ", assuming no changes are visible",
          e);
      return Collections.emptySet();
    }
  }

  private Set<Change.Id> visibleChangesByScan() {
    Project.NameKey project = projectCtl.getProject().getNameKey();
    try {
      Set<Change.Id> visibleChanges = new HashSet<>();
      for (ChangeNotes cn : changeNotesFactory.scan(db, reviewDb, project)) {
        if (projectCtl.controlFor(cn).isVisible(reviewDb)) {
          visibleChanges.add(cn.getChangeId());
        }
      }
      return visibleChanges;
    } catch (IOException | OrmException e) {
      log.error(
          "Cannot load changes for project " + project + ", assuming no changes are visible", e);
      return Collections.emptySet();
    }
  }

  private static boolean isMetadata(String name) {
    return name.startsWith(REFS_CHANGES) || RefNames.isRefsEdit(name);
  }

  private static boolean isTag(Ref ref) {
    return ref.getLeaf().getName().startsWith(Constants.R_TAGS);
  }
}
