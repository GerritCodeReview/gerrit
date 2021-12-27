// Copyright (C) 2020 The Android Open Source Project
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

package com.google.gerrit.testing;

import com.google.common.base.Strings;
import com.google.common.collect.ImmutableMap;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

public class TestLoggingActivator {
  private static final ImmutableMap<String, Level> LOG_LEVELS =
      ImmutableMap.<String, Level>builder()
          .put("com.google.gerrit", getGerritLogLevel())

          // Silence non-critical messages from MINA SSHD.
          .put("org.apache.mina", Level.WARNING)
          .put("org.apache.sshd.client", Level.WARNING)
          .put("org.apache.sshd.common", Level.WARNING)
          .put("org.apache.sshd.server", Level.WARNING)
          .put("org.apache.sshd.common.keyprovider.FileKeyPairProvider", Level.INFO)
          .put("com.google.gerrit.sshd.GerritServerSession", Level.WARNING)

          // Silence non-critical messages from mime-util.
          .put("eu.medsea.mimeutil", Level.WARNING)

          // Silence non-critical messages from openid4java.
          .put("org.apache.xml", Level.WARNING)
          .put("org.openid4java", Level.WARNING)
          .put("org.openid4java.consumer.ConsumerManager", Level.SEVERE)
          .put("org.openid4java.discovery.Discovery", Level.SEVERE)
          .put("org.openid4java.server.RealmVerifier", Level.SEVERE)
          .put("org.openid4java.message.AuthSuccess", Level.SEVERE)

          // Silence non-critical messages from apache.http.
          .put("org.apache.http", Level.WARNING)

          // Silence non-critical messages from Jetty.
          .put("org.eclipse.jetty", Level.WARNING)

          // Silence non-critical messages from JGit.
          .put("org.eclipse.jgit.transport.PacketLineIn", Level.WARNING)
          .put("org.eclipse.jgit.transport.PacketLineOut", Level.WARNING)
          .put("org.eclipse.jgit.internal.transport.sshd", Level.WARNING)
          .put("org.eclipse.jgit.util.FileUtils", Level.WARNING)
          .put("org.eclipse.jgit.internal.storage.file.FileSnapshot", Level.WARNING)
          .put("org.eclipse.jgit.util.FS", Level.WARNING)
          .put("org.eclipse.jgit.util.SystemReader", Level.WARNING)
          .build();

  private static Level getGerritLogLevel() {
    String value = Strings.nullToEmpty(System.getenv("GERRIT_LOG_LEVEL"));
    if (value.isEmpty()) {
      value = Strings.nullToEmpty(System.getProperty("gerrit.logLevel"));
    }

    try {
      return Level.parse(value);
    } catch (IllegalArgumentException e) {
      // for backwards compatibility handle log4j log levels
      if (value.equalsIgnoreCase("FATAL") || value.equalsIgnoreCase("ERROR")) {
        return Level.SEVERE;
      }
      if (value.equalsIgnoreCase("WARN")) {
        return Level.WARNING;
      }
      if (value.equalsIgnoreCase("DEBUG")) {
        return Level.FINE;
      }
      if (value.equalsIgnoreCase("TRACE")) {
        return Level.FINEST;
      }

      return Level.INFO;
    }
  }

  public static void configureLogging() {
    LogManager.getLogManager().reset();
    FloggerInitializer.initBackend();

    ConsoleHandler dst = new ConsoleHandler();
    dst.setLevel(Level.FINEST);

    Logger.getLogger(Logger.GLOBAL_LOGGER_NAME).addHandler(dst);

    LOG_LEVELS.entrySet().stream()
        .forEach(
            e -> {
              Logger logger = Logger.getLogger(e.getKey());
              logger.setLevel(e.getValue());
              logger.addHandler(dst);
            });
  }

  private TestLoggingActivator() {}
}
