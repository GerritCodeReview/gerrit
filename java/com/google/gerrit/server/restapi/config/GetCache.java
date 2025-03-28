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

import com.google.gerrit.extensions.common.CacheInfo;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.cache.CacheInfoFactory;
import com.google.gerrit.server.config.CacheResource;
import com.google.inject.Singleton;

@Singleton
public class GetCache implements RestReadView<CacheResource> {

  @Override
  public Response<CacheInfo> apply(CacheResource rsrc) {
    return Response.ok(CacheInfoFactory.create(rsrc.getName(), rsrc.getCache()));
  }
}
