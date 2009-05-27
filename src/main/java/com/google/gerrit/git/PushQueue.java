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

public class PushQueue {
  static final Logger log = LoggerFactory.getLogger(PushQueue.class);
  private static final int startDelay = 15; // seconds
  private static List<RemoteConfig> configs;
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

  public static void scheduleUpdate(final Project.NameKey project,
      final String ref) {
    for (final RemoteConfig cfg : allConfigs()) {
      if (wouldPushRef(cfg, ref)) {
        for (final URIish uri : cfg.getURIs()) {
          scheduleImp(project, ref, cfg, expandURI(uri, project));
        }
      }
    }
  }

  private static boolean wouldPushRef(final RemoteConfig cfg, final String ref) {
    for (final RefSpec s : cfg.getPushRefSpecs()) {
      if (s.matchSource(ref)) {
        return true;
      }
    }
    return false;
  }

  private static URIish expandURI(URIish uri, final Project.NameKey project) {
    uri = uri.setPath(replace(uri.getPath(), "name", project.get()));
    return uri;
  }

  private static synchronized void scheduleImp(final Project.NameKey project,
      final String ref, final RemoteConfig srcConf, final URIish uri) {
    PushOp e = pending.get(uri);
    if (e == null) {
      e = new PushOp(project.get(), srcConf, uri);
      WorkQueue.schedule(e, startDelay, TimeUnit.SECONDS);
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

  private static synchronized List<RemoteConfig> allConfigs() {
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
        final ArrayList<RemoteConfig> r = new ArrayList<RemoteConfig>();
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

          r.add(c);
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
}
