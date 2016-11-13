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

package com.google.gerrit.pgm.util;

import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Module to bind a single {@link ReviewDb} instance per thread.
 *
 * <p>New instances are opened on demand, but are closed only at shutdown.
 */
class PerThreadReviewDbModule extends LifecycleModule {
  private final SchemaFactory<ReviewDb> schema;

  @Inject
  PerThreadReviewDbModule(SchemaFactory<ReviewDb> schema) {
    this.schema = schema;
  }

  @Override
  protected void configure() {
    final List<ReviewDb> dbs = Collections.synchronizedList(new ArrayList<ReviewDb>());
    final ThreadLocal<ReviewDb> localDb = new ThreadLocal<>();

    bind(ReviewDb.class)
        .toProvider(
            new Provider<ReviewDb>() {
              @Override
              public ReviewDb get() {
                ReviewDb db = localDb.get();
                if (db == null) {
                  try {
                    db = schema.open();
                    dbs.add(db);
                    localDb.set(db);
                  } catch (OrmException e) {
                    throw new ProvisionException("unable to open ReviewDb", e);
                  }
                }
                return db;
              }
            });
    listener()
        .toInstance(
            new LifecycleListener() {
              @Override
              public void start() {
                // Do nothing.
              }

              @Override
              public void stop() {
                for (ReviewDb db : dbs) {
                  db.close();
                }
              }
            });
  }
}
