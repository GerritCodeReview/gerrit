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

import static com.google.common.base.Strings.isNullOrEmpty;

import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
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
import java.util.List;
import java.util.Map;

import javax.naming.NamingException;
import javax.naming.ldap.LdapName;

public class Schema_68 extends SchemaVersion {
  private final GitRepositoryManager mgr;
  private final PersonIdent serverUser;

  @Inject
  Schema_68(Provider<Schema_67> prior,
      GitRepositoryManager mgr,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.mgr = mgr;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui)
      throws OrmException, SQLException {

    // Find all groups that have an LDAP type.
    Map<AccountGroup.UUID, GroupReference> ldapUUIDMap = Maps.newHashMap();
    List<AccountGroup.Id> toDelete = Lists.newArrayList();
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    try {
      ResultSet rs = stmt.executeQuery(
          "SELECT group_id, group_uuid, external_name FROM account_groups"
          + " WHERE group_type ='LDAP'");
      try {
        Map<Integer, ContributorAgreement> agreements = Maps.newHashMap();
        while (rs.next()) {
          AccountGroup.Id groupId = new AccountGroup.Id(rs.getInt(1));
          toDelete.add(groupId);

          AccountGroup.UUID groupUUID = new AccountGroup.UUID(rs.getString(2));
          String dn = rs.getString(3);
          GroupReference ref = groupReference(dn);
          ldapUUIDMap.put(groupUUID, ref);
        }
      } catch (NamingException e) {
        throw new RuntimeException(e);
      } finally {
        rs.close();
      }
    } finally {
      stmt.close();
    }

    if (toDelete.isEmpty()) {
      return; // No ldap groups. Nothing to do.
    }

    ui.message("Update LDAP groups to be GroupReferences.");

    // Update the groupOwnerUUID for LDAP groups to point to the new UUID.
    List<AccountGroup> toUpdate = Lists.newArrayList();
    com.google.gwtorm.server.ResultSet<AccountGroup> rs =
        db.accountGroups().all();
    try {
      for (AccountGroup g : rs) {
        if (ldapUUIDMap.containsKey(g.getGroupUUID())) {
          continue; // Ignore the LDAP groups
        }

        GroupReference ref = ldapUUIDMap.get(g.getOwnerGroupUUID());
        if (ref != null) {
          g.setOwnerGroupUUID(ref.getUUID());
          toUpdate.add(g);
        }
      }
    } finally {
      rs.close();
    }
    db.accountGroups().update(toUpdate);

    // Update project.config group references to use the new LDAP GroupReference
    for (Project.NameKey name : mgr.list()) {
      Repository git;
      try {
        git = mgr.openRepository(name);
      } catch (RepositoryNotFoundException e) {
        throw new OrmException(e);
      }

      try {
        MetaDataUpdate md =
            new MetaDataUpdate(new NoReplication(), name, git);
        md.getCommitBuilder().setAuthor(serverUser);
        md.getCommitBuilder().setCommitter(serverUser);

        ProjectConfig config = ProjectConfig.read(md);

        boolean updated = false;
        for (Map.Entry<AccountGroup.UUID, GroupReference> entry: ldapUUIDMap.entrySet()) {
          GroupReference ref = config.getGroup(entry.getKey());
          if (ref != null) {
            updated = true;
            ref.setName(entry.getValue().getName());
            ref.setUUID(entry.getValue().getUUID());
            config.resolve(ref);
          }
        }

        if (!updated) {
          continue;
        }

        md.setMessage("Upgrade to Gerrit Code Review schema 68\n");
        if (!config.commit(md)) {
          throw new OrmException("Cannot update " + name);
        }
      } catch (IOException e) {
        throw new OrmException(e);
      } catch (ConfigInvalidException e) {
        throw new OrmException(e);
      } finally {
        git.close();
      }
    }

    // Delete existing LDAP groups
    db.accountGroups().deleteKeys(toDelete);
  }

  private static GroupReference groupReference(String dn)
      throws NamingException {
    Preconditions.checkState(!isNullOrEmpty(dn), "Invalid LDAP dn: %s", dn);
    LdapName name = new LdapName(dn);
    Preconditions.checkState(!name.isEmpty(), "Invalid LDAP dn: %s", dn);
    String cn = name.get(name.size() - 1);
    int index = cn.indexOf('=');
    if (index >= 0) {
      cn = cn.substring(index + 1);
    }
    return new GroupReference(new AccountGroup.UUID("ldap:" + dn), "ldap/" + cn);
  }
}
