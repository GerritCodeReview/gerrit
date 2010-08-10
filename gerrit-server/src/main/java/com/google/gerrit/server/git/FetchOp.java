// Copyright (C) 2010 The Android Open Source Project
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
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.assistedinject.Assisted;

import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.TrackingRefUpdate;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.URIish;

import java.io.IOException;

/**
 * A fetch remote operation started by {@link ReplicationQueue}.
 * <p>
 * Instance members are protected by the lock within {@link ReplicationQueueImpl}. Callers must
 * take that lock to ensure they are working with a current view of the object.
 */
public class FetchOp extends ReplicateOp {
  interface Factory extends ReplicateOp.Factory {
    FetchOp create(Project.NameKey d, URIish u);
  }

  @Inject
  FetchOp(final GitRepositoryManager repositoryManager,
      final SchemaFactory<ReviewDb> schema,
      final ReplicationQueueImpl.ReplicationConfig pool,
      final RemoteConfig config,
      @Assisted final Project.NameKey projectName,
      @Assisted final URIish uri) {
    super(repositoryManager, projectName, pool, uri, schema, config);
  }

  @Override
  protected void runImpl() throws IOException {
    final Transport tn = Transport.open(db, uri);
    final FetchResult res;
    try {
      res = fetchVia(tn);
    } finally {
      try {
        tn.close();
      } catch (Throwable e2) {
        log.warn("Unexpected error while closing " + uri, e2);
      }
    }

    for (final TrackingRefUpdate u : res.getTrackingRefUpdates()) {
      switch (u.getResult()) {
        case FAST_FORWARD:
        case FORCED:
        case NEW:
        case NO_CHANGE:
        case RENAMED:
          break;

        case NOT_ATTEMPTED:
        case IO_FAILURE:
        case LOCK_FAILURE:
        case REJECTED:
        case REJECTED_CURRENT_BRANCH:
          log.error("Failed replicate of " + u.getLocalName() + " from " + uri
              + ": status " + u.getResult().name());
          break;
      }
    }
  }

  private FetchResult fetchVia(final Transport tn) throws IOException {
    tn.applyConfig(config);

    return tn.fetch(NullProgressMonitor.INSTANCE, config.getFetchRefSpecs());
  }

  @Override
  protected String direction() {
    return "from";
  }

  @Override
  public String toString() {
    return "fetch " + uri;
  }

  @Override
  void addRef(String ref) {
    // No-op
  }
}
