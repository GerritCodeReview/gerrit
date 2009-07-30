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

package com.google.gerrit.git;

import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.server.config.SitePath;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.google.inject.assistedinject.FactoryProvider;

import com.jcraft.jsch.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.errors.ConfigInvalidException;
import org.spearce.jgit.lib.Config;
import org.spearce.jgit.lib.FileBasedConfig;
import org.spearce.jgit.transport.OpenSshConfig;
import org.spearce.jgit.transport.RefSpec;
import org.spearce.jgit.transport.RemoteConfig;
import org.spearce.jgit.transport.SshConfigSessionFactory;
import org.spearce.jgit.transport.SshSessionFactory;
import org.spearce.jgit.transport.URIish;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Manages automatic replication to remote repositories. */
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
  private final List<ReplicationConfig> configs;

  @Inject
  PushReplication(final Injector i, @SitePath final File sitePath)
      throws ConfigInvalidException, IOException {
    injector = i;
    configs = allConfigs(sitePath);
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

  private List<ReplicationConfig> allConfigs(final File path)
      throws ConfigInvalidException, IOException {
    final File cfgFile = new File(path, "replication.config");
    final FileBasedConfig cfg = new FileBasedConfig(cfgFile);

    if (!cfg.getFile().exists()) {
      log.warn("No " + cfg.getFile() + "; not replicating");
      return Collections.emptyList();
    }

    try {
      cfg.load();
    } catch (ConfigInvalidException e) {
      throw new ConfigInvalidException("Config file " + cfg.getFile()
          + " is invalid: " + e.getMessage(), e);
    } catch (IOException e) {
      throw new IOException("Cannot read " + cfgFile + ": " + e.getMessage(), e);
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

      r.add(new ReplicationConfig(injector, c, cfg));
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

  static class ReplicationConfig {
    private final RemoteConfig remote;
    private final int delay;
    private final WorkQueue.Executor pool;
    private final Map<URIish, PushOp> pending = new HashMap<URIish, PushOp>();
    private final PushOp.Factory opFactory;

    ReplicationConfig(final Injector injector, final RemoteConfig rc,
        final Config cfg) {
      remote = rc;
      delay = Math.max(0, getInt(rc, cfg, "replicationdelay", 15));

      final int poolSize = Math.max(0, getInt(rc, cfg, "threads", 1));
      final String poolName = "ReplicateTo-" + rc.getName();
      pool = WorkQueue.createQueue(poolSize, poolName);

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
