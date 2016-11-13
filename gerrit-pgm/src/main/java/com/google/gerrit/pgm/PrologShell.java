// Copyright (C) 2011 The Android Open Source Project
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
import com.googlecode.prolog_cafe.exceptions.HaltException;
import com.googlecode.prolog_cafe.lang.BufferingPrologControl;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologClassLoader;
import com.googlecode.prolog_cafe.lang.SymbolTerm;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.kohsuke.args4j.Option;

public class PrologShell extends AbstractProgram {
  @Option(name = "-s", metaVar = "FILE.pl", usage = "file to load")
  private List<String> fileName = new ArrayList<>();

  @Override
  public int run() {
    banner();

    BufferingPrologControl pcl = new BufferingPrologControl();
    pcl.setPrologClassLoader(new PrologClassLoader(getClass().getClassLoader()));
    pcl.setEnabled(Prolog.Feature.IO, true);
    pcl.setEnabled(Prolog.Feature.STATISTICS, true);
    pcl.configureUserIO(System.in, System.out, System.err);
    pcl.initialize(Prolog.BUILTIN);

    for (String file : fileName) {
      String path;
      try {
        path = new File(file).getCanonicalPath();
      } catch (IOException e) {
        path = new File(file).getAbsolutePath();
      }
      pcl.execute(Prolog.BUILTIN, "consult", SymbolTerm.create(path));
      System.err.println();
      System.err.flush();
    }

    try {
      pcl.execute(Prolog.BUILTIN, "cafeteria");
      write("% halt\n");
      return 0;
    } catch (HaltException halt) {
      write("% halt(" + halt.getStatus() + ")\n");
      return halt.getStatus();
    }
  }

  private void banner() {
    System.err.format(
        "Gerrit Code Review %s - Interactive Prolog Shell",
        com.google.gerrit.common.Version.getVersion());
    System.err.println();
    System.err.println(
        "(type Ctrl-D or \"halt.\" to exit," + " \"['path/to/file.pl'].\" to load a file)");
    System.err.println();
    System.err.flush();
  }

  private void write(String msg) {
    System.out.flush();
    System.err.flush();
    System.out.println(msg);
    System.out.flush();
  }
}
