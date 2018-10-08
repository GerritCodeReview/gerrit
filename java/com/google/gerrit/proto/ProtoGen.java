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

package com.google.gerrit.proto;

import static com.google.common.base.Preconditions.checkState;
import static java.nio.charset.StandardCharsets.UTF_8;

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
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import org.kohsuke.args4j.ParserProperties;

public class ProtoGen {
  @Option(
      name = "--output",
      aliases = {"-o"},
      required = true,
      metaVar = "FILE",
      usage = "File to write .proto into")
  private File file;

  public static void main(String[] argv) throws Exception {
    System.exit(new ProtoGen().run(argv));
  }

  private int run(String[] argv) throws Exception {
    CmdLineParser parser = new CmdLineParser(this, ParserProperties.defaults().withAtSyntax(false));
    try {
      parser.parseArgument(argv);
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      System.err.println(getClass().getSimpleName() + " -o output.proto");
      parser.printUsage(System.err);
      return 1;
    }

    LockFile lock = new LockFile(file.getAbsoluteFile());
    checkState(lock.lock(), "cannot lock %s", file);
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

        out.write(header);
        jsm.generateProto(out);
        out.flush();
      }
      checkState(lock.commit(), "Could not write to %s", file);
    } finally {
      lock.unlock();
    }
    return 0;
  }
}
