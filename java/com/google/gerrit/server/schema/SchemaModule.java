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

import com.google.common.collect.ImmutableList;
import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.GerritPersonIdentProvider;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.AllProjectsNameProvider;
import com.google.gerrit.server.config.AllUsersName;
import com.google.gerrit.server.config.AllUsersNameProvider;
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.config.AnonymousCowardNameProvider;
import com.google.gerrit.server.config.GerritImportedServerIds;
import com.google.gerrit.server.config.GerritImportedServerIdsProvider;
import com.google.gerrit.server.config.GerritServerId;
import com.google.gerrit.server.config.GerritServerIdProvider;
import com.google.gerrit.server.index.group.GroupIndexCollection;
import com.google.inject.TypeLiteral;
import org.eclipse.jgit.lib.PersonIdent;

/** Bindings for low-level Gerrit schema data. */
public class SchemaModule extends FactoryModule {
  @Override
  protected void configure() {
    bind(PersonIdent.class)
        .annotatedWith(GerritPersonIdent.class)
        .toProvider(GerritPersonIdentProvider.class);

    bind(AllProjectsName.class).toProvider(AllProjectsNameProvider.class).in(SINGLETON);

    bind(AllUsersName.class).toProvider(AllUsersNameProvider.class).in(SINGLETON);

    bind(String.class)
        .annotatedWith(AnonymousCowardName.class)
        .toProvider(AnonymousCowardNameProvider.class);

    bind(String.class)
        .annotatedWith(GerritServerId.class)
        .toProvider(GerritServerIdProvider.class)
        .in(SINGLETON);

    bind(new TypeLiteral<ImmutableList<String>>() {})
        .annotatedWith(GerritImportedServerIds.class)
        .toProvider(GerritImportedServerIdsProvider.class)
        .in(SINGLETON);

    // It feels wrong to have this binding in a seemingly unrelated module, but it's a dependency of
    // SchemaCreatorImpl, so it's needed.
    // TODO(dborowitz): Is there any way to untangle this?
    bind(GroupIndexCollection.class);
    bind(SchemaCreator.class).to(SchemaCreatorImpl.class);
  }
}
