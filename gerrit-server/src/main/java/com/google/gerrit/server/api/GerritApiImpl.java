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

import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.api.GerritApi;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.projects.Projects;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

@Singleton
class GerritApiImpl extends GerritApi.NotImplemented implements GerritApi {
  private final Provider<Accounts> accounts;
  private final Provider<Changes> changes;
  private final Provider<Projects> projects;

  @Inject
  GerritApiImpl(Provider<Accounts> accounts,
      Provider<Changes> changes,
      Provider<Projects> projects) {
    this.accounts = accounts;
    this.changes = changes;
    this.projects = projects;
  }

  @Override
  public Accounts accounts() {
    return accounts.get();
  }

  @Override
  public Changes changes() {
    return changes.get();
  }

  @Override
  public Projects projects() {
    return projects.get();
  }
}
