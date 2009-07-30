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

import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.jdbc.Database;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import com.google.inject.name.Names;

import javax.sql.DataSource;

/** Loads the database with standard dependencies. */
public class DatabaseModule extends FactoryModule {
  public static final Key<DataSource> DS =
      Key.get(DataSource.class, Names.named("ReviewDb"));

  @Override
  protected void configure() {
    bind(DS).toProvider(ReviewDbDataSourceProvider.class).in(SINGLETON);
    bind(new TypeLiteral<SchemaFactory<ReviewDb>>() {}).to(
        new TypeLiteral<Database<ReviewDb>>() {});
    bind(new TypeLiteral<Database<ReviewDb>>() {}).toProvider(
        ReviewDbProvider.class).in(SINGLETON);

    bind(SystemConfig.class).toProvider(SystemConfigProvider.class).in(
        SINGLETON);
  }
}
