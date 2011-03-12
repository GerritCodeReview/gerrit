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

package com.google.gerrit.httpd;

import com.google.gerrit.common.PageLinks;
import com.google.gerrit.reviewdb.Change;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.AnonymousUser;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.config.CanonicalWebUrl;
import com.google.gerrit.server.git.GitRepositoryManager;
import com.google.gerrit.server.git.ReceiveCommits;
import com.google.gerrit.server.git.TransferConfig;
import com.google.gerrit.server.git.VisibleRefFilter;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.Singleton;

import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.http.server.GitServlet;
import org.eclipse.jgit.http.server.resolver.AsIsFileService;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.storage.pack.PackConfig;
import org.eclipse.jgit.transport.ReceivePack;
import org.eclipse.jgit.transport.UploadPack;
import org.eclipse.jgit.transport.resolver.ReceivePackFactory;
import org.eclipse.jgit.transport.resolver.RepositoryResolver;
import org.eclipse.jgit.transport.resolver.ServiceNotAuthorizedException;
import org.eclipse.jgit.transport.resolver.ServiceNotEnabledException;
import org.eclipse.jgit.transport.resolver.UploadPackFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;

import javax.annotation.Nullable;
import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/** Serves Git repositories over HTTP. */
@Singleton
public class ProjectServlet extends GitServlet {
  private static final Logger log =
      LoggerFactory.getLogger(ProjectServlet.class);

  private static final String ATT_CONTROL = ProjectControl.class.getName();

  static class Module extends AbstractModule {
    @Override
    protected void configure() {
      bind(Resolver.class);
      bind(Upload.class);
      bind(Receive.class);
    }
  }

  static ProjectControl getProjectControl(HttpServletRequest req)
      throws ServiceNotEnabledException {
    ProjectControl pc = (ProjectControl) req.getAttribute(ATT_CONTROL);
    if (pc == null) {
      log.error("No " + ATT_CONTROL + " in request", new Exception("here"));
      throw new ServiceNotEnabledException();
    }
    return pc;
  }

  private final Provider<String> urlProvider;

  @Inject
  ProjectServlet(final Resolver resolver, final Upload upload,
      final Receive receive,
      @CanonicalWebUrl @Nullable Provider<String> urlProvider) {
    this.urlProvider = urlProvider;

    setRepositoryResolver(resolver);
    setAsIsFileService(AsIsFileService.DISABLED);
    setUploadPackFactory(upload);
    setReceivePackFactory(receive);
  }

  @Override
  public void init(ServletConfig config) throws ServletException {
    super.init(config);

    serveRegex("^/(.*?)/?$").with(new HttpServlet() {
      @Override
      protected void doGet(HttpServletRequest req, HttpServletResponse rsp)
          throws IOException {
        ProjectControl pc;
        try {
          pc = getProjectControl(req);
        } catch (ServiceNotEnabledException e) {
          rsp.sendError(HttpServletResponse.SC_NOT_FOUND);
          return;
        }

        Project.NameKey dst = pc.getProject().getNameKey();
        StringBuilder r = new StringBuilder();
        r.append(urlProvider.get());
        r.append('#');
        r.append(PageLinks.toChangeQuery(PageLinks.projectQuery(dst,
            Change.Status.NEW)));
        rsp.sendRedirect(r.toString());
      }
    });
  }

  static class Resolver implements RepositoryResolver<HttpServletRequest> {
    private final GitRepositoryManager manager;
    private final ProjectControl.Factory projectControlFactory;

    @Inject
    Resolver(GitRepositoryManager manager,
        ProjectControl.Factory projectControlFactory) {
      this.manager = manager;
      this.projectControlFactory = projectControlFactory;
    }

    @Override
    public Repository open(HttpServletRequest req, String projectName)
        throws RepositoryNotFoundException, ServiceNotAuthorizedException,
        ServiceNotEnabledException {
      if (projectName.endsWith(".git")) {
        // Be nice and drop the trailing ".git" suffix, which we never keep
        // in our database, but clients might mistakenly provide anyway.
        //
        projectName = projectName.substring(0, projectName.length() - 4);
      }

      if (projectName.startsWith("/")) {
        // Be nice and drop the leading "/" if supplied by an absolute path.
        // We don't have a file system hierarchy, just a flat namespace in
        // the database's Project entities. We never encode these with a
        // leading '/' but users might accidentally include them in Git URLs.
        //
        projectName = projectName.substring(1);
      }

      final ProjectControl pc;
      try {
        final Project.NameKey nameKey = new Project.NameKey(projectName);
        pc = projectControlFactory.controlFor(nameKey);
      } catch (NoSuchProjectException err) {
        throw new RepositoryNotFoundException(projectName);
      }
      if (!pc.isVisible()) {
        if (pc.getCurrentUser() instanceof AnonymousUser) {
          throw new ServiceNotAuthorizedException();
        } else {
          throw new ServiceNotEnabledException();
        }
      }
      req.setAttribute(ATT_CONTROL, pc);

      return manager.openRepository(pc.getProject().getNameKey());
    }
  }

  static class Upload implements UploadPackFactory<HttpServletRequest> {
    private final Provider<ReviewDb> db;
    private final PackConfig packConfig;

    @Inject
    Upload(final Provider<ReviewDb> db, final TransferConfig tc) {
      this.db = db;
      this.packConfig = tc.getPackConfig();
    }

    @Override
    public UploadPack create(HttpServletRequest req, Repository repo)
        throws ServiceNotEnabledException, ServiceNotAuthorizedException {
      ProjectControl pc = getProjectControl(req);
      if (!pc.canRunUploadPack()) {
        throw new ServiceNotAuthorizedException();
      }

      // The Resolver above already checked READ access for us.
      //
      UploadPack up = new UploadPack(repo);
      up.setPackConfig(packConfig);
      if (!pc.allRefsAreVisible()) {
        up.setRefFilter(new VisibleRefFilter(repo, pc, db.get(), true));
      }
      return up;
    }
  }

  static class Receive implements ReceivePackFactory<HttpServletRequest> {
    private final ReceiveCommits.Factory factory;

    @Inject
    Receive(final ReceiveCommits.Factory factory) {
      this.factory = factory;
    }

    @Override
    public ReceivePack create(HttpServletRequest req, Repository db)
        throws ServiceNotEnabledException, ServiceNotAuthorizedException {
      final ProjectControl pc = getProjectControl(req);
      if (!pc.canRunReceivePack()) {
        throw new ServiceNotAuthorizedException();
      }

      if (pc.getCurrentUser() instanceof IdentifiedUser) {
        final IdentifiedUser user = (IdentifiedUser) pc.getCurrentUser();
        final ReceiveCommits rc = factory.create(pc, db);
        final ReceiveCommits.Capable s = rc.canUpload();
        if (s != ReceiveCommits.Capable.OK) {
          // TODO We should alert the user to this message on the HTTP
          // response channel, assuming Git will even report it to them.
          //
          final String who = user.getUserName();
          final String why = s.getMessage();
          log.warn("Rejected push from " + who + ": " + why);
          throw new ServiceNotEnabledException();
        }

        rc.getReceivePack().setRefLogIdent(user.newRefLogIdent());
        return rc.getReceivePack();

      } else {
        throw new ServiceNotAuthorizedException();
      }
    }
  }
}
