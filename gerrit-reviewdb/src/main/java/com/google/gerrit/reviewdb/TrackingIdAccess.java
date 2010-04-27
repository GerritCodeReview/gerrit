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

package com.google.gerrit.reviewdb;

import com.google.gwtorm.client.Access;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.PrimaryKey;
import com.google.gwtorm.client.Query;
import com.google.gwtorm.client.ResultSet;

public interface TrackingIdAccess extends Access<TrackingId, TrackingId.Key> {
  @PrimaryKey("key")
  TrackingId get(TrackingId.Key key) throws OrmException;

  @Query("WHERE key.changeId = ?")
  ResultSet<TrackingId> getTrackingId(Change.Id change) throws OrmException;

  @Query("WHERE key.trackingId = ?")
  ResultSet<TrackingId> getTrackingId(TrackingId.Id trackingId) throws OrmException;
}
