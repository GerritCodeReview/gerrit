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

import com.google.gerrit.git.WorkQueue;
import com.google.gerrit.server.ssh.CmdLineParser;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.Option;

import java.io.StringWriter;

/** Base class for command line invocations of Gerrit Code Review. */
public abstract class AbstractProgram {
  private final Object sleepLock = new Object();
  private boolean running = true;

  @Option(name = "--help", usage = "display this help text", aliases = {"-h"})
  private boolean help;

  private String getName() {
    String n = getClass().getName();
    int dot = n.lastIndexOf('.');
    if (0 < dot) {
      n = n.substring(dot + 1);
    }
    return n.toLowerCase();
  }

  public final int main(final String[] argv) throws Exception {
    final CmdLineParser clp = new CmdLineParser(this);
    try {
      clp.parseArgument(argv);
    } catch (CmdLineException err) {
      if (!help) {
        System.err.println("fatal: " + err.getMessage());
        return 1;
      }
    }

    if (help) {
      final StringWriter msg = new StringWriter();
      msg.write(getName());
      clp.printSingleLineUsage(msg, null);
      msg.write('\n');

      msg.write('\n');
      clp.printUsage(msg, null);
      msg.write('\n');
      System.err.println(msg.toString());
      return 1;
    }

    try {
      return run();
    } finally {
      WorkQueue.terminate();
    }
  }

  /** Method that never returns, e.g. to keep a daemon running. */
  protected int never() {
    synchronized (sleepLock) {
      while (running) {
        try {
          sleepLock.wait(60 * 60 * 1000L);
        } catch (InterruptedException e) {
          continue;
        }
      }
      return 0;
    }
  }

  /**
   * Run this program's logic, returning the command exit status.
   * <p>
   * When this method completes, the JVM is terminated. To keep the JVM running,
   * use {@code return never()}.
   */
  public abstract int run() throws Exception;
}
