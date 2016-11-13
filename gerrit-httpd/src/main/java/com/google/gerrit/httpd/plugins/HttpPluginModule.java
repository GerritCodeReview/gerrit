// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.httpd.plugins;

import com.google.gerrit.httpd.resources.Resource;
import com.google.gerrit.httpd.resources.ResourceKey;
import com.google.gerrit.httpd.resources.ResourceWeigher;
import com.google.gerrit.server.cache.CacheModule;
import com.google.gerrit.server.plugins.ModuleGenerator;
import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.servlet.ServletModule;

public class HttpPluginModule extends ServletModule {
  static final String PLUGIN_RESOURCES = "plugin_resources";

  @Override
  protected void configureServlets() {
    bind(HttpPluginServlet.class);
    serveRegex("^/(?:a/)?plugins/(.*)?$").with(HttpPluginServlet.class);

    bind(LfsPluginServlet.class);
    serveRegex(LfsPluginServlet.URL_REGEX).with(LfsPluginServlet.class);

    bind(StartPluginListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(HttpPluginServlet.class);

    bind(ReloadPluginListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(HttpPluginServlet.class);

    bind(StartPluginListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(LfsPluginServlet.class);

    bind(ReloadPluginListener.class)
        .annotatedWith(UniqueAnnotations.create())
        .to(LfsPluginServlet.class);

    bind(ModuleGenerator.class).to(HttpAutoRegisterModuleGenerator.class);

    install(
        new CacheModule() {
          @Override
          protected void configure() {
            cache(PLUGIN_RESOURCES, ResourceKey.class, Resource.class)
                .maximumWeight(2 << 20)
                .weigher(ResourceWeigher.class);
          }
        });
  }
}
