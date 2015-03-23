// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.sshd.commands;

import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.reviewdb.server.ReviewDb;
import com.google.gerrit.server.change.ArchiveFormat;
import com.google.gerrit.server.change.GetArchive;
import com.google.gerrit.sshd.AbstractGitCommand;
import com.google.inject.Inject;
import com.google.inject.Provider;

import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.transport.PacketLineIn;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.SideBandOutputStream;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Allows getting archives for Git repositories over SSH using the Git
 * upload-archive protocol.
 */
public class UploadArchive extends AbstractGitCommand {
  /**
   * Options for parsing Git commands.
   * <p>
   * These options are not passed on command line, but received through input
   * stream in pkt-line format.
   */
  static class Options {
    @Option(name = "-f", aliases = {"--format"}, usage = "Format of the"
        + " resulting archive: tar or zip... If this option is not given, and"
        + " the output file is specified, the format is inferred from the"
        + " filename if possible (e.g. writing to \"foo.zip\" makes the output"
        + " to be in the zip format). Otherwise the output format is tar.")
    private String format = "tar";

    @Option(name = "--prefix",
        usage = "Prepend <prefix>/ to each filename in the archive.")
    private String prefix;

    @Option(name = "-0", usage = "Store the files instead of deflating them.")
    private boolean level0;
    @Option(name = "-1")
    private boolean level1;
    @Option(name = "-2")
    private boolean level2;
    @Option(name = "-3")
    private boolean level3;
    @Option(name = "-4")
    private boolean level4;
    @Option(name = "-5")
    private boolean level5;
    @Option(name = "-6")
    private boolean level6;
    @Option(name = "-7")
    private boolean level7;
    @Option(name = "-8")
    private boolean level8;
    @Option(name = "-9", usage = "Highest and slowest compression level. You "
        + "can specify any number from 1 to 9 to adjust compression speed and "
        + "ratio.")
    private boolean level9;

    @Argument(index = 0, required = true, usage = "The tree or commit to "
        + "produce an archive for.")
    private String treeIsh = "master";

    @Argument(index = 1, multiValued = true, usage =
        "Without an optional path parameter, all files and subdirectories of "
        + "the current working directory are included in the archive. If one "
        + "or more paths are specified, only these are included.")
    private List<String> path;
  }

  @Inject
  private GetArchive.AllowedFormats allowedFormats;
  @Inject
  private Provider<ReviewDb> db;
  private Options options = new Options();

  /**
   * Read and parse arguments from input stream.
   * This method gets the arguments from input stream, in Pkt-line format,
   * then parses them to fill the options object.
   */
  protected void readArguments() throws IOException, Failure {
    String argCmd = "argument ";
    List<String> args = Lists.newArrayList();

    // Read arguments in Pkt-Line format
    PacketLineIn packetIn = new PacketLineIn(in);
    for (;;) {
      String s = packetIn.readString();
      if (s == PacketLineIn.END) {
        break;
      }
      if (!s.startsWith(argCmd)) {
        throw new Failure(1, "fatal: 'argument' token or flush expected");
      }
      String[] parts = s.substring(argCmd.length()).split("=", 2);
      for(String p : parts) {
        args.add(p);
      }
    }

    try {
      // Parse them into the 'options' field
      CmdLineParser parser = new CmdLineParser(options);
      parser.parseArgument(args);
      if (options.path == null || Arrays.asList(".").equals(options.path)) {
        options.path = Collections.emptyList();
      }
    } catch (CmdLineException e) {
      throw new Failure(2, "fatal: unable to parse arguments, " + e);
    }
  }

  @Override
  protected void runImpl() throws IOException, Failure {
    PacketLineOut packetOut = new PacketLineOut(out);
    packetOut.setFlushOnEnd(true);
    packetOut.writeString("ACK");
    packetOut.end();

    try {
      // Parse Git arguments
      readArguments();

      ArchiveFormat f = allowedFormats.getExtensions().get("." + options.format);
      if (f == null) {
        throw new Failure(3, "fatal: upload-archive not permitted");
      }

      // Find out the object to get from the specified reference and paths
      ObjectId treeId = repo.resolve(options.treeIsh);
      if (treeId.equals(ObjectId.zeroId())) {
        throw new Failure(4, "fatal: reference not found");
      }

      // Verify the user has permissions to read the specified reference
      if (!projectControl.allRefsAreVisible() && !canRead(treeId)) {
          throw new Failure(5, "fatal: cannot perform upload-archive operation");
      }

      try {
        // The archive is sent in DATA sideband channel
        SideBandOutputStream sidebandOut =
            new SideBandOutputStream(SideBandOutputStream.CH_DATA,
                SideBandOutputStream.MAX_BUF, out);
        new ArchiveCommand(repo)
            .setFormat(f.name())
            .setFormatOptions(getFormatOptions(f))
            .setTree(treeId)
            .setPaths(options.path.toArray(new String[0]))
            .setPrefix(options.prefix)
            .setOutputStream(sidebandOut)
            .call();
        sidebandOut.flush();
        sidebandOut.close();
      } catch (GitAPIException e) {
        throw new Failure(7, "fatal: git api exception, " + e);
      }
    } catch (Failure f) {
      // Report the error in ERROR sideband channel
      SideBandOutputStream sidebandError =
          new SideBandOutputStream(SideBandOutputStream.CH_ERROR,
              SideBandOutputStream.MAX_BUF, out);
      sidebandError.write(f.getMessage().getBytes(UTF_8));
      sidebandError.flush();
      sidebandError.close();
      throw f;
    } finally {
      // In any case, cleanly close the packetOut channel
      packetOut.end();
    }
  }

  private Map<String, Object> getFormatOptions(ArchiveFormat f) {
    if (f == ArchiveFormat.ZIP) {
      int value = Arrays.asList(options.level0, options.level1, options.level2,
          options.level3, options.level4, options.level5, options.level6,
          options.level7, options.level8, options.level9).indexOf(true);
      if (value >= 0) {
        return ImmutableMap.<String, Object> of(
            "level", Integer.valueOf(value));
      }
    }
    return Collections.emptyMap();
  }

  private boolean canRead(ObjectId revId) throws IOException {
    try (RevWalk rw = new RevWalk(repo)) {
      RevCommit commit = rw.parseCommit(revId);
      return projectControl.canReadCommit(db.get(), rw, commit);
    }
  }
}
