// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.restapi.config;

import static com.google.gerrit.server.config.CacheResource.CACHE_KIND;
import static com.google.gerrit.server.config.ConfigResource.CONFIG_KIND;

import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;

public class RestCacheAdminModule extends RestApiModule {

  @Override
  protected void configure() {
    DynamicMap.mapOf(binder(), CACHE_KIND);

    child(CONFIG_KIND, "caches").to(CachesCollection.class);
    postOnCollection(CACHE_KIND).to(PostCaches.class);
    get(CACHE_KIND).to(GetCache.class);
    post(CACHE_KIND, "flush").to(FlushCache.class);

    get(CONFIG_KIND, "summary").to(GetSummary.class);
  }
}
