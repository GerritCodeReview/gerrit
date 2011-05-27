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

package com.google.gerrit.server.git;

import com.google.gerrit.reviewdb.AccountGroup;
import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.CurrentUser;
import com.google.gerrit.server.ReplicationUser;
import com.google.gerrit.server.config.ConfigUtil;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.Singleton;
import com.google.inject.assistedinject.FactoryProvider;

import com.jcraft.jsch.Session;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.storage.file.FileBasedConfig;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteSession;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.util.QuotedString;
import org.eclipse.jgit.util.io.StreamCopyThread;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/** Manages automatic replication to remote repositories. */
@Singleton
public class PushReplication implements ReplicationQueue {
  static final Logger log = LoggerFactory.getLogger(PushReplication.class);

  static {
    // Install our own factory which always runs in batch mode, as we
    // have no UI available for interactive prompting.
    //
    SshSessionFactory.setInstance(new JschConfigSessionFactory() {
      @Override
      protected void configure(OpenSshConfig.Host hc, Session session) {
        // Default configuration is batch mode.
      }
    });
  }

  private final Injector injector;
  private final WorkQueue workQueue;
  private final List<ReplicationConfig> configs;
  private final SchemaFactory<ReviewDb> database;
  private final ReplicationUser.Factory replicationUserFactory;

  @Inject
  PushReplication(final Injector i, final WorkQueue wq, final SitePaths site,
      final ReplicationUser.Factory ruf, final SchemaFactory<ReviewDb> db)
      throws ConfigInvalidException, IOException {
    injector = i;
    workQueue = wq;
    database = db;
    replicationUserFactory = ruf;
    configs = allConfigs(site);
  }

  @Override
  public boolean isEnabled() {
    return configs.size() > 0;
  }

  @Override
  public void scheduleFullSync(final Project.NameKey project,
      final String urlMatch) {
    for (final ReplicationConfig cfg : configs) {
      for (final URIish uri : cfg.getURIs(project, urlMatch)) {
        cfg.schedule(project, PushOp.MIRROR_ALL, uri);
      }
    }
  }

  @Override
  public void scheduleUpdate(final Project.NameKey project, final String ref) {
    for (final ReplicationConfig cfg : configs) {
      if (cfg.wouldPushRef(ref)) {
        for (final URIish uri : cfg.getURIs(project, null)) {
          cfg.schedule(project, ref, uri);
        }
      }
    }
  }

  private static String replace(final String pat, final String key,
      final String val) {
    final int n = pat.indexOf("${" + key + "}");

    if (n != -1) {
      return pat.substring(0, n) + val + pat.substring(n + 3 + key.length());
    } else {
      return null;
    }
  }

  private List<ReplicationConfig> allConfigs(final SitePaths site)
      throws ConfigInvalidException, IOException {
    final FileBasedConfig cfg =
        new FileBasedConfig(site.replication_config, FS.DETECTED);

    if (!cfg.getFile().exists()) {
      log.warn("No " + cfg.getFile() + "; not replicating");
      return Collections.emptyList();
    }
    if (cfg.getFile().length() == 0) {
      log.info("Empty " + cfg.getFile() + "; not replicating");
      return Collections.emptyList();
    }

    try {
      cfg.load();
    } catch (ConfigInvalidException e) {
      throw new ConfigInvalidException("Config file " + cfg.getFile()
          + " is invalid: " + e.getMessage(), e);
    } catch (IOException e) {
      throw new IOException("Cannot read " + cfg.getFile() + ": "
          + e.getMessage(), e);
    }

    final List<ReplicationConfig> r = new ArrayList<ReplicationConfig>();
    for (final RemoteConfig c : allRemotes(cfg)) {
      if (c.getURIs().isEmpty()) {
        continue;
      }

      for (final URIish u : c.getURIs()) {
        if (u.getPath() == null || !u.getPath().contains("${name}")) {
          throw new ConfigInvalidException("remote." + c.getName() + ".url"
              + " \"" + u + "\" lacks ${name} placeholder in " + cfg.getFile());
        }
      }

      // In case if refspec destination for push is not set then we assume it is
      // equal to source
      for (RefSpec ref : c.getPushRefSpecs()) {
        if (ref.getDestination() == null) {
          ref.setDestination(ref.getSource());
        }
      }


      if (c.getPushRefSpecs().isEmpty()) {
        RefSpec spec = new RefSpec();
        spec = spec.setSourceDestination("refs/*", "refs/*");
        spec = spec.setForceUpdate(true);
        c.addPushRefSpec(spec);
      }

      r.add(new ReplicationConfig(injector, workQueue, c, cfg, database,
          replicationUserFactory));
    }
    return Collections.unmodifiableList(r);
  }

