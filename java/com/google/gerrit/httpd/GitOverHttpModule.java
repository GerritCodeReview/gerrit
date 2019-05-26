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

import static com.google.gerrit.httpd.GitOverHttpServlet.URL_REGEX;

import com.google.gerrit.entities.CoreDownloadSchemes;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.DownloadConfig;
import com.google.inject.Inject;
import com.google.inject.servlet.ServletModule;

/** Configures Git access over HTTP with authentication. */
public class GitOverHttpModule extends ServletModule {
  private final AuthConfig authConfig;
  private final DownloadConfig downloadConfig;

  @Inject
  GitOverHttpModule(AuthConfig authConfig, DownloadConfig downloadConfig) {
    this.authConfig = authConfig;
    this.downloadConfig = downloadConfig;
  }

  @Override
  protected void configureServlets() {
    if (downloadConfig.getDownloadSchemes().contains(CoreDownloadSchemes.ANON_HTTP)
        || downloadConfig.getDownloadSchemes().contains(CoreDownloadSchemes.HTTP)) {
      filterRegex(URL_REGEX).through(GerritAuthModule.retreiveAuthFilterFromConfig(authConfig));
      serveRegex(URL_REGEX).with(GitOverHttpServlet.class);
    }
  }
}
