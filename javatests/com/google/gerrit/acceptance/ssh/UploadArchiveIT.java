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
import static com.google.common.truth.TruthJUnit.assume;

import com.google.common.base.Splitter;
import com.google.gerrit.acceptance.AbstractDaemonTest;
import com.google.gerrit.acceptance.GerritConfig;
import com.google.gerrit.acceptance.NoHttpd;
import com.google.gerrit.acceptance.PushOneCommit;
import com.google.gerrit.acceptance.UseSsh;
import com.google.gerrit.testutil.NoteDbMode;
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
import org.junit.Before;
import org.junit.Test;

@NoHttpd
@UseSsh
public class UploadArchiveIT extends AbstractDaemonTest {

  @Before
  public void setUp() {
    // There is some Guice request scoping problem preventing this test from
    // passing in CHECK mode.
    assume().that(NoteDbMode.get()).isNotEqualTo(NoteDbMode.CHECK);
  }

  @Test
  @GerritConfig(name = "download.archive", value = "off")
  public void archiveFeatureOff() throws Exception {
    archiveNotPermitted();
  }

  @Test
  @GerritConfig(
    name = "download.archive",
    values = {"tar", "tbz2", "tgz", "txz"}
  )
  public void zipFormatDisabled() throws Exception {
    archiveNotPermitted();
  }

  @Test
  public void zipFormat() throws Exception {
    PushOneCommit.Result r = createChange();
    String abbreviated = r.getCommit().abbreviate(8).name();
    String c = command(r, abbreviated);

    InputStream out =
        adminSshSession.exec2("git-upload-archive " + project.get(), argumentsToInputStream(c));

    // Wrap with PacketLineIn to read ACK bytes from output stream
    PacketLineIn in = new PacketLineIn(out);
    String tmp = in.readString();
    assertThat(tmp).isEqualTo("ACK");
    tmp = in.readString();

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

  private String command(PushOneCommit.Result r, String abbreviated) {
    String c =
        "-f=zip "
            + "-9 "
            + "--prefix="
            + abbreviated
            + "/ "
            + r.getCommit().name()
            + " "
            + PushOneCommit.FILE_NAME;
    return c;
  }

  private void archiveNotPermitted() throws Exception {
    PushOneCommit.Result r = createChange();
    String abbreviated = r.getCommit().abbreviate(8).name();
    String c = command(r, abbreviated);

    InputStream out =
        adminSshSession.exec2("git-upload-archive " + project.get(), argumentsToInputStream(c));

    // Wrap with PacketLineIn to read ACK bytes from output stream
    PacketLineIn in = new PacketLineIn(out);
    String tmp = in.readString();
    assertThat(tmp).isEqualTo("ACK");
    tmp = in.readString();
    tmp = in.readString();
    tmp = tmp.substring(1);
    assertThat(tmp).isEqualTo("fatal: upload-archive not permitted");
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