  private List<RemoteConfig> allRemotes(final FileBasedConfig cfg)
      throws ConfigInvalidException {
    List<String> names = new ArrayList<String>(cfg.getSubsections("remote"));
    Collections.sort(names);

    final List<RemoteConfig> result = new ArrayList<RemoteConfig>(names.size());
    for (final String name : names) {
      try {
        result.add(new RemoteConfig(cfg, name));
      } catch (URISyntaxException e) {
        throw new ConfigInvalidException("remote " + name
            + " has invalid URL in " + cfg.getFile());
      }
    }
    return result;
  }

  @Override
  public void replicateNewProject(Project.NameKey projectName, String head) {
    if (!isEnabled()) {
      return;
    }

    for (ReplicationConfig config : configs) {
      List<URIish> uriList = config.getURIs(projectName, "*");
      String[] adminUrls = config.getAdminUrls();
      boolean adminURLUsed = false;

      for (String url : adminUrls) {
        URIish adminURI = null;
        try {
          if (url != null && !url.isEmpty()) {
            adminURI = new URIish(url);
          }
        } catch (URISyntaxException e) {
          log.error("The URL '" + url + "' is invalid");
        }

        if (adminURI != null) {
          final String replacedPath =
              replace(adminURI.getPath(), "name", projectName.get());
          if (replacedPath != null) {
            adminURI = adminURI.setPath(replacedPath);
            if (usingSSH(adminURI)) {
              replicateProject(adminURI, head);
              adminURLUsed = true;
            } else {
              log.error("The adminURL '" + url
                  + "' is non-SSH which is not allowed");
            }
          }
        }
      }

      if (!adminURLUsed) {
        for (URIish uri : uriList) {
          replicateProject(uri, head);
        }
      }
    }
  }

  private void replicateProject(final URIish replicateURI, final String head) {
    SshSessionFactory sshFactory = SshSessionFactory.getInstance();
    RemoteSession sshSession;
    String projectPath = QuotedString.BOURNE.quote(replicateURI.getPath());

    if (!usingSSH(replicateURI)) {
      log.warn("Cannot create new project on remote site since the connection "
          + "method is not SSH: " + replicateURI.toString());
      return;
    }

    OutputStream errStream = createErrStream();
    String cmd =
        "mkdir -p " + projectPath + "&& cd " + projectPath
            + "&& git init --bare" + "&& git symbolic-ref HEAD "
            + QuotedString.BOURNE.quote(head);

    try {
      sshSession = sshFactory.getSession(replicateURI, null, FS.DETECTED, 0);
      Process proc = sshSession.exec(cmd, 0);
      proc.getOutputStream().close();
      StreamCopyThread out = new StreamCopyThread(proc.getInputStream(), errStream);
      StreamCopyThread err = new StreamCopyThread(proc.getErrorStream(), errStream);
      out.start();
      err.start();
      try {
        proc.waitFor();
        out.halt();
        err.halt();
      } catch (InterruptedException interrupted) {
        // Don't wait, drop out immediately.
      }
      sshSession.disconnect();
    } catch (IOException e) {
      log.error("Communication error when trying to replicate to: "
          + replicateURI.toString() + "\n" + "Error reported: "
          + e.getMessage() + "\n" + "Error in communication: "
          + errStream.toString());
    }
  }

  private OutputStream createErrStream() {
    return new OutputStream() {
      private StringBuilder all = new StringBuilder();
      private StringBuilder sb = new StringBuilder();

      @Override
      public String toString() {
        String r = all.toString();
        while (r.endsWith("\n"))
          r = r.substring(0, r.length() - 1);
        return r;
      }

      @Override
      public synchronized void write(final int b) throws IOException {
        if (b == '\r') {
          return;
        }

        sb.append((char) b);

        if (b == '\n') {
          all.append(sb);
          sb.setLength(0);
        }
      }
    };
  }

