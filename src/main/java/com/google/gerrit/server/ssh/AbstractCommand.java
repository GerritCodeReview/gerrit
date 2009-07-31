// Copyright (C) 2008 The Android Open Source Project
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

package com.google.gerrit.server.ssh;

import com.google.gerrit.client.data.ProjectCache;
import com.google.gerrit.client.reviewdb.Account;
import com.google.gerrit.client.reviewdb.AccountGroup;
import com.google.gerrit.client.reviewdb.ApprovalCategory;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.server.BaseServiceImplementation;
import com.google.gerrit.server.GerritServer;
import com.google.gerrit.server.IdentifiedUser;
import com.google.gerrit.server.RemotePeer;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;

import java.io.BufferedWriter;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.net.SocketAddress;
import java.util.Set;

public abstract class AbstractCommand extends BaseCommand {
  private static final String ENC = "UTF-8";

  @Inject
  protected GerritServer server;

  @Inject
  protected SchemaFactory<ReviewDb> schema;

  @Inject
  @RemotePeer
  private SocketAddress remoteAddress;

  @Inject
  private IdentifiedUser currentUser;

  protected ReviewDb db;

  private Set<AccountGroup.Id> userGroups;

  protected PrintWriter toPrintWriter(final OutputStream o)
      throws UnsupportedEncodingException {
    return new PrintWriter(new BufferedWriter(new OutputStreamWriter(o, ENC)));
  }

  protected GerritServer getGerritServer() {
    return server;
  }

  protected ReviewDb openReviewDb() throws Failure {
    if (db == null) {
      try {
        db = schema.open();
      } catch (OrmException e) {
        throw new Failure(1, "fatal: Gerrit database is offline", e);
      }
    }
    return db;
  }

  protected Account.Id getAccountId() {
    return currentUser.getAccountId();
  }

  protected SocketAddress getRemoteAddress() {
    return remoteAddress;
  }

  protected Set<AccountGroup.Id> getGroups() {
    if (userGroups == null) {
      userGroups = Common.getGroupCache().getEffectiveGroups(getAccountId());
    }
    return userGroups;
  }

  protected boolean canRead(final ProjectCache.Entry project) {
    return canPerform(project, ApprovalCategory.READ, (short) 1);
  }

  protected boolean canPerform(final ProjectCache.Entry project,
      final ApprovalCategory.Id actionId, final short val) {
    return BaseServiceImplementation.canPerform(getGroups(), project, actionId,
        val);
  }

  protected void assertIsAdministrator() throws Failure {
    if (!Common.getGroupCache().isAdministrator(getAccountId())) {
      throw new Failure(1, "fatal: Not a Gerrit administrator");
    }
  }

  public void start() {
    startThread(new CommandRunnable() {
      public void run() throws Exception {
        try {
          preRun();
          parseCommandLine();
          AbstractCommand.this.run();
        } finally {
          postRun();
        }
      }
    });
  }

  @SuppressWarnings("unused")
  protected void preRun() throws Failure {
  }

  protected abstract void run() throws Exception;

  protected void postRun() {
    closeDb();
  }

  protected void closeDb() {
    if (db != null) {
      db.close();
      db = null;
    }
  }
}
