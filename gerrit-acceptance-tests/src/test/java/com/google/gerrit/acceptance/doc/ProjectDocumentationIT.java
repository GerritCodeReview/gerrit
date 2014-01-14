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

package com.google.gerrit.acceptance.doc;

import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.createProject;
import static com.google.gerrit.acceptance.GitUtil.initSsh;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.AccountCreator;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.HttpResponse;
import com.google.gerrit.acceptance.HttpSession;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.RestSession;
import com.google.gerrit.acceptance.SshSession;
import com.google.gerrit.acceptance.TestAccount;
import com.google.gerrit.acceptance.UseLocalDisk;
import com.google.gerrit.common.data.AccessSection;
import com.google.gerrit.common.data.GroupReference;
import com.google.gerrit.common.data.Permission;
import com.google.gerrit.common.data.PermissionRule;
import com.google.gerrit.extensions.restapi.Url;
import com.google.gerrit.reviewdb.client.AccountGroup;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.account.GroupCache;
import com.google.gerrit.server.git.MetaDataUpdate;
import com.google.gerrit.server.git.ProjectConfig;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class ProjectDocumentationIT extends AbstractDaemonTest {

  @Inject
  private AccountCreator accounts;

  @Inject
  private SchemaFactory<ReviewDb> reviewDbProvider;

  @Inject
  private PushOneCommit.Factory pushFactory;

  @Inject
  private MetaDataUpdate.Server metaDataUpdateFactory;

  @Inject
  private ProjectCache projectCache;

  @Inject
  private GroupCache groupCache;

  private TestAccount admin;
  private HttpSession httpSession;
  private SshSession sshSession;
  private Project.NameKey project;
  private Git git;
  private ReviewDb db;


  @Before
  public void setUp() throws Exception {
    admin = accounts.admin();
    httpSession = new HttpSession(server, admin);
    sshSession = new SshSession(server, admin);
    initSsh(admin);

    project = new Project.NameKey("p");
    createProject(sshSession, project.get());
    git = cloneProject(sshSession.getUrl() + "/" + project.get());

    db = reviewDbProvider.open();
  }

  @After
  public void cleanup() {
    sshSession.close();
    db.close();
  }

  @Test
  @GerritConfig(name="site.enableProjectDocs", value="true")
  public void noDocs_NotFound() throws IOException {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        httpSession.get("/doc/" + project.get()).getStatusCode());
  }

  @Test
  @GerritConfig(name="site.enableProjectDocs", value="true")
  public void getDoc() throws IOException, GitAPIException {
    pushFactory.create(db, admin.getIdent(), "Add readme", "README.md", "read me")
        .to(git, "refs/heads/master");

    HttpResponse r = httpSession.get("/doc/" + project.get() + "/README.html");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    String content = r.getEntityContent();
    assertTrue(content.startsWith("<html>"));
    assertTrue(content.contains("read me"));
    assertTrue(content.endsWith("</html>"));
  }

  @Test
  public void projectDocsDisabled_NotFound() throws IOException, GitAPIException {
    pushFactory.create(db, admin.getIdent(), "Add readme", "README.md", "read me")
        .to(git, "refs/heads/master");
    assertEquals(HttpStatus.SC_NOT_FOUND,
        httpSession.get("/doc/" + project.get()).getStatusCode());
  }

  @Test
  @GerritConfig(name="site.enableProjectDocs", value="true")
  public void nonVisibleProject_NotFound() throws IOException,
      GitAPIException, OrmException, JSchException, ConfigInvalidException {
    pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "content")
        .to(git, "refs/heads/master");
    String users = "Users";
    new RestSession(server, admin).put("/groups/" + users);
    TestAccount user = accounts.create("user", users);
    blockReadFor(users);
    HttpResponse r = new HttpSession(server, user)
        .get("/a/doc/" + project.get() + "/test.html");
    assertEquals(HttpStatus.SC_NOT_FOUND, r.getStatusCode());
  }

  private void blockReadFor(String groupName) throws IOException,
      ConfigInvalidException {
    MetaDataUpdate md = metaDataUpdateFactory.create(project);
    md.setMessage(String.format("Block %s for %s", Permission.READ, groupName));
    ProjectConfig config = ProjectConfig.read(md);
    AccessSection s = config.getAccessSection("refs/*", true);
    Permission p = s.getPermission(Permission.READ, true);
    AccountGroup group = groupCache.get(new AccountGroup.NameKey(groupName));
    PermissionRule rule = new PermissionRule(config.resolve(
        new GroupReference(group.getGroupUUID(), group.getName())));
    rule.setBlock();
    p.add(rule);
    config.commit(md);
    projectCache.evict(config.getProject());
  }

  @Test
  @GerritConfig(name="site.enableProjectDocs", value="true")
  public void redirectToReadme() throws IOException, GitAPIException {
    pushFactory.create(db, admin.getIdent(), "Add readme", "README.md", "read me")
        .to(git, "refs/heads/master");

    HttpResponse r = httpSession.get("/doc/" + project.get());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("read me"));

    r = httpSession.get("/doc/" + project.get() + "/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("read me"));
  }

  @Test
  @GerritConfig(name="site.enableProjectDocs", value="true")
  public void notExistingDoc_NotFound() throws IOException, GitAPIException {
    pushFactory.create(db, admin.getIdent(), "Add readme", "README.md", "read me")
        .to(git, "refs/heads/master");

    assertEquals(HttpStatus.SC_NOT_FOUND,
        httpSession.get("/doc/" + project.get() + "/not-existing.html")
            .getStatusCode());
  }

  @Test
  @GerritConfig(name="site.enableProjectDocs", value="true")
  public void noRev_NotFound() throws IOException, GitAPIException {
    pushFactory.create(db, admin.getIdent(), "Add readme", "README.md", "read me")
        .to(git, "refs/heads/master");
    assertEquals(HttpStatus.SC_NOT_FOUND,
        httpSession.get("/doc/" + project.get() + "/rev/").getStatusCode());
  }

  @Test
  @GerritConfig(name="site.enableProjectDocs", value="true")
  public void getDocFromBranch() throws IOException, GitAPIException {
    pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "master content")
        .to(git, "refs/heads/master");
    pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "stable content")
        .to(git, "refs/heads/stable");

    HttpResponse r =
        httpSession.get("/doc/" + project.get() + "/rev/"
            + Url.encode("refs/heads/master") + "/test.html");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("master content"));

    r = httpSession.get("/doc/" + project.get() + "/rev/"
        + Url.encode("refs/heads/stable") + "/test.html");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("stable content"));

    r = httpSession.get("/doc/" + project.get() + "/rev/stable/test.html");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("stable content"));
  }

  @Test
  @GerritConfig(name="site.enableProjectDocs", value="true")
  public void getDocFromNonExistingBranch_NotFound() throws IOException, GitAPIException {
    pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "master content")
        .to(git, "refs/heads/master");

    assertEquals(HttpStatus.SC_NOT_FOUND,
        httpSession.get("/doc/" + project.get() + "/rev/not-existing/test.html")
            .getStatusCode());
  }

  @Test
  @GerritConfig(name="site.enableProjectDocs", value="true")
  public void urlEncoding() throws JSchException, IOException, GitAPIException {
    String p = "foo/bar";
    createProject(sshSession, p);
    Git git = cloneProject(sshSession.getUrl() + "/" + p);

    pushFactory.create(db, admin.getIdent(), "Add doc", "docs/test.md", "test content")
        .to(git, "refs/heads/master");
    HttpResponse r = httpSession.get("/doc/" + Url.encode(p) + "/" + Url.encode("docs/test.html"));
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("test content"));
  }

  @Test
  @GerritConfig(name="site.enableProjectDocs", value="true")
  public void getDocFromChange() throws IOException, GitAPIException, OrmException {
    pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "master content")
        .to(git, "refs/heads/master");
    PushOneCommit.Result result =
        pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "change content")
            .to(git, "refs/for/master");

    HttpResponse r =
        httpSession.get("/doc/" + project.get() + "/rev/"
            + Url.encode(result.getPatchSetId().toRefName()) + "/test.html");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("change content"));
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name="site.enableProjectDocs", value="true")
  public void getDocFromHead() throws IOException, GitAPIException, OrmException {
    pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "master content")
        .to(git, "refs/heads/master");
    pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "stable content")
        .to(git, "refs/heads/stable");

    HttpResponse r = httpSession.get("/doc/" + project.get() + "/test.html");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("master content"));

    new RestSession(server, admin).put("/projects/" + project.get() + "/HEAD",
        new HeadInput("stable"));

    r = httpSession.get("/doc/" + project.get() + "/test.html");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("stable content"));

    r = httpSession.get("/doc/" + project.get() + "/rev/HEAD/test.html");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("stable content"));
  }

  @Test
  @GerritConfig(name="site.enableProjectDocs", value="true")
  public void getDocFromCommitId() throws IOException, GitAPIException, OrmException {
    PushOneCommit.Result result =
        pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "change content")
            .to(git, "refs/for/master");

    HttpResponse r =
        httpSession.get("/a/doc/" + project.get() + "/rev/"
            + result.getCommitId().getName() + "/test.html");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("change content"));
  }

  private static class HeadInput {
    @SuppressWarnings("unused")
    String ref;

    public HeadInput(String ref) {
      this.ref = ref;
    }
  }
}
