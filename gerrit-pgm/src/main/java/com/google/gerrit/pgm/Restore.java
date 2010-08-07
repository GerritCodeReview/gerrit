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
import com.google.gerrit.pgm.util.SiteProgram;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gerrit.server.schema.backup.RestoreBackup;
import com.google.gwtorm.client.SchemaFactory;
import com.google.inject.Inject;
import com.google.inject.Injector;

import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.FileInputStream;

/** Restore the database from a compressed series of protobuf objects. */
public class Restore extends SiteProgram {
  private static final String OK_FLAG =
      "--yes-really-import-and-destroy-current-data";

  private final LifecycleManager manager = new LifecycleManager();
  private Injector dbInjector;

  @Option(name = "--input", aliases = {"-i"}, required = true, metaVar = "FILE", usage = "File to read backup from")
  private File input;

  @Option(name = OK_FLAG)
  private boolean run;

  @Inject
  private SchemaFactory<ReviewDb> dstdb;

  @Override
  public int run() throws Exception {
    mustHaveValidSite();

    if (!run) {
      throw die("Must pass " + OK_FLAG);
    }

    dbInjector = createDbInjector(SINGLE_USER);
    manager.add(dbInjector);
    manager.start();
    dbInjector.injectMembers(this);

    ReviewDb dst = dstdb.open();
    try {
      FileInputStream in = new FileInputStream(input);
      try {
        RestoreBackup.restore(in, dst);
      } finally {
        in.close();
      }
      System.err.println("Restore completed");
    } finally {
      dst.close();
    }
    return 0;
  }
}
