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

package com.google.gerrit.server.config;

import static com.google.gerrit.common.data.GlobalCapability.FLUSH_CACHES;
import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.annotations.RequiresAnyCapability;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.extensions.restapi.UnprocessableEntityException;
import com.google.gerrit.server.config.PostCaches.Input;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.util.ArrayList;
import java.util.List;

@RequiresAnyCapability({FLUSH_CACHES, MAINTAIN_SERVER})
@Singleton
public class PostCaches implements RestModifyView<ConfigResource, Input> {
  public static class Input {
    public Operation operation;
    public List<String> caches;

    public Input() {}

    public Input(Operation op) {
      this(op, null);
    }

    public Input(Operation op, List<String> c) {
      operation = op;
      caches = c;
    }
  }

  public enum Operation {
    FLUSH_ALL,
    FLUSH
  }

  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final FlushCache flushCache;

  @Inject
  public PostCaches(DynamicMap<Cache<?, ?>> cacheMap, FlushCache flushCache) {
    this.cacheMap = cacheMap;
    this.flushCache = flushCache;
  }

  @Override
  public Response<String> apply(ConfigResource rsrc, Input input)
      throws AuthException, BadRequestException, UnprocessableEntityException {
    if (input == null || input.operation == null) {
      throw new BadRequestException("operation must be specified");
    }

    switch (input.operation) {
      case FLUSH_ALL:
        if (input.caches != null) {
          throw new BadRequestException(
              "specifying caches is not allowed for operation 'FLUSH_ALL'");
        }
        flushAll();
        return Response.ok("");
      case FLUSH:
        if (input.caches == null || input.caches.isEmpty()) {
          throw new BadRequestException("caches must be specified for operation 'FLUSH'");
        }
        flush(input.caches);
        return Response.ok("");
      default:
        throw new BadRequestException("unsupported operation: " + input.operation);
    }
  }

  private void flushAll() throws AuthException {
    for (DynamicMap.Entry<Cache<?, ?>> e : cacheMap) {
      CacheResource cacheResource =
          new CacheResource(e.getPluginName(), e.getExportName(), e.getProvider());
      if (FlushCache.WEB_SESSIONS.equals(cacheResource.getName())) {
        continue;
      }
      flushCache.apply(cacheResource, null);
    }
  }

  private void flush(List<String> cacheNames) throws UnprocessableEntityException, AuthException {
    List<CacheResource> cacheResources = new ArrayList<>(cacheNames.size());

    for (String n : cacheNames) {
      String pluginName = "gerrit";
      String cacheName = n;
      int i = cacheName.lastIndexOf('-');
      if (i != -1) {
        pluginName = cacheName.substring(0, i);
        cacheName = cacheName.length() > i + 1 ? cacheName.substring(i + 1) : "";
      }

      Cache<?, ?> cache = cacheMap.get(pluginName, cacheName);
      if (cache != null) {
        cacheResources.add(new CacheResource(pluginName, cacheName, cache));
      } else {
        throw new UnprocessableEntityException(String.format("cache %s not found", n));
      }
    }

    for (CacheResource rsrc : cacheResources) {
      flushCache.apply(rsrc, null);
    }
  }
}
