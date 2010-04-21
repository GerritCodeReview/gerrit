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

package com.google.gerrit.server.schema;

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.RefRight;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.lib.Constants;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

class Schema_25 extends SchemaVersion {
  private Set<ApprovalCategory.Id> nonActions;

  @Inject
  Schema_25(Provider<Schema_24> prior) {
    super(prior);
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException, SQLException {
    nonActions = new HashSet<ApprovalCategory.Id>();
    for (ApprovalCategory c : db.approvalCategories().all()) {
      if (!c.isAction()) {
        nonActions.add(c.getId());
      }
    }

    List<RefRight> rights = new ArrayList<RefRight>();
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      ResultSet rs = stmt.executeQuery("SELECT * FROM project_rights");
      try {
        while (rs.next()) {
          rights.add(toRefRight(rs));
        }
      } finally {
        rs.close();
      }

      db.refRights().insert(rights);
      stmt.execute("CREATE INDEX ref_rights_byCatGroup"
          + " ON ref_rights (category_id, group_id)");
    } finally {
      stmt.close();
    }
  }

  private RefRight toRefRight(ResultSet rs) throws SQLException {
    short min_value = rs.getShort("min_value");
    short max_value = rs.getShort("max_value");
    String category_id = rs.getString("category_id");
    int group_id = rs.getInt("group_id");
    String project_name = rs.getString("project_name");

    ApprovalCategory.Id category = new ApprovalCategory.Id(category_id);
    Project.NameKey project = new Project.NameKey(project_name);
    AccountGroup.Id group = new AccountGroup.Id(group_id);

    RefRight.RefPattern ref;
    if (category.equals(ApprovalCategory.SUBMIT)
        || category.equals(ApprovalCategory.PUSH_HEAD)
        || nonActions.contains(category)) {
      // Explicitly related to a branch head.
      ref = new RefRight.RefPattern(Constants.R_HEADS + "*");

    } else if (category.equals(ApprovalCategory.PUSH_TAG)) {
      // Explicitly related to the tag namespace.
      ref = new RefRight.RefPattern(Constants.R_TAGS + "/*");

    } else if (category.equals(ApprovalCategory.READ)
        || category.equals(ApprovalCategory.OWN)) {
      // Currently these are project-wide rights, so apply that way.
      ref = new RefRight.RefPattern(RefRight.ALL);

    } else {
      // Assume project wide for the default.
      ref = new RefRight.RefPattern(RefRight.ALL);
    }

    RefRight.Key key = new RefRight.Key(project, ref, category, group);
    RefRight r = new RefRight(key);
    r.setMinValue(min_value);
    r.setMaxValue(max_value);
    return r;
  }
}