  private boolean usingSSH(final URIish uri) {
    final String scheme = uri.getScheme();
    if (!uri.isRemote()) return false;
    if (scheme != null && scheme.toLowerCase().contains("ssh")) return true;
    if (scheme == null && uri.getHost() != null && uri.getPath() != null)
      return true;
    return false;
  }

  static class ReplicationConfig {
    private final RemoteConfig remote;
    private final String[] adminUrls;
    private final int delay;
    private final int retryDelay;
    private final WorkQueue.Executor pool;
    private final Map<URIish, PushOp> pending = new HashMap<URIish, PushOp>();
    private final PushOp.Factory opFactory;
    private final ProjectControl.Factory projectControlFactory;

    ReplicationConfig(final Injector injector, final WorkQueue workQueue,
        final RemoteConfig rc, final Config cfg, SchemaFactory<ReviewDb> db,
        final ReplicationUser.Factory replicationUserFactory) {

      remote = rc;
      delay = Math.max(0, getInt(rc, cfg, "replicationdelay", 15));
      retryDelay = Math.max(0, getInt(rc, cfg, "replicationretry", 1));

      final int poolSize = Math.max(0, getInt(rc, cfg, "threads", 1));
      final String poolName = "ReplicateTo-" + rc.getName();
      pool = workQueue.createQueue(poolSize, poolName);

      String[] authGroupNames =
          cfg.getStringList("remote", rc.getName(), "authGroup");
      final Set<AccountGroup.UUID> authGroups;
      if (authGroupNames.length > 0) {
        authGroups = ConfigUtil.groupsFor(db, authGroupNames, //
            log, "Group \"{0}\" not in database, removing from authGroup");
      } else {
        authGroups = ReplicationUser.EVERYTHING_VISIBLE;
      }

      adminUrls = cfg.getStringList("remote", rc.getName(), "adminUrl");

      final ReplicationUser remoteUser =
          replicationUserFactory.create(authGroups);

      projectControlFactory =
          injector.createChildInjector(new AbstractModule() {
            @Override
            protected void configure() {
              bind(CurrentUser.class).toInstance(remoteUser);
            }
          }).getInstance(ProjectControl.Factory.class);

      opFactory = injector.createChildInjector(new AbstractModule() {
        @Override
        protected void configure() {
          bind(PushReplication.ReplicationConfig.class).toInstance(
              ReplicationConfig.this);
          bind(RemoteConfig.class).toInstance(remote);
          bind(PushOp.Factory.class).toProvider(
              FactoryProvider.newFactory(PushOp.Factory.class, PushOp.class));
        }
      }).getInstance(PushOp.Factory.class);
    }

    private int getInt(final RemoteConfig rc, final Config cfg,
        final String name, final int defValue) {
      return cfg.getInt("remote", rc.getName(), name, defValue);
    }

    void schedule(final Project.NameKey project, final String ref,
        final URIish uri) {
      try {
        if (!controlFor(project).isVisible()) {
          return;
        }
      } catch (NoSuchProjectException e1) {
        log.error("Internal error: project " + project
            + " not found during replication");
        return;
      }
      synchronized (pending) {
        PushOp e = pending.get(uri);
        if (e == null) {
          e = opFactory.create(project, uri);
          pool.schedule(e, delay, TimeUnit.SECONDS);
          pending.put(uri, e);
        }
        e.addRef(ref);
      }
    }

