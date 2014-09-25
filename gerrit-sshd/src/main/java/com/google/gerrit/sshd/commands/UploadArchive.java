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

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.gerrit.sshd.AbstractGitCommand;

import org.apache.commons.compress.archivers.ArchiveOutputStream;
import org.apache.commons.compress.archivers.zip.ZipArchiveOutputStream;
import org.eclipse.jgit.api.ArchiveCommand;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.archive.*;
import org.eclipse.jgit.lib.FileMode;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.transport.PacketLineIn;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.transport.SideBandOutputStream;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/** Allows getting archives for Git repositories over SSH using the Git
 * upload-archive protocol.
 */
public class UploadArchive extends AbstractGitCommand {

  /** Options for parsing Git commands.
   * These options are not passed on command line, but received through input
   * stream in Pkt-Line format.
   */
  static class Options {
    @Option(name = "-f", aliases = {"--format"})
    private String format = "tar";

    @Option(name = "--prefix")
    private String prefix = null;

    @Option(name = "-0")
    private boolean level0 = false;
    @Option(name = "-1")
    private boolean level1 = false;
    @Option(name = "-2")
    private boolean level2 = false;
    @Option(name = "-3")
    private boolean level3 = false;
    @Option(name = "-4")
    private boolean level4 = false;
    @Option(name = "-5")
    private boolean level5 = false;
    @Option(name = "-6")
    private boolean level6 = false;
    @Option(name = "-7")
    private boolean level7 = false;
    @Option(name = "-8")
    private boolean level8 = false;
    @Option(name = "-9")
    private boolean level9 = false;

    @Argument(index = 0, required = true)
    private String treeIsh = "master";

    @Argument(index = 1, multiValued = true)
    private List<String> path = null;
  }
  private Options options = new Options();

  /** Wrapper around ZipFormat, to allow configuring the compression level.
   * The compression level is stored in a thread local storage, which must be
   * set before createArchiveOutputStream().
   */
  static class ZipFormat
      implements ArchiveCommand.Format<ArchiveOutputStream> {

    org.eclipse.jgit.archive.ZipFormat zipFormat =
        new org.eclipse.jgit.archive.ZipFormat();

    public ArchiveOutputStream createArchiveOutputStream(OutputStream s) {
      ArchiveOutputStream archiveStream =
          zipFormat.createArchiveOutputStream(s);
      Integer level = m_level.get();
      if (level != null && archiveStream instanceof ZipArchiveOutputStream) {
        ((ZipArchiveOutputStream)archiveStream).setLevel(level);
      }
      return archiveStream;
    }

    public void putEntry(ArchiveOutputStream out, String path, FileMode mode,
            org.eclipse.jgit.lib.ObjectLoader loader)
        throws IOException {
      zipFormat.putEntry(out, path, mode, loader);
    }

    public Iterable<String> suffixes() {
      return zipFormat.suffixes();
    }

    @Override
    public boolean equals(Object other) {
      return zipFormat.equals(other);
    }

    @Override
    public int hashCode() {
      return zipFormat.hashCode();
    }

    static public void setLevel(int value) {
      m_level.set(value);
    }

    static public void resetLevel() {
      m_level.remove();
    }

    private static final ThreadLocal<Integer> m_level =
        new ThreadLocal<Integer>();
  }

  /** Find the requested Zip compression and select it in ZipFormat.
   */
  private void setupZipCompressionLevel() {
    int value = Arrays.asList(options.level0, options.level1, options.level2,
        options.level3, options.level4, options.level5, options.level6,
        options.level7, options.level8, options.level9).indexOf(true);
    if (value >= 0) {
      ZipFormat.setLevel(value);
    }
  }

  /** Map of archive formats adapters.
   * Keep a map of all our archive formats, and register/unregister dynamically
   * on each request. This allows the format objects to be unloaded and re-loaded
   * if the plugin is removed or updated.
   */
  private static final ImmutableMap<String,
      ArchiveCommand.Format<ArchiveOutputStream>> s_formats;
  static {
    ImmutableMap.Builder<String,
        ArchiveCommand.Format<ArchiveOutputStream>> builder = ImmutableMap.builder();
    List<ArchiveCommand.Format<ArchiveOutputStream>> formats = Arrays.asList(
        new TarFormat(),
        new TgzFormat(),
        new Tbz2Format(),
        new TxzFormat(),
        new ZipFormat());
    for (ArchiveCommand.Format<ArchiveOutputStream> format : formats) {
      for (String ext : format.suffixes()) {
        //Remove the leading dot from the extension
        builder.put(ext.substring(1), format);
      }
    }
    s_formats = builder.build();
  }

