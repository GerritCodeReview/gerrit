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

package com.google.gerrit.server.query.change;

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.change.ChangeData.NeededData;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.impl.ListResultSet;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.assistedinject.Assisted;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;

class AndSource extends PrefetchableAndPredicate implements ChangeDataSource {
  interface Factory {
    AndSource create(Collection<? extends Predicate<ChangeData>> that);
  }

  private static final Comparator<Predicate<ChangeData>> CMP =
      new Comparator<Predicate<ChangeData>>() {
        @Override
        public int compare(Predicate<ChangeData> a, Predicate<ChangeData> b) {
          int ai = a instanceof ChangeDataSource ? 0 : 1;
          int bi = b instanceof ChangeDataSource ? 0 : 1;
          int cmp = ai - bi;

          if (cmp == 0 //
              && a instanceof ChangeDataSource //
              && b instanceof ChangeDataSource) {
            ai = ((ChangeDataSource) a).hasChange() ? 0 : 1;
            bi = ((ChangeDataSource) b).hasChange() ? 0 : 1;
            cmp = ai - bi;
          }

          if (cmp == 0) {
            cmp = a.getCost() - b.getCost();
          }

          if (cmp == 0 //
              && a instanceof ChangeDataSource //
              && b instanceof ChangeDataSource) {
            ChangeDataSource as = (ChangeDataSource) a;
            ChangeDataSource bs = (ChangeDataSource) b;
            cmp = as.getCardinality() - bs.getCardinality();
          }

          return cmp;
        }
      };

  private static List<Predicate<ChangeData>> sort(
      Collection<? extends Predicate<ChangeData>> that) {
    ArrayList<Predicate<ChangeData>> r =
        new ArrayList<Predicate<ChangeData>>(that);
    Collections.sort(r, CMP);
    return r;
  }

  private int cardinality = -1;
  private final Provider<ReviewDb> dbProvider;
  private final ProjectCache projectCache;

  @Inject
  AndSource(Provider<ReviewDb> dbProvider, ProjectCache projectCache,
      @Assisted final Collection<? extends Predicate<ChangeData>> that) {
    super(sort(that));
    this.dbProvider = dbProvider;
    this.projectCache = projectCache;
  }

  @Override
  public boolean hasChange() {
    ChangeDataSource source = source();
    return source != null && source.hasChange();
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    ChangeDataSource source = source();
    if (source == null) {
      throw new OrmException("No ChangeDataSource: " + this);
    }

    // TODO(spearce) This probably should be more lazy.
    //
    ArrayList<ChangeData> r = new ArrayList<ChangeData>();
    ChangeData last = null;
    boolean skipped = false;
    for (ChangeData cd : prefetchData(source)) {
      if (match(cd)) {
        r.add(cd);
      } else {
        skipped = true;
      }
      last = cd;
    }

    if (skipped && last != null && source instanceof Paginated) {
      // If our source is a paginated source and we skipped at
      // least one of its results, we may not have filled the full
      // limit the caller wants.  Restart the source and continue.
      //
      Paginated p = (Paginated) source;
      while (skipped && r.size() < p.limit()) {
        ChangeData lastBeforeRestart = last;
        skipped = false;
        last = null;
        for (ChangeData cd : p.restart(lastBeforeRestart)) {
          if (match(cd)) {
            r.add(cd);
          } else {
            skipped = true;
          }
          last = cd;
        }
      }
    }

    return new ListResultSet<ChangeData>(r);
  }

  private ChangeDataSource source() {
    for (Predicate<ChangeData> p : getChildren()) {
      if (p instanceof ChangeDataSource) {
        return (ChangeDataSource) p;
      }
    }
    return null;
  }

  private Collection<ChangeData> prefetchData(ChangeDataSource source) throws OrmException {
    final ReviewDb db = dbProvider.get();
    final ArrayList<ChangeData> data = new ArrayList<ChangeData>();
    final EnumSet<NeededData> needed = getNeededData();

    for (ChangeData cd : source.read()) {
      data.add(cd);
    }

    for (ChangeData cd : data) {
      if (needed.contains(NeededData.APPROVALS)) {
        cd.setApprovals(db.patchSetApprovals().byChange(cd.getId()).toList());
      }
      if (needed.contains(NeededData.CHANGE)) {
        cd.setChange(db.changes().get(cd.getId()));
      }
      if (needed.contains(NeededData.PATCHES)) {
        cd.setPatches(db.patchSets().byChange(cd.getId()).toList());
      }
      if (needed.contains(NeededData.COMMENTS)) {
        cd.setComments(db.patchComments().byChange(cd.getId()).toList());
      }
      if (needed.contains(NeededData.TRACKING_IDS)) {
        cd.setTrackingIds(db.trackingIds().byChange(cd.getId()).toList());
      }
    }

    if (needed.contains(NeededData.PROJECT_STATE)) {
      ArrayList<Project.NameKey> projectNames = new ArrayList<Project.NameKey>();
      for (ChangeData cd : data) {
        projectNames.add(cd.getChange().getProject());
      }

      Map<Project.NameKey, ProjectState> projectMap =
          projectCache.getAll(projectNames);

      for (ChangeData cd : data) {
        cd.setProjectState(projectMap.get(cd.getChange().getProject()));
      }
    }

    db.close();
    return data;
  }

  @Override
  public int getCardinality() {
    if (cardinality < 0) {
      cardinality = Integer.MAX_VALUE;
      for (Predicate<ChangeData> p : getChildren()) {
        if (p instanceof ChangeDataSource) {
          int c = ((ChangeDataSource) p).getCardinality();
          cardinality = Math.min(cardinality, c);
        }
      }
    }
    return cardinality;
  }


}
