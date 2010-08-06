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

import com.google.gerrit.pgm.util.AbstractProgram;
import com.google.gerrit.reviewdb.ReviewDb;
import com.google.gwtorm.schema.java.JavaSchemaModel;

import org.eclipse.jgit.storage.file.LockFile;
import org.eclipse.jgit.util.FS;
import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class ProtoGen extends AbstractProgram {
  @Option(name = "--output", aliases = {"-o"}, required = true, metaVar = "FILE", usage = "File to write .proto into")
  private File file;

  @Override
  public int run() throws Exception {
    LockFile lf = new LockFile(file, FS.DETECTED);

    if (!lf.lock()) {
      throw new IOException("Cannot lock " + file);
    }

    try {
      JavaSchemaModel jsm = new JavaSchemaModel(ReviewDb.class);
      PrintWriter out = new PrintWriter(lf.getOutputStream());
      try {
        jsm.generateProto(out);
        out.flush();
      } finally {
        out.close();
      }
      if (!lf.commit()) {
        throw new IOException("Could not write to " + file);
      }
    } finally {
      lf.unlock();
    }
    return 0;
  }
}
