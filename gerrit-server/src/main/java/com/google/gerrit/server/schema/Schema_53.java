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

import static com.google.gerrit.common.data.Permission.CREATE;
import static com.google.gerrit.common.data.Permission.FORGE_AUTHOR;
import static com.google.gerrit.common.data.Permission.FORGE_COMMITTER;
import static com.google.gerrit.common.data.Permission.FORGE_SERVER;
import static com.google.gerrit.common.data.Permission.LABEL;
import static com.google.gerrit.common.data.Permission.OWNER;
import static com.google.gerrit.common.data.Permission.PUSH;
import static com.google.gerrit.common.data.Permission.PUSH_MERGE;
import static com.google.gerrit.common.data.Permission.PUSH_TAG;
import static com.google.gerrit.common.data.Permission.READ;
import static com.google.gerrit.common.data.Permission.SUBMIT;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.ApprovalCategory;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.reviewdb.SystemConfig;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.NoReplication;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.jdbc.JdbcSchema;
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
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

class Schema_53 extends SchemaVersion {
  private final GitRepositoryManager mgr;
  private final PersonIdent serverUser;

  private SystemConfig systemConfig;
  private Map<AccountGroup.Id, GroupReference> groupMap;
  private Map<ApprovalCategory.Id, ApprovalCategory> categoryMap;
  private GroupReference projectOwners;

  private Map<Project.NameKey, Project.NameKey> parentsByProject;
  private Map<Project.NameKey, List<OldRefRight>> rightsByProject;

  private final String OLD_SUBMIT = "SUBM";
  private final String OLD_READ = "READ";
  private final String OLD_OWN = "OWN";
  private final String OLD_PUSH_TAG = "pTAG";
  private final String OLD_PUSH_HEAD = "pHD";
  private final String OLD_FORGE_IDENTITY = "FORG";

  @Inject
  Schema_53(Provider<Schema_52> prior, GitRepositoryManager mgr,
      @GerritPersonIdent PersonIdent serverUser) {
    super(prior);
    this.mgr = mgr;
    this.serverUser = serverUser;
  }

  @Override
  protected void migrateData(ReviewDb db, UpdateUI ui) throws OrmException,
      SQLException {
    systemConfig = db.systemConfig().get(new SystemConfig.Key());
    categoryMap = db.approvalCategories().toMap(db.approvalCategories().all());

    assignGroupUUIDs(db);
    readOldRefRights(db);
    readProjectParents(db);
    exportProjectConfig(db);

    deleteActionCategories(db);
  }

  private void deleteActionCategories(ReviewDb db) throws OrmException {
    List<ApprovalCategory> delete = new ArrayList<ApprovalCategory>();
    for (ApprovalCategory category : categoryMap.values()) {
      if (category.getPosition() < 0) {
        delete.add(category);
      }
    }
    db.approvalCategories().delete(delete);
  }

  private void assignGroupUUIDs(ReviewDb db) throws OrmException {
    groupMap = new HashMap<AccountGroup.Id, GroupReference>();
    List<AccountGroup> groups = db.accountGroups().all().toList();
    for (AccountGroup g : groups) {
      if (g.getId().equals(systemConfig.ownerGroupId)) {
        g.setGroupUUID(AccountGroup.PROJECT_OWNERS);
        projectOwners = GroupReference.forGroup(g);

      } else if (g.getId().equals(systemConfig.anonymousGroupId)) {
        g.setGroupUUID(AccountGroup.ANONYMOUS_USERS);

      } else if (g.getId().equals(systemConfig.registeredGroupId)) {
        g.setGroupUUID(AccountGroup.REGISTERED_USERS);

      } else {
        g.setGroupUUID(GroupUUID.make(g.getName(), serverUser));
      }
      groupMap.put(g.getId(), GroupReference.forGroup(g));
    }
    db.accountGroups().update(groups);

    systemConfig.adminGroupUUID = toUUID(systemConfig.adminGroupId);
    systemConfig.batchUsersGroupUUID = toUUID(systemConfig.batchUsersGroupId);
    db.systemConfig().update(Collections.singleton(systemConfig));
  }

