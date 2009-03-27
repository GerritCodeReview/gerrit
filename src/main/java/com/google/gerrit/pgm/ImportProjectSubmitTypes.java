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

package com.google.gerrit.pgm;

import com.google.gerrit.client.reviewdb.Project;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.client.rpc.Common;
import com.google.gerrit.git.InvalidRepositoryException;
import com.google.gerrit.git.WorkQueue;
import com.google.gerrit.server.GerritServer;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.lib.ProgressMonitor;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.lib.TextProgressMonitor;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

/** Update project's submit_type field from their git config files. */
public class ImportProjectSubmitTypes {
  private static final String GERRIT = "gerrit";
  private static final String FFO = "fastforwardonly";

  public static void main(final String[] argv) throws OrmException,
      XsrfException {
    try {
      mainImpl(argv);
    } finally {
      WorkQueue.terminate();
    }
  }

  private static void mainImpl(final String[] argv) throws OrmException,
      XsrfException {
    final ProgressMonitor pm = new TextProgressMonitor();
    final GerritServer gs = GerritServer.getInstance();
    final ReviewDb db = Common.getSchemaFactory().open();
    try {
      final List<Project> all = db.projects().all().toList();
      pm.start(1);
      pm.beginTask("Update projects", all.size());
      for (final Project p : all) {
        if (p.getSubmitType() != null
            && p.getSubmitType() != Project.SubmitType.MERGE_IF_NECESSARY) {
          pm.update(1);
          continue;
        }

        final Repository r;
        try {
          r = gs.getRepositoryCache().get(p.getName());
        } catch (InvalidRepositoryException e) {
          pm.update(1);
          continue;
        }

        if ("true".equals(r.getConfig().getString(GERRIT, null, FFO))) {
          p.setSubmitType(Project.SubmitType.FAST_FORWARD_ONLY);
          db.projects().update(Collections.singleton(p));
          r.getConfig().unsetString(GERRIT, null, FFO);
          try {
            r.getConfig().save();
          } catch (IOException e) {
            // Ignore a save error
          }
        }
        pm.update(1);
      }
      pm.endTask();
    } finally {
      db.close();
    }
  }
}
