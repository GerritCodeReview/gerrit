// Copyright (C) 2018 The Android Open Source Project
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

import com.google.gerrit.extensions.client.GitBasicAuthPolicy;
import com.google.gerrit.server.config.AuthConfig;
import com.google.inject.Inject;
import com.google.inject.servlet.ServletModule;
import javax.servlet.Filter;

/** Configures filter for authenticating REST requests. */
public class GerritAuthModule extends ServletModule {
  private static final String NOT_AUTHORIZED_LFS_URL_REGEX = "^(?:(?!/a/))" + LFS_URL_WO_AUTH_REGEX;
  private final AuthConfig authConfig;

  @Inject
  GerritAuthModule(AuthConfig authConfig) {
    this.authConfig = authConfig;
  }

  @Override
  protected void configureServlets() {
    Class<? extends Filter> authFilter = retreiveAuthFilterFromConfig(authConfig);

    filterRegex(NOT_AUTHORIZED_LFS_URL_REGEX).through(authFilter);
    filter("/a/*").through(authFilter);
  }

  static Class<? extends Filter> retreiveAuthFilterFromConfig(AuthConfig authConfig) {
    Class<? extends Filter> authFilter;
    if (authConfig.isTrustContainerAuth()) {
      authFilter = ContainerAuthFilter.class;
    } else {
      authFilter =
          authConfig.getGitBasicAuthPolicy() == GitBasicAuthPolicy.OAUTH
              ? ProjectOAuthFilter.class
              : ProjectBasicAuthFilter.class;
    }
    return authFilter;
  }
}
