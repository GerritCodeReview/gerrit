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

package com.google.gerrit.server.config;

import static com.google.inject.Scopes.SINGLETON;

import com.google.gerrit.extensions.config.FactoryModule;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.project.PerRequestProjectControlCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.RequestCleanup;
import com.google.inject.servlet.RequestScoped;

/** Bindings for {@link RequestScoped} entities. */
public class GerritRequestModule extends FactoryModule {
  @Override
  protected void configure() {
    bind(RequestCleanup.class).in(RequestScoped.class);
    bind(RequestScopedReviewDbProvider.class);
    bind(IdentifiedUser.RequestFactory.class).in(SINGLETON);

    bind(PerRequestProjectControlCache.class).in(RequestScoped.class);
    bind(ProjectControl.Factory.class).in(SINGLETON);
  }
}
