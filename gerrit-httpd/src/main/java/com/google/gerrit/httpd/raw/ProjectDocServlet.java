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

import static javax.servlet.http.HttpServletResponse.SC_NOT_MODIFIED;

import com.google.common.base.Objects;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.Hashing;
import com.google.common.net.HttpHeaders;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.httpd.resources.Resource;
import com.google.gerrit.reviewdb.client.Project;
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
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;

import java.io.IOException;
import java.util.concurrent.TimeUnit;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Singleton
public class ProjectDocServlet extends HttpServlet {
  private static final long serialVersionUID = 1L;

  private final ProjectControl.Factory projectControlFactory;
  private final Provider<GetHead> getHead;
  private final GitRepositoryManager repoManager;
  private final LoadingCache<ProjectDocResourceKey, Resource> docCache;

  @Inject
  ProjectDocServlet(ProjectControl.Factory projectControlFactory,
      Provider<GetHead> getHead, GitRepositoryManager repoManager,
      @Named(ProjectDocLoader.Module.PROJECT_DOC_RESOURCES)
          LoadingCache<ProjectDocResourceKey, Resource> cache) {
    this.projectControlFactory = projectControlFactory;
    this.getHead = getHead;
    this.repoManager = repoManager;
    this.docCache = cache;
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
      Resource.NOT_FOUND.send(req, res);
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
        Resource.NOT_FOUND.send(req, res);
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
      Resource.NOT_FOUND.send(req, res);
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
            Resource.NOT_FOUND.send(req, res);
            return;
          }
        }
      }

      Repository repo = repoManager.openRepository(projectName);
      try {
        ObjectId revId = repo.resolve(Objects.firstNonNull(rev, Constants.HEAD));
        if (revId == null) {
          Resource.NOT_FOUND.send(req, res);
          return;
        }

        if (ObjectId.isId(rev)) {
          RevWalk rw = new RevWalk(repo);
          try {
            RevCommit commit = rw.parseCommit(repo.resolve(rev));
            if (!projectControl.canReadCommit(rw, commit)) {
              Resource.NOT_FOUND.send(req, res);
              return;
            }
          } finally {
            rw.release();
          }
        }

        String eTag = null;
        String receivedETag = req.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (receivedETag != null) {
          eTag = computeETag(project, revId, file);
          if (eTag.equals(receivedETag)) {
            res.sendError(SC_NOT_MODIFIED);
            return;
          }
        }

        Resource rsc = docCache.getUnchecked(
            new ProjectDocResourceKey(projectName, file, revId));

        if (rsc != Resource.NOT_FOUND) {
          res.setHeader(HttpHeaders.ETAG,
              Objects.firstNonNull(eTag, computeETag(project, revId, file)));
        }
        CacheHeaders.setCacheablePrivate(res, 7, TimeUnit.DAYS, false);
        rsc.send(req, res);
        return;
      } finally {
        repo.close();
      }
    } catch (RepositoryNotFoundException | NoSuchProjectException
        | ResourceNotFoundException | AuthException | RevisionSyntaxException e) {
      Resource.NOT_FOUND.send(req, res);
      return;
    }
  }

  private static String computeETag(String project, ObjectId revId, String file) {
    return Hashing.md5().newHasher()
        .putUnencodedChars(project)
        .putUnencodedChars(revId.getName())
        .putUnencodedChars(file)
        .hash().toString();
  }
}
