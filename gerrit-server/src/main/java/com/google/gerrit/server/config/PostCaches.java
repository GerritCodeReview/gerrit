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
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.config.PostCaches.Input;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import java.util.Map.Entry;

@Singleton
public class PostCaches implements RestModifyView<ConfigResource, Input> {
  public static class Input {
    public Operation operation;
  }

  public static enum Operation {
    LIST, FLUSH_ALL;
  }

  private final Provider<CurrentUser> self;
  private final Provider<ListCaches> listCaches;
  private final Provider<FlushCache> flushCache;

  @Inject
  public PostCaches(Provider<CurrentUser> self,
      Provider<ListCaches> listCaches, Provider<FlushCache> flushCache) {
    this.self = self;
    this.listCaches = listCaches;
    this.flushCache = flushCache;
  }

  @Override
  public Object apply(ConfigResource rsrc, Input input) throws AuthException,
      ResourceNotFoundException, BadRequestException {
    CurrentUser user = self.get();
    if (user instanceof AnonymousUser) {
      throw new AuthException("Authentication required");
    } else if(!(user.isIdentifiedUser())) {
      throw new ResourceNotFoundException();
    }

    if (input == null || input.operation == null) {
      throw new BadRequestException("operation must be specified");
    }

    switch (input.operation) {
      case LIST:
        if (!user.getCapabilities().canViewCaches()) {
          throw new AuthException("not allowed to view caches");
        }
        return listCaches.get().getCaches().keySet();
      case FLUSH_ALL:
        if (!user.getCapabilities().canFlushCaches()) {
          throw new AuthException("not allowed to flush caches");
        }
        for (Entry<String, Provider<Cache<?, ?>>> e :
            listCaches.get().getCaches().entrySet()) {
          if (FlushCache.WEB_SESSIONS.equals(e.getKey())) {
            continue;
          }
          flushCache.get().apply(new CacheResource(e.getKey(), e.getValue()),
              new FlushCache.Input());
        }
        return Response.ok("ok");
      default:
        throw new BadRequestException("unsupported operation: " + input.operation);
    }
  }
}
