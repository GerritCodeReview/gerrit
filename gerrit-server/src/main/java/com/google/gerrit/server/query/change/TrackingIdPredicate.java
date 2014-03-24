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
import com.google.gerrit.server.config.TrackingFooters;
import com.google.gerrit.server.index.IndexPredicate;
import com.google.gerrit.server.index.change.ChangeField;
import com.google.gerrit.server.project.NoSuchChangeException;
import com.google.gwtorm.server.OrmException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

class TrackingIdPredicate extends IndexPredicate<ChangeData> {
  private static final Logger log = LoggerFactory.getLogger(TrackingIdPredicate.class);

  private final TrackingFooters trackingFooters;

  TrackingIdPredicate(TrackingFooters trackingFooters, String trackingId) {
    super(ChangeField.TR, trackingId);
    this.trackingFooters = trackingFooters;
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    Change c = object.change();
    if (c != null) {
      try {
        return trackingFooters.extract(object.commitFooters())
            .values().contains(getValue());
      } catch (NoSuchChangeException | IOException e) {
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
