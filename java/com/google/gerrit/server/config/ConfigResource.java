// Copyright (C) 2013 The Android Open Source Project
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

import com.google.gerrit.extensions.restapi.CacheControl;
import com.google.gerrit.extensions.restapi.RestResource;
import com.google.gerrit.extensions.restapi.RestView;
import com.google.inject.TypeLiteral;
import java.util.concurrent.TimeUnit;

public class ConfigResource implements RestResource {
  public static final TypeLiteral<RestView<ConfigResource>> CONFIG_KIND = new TypeLiteral<>() {};

  /**
   * Default cache control that gets set on the 'Cache-Control' header for responses on this
   * resource that are cacheable.
   *
   * <p>Not all resources are cacheable and in fact the vast majority might not be. Caching is a
   * trade-off between the freshness of data and the number of QPS that the web UI sends.
   */
  public static CacheControl DEFAULT_CACHE_CONTROL = CacheControl.PRIVATE(300, TimeUnit.SECONDS);
}
