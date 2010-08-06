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
import org.kohsuke.args4j.Argument;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;

public class ProtoGen extends AbstractProgram {
  @Argument(index = 0, required = true, metaVar = "FILE", usage = "file to output")
  private String fileName;

  @Override
  public int run() throws Exception {
    File f = new File(fileName);
    LockFile lf = new LockFile(f, FS.DETECTED);

    if (!lf.lock()) {
      throw new IOException("Cannot lock " + f);
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
      lf.commit();
    } finally {
      lf.unlock();
    }
    return 0;
  }
}
