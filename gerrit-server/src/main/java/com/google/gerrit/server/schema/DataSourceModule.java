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

package com.google.gerrit.server.schema;

import com.google.inject.AbstractModule;
import com.google.inject.name.Names;

public class DataSourceModule extends AbstractModule {

  @Override
  protected void configure() {
    bind(DataSourceType.class).annotatedWith(Names.named("h2")).to(H2.class);
    bind(DataSourceType.class).annotatedWith(Names.named("jdbc")).to(JDBC.class);
    bind(DataSourceType.class).annotatedWith(Names.named("mysql")).to(MySql.class);
    bind(DataSourceType.class).annotatedWith(Names.named("oracle")).to(Oracle.class);
    bind(DataSourceType.class).annotatedWith(Names.named("postgresql")).to(PostgreSQL.class);
  }
}
