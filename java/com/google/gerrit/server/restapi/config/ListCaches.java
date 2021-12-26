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

import static com.google.common.collect.ImmutableList.toImmutableList;
import static com.google.gerrit.common.data.GlobalCapability.MAINTAIN_SERVER;
import static com.google.gerrit.common.data.GlobalCapability.VIEW_CACHES;
import static com.google.gerrit.server.config.CacheResource.cacheNameOf;
import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;

import com.google.common.cache.Cache;
import com.google.common.collect.Streams;
import com.google.gerrit.extensions.annotations.RequiresAnyCapability;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.registration.Extension;
import com.google.gerrit.extensions.restapi.BinaryResult;
import com.google.gerrit.extensions.restapi.Response;
import com.google.gerrit.extensions.restapi.RestReadView;
import com.google.gerrit.server.cache.CacheInfo;
import com.google.gerrit.server.config.ConfigResource;
import com.google.inject.Inject;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.kohsuke.args4j.Option;

@RequiresAnyCapability({VIEW_CACHES, MAINTAIN_SERVER})
public class ListCaches implements RestReadView<ConfigResource> {
  private final DynamicMap<Cache<?, ?>> cacheMap;

  public enum OutputFormat {
    LIST,
    TEXT_LIST
  }

  @Option(name = "--format", usage = "output format")
  private OutputFormat format;

  public ListCaches setFormat(OutputFormat format) {
    this.format = format;
    return this;
  }

  @Inject
  public ListCaches(DynamicMap<Cache<?, ?>> cacheMap) {
    this.cacheMap = cacheMap;
  }

  public Map<String, CacheInfo> getCacheInfos() {
    Map<String, CacheInfo> cacheInfos = new TreeMap<>();
    for (Extension<Cache<?, ?>> e : cacheMap) {
      cacheInfos.put(
          cacheNameOf(e.getPluginName(), e.getExportName()), new CacheInfo(e.getProvider().get()));
    }
    return cacheInfos;
  }

  @Override
  public Response<Object> apply(ConfigResource rsrc) {
    if (format == null) {
      return Response.ok(getCacheInfos());
    }
    Stream<String> cacheNames =
        Streams.stream(cacheMap)
            .map(e -> cacheNameOf(e.getPluginName(), e.getExportName()))
            .sorted();
    if (OutputFormat.TEXT_LIST.equals(format)) {
      return Response.ok(
          BinaryResult.create(cacheNames.collect(joining("\n")))
              .base64()
              .setContentType("text/plain")
              .setCharacterEncoding(UTF_8));
    }
    return Response.ok(cacheNames.collect(toImmutableList()));
  }
}
