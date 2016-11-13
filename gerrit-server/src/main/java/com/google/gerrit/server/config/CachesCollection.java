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

import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_CACHES;

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.annotations.RequiresAnyCapability;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AcceptsPost;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestApiException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.CurrentUser;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@RequiresAnyCapability({VIEW_CACHES, MAINTAIN_SERVER})
@Singleton
public class CachesCollection
    implements ChildCollection<ConfigResource, CacheResource>, AcceptsPost<ConfigResource> {

  private final DynamicMap<RestView<CacheResource>> views;
  private final Provider<ListCaches> list;
  private final Provider<CurrentUser> self;
  private final DynamicMap<Cache<?, ?>> cacheMap;
  private final PostCaches postCaches;

  @Inject
  CachesCollection(
      DynamicMap<RestView<CacheResource>> views,
      Provider<ListCaches> list,
      Provider<CurrentUser> self,
      DynamicMap<Cache<?, ?>> cacheMap,
      PostCaches postCaches) {
    this.views = views;
    this.list = list;
    this.self = self;
    this.cacheMap = cacheMap;
    this.postCaches = postCaches;
  }

  @Override
  public RestView<ConfigResource> list() {
    return list.get();
  }

  @Override
  public CacheResource parse(ConfigResource parent, IdString id)
      throws AuthException, ResourceNotFoundException {
    CurrentUser user = self.get();
    if (user instanceof AnonymousUser) {
      throw new AuthException("Authentication required");
    } else if (!user.isIdentifiedUser()) {
      throw new ResourceNotFoundException();
    } else if (!user.getCapabilities().canViewCaches()) {
      throw new AuthException("not allowed to view caches");
    }

    String cacheName = id.get();
    String pluginName = "gerrit";
    int i = cacheName.lastIndexOf('-');
    if (i != -1) {
      pluginName = cacheName.substring(0, i);
      cacheName = cacheName.length() > i + 1 ? cacheName.substring(i + 1) : "";
    }

    Provider<Cache<?, ?>> cacheProvider = cacheMap.byPlugin(pluginName).get(cacheName);
    if (cacheProvider == null) {
      throw new ResourceNotFoundException(id);
    }
    return new CacheResource(pluginName, cacheName, cacheProvider);
  }

  @Override
  public DynamicMap<RestView<CacheResource>> views() {
    return views;
  }

  @SuppressWarnings("unchecked")
  @Override
  public PostCaches post(ConfigResource parent) throws RestApiException {
    return postCaches;
  }
}
