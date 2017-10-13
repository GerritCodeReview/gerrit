// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.query.project;

import com.google.common.base.Joiner;
import com.google.common.collect.Lists;
import com.google.gerrit.index.IndexConfig;
import com.google.gerrit.index.query.InternalQuery;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.index.project.ProjectIndexCollection;
import com.google.gerrit.server.project.ProjectData;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Query wrapper for the project index.
 *
 * <p>Instances are one-time-use. Other singleton classes should inject a Provider rather than
 * holding on to a single instance.
 */
public class InternalProjectQuery extends InternalQuery<ProjectData> {
  private static final Logger log = LoggerFactory.getLogger(InternalProjectQuery.class);

  @Inject
  InternalProjectQuery(
      ProjectQueryProcessor queryProcessor,
      ProjectIndexCollection indexes,
      IndexConfig indexConfig) {
    super(queryProcessor, indexes, indexConfig);
  }

  @Override
  public InternalProjectQuery setLimit(int n) {
    super.setLimit(n);
    return this;
  }

  @Override
  public InternalProjectQuery enforceVisibility(boolean enforce) {
    super.enforceVisibility(enforce);
    return this;
  }

  @Override
  public InternalProjectQuery setRequestedFields(Set<String> fields) {
    super.setRequestedFields(fields);
    return this;
  }

  @Override
  public InternalProjectQuery noFields() {
    super.noFields();
    return this;
  }

  public ProjectData oneByName(Project.NameKey projectName) throws OrmException {
    List<ProjectData> matches = query(ProjectPredicates.name(projectName));
    if (matches.size() == 1) {
      return matches.get(0);
    } else if (matches.size() > 0) {
      StringBuilder msg = new StringBuilder();
      msg.append("Ambiguous project name ").append(projectName.get()).append(" for projects: ");
      Joiner.on(", ").appendTo(msg, Lists.transform(matches, ProjectData.PROJECT_NAME_FUNCTION));
      log.warn(msg.toString());
    }
    return null;
  }
}
