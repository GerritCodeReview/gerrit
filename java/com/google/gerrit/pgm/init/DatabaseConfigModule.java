// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.pgm.init;

import com.google.gerrit.server.config.SitePaths;
import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class DatabaseConfigModule extends AbstractModule {

  private final SitePaths site;

  public DatabaseConfigModule(SitePaths site) {
    this.site = site;
  }

  @Override
  protected void configure() {
    bind(SitePaths.class).toInstance(site);
    bind(DatabaseConfigInitializer.class)
        .annotatedWith(Names.named("db2"))
        .to(DB2Initializer.class);
    bind(DatabaseConfigInitializer.class)
        .annotatedWith(Names.named("derby"))
        .to(DerbyInitializer.class);
    bind(DatabaseConfigInitializer.class).annotatedWith(Names.named("h2")).to(H2Initializer.class);
    bind(DatabaseConfigInitializer.class)
        .annotatedWith(Names.named("jdbc"))
        .to(JDBCInitializer.class);
    bind(DatabaseConfigInitializer.class)
        .annotatedWith(Names.named("mariadb"))
        .to(MariaDbInitializer.class);
    bind(DatabaseConfigInitializer.class)
        .annotatedWith(Names.named("mysql"))
        .to(MySqlInitializer.class);
    bind(DatabaseConfigInitializer.class)
        .annotatedWith(Names.named("oracle"))
        .to(OracleInitializer.class);
    bind(DatabaseConfigInitializer.class)
        .annotatedWith(Names.named("postgresql"))
        .to(PostgreSQLInitializer.class);
    bind(DatabaseConfigInitializer.class)
        .annotatedWith(Names.named("maxdb"))
        .to(MaxDbInitializer.class);
    bind(DatabaseConfigInitializer.class)
        .annotatedWith(Names.named("hana"))
        .to(HANAInitializer.class);
  }
}
