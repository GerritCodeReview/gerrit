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
import com.google.inject.assistedinject.Assisted;

import com.jcraft.jsch.JSchException;

import org.eclipse.jgit.errors.NoRemoteRepositoryException;
import org.eclipse.jgit.errors.NotSupportedException;
import org.eclipse.jgit.errors.RepositoryNotFoundException;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.RemoteConfig;
import org.eclipse.jgit.transport.URIish;
import org.slf4j.Logger;

import java.io.IOException;

/**
 * A base class for replication operations such as push & fetch replication.
 */
public abstract class ReplicateOp implements ProjectRunnable {
  interface Factory {
    ReplicateOp create(Project.NameKey d, URIish u);
  }

  protected static final Logger log = ReplicationQueueImpl.log;

  protected final GitRepositoryManager repoManager;
  protected final SchemaFactory<ReviewDb> schema;
  protected final ReplicationQueueImpl.ReplicationConfig pool;
  protected final RemoteConfig config;
  protected final Project.NameKey projectName;
  protected final URIish uri;
  protected Repository db;

  public ReplicateOp(final GitRepositoryManager repoManager,
      @Assisted final Project.NameKey projectName,
      final ReplicationQueueImpl.ReplicationConfig pool,
      @Assisted final URIish uri,
      final SchemaFactory<ReviewDb> schema,
      final RemoteConfig config) {
    this.repoManager = repoManager;
    this.projectName = projectName;
    this.pool = pool;
    this.uri = uri;
    this.schema = schema;
    this.config = config;
  }

  @Override
  public void run() {
    try {
      // Lock the queue, and remove ourselves, so we can't be modified once
      // we start replication (instead a new instance, with the same URI, is
      // created and scheduled for a future point in time.)
      //
      pool.notifyStarting(this);
      db = repoManager.openRepository(projectName.get());
      runImpl();
    } catch (RepositoryNotFoundException e) {
      log.error("Cannot replicate " + projectName + "; " + e.getMessage());

    } catch (NoRemoteRepositoryException e) {
      log.error("Cannot replicate " + direction() + " " + uri + "; repository not found");

    } catch (NotSupportedException e) {
      log.error("Cannot replicate " + direction() + " " + uri, e);

    } catch (TransportException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof JSchException
          && cause.getMessage().startsWith("UnknownHostKey:")) {
        log.error("Cannot replicate " + direction() + " " + uri + ": " + cause.getMessage());
      } else {
        log.error("Cannot replicate " + direction() + " " + uri, e);
      }

    } catch (IOException e) {
      log.error("Cannot replicate " + direction() + " " + uri, e);

    } catch (RuntimeException e) {
      log.error("Unexpected error during replication " + direction() + " " + uri, e);

    } catch (Error e) {
      log.error("Unexpected error during replication " + direction() + " " + uri, e);

    } finally {
      if (db != null) {
        db.close();
      }
    }
  }

  abstract void runImpl() throws IOException;

  abstract String direction();

  abstract void addRef(final String ref);

  URIish getURI() {
    return uri;
  }

  @Override
  public Project.NameKey getProjectNameKey() {
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
