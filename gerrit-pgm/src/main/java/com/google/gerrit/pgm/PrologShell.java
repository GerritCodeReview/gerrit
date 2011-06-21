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

import com.googlecode.prolog_cafe.lang.BufferingPrologControl;
import com.googlecode.prolog_cafe.lang.HaltException;
import com.googlecode.prolog_cafe.lang.Prolog;
import com.googlecode.prolog_cafe.lang.PrologClassLoader;
import com.googlecode.prolog_cafe.lang.PrologMain;
import com.googlecode.prolog_cafe.lang.SymbolTerm;

import org.kohsuke.args4j.Option;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class PrologShell extends AbstractProgram {
  @Option(name = "-s", multiValued = true, metaVar = "FILE.pl", usage = "file to load")
  private List<String> fileName = new ArrayList<String>();

  @Override
  public int run() {
    banner();

    BufferingPrologControl pcl = new BufferingPrologControl();
    pcl.setPrologClassLoader(new PrologClassLoader(getClass().getClassLoader()));
    pcl.initialize(Prolog.BUILTIN);
    pcl.execute(Prolog.BUILTIN, "set_prolog_flag",
        SymbolTerm.intern("print_stack_trace"),
        SymbolTerm.intern("on"));

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
    System.err.format("Gerrit Code Review %s - Interactive Prolog Shell",
        com.google.gerrit.common.Version.getVersion());
    System.err.println();
    System.err.println("based on " + PrologMain.VERSION);
    System.err.println("         " + PrologMain.COPYRIGHT);
    System.err.println("(type Ctrl-D or \"halt.\" to exit," +
    		" \"['path/to/file.pl'].\" to load a file)");
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
