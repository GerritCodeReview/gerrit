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

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.gerrit.pgm.util.AbstractProgram;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gwtorm.schema.java.JavaSchemaModel;
import java.io.BufferedWriter;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import org.eclipse.jgit.internal.storage.file.LockFile;
import org.eclipse.jgit.util.IO;
import org.kohsuke.args4j.Option;

public class ProtoGen extends AbstractProgram {
  @Option(
    name = "--output",
    aliases = {"-o"},
    required = true,
    metaVar = "FILE",
    usage = "File to write .proto into"
  )
  private File file;

  @Override
  public int run() throws Exception {
    LockFile lock = new LockFile(file.getAbsoluteFile());
    if (!lock.lock()) {
      throw die("Cannot lock " + file);
    }
    try {
      JavaSchemaModel jsm = new JavaSchemaModel(ReviewDb.class);
      try (OutputStream o = lock.getOutputStream();
          PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(o, UTF_8)))) {
        String header;
        try (InputStream in = getClass().getResourceAsStream("ProtoGenHeader.txt")) {
          ByteBuffer buf = IO.readWholeStream(in, 1024);
          int ptr = buf.arrayOffset() + buf.position();
          int len = buf.remaining();
          header = new String(buf.array(), ptr, len, UTF_8);
        }

        String version = com.google.gerrit.common.Version.getVersion();
        out.write(header.replace("@@VERSION@@", version));
        jsm.generateProto(out);
        out.flush();
      }
      if (!lock.commit()) {
        throw die("Could not write to " + file);
      }
    } finally {
      lock.unlock();
    }
    System.out.println("Created " + file.getPath());
    return 0;
  }
}
