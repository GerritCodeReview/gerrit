// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.plugins.checks.api;

import static com.google.gerrit.plugins.checks.api.CheckerResource.CHECKER_KIND;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.extensions.registration.DynamicMap;
import com.google.gerrit.extensions.restapi.RestApiModule;
import com.google.gerrit.httpd.plugins.HttpPluginModule;

public class HttpModule extends HttpPluginModule {
  @Override
  protected void configureServlets() {
    bind(CheckersCollection.class);

    bind(Checkers.class).to(CheckersImpl.class);

    serveRegex("^/checkers/(.*)$").with(CheckersRestApiServlet.class);

    install(
        new RestApiModule() {
          @Override
          public void configure() {
            DynamicMap.mapOf(binder(), CHECKER_KIND);
            postOnCollection(CHECKER_KIND).to(CreateChecker.class);
            get(CHECKER_KIND).to(GetChecker.class);
            post(CHECKER_KIND).to(UpdateChecker.class);
          }
        });

    install(
        new FactoryModule() {
          @Override
          public void configure() {
            factory(CheckerApiImpl.Factory.class);
          }
        });
  }
}
