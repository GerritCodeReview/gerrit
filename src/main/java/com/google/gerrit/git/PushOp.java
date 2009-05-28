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
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.NullProgressMonitor;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.transport.FetchConnection;
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
import java.util.Map;
import java.util.Set;

/**
 * A push to remote operation started by {@link PushQueue}.
 * <p>
 * Instance members are protected by the lock within PushQueue. Callers must
 * take that lock to ensure they are working with a current view of the object.
 */
class PushOp implements Runnable {
  private static final Logger log = PushQueue.log;
  static final String MIRROR_ALL = "..all..";

  private final Set<String> delta = new HashSet<String>();
  private final String projectName;
  private final RemoteConfig config;
  private final URIish uri;
  private boolean mirror;

  private Repository db;

  PushOp(final String d, final RemoteConfig c, final URIish u) {
    projectName = d;
    config = c;
    uri = u;
  }

  URIish getURI() {
    return uri;
  }

  void addRef(final String ref) {
    if (MIRROR_ALL.equals(ref)) {
      delta.clear();
      mirror = true;
    } else if (!mirror) {
      delta.add(ref);
    }
  }

  public void run() {
    try {
      // Lock the queue, and remove ourselves, so we can't be modified once
      // we start replication (instead a new instance, with the same URI, is
      // created and scheduled for a future point in time.)
      //
      PushQueue.notifyStarting(this);
      openRepository();
      runImpl();
    } catch (OrmException e) {
      log.error("Cannot open database", e);

    } catch (XsrfException e) {
      log.error("Cannot open database", e);

    } catch (InvalidRepositoryException e) {
      log.error("Cannot replicate " + projectName + "; " + e.getMessage());

    } catch (NoRemoteRepositoryException e) {
      log.error("Cannot replicate to " + uri + "; repository not found");

    } catch (NotSupportedException e) {
      log.error("Cannot replicate to " + uri, e);

    } catch (TransportException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof JSchException
          && cause.getMessage().startsWith("UnknownHostKey:")) {
        log.error("Cannot replicate to " + uri + ": " + cause.getMessage());
      } else {
        log.error("Cannot replicate to " + uri, e);
      }

    } catch (IOException e) {
      log.error("Cannot replicate to " + uri, e);

    } catch (RuntimeException e) {
      log.error("Unexpected error during replication to " + uri, e);

    } catch (Error e) {
      log.error("Unexpected error during replication to " + uri, e);

    }
  }

  @Override
  public String toString() {
    return (mirror ? "mirror " : "push ") + uri;
  }

  private void openRepository() throws InvalidRepositoryException,
      OrmException, XsrfException {
    db = GerritServer.getInstance().getRepositoryCache().get(projectName);
  }

  private void runImpl() throws IOException {
    final Transport tn = Transport.open(db, uri);
    final PushResult res;
    try {
      res = pushVia(tn);
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

  private PushResult pushVia(final Transport tn) throws IOException,
      NotSupportedException, TransportException {
    tn.applyConfig(config);

    final List<RemoteRefUpdate> todo = generateUpdates(tn);
    if (todo.isEmpty()) {
      // If we have no commands selected, we have nothing to do.
      // Calling JGit at this point would just redo the work we
      // already did, and come up with the same answer. Instead
      // send back an empty result.
      //
      return new PushResult();
    }

    return tn.push(NullProgressMonitor.INSTANCE, todo);
  }

  private List<RemoteRefUpdate> generateUpdates(final Transport tn)
      throws IOException {
    final List<RemoteRefUpdate> cmds = new ArrayList<RemoteRefUpdate>();
    final Map<String, Ref> local = db.getAllRefs();

    if (mirror) {
      final Map<String, Ref> remote = listRemote(tn);

      for (final Ref src : local.values()) {
        final RefSpec spec = matchSrc(src.getOrigName());
        if (spec != null) {
          final Ref dst = remote.get(spec.getDestination());
          if (dst == null || !src.getObjectId().equals(dst.getObjectId())) {
            // Doesn't exist yet, or isn't the same value, request to push.
            //
            send(cmds, spec);
          }
        }
      }

      for (final Ref ref : remote.values()) {
        if (!isHEAD(ref)) {
          final RefSpec spec = matchDst(ref.getName());
          if (spec != null && !local.containsKey(spec.getSource())) {
            // No longer on local side, request removal.
            //
            delete(cmds, spec);
          }
        }
      }

    } else {
      for (final String src : delta) {
        final RefSpec spec = matchSrc(src);
        if (spec != null) {
          // If the ref still exists locally, send it, otherwise delete it.
          //
          if (local.containsKey(src)) {
            send(cmds, spec);
          } else {
            delete(cmds, spec);
          }
        }
      }
    }

    return cmds;
  }

  private Map<String, Ref> listRemote(final Transport tn)
      throws NotSupportedException, TransportException {
    final FetchConnection fc = tn.openFetch();
    try {
      return fc.getRefsMap();
    } finally {
      fc.close();
    }
  }

  private RefSpec matchSrc(final String ref) {
    for (final RefSpec s : config.getPushRefSpecs()) {
      if (s.matchSource(ref)) {
        return s.expandFromSource(ref);
      }
    }
    return null;
  }

  private RefSpec matchDst(final String ref) {
    for (final RefSpec s : config.getPushRefSpecs()) {
      if (s.matchDestination(ref)) {
        return s.expandFromDestination(ref);
      }
    }
    return null;
  }

  private void send(final List<RemoteRefUpdate> cmds, final RefSpec spec)
      throws IOException {
    final String src = spec.getSource();
    final String dst = spec.getDestination();
    final boolean force = spec.isForceUpdate();
    cmds.add(new RemoteRefUpdate(db, src, dst, force, null, null));
  }

  private void delete(final List<RemoteRefUpdate> cmds, final RefSpec spec)
      throws IOException {
    final String dst = spec.getDestination();
    final boolean force = spec.isForceUpdate();
    cmds.add(new RemoteRefUpdate(db, null, dst, force, null, null));
  }

  private static boolean isHEAD(final Ref ref) {
    return Constants.HEAD.equals(ref.getOrigName());
  }
}
