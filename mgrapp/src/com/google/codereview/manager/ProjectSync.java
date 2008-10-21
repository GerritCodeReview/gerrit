// Copyright 2008 Google Inc.
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

package com.google.codereview.manager;

import com.google.codereview.internal.SyncProject.BranchSync;
import com.google.codereview.internal.SyncProject.SyncProjectRequest;
import com.google.codereview.internal.SyncProject.SyncProjectResponse;
import com.google.codereview.rpc.SimpleController;
import com.google.codereview.util.GitMetaUtil;
import com.google.protobuf.RpcCallback;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.spearce.jgit.errors.IncorrectObjectTypeException;
import org.spearce.jgit.lib.Constants;
import org.spearce.jgit.lib.Ref;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevWalk;

import java.io.File;
import java.io.IOException;

public class ProjectSync {
  private static final Log LOG = LogFactory.getLog(ProjectSync.class);

  private final Backend server;

  public ProjectSync(final Backend be) {
    server = be;
  }

  public void sync() {
    syncDirectoryImpl(server.getRepositoryCache().getBaseDirectory(), "");
  }

  private void syncDirectoryImpl(final File root, final String prefix) {
    final File[] entries = root.listFiles();
    if (entries == null) {
      return;
    }

    for (final File f : entries) {
      final String fName = f.getName();
      if (fName.equals(".") || fName.equals("..")) {
        continue;
      }

      if (!f.isDirectory()) {
        continue;
      }

      String fullName = prefix + fName;
      final Repository db;
      try {
        db = GitMetaUtil.open(f);
      } catch (IOException e) {
        LOG.error("Cannot open " + f + ": " + e.toString());
        continue;
      }

      if (db != null) {
        try {
          if (fullName.endsWith(".git")) {
            fullName = fullName.substring(0, fullName.length() - 4);
          }
          sync(fullName, db);
        } finally {
          db.close();
        }
      } else {
        syncDirectoryImpl(f, fullName + "/");
      }
    }
  }

  public void sync(final String name, final Repository db) {
    final SyncProjectRequest.Builder req = SyncProjectRequest.newBuilder();
    req.setProjectName(name);

    try {
      final RevWalk rw = new RevWalk(db);
      for (final Ref ref : db.getAllRefs().values()) {
        if (ref.getName().startsWith(Constants.R_HEADS)) {
          final RevCommit c;
          try {
            c = rw.parseCommit(ref.getObjectId());
          } catch (IncorrectObjectTypeException e) {
            continue;
          }
          req.addBranch(toBranch(ref, c));
        }
      }
    } catch (IOException err) {
      LOG.error("Cannot synchronize " + name, err);
    }

    send(req.build());
  }

  private BranchSync toBranch(final Ref r, final RevCommit c) {
    final BranchSync.Builder bs = BranchSync.newBuilder();
    bs.setBranchName(r.getName());
    bs.setCommit(GitMetaUtil.toGitCommit(c));
    return bs.build();
  }

  private void send(final SyncProjectRequest req) {
    final SimpleController ctrl = new SimpleController();
    server.getAdminService().syncProject(ctrl, req,
        new RpcCallback<SyncProjectResponse>() {
          public void run(final SyncProjectResponse rsp) {
          }
        });
    if (ctrl.failed()) {
      final String name = req.getProjectName();
      LOG.error("syncProject " + name + ": " + ctrl.errorText());
    }
  }
}
