// Copyright 2008 Google Inc.
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

package com.google.gerrit.pgm;

import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.AccountGroupMember;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ProjectRight;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.reviewdb.SystemConfig;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.git.InvalidRepositoryException;
import com.google.gerrit.git.PatchSetImporter;
import com.google.gerrit.server.GerritServer;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;

import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.ProgressMonitor;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.RefUpdate;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.TextProgressMonitor;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Imports data from Gerrit 1 into Gerrit 2.
 * <p>
 * The tool assumes that <code>devutil/import_gerrit1.sql</code> has already
 * been executed on this schema. All existing ProjectRight entities are wiped
 * from the database and generated from scratch.
 * <p>
 * The tool requires Gerrit 1 tables (<code>gerrit1.$table_name</code>) through
 * the same database connection as the ReviewDb schema is on.
 */
public class ImportGerrit1 {
  private static GerritServer gs;
  private static ReviewDb db;
  private static Connection sql;
  private static ApprovalCategory verifyCategory;
  private static ApprovalCategory approveCategory;
  private static ApprovalCategory submitCategory;

  public static void main(final String[] argv) throws OrmException,
      XsrfException, SQLException, IOException, InvalidRepositoryException {
    final ProgressMonitor pm = new TextProgressMonitor();
    gs = GerritServer.getInstance();
    db = Common.getSchemaFactory().open();
    sql = ((JdbcSchema) db).getConnection();
    try {
      verifyCategory = db.approvalCategories().byName("Verified");
      approveCategory = db.approvalCategories().byName("Code Review");
      submitCategory = db.approvalCategories().get(ApprovalCategory.SUBMIT);

      final Statement query = sql.createStatement();
      java.sql.ResultSet srcs;

      // Convert the approval right data from projects.
      //
      pm.start(1);
      pm.beginTask("Import project rights", ProgressMonitor.UNKNOWN);
      query.executeUpdate("DELETE FROM project_rights");
      insertApprovalWildCard();
      insertAccessWildCard();
      srcs =
          query.executeQuery("SELECT p.project_id, r.ar_key"
              + " FROM gerrit1.project_code_reviews r, projects p"
              + " WHERE p.project_id = r.project_id");
      while (srcs.next()) {
        final Project.Id projectId = new Project.Id(srcs.getInt(1));
        final String arKey = srcs.getString(2);
        doImport(projectId, arKey);
        pm.update(1);
      }
      srcs.close();
      pm.endTask();

      // Rebuild the cached PatchSet information directly from Git.
      // There's some oddities in the Gerrit 1 data that we got from
      // Google App Engine's data store; the quickest way to fix it
      // is to just recache the data from Git.
      //
      srcs =
          query.executeQuery("SELECT change_id,patch_set_id FROM patch_sets");
      final ArrayList<PatchSet.Id> psToDo = new ArrayList<PatchSet.Id>();
      while (srcs.next()) {
        final Change.Id changeId = new Change.Id(srcs.getInt(1));
        final PatchSet.Id psId = new PatchSet.Id(changeId, srcs.getInt(2));
        psToDo.add(psId);
      }
      query.close();

      final Map<String, Set<String>> validRefs =
          new HashMap<String, Set<String>>();
      pm.start(1);
      pm.beginTask("Import patch sets", psToDo.size());
      for (final PatchSet.Id psId : psToDo) {
        final Change c = db.changes().get(psId.getParentKey());
        final PatchSet ps = db.patchSets().get(psId);
        final String projectName = c.getDest().getParentKey().get();
        final Repository repo = gs.getRepositoryCache().get(projectName);
        final RevWalk rw = new RevWalk(repo);
        final RevCommit src =
            rw.parseCommit(ObjectId.fromString(ps.getRevision().get()));
        new PatchSetImporter(db, repo, src, ps, false).run();
        Set<String> s = validRefs.get(projectName);
        if (s == null) {
          s = new HashSet<String>();
          validRefs.put(projectName, s);
        }
        s.add(ps.getRefName());
        pm.update(1);
      }
      pm.endTask();

      pruneProjectRefs(pm, validRefs);
    } finally {
      db.close();
    }
  }