  private AccountGroup.UUID toUUID(AccountGroup.Id id) {
    return groupMap.get(id).getUUID();
  }

  private void exportProjectConfig(ReviewDb db) throws OrmException,
      SQLException {
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    ResultSet rs = stmt.executeQuery("SELECT * FROM projects ORDER BY name");
    while (rs.next()) {
      final String name = rs.getString("name");
      final Project.NameKey nameKey = new Project.NameKey(name);

      Repository git;
      try {
        git = mgr.openRepository(nameKey);
      } catch (RepositoryNotFoundException notFound) {
        // A repository may be missing if this project existed only to store
        // inheritable permissions. For example '-- All Projects --'.
        try {
          git = mgr.createRepository(nameKey);
        } catch (RepositoryNotFoundException err) {
          throw new OrmException("Cannot create repository " + name, err);
        }
      }
      try {
        MetaDataUpdate md =
            new MetaDataUpdate(new NoReplication(), nameKey, git);
        md.getCommitBuilder().setAuthor(serverUser);
        md.getCommitBuilder().setCommitter(serverUser);

        ProjectConfig config = ProjectConfig.read(md);
        loadProject(rs, config.getProject());
        config.getAccessSections().clear();
        convertRights(config);

        // Grant out read and push on the config branch by default.
        //
        if (config.getProject().getNameKey().equals(systemConfig.wildProjectName)) {
          AccessSection meta = config.getAccessSection(GitRepositoryManager.REF_CONFIG, true);
          Permission read = meta.getPermission(READ, true);
          Permission push = meta.getPermission(PUSH, true);
          PermissionRule rule;

          rule = read.getRule(config.resolve(projectOwners), true);
          rule.setDeny(false);
          rule.setForce(false);

          rule = push.getRule(config.resolve(projectOwners), true);
          rule.setDeny(false);
          rule.setForce(false);
        }

        md.setMessage("Import project configuration from SQL\n");
        if (!config.commit(md)) {
          throw new OrmException("Cannot export project " + name);
        }
      } catch (ConfigInvalidException err) {
        throw new OrmException("Cannot read project " + name, err);
      } catch (IOException err) {
        throw new OrmException("Cannot export project " + name, err);
      } finally {
        git.close();
      }
    }
    rs.close();
    stmt.close();
  }

  private void loadProject(ResultSet rs, Project project) throws SQLException,
      OrmException {
    project.setDescription(rs.getString("description"));
    project.setUseContributorAgreements("Y".equals(rs
        .getString("use_contributor_agreements")));

    switch (rs.getString("submit_type").charAt(0)) {
      case 'F':
        project.setSubmitType(Project.SubmitType.FAST_FORWARD_ONLY);
        break;
      case 'M':
        project.setSubmitType(Project.SubmitType.MERGE_IF_NECESSARY);
        break;
      case 'A':
        project.setSubmitType(Project.SubmitType.MERGE_ALWAYS);
        break;
      case 'C':
        project.setSubmitType(Project.SubmitType.CHERRY_PICK);
        break;
      default:
        throw new OrmException("Unsupported submit_type="
            + rs.getString("submit_type") + " on project " + project.getName());
    }

    project.setUseSignedOffBy("Y".equals(rs.getString("use_signed_off_by")));
    project.setRequireChangeID("Y".equals(rs.getString("require_change_id")));
    project.setUseContentMerge("Y".equals(rs.getString("use_content_merge")));
    project.setParentName(rs.getString("parent_name"));
  }

