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

package com.google.gerrit.acceptance.project.doc;

import static com.google.gerrit.acceptance.GitUtil.cloneProject;
import static com.google.gerrit.acceptance.GitUtil.createProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.google.common.net.HttpHeaders;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.GerritConfigs;
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
import com.google.gerrit.server.project.SetHead;
import com.google.gwtorm.server.OrmException;
import com.google.gwtorm.server.SchemaFactory;
import com.google.inject.Inject;

import com.jcraft.jsch.JSchException;

import org.apache.http.HttpStatus;
import org.apache.http.message.BasicHeader;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.ConfigInvalidException;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

public class ProjectDocumentationIT extends AbstractDaemonTest {

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

  private HttpSession httpSession;
  private SshSession sshSession;
  private Project.NameKey project;
  private Git git;
  private ReviewDb db;


  @Before
  public void setUp() throws Exception {
    httpSession = new HttpSession(server, admin);
    sshSession = new SshSession(server, admin);

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
  @GerritConfig(name="site.enableSrcToMarkdown", value="true")
  public void noDocs_NotFound() throws IOException {
    assertEquals(HttpStatus.SC_NOT_FOUND,
        httpSession.get("/src/" + project.get()).getStatusCode());
  }

  @Test
  @GerritConfig(name="site.enableSrcToMarkdown", value="true")
  public void getDoc() throws IOException, GitAPIException {
    pushFactory.create(db, admin.getIdent(), "Add readme", "README.md", "read me")
        .to(git, "refs/heads/master");

    HttpResponse r = httpSession.get("/src/" + project.get() + "/README.md");
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
        httpSession.get("/src/" + project.get()).getStatusCode());
  }

  @Test
  @GerritConfig(name="site.enableSrcToMarkdown", value="true")
  public void nonVisibleProject_NotFound() throws IOException,
      GitAPIException, OrmException, JSchException, ConfigInvalidException {
    pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "content")
        .to(git, "refs/heads/master");
    String users = "Users";
    new RestSession(server, admin).put("/groups/" + users);
    TestAccount user = accounts.create("user", users);
    blockReadFor(users);
    HttpResponse r = new HttpSession(server, user)
        .get("/a/src/" + project.get() + "/test.md");
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
  @GerritConfig(name="site.enableSrcToMarkdown", value="true")
  public void redirectToReadme() throws IOException, GitAPIException {
    pushFactory.create(db, admin.getIdent(), "Add readme", "README.md", "read me")
        .to(git, "refs/heads/master");

    HttpResponse r = httpSession.get("/src/" + project.get());
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("read me"));

