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

import com.google.gerrit.server.GerritServer;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import com.jcraft.jsch.JSchException;

import org.slf4j.Logger;
import org.spearce.jgit.errors.NoRemoteRepositoryException;
import org.spearce.jgit.errors.NotSupportedException;
import org.spearce.jgit.errors.TransportException;
import org.spearce.jgit.lib.NullProgressMonitor;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.transport.PushResult;
import org.spearce.jgit.transport.RefSpec;
import org.spearce.jgit.transport.RemoteConfig;
import org.spearce.jgit.transport.RemoteRefUpdate;
import org.spearce.jgit.transport.Transport;
import org.spearce.jgit.transport.URIish;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * A push to remote operation started by {@link PushQueue}.
 * <p>
 * Instance members are protected by the lock within PushQueue. Callers must
 * take that lock to ensure they are working with a current view of the object.
 */
class PushOp implements Runnable {
  private static final Logger log = PushQueue.log;

  private final Set<String> delta = new HashSet<String>();
  private final String projectName;
  private final RemoteConfig config;
  private final URIish uri;

  PushOp(final String d, final RemoteConfig c, final URIish u) {
    projectName = d;
    config = c;
    uri = u;
  }

  URIish getURI() {
    return uri;
  }

  void addRef(final String ref) {
    delta.add(ref);
  }

  public void run() {
    try {
      // Lock the queue, and remove ourselves, so we can't be modified once
      // we start replication (instead a new instance, with the same URI, is
      // created and scheduled for a future point in time.)
      //
      PushQueue.notifyStarting(this);
      pushImpl();
    } catch (RuntimeException e) {
      log.error("Unexpected error during replication", e);
    } catch (Error e) {
      log.error("Unexpected error during replication", e);
    }
  }

  @Override
  public String toString() {
    return "push " + uri;
  }

  private void pushImpl() {
    final Repository db;
    try {
      db = GerritServer.getInstance().getRepositoryCache().get(projectName);
    } catch (OrmException e) {
      log.error("Cannot open repository cache", e);
      return;
    } catch (XsrfException e) {
      log.error("Cannot open repository cache", e);
      return;
    } catch (InvalidRepositoryException e) {
      log.error("Cannot replicate " + projectName, e);
      return;
    }

    final Transport tn;
    try {
      tn = Transport.open(db, uri);
      tn.applyConfig(config);
    } catch (NotSupportedException e) {
      log.error("Cannot replicate to " + uri, e);
      return;
    }

    final PushResult res;
    try {
      res = tn.push(NullProgressMonitor.INSTANCE, computeUpdates(db));
    } catch (NoRemoteRepositoryException e) {
      log.error("Cannot replicate to " + uri + "; repository not found");
      return;
    } catch (NotSupportedException e) {
      log.error("Cannot replicate to " + uri, e);
      return;
    } catch (TransportException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof JSchException
          && cause.getMessage().startsWith("UnknownHostKey:")) {
        log.error("Cannot replicate to " + uri + ": " + cause.getMessage());
        return;
      }
      log.error("Cannot replicate to " + uri, e);
      return;
    } catch (IOException e) {
      log.error("Cannot replicate to " + uri, e);
      return;
    } finally {
      try {
        tn.close();
      } catch (Throwable e2) {
        log.warn("Unexpected error while closing " + uri, e2);
      }
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
          log.error("Failed replicate of " + u.getRemoteName() + " to " + uri
              + ": status " + u.getStatus().name());
          break;

        case REJECTED_OTHER_REASON:
          log.error("Failed replicate of " + u.getRemoteName() + " to " + uri
              + ", reason: " + u.getMessage());
          break;
      }
    }
  }

  private List<RemoteRefUpdate> computeUpdates(final Repository db)
      throws IOException {
    final ArrayList<RemoteRefUpdate> cmds = new ArrayList<RemoteRefUpdate>();
    for (final String ref : delta) {
      final String src = ref;
      RefSpec spec = null;
      for (final RefSpec s : config.getPushRefSpecs()) {
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
    return cmds;
  }
}
