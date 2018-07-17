// Copyright (C) 2012 The Android Open Source Project
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

import static com.google.gerrit.sshd.CommandMetaData.Mode.MASTER_OR_SLAVE;

import com.google.common.base.Strings;
import com.google.gerrit.server.plugins.PluginInstallException;
import com.google.gerrit.sshd.CommandMetaData;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.Option;

@CommandMetaData(name = "install", description = "Install/Add a plugin", runsAt = MASTER_OR_SLAVE)
final class PluginInstallCommand extends PluginAdminSshCommand {
  @Option(
      name = "--name",
      aliases = {"-n"},
      usage = "install under name")
  private String name;

  @Option(name = "-")
  void useInput(@SuppressWarnings("unused") boolean on) {
    source = "-";
  }

  @Argument(index = 0, metaVar = "-|URL", usage = "JAR to load")
  private String source;

  @SuppressWarnings("resource")
  @Override
  protected void doRun() throws UnloggedFailure {
    if (Strings.isNullOrEmpty(source)) {
      throw die("Argument \"-|URL\" is required");
    }
    if (Strings.isNullOrEmpty(name) && "-".equalsIgnoreCase(source)) {
      throw die("--name required when source is stdin");
    }

    if (Strings.isNullOrEmpty(name)) {
      int s = source.lastIndexOf('/');
      if (0 <= s) {
        name = source.substring(s + 1);
      } else {
        name = source;
      }
    }

    InputStream data;
    if ("-".equalsIgnoreCase(source)) {
      data = in;
    } else if (new File(source).isFile() && source.equals(new File(source).getAbsolutePath())) {
      try {
        data = Files.newInputStream(new File(source).toPath());
      } catch (IOException e) {
        throw die("cannot read " + source);
      }
    } else {
      try {
        data = new URL(source).openStream();
      } catch (MalformedURLException e) {
        throw die("invalid url " + source);
      } catch (IOException e) {
        throw die("cannot read " + source);
      }
    }
    try {
      loader.installPluginFromStream(name, data);
    } catch (IOException e) {
      throw die("cannot install plugin");
    } catch (PluginInstallException e) {
      e.printStackTrace(stderr);
      String msg = String.format("Plugin failed to install. Cause: %s", e.getMessage());
      throw die(msg);
    } finally {
      try {
        data.close();
      } catch (IOException err) {
        // Ignored
      }
    }
  }
}