    r = httpSession.get("/src/" + project.get() + "/");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("read me"));
  }

  @Test
  @GerritConfig(name="site.enableSrcToMarkdown", value="true")
  public void notExistingDoc_NotFound() throws IOException, GitAPIException {
    pushFactory.create(db, admin.getIdent(), "Add readme", "README.md", "read me")
        .to(git, "refs/heads/master");

    assertEquals(HttpStatus.SC_NOT_FOUND,
        httpSession.get("/src/" + project.get() + "/not-existing.md")
            .getStatusCode());
  }

  @Test
  @GerritConfig(name="site.enableSrcToMarkdown", value="true")
  public void getDocFromBranch() throws IOException, GitAPIException {
    pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "master content")
        .to(git, "refs/heads/master");
    pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "stable content")
        .to(git, "refs/heads/stable");

    HttpResponse r =
        httpSession.get("/src/" + project.get() + "/rev/"
            + Url.encode("refs/heads/master") + "/test.md");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("master content"));

    r = httpSession.get("/src/" + project.get() + "/rev/"
        + Url.encode("refs/heads/stable") + "/test.md");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("stable content"));

    r = httpSession.get("/src/" + project.get() + "/rev/stable/test.md");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("stable content"));
  }

  @Test
  @GerritConfig(name="site.enableSrcToMarkdown", value="true")
  public void getDocFromNonExistingBranch_NotFound() throws IOException, GitAPIException {
    pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "master content")
        .to(git, "refs/heads/master");

    assertEquals(HttpStatus.SC_NOT_FOUND,
        httpSession.get("/src/" + project.get() + "/rev/not-existing/test.md")
            .getStatusCode());
  }

  @Test
  @GerritConfig(name="site.enableSrcToMarkdown", value="true")
  public void urlEncoding() throws JSchException, IOException, GitAPIException {
    String p = "foo/bar";
    createProject(sshSession, p);
    Git git = cloneProject(sshSession.getUrl() + "/" + p);

    pushFactory.create(db, admin.getIdent(), "Add doc", "docs/test.md", "test content")
        .to(git, "refs/heads/master");
    HttpResponse r = httpSession.get("/src/" + Url.encode(p) + "/" + Url.encode("docs/test.md"));
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("test content"));
  }

  @Test
  @GerritConfig(name="site.enableSrcToMarkdown", value="true")
  public void getDocFromChange() throws IOException, GitAPIException, OrmException {
    pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "master content")
        .to(git, "refs/heads/master");
    PushOneCommit.Result result =
        pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "change content")
            .to(git, "refs/for/master");

    HttpResponse r =
        httpSession.get("/src/" + project.get() + "/rev/"
            + Url.encode(result.getPatchSetId().toRefName()) + "/test.md");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("change content"));
  }

  @Test
  @UseLocalDisk
  @GerritConfig(name="site.enableSrcToMarkdown", value="true")
  public void getDocFromHead() throws IOException, GitAPIException, OrmException {
    pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "master content")
        .to(git, "refs/heads/master");
    pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "stable content")
        .to(git, "refs/heads/stable");

    HttpResponse r = httpSession.get("/src/" + project.get() + "/test.md");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("master content"));

    SetHead.Input in = new SetHead.Input();
    in.ref = "stable";
    new RestSession(server, admin).put("/projects/" + project.get() + "/HEAD", in);

    r = httpSession.get("/src/" + project.get() + "/test.md");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("stable content"));

    r = httpSession.get("/src/" + project.get() + "/rev/HEAD/test.md");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("stable content"));
  }

  @Test
  @GerritConfig(name="site.enableSrcToMarkdown", value="true")
  public void getDocFromCommitId() throws IOException, GitAPIException, OrmException {
    PushOneCommit.Result result =
        pushFactory.create(db, admin.getIdent(), "Add doc", "test.md", "change content")
            .to(git, "refs/for/master");

    HttpResponse r =
        httpSession.get("/a/src/" + project.get() + "/rev/"
            + result.getCommitId().getName() + "/test.md");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    assertTrue(r.getEntityContent().contains("change content"));
  }

  @Test
  @GerritConfig(name="site.enableSrcToMarkdown", value="true")
  public void noEmbeddedHtml() throws IOException, GitAPIException {
    StringBuilder mdContent = new StringBuilder();
    mdContent.append("read me\n\n");
    mdContent.append("<div>");
    mdContent.append("<a href=\"test.html\">test</a>");
    mdContent.append("<br/>");
    mdContent.append("<p>test</p>");
    mdContent.append("</div>");

    pushFactory.create(db, admin.getIdent(), "Add readme", "README.md", mdContent.toString())
        .to(git, "refs/heads/master");

    HttpResponse r = httpSession.get("/src/" + project.get() + "/README.md");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    String htmlContent = r.getEntityContent();
    assertTrue(htmlContent.contains("read me"));
    assertFalse(htmlContent.contains("test"));
    assertFalse(htmlContent.contains("<br/>"));
  }

  @Test
  @GerritConfig(name="site.enableSrcToMarkdown", value="true")
  public void noXSS() throws IOException, GitAPIException {
    StringBuilder mdContent = new StringBuilder();
    mdContent.append("read me\n\n");
    mdContent.append("[test](javascript:alert('xss'))");

    pushFactory.create(db, admin.getIdent(), "Add readme", "README.md", mdContent.toString())
        .to(git, "refs/heads/master");

    HttpResponse r = httpSession.get("/src/" + project.get() + "/README.md");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    String htmlContent = r.getEntityContent();
    assertTrue(htmlContent.contains("read me"));
    assertFalse(htmlContent.contains("<a"));
  }

  @Test
  @GerritConfig(name="site.enableSrcToMarkdown", value="true")
  public void getDocFromCache() throws IOException, GitAPIException {
    pushFactory.create(db, admin.getIdent(), "Add readme", "README.md", "read me")
        .to(git, "refs/heads/master");
    HttpResponse r = httpSession.get("/src/" + project.get() + "/README.md");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
    String eTag = r.getHeader(HttpHeaders.ETAG);
    assertNotNull(eTag);
    r.consume();
    r = httpSession.get("/src/" + project.get() + "/README.md",
        new BasicHeader(HttpHeaders.IF_NONE_MATCH, eTag));
    assertEquals(HttpStatus.SC_NOT_MODIFIED, r.getStatusCode());
  }

  @Test
  @GerritConfig(name="site.enableSrcToMarkdown", value="true")
  public void getImage_NotFound() throws IOException, GitAPIException {
    pushFactory.create(db, admin.getIdent(), "Add readme", "test.jpg", "content")
        .to(git, "refs/heads/master");
    HttpResponse r = httpSession.get("/src/" + project.get() + "/test.jpg");
    assertEquals(HttpStatus.SC_NOT_FOUND, r.getStatusCode());
  }

  @Test
  @GerritConfigs({
    @GerritConfig(name="site.enableSrcToMarkdown", value="true"),
    @GerritConfig(name="mimetype.image/*.safe", value="true")
  })
  public void getImageSafeMimeType() throws IOException, GitAPIException {
    pushFactory.create(db, admin.getIdent(), "Add readme", "test.jpg", "content")
        .to(git, "refs/heads/master");
    HttpResponse r = httpSession.get("/src/" + project.get() + "/test.jpg");
    assertEquals(HttpStatus.SC_OK, r.getStatusCode());
  }
}
