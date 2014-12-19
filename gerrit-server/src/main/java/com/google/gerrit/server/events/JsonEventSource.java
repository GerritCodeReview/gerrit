// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.events;

import com.google.gerrit.common.EventSourceImpl;
import com.google.gerrit.common.ChangeListener;
import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.events.Event;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

/** An EventSource which can fire json events */
@Singleton
public class JsonEventSource extends EventSourceImpl {
  protected Gson gson =
      new GsonBuilder().registerTypeAdapter(Event.class,
      new EventDeserializer()).create();

  protected Provider<ReviewDb> db;

  @Inject
  public JsonEventSource(Provider<ReviewDb> db, ProjectCache projectCache,
      DynamicSet<ChangeListener> unrestrictedListeners) {
    super(projectCache, unrestrictedListeners);
    this.db = db;
  }

  public void fireEvent(String jsonEvent) throws OrmException {
    super.fireEvent(gson.fromJson(jsonEvent, Event.class), db.get());
  }
}
