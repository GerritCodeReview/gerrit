// Copyright (C) 2009 The Android Open Source Project
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

import com.google.gerrit.common.Version;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.AccountGroupName;
import com.google.gerrit.reviewdb.client.ApprovalCategory;
import com.google.gerrit.reviewdb.client.ApprovalCategoryValue;
import com.google.gerrit.reviewdb.client.CurrentSchemaVersion;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.client.Project.InheritableBoolean;
import com.google.gerrit.reviewdb.client.SystemConfig;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.GerritPersonIdent;
import com.google.gerrit.server.account.GroupUUID;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.config.SitePath;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gwtorm.jdbc.JdbcExecutor;
import com.google.gwtorm.jdbc.JdbcSchema;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

/** Creates the current database schema and populates initial code rows. */
public class SchemaCreator {
  private final @SitePath
  File site_path;

  private final GitRepositoryManager mgr;
  private final AllProjectsName allProjectsName;
  private final PersonIdent serverUser;
  private final DataSourceType dataSourceType;

  private final int versionNbr;

  private AccountGroup admin;
  private AccountGroup anonymous;
  private AccountGroup registered;
  private AccountGroup owners;

  @Inject
  public SchemaCreator(SitePaths site,
      @Current SchemaVersion version,
      GitRepositoryManager mgr,
      AllProjectsName allProjectsName,
      @GerritPersonIdent PersonIdent au,
      DataSourceType dst) {
    this(site.site_path, version, mgr, allProjectsName, au, dst);
  }

  public SchemaCreator(@SitePath File site,
      @Current SchemaVersion version,
      GitRepositoryManager gitMgr,
      AllProjectsName ap,
      @GerritPersonIdent PersonIdent au,
      DataSourceType dst) {
    site_path = site;
    mgr = gitMgr;
    allProjectsName = ap;
    serverUser = au;
    dataSourceType = dst;
    versionNbr = version.getVersionNbr();
  }

  public void create(final ReviewDb db) throws OrmException, IOException,
      ConfigInvalidException {
    final JdbcSchema jdbc = (JdbcSchema) db;
    final JdbcExecutor e = new JdbcExecutor(jdbc);
    try {
      jdbc.updateSchema(e);
    } finally {
      e.close();
    }

    final CurrentSchemaVersion sVer = CurrentSchemaVersion.create();
    sVer.versionNbr = versionNbr;
    db.schemaVersion().insert(Collections.singleton(sVer));

    final SystemConfig sConfig = initSystemConfig(db);
    initVerifiedCategory(db);
    initCodeReviewCategory(db, sConfig);

    if (mgr != null) {
      // TODO This should never be null when initializing a site.
      initWildCardProject();
    }

    dataSourceType.getIndexScript().run(db);
    dataSourceType.getNextValScript().run(db);
  }

  private AccountGroup newGroup(ReviewDb c, String name, AccountGroup.UUID uuid)
      throws OrmException {
    if (uuid == null) {
      uuid = GroupUUID.make(name, serverUser);
    }
    return new AccountGroup( //
        new AccountGroup.NameKey(name), //
        new AccountGroup.Id(c.nextAccountGroupId()), //
        uuid);
  }