  private static void pruneProjectRefs(final ProgressMonitor pm,
      final Map<String, Set<String>> validRefs) throws OrmException,
      IOException {
    final List<Project> pToClean = db.projects().all().toList();
    pm.start(1);
    pm.beginTask("Prune old refs", pToClean.size());
    for (final Project p : pToClean) {
      final Repository repo;
      try {
        repo = gs.getRepositoryCache().get(p.getName());
      } catch (InvalidRepositoryException e) {
        pm.update(1);
        continue;
      }

      Set<String> valid = validRefs.get(p.getName());
      if (valid == null) {
        valid = Collections.emptySet();
      }

      final RevWalk rw = new RevWalk(repo);
      for (final Ref r : repo.getAllRefs().values()) {
        boolean delete = false;
        if (r.getName().startsWith("refs/merges/")) {
          delete = true;
        }
        if (r.getName().startsWith("refs/changes/")
            && !valid.contains(r.getName())) {
          delete = true;
        }
        if (delete) {
          final RefUpdate u = repo.updateRef(r.getName());
          u.setForceUpdate(true);
          u.delete(rw);
        }
      }
      pm.update(1);
    }
    pm.endTask();
  }

  private static void insertApprovalWildCard() throws OrmException {
    final ProjectRight.Key key =
        new ProjectRight.Key(ProjectRight.WILD_PROJECT,
            approveCategory.getId(), db.systemConfig().get(
                new SystemConfig.Key()).registeredGroupId);
    final ProjectRight pr = new ProjectRight(key);
    pr.setMinValue((short) -1);
    pr.setMaxValue((short) 1);
    db.projectRights().insert(Collections.singleton(pr));
  }

  private static void insertAccessWildCard() throws OrmException {
    final ProjectRight.Key key =
        new ProjectRight.Key(ProjectRight.WILD_PROJECT, ApprovalCategory.READ,
            db.systemConfig().get(new SystemConfig.Key()).anonymousGroupId);
    final ProjectRight pr = new ProjectRight(key);
    pr.setMinValue((short) 1);
    pr.setMaxValue((short) 1);
    db.projectRights().insert(Collections.singleton(pr));
  }

  private static void doImport(final Project.Id projectId, final String arKey)
      throws OrmException, SQLException {
    final int arId = findId(arKey);
    if (arId < 0) {
      return;
    }

    final Project proj = db.projects().get(projectId);
    final Set<AccountGroup.Id> approverg = groups(arId, "approver");
    final Set<AccountGroup.Id> verifierg = groups(arId, "verifier");
    final Set<AccountGroup.Id> submitterg = groups(arId, "submitter");

    final Set<Account.Id> approveru = users(arId, "approver");
    final Set<Account.Id> verifieru = users(arId, "verifier");
    final Set<Account.Id> submitteru = users(arId, "submitter");

    importCat(proj, "approvers", approveCategory, approverg, approveru);
    importCat(proj, "verifiers", verifyCategory, verifierg, verifieru);
    importCat(proj, "submitters", submitCategory, submitterg, submitteru);
  }

