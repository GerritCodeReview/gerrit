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

import com.google.common.base.CharMatcher;
import com.google.common.base.Objects;
import com.google.common.cache.LoadingCache;
import com.google.common.hash.Hashing;
import com.google.common.net.HttpHeaders;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.IdString;
import com.google.gerrit.extensions.restapi.ResourceNotFoundException;
import com.google.gerrit.httpd.raw.ProjectDocResourceKey.DiffMode;
import com.google.gerrit.httpd.resources.Resource;
import com.google.gerrit.httpd.resources.SmallResource;
import com.google.gerrit.reviewdb.client.Project;
import com.google.gerrit.server.FileTypeRegistry;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.project.GetHead;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gerrit.server.project.ProjectResource;
import com.google.gwtexpui.server.CacheHeaders;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;
import com.google.inject.name.Named;

import eu.medsea.mimeutil.MimeType;

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
  private final ProjectCache projectCache;
  private final FileTypeRegistry fileTypeRegistry;

  @Inject
  ProjectDocServlet(ProjectControl.Factory projectControlFactory,
      Provider<GetHead> getHead, GitRepositoryManager repoManager,
      @Named(ProjectDocLoader.Module.PROJECT_DOC_RESOURCES)
          LoadingCache<ProjectDocResourceKey, Resource> cache,
          ProjectCache projectCache, FileTypeRegistry fileTypeRegistry) {
    this.projectControlFactory = projectControlFactory;
    this.getHead = getHead;
    this.repoManager = repoManager;
    this.docCache = cache;
    this.projectCache = projectCache;
    this.fileTypeRegistry = fileTypeRegistry;
  }

  @Override
  public void service(HttpServletRequest req, HttpServletResponse res)
      throws IOException {
    if (!"GET".equals(req.getMethod()) && !"HEAD".equals(req.getMethod())) {
      CacheHeaders.setNotCacheable(res);
      res.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
      return;
    }

    ResourceKey key = ResourceKey.fromPath(req.getPathInfo());
    if (projectCache.get(key.project) == null) {
      Resource.NOT_FOUND.send(req, res);
      return;
    }
    if (key.file == null) {
      res.sendRedirect(getRedirectUrl(req, key));
      return;
    }
    MimeType mimeType = fileTypeRegistry.getMimeType(key.file, null);
    if (!key.file.endsWith(".md")
        && !("image".equals(mimeType.getMediaType())
            && fileTypeRegistry.isSafeInline(mimeType))) {
      Resource.NOT_FOUND.send(req, res);
      return;
    }

    try {
      ProjectControl projectControl = projectControlFactory.validateFor(key.project);
      String rev =
          resolveRevision(Objects.firstNonNull(key.revision, Constants.HEAD),
              projectControl);
      if (rev == null) {
        Resource.NOT_FOUND.send(req, res);
        return;
      }
      String revB = key.revisionB;
      if (revB != null) {
        revB = resolveRevision(revB, projectControl);
        if (revB == null) {
          Resource.NOT_FOUND.send(req, res);
          return;
        }
      }

      Repository repo = repoManager.openRepository(key.project);
      try {
        ObjectId revId =
            getRevisionId(repo, Objects.firstNonNull(rev, Constants.HEAD),
                projectControl);
        if (revId == null) {
          Resource.NOT_FOUND.send(req, res);
          return;
        }

        ObjectId revIdB = null;
        if (revB != null) {
          revIdB = getRevisionId(repo, revB, projectControl);
          if (revIdB == null) {
            Resource.NOT_FOUND.send(req, res);
            return;
          }
        }

        String eTag = null;
        String receivedETag = req.getHeader(HttpHeaders.IF_NONE_MATCH);
        if (receivedETag != null) {
          eTag = computeETag(key.project, key.file, revId, revIdB, key.diffMode);
          if (eTag.equals(receivedETag)) {
            res.sendError(SC_NOT_MODIFIED);
            return;
          }
        }

        Resource rsc;
        if (key.file.endsWith(".md")) {
          rsc = docCache.getUnchecked(
            new ProjectDocResourceKey(key.project, key.file, revId, revIdB, key.diffMode));
        } else if ("image".equals(mimeType.getMediaType())) {
          rsc = getImageResource(repo, revId, key.file);
        } else {
          rsc = Resource.NOT_FOUND;
        }

        if (rsc != Resource.NOT_FOUND) {
          res.setHeader(HttpHeaders.ETAG,
              Objects.firstNonNull(eTag,
                  computeETag(key.project, key.file, revId, revIdB, key.diffMode)));
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

  private String resolveRevision(String rev, ProjectControl projectControl)
      throws IOException, ResourceNotFoundException, AuthException {
    if (Constants.HEAD.equals(rev)) {
      rev = getHead.get().apply(new ProjectResource(projectControl));
    } else  {
      if (!ObjectId.isId(rev)) {
        if (!rev.startsWith(Constants.R_REFS)) {
          rev = Constants.R_HEADS + rev;
        }
        if (!projectControl.controlForRef(rev).isVisible()) {
          return null;
        }
      }
    }
    return rev;
  }

  private ObjectId getRevisionId(Repository repo, String rev,
      ProjectControl projectControl) throws IOException {
    try {
      ObjectId revId = repo.resolve(rev);
      if (revId == null) {
        return null;
      }

      if (ObjectId.isId(rev)) {
        RevWalk rw = new RevWalk(repo);
        try {
          RevCommit commit = rw.parseCommit(repo.resolve(rev));
          if (!projectControl.canReadCommit(rw, commit)) {
            return null;
          }
        } finally {
          rw.release();
        }
      }
      return revId;
    } catch (RevisionSyntaxException e) {
      return null;
    }
  }

  private Resource getImageResource(Repository repo, ObjectId revId, String file) {
    RevWalk rw = new RevWalk(repo);
    try {
      RevCommit commit = rw.parseCommit(revId);
      RevTree tree = commit.getTree();
      TreeWalk tw = new TreeWalk(repo);
      try {
        tw.addTree(tree);
        tw.setRecursive(true);
        tw.setFilter(PathFilter.create(file));
        if (!tw.next()) {
          return Resource.NOT_FOUND;
        }
        ObjectId objectId = tw.getObjectId(0);
        ObjectLoader loader = repo.open(objectId);
        byte[] content = loader.getBytes(Integer.MAX_VALUE);

        MimeType mimeType = fileTypeRegistry.getMimeType(file, content);
        if (!"image".equals(mimeType.getMediaType())
            || !fileTypeRegistry.isSafeInline(mimeType)) {
          return Resource.NOT_FOUND;
        }
        return new SmallResource(content)
            .setContentType(mimeType.toString())
            .setCharacterEncoding("UTF-8")
            .setLastModified(commit.getCommitTime());
      } finally {
        tw.release();
      }
    } catch (IOException e) {
      return Resource.NOT_FOUND;
    } finally {
      rw.release();
    }
  }

  private static String computeETag(Project.NameKey project, String file,
      ObjectId revId, ObjectId revIdB, DiffMode diffMode) {
    return Hashing.md5().newHasher()
        .putUnencodedChars(project.get())
        .putUnencodedChars(revId.getName())
        .putUnencodedChars(revIdB != null ? revIdB.getName() : "")
        .putUnencodedChars(diffMode != null ? diffMode.name() : "")
        .putUnencodedChars(file)
        .hash().toString();
  }

  private String getRedirectUrl(HttpServletRequest req, ResourceKey key) {
    StringBuilder redirectUrl = new StringBuilder();
    redirectUrl.append(req.getRequestURL().substring(0,
        req.getRequestURL().length() - req.getRequestURI().length()));
    redirectUrl.append(req.getContextPath());
    redirectUrl.append("/src/");
    redirectUrl.append(key.project);
    redirectUrl.append("/");
    if (key.revision != null) {
      redirectUrl.append("rev/");
      redirectUrl.append(key.revision);
      redirectUrl.append("/");
    }
    redirectUrl.append("README.md");
    return redirectUrl.toString();
  }

  private static class ResourceKey {
    final Project.NameKey project;
    final String file;
    final String revision;
    final String revisionB;
    final DiffMode diffMode;

    static ResourceKey fromPath(String path) {
      String project;
      String file = null;
      String revision = null;
      String revisionB = null;
      DiffMode diffMode = null;

      int i = path.indexOf('/');
      if (i != -1 && i != path.length() - 1) {
        project = IdString.fromUrl(path.substring(0, i)).get();
        String rest = path.substring(i + 1);

        if (rest.startsWith("rev/")) {
          if (rest.length() > 4) {
            rest = rest.substring(4);
            i = rest.indexOf('/');
            if (i != -1 && i != path.length() - 1) {
              revision = IdString.fromUrl(rest.substring(0, i)).get();
              file = IdString.fromUrl(rest.substring(i + 1)).get();
            } else {
              revision = IdString.fromUrl(rest).get();
            }
          }
        } else {
          file = IdString.fromUrl(rest).get();
        }

      } else {
        project = CharMatcher.is('/').trimTrailingFrom(path);
      }

      if (revision != null) {
        if (revision.contains("..")) {
          diffMode = DiffMode.UNIFIED;
          int p = revision.indexOf("..");
          revisionB = revision.substring(p + 2);
          revision = revision.substring(0, p);
        } else if (revision.contains("<-")) {
          diffMode = DiffMode.SIDEBYSIDE_A;
          int p = revision.indexOf("<-");
          revisionB = revision.substring(p + 2);
          revision = revision.substring(0, p);
        } else if (revision.contains("->")) {
          diffMode = DiffMode.SIDEBYSIDE_B;
          int p = revision.indexOf("->");
          revisionB = revision.substring(p + 2);
          revision = revision.substring(0, p);
        }
      }

      return new ResourceKey(project, file, revision, revisionB, diffMode);
    }

    private ResourceKey(String p, String f, String r, String rB, DiffMode dm) {
      project = p != null ? new Project.NameKey(p) : null;
      file = f;
      revision = r;
      revisionB = rB;
      diffMode = dm;
    }
  }
}
