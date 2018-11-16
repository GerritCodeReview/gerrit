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

package com.google.gerrit.testing;

import com.google.gerrit.pgm.init.index.elasticsearch.ElasticIndexModuleOnInit;
import com.google.gerrit.pgm.init.index.lucene.LuceneIndexModuleOnInit;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.schema.ReviewDbSchemaCreator;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;

/** Husk of an in-memory ReviewDb implementation. */
// TODO(dborowitz): Inline callers to get their own darn schemaCreator.
public class InMemoryDatabase implements SchemaFactory<ReviewDb> {
  /** Drop the database from memory; does nothing if the instance was null. */
  public static void drop(InMemoryDatabase db) {}

  private final ReviewDbSchemaCreator schemaCreator;
  private final SchemaFactory<ReviewDb> schemaFactory;

  private boolean created;

  @Inject
  InMemoryDatabase(Injector injector) throws OrmException {
    Injector childInjector =
        injector.createChildInjector(
            new AbstractModule() {
              @Override
              protected void configure() {
                switch (IndexModule.getIndexType(injector)) {
                  case LUCENE:
                    install(new LuceneIndexModuleOnInit());
                    break;
                  case ELASTICSEARCH:
                    install(new ElasticIndexModuleOnInit());
                    break;
                  default:
                    throw new IllegalStateException("unsupported index.type");
                }
              }
            });
    this.schemaCreator = childInjector.getInstance(ReviewDbSchemaCreator.class);
    this.schemaFactory =
        childInjector.getInstance(Key.get(new TypeLiteral<SchemaFactory<ReviewDb>>() {}));
  }

  InMemoryDatabase(ReviewDbSchemaCreator schemaCreator, SchemaFactory<ReviewDb> schemaFactory) {
    this.schemaCreator = schemaCreator;
    this.schemaFactory = schemaFactory;
  }

  @Override
  public ReviewDb open() throws OrmException {
    return schemaFactory.open();
  }

  /** Ensure the database schema has been created and initialized. */
  public InMemoryDatabase create() throws OrmException {
    if (!created) {
      created = true;
      try {
        schemaCreator.create();
      } catch (IOException | ConfigInvalidException e) {
        throw new OrmException("Cannot create in-memory database", e);
      }
    }
    return this;
  }
}
