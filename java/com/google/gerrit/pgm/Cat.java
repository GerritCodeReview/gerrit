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

import com.google.gerrit.launcher.GerritLauncher;
import com.google.gerrit.pgm.util.AbstractProgram;
import java.io.IOException;
import java.io.InputStream;
import org.kohsuke.args4j.Argument;

/** Dump the contents of a file in our archive. */
public class Cat extends AbstractProgram {
  @Argument(index = 0, required = true, metaVar = "FILE", usage = "file to output")
  private String fileName;

  @Override
  public int run() throws IOException {
    while (fileName.startsWith("/")) {
      fileName = fileName.substring(1);
    }

    String name;
    if (fileName.equals("LICENSES.txt")) {
      name = fileName;
    } else {
      name = "WEB-INF/" + fileName;
    }

    try (InputStream in = open(name)) {
      if (in == null) {
        System.err.println("error: no such file " + fileName);
        return 1;
      }

      try {
        final byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) >= 0) {
          System.out.write(buf, 0, n);
        }
      } finally {
        System.out.flush();
      }
    }
    return 0;
  }

  private InputStream open(String name) {
    return GerritLauncher.class.getClassLoader().getResourceAsStream(name);
  }
}
