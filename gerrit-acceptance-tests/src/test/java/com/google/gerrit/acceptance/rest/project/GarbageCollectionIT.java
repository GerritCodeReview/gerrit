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

package com.google.gerrit.acceptance.rest.project;

import static com.google.gerrit.acceptance.git.GitUtil.createProject;
import static org.junit.Assert.assertEquals;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.GcAssert;
import com.google.gerrit.acceptance.RestResponse;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GlobalCapability;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.config.AllProjectsName;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class GarbageCollectionIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private AllProjectsName allProjects;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  @Inject
  private GroupCache groupCache;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private GcAssert gcAssert;

  private TestAccount admin;
  private TestAccount projectOwner;
  private RestSession session;
  private RestSession sessionProjectOwner;
  private Project.NameKey project1;
  private Project.NameKey project2;

  @Before
  public void setUp() throws Exception {
    admin = accounts.admin();
    projectOwner = accounts.projectOwner();

    SshSession sshSession = new SshSession(server, admin);

    project1 = new Project.NameKey("p1");
    createProject(sshSession, project1.get());

    project2 = new Project.NameKey("p2");
    createProject(sshSession, project2.get());

    session = new RestSession(server, admin);
    sessionProjectOwner = new RestSession(server, projectOwner);
  }

  @Test
  public void testGcNonExistingProject_NotFound() throws IOException {
    assertEquals(HttpStatus.SC_NOT_FOUND, POST("/projects/non-existing/gc"));
  }

  @Test
  public void testGcNotAllowed_Forbidden() throws IOException, OrmException, JSchException {
    assertEquals(HttpStatus.SC_FORBIDDEN,
        new RestSession(server, accounts.create("user", "user@example.com", "User"))
            .post("/projects/" + allProjects.get() + "/gc").getStatusCode());
  }

  @Test
  @UseLocalDisk
  public void testGcOneProject() throws JSchException, IOException {
    assertEquals(HttpStatus.SC_OK, POST("/projects/" + allProjects.get() + "/gc"));
    gcAssert.assertHasPackFile(allProjects);
    gcAssert.assertHasNoPackFile(project1, project2);
  }

  @Test
  public void testGcProjectOwner() throws JSchException, IOException,
      ConfigInvalidException {
    String endPoint = "/projects/" + project2.get() + "/gc";
    assertEquals(HttpStatus.SC_FORBIDDEN, postProjectOwner(endPoint));
    grantGC();
    assertEquals(HttpStatus.SC_FORBIDDEN, postProjectOwner(endPoint));
    grantOwner();
    assertEquals(HttpStatus.SC_OK, postProjectOwner(endPoint));
  }

  private int POST(String endPoint) throws IOException {
    RestResponse r = session.post(endPoint);
    r.consume();
    return r.getStatusCode();
  }

  private int postProjectOwner(String endPoint) throws IOException {
    RestResponse r = sessionProjectOwner.post(endPoint);
    r.consume();
    return r.getStatusCode();
  }

  private void grantOwner() throws IOException, ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(project2);
    md.setMessage(String.format("Grant %s", Permission.OWNER));
    ProjectConfig config = ProjectConfig.read(md);
    AccessSection s = config.getAccessSection("refs/*", true);
    Permission p = s.getPermission(Permission.OWNER, true);
    AccountGroup adminGroup = groupCache.get(new AccountGroup.NameKey("Registered Users"));
    PermissionRule rule = new PermissionRule(config.resolve(adminGroup));
    p.add(rule);
    config.commit(md);
    projectCache.evict(config.getProject());
  }

  private void grantGC() throws IOException, ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(allProjects);
    md.setMessage(String.format("Grant %s", GlobalCapability.RUN_GC));
    ProjectConfig config = ProjectConfig.read(md);
    AccessSection s = config.getAccessSection(
        AccessSection.GLOBAL_CAPABILITIES);
    Permission p = s.getPermission(GlobalCapability.RUN_GC, true);
    AccountGroup projectOwnersGroup = groupCache.get(
        new AccountGroup.NameKey("Project Owners"));
    PermissionRule rule = new PermissionRule(
        config.resolve(projectOwnersGroup));
    p.add(rule);
    config.commit(md);
    projectCache.evict(config.getProject());
  }
}
