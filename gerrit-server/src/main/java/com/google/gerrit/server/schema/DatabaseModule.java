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

import com.google.gerrit.lifecycle.LifecycleModule;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.config.FactoryModule;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.LocalDiskRepositoryManager;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.jdbc.Database;
import com.google.inject.TypeLiteral;

import org.eclipse.jgit.lib.PersonIdent;

/** Loads the database with standard dependencies. */
public class DatabaseModule extends FactoryModule {
  @Override
  protected void configure() {
    install(new SchemaVersion.Module());

    bind(new TypeLiteral<SchemaFactory<ReviewDb>>() {}).to(
        new TypeLiteral<Database<ReviewDb>>() {}).in(SINGLETON);
    bind(new TypeLiteral<Database<ReviewDb>>() {}).toProvider(
        ReviewDbDatabaseProvider.class).in(SINGLETON);

    bind(PersonIdent.class).annotatedWith(GerritPersonIdent.class).toProvider(
        GerritPersonIdentProvider.class);

    bind(GitRepositoryManager.class).to(LocalDiskRepositoryManager.class);
    install(new LifecycleModule() {
      @Override
      protected void configure() {
        listener().to(LocalDiskRepositoryManager.Lifecycle.class);
      }
    });
  }
}
