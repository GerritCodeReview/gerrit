// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.api;

import com.google.gerrit.extensions.api.GerritApi;
import com.google.inject.AbstractModule;

public class Module extends AbstractModule {
  @Override
  protected void configure() {
    bind(GerritApi.class).to(GerritApiImpl.class);

    install(new com.google.gerrit.server.api.accounts.Module());
    install(new com.google.gerrit.server.api.changes.Module());
    install(new com.google.gerrit.server.api.config.Module());
    install(new com.google.gerrit.server.api.groups.Module());
    install(new com.google.gerrit.server.api.projects.Module());
  }
}
