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
import com.google.gerrit.server.config.AnonymousCowardName;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.NoReplication;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.git.VersionedMetaData.BatchMetaDataUpdate;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.CommitBuilder;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.util.SystemReader;

import java.io.IOException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

public class Schema_65 extends SchemaVersion {
  private final AllProjectsName allProjects;
  private final GitRepositoryManager mgr;
  private final PersonIdent serverUser;
  private final @AnonymousCowardName String anonymousCowardName;

  @Inject
  Schema_65(Provider<Schema_64> prior,
      AllProjectsName allProjects,
      GitRepositoryManager mgr,
      @GerritPersonIdent PersonIdent serverUser,
      @AnonymousCowardName String anonymousCowardName) {
    super(prior);
    this.allProjects = allProjects;
    this.mgr = mgr;
    this.serverUser = serverUser;
    this.anonymousCowardName = anonymousCowardName;
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
      ProjectConfig config = ProjectConfig.read(md);
      Map<Integer, ContributorAgreement> agreements = getAgreementToAdd(db, config);
      if (agreements.isEmpty()) {
        return;
      }
      ui.message("Moved contributor agreements to project.config");

      // Create the group for individuals.
      createGroupForIndividuals(db, config, agreements);

      // Scan AccountAgreement
      long minTime = addAccountAgreements(db, agreements);

      ProjectConfig base = ProjectConfig.read(md, null);
      for (ContributorAgreement agreement : agreements.values()) {
        base.replace(agreement);
      }

      BatchMetaDataUpdate batch = base.openUpdate(md);
      try {
        // Scan AccountGroupAgreement
        List<AccountGroupAgreement> groupAgreements =
            getAccountGroupAgreements(db, agreements);

        // Find the earliest change
        for (AccountGroupAgreement aga : groupAgreements) {
          minTime = Math.min(minTime, aga.getTime());
        }
        minTime -= 60 * 1000; // 1 Minute

        CommitBuilder commit = new CommitBuilder();
        commit.setAuthor(new PersonIdent(serverUser, new Date(minTime)));
        commit.setCommitter(new PersonIdent(serverUser, new Date(minTime)));
        commit.setMessage("Add the ContributorAgreements for upgrage to Gerrit Code Review schema 65\n");
        batch.write(commit);

        for (AccountGroupAgreement aga : groupAgreements) {
          AccountGroup group = db.accountGroups().get(aga.groupId);
          if (group == null) {
            continue;
          }

          ContributorAgreement agreement = agreements.get(aga.claId);
          agreement.getAccepted().add(new PermissionRule(config.resolve(group)));
          base.replace(agreement);

          PersonIdent ident = null;
          if (aga.reviewedBy != null) {
            Account ua = db.accounts().get(aga.reviewedBy);
            if (ua != null) {
              String name = ua.getFullName();
              String email = ua.getPreferredEmail();

              if (email == null || email.isEmpty()) {
                // No preferred email is configured. Use a generic identity so we
                // don't leak an address the user may have given us, but doesn't
                // necessarily want to publish through Git records.
                //
                String user = ua.getUserName();
                if (user == null || user.isEmpty()) {
                  user = "account-" + ua.getId().toString();
                }

                String host = SystemReader.getInstance().getHostname();
                email = user + "@" + host;
              }

              if (name == null || name.isEmpty()) {
                final int at = email.indexOf('@');
                if (0 < at) {
                  name = email.substring(0, at);
                } else {
                  name = anonymousCowardName;
                }
              }

              ident = new PersonIdent(name, email, new Date(aga.getTime()), TimeZone.getDefault());
            }
          }
          if (ident == null) {
            ident = new PersonIdent(serverUser, new Date(aga.getTime()));
          }

          // Build the commits such that it keeps track of the date added and
          // who added it.
          commit = new CommitBuilder();
          commit.setAuthor(ident);
          commit.setCommitter(ident);
          commit.setMessage(String.format("Add the accepted group \"%s\" to " +
          		"contributor agreement\n\"%s\" for upgrage to Gerrit Code " +
          		"Review schema 65\n", group.getName(), agreement.getName()));
          batch.write(commit);
        }

        // Merge the agreements with the other data in project.config.
        commit = new CommitBuilder();
        commit.setAuthor(serverUser);
        commit.setCommitter(serverUser);
        commit.setMessage("Upgrade to Gerrit Code Review schema 65\n");
        commit.addParentId(config.getRevision());
        batch.write(config, commit);

        // Save the the final metadata.
        if (!batch.commitAt(config.getRevision())) {
          throw new OrmException("Cannot update " + allProjects);
        }
      } finally {
        batch.close();
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

  private List<AccountGroup.Id> getAdministrateServerGroups(
      ReviewDb db, ProjectConfig cfg) throws OrmException {
    List<PermissionRule> rules = cfg.getAccessSection(AccessSection.GLOBAL_CAPABILITIES)
       .getPermission(GlobalCapability.ADMINISTRATE_SERVER)
       .getRules();

    List<AccountGroup.Id> groups =
        Lists.newArrayListWithExpectedSize(rules.size());
    for (PermissionRule rule : rules) {
      if (rule.getAction() == Action.ALLOW) {
        AccountGroup group = db.accountGroups()
            .byUUID(rule.getGroup().getUUID())
            .toList()
            .get(0);
        if (group != null) {
          groups.add(group.getId());
        }
      }
    }
    if (groups.isEmpty()) {
      throw new IllegalStateException("no administrator group found");
    }

    return groups;
  }

  private void createGroupForIndividuals(ReviewDb db, ProjectConfig config,
      Map<Integer, ContributorAgreement> agreements) throws OrmException {
    List<AccountGroup.Id> adminGroupIds = getAdministrateServerGroups(db, config);
    for (ContributorAgreement agreement : agreements.values()) {
      String name = "CLA Accepted - " + agreement.getName();
      AccountGroupName agn =
          db.accountGroupNames().get(new AccountGroup.NameKey(name));
      AccountGroup ag;
      if (agn != null) {
        ag = db.accountGroups().get(agn.getId());
        if (ag == null) {
          throw new IllegalStateException(
              "account group name exists but account group does not: " + name);
        }

        if (!adminGroupIds.contains(ag.getOwnerGroupId())) {
          throw new IllegalStateException(
              "individual group exists with non admin owner group: " + name);
        }
      } else {
        ag = createGroup(db, name, adminGroupIds.get(0),
            String.format("Users who have accepted the %s CLA", agreement.getName()));
      }
      GroupReference group = config.resolve(ag);
      agreement.setAccepted(Lists.newArrayList(new PermissionRule(group)));
      if (agreement.getAutoVerify() != null) {
        agreement.setAutoVerify(group);
      }
    }
  }

  private long addAccountAgreements(
      ReviewDb db, Map<Integer, ContributorAgreement> agreements)
          throws SQLException, OrmException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      ResultSet rs = stmt.executeQuery(
          "SELECT account_id, cla_id, accepted_on, reviewed_by," +
          "       reviewed_on, review_comments " +
          "FROM account_agreements WHERE status = 'V'");
      try {
        long minTime = System.currentTimeMillis();
        while (rs.next()) {
          Account.Id accountId = new Account.Id(rs.getInt(1));
          Account.Id reviewerId = new Account.Id(rs.getInt(4));
          if (rs.wasNull()) {
            reviewerId = accountId;
          }

          int claId = rs.getInt(2);
          ContributorAgreement agreement = agreements.get(claId);
          if (agreement == null) {
            continue;  // Agreement is invalid
          }

          Timestamp acceptedOn = rs.getTimestamp(3);
          minTime = Math.min(minTime, acceptedOn.getTime());

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
            db.accountGroupMembersAudit().insert(
                Collections.singleton(
                    new AccountGroupMemberAudit(m, reviewerId, acceptedOn)));
            db.accountGroupMembers().insert(Collections.singleton(m));
            // No need to evict the AccountCache, since it is not used in
            // this migration.
          }
        }
        return minTime;
      } finally {
        rs.close();
      }
    } finally {
      stmt.close();
    }
  }

  private static class AccountGroupAgreement {
    private AccountGroup.Id groupId;
    private int claId;
    private Timestamp acceptedOn;
    private Account.Id reviewedBy;
    private Timestamp reviewedOn;

    private long getTime() {
      return (reviewedOn == null) ? acceptedOn.getTime() : reviewedOn.getTime();
    }
  }

  private List<AccountGroupAgreement> getAccountGroupAgreements(
      ReviewDb db, Map<Integer, ContributorAgreement> agreements)
          throws SQLException {

    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      ResultSet rs = stmt.executeQuery(
          "SELECT group_id, cla_id, accepted_on, reviewed_by, reviewed_on " +
          "FROM account_group_agreements");
      try {
        List<AccountGroupAgreement> groupAgreements = Lists.newArrayList();
        while (rs.next()) {
          AccountGroupAgreement a = new AccountGroupAgreement();
          a.groupId = new AccountGroup.Id(rs.getInt(1));
          a.claId = rs.getInt(2);
          if (!agreements.containsKey(a.claId)) {
            continue; // Agreement is invalid
          }
          a.acceptedOn = rs.getTimestamp(3);
          a.reviewedBy = new Account.Id(rs.getInt(4));
          if (rs.wasNull()) {
            a.reviewedBy = null;
          }

          a.reviewedOn = rs.getTimestamp(5);
          if (rs.wasNull()) {
            a.reviewedOn = null;
          }

          groupAgreements.add(a);
        }
        return groupAgreements;
      } finally {
        rs.close();
      }
    } finally {
      stmt.close();
    }
  }
}
