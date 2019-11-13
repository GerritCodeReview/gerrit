// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.acceptance.ssh;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import com.google.common.base.Splitter;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.PushOneCommit.Result;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.acceptance.config.GerritConfig;
import com.google.gerrit.git.ObjectIds;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Set;
import java.util.TreeSet;
import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.eclipse.jgit.transport.PacketLineIn;
import org.eclipse.jgit.transport.PacketLineOut;
import org.eclipse.jgit.util.IO;
import org.junit.Test;

@NoHttpd
@UseSsh
public class UploadArchiveIT extends AbstractDaemonTest {

  @Test
  @GerritConfig(name = "download.archive", value = "off")
  public void archiveFeatureOff() throws Exception {
    assertArchiveNotPermitted();
  }

  @Test
  @GerritConfig(
      name = "download.archive",
      values = {"tar", "tbz2", "tgz", "txz"})
  public void zipFormatDisabled() throws Exception {
    assertArchiveNotPermitted();
  }

  @Test
  public void zipFormat() throws Exception {
    PushOneCommit.Result r = createChange();
    String abbreviated = abbreviateName(r);
    String c = command(r, "zip", abbreviated);

    InputStream out =
        adminSshSession.exec2("git-upload-archive " + project.get(), argumentsToInputStream(c));

    // Wrap with PacketLineIn to read ACK bytes from output stream
    PacketLineIn in = new PacketLineIn(out);
    String tmp = in.readString();
    assertThat(tmp).isEqualTo("ACK");
    in.readString();

    // Skip length (4 bytes) + 1 byte
    // to position the output stream to the raw zip stream
    byte[] buffer = new byte[5];
    IO.readFully(out, buffer, 0, 5);
    Set<String> entryNames = new TreeSet<>();
    try (ZipArchiveInputStream zip = new ZipArchiveInputStream(out)) {
      ZipArchiveEntry zipEntry = zip.getNextZipEntry();
      while (zipEntry != null) {
        String name = zipEntry.getName();
        entryNames.add(name);
        zipEntry = zip.getNextZipEntry();
      }
    }

    assertThat(entryNames)
        .containsExactly(
            String.format("%s/", abbreviated),
            String.format("%s/%s", abbreviated, PushOneCommit.FILE_NAME))
        .inOrder();
  }

  // Make sure we have coverage for the dependency on xz.
  @Test
  public void txzFormat() throws Exception {
    PushOneCommit.Result r = createChange();
    String abbreviated = abbreviateName(r);
    String c = command(r, "tar.xz", abbreviated);

    try (InputStream out =
        adminSshSession.exec2("git-upload-archive " + project.get(), argumentsToInputStream(c))) {

      // Wrap with PacketLineIn to read ACK bytes from output stream
      PacketLineIn in = new PacketLineIn(out);
      String packet = in.readString();
      assertThat(packet).isEqualTo("ACK");

      // Discard first bit of data, which should be empty.
      packet = in.readString();
      assertThat(packet).isEmpty();

      // Make sure the next one is not on the error channel
      packet = in.readString();

      // 1 = DATA. It would be nicer to parse the OutputStream with SideBandInputStream from JGit,
      // but
      // that is currently not public.
      char channel = packet.charAt(0);
      if (channel != 1) {
        assertWithMessage("got packet on channel " + (int) channel, packet).fail();
      }
    }
  }

  private String command(PushOneCommit.Result r, String format, String abbreviated) {
    String c =
        String.format(
            "-f=%s --prefix=%s/ %s %s",
            format, abbreviated, r.getCommit().name(), PushOneCommit.FILE_NAME);
    return c;
  }

  private void assertArchiveNotPermitted() throws Exception {
    PushOneCommit.Result r = createChange();
    String abbreviated = abbreviateName(r);
    String c = command(r, "zip", abbreviated);

    InputStream out =
        adminSshSession.exec2("git-upload-archive " + project.get(), argumentsToInputStream(c));

    // Wrap with PacketLineIn to read ACK bytes from output stream
    PacketLineIn in = new PacketLineIn(out);
    String tmp = in.readString();
    assertThat(tmp).isEqualTo("ACK");
    in.readString();
    tmp = in.readString();
    tmp = tmp.substring(1);
    assertThat(tmp).isEqualTo("fatal: upload-archive not permitted for format zip");
  }

  private String abbreviateName(Result r) throws Exception {
    return ObjectIds.abbreviateName(r.getCommit(), 8, testRepo.getRevWalk().getObjectReader());
  }

  private InputStream argumentsToInputStream(String c) throws Exception {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    PacketLineOut pctOut = new PacketLineOut(out);
    for (String arg : Splitter.on(' ').split(c)) {
      pctOut.writeString("argument " + arg);
    }
    pctOut.end();
    return new ByteArrayInputStream(out.toByteArray());
  }
}
