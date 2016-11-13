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

package com.google.gerrit.pgm.init;

import static com.google.gerrit.common.FileUtil.chmod;
import static com.google.gerrit.pgm.init.api.InitUtil.die;
import static com.google.gerrit.pgm.init.api.InitUtil.hostname;
import static java.nio.file.Files.exists;

import com.google.gerrit.pgm.init.api.ConsoleUI;
import com.google.gerrit.pgm.init.api.InitStep;
import com.google.gerrit.pgm.init.api.Section;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.util.HostPlatform;
import com.google.gerrit.server.util.SocketUtil;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import java.io.IOException;
import java.lang.ProcessBuilder.Redirect;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.sshd.common.util.SecurityUtils;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

/** Initialize the {@code sshd} configuration section. */
@Singleton
class InitSshd implements InitStep {
  private final ConsoleUI ui;
  private final SitePaths site;
  private final Libraries libraries;
  private final Section sshd;

  @Inject
  InitSshd(
      final ConsoleUI ui,
      final SitePaths site,
      final Libraries libraries,
      final Section.Factory sections) {
    this.ui = ui;
    this.site = site;
    this.libraries = libraries;
    this.sshd = sections.get("sshd", null);
  }

  @Override
  public void run() throws Exception {
    ui.header("SSH Daemon");

    String hostname = "*";
    int port = 29418;
    String listenAddress = sshd.get("listenAddress");
    if (isOff(listenAddress)) {
      hostname = "off";
    } else if (listenAddress != null && !listenAddress.isEmpty()) {
      final InetSocketAddress addr = SocketUtil.parse(listenAddress, port);
      hostname = SocketUtil.hostname(addr);
      port = addr.getPort();
    }

    hostname = ui.readString(hostname, "Listen on address");
    if (isOff(hostname)) {
      sshd.set("listenAddress", "off");
      return;
    }

    port = ui.readInt(port, "Listen on port");
    sshd.set("listenAddress", SocketUtil.format(hostname, port));

    if (exists(site.ssh_rsa) || exists(site.ssh_dsa)) {
      libraries.bouncyCastleSSL.downloadRequired();
    } else if (!exists(site.ssh_key)) {
      libraries.bouncyCastleSSL.downloadOptional();
    }

    generateSshHostKeys();
  }

  private static boolean isOff(String listenHostname) {
    return "off".equalsIgnoreCase(listenHostname)
        || "none".equalsIgnoreCase(listenHostname)
        || "no".equalsIgnoreCase(listenHostname);
  }

  private void generateSshHostKeys() throws InterruptedException, IOException {
    if (!exists(site.ssh_key) //
        && !exists(site.ssh_rsa) //
        && !exists(site.ssh_dsa)) {
      System.err.print("Generating SSH host key ...");
      System.err.flush();

      if (SecurityUtils.isBouncyCastleRegistered()) {
        // Generate the SSH daemon host key using ssh-keygen.
        //
        final String comment = "gerrit-code-review@" + hostname();

        // Workaround for JDK-6518827 - zero-length argument ignored on Win32
        String emptyPassphraseArg = HostPlatform.isWin32() ? "\"\"" : "";

        System.err.print(" rsa...");
        System.err.flush();
        new ProcessBuilder(
                "ssh-keygen",
                "-q" /* quiet */,
                "-t",
                "rsa",
                "-P",
                emptyPassphraseArg,
                "-C",
                comment,
                "-f",
                site.ssh_rsa.toAbsolutePath().toString())
            .redirectError(Redirect.INHERIT)
            .redirectOutput(Redirect.INHERIT)
            .start()
            .waitFor();

        System.err.print(" dsa...");
        System.err.flush();
        new ProcessBuilder(
                "ssh-keygen",
                "-q" /* quiet */,
                "-t",
                "dsa",
                "-P",
                emptyPassphraseArg,
                "-C",
                comment,
                "-f",
                site.ssh_dsa.toAbsolutePath().toString())
            .redirectError(Redirect.INHERIT)
            .redirectOutput(Redirect.INHERIT)
            .start()
            .waitFor();

      } else {
        // Generate the SSH daemon host key ourselves. This is complex
        // because SimpleGeneratorHostKeyProvider doesn't mark the data
        // file as only readable by us, exposing the private key for a
        // short period of time. We try to reduce that risk by creating
        // the key within a temporary directory.
        //
        Path tmpdir = site.etc_dir.resolve("tmp.sshkeygen");
        try {
          Files.createDirectory(tmpdir);
        } catch (IOException e) {
          throw die("Cannot create directory " + tmpdir, e);
        }
        chmod(0600, tmpdir);

        Path tmpkey = tmpdir.resolve(site.ssh_key.getFileName().toString());
        SimpleGeneratorHostKeyProvider p;

        System.err.print(" rsa(simple)...");
        System.err.flush();
        p = new SimpleGeneratorHostKeyProvider();
        p.setPath(tmpkey.toAbsolutePath());
        p.setAlgorithm("RSA");
        p.loadKeys(); // forces the key to generate.
        chmod(0600, tmpkey);

        try {
          Files.move(tmpkey, site.ssh_key);
        } catch (IOException e) {
          throw die("Cannot rename " + tmpkey + " to " + site.ssh_key, e);
        }
        try {
          Files.delete(tmpdir);
        } catch (IOException e) {
          throw die("Cannot delete " + tmpdir, e);
        }
      }
      System.err.println(" done");
    }
  }
}
