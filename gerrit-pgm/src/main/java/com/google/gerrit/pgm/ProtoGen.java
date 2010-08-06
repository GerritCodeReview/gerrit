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

import java.io.BufferedWriter;
import java.io.File;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;

public class ProtoGen extends AbstractProgram {
  @Option(name = "--output", aliases = {"-o"}, required = true, metaVar = "FILE", usage = "File to write .proto into")
  private File file;

  @Override
  public int run() throws Exception {
    LockFile lf = new LockFile(file.getAbsoluteFile(), FS.DETECTED);
    if (!lf.lock()) {
      throw die("Cannot lock " + file);
    }
    try {
      JavaSchemaModel jsm = new JavaSchemaModel(ReviewDb.class);
      PrintWriter out =
          new PrintWriter(new BufferedWriter(new OutputStreamWriter(lf
              .getOutputStream(), "UTF-8")));
      try {
out.println("// Copyright (C) 2011 The Android Open Source Project");
out.println("//");
out.println("// Licensed under the Apache License, Version 2.0 (the \"License\");");
out.println("// you may not use this file except in compliance with the License.");
out.println("// You may obtain a copy of the License at");
out.println("//");
out.println("// http://www.apache.org/licenses/LICENSE-2.0");
out.println("//");
out.println("// Unless required by applicable law or agreed to in writing, software");
out.println("// distributed under the License is distributed on an \"AS IS\" BASIS,");
out.println("// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.");
out.println("// See the License for the specific language governing permissions and");
out.println("// limitations under the License.");
out.println("//");
out.println("// Gerrit Code Review (version " + com.google.gerrit.common.Version.getVersion() + ")");
out.println();
out.println("syntax = \"proto2\";");
out.println();
out.println("option java_api_version = 2;");
out.println("option java_package = \"com.google.gerrit.proto.reviewdb\";");
out.println();
out.println("package devtools.gerritcodereview;\n");
out.println();

        jsm.generateProto(out);
        out.flush();
      } finally {
        out.close();
      }
      if (!lf.commit()) {
        throw die("Could not write to " + file);
      }
    } finally {
      lf.unlock();
    }
    System.out.println("Created " + file.getPath());
    return 0;
  }
}
