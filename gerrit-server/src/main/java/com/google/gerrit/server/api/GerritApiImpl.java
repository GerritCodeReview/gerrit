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
import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.config.Config;
import com.google.gerrit.extensions.api.groups.Groups;
import com.google.gerrit.extensions.api.projects.Projects;
import com.google.inject.Inject;
import com.google.inject.Singleton;

@Singleton
class GerritApiImpl implements GerritApi {
  private final Accounts accounts;
  private final Changes changes;
  private final Config config;
  private final Groups groups;
  private final Projects projects;

  @Inject
  GerritApiImpl(
      Accounts accounts, Changes changes, Config config, Groups groups, Projects projects) {
    this.accounts = accounts;
    this.changes = changes;
    this.config = config;
    this.groups = groups;
    this.projects = projects;
  }

  @Override
  public Accounts accounts() {
    return accounts;
  }

  @Override
  public Changes changes() {
    return changes;
  }

  @Override
  public Config config() {
    return config;
  }

  @Override
  public Groups groups() {
    return groups;
  }

  @Override
  public Projects projects() {
    return projects;
  }
}