  private static void importCat(final Project proj, final String type,
      final ApprovalCategory category, final Set<AccountGroup.Id> groups,
      final Set<Account.Id> users) throws OrmException {
    final HashSet<Account.Id> needGroup = new HashSet<Account.Id>(users);
    for (final AccountGroup.Id groupId : groups) {
      insertRight(proj, category, groupId);
      for (final Iterator<Account.Id> i = needGroup.iterator(); i.hasNext();) {
        if (Common.getGroupCache().isInGroup(i.next(), groupId)) {
          i.remove();
        }
      }
    }

    if (!needGroup.isEmpty()) {
      final AccountGroup.Id groupId =
          new AccountGroup.Id(db.nextAccountGroupId());
      final AccountGroup group =
          new AccountGroup(new AccountGroup.NameKey(shortName(proj) + "_"
              + proj.getId().get() + "-" + type), groupId);
      group.setOwnerGroupId(proj.getOwnerGroupId());
      group.setDescription(proj.getName() + " " + type);
      db.accountGroups().insert(Collections.singleton(group));
      for (final Account.Id aId : needGroup) {
        db.accountGroupMembers().insert(
            Collections.singleton(new AccountGroupMember(
                new AccountGroupMember.Key(aId, groupId))));
      }
      insertRight(proj, category, groupId);
    }
  }

  private static void insertRight(final Project proj,
      final ApprovalCategory category, final AccountGroup.Id groupId)
      throws OrmException {
    final ProjectRight.Key key =
        new ProjectRight.Key(proj.getId(), category.getId(), groupId);
    final ProjectRight pr = new ProjectRight(key);
    if (category == approveCategory) {
      pr.setMinValue((short) -2);
      pr.setMaxValue((short) 2);
    } else if (category == verifyCategory) {
      pr.setMinValue((short) -1);
      pr.setMaxValue((short) 1);
    } else if (category == submitCategory) {
      pr.setMinValue((short) 1);
      pr.setMaxValue((short) 1);
    } else {
      throw new OrmException("Cannot import category " + category.getId());
    }
    db.projectRights().insert(Collections.singleton(pr));
  }

  private static String shortName(final Project proj) {
    final String n = proj.getName();
    final int s = n.lastIndexOf('/');
    return 0 < s ? n.substring(s + 1) : n;
  }

  private static Set<AccountGroup.Id> groups(final int arId, final String type)
      throws SQLException {
    final PreparedStatement ps =
        sql.prepareStatement("SELECT g.group_id FROM account_groups g,"
            + " gerrit1.approval_right_groups s, gerrit1.account_groups o"
            + " WHERE s.ar_id = ? AND s.type = ?"
            + " AND o.gae_key = s.group_key AND (g.name = o.name"
            + " OR (g.name = 'Administrators' AND o.name = 'admin'))");
    try {
      ps.setInt(1, arId);
      ps.setString(2, type);
      final java.sql.ResultSet rs = ps.executeQuery();
      try {
        final HashSet<AccountGroup.Id> r = new HashSet<AccountGroup.Id>();
        while (rs.next()) {
          r.add(new AccountGroup.Id(rs.getInt(1)));
        }
        return r;
      } finally {
        rs.close();
      }
    } finally {
      ps.close();
    }
  }

  private static Set<Account.Id> users(final int arId, final String type)
      throws SQLException {
    final PreparedStatement ps =
        sql.prepareStatement("SELECT a.account_id FROM accounts a,"
            + " gerrit1.approval_right_users s"
            + " WHERE s.ar_id = ? AND s.type = ?"
            + " AND a.preferred_email = s.email");
    try {
      ps.setInt(1, arId);
      ps.setString(2, type);
      final java.sql.ResultSet rs = ps.executeQuery();
      try {
        final HashSet<Account.Id> r = new HashSet<Account.Id>();
        while (rs.next()) {
          r.add(new Account.Id(rs.getInt(1)));
        }
        return r;
      } finally {
        rs.close();
      }
    } finally {
      ps.close();
    }
  }

  private static int findId(final String arKey) throws SQLException {
    final PreparedStatement ps =
        sql.prepareStatement("SELECT ar_id FROM gerrit1.approval_rights"
            + " WHERE gae_key=?");
    try {
      ps.setString(1, arKey);
      final java.sql.ResultSet rs = ps.executeQuery();
      try {
        if (rs.next()) {
          return rs.getInt(1);
        }
        return -1;
      } finally {
        rs.close();
      }
    } finally {
      ps.close();
    }
  }
}
