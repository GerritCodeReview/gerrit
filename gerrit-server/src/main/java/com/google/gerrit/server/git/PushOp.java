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
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.project.NoSuchProjectException;
import com.google.gerrit.server.project.ProjectControl;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.transport.FetchConnection;
import org.eclipse.jgit.transport.PushResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.RemoteRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;

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
 * Instance members are protected by the lock within {@link ReplicationQueueImpl}. Callers must
 * take that lock to ensure they are working with a current view of the object.
 */
class PushOp extends ReplicateOp {
  interface Factory extends ReplicateOp.Factory {
    PushOp create(Project.NameKey d, URIish u);
  }
  static final String MIRROR_ALL = "..all..";

  private final Set<String> delta = new HashSet<String>();
  private boolean mirror;

  @Inject
  PushOp(final GitRepositoryManager repositoryManager,
      final SchemaFactory<ReviewDb> schema,
      final ReplicationQueueImpl.ReplicationConfig pool, final RemoteConfig config,
      @Assisted final Project.NameKey projectName, @Assisted final URIish uri) {
    super(repositoryManager, projectName, pool, uri, schema, config);
  }

  void addRef(final String ref) {
    if (MIRROR_ALL.equals(ref)) {
      delta.clear();
      mirror = true;
    } else if (!mirror) {
      delta.add(ref);
    }
  }

  @Override
  public String toString() {
    return (mirror ? "mirror " : "push ") + uri;
  }

  protected void runImpl() throws IOException {
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

  @Override
  protected String direction() {
    return "to";
  }

  private PushResult pushVia(final Transport tn) throws IOException {
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
        local = new VisibleRefFilter(db, pc, meta).filter(local);
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
            send(cmds, spec);
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
}
