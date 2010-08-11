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

import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.TrackingId;
import com.google.gerrit.server.query.OperatorPredicate;
import com.google.gerrit.server.query.change.ChangeData.NeededData;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.ResultSet;
import com.google.gwtorm.client.impl.ListResultSet;
import com.google.inject.Provider;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashSet;

class TrackingIdPredicate extends OperatorPredicate<ChangeData> implements
    ChangeDataSource, Prefetchable {
  private final Provider<ReviewDb> db;

  TrackingIdPredicate(Provider<ReviewDb> db, String trackingId) {
    super(ChangeQueryBuilder.FIELD_TR, trackingId);
    this.db = db;
  }

  @Override
  public boolean match(final ChangeData object) throws OrmException {
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

  @Override
  public EnumSet<NeededData> getNeededData() {
    return EnumSet.of(NeededData.TRACKING_IDS);
  }
}
