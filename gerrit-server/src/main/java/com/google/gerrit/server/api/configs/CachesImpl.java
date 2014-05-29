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

package com.google.gerrit.server.api.configs;

import com.google.gerrit.extensions.api.configs.Caches;
import com.google.gerrit.extensions.common.CacheInfo;
import com.google.gerrit.server.config.ConfigResource;
import com.google.gerrit.server.config.ListCaches;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Collection;

@Singleton
public class CachesImpl implements Caches {
  private final ListCaches listCaches;

  @Inject
  CachesImpl(ListCaches listCaches) {
    this.listCaches = listCaches;
  }

  @Override
  public Collection<CacheInfo> list() {
    return listCaches.apply(new ConfigResource()).values();
  }
}
