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

import com.google.common.cache.LoadingCache;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.httpd.resources.Resource;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.auth.AuthException;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.GetHead;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;

import java.io.IOException;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class ProjectDocServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private final ProjectControl.Factory projectControlFactory;
  private final Provider<GetHead> getHead;
  private final GitRepositoryManager repoManager;
  private final LoadingCache<ProjectDocResourceKey, Resource> resourceCache;

  @Inject
  ProjectDocServlet(ProjectControl.Factory projectControlFactory,
      Provider<GetHead> getHead, GitRepositoryManager repoManager,
      @Named(ProjectDocLoader.Module.PROJECT_DOC_RESOURCES)
          LoadingCache<ProjectDocResourceKey, Resource> cache) {
    this.projectControlFactory = projectControlFactory;
    this.getHead = getHead;
    this.repoManager = repoManager;
    this.resourceCache = cache;
  }

  @Override
  public void service(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    if (!"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod())) {
      CacheHeaders.setNotCacheable(res);
      res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      return;
    }

    String uri = req.getRequestURI();
    if (uri.startsWith("/a")) {
      uri = uri.substring(2);
    }
    String ctx = req.getContextPath() + "doc/";
    if (uri.length() <= ctx.length()) {
      Resource.NOT_FOUND.send(req, res);
      return;
    }

    String path = uri.substring(ctx.length() + 1);
    if ("".equals(path)) {
      Resource.NOT_FOUND.send(req, res);
      return;
    }

    int i = path.indexOf("/");
    if (i == -1) {
      res.sendRedirect(uri + "/README.html");
      return;
    } else if (i == path.length() - 1) {
      res.sendRedirect(uri + "README.html");
      return;
    }

    String project = IdString.fromUrl(path.substring(0, i)).get();
    String rest = path.substring(i + 1);

    String rev = null;
    if (rest.startsWith("rev/")) {
      if (rest.length() == 4) {
        Resource.NOT_FOUND.send(req, res);
        return;
      }
      rest = rest.substring(4);
      i = rest.indexOf("/");
      if (i == -1) {
        res.sendRedirect(uri + "/README.html");
        return;
      } else if (i == rest.length() - 1) {
        res.sendRedirect(uri + "README.html");
        return;
      }
      rev = IdString.fromUrl(rest.substring(0, i)).get();
      rest = rest.substring(i + 1);
    }

    String file = IdString.fromUrl(rest).get();
    if (!file.endsWith(".html")) {
      Resource.NOT_FOUND.send(req, res);
      return;
    }
    file = file.substring(0, file.length() - 4) + "md";

    Project.NameKey projectName = new Project.NameKey(project);
    try {
      ProjectControl projectControl = projectControlFactory.validateFor(projectName);
      if (rev == null || Constants.HEAD.equals(rev)) {
        rev = getHead.get().apply(new ProjectResource(projectControl));
      } else  {
        if (ObjectId.isId(rev)) {
          if (!projectControl.isOwner()) {
            Resource.NOT_FOUND.send(req, res);
            return;
          }
        } else {
          if (!rev.startsWith("refs/")) {
            rev = "refs/heads/" + rev;
          }
          if (!projectControl.controlForRef(rev).isVisible()) {
            Resource.NOT_FOUND.send(req, res);
            return;
          }
        }
      }

      Repository repo = repoManager.openRepository(projectName);
      try {
        ObjectId revId = repo.resolve(rev != null ? rev : Constants.HEAD);
        if (revId == null) {
          Resource.NOT_FOUND.send(req, res);
          return;
        }
        Resource rsc = resourceCache.getUnchecked(
            new ProjectDocResourceKey(projectName, file, revId));
        rsc.send(req, res);
        return;
      } finally {
        repo.close();
      }
    } catch (RepositoryNotFoundException | NoSuchProjectException
        | ResourceNotFoundException | AuthException e) {
      Resource.NOT_FOUND.send(req, res);
      return;
    }
  }
}
