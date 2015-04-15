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

package com.google.gerrit.server.query.change;

import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.Change.Id;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.query.OrPredicate;
import com.google.gerrit.server.query.Predicate;
import com.google.gerrit.server.query.QueryParseException;
import com.google.gerrit.server.query.change.ChangeQueryBuilder.Arguments;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.ResultSet;

import java.io.IOException;
import java.util.List;
import java.util.Set;

class HasEditsByProjectPredicate extends OrPredicate<ChangeData> implements
    ChangeDataSource {

  private static List<Predicate<ChangeData>> predicates(Set<Change.Id> ids) {
    List<Predicate<ChangeData>> r = Lists.newArrayListWithCapacity(ids.size());
    for (Change.Id id : ids) {
      r.add(new LegacyChangeIdPredicate(id));
    }
    return r;
  }

  private final Arguments args;
  private final IdentifiedUser user;
  private final String project;

  HasEditsByProjectPredicate(Arguments args, String project)
      throws QueryParseException {
    this(args, args.getIdentifiedUser(), project);
  }

  private static Set<Id> editsForProject(Arguments args, IdentifiedUser user,
      String project) throws QueryParseException {
    try {
      return args.editUtil.byProject(new Project.NameKey(project), user);
    } catch (IOException e) {
      throw new QueryParseException("Cannot retrieve edits", e);
    }
  }

  @Override
  public boolean match(ChangeData object) throws OrmException {
    return retrieveEditIds().contains(object.getId());
  }

  @Override
  public ResultSet<ChangeData> read() throws OrmException {
    return ChangeDataResultSet.change(args.changeDataFactory, args.db,
        args.db.get().changes().get(retrieveEditIds()));
  }

  @Override
  public boolean hasChange() {
    return true;
  }

  @Override
  public int getCardinality() {
    return 10;
  }

  @Override
  public int getCost() {
    return 0;
  }

  @Override
  public String toString() {
    if (project.indexOf(' ') < 0) {
      return ChangeQueryBuilder.FIELD_EDITSBYPROJECT + ":" + project;
    } else {
      return ChangeQueryBuilder.FIELD_EDITSBYPROJECT + ":\"" + project + "\"";
    }
  }

  private HasEditsByProjectPredicate(Arguments args, IdentifiedUser user,
      String project) throws QueryParseException {
    super(predicates(editsForProject(args, user, project)));
    this.args = args;
    this.user = user;
    this.project = project;
  }

  private Set<Change.Id> retrieveEditIds() throws OrmException {
    try {
      return args.editUtil.byProject(new Project.NameKey(project), user);
    } catch (IOException e) {
      throw new OrmException("Cannot retrieve edits", e);
    }
  }
}
