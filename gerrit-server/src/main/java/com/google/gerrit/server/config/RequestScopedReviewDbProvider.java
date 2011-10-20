// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.server.config;

import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.RequestCleanup;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;

/** Provides {@link ReviewDb} database handle live only for this request. */
@Singleton
final class RequestScopedReviewDbProvider implements Provider<ReviewDb> {
  private final SchemaFactory<ReviewDb> schema;
  private final Provider<RequestCleanup> cleanup;

  @Inject
  RequestScopedReviewDbProvider(final SchemaFactory<ReviewDb> schema,
      final Provider<RequestCleanup> cleanup) {
    this.schema = schema;
    this.cleanup = cleanup;
  }

  @Override
  public ReviewDb get() {
    final ReviewDb c;
    try {
      c = schema.open();
    } catch (OrmException e) {
      throw new ProvisionException("Cannot open ReviewDb", e);
    }
    try {
      cleanup.get().add(new Runnable() {
        @Override
        public void run() {
          c.close();
        }
      });
      return c;
    } catch (Error e) {
      c.close();
      throw e;
    } catch (RuntimeException e) {
      c.close();
      throw e;
    }
  }
}
