// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.server.git;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.ContributorAgreement;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.extensions.events.GitReferenceUpdated;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.errors.IncorrectObjectTypeException;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.junit.LocalDiskRepositoryTestCase;
import org.eclipse.jgit.junit.TestRepository;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevObject;
import org.eclipse.jgit.util.RawParseUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.Collections;

public class ProjectConfigTest extends LocalDiskRepositoryTestCase {
  private final GroupReference developers = new GroupReference(
      new AccountGroup.UUID("X"), "Developers");
  private final GroupReference staff = new GroupReference(
      new AccountGroup.UUID("Y"), "Staff");

  private Repository db;
  private TestRepository<Repository> util;

  @Override
  @Before
  public void setUp() throws Exception {
    super.setUp();
    db = createBareRepository();
    util = new TestRepository<Repository>(db);
  }

  @Test
  public void testReadConfig() throws Exception {
    RevCommit rev = util.commit(util.tree( //
        util.file("groups", util.blob(group(developers))), //
        util.file("project.config", util.blob(""//
            + "[access \"refs/heads/*\"]\n" //
            + "  exclusiveGroupPermissions = read submit create\n" //
            + "  submit = group Developers\n" //
            + "  push = group Developers\n" //
            + "  read = group Developers\n" //
            + "[accounts]\n" //
            + "  sameGroupVisibility = deny group Developers\n" //
            + "  sameGroupVisibility = block group Staff\n" //
            + "[contributor-agreement \"Individual\"]\n" //
            + "  description = A simple description\n" //
            + "  accepted = group Developers\n" //
            + "  accepted = group Staff\n" //
            + "  requireContactInformation = true\n" //
            + "  autoVerify = group Developers\n" //
            + "  agreementUrl = http://www.example.com/agree\n")) //
        ));

    ProjectConfig cfg = read(rev);
    assertEquals(2, cfg.getAccountsSection().getSameGroupVisibility().size());
    ContributorAgreement ca = cfg.getContributorAgreement("Individual");
    assertEquals("Individual", ca.getName());
    assertEquals("A simple description", ca.getDescription());
    assertEquals("http://www.example.com/agree", ca.getAgreementUrl());
    assertEquals(2, ca.getAccepted().size());
    assertEquals(developers, ca.getAccepted().get(0).getGroup());
    assertEquals("Staff", ca.getAccepted().get(1).getGroup().getName());
    assertEquals("Developers", ca.getAutoVerify().getName());
    assertTrue(ca.isRequireContactInformation());

    AccessSection section = cfg.getAccessSection("refs/heads/*");
    assertNotNull("has refs/heads/*", section);
    assertNull("no refs/*", cfg.getAccessSection("refs/*"));

    Permission create = section.getPermission(Permission.CREATE);
    Permission submit = section.getPermission(Permission.SUBMIT);
    Permission read = section.getPermission(Permission.READ);
    Permission push = section.getPermission(Permission.PUSH);

    assertTrue(create.getExclusiveGroup());
    assertTrue(submit.getExclusiveGroup());
    assertTrue(read.getExclusiveGroup());
    assertFalse(push.getExclusiveGroup());
  }

