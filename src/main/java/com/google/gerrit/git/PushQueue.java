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
import com.google.gerrit.server.GerritServer;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import com.jcraft.jsch.Session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.lib.RepositoryConfig;
import org.spearce.jgit.transport.OpenSshConfig;
import org.spearce.jgit.transport.RefSpec;
import org.spearce.jgit.transport.RemoteConfig;
import org.spearce.jgit.transport.SshConfigSessionFactory;
import org.spearce.jgit.transport.SshSessionFactory;
import org.spearce.jgit.transport.URIish;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Manages automatic replication to remote repositories. */
public class PushQueue {
  static final Logger log = LoggerFactory.getLogger(PushQueue.class);
  private static List<ReplicationConfig> configs;
  private static final Map<URIish, PushOp> pending =
      new HashMap<URIish, PushOp>();

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

  /** Determine if replication is enabled, or not. */
  public static boolean isReplicationEnabled() {
    return !allConfigs().isEmpty();
  }

  /**
   * Schedule a full replication for a single project.
   * <p>
   * All remote URLs are checked to verify the are current with regards to the
   * local project state. If not, they are updated by pushing new refs, updating
   * existing ones which don't match, and deleting stale refs which have been
   * removed from the local repository.
   * 
   * @param project identity of the project to replicate.
   * @param urlMatch substring that must appear in a URI to support replication.
   */
  public static void scheduleFullSync(final Project.NameKey project,
      final String urlMatch) {
    for (final ReplicationConfig cfg : allConfigs()) {
      for (final URIish uri : cfg.getURIs(project, urlMatch)) {
        scheduleImp(project, PushOp.MIRROR_ALL, cfg, uri);
      }
    }
  }

  /**
   * Schedule update of a single ref.
   * <p>
   * This method automatically tries to batch together multiple requests in the
   * same project, to take advantage of Git's native ability to update multiple
   * refs during a single push operation.
   * 
   * @param project identity of the project to replicate.
   * @param ref unique name of the ref; must start with {@code refs/}.
   */
  public static void scheduleUpdate(final Project.NameKey project,
      final String ref) {
    for (final ReplicationConfig cfg : allConfigs()) {
      if (cfg.wouldPushRef(ref)) {
        for (final URIish uri : cfg.getURIs(project, null)) {
          scheduleImp(project, ref, cfg, uri);
        }
      }
    }
  }

  private static synchronized void scheduleImp(final Project.NameKey project,
      final String ref, final ReplicationConfig config, final URIish uri) {
    PushOp e = pending.get(uri);
    if (e == null) {
      e = new PushOp(project.get(), config.remote, uri);
      WorkQueue.schedule(e, config.delay, TimeUnit.SECONDS);
      pending.put(uri, e);
    }
    e.addRef(ref);
  }

  static synchronized void notifyStarting(final PushOp op) {
    pending.remove(op.getURI());
  }

  private static String replace(final String pat, final String key,
      final String val) {
    final int n = pat.indexOf("${" + key + "}");
    return pat.substring(0, n) + val + pat.substring(n + 3 + key.length());
  }

  private static synchronized List<ReplicationConfig> allConfigs() {
    if (configs == null) {
      final File path;
      try {
        final GerritServer gs = GerritServer.getInstance();
        path = gs.getSitePath();
        if (path == null || gs.getRepositoryCache() == null) {
          return Collections.emptyList();
        }
      } catch (OrmException e) {
        return Collections.emptyList();
      } catch (XsrfException e) {
        return Collections.emptyList();
      }

      final File cfgFile = new File(path, "replication.config");
      final RepositoryConfig cfg = new RepositoryConfig(null, cfgFile);
      try {
        cfg.load();

        final List<ReplicationConfig> r = new ArrayList<ReplicationConfig>();
        for (final RemoteConfig c : RemoteConfig.getAllRemoteConfigs(cfg)) {
          if (c.getURIs().isEmpty()) {
            continue;
          }

          for (final URIish u : c.getURIs()) {
            if (u.getPath() == null || !u.getPath().contains("${name}")) {
              final String s = u.toString();
              throw new URISyntaxException(s, "No ${name}");
            }
          }

          if (c.getPushRefSpecs().isEmpty()) {
            RefSpec spec = new RefSpec();
            spec = spec.setSourceDestination("refs/*", "refs/*");
            spec = spec.setForceUpdate(true);
            c.addPushRefSpec(spec);
          }

          r.add(new ReplicationConfig(c, cfg));
        }
        configs = Collections.unmodifiableList(r);
      } catch (FileNotFoundException e) {
        log.warn("No " + cfgFile + "; not replicating");
        configs = Collections.emptyList();
      } catch (IOException e) {
        log.error("Can't read " + cfgFile, e);
        return Collections.emptyList();
      } catch (URISyntaxException e) {
        log.error("Invalid URI in " + cfgFile + ": " + e.getMessage());
        return Collections.emptyList();
      }
    }
    return configs;
  }

  private static class ReplicationConfig {
    final RemoteConfig remote;
    final int delay;

    ReplicationConfig(final RemoteConfig rc, final RepositoryConfig cfg) {
      remote = rc;
      delay = posInt(rc, cfg, "replicationdelay", 15);
    }

    private static int posInt(final RemoteConfig rc,
        final RepositoryConfig cfg, final String name, final int defValue) {
      return Math.max(0, cfg.getInt("remote", rc.getName(), name, defValue));
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

    private static boolean matches(URIish uri, final String urlMatch) {
      if (urlMatch == null || urlMatch.equals("") || urlMatch.equals("*")) {
        return true;
      }
      return uri.toString().contains(urlMatch);
    }
  }
}
