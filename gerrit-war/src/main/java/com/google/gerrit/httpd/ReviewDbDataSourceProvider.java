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

package com.google.gerrit.httpd;

import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.jdbc.Database;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Provides access to the {@code ReviewDb} DataSource. */
@Singleton
final class ReviewDbDataSourceProvider implements
    Provider<SchemaFactory<ReviewDb>>, LifecycleListener {
  private SchemaFactory<ReviewDb> ds;
  private DataSource dataSource;

  @Override
  public synchronized SchemaFactory<ReviewDb> get() {
    if (dataSource == null) {
      dataSource = open();
    }
    if (ds == null) {
      try {
        ds = new Database<ReviewDb>(dataSource, ReviewDb.class);
      } catch (OrmException err) {
        throw new ProvisionException("Cannot initialize database", err);
      }
    }
    return ds;
  }

  @Override
  public void start() {
  }

  @Override
  public synchronized void stop() {
    if (dataSource != null) {
      closeDataSource(dataSource);
    }
  }

  private DataSource open() {
    final String dsName = "java:comp/env/jdbc/ReviewDb";
    try {
      return (DataSource) new InitialContext().lookup(dsName);
    } catch (NamingException namingErr) {
      throw new ProvisionException("No DataSource " + dsName, namingErr);
    }
  }

  private void closeDataSource(final DataSource ds) {
    try {
      Class<?> type = Class.forName("org.apache.commons.dbcp.BasicDataSource");
      if (type.isInstance(ds)) {
        type.getMethod("close").invoke(ds);
        return;
      }
    } catch (Throwable bad) {
      // Oh well, its not a Commons DBCP pooled connection.
    }

    try {
      Class<?> type = Class.forName("com.mchange.v2.c3p0.DataSources");
      if (type.isInstance(ds)) {
        type.getMethod("destroy", DataSource.class).invoke(null, ds);
        return;
      }
    } catch (Throwable bad) {
      // Oh well, its not a c3p0 pooled connection.
    }
  }
}
