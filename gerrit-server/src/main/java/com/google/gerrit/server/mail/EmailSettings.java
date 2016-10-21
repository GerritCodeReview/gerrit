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

package com.google.gerrit.server.mail;

import com.google.gerrit.server.config.GerritServerConfig;
import com.google.gerrit.server.mail.receive.Protocol;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import org.eclipse.jgit.lib.Config;

@Singleton
public class EmailSettings {
  // Send
  public final boolean html;
  public final boolean includeDiff;
  public final int maximumDiffSize;
  // Receive
  public final Protocol protocol;
  public final String host;
  public final int port;
  public final String username;
  public final String password;
  public final Encryption encryption;
  public final int fetchInterval; // in seconds

  @Inject
  EmailSettings(@GerritServerConfig Config cfg) {
    // Send
    html = cfg.getBoolean("sendemail", "html", true);
    includeDiff = cfg.getBoolean("sendemail", "includeDiff", false);
    maximumDiffSize = cfg.getInt("sendemail", "maximumDiffSize", 256 << 10);
    // Receive
    protocol = cfg.getEnum("receiveemail", null, "protocol", Protocol.NONE);
    host = cfg.getString("receiveemail", null, "host");
    port = cfg.getInt("receiveemail", "port", 0);
    username = cfg.getString("receiveemail", null, "username");
    password = cfg.getString("receiveemail", null, "password");
    encryption =
        cfg.getEnum("receiveemail", null, "encryption", Encryption.NONE);
    fetchInterval = cfg.getInt("receiveemail", "fetchInterval", 60);
  }
}
