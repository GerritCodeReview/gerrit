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

import com.google.gerrit.reviewdb.Project;
import com.google.gerrit.reviewdb.Project.NameKey;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import com.jcraft.jsch.JSchException;

import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A push to remote operation started by {@link ReplicationQueue}.
 * <p>
 * Instance members are protected by the lock within PushQueue. Callers must
 * take that lock to ensure they are working with a current view of the object.
 */
class PushOp implements ProjectRunnable {
  interface Factory {
    PushOp create(Project.NameKey d, URIish u);
  }

  private static final Logger log = PushReplication.log;
  static final String MIRROR_ALL = "..all..";

  private final GitRepositoryManager repoManager;
  private final SchemaFactory<ReviewDb> schema;
  private final PushReplication.ReplicationConfig pool;
  private final RemoteConfig config;
  private final CredentialsProvider credentialsProvider;

  private final Set<String> delta = new HashSet<String>();
  private final Project.NameKey projectName;
  private final URIish uri;
  private boolean mirror;

  private Repository db;

  /**
   * It indicates if the current instance is in fact retrying to push.
   */
  private boolean retrying;

  private boolean canceled;

  @Inject
  PushOp(final GitRepositoryManager grm, final SchemaFactory<ReviewDb> s,
      final PushReplication.ReplicationConfig p, final RemoteConfig c,
      final SecureCredentialsProvider.Factory cpFactory,
      @Assisted final Project.NameKey d, @Assisted final URIish u) {
    repoManager = grm;
    schema = s;
    pool = p;
    config = c;
    credentialsProvider = cpFactory.create(c.getName());
    projectName = d;
    uri = u;
  }

  public boolean isRetrying() {
    return retrying;
  }

  public void setToRetry() {
    retrying = true;
  }

  public void cancel() {
    canceled = true;
  }

  public boolean wasCanceled() {
    return canceled;
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

  public Set<String> getRefs() {
    final Set<String> refs;

    if (mirror) {
      refs = new HashSet<String>(1);
      refs.add(MIRROR_ALL);
    } else {
      refs = delta;
    }

    return refs;
  }

  public void addRefs(Set<String> refs) {
    if (!mirror) {
      for (String ref : refs) {
        addRef(ref);
      }
    }
  }

  public void run() {
    // Lock the queue, and remove ourselves, so we can't be modified once
    // we start replication (instead a new instance, with the same URI, is
    // created and scheduled for a future point in time.)
    //
    pool.notifyStarting(this);

    // It should only verify if it was canceled after calling notifyStarting,
    // since the canceled flag would be set locking the queue.
    if (!canceled) {
      try {
        db = repoManager.openRepository(projectName);
        runImpl();
      } catch (RepositoryNotFoundException e) {
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

        // The remote push operation should be retried.
        pool.reschedule(this);
      } catch (IOException e) {
        log.error("Cannot replicate to " + uri, e);

      } catch (RuntimeException e) {
        log.error("Unexpected error during replication to " + uri, e);

      } catch (Error e) {
        log.error("Unexpected error during replication to " + uri, e);

      } finally {
        if (db != null) {
          db.close();
        }
      }
    }
  }

  @Override
  public String toString() {
    return (mirror ? "mirror " : "push ") + uri;
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
          if ("non-fast-forward".equals(u.getMessage())) {
            log.error("Failed replicate of " + u.getRemoteName() + " to " + uri
                + ", remote rejected non-fast-forward push."
                + "  Check receive.denyNonFastForwards variable in config file"
                + " of destination repository.");
          } else {
            log.error("Failed replicate of " + u.getRemoteName() + " to " + uri
                + ", reason: " + u.getMessage());
          }
          break;
      }
    }
  }

  private PushResult pushVia(final Transport tn) throws IOException,
      NotSupportedException, TransportException {
    tn.applyConfig(config);
    tn.setCredentialsProvider(credentialsProvider);

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
    final ProjectControl pc;
    try {
      pc = pool.controlFor(projectName);
    } catch (NoSuchProjectException e) {
      return Collections.emptyList();
    }

    Map<String, Ref> local = db.getAllRefs();
    if (!pc.allRefsAreVisible()) {
      if (!mirror) {
        // If we aren't mirroring, reduce the space we need to filter
        // to only the references we will update during this operation.
        //
        Map<String, Ref> n = new HashMap<String, Ref>();
        for (String src : delta) {
          Ref r = local.get(src);
          if (r != null) {
            n.put(src, r);
          }
        }
        local = n;
      }

      final ReviewDb meta;
      try {
        meta = schema.open();
      } catch (OrmException e) {
        log.error("Cannot read database to replicate to " + projectName, e);
        return Collections.emptyList();
      }
      try {
        local = new VisibleRefFilter(db, pc, meta, true).filter(local);
      } finally {
        meta.close();
      }
    }

    final List<RemoteRefUpdate> cmds = new ArrayList<RemoteRefUpdate>();
    if (mirror) {
      final Map<String, Ref> remote = listRemote(tn);

      for (final Ref src : local.values()) {
        final RefSpec spec = matchSrc(src.getName());
        if (spec != null) {
          final Ref dst = remote.get(spec.getDestination());
          if (dst == null || !src.getObjectId().equals(dst.getObjectId())) {
            // Doesn't exist yet, or isn't the same value, request to push.
            //
            send(cmds, spec, src);
          }
        }
      }

      for (final Ref ref : remote.values()) {
        if (!Constants.HEAD.equals(ref.getName())) {
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
          Ref srcRef = local.get(src);
          if (srcRef != null) {
            send(cmds, spec, srcRef);
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

  private void send(final List<RemoteRefUpdate> cmds, final RefSpec spec,
      final Ref src) throws IOException {
    final String dst = spec.getDestination();
    final boolean force = spec.isForceUpdate();
    cmds.add(new RemoteRefUpdate(db, src, dst, force, null, null));
  }

  private void delete(final List<RemoteRefUpdate> cmds, final RefSpec spec)
      throws IOException {
    final String dst = spec.getDestination();
    final boolean force = spec.isForceUpdate();
    cmds.add(new RemoteRefUpdate(db, (Ref) null, dst, force, null, null));
  }

  @Override
  public NameKey getProjectNameKey() {
    return projectName;
  }

  @Override
  public String getRemoteName() {
    return config.getName();
  }

  @Override
  public boolean hasCustomizedPrint() {
    return true;
  }
}
