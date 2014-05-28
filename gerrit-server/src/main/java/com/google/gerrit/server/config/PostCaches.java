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

import com.google.common.cache.Cache;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.extensions.annotations.RequiresCapability;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.config.PostCaches.Input;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@RequiresCapability(GlobalCapability.FLUSH_CACHES)
@Singleton
public class PostCaches implements RestModifyView<ConfigResource, Input> {
  public static class Input {
    public Operation operation;

    public Input() {
    }

    public Input(Operation op) {
      operation = op;
    }
  }

  public static enum Operation {
    FLUSH_ALL;
  }

  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final FlushCache flushCache;

  @Inject
  public PostCaches(DynamicMap<Cache<?, ?>> cacheMap,
      FlushCache flushCache) {
    this.cacheMap = cacheMap;
    this.flushCache = flushCache;
  }

  @Override
  public Object apply(ConfigResource rsrc, Input input) throws AuthException,
      ResourceNotFoundException, BadRequestException {
    if (input == null || input.operation == null) {
      throw new BadRequestException("operation must be specified");
    }

    switch (input.operation) {
      case FLUSH_ALL:
        for (DynamicMap.Entry<Cache<?, ?>> e : cacheMap) {
          CacheResource cacheResource =
              new CacheResource(e.getPluginName(), e.getExportName(),
                  e.getProvider());
          if (FlushCache.WEB_SESSIONS.equals(cacheResource.getName())) {
            continue;
          }
          flushCache.apply(cacheResource, new FlushCache.Input());
        }
        return Response.ok("ok");
      default:
        throw new BadRequestException("unsupported operation: " + input.operation);
    }
  }
}
