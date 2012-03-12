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

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.TrackingId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gwtorm.server.ListResultSet;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.HashSet;

class TrackingIdPredicate extends OperatorPredicate<ChangeData, PatchSet> implements
    ChangeDataSource {
  private final Provider<ReviewDb> db;

  TrackingIdPredicate(Provider<ReviewDb> db, String trackingId) {
    super(ChangeQueryBuilder.FIELD_TR, trackingId);
    this.db = db;
  }

  @Override
  public boolean match(final ChangeData object, final PatchSet ps)
      throws OrmException {
    for (TrackingId c : object.trackingIds(db)) {
      if (getValue().equals(c.getTrackingId())) {
        return true;
      }
    }
    return false;
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    HashSet<Change.Id> ids = new HashSet<Change.Id>();
    for (TrackingId sc : db.get().trackingIds() //
        .byTrackingId(new TrackingId.Id(getValue()))) {
      ids.add(sc.getChangeId());
    }

    ArrayList<ChangeData> r = new ArrayList<ChangeData>(ids.size());
    for (Change.Id id : ids) {
      r.add(new ChangeData(id));
    }
    return new ListResultSet<ChangeData>(r);
  }

  @Override
  public boolean hasChange() {
    return false;
  }

  @Override
  public int getCardinality() {
    return ChangeCosts.CARD_TRACKING_IDS;
  }

  @Override
  public int getCost() {
    return ChangeCosts.cost(ChangeCosts.TR_SCAN, getCardinality());
  }
}
