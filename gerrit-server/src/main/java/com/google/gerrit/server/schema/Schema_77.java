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

import static com.google.common.base.Preconditions.checkArgument;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.LabelType;
import com.google.gerrit.common.data.LabelTypes;
import com.google.gerrit.common.data.LabelValue;
import com.google.gerrit.reviewdb.client.PatchSetApproval.LabelId;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.schema.sql.DialectH2;
import com.google.gwtorm.schema.sql.DialectMySQL;
import com.google.gwtorm.schema.sql.DialectPostgreSQL;
import com.google.gwtorm.schema.sql.SqlDialect;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.sql.PreparedStatement;
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
    try {
      LegacyLabelTypes labelTypes = getLegacyTypes(db);
      SqlDialect dialect = ((JdbcSchema) db).getDialect();
      if (dialect instanceof DialectH2) {
        alterTable(db, "ALTER TABLE %s ALTER COLUMN %s varchar(255)");
      } else if (dialect instanceof DialectPostgreSQL) {
        alterTable(db, "ALTER TABLE %s ALTER %s TYPE varchar(255)");
      } else if (dialect instanceof DialectMySQL) {
        alterTable(db, "ALTER TABLE %s MODIFY %s varchar(255) BINARY");
      } else {
        alterTable(db, "ALTER TABLE %s MODIFY %s varchar(255)");
      }
      migratePatchSetApprovals(db, labelTypes);
      migrateLabelsToAllProjects(db, labelTypes);
    } catch (RepositoryNotFoundException e) {
      throw new OrmException(e);
    } catch (SQLException e) {
      throw new OrmException(e);
    } catch (IOException e) {
      throw new OrmException(e);
    } catch (ConfigInvalidException e) {
      throw new OrmException(e);
    }
    ui.message(
        "Migrated label types from database to All-Projects project.config");
  }

  private void alterTable(ReviewDb db, String sqlFormat) throws SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      stmt.executeUpdate(
          String.format(sqlFormat, "patch_set_approvals", "category_id"));
    } finally {
      stmt.close();
    }
  }

  private void migrateLabelsToAllProjects(ReviewDb db,
      LegacyLabelTypes labelTypes) throws SQLException,
      RepositoryNotFoundException, IOException, ConfigInvalidException {
    Repository git = mgr.openRepository(allProjects);

    try {
      MetaDataUpdate md =
          new MetaDataUpdate(GitReferenceUpdated.DISABLED, allProjects, git);
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);

      ProjectConfig config = ProjectConfig.read(md);
      Map<String, LabelType> configTypes = config.getLabelSections();
      List<LabelType> newTypes = Lists.newArrayList();
      for (LegacyLabelType type : labelTypes.getLegacyLabelTypes()) {
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
    } finally {
      git.close();
    }
  }

  private void migratePatchSetApprovals(ReviewDb db,
      LegacyLabelTypes labelTypes) throws SQLException {
    PreparedStatement stmt = ((JdbcSchema) db).getConnection().prepareStatement(
        "UPDATE patch_set_approvals SET category_id = ? WHERE category_id = ?");
    try {
      for (LegacyLabelType type : labelTypes.getLegacyLabelTypes()) {
        stmt.setString(1, type.getName());
        stmt.setString(2, type.getId());
        stmt.addBatch();
      }
      stmt.executeBatch();
    } finally {
      stmt.close();
    }
  }

  static class LegacyLabelType extends LabelType {
    private String id;

    private LegacyLabelType(String name, List<LabelValue> values) {
      super(name, values);
    }

    String getId() {
      return id;
    }

    private void setId(String id) {
      checkArgument(id.length() <= 4, "Invalid legacy label ID: \"%s\"", id);
      this.id = id;
    }
  }

  static class LegacyLabelTypes extends LabelTypes {
    private final List<LegacyLabelType> legacyTypes;

    private final Map<String, LegacyLabelType> byId;

    private LegacyLabelTypes(List<LegacyLabelType> types) {
      super(types);
      legacyTypes = types;
      byId = Maps.newHashMap();
      for (LegacyLabelType type : types) {
        byId.put(type.getId(), type);
      }
    }

    List<LegacyLabelType> getLegacyLabelTypes() {
      return legacyTypes;
    }

    @Override
    public LegacyLabelType byLabel(LabelId labelId) {
      LegacyLabelType t = byId.get(labelId.get());
      return t != null ? t : (LegacyLabelType) super.byLabel(labelId);
    }

    LegacyLabelType byId(LabelId id) {
      return byId.get(id.get());
    }
  }

  static LegacyLabelTypes getLegacyTypes(ReviewDb db) throws SQLException {
    List<LegacyLabelType> types = Lists.newArrayListWithCapacity(2);
    Statement catStmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      ResultSet catRs = catStmt.executeQuery(
          "SELECT category_id, name, abbreviated_name, function_name, "
          + " copy_min_score"
          + " FROM approval_categories"
          + " ORDER BY position, name");
      PreparedStatement valStmt = ((JdbcSchema) db).getConnection().prepareStatement(
              "SELECT value, name"
                      + " FROM approval_category_values"
                      + " WHERE category_id = ?");
      try {
        while (catRs.next()) {
          String id = catRs.getString("category_id");
          valStmt.setString(1, id);
          List<LabelValue> values = Lists.newArrayListWithCapacity(5);
          ResultSet valRs = valStmt.executeQuery();
          while (valRs.next()) {
            values.add(new LabelValue(
                valRs.getShort("value"), valRs.getString("name")));
          }
          LegacyLabelType type =
              new LegacyLabelType(getLabelName(catRs.getString("name")), values);
          type.setId(id);
          type.setAbbreviation(catRs.getString("abbreviated_name"));
          type.setFunctionName(catRs.getString("function_name"));
          type.setCopyMinScore("Y".equals(catRs.getString("copy_min_score")));
          types.add(type);
        }
      } finally {
        valStmt.close();
      }
    } finally {
      catStmt.close();
    }
    return new LegacyLabelTypes(types);
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
