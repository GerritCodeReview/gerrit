// Copyright (C) 2025 The Android Open Source Project
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

package com.google.gerrit.server.restapi.config.indexes;

import static com.google.gerrit.server.config.ConfigResource.CONFIG_KIND;
import static com.google.gerrit.server.config.IndexResource.INDEX_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.server.restapi.config.indexes.versions.ConfigIndexesVersionsRestApiModule;

/** Guice module that binds all REST endpoints for {@code /config/server/indexes}. */
public class ConfigIndexesRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), INDEX_KIND);

    /** List indexes {@code GET /config/server/indexes}. */
    child(CONFIG_KIND, "indexes").to(IndexCollection.class);

    /** Get index {@code GET /config/server/indexes/<index-id>}. */
    get(INDEX_KIND).to(GetIndex.class);

    /** Create a snapshot for the index {@code POST /config/server/indexes/<index-id>/snapshot}. */
    post(INDEX_KIND, "snapshot").to(SnapshotIndex.class);

    /** Module for {@code/config/server/indexes/<index-id>/versions}. */
    install(new ConfigIndexesVersionsRestApiModule());
  }
}
