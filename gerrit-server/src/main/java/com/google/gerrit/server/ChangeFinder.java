// Copyright (C) 2016 The Android Open Source Project
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

package com.google.gerrit.server;

import com.google.common.base.Throwables;
import com.google.common.cache.Cache;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Sets;
import com.google.common.primitives.Ints;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.change.ChangeTriplet;
import com.google.gerrit.server.project.ChangeControl;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gerrit.server.query.change.ChangeData;
import com.google.gerrit.server.query.change.InternalChangeQuery;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Module;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

@Singleton
public class ChangeFinder {
  private static final String CACHE_NAME = "changeid_project";

  public static Module module() {
    return new CacheModule() {
      @Override
      protected void configure() {
        cache(CACHE_NAME, Change.Id.class, String.class).maximumWeight(1024);
      }
    };
  }

  private final IndexConfig indexConfig;
  private final Cache<Change.Id, String> changeIdProjectCache;
  private final Provider<InternalChangeQuery> queryProvider;
  private final Provider<ReviewDb> reviewDb;
  private final ChangeControl.GenericFactory changeControlFactory;

  @Inject
  ChangeFinder(
      IndexConfig indexConfig,
      @Named(CACHE_NAME) Cache<Change.Id, String> changeIdProjectCache,
      Provider<InternalChangeQuery> queryProvider,
      Provider<ReviewDb> reviewDb,
      ChangeControl.GenericFactory changeControlFactory) {
    this.indexConfig = indexConfig;
    this.changeIdProjectCache = changeIdProjectCache;
    this.queryProvider = queryProvider;
    this.reviewDb = reviewDb;
    this.changeControlFactory = changeControlFactory;
  }

  /**
   * Find changes matching the given identifier.
   *
   * @param id change identifier, either a numeric ID, a Change-Id, or project~branch~id triplet.
   * @param user user to wrap in controls.
   * @return possibly-empty list of controls for all matching changes, corresponding to the given
   *     user; may or may not be visible.
   * @throws OrmException if an error occurred querying the database.
   */
  public List<ChangeControl> find(String id, CurrentUser user) throws OrmException {
    if (id.isEmpty()) {
      return Collections.emptyList();
    }

    int z = id.lastIndexOf('~');
    int y = id.lastIndexOf('~', z - 1);
    if (y < 0 && z > 0) {
      // Try project~numericChangeId
      Integer n = Ints.tryParse(id.substring(z + 1));
      if (n != null) {
        return fromProjectNumber(user, id.substring(0, z), n.intValue());
      }
    }

    if (y < 0 && z < 0) {
      // Try numeric changeId
      Integer n = Ints.tryParse(id);
      if (n != null) {
        return find(new Change.Id(n), user);
      }
    }

    // Use the index to search for changes, but don't return any stored fields,
    // to force rereading in case the index is stale.
    InternalChangeQuery query = queryProvider.get().noFields();
    if (y > 0 && z > 0) {
      // Try change triplet (project~branch~Ihash...)
      Optional<ChangeTriplet> triplet = ChangeTriplet.parse(id, y, z);
      if (triplet.isPresent()) {
        ChangeTriplet t = triplet.get();
        return asChangeControls(query.byBranchKey(t.branch(), t.id()), user);
      }
    }

    // Try isolated Ihash... format ("Change-Id: Ihash").
    return asChangeControls(query.byKeyPrefix(id), user);
  }

  private List<ChangeControl> fromProjectNumber(CurrentUser user, String project, int changeNumber)
      throws OrmException {
    Change.Id cId = new Change.Id(changeNumber);
    try {
      return ImmutableList.of(
          changeControlFactory.controlFor(
              reviewDb.get(), Project.NameKey.parse(project), cId, user));
    } catch (NoSuchChangeException e) {
      return Collections.emptyList();
    } catch (IllegalArgumentException e) {
      String changeNotFound = String.format("change %s not found in ReviewDb", cId);
      String projectNotFound =
          String.format(
              "passed project %s when creating ChangeNotes for %s, but actual project is",
              project, cId);
      if (e.getMessage().equals(changeNotFound) || e.getMessage().startsWith(projectNotFound)) {
        return Collections.emptyList();
      }
      throw e;
    } catch (OrmException e) {
      // Distinguish between a RepositoryNotFoundException (project argument invalid) and
      // other OrmExceptions (failure in the persistence layer).
      if (Throwables.getRootCause(e) instanceof RepositoryNotFoundException) {
        return Collections.emptyList();
      }
      throw e;
    }
  }

  public ChangeControl findOne(Change.Id id, CurrentUser user) throws OrmException {
    List<ChangeControl> ctls = find(id, user);
    if (ctls.size() != 1) {
      throw new NoSuchChangeException(id);
    }
    return ctls.get(0);
  }

  public List<ChangeControl> find(Change.Id id, CurrentUser user) throws OrmException {
    String project = changeIdProjectCache.getIfPresent(id);
    if (project != null) {
      return fromProjectNumber(user, project, id.get());
    }

    // Use the index to search for changes, but don't return any stored fields,
    // to force rereading in case the index is stale.
    InternalChangeQuery query = queryProvider.get().noFields();
    List<ChangeData> r = query.byLegacyChangeId(id);
    if (r.size() == 1) {
      changeIdProjectCache.put(id, r.get(0).project().get());
    }
    return asChangeControls(r, user);
  }

  private List<ChangeControl> asChangeControls(List<ChangeData> cds, CurrentUser user)
      throws OrmException {
    List<ChangeControl> ctls = new ArrayList<>(cds.size());
    if (!indexConfig.separateChangeSubIndexes()) {
      for (ChangeData cd : cds) {
        ctls.add(cd.changeControl(user));
      }
      return ctls;
    }

    // If an index implementation uses separate non-atomic subindexes, it's possible to temporarily
    // observe a change as present in both subindexes, if this search is concurrent with a write.
    // Dedup to avoid confusing the caller. We can choose an arbitrary ChangeData instance because
    // the index results have no stored fields, so the data is already reloaded. (It's also possible
    // that a change might appear in zero subindexes, but there's nothing we can do here to help
    // this case.)
    Set<Change.Id> seen = Sets.newHashSetWithExpectedSize(cds.size());
    for (ChangeData cd : cds) {
      if (seen.add(cd.getId())) {
        ctls.add(cd.changeControl(user));
      }
    }
    return ctls;
  }
}
