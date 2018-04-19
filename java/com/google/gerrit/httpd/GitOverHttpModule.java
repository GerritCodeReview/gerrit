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

import static com.google.gerrit.extensions.api.lfs.LfsDefinitions.LFS_URL_WO_AUTH_REGEX;
import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.extensions.registration.DynamicSet;
import com.google.gerrit.httpd.auth.BasicHttpAuthProtocolHandler;
import com.google.gerrit.httpd.auth.DefaultHttpAuthProtocolSelector;
import com.google.gerrit.httpd.auth.HttpAuthProtocolHandler;
import com.google.gerrit.httpd.auth.HttpAuthorizer;
import com.google.gerrit.reviewdb.client.CoreDownloadSchemes;
import com.google.gerrit.server.config.AuthConfig;
import com.google.gerrit.server.config.DownloadConfig;
import com.google.inject.Inject;
import com.google.inject.servlet.ServletModule;

/** Configures Git access over HTTP with authentication. */
public class GitOverHttpModule extends ServletModule {
  private static final String NOT_AUTHORIZED_LFS_URL_REGEX = "^(?:(?!/a/))" + LFS_URL_WO_AUTH_REGEX;

  private final AuthConfig authConfig;
  private final DownloadConfig downloadConfig;

  @Inject
  GitOverHttpModule(AuthConfig authConfig, DownloadConfig downloadConfig) {
    this.authConfig = authConfig;
    this.downloadConfig = downloadConfig;
  }

  @Override
  protected void configureServlets() {
    DynamicSet.setOf(binder(), HttpAuthProtocolHandler.class);
    if (authConfig.isTrustContainerAuth()) {
      // authFilter = ContainerAuthFilter.class;
      throw new RuntimeException("implement me"); // TODO(dpursehouse)
    }

    DynamicSet.bind(binder(), HttpAuthProtocolHandler.class).to(BasicHttpAuthProtocolHandler.class);
    bind(DefaultHttpAuthProtocolSelector.class).in(SINGLETON);

    if (isHttpEnabled()) {
      String git = GitOverHttpServlet.URL_REGEX;
      filterRegex(git).through(HttpAuthorizer.class);
      serveRegex(git).with(GitOverHttpServlet.class);
    }

    filterRegex(NOT_AUTHORIZED_LFS_URL_REGEX).through(HttpAuthorizer.class);
    filter("/a/*").through(HttpAuthorizer.class);
  }

  private boolean isHttpEnabled() {
    return downloadConfig.getDownloadSchemes().contains(CoreDownloadSchemes.ANON_HTTP)
        || downloadConfig.getDownloadSchemes().contains(CoreDownloadSchemes.HTTP);
  }
}
