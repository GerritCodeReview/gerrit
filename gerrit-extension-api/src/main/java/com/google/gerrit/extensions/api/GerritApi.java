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

package com.google.gerrit.extensions.api;

import com.google.gerrit.extensions.api.accounts.Accounts;
import com.google.gerrit.extensions.api.changes.Changes;
import com.google.gerrit.extensions.api.config.Config;
import com.google.gerrit.extensions.api.groups.Groups;
import com.google.gerrit.extensions.api.projects.Projects;
import com.google.gerrit.extensions.restapi.NotImplementedException;

public interface GerritApi {
  Accounts accounts();

  Changes changes();

  Config config();

  Groups groups();

  Projects projects();

  /**
   * A default implementation which allows source compatibility when adding new methods to the
   * interface.
   */
  class NotImplemented implements GerritApi {
    @Override
    public Accounts accounts() {
      throw new NotImplementedException();
    }

    @Override
    public Changes changes() {
      throw new NotImplementedException();
    }

    @Override
    public Config config() {
      throw new NotImplementedException();
    }

    @Override
    public Groups groups() {
      throw new NotImplementedException();
    }

    @Override
    public Projects projects() {
      throw new NotImplementedException();
    }
  }
}