    /**
     * It schedules again a PushOp instance.
     * <p>
     * It is assumed to be previously scheduled and found a
     * transport exception. It will schedule it as a push
     * operation to be retried after the minutes count
     * determined by class attribute retryDelay.
     * <p>
     * In case the PushOp instance to be scheduled has same
     * URI than one also pending for retry, it adds to the one
     * pending the refs list of the parameter instance.
     * <p>
     * In case the PushOp instance to be scheduled has same
     * URI than one pending, but not pending for retry, it
     * indicates the one pending should be canceled when it
     * starts executing, removes it from pending list, and
     * adds its refs to the parameter instance. The parameter
     * instance is scheduled for retry.
     * <p>
     * Notice all operations to indicate a PushOp should be
     * canceled, or it is retrying, or remove/add it from/to
     * pending Map should be protected by the lock on pending
     * Map class instance attribute.
     *
     * @param pushOp The PushOp instance to be scheduled.
     */
    void reschedule(final PushOp pushOp) {
      try {
        if (!controlFor(pushOp.getProjectNameKey()).isVisible()) {
          return;
        }
      } catch (NoSuchProjectException e1) {
        log.error("Internal error: project " + pushOp.getProjectNameKey()
            + " not found during replication");
        return;
      }

      // It locks access to pending variable.
      synchronized (pending) {
        URIish uri = pushOp.getURI();
        PushOp pendingPushOp = pending.get(uri);

        if (pendingPushOp != null) {
          // There is one PushOp instance already pending to same URI.

          if (pendingPushOp.isRetrying()) {
            // The one pending is one already retrying, so it should
            // maintain it and add to it the refs of the one passed
            // as parameter to the method.

            // This scenario would happen if a PushOp has started running
            // and then before it failed due transport exception, another
            // one to same URI started. The first one would fail and would
            // be rescheduled, being present in pending list. When the
            // second one fails, it will also be rescheduled and then,
            // here, find out replication to its URI is already pending
            // for retry (blocking).
            pendingPushOp.addRefs(pushOp.getRefs());

          } else {
            // The one pending is one that is NOT retrying, it was just
            // scheduled believing no problem would happen. The one pending
            // should be canceled, and this is done by setting its canceled
            // flag, removing it from pending list, and adding its refs to
            // the pushOp instance that should then, later, in this method,
            // be scheduled for retry.

            // Notice that the PushOp found pending will start running and,
            // when notifying it is starting (with pending lock protection),
            // it will see it was canceled and then it will do nothing with
            // pending list and it will not execute its run implementation.

            pendingPushOp.cancel();
            pending.remove(uri);

            pushOp.addRefs(pendingPushOp.getRefs());
          }
        }

        if (pendingPushOp == null || !pendingPushOp.isRetrying()) {
          // The PushOp method param instance should be scheduled for retry.
          // Remember when retrying it should be used different delay.

          pushOp.setToRetry();

          pending.put(uri, pushOp);
          pool.schedule(pushOp, retryDelay, TimeUnit.MINUTES);
        }
      }
    }

    ProjectControl controlFor(final Project.NameKey project)
        throws NoSuchProjectException {
      return projectControlFactory.controlFor(project);
    }

    void notifyStarting(final PushOp op) {
      synchronized (pending) {
        if (!op.wasCanceled()) {
          pending.remove(op.getURI());
        }
      }
    }

    boolean wouldPushRef(final String ref) {
      for (final RefSpec s : remote.getPushRefSpecs()) {
        if (s.matchSource(ref)) {
          return true;
        }
      }
      return false;
    }

    List<URIish> getURIs(final Project.NameKey project, final String urlMatch) {
      final List<URIish> r = new ArrayList<URIish>(remote.getURIs().size());
      for (URIish uri : remote.getURIs()) {
        if (matches(uri, urlMatch)) {
          String name = project.get();
          if (needsUrlEncoding(uri)) {
            name = encode(name);
          }
          String replacedPath = replace(uri.getPath(), "name", name);
          if (replacedPath != null) {
            uri = uri.setPath(replacedPath);
            r.add(uri);
          }
        }
      }
      return r;
    }

    static boolean needsUrlEncoding(URIish uri) {
      return "http".equalsIgnoreCase(uri.getScheme())
        || "https".equalsIgnoreCase(uri.getScheme())
        || "amazon-s3".equalsIgnoreCase(uri.getScheme());
    }

    static String encode(String str) {
      try {
        // Some cleanup is required. The '/' character is always encoded as %2F
        // however remote servers will expect it to be not encoded as part of the
        // path used to the repository. Space is incorrectly encoded as '+' for this
        // context. In the path part of a URI space should be %20, but in form data
        // space is '+'. Our cleanup replace fixes these two issues.
        return URLEncoder.encode(str, "UTF-8")
          .replaceAll("%2[fF]", "/")
          .replace("+", "%20");
      } catch (UnsupportedEncodingException e) {
        throw new RuntimeException(e);
      }
    }

    String[] getAdminUrls() {
      return this.adminUrls;
    }

    private boolean matches(URIish uri, final String urlMatch) {
      if (urlMatch == null || urlMatch.equals("") || urlMatch.equals("*")) {
        return true;
      }
      return uri.toString().contains(urlMatch);
    }
  }
}