  private void readOldRefRights(ReviewDb db) throws SQLException {
    rightsByProject = new HashMap<Project.NameKey, List<OldRefRight>>();

    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    ResultSet rs = stmt.executeQuery("SELECT * FROM ref_rights");
    while (rs.next()) {
      OldRefRight right = new OldRefRight(rs);
      if (right.group == null || right.category == null) {
        continue;
      }

      List<OldRefRight> list;

      list = rightsByProject.get(right.project);
      if (list == null) {
        list = new ArrayList<OldRefRight>();
        rightsByProject.put(right.project, list);
      }
      list.add(right);
    }
    rs.close();
    stmt.close();
  }

  private void readProjectParents(ReviewDb db) throws SQLException {
    parentsByProject = new HashMap<Project.NameKey, Project.NameKey>();
    Statement stmt = ((JdbcSchema) db).getConnection().createStatement();
    ResultSet rs = stmt.executeQuery("SELECT * FROM projects");
    while (rs.next()) {
      String name = rs.getString("name");
      String parent_name = rs.getString("parent_name");
      if (parent_name == null) {
        parent_name = systemConfig.wildProjectName.get();
      }
      parentsByProject.put(new Project.NameKey(name), //
          new Project.NameKey(parent_name));
    }
    rs.close();
    stmt.close();
  }

  private void convertRights(ProjectConfig config) {
    List<OldRefRight> myRights =
        rightsByProject.get(config.getProject().getNameKey());
    if (myRights == null) {
      return;
    }

    for (OldRefRight old : myRights) {
      AccessSection section = config.getAccessSection(old.ref_pattern, true);
      GroupReference group = config.resolve(old.group);

      if (OLD_SUBMIT.equals(old.category)) {
        PermissionRule submit = rule(group);
        submit.setDeny(old.max_value <= 0);
        add(section, SUBMIT, old.exclusive, submit);

      } else if (OLD_READ.equals(old.category)) {
        if (old.exclusive) {
          section.getPermission(READ, true).setExclusiveGroup(true);
          newChangePermission(config, old.ref_pattern).setExclusiveGroup(true);
        }

        PermissionRule read = rule(group);
        read.setDeny(old.max_value <= 0);
        add(section, READ, old.exclusive, read);

        if (3 <= old.max_value) {
          newMergePermission(config, old.ref_pattern).add(rule(group));
        } else if (3 <= inheritedMax(config, old)) {
          newMergePermission(config, old.ref_pattern).add(deny(group));
        }

        if (2 <= old.max_value) {
          newChangePermission(config, old.ref_pattern).add(rule(group));
        } else if (2 <= inheritedMax(config, old)) {
          newChangePermission(config, old.ref_pattern).add(deny(group));
        }

      } else if (OLD_OWN.equals(old.category)) {
        add(section, OWNER, false, rule(group));

      } else if (OLD_PUSH_TAG.equals(old.category)) {
        // TODO Handle values +1, +2?
        PermissionRule push = rule(group);
        push.setDeny(old.max_value <= 0);
        add(section, PUSH_TAG, old.exclusive, push);

      } else if (OLD_PUSH_HEAD.equals(old.category)) {
        if (old.exclusive) {
          section.getPermission(PUSH, true).setExclusiveGroup(true);
          section.getPermission(CREATE, true).setExclusiveGroup(true);
        }

        PermissionRule push = rule(group);
        push.setDeny(old.max_value <= 0);
        push.setForce(3 <= old.max_value);
        add(section, PUSH, old.exclusive, push);

        if (2 <= old.max_value) {
          add(section, CREATE, old.exclusive, rule(group));
        } else if (2 <= inheritedMax(config, old)) {
          add(section, CREATE, old.exclusive, deny(group));
        }

      } else if (OLD_FORGE_IDENTITY.equals(old.category)) {
        if (old.exclusive) {
          section.getPermission(FORGE_AUTHOR, true).setExclusiveGroup(true);
          section.getPermission(FORGE_COMMITTER, true).setExclusiveGroup(true);
          section.getPermission(FORGE_SERVER, true).setExclusiveGroup(true);
        }

        if (1 <= old.max_value) {
          add(section, FORGE_AUTHOR, old.exclusive, rule(group));
        }

        if (2 <= old.max_value) {
          add(section, FORGE_COMMITTER, old.exclusive, rule(group));
        } else if (2 <= inheritedMax(config, old)) {
          add(section, FORGE_COMMITTER, old.exclusive, deny(group));
        }

        if (3 <= old.max_value) {
          add(section, FORGE_SERVER, old.exclusive, rule(group));
        } else if (3 <= inheritedMax(config, old)) {
          add(section, FORGE_SERVER, old.exclusive, deny(group));
        }

      } else {
        PermissionRule rule = rule(group);
        rule.setRange(old.min_value, old.max_value);
        add(section, LABEL + varNameOf(old.category), old.exclusive, rule);
      }
    }
  }