  private SystemConfig initSystemConfig(final ReviewDb c) throws OrmException {
    admin = newGroup(c, "Administrators", null);
    admin.setDescription("Gerrit Site Administrators");
    admin.setType(AccountGroup.Type.INTERNAL);
    c.accountGroups().insert(Collections.singleton(admin));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(admin)));

    anonymous =
        newGroup(c, "Anonymous Users", AccountGroup.ANONYMOUS_USERS);
    anonymous.setDescription("Any user, signed-in or not");
    anonymous.setOwnerGroupUUID(admin.getGroupUUID());
    anonymous.setType(AccountGroup.Type.SYSTEM);
    c.accountGroups().insert(Collections.singleton(anonymous));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(anonymous)));

    registered =
        newGroup(c, "Registered Users", AccountGroup.REGISTERED_USERS);
    registered.setDescription("Any signed-in user");
    registered.setOwnerGroupUUID(admin.getGroupUUID());
    registered.setType(AccountGroup.Type.SYSTEM);
    c.accountGroups().insert(Collections.singleton(registered));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(registered)));

    final AccountGroup batchUsers = newGroup(c, "Non-Interactive Users", null);
    batchUsers.setDescription("Users who perform batch actions on Gerrit");
    batchUsers.setOwnerGroupUUID(admin.getGroupUUID());
    batchUsers.setType(AccountGroup.Type.INTERNAL);
    c.accountGroups().insert(Collections.singleton(batchUsers));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(batchUsers)));

    owners = newGroup(c, "Project Owners", AccountGroup.PROJECT_OWNERS);
    owners.setDescription("Any owner of the project");
    owners.setOwnerGroupUUID(admin.getGroupUUID());
    owners.setType(AccountGroup.Type.SYSTEM);
    c.accountGroups().insert(Collections.singleton(owners));
    c.accountGroupNames().insert(
        Collections.singleton(new AccountGroupName(owners)));

    final SystemConfig s = SystemConfig.create();
    try {
      s.sitePath = site_path.getCanonicalPath();
    } catch (IOException e) {
      s.sitePath = site_path.getAbsolutePath();
    }
    c.systemConfig().insert(Collections.singleton(s));
    return s;
  }

  private void initWildCardProject() throws IOException, ConfigInvalidException {
    Repository git;
    try {
      git = mgr.openRepository(allProjectsName);
    } catch (RepositoryNotFoundException notFound) {
      // A repository may be missing if this project existed only to store
      // inheritable permissions. For example 'All-Projects'.
      try {
        git = mgr.createRepository(allProjectsName);
        final RefUpdate u = git.updateRef(Constants.HEAD);
        u.link(GitRepositoryManager.REF_CONFIG);
      } catch (RepositoryNotFoundException err) {
        final String name = allProjectsName.get();
        throw new IOException("Cannot create repository " + name, err);
      }
    }
    try {
      MetaDataUpdate md =
          new MetaDataUpdate(GitReferenceUpdated.DISABLED, allProjectsName, git);
      md.getCommitBuilder().setAuthor(serverUser);
      md.getCommitBuilder().setCommitter(serverUser);

      ProjectConfig config = ProjectConfig.read(md);
      Project p = config.getProject();
      p.setDescription("Rights inherited by all other projects");
      p.setRequireChangeID(InheritableBoolean.TRUE);
      p.setUseContentMerge(InheritableBoolean.FALSE);
      p.setUseContributorAgreements(InheritableBoolean.FALSE);
      p.setUseSignedOffBy(InheritableBoolean.FALSE);

      AccessSection cap = config.getAccessSection(AccessSection.GLOBAL_CAPABILITIES, true);
      AccessSection all = config.getAccessSection(AccessSection.ALL, true);
      AccessSection heads = config.getAccessSection(AccessSection.HEADS, true);
      AccessSection meta = config.getAccessSection(GitRepositoryManager.REF_CONFIG, true);

      cap.getPermission(GlobalCapability.ADMINISTRATE_SERVER, true)
        .add(rule(config, admin));

      PermissionRule review = rule(config, registered);
      review.setRange(-1, 1);
      heads.getPermission(Permission.LABEL + "Code-Review", true).add(review);

      all.getPermission(Permission.READ, true) //
          .add(rule(config, admin));
      all.getPermission(Permission.READ, true) //
          .add(rule(config, anonymous));

      config.getAccessSection("refs/for/" + AccessSection.ALL, true) //
          .getPermission(Permission.PUSH, true) //
          .add(rule(config, registered));
      all.getPermission(Permission.FORGE_AUTHOR, true) //
          .add(rule(config, registered));

      Permission metaReadPermission = meta.getPermission(Permission.READ, true);
      metaReadPermission.setExclusiveGroup(true);
      metaReadPermission.add(rule(config, owners));

      md.setMessage("Initialized Gerrit Code Review " + Version.getVersion());
      config.commit(md);
    } finally {
      git.close();
    }
  }

  private PermissionRule rule(ProjectConfig config, AccountGroup group) {
    return new PermissionRule(config.resolve(group));
  }

  private void initVerifiedCategory(final ReviewDb c) throws OrmException {
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("VRIF"), "Verified");
    cat.setPosition((short) 0);
    cat.setAbbreviatedName("V");
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 1, "Verified"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "Fails"));
    c.approvalCategories().insert(Collections.singleton(cat));
    c.approvalCategoryValues().insert(vals);
  }

  private void initCodeReviewCategory(final ReviewDb c,
      final SystemConfig sConfig) throws OrmException {
    final ApprovalCategory cat;
    final ArrayList<ApprovalCategoryValue> vals;

    cat = new ApprovalCategory(new ApprovalCategory.Id("CRVW"), "Code Review");
    cat.setPosition((short) 1);
    cat.setAbbreviatedName("R");
    cat.setCopyMinScore(true);
    vals = new ArrayList<ApprovalCategoryValue>();
    vals.add(value(cat, 2, "Looks good to me, approved"));
    vals.add(value(cat, 1, "Looks good to me, but someone else must approve"));
    vals.add(value(cat, 0, "No score"));
    vals.add(value(cat, -1, "I would prefer that you didn't submit this"));
    vals.add(value(cat, -2, "Do not submit"));
    c.approvalCategories().insert(Collections.singleton(cat));
    c.approvalCategoryValues().insert(vals);
  }

  private static ApprovalCategoryValue value(final ApprovalCategory cat,
      final int value, final String name) {
    return new ApprovalCategoryValue(new ApprovalCategoryValue.Id(cat.getId(),
        (short) value), name);
  }
}
