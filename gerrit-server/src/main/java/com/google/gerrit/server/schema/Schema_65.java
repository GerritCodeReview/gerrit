// Copyright (C) 2012 The Android Open Source Project
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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.common.data.PermissionRule.Action;
import com.google.gerrit.reviewdb.client.Account;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupMember;
import com.google.gerrit.reviewdb.client.AccountGroupMemberAudit;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.NoReplication;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class Schema_65 extends SchemaVersion {
  private final AllProjectsName allProjects;
  private final GitRepositoryManager mgr;
  private final PersonIdent serverUser;

  @Inject
  Schema_65(Provider<Schema_64> prior,
      AllProjectsName allProjects,
      GitRepositoryManager mgr,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.allProjects = allProjects;
    this.mgr = mgr;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui)
      throws OrmException, SQLException {
    Repository git;
    try {
      git = mgr.openRepository(allProjects);
    } catch (RepositoryNotFoundException e) {
      throw new OrmException(e);
    }
    try {
      MetaDataUpdate md =
          new MetaDataUpdate(new NoReplication(), allProjects, git);
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);

      ProjectConfig config = ProjectConfig.read(md);
      Map<Integer, ContributorAgreement> agreements = getAgreementToAdd(db, config);
      if (agreements.isEmpty()) {
        return;
      }
      ui.message("Moved contributor agreements to project.config");

      // Create the group for individuals.
      createGroupForIndividuals(db, config, agreements);

      // Scan AccountAgreement
      // add the users to the autoVerify Group (keep the meta data)
      addAccountAgreements(db, agreements);

      // Scan AccountGroupAgreement
      // add the group to the accepted list.
      // ideally make the commit times/users match up with the existing ones
      addAccountGroupAgreements(db, config, agreements);

      // Save the agreements to the metadata
      md.setMessage("Upgrade to Gerrit Code Review schema 65\n");
      if (!config.commit(md)) {
        throw new OrmException("Cannot update " + allProjects);
      }
    } catch (IOException e) {
      throw new OrmException(e);
    } catch (ConfigInvalidException e) {
      throw new OrmException(e);
    } finally {
      git.close();
    }
  }

  private Map<Integer, ContributorAgreement> getAgreementToAdd(
      ReviewDb db, ProjectConfig config) throws SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      ResultSet rs = stmt.executeQuery(
          "SELECT short_name, id, require_contact_information," +
          "       short_description, agreement_url, auto_verify " +
          "FROM contributor_agreements WHERE active = 'Y'");
      try {
        Map<Integer, ContributorAgreement> agreements = Maps.newHashMap();
        while (rs.next()) {
          String name = rs.getString(1);
          if (config.getContributorAgreement(name) != null) {
            continue; // already exists
          }
          ContributorAgreement a = config.getContributorAgreement(name, true);
          agreements.put(rs.getInt(2), a);

          a.setRequireContactInformation("Y".equals(rs.getString(3)));
          a.setDescription(rs.getString(4));
          a.setAgreementUrl(rs.getString(5));
          if ("Y".equals(rs.getString(6))) {
            a.setAutoVerify(new GroupReference(null, null));
          }
        }
        return agreements;
      } finally {
        rs.close();
      }
    } finally {
      stmt.close();
    }
  }

  private AccountGroup createGroup(ReviewDb db, String groupName,
      AccountGroup.Id adminGroupId, String description)
          throws OrmException {
    final AccountGroup.Id groupId =
        new AccountGroup.Id(db.nextAccountGroupId());
    final AccountGroup.NameKey nameKey = new AccountGroup.NameKey(groupName);
    final AccountGroup.UUID uuid = GroupUUID.make(groupName, serverUser);
    final AccountGroup group = new AccountGroup(nameKey, groupId, uuid);
    group.setOwnerGroupId(adminGroupId);
    group.setDescription(description);
    final AccountGroupName gn = new AccountGroupName(group);
    // first insert the group name to validate that the group name hasn't
    // already been used to create another group
    db.accountGroupNames().insert(Collections.singleton(gn));
    db.accountGroups().insert(Collections.singleton(group));
    return group;
  }

  private AccountGroup.Id getAdministrateServerGroup(
      ReviewDb db, ProjectConfig cfg) throws OrmException {
    List<PermissionRule> rules = cfg.getAccessSection(AccessSection.GLOBAL_CAPABILITIES)
       .getPermission(GlobalCapability.ADMINISTRATE_SERVER)
       .getRules();

    for (PermissionRule rule : rules) {
      if (rule.getAction() == Action.ALLOW) {
        AccountGroup group = db.accountGroups()
            .byUUID(rule.getGroup().getUUID())
            .toList()
            .get(0);
        if (group != null) {
          return group.getId();
        }
      }
    }
    throw new IllegalStateException("no administrator group found");
  }

  private void createGroupForIndividuals(ReviewDb db, ProjectConfig config,
      Map<Integer, ContributorAgreement> agreements) throws OrmException {
    AccountGroup.Id adminGroupId = getAdministrateServerGroup(db, config);
    for (ContributorAgreement agreement : agreements.values()) {
      String name = "CLA Accepted - " + agreement.getName();
      AccountGroupName agn =
          db.accountGroupNames().get(new AccountGroup.NameKey(name));
      if (agn != null) {
        // TODO(cranger): should this attempt to generate a unique name?
        throw new IllegalStateException("group name already exists; " + name);
      }
      AccountGroup ag = createGroup(db, name, adminGroupId,
          String.format("Users who have accepted the %s CLA", agreement.getName()));
      GroupReference group = config.resolve(ag);
      agreement.setAccepted(Lists.newArrayList(new PermissionRule(group)));
      if (agreement.getAutoVerify() != null) {
        agreement.setAutoVerify(group);
      }
    }
  }

  private void addAccountAgreements(
      ReviewDb db, Map<Integer, ContributorAgreement> agreements)
          throws SQLException, OrmException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      ResultSet rs = stmt.executeQuery(
          "SELECT account_id, cla_id, accepted_on, reviewed_by," +
          "       reviewed_on, review_comments " +
          "FROM account_agreements WHERE status = 'V'");
      try {
        while (rs.next()) {
          Account.Id accountId = new Account.Id(rs.getInt(1));

          int claId = rs.getInt(2);
          ContributorAgreement agreement = agreements.get(claId);
          if (agreement == null) {
            continue;  // Agreement is invalid
          }

          // Enter Agreement
          AccountGroup group = db.accountGroups()
              .byUUID(agreement.getAccepted().get(0).getGroup().getUUID())
              .toList()
              .get(0);
          if (group == null) {
            throw new IllegalStateException("unexpect null AccountGroup");
          }

          final AccountGroupMember.Key key =
              new AccountGroupMember.Key(accountId, group.getId());
          AccountGroupMember m = db.accountGroupMembers().get(key);
          if (m == null) {
            m = new AccountGroupMember(key);
            // TODO(cranger): include the accepted and reviewed on fields?
            db.accountGroupMembersAudit().insert(
                Collections.singleton(
                    new AccountGroupMemberAudit(m, accountId)));
            db.accountGroupMembers().insert(Collections.singleton(m));
            // No need to evict the AccountCache, since it is not used in
            // this migration.
          }
        }
      } finally {
        rs.close();
      }
    } finally {
      stmt.close();
    }
  }

  private void addAccountGroupAgreements(ReviewDb db, ProjectConfig config,
      Map<Integer, ContributorAgreement> agreements)
          throws SQLException, OrmException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      ResultSet rs = stmt.executeQuery(
          "SELECT group_id, cla_id, accepted_on, reviewed_by," +
          "       reviewed_on, review_comments " +
          "FROM account_group_agreements");
      try {
        while (rs.next()) {
          AccountGroup.Id groupId = new AccountGroup.Id(rs.getInt(1));
          int claId = rs.getInt(2);
          ContributorAgreement agreement = agreements.get(claId);
          if (agreement == null) {
            continue;  // Agreement is invalid
          }

          AccountGroup group = db.accountGroups().get(groupId);
          if (group == null) {
            continue;
          }

          // TODO(cranger): build the commits such that it keeps track of the
          //                date added and who added it audit records.
          agreement.getAccepted().add(new PermissionRule(config.resolve(group)));
        }
      } finally {
        rs.close();
      }
    } finally {
      stmt.close();
    }
  }
}
