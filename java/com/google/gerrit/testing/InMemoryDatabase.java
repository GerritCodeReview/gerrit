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
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.index.IndexModule;
import com.google.gerrit.server.schema.SchemaCreator;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import java.io.IOException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;

/** Husk of an in-memory ReviewDb implementation. */
// TODO(dborowitz): Inline callers to get their own darn schemaCreator.
public class InMemoryDatabase implements SchemaFactory<ReviewDb> {
  private final GitRepositoryManager repoManager;
  private final AllProjectsName allProjectsName;
  private final SchemaCreator schemaCreator;
  private final SchemaFactory<ReviewDb> schemaFactory;

  @Inject
  InMemoryDatabase(Injector injector) {
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
    this.repoManager = childInjector.getInstance(GitRepositoryManager.class);
    this.allProjectsName = childInjector.getInstance(AllProjectsName.class);
    this.schemaCreator = childInjector.getInstance(SchemaCreator.class);
    this.schemaFactory =
        childInjector.getInstance(Key.get(new TypeLiteral<SchemaFactory<ReviewDb>>() {}));
  }

  InMemoryDatabase(
      GitRepositoryManager repoManager,
      AllProjectsName allProjectsName,
      SchemaCreator schemaCreator,
      SchemaFactory<ReviewDb> schemaFactory) {
    this.repoManager = repoManager;
    this.allProjectsName = allProjectsName;
    this.schemaCreator = schemaCreator;
    this.schemaFactory = schemaFactory;
  }

  @Override
  public ReviewDb open() throws OrmException {
    return schemaFactory.open();
  }

  /** Ensure the database schema has been created and initialized. */
  public InMemoryDatabase create() throws OrmException {
    try {
      try {
        repoManager.openRepository(allProjectsName).close();
      } catch (RepositoryNotFoundException e) {
        schemaCreator.create();
      }
    } catch (IOException | ConfigInvalidException e) {
      throw new OrmException("Cannot create in-memory database", e);
    }
    return this;
  }
}
