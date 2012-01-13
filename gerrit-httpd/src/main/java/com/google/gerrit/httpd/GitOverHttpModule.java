// Copyright (C) 2009 The Android Open Source Project
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

package com.google.gerrit.httpd;

import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.servlet.ServletModule;

/**
 * Configures Git access over {@code "/p/PROJECT_NAME"} URLs.
 */
public class GitOverHttpModule extends ServletModule {
  private final AuthConfig authConfig;

  @Inject
  GitOverHttpModule(AuthConfig authConfig) {
    this.authConfig = authConfig;
  }

  @Override
  protected void configureServlets() {
    filter("/p/*").through(ProjectAccessPathFilter.class);
    if (authConfig.isTrustContainerAuth()) {
      filter("/p/*").through(ContainerAuthFilter.class);
    } else {
      filter("/p/*").through(ProjectDigestFilter.class);
    }
    serve("/p/*").with(ProjectServlet.class);
  }
}
