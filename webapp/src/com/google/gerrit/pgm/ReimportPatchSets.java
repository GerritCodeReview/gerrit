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

package com.google.gerrit.pgm;

import com.google.gerrit.client.reviewdb.Change;
import com.google.gerrit.client.reviewdb.PatchSet;
import com.google.gerrit.client.reviewdb.ReviewDb;
import com.google.gerrit.git.InvalidRepositoryException;
import com.google.gerrit.git.PatchSetImporter;
import com.google.gerrit.server.GerritServer;
import com.google.gwtjsonrpc.server.XsrfException;
import com.google.gwtorm.client.OrmException;

import org.spearce.jgit.lib.ObjectId;
import org.spearce.jgit.lib.Repository;
import org.spearce.jgit.revwalk.RevCommit;
import org.spearce.jgit.revwalk.RevWalk;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.sql.SQLException;
import java.util.ArrayList;

/**
 * Recreates PatchSet and Patch entities for the changes supplied.
 * <p>
 * Takes on input strings of the form <code>change_id|patch_set_id</code>, such
 * as might be created by the following PostgreSQL database dump:
 * 
 * <pre>
 *  psql reviewdb -tAc 'select change_id,patch_set_id from patch_sets'
 * </pre>
 * <p>
 * For each supplied PatchSet the info and patch entities are completely updated
 * based on the data stored in Git.
 */
public class ReimportPatchSets {
  public static void main(final String[] argv) throws OrmException,
      XsrfException, IOException {
    final ArrayList<PatchSet.Id> todo = new ArrayList<PatchSet.Id>();
    final BufferedReader br =
        new BufferedReader(new InputStreamReader(System.in));
    String line;
    while ((line = br.readLine()) != null) {
      final String[] idstr = line.split("\\|");
      todo.add(new PatchSet.Id(Change.Id.fromString(idstr[0]), Integer
          .parseInt(idstr[1])));
    }

    final GerritServer gs = GerritServer.getInstance();
    final ReviewDb db = gs.getDatabase().open();
    try {
      for (int i = 0; i < todo.size(); i++) {
        System.out.print("\rImport " + (i + 1) + " of " + todo.size() + " ...");
        System.out.flush();

        final PatchSet.Id psid = todo.get(i);
        final PatchSet ps = db.patchSets().get(psid);
        if (ps == null) {
          System.out.println();
          System.err.println("NotFound " + psid);
          System.err.flush();
          continue;
        }

        final Change c = db.changes().get(ps.getKey().getParentKey());
        if (c == null) {
          System.out.println();
          System.err.println("Orphan " + psid);
          System.err.flush();
          continue;
        }

        final String projectName = c.getDest().getParentKey().get();
        final Repository repo;
        try {
          repo = gs.getRepositoryCache().get(projectName);
        } catch (InvalidRepositoryException ie) {
          System.out.println();
          System.err.println("NoProject " + psid);
          System.err.println("NoProject " + ie.getMessage());
          System.err.flush();
          continue;
        }

        final RevWalk rw = new RevWalk(repo);
        final RevCommit src =
            rw.parseCommit(ObjectId.fromString(ps.getRevision().get()));
        new PatchSetImporter(db, repo, src, ps, false).run();
      }
    } catch (OrmException e) {
      e.printStackTrace();
      if (e.getCause() instanceof SQLException) {
        final SQLException e2 = (SQLException) e.getCause();
        if (e2.getNextException() != null) {
          e2.getNextException().printStackTrace();
        }
      }
    } finally {
      System.out.println();
      db.close();
    }
  }
}
