// Copyright (C) 2024 The Android Open Source Project
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

package com.google.gerrit.server.api.config;

import static com.google.gerrit.server.api.ApiUtil.asRestApiException;

import com.google.gerrit.extensions.api.config.CachesApi;
import com.google.gerrit.extensions.common.CacheInfo;
import com.google.gerrit.extensions.common.Input;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.server.config.CacheResource;
import com.google.gerrit.server.restapi.config.FlushCache;
import com.google.gerrit.server.restapi.config.GetCache;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

public class CachesApiImpl implements CachesApi {
  interface Factory {
    CachesApiImpl create(CacheResource r);
  }

  private final CacheResource cache;
  private final GetCache getCache;
  private final FlushCache flushCache;

  @Inject
  CachesApiImpl(GetCache getCache, FlushCache flushCache, @Assisted CacheResource r) {
    this.getCache = getCache;
    this.flushCache = flushCache;
    this.cache = r;
  }

  @Override
  public CacheInfo get() throws RestApiException {
    try {
      return getCache.apply(cache).value();
    } catch (Exception e) {
      throw asRestApiException("Cannot get cache", e);
    }
  }

  @Override
  public void flush() throws RestApiException {
    try {
      @SuppressWarnings("unused")
      var unused = flushCache.apply(cache, new Input());
    } catch (Exception e) {
      throw asRestApiException("Cannot flush cache", e);
    }
  }
}
