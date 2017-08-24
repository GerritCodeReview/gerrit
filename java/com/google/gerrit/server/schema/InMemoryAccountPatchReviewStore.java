// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.schema;

import com.google.common.annotations.VisibleForTesting;
import com.google.gerrit.extensions.registration.DynamicItem;
import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.server.change.AccountPatchReviewStore;
import com.google.gwtorm.jdbc.SimpleDataSource;
import java.sql.SQLException;
import java.util.Properties;
import javax.sql.DataSource;

public class InMemoryAccountPatchReviewStore extends JdbcAccountPatchReviewStore {
  @VisibleForTesting
  public static class Module extends LifecycleModule {
    @Override
    protected void configure() {
      InMemoryAccountPatchReviewStore inMemoryStore = new InMemoryAccountPatchReviewStore();
      DynamicItem.bind(binder(), AccountPatchReviewStore.class).toInstance(inMemoryStore);
      listener().toInstance(inMemoryStore);
    }
  }

  /**
   * Creates an in-memory H2 database to store the reviewed flags. This should be used for tests
   * only.
   */
  @VisibleForTesting
  private InMemoryAccountPatchReviewStore() {
    super(newDataSource());
  }

  private static synchronized DataSource newDataSource() {
    final Properties p = new Properties();
    p.setProperty("driver", "org.h2.Driver");
    // DB_CLOSE_DELAY=-1: By default the content of an in-memory H2 database is lost at the moment
    // the last connection is closed. This option keeps the content as long as the vm lives.
    p.setProperty("url", "jdbc:h2:mem:account_patch_reviews;DB_CLOSE_DELAY=-1");
    try {
      return new SimpleDataSource(p);
    } catch (SQLException e) {
      throw new RuntimeException("Unable to create test datasource", e);
    }
  }
}
