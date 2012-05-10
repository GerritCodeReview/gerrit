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

import com.google.gerrit.server.plugins.ReloadPluginListener;
import com.google.gerrit.server.plugins.StartPluginListener;
import com.google.inject.internal.UniqueAnnotations;
import com.google.inject.servlet.ServletModule;

public class HttpPluginModule extends ServletModule {
  @Override
  protected void configureServlets() {
    bind(HttpPluginServlet.class);
    serve("/plugins/*").with(HttpPluginServlet.class);

    bind(StartPluginListener.class)
      .annotatedWith(UniqueAnnotations.create())
      .to(HttpPluginServlet.class);

    bind(ReloadPluginListener.class)
      .annotatedWith(UniqueAnnotations.create())
      .to(HttpPluginServlet.class);
  }
}
