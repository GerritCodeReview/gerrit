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

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import org.eclipse.jgit.errors.ConfigInvalidException;
import org.eclipse.jgit.lib.Config;
import org.eclipse.jgit.lib.FileBasedConfig;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.SshConfigSessionFactory;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.URIish;
import org.eclipse.jgit.util.QuotedString;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
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
    SshSessionFactory.setInstance(new SshConfigSessionFactory() {
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
    return pat.substring(0, n) + val + pat.substring(n + 3 + key.length());
  }

  private List<ReplicationConfig> allConfigs(final SitePaths site)
      throws ConfigInvalidException, IOException {
    final FileBasedConfig cfg = new FileBasedConfig(site.replication_config);

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

  public void replicateNewProject(Project.NameKey projectName, String head) {
    if (!isEnabled()) {
      return;
    }

    Iterator<ReplicationConfig> configIter = configs.iterator();

    while (configIter.hasNext()) {
      ReplicationConfig rp = configIter.next();
      List<URIish> uriList = rp.getURIs(projectName, "*");

      Iterator<URIish> uriIter = uriList.iterator();

      while (uriIter.hasNext()) {
        replicateProject(uriIter.next(), head);
      }
    }
  }

  private void replicateProject(final URIish replicateURI, final String head) {
    SshSessionFactory sshFactory = SshSessionFactory.getInstance();
    Session sshSession;
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
      sshSession =
          sshFactory.getSession(replicateURI.getUser(), replicateURI.getPass(),
              replicateURI.getHost(), replicateURI.getPort());
      sshSession.connect();

      Channel channel = sshSession.openChannel("exec");
      ((ChannelExec) channel).setCommand(cmd);

      channel.setInputStream(null);

      ((ChannelExec) channel).setErrStream(errStream);

      channel.connect();

      while (!channel.isClosed()) {
        try {
          final int delay = 50;
          Thread.sleep(delay);
        } catch (InterruptedException e) {
        }
      }
      channel.disconnect();
      sshSession.disconnect();
    } catch (JSchException e) {
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

      public String toString() {
        String r = all.toString();
        while (r.endsWith("\n"))
          r = r.substring(0, r.length() - 1);
        return r;
      }

      @Override
      public void write(final int b) throws IOException {
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
    private final int delay;
    private final WorkQueue.Executor pool;
    private final Map<URIish, PushOp> pending = new HashMap<URIish, PushOp>();
    private final PushOp.Factory opFactory;
    private final ProjectControl.Factory projectControlFactory;
    private final boolean authEnabled;

    ReplicationConfig(final Injector injector, final WorkQueue workQueue,
        final RemoteConfig rc, final Config cfg, SchemaFactory<ReviewDb> db,
        final ReplicationUser.Factory replicationUserFactory) {

      remote = rc;
      delay = Math.max(0, getInt(rc, cfg, "replicationdelay", 15));

      final int poolSize = Math.max(0, getInt(rc, cfg, "threads", 1));
      final String poolName = "ReplicateTo-" + rc.getName();
      pool = workQueue.createQueue(poolSize, poolName);

      String[] authGroupNames =
          cfg.getStringList("remote", rc.getName(), "authGroup");
      authEnabled = authGroupNames.length > 0;
      Set<AccountGroup.Id> authGroups = ConfigUtil.groupsFor(db, authGroupNames, log,
              "Group \"{0}\" not in database, removing from authGroup");

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
        if (authEnabled
            && !projectControlFactory.controlFor(project).isVisible()) {
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
          e = opFactory.create(project.get(), uri);
          pool.schedule(e, delay, TimeUnit.SECONDS);
          pending.put(uri, e);
        }
        e.addRef(ref);
      }
    }

    void notifyStarting(final PushOp op) {
      synchronized (pending) {
        pending.remove(op.getURI());
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
          uri = uri.setPath(replace(uri.getPath(), "name", project.get()));
          r.add(uri);
        }
      }
      return r;
    }

    private boolean matches(URIish uri, final String urlMatch) {
      if (urlMatch == null || urlMatch.equals("") || urlMatch.equals("*")) {
        return true;
      }
      return uri.toString().contains(urlMatch);
    }
  }
}
