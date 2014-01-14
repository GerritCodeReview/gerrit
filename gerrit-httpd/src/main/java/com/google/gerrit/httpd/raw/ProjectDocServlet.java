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

package com.google.gerrit.httpd.raw;

import com.google.common.base.Strings;
import com.google.common.collect.Maps;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.documentation.MarkdownFormatter;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.GetHead;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gerrit.server.ssh.SshAddressesModule;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;

import java.io.IOException;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class ProjectDocServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private final ProjectControl.Factory projectControlFactory;
  private final Provider<GetHead> getHead;
  private final GitRepositoryManager repoManager;
  private final Provider<String> webUrl;
  private final String sshHost;
  private final int sshPort;

  @Inject
  ProjectDocServlet(ProjectControl.Factory projectControlFactory,
      Provider<GetHead> getHead, GitRepositoryManager repoManager,
      @CanonicalWebUrl Provider<String> webUrl, SshInfo sshInfo) {
    this.projectControlFactory = projectControlFactory;
    this.getHead = getHead;
    this.repoManager = repoManager;
    this.webUrl = webUrl;

    String sshHost = "review.example.com";
    int sshPort = SshAddressesModule.DEFAULT_PORT;
    if (!sshInfo.getHostKeys().isEmpty()) {
      String host = sshInfo.getHostKeys().get(0).getHost();
      int c = host.lastIndexOf(':');
      if (0 <= c) {
        sshHost = host.substring(0, c);
        sshPort = Integer.parseInt(host.substring(c+1));
      } else {
        sshHost = host;
        sshPort = SshAddressesModule.IANA_SSH_PORT;
      }
    }
    this.sshHost = sshHost;
    this.sshPort = sshPort;
  }

  @Override
  public void service(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    if (!"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod())) {
      CacheHeaders.setNotCacheable(res);
      res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      return;
    }

    String path = req.getPathInfo();
    if ("".equals(path)) {
      notFound(res);
      return;
    }

    int i = path.indexOf('/');
    if (i == -1) {
      res.sendRedirect(req.getRequestURI() + "/README.md");
      return;
    } else if (i == path.length() - 1) {
      res.sendRedirect(req.getRequestURI() + "README.md");
      return;
    }

    String project = IdString.fromUrl(path.substring(0, i)).get();
    String rest = path.substring(i + 1);

    String rev = null;
    if (rest.startsWith("rev/")) {
      if (rest.length() == 4) {
        notFound(res);
        return;
      }
      rest = rest.substring(4);
      i = rest.indexOf('/');
      if (i == -1) {
        res.sendRedirect(req.getRequestURI() + "/README.md");
        return;
      } else if (i == rest.length() - 1) {
        res.sendRedirect(req.getRequestURI() + "README.md");
        return;
      }
      rev = IdString.fromUrl(rest.substring(0, i)).get();
      rest = rest.substring(i + 1);
    }

    String file = IdString.fromUrl(rest).get();
    if (!file.endsWith(".md")) {
      notFound(res);
      return;
    }

    Project.NameKey projectName = new Project.NameKey(project);
    try {
      ProjectControl projectControl = projectControlFactory.validateFor(projectName);
      if (rev == null || Constants.HEAD.equals(rev)) {
        rev = getHead.get().apply(new ProjectResource(projectControl));
      } else  {
        if (!ObjectId.isId(rev)) {
          if (!rev.startsWith(Constants.R_REFS)) {
            rev = Constants.R_HEADS + rev;
          }
          if (!projectControl.controlForRef(rev).isVisible()) {
            notFound(res);
            return;
          }
        }
      }

      Repository repo = repoManager.openRepository(projectName);
      try {
        RevWalk rw = new RevWalk(repo);
        try {
          ObjectId revId = repo.resolve(rev);
          if (revId == null) {
            notFound(res);
            return;
          }
          RevCommit commit = rw.parseCommit(repo.resolve(rev));

          if (ObjectId.isId(rev) && !projectControl.canReadCommit(rw, commit)) {
            notFound(res);
            return;
          }

          RevTree tree = commit.getTree();
          TreeWalk tw = new TreeWalk(repo);
          try {
            tw.addTree(tree);
            tw.setRecursive(true);
            tw.setFilter(PathFilter.create(file));
            if (!tw.next()) {
              notFound(res);
              return;
            }
            ObjectId objectId = tw.getObjectId(0);
            ObjectLoader loader = repo.open(objectId);
            byte[] md = loader.getBytes(Integer.MAX_VALUE);
            sendMarkdownAsHtml(new String(md, "UTF-8"), commit.getCommitTime(), res);
          } finally {
            tw.release();
          }
        } finally {
          rw.release();
        }
      } finally {
        repo.close();
      }
    } catch (RepositoryNotFoundException | NoSuchProjectException
        | ResourceNotFoundException | AuthException | RevisionSyntaxException e) {
      notFound(res);
      return;
    }
  }

  private void sendMarkdownAsHtml(String md, int lastModified, HttpServletResponse res)
      throws IOException {
    byte[] html = new MarkdownFormatter()
        .markdownToDocHtml(replaceMacros(md), "UTF-8", true);
    res.setDateHeader("Last-Modified", lastModified);
    res.setContentType("text/html");
    res.setCharacterEncoding("UTF-8");
    res.setContentLength(html.length);
    res.getOutputStream().write(html);
  }

  private String replaceMacros(String md) {
    Map<String, String> macros = Maps.newHashMap();
    macros.put("SSH_HOST", sshHost);
    macros.put("SSH_PORT", "" + sshPort);
    String url = webUrl.get();
    if (Strings.isNullOrEmpty(url)) {
      url = "http://review.example.com/";
    }
    macros.put("URL", url);

    Matcher m = Pattern.compile("(\\\\)?@([A-Z_]+)@").matcher(md);
    StringBuffer sb = new StringBuffer();
    while (m.find()) {
      String key = m.group(2);
      String val = macros.get(key);
      if (m.group(1) != null) {
        m.appendReplacement(sb, "@" + key + "@");
      } else if (val != null) {
        m.appendReplacement(sb, val);
      } else {
        m.appendReplacement(sb, "@" + key + "@");
      }
    }
    m.appendTail(sb);
    return sb.toString();
  }

  private static void notFound(HttpServletResponse res) throws IOException {
    CacheHeaders.setNotCacheable(res);
    res.sendError(HttpServletResponse.SC_NOT_FOUND);
  }
}
