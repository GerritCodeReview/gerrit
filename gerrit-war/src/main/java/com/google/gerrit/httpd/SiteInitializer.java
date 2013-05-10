// Copyright (C) 2013 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.gerrit.pgm.Init;
import com.google.gerrit.server.config.GerritServerConfigModule;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.schema.DataSourceModule;
import com.google.gerrit.server.schema.DataSourceType;
import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Module;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Named;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.annotation.Annotation;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

final class SiteInitializer {
  private static final Logger log = LoggerFactory
      .getLogger(SiteInitializer.class);

  final String sitePath;
  final String initPath;

  SiteInitializer(String sitePath, String initPath) {
    this.sitePath = sitePath;
    this.initPath = initPath;
  }

  public void init() {
    try {

      if (sitePath != null) {
        File site = new File(sitePath);
        log.info(String.format("Initializing site at %s",
            site.getAbsolutePath()));
        new Init(site).run();
        return;
      }

      Connection conn = connectToDb();
      try {
        File site = getSiteFromReviewDb(conn);
        if (site == null && initPath != null) {
          site = new File(initPath);
        }

        if (site != null) {
          log.info(String.format("Initializing site at %s",
              site.getAbsolutePath()));
          String dbType = getDbType(conn, site);
          new Init(site, ReviewDbDataSourceProvider.class, dbType).run();
        }
      } finally {
        conn.close();
      }
    } catch (Exception e) {
      log.error("Site init failed", e);
      throw new RuntimeException(e);
    }
  }

  private Connection connectToDb() throws SQLException {
    return new ReviewDbDataSourceProvider().get().getConnection();
  }

  private File getSiteFromReviewDb(Connection conn) {
    try {
      ResultSet rs = conn.createStatement().executeQuery(
          "select site_path from system_config");
      if (rs.next()) {
        return new File(rs.getString(1));
      }
      return null;
    } catch (SQLException e) {
      return null;
    }
  }

  private String getDbType(Connection conn, final File site) throws SQLException {
    String dbProductName =
        conn.getMetaData().getDatabaseProductName().toLowerCase();

    List<Module> modules = Lists.newArrayList();
    modules.add(new AbstractModule() {
      @Override
      protected void configure() {
        bind(File.class).annotatedWith(SitePath.class).toInstance(site);
      }
    });
    modules.add(new GerritServerConfigModule());
    modules.add(new DataSourceModule());
    Injector i = Guice.createInjector(modules);
    List<Binding<DataSourceType>> dsTypeBindings =
        i.findBindingsByType(new TypeLiteral<DataSourceType>() {});
    for (Binding<DataSourceType> binding : dsTypeBindings) {
      Annotation annotation = binding.getKey().getAnnotation();
      if (annotation instanceof Named) {
        if (((Named) annotation).value().toLowerCase().contains(dbProductName)) {
          return ((Named) annotation).value();
        }
      }
    }
    throw new IllegalStateException(String.format(
        "Cannot guess database type from the database product name '%s'",
        dbProductName));
  }
}
