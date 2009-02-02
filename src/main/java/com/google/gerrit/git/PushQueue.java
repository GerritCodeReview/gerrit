// Copyright 2009 Google Inc.
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

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.spearce.jgit.errors.NotSupportedException;
import org.spearce.jgit.errors.TransportException;
import org.spearce.jgit.lib.NullProgressMonitor;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.RepositoryConfig;
import org.spearce.jgit.transport.PushResult;
import org.spearce.jgit.transport.RefSpec;
import org.spearce.jgit.transport.RemoteConfig;
import org.spearce.jgit.transport.RemoteRefUpdate;
import org.spearce.jgit.transport.Transport;
import org.spearce.jgit.transport.URIish;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class PushQueue {
  private static final Logger log = LoggerFactory.getLogger(PushQueue.class);
  private static final int startDelay = 15; // seconds
  private static List<RemoteConfig> configs;
  private static final Map<URIish, PushOp> active =
      new HashMap<URIish, PushOp>();

  public static void scheduleUpdate(final Project.NameKey project,
      final String ref) {
    for (final RemoteConfig srcConf : allConfigs()) {
      RefSpec spec = null;
      for (final RefSpec s : srcConf.getPushRefSpecs()) {
        if (s.matchSource(ref)) {
          spec = s;
          break;
        }
      }
      if (spec == null) {
        continue;
      }

      for (URIish uri : srcConf.getURIs()) {
        uri = uri.setPath(replace(uri.getPath(), "name", project.get()));
        scheduleImp(project, ref, srcConf, uri);
      }
    }
  }

  private static synchronized void scheduleImp(final Project.NameKey project,
      final String ref, final RemoteConfig srcConf, final URIish uri) {
    PushOp e = active.get(uri);
    if (e == null) {
      final PushOp newOp = new PushOp(project.get(), srcConf, uri);
      WorkQueue.schedule(new Runnable() {
        public void run() {
          try {
            pushImpl(newOp);
          } catch (RuntimeException e) {
            log.error("Unexpected error during replication", e);
          } catch (Error e) {
            log.error("Unexpected error during replication", e);
          }
        }
      }, startDelay, TimeUnit.SECONDS);
      active.put(uri, newOp);
      e = newOp;
    }
    e.delta.add(ref);
  }

  private static void pushImpl(final PushOp op) {
    removeFromActive(op);
    final Repository db;
    try {
      db = GerritServer.getInstance().getRepositoryCache().get(op.projectName);
    } catch (OrmException e) {
      log.error("Cannot open repository cache", e);
      return;
    } catch (XsrfException e) {
      log.error("Cannot open repository cache", e);
      return;
    } catch (InvalidRepositoryException e) {
      log.error("Cannot replicate " + op.projectName, e);
      return;
    }

    final ArrayList<RemoteRefUpdate> cmds = new ArrayList<RemoteRefUpdate>();
    try {
      for (final String ref : op.delta) {
        final String src = ref;
        RefSpec spec = null;
        for (final RefSpec s : op.config.getPushRefSpecs()) {
          if (s.matchSource(src)) {
            spec = s.expandFromSource(src);
            break;
          }
        }
        if (spec == null) {
          continue;
        }

        // If the ref still exists locally, send it, else delete it.
        //
        final String srcexp = db.resolve(src) != null ? src : null;
        final String dst = spec.getDestination();
        final boolean force = spec.isForceUpdate();
        cmds.add(new RemoteRefUpdate(db, srcexp, dst, force, null, null));
      }
    } catch (IOException e) {
      log.error("Cannot replicate " + op.projectName, e);
      return;
    }

    final Transport tn;
    try {
      tn = Transport.open(db, op.uri);
      tn.applyConfig(op.config);
    } catch (NotSupportedException e) {
      log.error("Cannot replicate to " + op.uri, e);
      return;
    }

    final PushResult res;
    try {
      res = tn.push(NullProgressMonitor.INSTANCE, cmds);
    } catch (NotSupportedException e) {
      log.error("Cannot replicate to " + op.uri, e);
      return;
    } catch (TransportException e) {
      log.error("Cannot replicate to " + op.uri, e);
      return;
    } finally {
      tn.close();
    }

    for (final RemoteRefUpdate u : res.getRemoteUpdates()) {
      switch (u.getStatus()) {
        case OK:
        case UP_TO_DATE:
        case NON_EXISTING:
          break;

        case NOT_ATTEMPTED:
        case AWAITING_REPORT:
        case REJECTED_NODELETE:
        case REJECTED_NONFASTFORWARD:
        case REJECTED_REMOTE_CHANGED:
          log.error("Failed replicate of " + u.getRemoteName() + " to "
              + op.uri + ": status " + u.getStatus().name());
          break;

        case REJECTED_OTHER_REASON:
          log.error("Failed replicate of " + u.getRemoteName() + " to "
              + op.uri + ", reason: " + u.getMessage());
          break;
      }
    }
  }

  private static synchronized void removeFromActive(final PushOp op) {
    active.remove(op.uri);
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
        log.error("Invalid URI in " + cfgFile, e);
        return Collections.emptyList();
      }
    }
    return configs;
  }

  private static class PushOp {
    final Set<String> delta = new HashSet<String>();
    final String projectName;
    final RemoteConfig config;
    final URIish uri;

    PushOp(final String d, final RemoteConfig c, final URIish u) {
      projectName = d;
      config = c;
      uri = u;
    }
  }
}
