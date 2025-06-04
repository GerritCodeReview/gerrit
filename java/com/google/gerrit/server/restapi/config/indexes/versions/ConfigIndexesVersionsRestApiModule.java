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

package com.google.gerrit.server.restapi.config.indexes.versions;

import static com.google.gerrit.server.config.IndexResource.INDEX_KIND;
import static com.google.gerrit.server.config.IndexVersionResource.INDEX_VERSION_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

/**
 * Guice module that binds all REST endpoints for {@code
 * /config/server/indexes/<index-version>/versions}.
 */
public class ConfigIndexesVersionsRestApiModule extends RestApiModule {
  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), INDEX_VERSION_KIND);

    /** List index versions {@code GET /config/server/indexes/<index-version>/versions}. */
    child(INDEX_KIND, "versions").to(IndexVersionsCollection.class);

    /** Get index version {@code GET /config/server/indexes/<index-version>/versions/<version>}. */
    get(INDEX_VERSION_KIND).to(GetIndexVersion.class);

    /**
     * Reindex index version {@code POST
     * /config/server/indexes/<index-version>/versions/<version>/reindex}.
     */
    post(INDEX_VERSION_KIND, "reindex").to(ReindexIndexVersion.class);

    /**
     * Create snapshot of index version {@code POST
     * /config/server/indexes/<index-version>/versions/<version>/snapshot}.
     */
    post(INDEX_VERSION_KIND, "snapshot").to(SnapshotIndexVersion.class);
  }
}
