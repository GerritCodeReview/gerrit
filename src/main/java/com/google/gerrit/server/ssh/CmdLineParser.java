/*
 * Copyright (C) 2008, Shawn O. Pearce <spearce@spearce.org>
 * 
 * (Taken from JGit org.spearce.jgit.pgm.opt.CmdLineParser.)
 * 
 * All rights reserved.
 * 
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * 
 * - Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 * 
 * - Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * 
 * - Neither the name of the Git Development Community nor the names of its
 * contributors may be used to endorse or promote products derived from this
 * software without specific prior written permission.
 * 
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
 * LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
 * CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
 * SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
 * INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
 * CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 */

package com.google.gerrit.server.ssh;

import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.IllegalAnnotationError;

import java.util.ArrayList;

/**
 * Extended command line parser which handles --foo=value arguments.
 * <p>
 * The args4j package does not natively handle --foo=value and instead prefers
 * to see --foo value on the command line. Many users are used to the GNU style
 * --foo=value long option, so we convert from the GNU style format to the
 * args4j style format prior to invoking args4j for parsing.
 */
public class CmdLineParser extends org.kohsuke.args4j.CmdLineParser {
  /**
   * Creates a new command line owner that parses arguments/options and set them
   * into the given object.
   * 
   * @param bean instance of a class annotated by
   *        {@link org.kohsuke.args4j.Option} and
   *        {@link org.kohsuke.args4j.Argument}. this object will receive
   *        values.
   * 
   * @throws IllegalAnnotationError if the option bean class is using args4j
   *         annotations incorrectly.
   */
  public CmdLineParser(final Object bean) throws IllegalAnnotationError {
    super(bean);
  }

  @Override
  public void parseArgument(final String... args) throws CmdLineException {
    final ArrayList<String> tmp = new ArrayList<String>(args.length);
    for (int argi = 0; argi < args.length; argi++) {
      final String str = args[argi];
      if (str.equals("--")) {
        while (argi < args.length)
          tmp.add(args[argi++]);
        break;
      }

      if (str.startsWith("--")) {
        final int eq = str.indexOf('=');
        if (eq > 0) {
          tmp.add(str.substring(0, eq));
          tmp.add(str.substring(eq + 1));
          continue;
        }
      }

      tmp.add(str);
    }

    super.parseArgument(tmp.toArray(new String[tmp.size()]));
  }
}
