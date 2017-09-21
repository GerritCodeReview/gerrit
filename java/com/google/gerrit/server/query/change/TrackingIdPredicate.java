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

import com.google.gerrit.server.index.change.ChangeField;
import com.google.gwtorm.server.OrmException;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TrackingIdPredicate extends ChangeIndexPredicate {
  private static final Logger log = LoggerFactory.getLogger(TrackingIdPredicate.class);

  public TrackingIdPredicate(String trackingId) {
    super(ChangeField.TR, trackingId);
  }

  @Override
  public boolean match(ChangeData cd) throws OrmException {
    try {
      return cd.trackingFooters().containsValue(getValue());
    } catch (IOException e) {
      log.warn("Cannot extract footers from " + cd.getId(), e);
    }
    return false;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
