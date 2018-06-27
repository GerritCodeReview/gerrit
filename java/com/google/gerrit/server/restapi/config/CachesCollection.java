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

import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_CACHES;

import com.google.common.cache.Cache;
import com.google.gerrit.extensions.annotations.RequiresAnyCapability;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.ChildCollection;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.config.CacheResource;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.permissions.GlobalPermission;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@RequiresAnyCapability({VIEW_CACHES, MAINTAIN_SERVER})
@Singleton
public class CachesCollection implements ChildCollection<ConfigResource, CacheResource> {

  private final DynamicMap<RestView<CacheResource>> views;
  private final Provider<ListCaches> list;
  private final PermissionBackend permissionBackend;
  private final DynamicMap<Cache<?, ?>> cacheMap;

  @Inject
  CachesCollection(
      DynamicMap<RestView<CacheResource>> views,
      Provider<ListCaches> list,
      PermissionBackend permissionBackend,
      DynamicMap<Cache<?, ?>> cacheMap) {
    this.views = views;
    this.list = list;
    this.permissionBackend = permissionBackend;
    this.cacheMap = cacheMap;
  }

  @Override
  public RestView<ConfigResource> list() {
    return list.get();
  }

  @Override
  public CacheResource parse(ConfigResource parent, IdString id)
      throws AuthException, ResourceNotFoundException, PermissionBackendException {
    permissionBackend.currentUser().check(GlobalPermission.VIEW_CACHES);

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
}
