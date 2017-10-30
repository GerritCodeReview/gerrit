// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.sshd;

import com.google.common.io.BaseEncoding;
import com.google.gerrit.extensions.events.LifecycleListener;
import com.google.gerrit.server.config.SitePaths;
import com.google.gerrit.server.ssh.SshInfo;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.jcraft.jsch.HostKey;
import java.io.IOException;
import java.io.Writer;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class ExternalDaemon implements SshInfo, LifecycleListener {
  private static final Logger log = LoggerFactory.getLogger(ExternalDaemon.class);

  private final List<HostKey> hostKeys;
  private final Path infoPath;
  private final ExecutorService startExecutor;
  private final ExternalStream.Factory streamFactory;
  private ServerSocket sock;
  private String authKey; // TODO(sop) support rotating keys
  private AcceptorThread acceptor;

  @Inject
  ExternalDaemon(
      SitePaths paths, ExternalStream.Factory streamFactory, CommandFactoryProvider cmdProvider) {
    hostKeys = Collections.emptyList();
    infoPath = paths.tmp_dir.resolve("sshd_backend");
    this.streamFactory = streamFactory;
    startExecutor = cmdProvider.getStartExecutor();
  }

  @Override
  public synchronized void start() {
    try {
      InetAddress localhost = InetAddress.getLoopbackAddress();
      sock = new ServerSocket(0, 4, localhost);
      authKey = generateRandomAuthKey();
      writeInfoFile();

      acceptor = new AcceptorThread();
      acceptor.start();
    } catch (IOException e) {
      log.error("cannot start SSH accept socket", e);
    }
  }

  @Override
  public synchronized void stop() {
    if (acceptor != null) {
      try {
        acceptor.stopAndWait();
      } catch (InterruptedException e) {
        log.warn("interrupted waiting for acceptor to stop", e);
      } finally {
        acceptor = null;
      }
    }

    if (sock != null) {
      try {
        sock.close();
      } catch (IOException e) {
        log.warn("cannot close accept socket", e);
      } finally {
        sock = null;
      }
    }
  }

  @Override
  public List<HostKey> getHostKeys() {
    return hostKeys;
  }

  boolean checkAuth(String auth) {
    return authKey.equals(auth);
  }

  private void writeInfoFile() throws IOException {
    Path tmp = Files.createTempFile(infoPath.getParent(), ".sshd_backend_", "");
    try (Writer out = Files.newBufferedWriter(tmp, StandardCharsets.UTF_8)) {
      Set<PosixFilePermission> perms = new HashSet<>();
      perms.add(PosixFilePermission.OWNER_READ);
      perms.add(PosixFilePermission.OWNER_WRITE);
      Files.setPosixFilePermissions(tmp, perms);

      out.write(Integer.toString(sock.getLocalPort()) + '\n');
      out.write(authKey + '\n');
    }
    Files.move(tmp, infoPath, StandardCopyOption.REPLACE_EXISTING);
  }

  private static String generateRandomAuthKey() {
    byte[] raw = new byte[32 / 5 * 8];
    new Random().nextBytes(raw);
    return BaseEncoding.base64().omitPadding().encode(raw);
  }

  private class AcceptorThread extends Thread {
    private volatile boolean accept = true;

    AcceptorThread() {
      super("SSHD-Acceptor");
    }

    @Override
    public void run() {
      try {
        while (accept) {
          Socket s = sock.accept();
          streamFactory.create(s).begin(startExecutor);
        }
      } catch (IOException e) {
        log.error("cannot accept SSH stream", e);
      }
    }

    void stopAndWait() throws InterruptedException {
      accept = false;
      join();
    }
  }
}
