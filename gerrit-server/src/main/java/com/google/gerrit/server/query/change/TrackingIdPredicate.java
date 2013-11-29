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
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.ChangeField;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Provider;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

class TrackingIdPredicate extends IndexPredicate<ChangeData> {
  private static final Logger log = LoggerFactory.getLogger(TrackingIdPredicate.class);

  private final Provider<ReviewDb> db;
  private final TrackingFooters trackingFooters;
  private final GitRepositoryManager repositoryManager;

  TrackingIdPredicate(Provider<ReviewDb> db,
      TrackingFooters trackingFooters,
      GitRepositoryManager repositoryManager,
      String trackingId) {
    super(ChangeField.TR, trackingId);
    this.db = db;
    this.trackingFooters = trackingFooters;
    this.repositoryManager = repositoryManager;
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    Change c = object.change(db);
    if (c != null) {
      try {
        return trackingFooters.extract(object.commitFooters(repositoryManager, db))
            .values().contains(getValue());
      } catch (IOException e) {
        log.warn("Cannot extract footers from " + c.getChangeId(), e);
      }
    }
    return false;
  }

  @Override
  public int getCost() {
    return ChangeCosts.cost(
        ChangeCosts.TR_SCAN,
        ChangeCosts.CARD_TRACKING_IDS);
  }
}
