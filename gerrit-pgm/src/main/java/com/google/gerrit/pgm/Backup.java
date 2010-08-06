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

package com.google.gerrit.pgm;

import static com.google.gerrit.server.schema.DataSourceProvider.Context.SINGLE_USER;

import com.google.gerrit.lifecycle.LifecycleManager;
import com.google.gerrit.pgm.backup.BackupAccess;
import com.google.gerrit.pgm.backup.BackupDatabase;
import com.google.gerrit.pgm.backup.Counters;
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.client.Access;
import com.google.gwtorm.client.OrmException;
import com.google.gwtorm.client.SchemaFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;
import com.google.inject.Inject;
import com.google.inject.Injector;

import org.eclipse.jgit.lib.NullProgressMonitor;
import org.eclipse.jgit.lib.ProgressMonitor;
import org.eclipse.jgit.lib.TextProgressMonitor;
import org.eclipse.jgit.storage.file.LockFile;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Option;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.GZIPOutputStream;

/** Backup the database as a compressed series of protobuf objects. */
public class Backup extends SiteProgram {
  private final LifecycleManager manager = new LifecycleManager();
  private Injector dbInjector;

  @Option(name = "--quiet", aliases = {"-q"}, usage = "Quiet (suppress progress)")
  private boolean quiet;

  @Option(name = "--output", aliases = {"-o"}, required = true, metaVar = "FILE", usage = "File to write the backup into")
  private File output;

  @Inject
  private SchemaFactory<ReviewDb> srcdb;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();

    dbInjector = createDbInjector(SINGLE_USER);
    manager.add(dbInjector);
    manager.start();
    dbInjector.injectMembers(this);

    BackupDatabase<ReviewDb> bck = new BackupDatabase<ReviewDb>(ReviewDb.class);
    ReviewDb dst = bck.open();

    ReviewDb src = srcdb.open();
    try {
      final LockFile lf = new LockFile(output.getAbsoluteFile(), FS.DETECTED);
      if (!lf.lock()) {
        throw die("Cannot lock " + output);
      }
      try {
        BufferedOutputStream out =
            new BufferedOutputStream(new GZIPOutputStream(lf.getOutputStream(),
                8192));
        try {
          backup(src, dst, out);
        } finally {
          out.close();
        }
        if (!lf.commit()) {
          throw die("Cannot commit " + output);
        }
        if (!quiet) {
          System.err.println("Backup completed to " + output);
        }
      } finally {
        lf.unlock();
      }
    } finally {
      src.close();
    }
    return 0;
  }

  @SuppressWarnings("unchecked")
  private void backup(ReviewDb src, ReviewDb dst, BufferedOutputStream out)
      throws OrmException, IOException {
    Map<Integer, BackupAccess<?, ?>> relations = index(dst);

    ProgressMonitor pm =
        quiet ? NullProgressMonitor.INSTANCE : new TextProgressMonitor();

    Counters cnts = new Counters();
    cnts.accountGroupId = src.nextAccountGroupId();
    cnts.accountId = src.nextAccountId();
    cnts.changeId = src.nextChangeId();
    cnts.changeMessageId = src.nextChangeMessageId();
    cnts.contributorAgreementId = src.nextContributorAgreementId();
    Counters.CODEC.encodeWithSize(cnts, out);

    for (Access<?, ?> s : src.allRelations()) {
      BackupAccess<?, ?> d = relations.get(s.getRelationID());
      ProtobufCodec pc = d.getObjectCodec();

      pm.beginTask("Backup " + s.getRelationName(), ProgressMonitor.UNKNOWN);
      for (Object obj : s.iterateAllEntities()) {
        pc.encodeWithSize(obj, out);
        pm.update(1);
      }
      pm.endTask();
    }
  }

  @SuppressWarnings("unchecked")
  private Map<Integer, BackupAccess<?, ?>> index(ReviewDb dst) {
    Map<Integer, BackupAccess<?, ?>> relations =
        new HashMap<Integer, BackupAccess<?, ?>>();

    for (Access<?, ?> a : dst.allRelations()) {
      relations.put(a.getRelationID(), (BackupAccess) a);
    }
    return relations;
  }
}
