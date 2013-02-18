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

package com.google.gerrit.server.schema;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Map;

public class Schema_77 extends SchemaVersion {
  private final GitRepositoryManager mgr;
  private final AllProjectsName allProjects;
  private final PersonIdent serverUser;

  @Inject
  Schema_77(Provider<Schema_76> prior, AllProjectsName allProjects,
      GitRepositoryManager mgr, @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.allProjects = allProjects;
    this.mgr = mgr;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException {
    LabelTypes legacyTypes;
    try {
      legacyTypes = getLegacyTypes(db);
    } catch (SQLException e) {
      throw new OrmException(e);
    }

    Repository git;
    try {
      git = mgr.openRepository(allProjects);
    } catch (IOException e) {
      throw new OrmException(e);
    }

    try {
      MetaDataUpdate md =
          new MetaDataUpdate(GitReferenceUpdated.DISABLED, allProjects, git);
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);

      ProjectConfig config = ProjectConfig.read(md);
      Map<String, LabelType> configTypes = config.getLabelSections();
      List<LabelType> newTypes = Lists.newArrayList();
      for (LabelType type : legacyTypes.getLabelTypes()) {
        if (!configTypes.containsKey(type.getName())) {
          newTypes.add(type);
        }
      }
      newTypes.addAll(configTypes.values());
      configTypes.clear();
      for (LabelType type : newTypes) {
        configTypes.put(type.getName(), type);
      }
      md.setMessage("Upgrade to Gerrit Code Review schema 77\n");
      config.commit(md);
    } catch (IOException e) {
      throw new OrmException(e);
    } catch (ConfigInvalidException e) {
      throw new OrmException(e);
    } finally {
      git.close();
    }
    ui.message(
        "Migrated label types from database to All-Projects project.config");
  }

  static LabelTypes getLegacyTypes(ReviewDb db) throws SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    List<LabelType> types = Lists.newArrayListWithExpectedSize(2);
    try {
      ResultSet rs = stmt.executeQuery(
          "SELECT approval_categories.category_id,"
          + " approval_categories.name AS category_name, abbreviated_name,"
          + " function_name, copy_min_score, value,"
          + " approval_category_values.name AS text"
          + " FROM approval_categories"
          + " JOIN approval_category_values ON approval_categories.category_id"
          + " = approval_category_values.category_id"
          + " ORDER BY position, category_name, value");
      try {
        String last = null;
        LabelType curr = null;
        List<LabelValue> values = Lists.newArrayListWithCapacity(5);
        while (rs.next()) {
          if (rs.getString("category_id") != last) {
            if (curr != null) {
              LabelType toAdd = new LabelType(curr.getName(), values);
              toAdd.setAbbreviatedName(curr.getAbbreviatedName());
              toAdd.setFunctionName(curr.getFunctionName());
              toAdd.setCopyMinScore(curr.isCopyMinScore());
              types.add(toAdd);
              values.clear();
            }
            curr = new LabelType(getLabelName(rs.getString("category_name")),
                ImmutableList.<LabelValue> of());
            curr.setAbbreviatedName(rs.getString("abbreviated_name"));
            curr.setFunctionName(rs.getString("function_name"));
            curr.setCopyMinScore(rs.getBoolean("copy_min_score"));
          }
          values.add(
              new LabelValue(rs.getShort("value"), rs.getString("text")));
        }
      } finally {
        rs.close();
      }
    } finally {
      stmt.close();
    }
    return new LabelTypes(types);
  }

  private static String getLabelName(String name) {
    StringBuilder r = new StringBuilder();
    for (int i = 0; i < name.length(); i++) {
      char c = name.charAt(i);
      if (('0' <= c && c <= '9') //
          || ('a' <= c && c <= 'z') //
          || ('A' <= c && c <= 'Z') //
          || (c == '-')) {
        r.append(c);
      } else if (c == ' ') {
        r.append('-');
      }
    }
    return r.toString();
  }
}
