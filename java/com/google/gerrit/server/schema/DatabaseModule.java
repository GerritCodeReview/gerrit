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

package com.google.gerrit.server.schema;

import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.notedb.ChangeBundleReader;
import com.google.gerrit.server.notedb.GwtormChangeBundleReader;
import com.google.gwtorm.jdbc.Database;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;

/** Loads the database with standard dependencies. */
public class DatabaseModule extends FactoryModule {
  @Override
  protected void configure() {
    TypeLiteral<SchemaFactory<ReviewDb>> schemaFactory =
        new TypeLiteral<SchemaFactory<ReviewDb>>() {};
    TypeLiteral<Database<ReviewDb>> database = new TypeLiteral<Database<ReviewDb>>() {};

    bind(schemaFactory).to(NotesMigrationSchemaFactory.class);
    bind(Key.get(schemaFactory, ReviewDbFactory.class)).to(database).in(SINGLETON);
    bind(database).toProvider(ReviewDbDatabaseProvider.class);
    bind(ChangeBundleReader.class).to(GwtormChangeBundleReader.class);
  }
}
