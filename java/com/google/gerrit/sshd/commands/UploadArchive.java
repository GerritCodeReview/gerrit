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

import static com.google.common.base.Preconditions.checkNotNull;
import static java.nio.charset.StandardCharsets.UTF_8;

import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableMap;
import com.google.gerrit.config.ArchiveFormat;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.server.permissions.PermissionBackend;
import com.google.gerrit.server.permissions.PermissionBackendException;
import com.google.gerrit.server.permissions.ProjectPermission;
import com.google.gerrit.server.project.ProjectCache;
import com.google.gerrit.server.project.ProjectState;
import com.google.gerrit.server.restapi.change.AllowedFormats;
import com.google.gerrit.server.restapi.project.CommitsCollection;
import com.google.gerrit.sshd.AbstractGitCommand;
import com.google.inject.Inject;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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

/** Allows getting archives for Git repositories over SSH using the Git upload-archive protocol. */
public class UploadArchive extends AbstractGitCommand {
  /**
   * Options for parsing Git commands.
   *
   * <p>These options are not passed on command line, but received through input stream in pkt-line
   * format.
   */
  static class Options {
    @Option(
      name = "-f",
      aliases = {"--format"},
      usage =
          "Format of the"
              + " resulting archive: tar or zip... If this option is not given, and"
              + " the output file is specified, the format is inferred from the"
              + " filename if possible (e.g. writing to \"foo.zip\" makes the output"
              + " to be in the zip format). Otherwise the output format is tar."
    )
    private String format = "tar";

    @Option(name = "--prefix", usage = "Prepend <prefix>/ to each filename in the archive.")
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

    @Option(
      name = "-9",
      usage =
          "Highest and slowest compression level. You "
              + "can specify any number from 1 to 9 to adjust compression speed and "
              + "ratio."
    )
    private boolean level9;

    @Argument(index = 0, required = true, usage = "The tree or commit to produce an archive for.")
    private String treeIsh = "master";

    @Argument(
      index = 1,
      multiValued = true,
      usage =
          "Without an optional path parameter, all files and subdirectories of "
              + "the current working directory are included in the archive. If one "
              + "or more paths are specified, only these are included."
    )
    private List<String> path;
  }

  @Inject private PermissionBackend permissionBackend;
  @Inject private CommitsCollection commits;
  @Inject private AllowedFormats allowedFormats;
  @Inject private ProjectCache projectCache;
  private Options options = new Options();

  /**
   * Read and parse arguments from input stream. This method gets the arguments from input stream,
   * in Pkt-line format, then parses them to fill the options object.
   */
  protected void readArguments() throws IOException, Failure {
    String argCmd = "argument ";
    List<String> args = new ArrayList<>();

    // Read arguments in Pkt-Line format
    PacketLineIn packetIn = new PacketLineIn(in);
    for (; ; ) {
      String s = packetIn.readString();
      if (s == PacketLineIn.END) {
        break;
      }
      if (!s.startsWith(argCmd)) {
        throw new Failure(1, "fatal: 'argument' token or flush expected, got " + s);
      }
      for (String p : Splitter.on('=').limit(2).split(s.substring(argCmd.length()))) {
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
  protected void runImpl() throws IOException, PermissionBackendException, Failure {
    PacketLineOut packetOut = new PacketLineOut(out);
    packetOut.setFlushOnEnd(true);
    packetOut.writeString("ACK");
    packetOut.end();

    try {
      // Parse Git arguments
      readArguments();

      ArchiveFormat f = allowedFormats.getExtensions().get("." + options.format);
      if (f == null) {
        throw new Failure(3, "fatal: upload-archive not permitted for format " + options.format);
      }

      // Find out the object to get from the specified reference and paths
      ObjectId treeId = repo.resolve(options.treeIsh);
      if (treeId == null) {
        throw new Failure(4, "fatal: reference not found: " + options.treeIsh);
      }

      // Verify the user has permissions to read the specified tree.
      if (!canRead(treeId)) {
        throw new Failure(5, "fatal: no permission to read tree" + options.treeIsh);
      }

      // The archive is sent in DATA sideband channel
      try (SideBandOutputStream sidebandOut =
          new SideBandOutputStream(
              SideBandOutputStream.CH_DATA, SideBandOutputStream.MAX_BUF, out)) {
        new ArchiveCommand(repo)
            .setFormat(f.name())
            .setFormatOptions(getFormatOptions(f))
            .setTree(treeId)
            .setPaths(options.path.toArray(new String[0]))
            .setPrefix(options.prefix)
            .setOutputStream(sidebandOut)
            .call();
        sidebandOut.flush();
      } catch (GitAPIException e) {
        throw new Failure(7, "fatal: git api exception, " + e);
      }
    } catch (Throwable t) {
      // Report the error in ERROR sideband channel. Catch Throwable too so we can also catch
      // NoClassDefFound.
      try (SideBandOutputStream sidebandError =
          new SideBandOutputStream(
              SideBandOutputStream.CH_ERROR, SideBandOutputStream.MAX_BUF, out)) {
        sidebandError.write(t.getMessage().getBytes(UTF_8));
        sidebandError.flush();
      }
      throw t;
    } finally {
      // In any case, cleanly close the packetOut channel
      packetOut.end();
    }
  }

  private Map<String, Object> getFormatOptions(ArchiveFormat f) {
    if (f == ArchiveFormat.ZIP) {
      int value =
          Arrays.asList(
                  options.level0,
                  options.level1,
                  options.level2,
                  options.level3,
                  options.level4,
                  options.level5,
                  options.level6,
                  options.level7,
                  options.level8,
                  options.level9)
              .indexOf(true);
      if (value >= 0) {
        return ImmutableMap.<String, Object>of("level", Integer.valueOf(value));
      }
    }
    return Collections.emptyMap();
  }

  private boolean canRead(ObjectId revId) throws IOException, PermissionBackendException {
    ProjectState projectState = projectCache.get(projectName);
    checkNotNull(projectState, "Failed to load project %s", projectName);

    if (!projectState.statePermitsRead()) {
      return false;
    }

    try {
      permissionBackend.user(user).project(projectName).check(ProjectPermission.READ);
      return true;
    } catch (AuthException e) {
      // Check reachability of the specific revision.
      try (RevWalk rw = new RevWalk(repo)) {
        RevCommit commit = rw.parseCommit(revId);
        return commits.canRead(projectState, repo, commit);
      }
    }
  }
}
