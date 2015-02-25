// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.httpd.auth.oauth;

import com.google.gerrit.extensions.auth.oauth.OAuthServiceProvider;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.inject.servlet.ServletModule;

/** Servlets and support related to OAuth authentication. */
public class OAuthModule extends ServletModule {

  @Override
  protected void configureServlets() {
    filter("/login", "/login/*", "/oauth").through(OAuthWebFilter.class);
    // This is needed to invalidate OAuth session during logout
    serve("/logout").with(OAuthLogoutServlet.class);
    DynamicMap.mapOf(binder(), OAuthServiceProvider.class);
  }
}
