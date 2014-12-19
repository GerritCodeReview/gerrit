// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import static com.google.gerrit.server.query.Predicate.and;

import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import java.util.List;

/** Execute a single common query over changes. */
public class CommonChangeQuery {
  private final QueryProcessor qp;
  private final ChangeQueryBuilder qb;

  @Inject
  CommonChangeQuery(QueryProcessor queryProcessor) {
    qp = queryProcessor;
    qb = qp.getQueryBuilder();
  }

  public CommonChangeQuery setLimit(int n) {
    qp.setLimit(n);
    return this;
  }

  public List<ChangeData> byProjectOpen(Project.NameKey project)
      throws OrmException {
    return query(and(qb.project(project.get()), qb.status_open()));
  }

  private List<ChangeData> query(Predicate<ChangeData> p) throws OrmException {
    try {
      return qp.queryChanges(p).changes();
    } catch (QueryParseException e) {
      throw new OrmException(e);
    }
  }
}
