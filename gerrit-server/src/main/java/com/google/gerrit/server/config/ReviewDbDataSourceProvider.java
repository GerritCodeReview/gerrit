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

import com.google.gerrit.lifecycle.LifecycleListener;
import com.google.gwtorm.jdbc.SimpleDataSource;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import com.google.inject.Singleton;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.SQLException;
import java.util.Map;
import java.util.Properties;

import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

/** Provides access to the {@code ReviewDb} DataSource. */
@Singleton
final class ReviewDbDataSourceProvider implements Provider<DataSource>,
    LifecycleListener {
  private DataSource ds;

  @Override
  public synchronized DataSource get() {
    if (ds == null) {
      ds = open();
    }
    return ds;
  }

  @Override
  public void start() {
  }

  @Override
  public synchronized void stop() {
    if (ds != null) {
      closeDataSource(ds);
    }
  }

  private DataSource open() {
    final String dsName = "java:comp/env/jdbc/ReviewDb";
    try {
      return (DataSource) new InitialContext().lookup(dsName);
    } catch (NamingException namingErr) {
      final Properties p = readGerritDataSource();
      if (p == null) {
        throw new ProvisionException("Initialization error:\n"
            + "  * No DataSource " + dsName + "\n"
            + "  * No -DGerritServer=GerritServer.properties"
            + " on Java command line", namingErr);
      }

      try {
        return new SimpleDataSource(p);
      } catch (SQLException se) {
        throw new ProvisionException("Database unavailable", se);
      }
    }
  }

  private static Properties readGerritDataSource() throws ProvisionException {
    final Properties srvprop = new Properties();
    String name = System.getProperty("GerritServer");
    if (name == null) {
      name = "GerritServer.properties";
    }
    try {
      final InputStream in = new FileInputStream(name);
      try {
        srvprop.load(in);
      } finally {
        in.close();
      }
    } catch (IOException e) {
      throw new ProvisionException("Cannot read " + name, e);
    }

    final Properties dbprop = new Properties();
    for (final Map.Entry<Object, Object> e : srvprop.entrySet()) {
      final String key = (String) e.getKey();
      if (key.startsWith("database.")) {
        dbprop.put(key.substring("database.".length()), e.getValue());
      }
    }
    return dbprop;
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