  private static Permission newChangePermission(ProjectConfig config,
      String name) {
    if (name.startsWith(AccessSection.REGEX_PREFIX)) {
      name = AccessSection.REGEX_PREFIX
          + "refs/for/"
          + name.substring(AccessSection.REGEX_PREFIX.length());
    } else {
      name = "refs/for/" + name;
    }
    return config.getAccessSection(name, true).getPermission(PUSH, true);
  }

  private static Permission newMergePermission(ProjectConfig config,
      String name) {
    if (name.startsWith(AccessSection.REGEX_PREFIX)) {
      name = AccessSection.REGEX_PREFIX
          + "refs/for/"
          + name.substring(AccessSection.REGEX_PREFIX.length());
    } else {
      name = "refs/for/" + name;
    }
    return config.getAccessSection(name, true).getPermission(PUSH_MERGE, true);
  }

  private static PermissionRule rule(GroupReference group) {
    return new PermissionRule(group);
  }

  private static PermissionRule deny(GroupReference group) {
    PermissionRule rule = rule(group);
    rule.setDeny(true);
    return rule;
  }

  private int inheritedMax(ProjectConfig config, OldRefRight old) {
    int max = 0;

    String ref = old.ref_pattern;
    String category = old.category;
    AccountGroup.UUID group = old.group.getUUID();

    Project.NameKey project = config.getProject().getParent();
    if (project == null) {
      project = systemConfig.wildProjectName;
    }
    do {
      List<OldRefRight> rights = rightsByProject.get(project);
      if (rights != null) {
        for (OldRefRight r : rights) {
          if (r.ref_pattern.equals(ref) //
              && r.group.getUUID().equals(group) //
              && r.category.equals(category)) {
            max = Math.max(max, r.max_value);
            break;
          }
        }
      }
      project = parentsByProject.get(project);
    } while (!project.equals(systemConfig.wildProjectName));

    return max;
  }

  private String varNameOf(String id) {
    ApprovalCategory category = categoryMap.get(new ApprovalCategory.Id(id));
    if (category == null) {
      category = new ApprovalCategory(new ApprovalCategory.Id(id), id);
    }
    return category.getLabelName();
  }

  private static void add(AccessSection section, String name,
      boolean exclusive, PermissionRule rule) {
    Permission p = section.getPermission(name, true);
    p.setExclusiveGroup(exclusive);
    p.add(rule);
  }

  private class OldRefRight {
    final int min_value;
    final int max_value;
    final String ref_pattern;
    final boolean exclusive;
    final GroupReference group;
    final String category;
    final Project.NameKey project;

    OldRefRight(ResultSet rs) throws SQLException {
      min_value = rs.getInt("min_value");
      max_value = rs.getInt("max_value");
      project = new Project.NameKey(rs.getString("project_name"));

      String r = rs.getString("ref_pattern");
      exclusive = r.startsWith("-");
      if (exclusive) {
        r = r.substring(1);
      }
      ref_pattern = r;

      category = rs.getString("category_id");
      group = groupMap.get(new AccountGroup.Id(rs.getInt("group_id")));
    }
  }
}
