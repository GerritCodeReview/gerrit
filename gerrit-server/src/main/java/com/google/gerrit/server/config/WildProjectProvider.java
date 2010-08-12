// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Provider;

public class WildProjectProvider implements Provider<Project> {
  private final Project wildProject;

  @Inject
  WildProjectProvider(final SystemConfig config, final SchemaFactory<ReviewDb> schemaFactory) throws OrmException {
    ReviewDb db = schemaFactory.open();
    try {
      wildProject = db.projects().get(config.wildProjectId);
    } finally {
      db.close();
    }
  }

  public Project get() {
    return wildProject;
  }
}