  /** Try to register the archive format for the specified extension.
   * If the function succeeds, the unregisterArchiveFormat() should be called
   * afterwards to allow the resources to be released. In case of success, the
   * function also configures Zip compression level.
   *
   * The ArchiveCommand class supports registering the same object multiple
   * times for the same file extension: this way, multiple instances of this
   * class can safely register the same format at the same time.
   *
   * @param ext The file extension
   * @return true if the format was registered
   */
  private boolean registerArchiveFormat(String ext) {
    ArchiveCommand.Format<ArchiveOutputStream> format = s_formats.get(ext);
    if (format == null) {
      return false;
    }
    try {
      ArchiveCommand.registerFormat(ext, format);
      if ("zip".equals(ext)) {
        setupZipCompressionLevel();
      }
      return true;
    } catch (Exception e) {
      return false;
    }
  }

  /** Unregister an archive format.
   * This function should only be called after the call to
   * registerArchiveFormat() has succeeded. The function also resets the Zip
   * compression level.
   * @param ext The file extension
   */
  private void unregisterArchiveFormat(String ext) {
    ZipFormat.resetLevel();
    ArchiveCommand.unregisterFormat(ext);
  }

  /** Read and parse arguments from input stream.
   * This method gets the arguments from input stream, in Pkt-line format,
   * then parses them to fill the m_options object.
   */
  protected void readArguments() throws IOException, Failure {
    final String arg_cmd = "argument ";
    List<String> args = Lists.newArrayList();

    //Read arguments in Pkt-Line format
    PacketLineIn packetIn = new PacketLineIn(in);
    for (; ; ) {
      String s = packetIn.readString();
      if (s == PacketLineIn.END) {
        break;
      }
      if (!s.startsWith(arg_cmd)) {
        throw new Failure(1, "fatal: 'argument' token or flush expected");
      }
      String[] parts = s.substring(arg_cmd.length()).split("=", 2);
      for(String p : parts) {
        args.add(p);
      }
    }

    try {
      //Parse them into the 'm_options' field
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
      //Parse Git arguments
      readArguments();

      //Verify the user has permissions to read the specified reference
      if (!projectControl.allRefsAreVisible()) {
        throw new Failure(4, "fatal: upload-archive not permitted on this server");
      }

      //Find out the object to get from the specified reference and requested path
      ObjectId treeId = repo.resolve(options.treeIsh);
      if (treeId.equals(ObjectId.zeroId())) {
        throw new Failure(5, "fatal: reference not found");
      }

      //Build the archive
      final String format = options.format;
      final boolean wasFormatRegistered = registerArchiveFormat(format);
      try {
        //The archive is sent in DATA sideband channel
        SideBandOutputStream sidebandOut =
            new SideBandOutputStream(SideBandOutputStream.CH_DATA,
                                     SideBandOutputStream.MAX_BUF, out);
        new ArchiveCommand(repo)
            .setFormat(format)
            .setTree(treeId)
            .setPaths(options.path.toArray(new String[0]))
            .setPrefix(options.prefix)
            .setOutputStream(sidebandOut)
            .call();
        sidebandOut.flush();
        sidebandOut.close();
      } catch (GitAPIException e) {
        throw new Failure(6, "fatal: git api exception, " + e);
      } finally {
        if (wasFormatRegistered) {
          unregisterArchiveFormat(format);
        }
      }
    } catch (Failure f) {
      //Report the error in ERROR sideband channel
      SideBandOutputStream sidebandError =
          new SideBandOutputStream(SideBandOutputStream.CH_ERROR,
                                   SideBandOutputStream.MAX_BUF, out);
      sidebandError.write(f.getMessage().getBytes());
      sidebandError.flush();
      sidebandError.close();
      throw f;
    } finally {
      //In any case, cleanly close the packetOut channel
      packetOut.end();
    }
  }
}