  @Test
  public void testEditConfig() throws Exception {
    RevCommit rev = util.commit(util.tree( //
        util.file("groups", util.blob(group(developers))), //
        util.file("project.config", util.blob(""//
            + "[access \"refs/heads/*\"]\n" //
            + "  exclusiveGroupPermissions = read submit\n" //
            + "  submit = group Developers\n" //
            + "  upload = group Developers\n" //
            + "  read = group Developers\n" //
            + "[accounts]\n" //
            + "  sameGroupVisibility = deny group Developers\n" //
            + "  sameGroupVisibility = block group Staff\n" //
            + "[contributor-agreement \"Individual\"]\n" //
            + "  description = A simple description\n" //
            + "  accepted = group Developers\n" //
            + "  requireContactInformation = true\n" //
            + "  autoVerify = group Developers\n" //
            + "  agreementUrl = http://www.example.com/agree\n")) //
        ));
    update(rev);

    ProjectConfig cfg = read(rev);
    AccessSection section = cfg.getAccessSection("refs/heads/*");
    cfg.getAccountsSection().setSameGroupVisibility(
        Collections.singletonList(new PermissionRule(cfg.resolve(staff))));
    Permission submit = section.getPermission(Permission.SUBMIT);
    submit.add(new PermissionRule(cfg.resolve(staff)));
    ContributorAgreement ca = cfg.getContributorAgreement("Individual");
    ca.setRequireContactInformation(false);
    ca.setAccepted(Collections.singletonList(new PermissionRule(cfg.resolve(staff))));
    ca.setAutoVerify(null);
    ca.setDescription("A new description");
    rev = commit(cfg);
    assertEquals(""//
        + "[access \"refs/heads/*\"]\n" //
        + "  exclusiveGroupPermissions = read submit\n" //
        + "  submit = group Developers\n" //
        + "\tsubmit = group Staff\n" //
        + "  upload = group Developers\n" //
        + "  read = group Developers\n"//
        + "[accounts]\n" //
        + "  sameGroupVisibility = group Staff\n" //
        + "[contributor-agreement \"Individual\"]\n" //
        + "  description = A new description\n" //
        + "  accepted = group Staff\n" //
        + "  agreementUrl = http://www.example.com/agree\n" //
        + "[project]\n"//
        + "\tstate = active\n" //
        + "[template]\n" //
        + "\tprojectNamePrefix = \n", text(rev, "project.config"));
  }

  @Test
  public void testEditConfigMissingGroupTableEntry() throws Exception {
    RevCommit rev = util.commit(util.tree( //
        util.file("groups", util.blob(group(developers))), //
        util.file("project.config", util.blob(""//
            + "[access \"refs/heads/*\"]\n" //
            + "  exclusiveGroupPermissions = read submit\n" //
            + "  submit = group People Who Can Submit\n" //
            + "  upload = group Developers\n" //
            + "  read = group Developers\n")) //
        ));
    update(rev);

    ProjectConfig cfg = read(rev);
    AccessSection section = cfg.getAccessSection("refs/heads/*");
    Permission submit = section.getPermission(Permission.SUBMIT);
    submit.add(new PermissionRule(cfg.resolve(staff)));
    rev = commit(cfg);
    assertEquals(""//
        + "[access \"refs/heads/*\"]\n" //
        + "  exclusiveGroupPermissions = read submit\n" //
        + "  submit = group People Who Can Submit\n" //
        + "\tsubmit = group Staff\n" //
        + "  upload = group Developers\n" //
        + "  read = group Developers\n"//
        + "[project]\n"//
        + "\tstate = active\n" //
        + "[template]\n" //
        + "\tprojectNamePrefix = \n", text(rev, "project.config"));
  }

  private ProjectConfig read(RevCommit rev) throws IOException,
      ConfigInvalidException {
    ProjectConfig cfg = new ProjectConfig(new Project.NameKey("test"));
    cfg.load(db, rev);
    return cfg;
  }

  private RevCommit commit(ProjectConfig cfg) throws IOException,
      MissingObjectException, IncorrectObjectTypeException {
    MetaDataUpdate md = new MetaDataUpdate(
        GitReferenceUpdated.DISABLED,
        cfg.getProject().getNameKey(),
        db);
    util.tick(5);
    util.setAuthorAndCommitter(md.getCommitBuilder());
    md.setMessage("Edit\n");
    cfg.commit(md);

    Ref ref = db.getRef(GitRepositoryManager.REF_CONFIG);
    return util.getRevWalk().parseCommit(ref.getObjectId());
  }

  private void update(RevCommit rev) throws Exception {
    RefUpdate u = db.updateRef(GitRepositoryManager.REF_CONFIG);
    u.disableRefLog();
    u.setNewObjectId(rev);
    switch (u.forceUpdate()) {
      case FAST_FORWARD:
      case FORCED:
      case NEW:
      case NO_CHANGE:
        break;
      default:
        fail("Cannot update ref for test: " + u.getResult());
    }
  }

  private String text(RevCommit rev, String path) throws Exception {
    RevObject blob = util.get(rev.getTree(), path);
    byte[] data = db.open(blob).getCachedBytes(Integer.MAX_VALUE);
    return RawParseUtils.decode(data);
  }

  private static String group(GroupReference g) {
    return g.getUUID().get() + "\t" + g.getName() + "\n";
  }
}
