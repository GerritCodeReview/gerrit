// Copyright (C) 2011 The Android Open Source Project
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

package com.google.gerrit.test;

import com.jcraft.jsch.JSchException;

import org.eclipse.jgit.lib.PersonIdent;

import java.io.File;
import java.net.URL;

public class ConfiguredGerritServer implements GerritServer {

  private final static String USER = GerritTestProperty.USER.getOrFail();
  private final static String PASSWORD = GerritTestProperty.PASSWORD.get();
  private final static int SSH_PORT = GerritTestProperty.SSH_PORT.get();
  private final static File SSH_KEY = GerritTestProperty.SSH_KEY.getOrFail();
  private final static String PASSPHRASE = GerritTestProperty.PASSPHRASE.get();

  private final URL url;
  private final PersonIdent adminIdent;

  ConfiguredGerritServer(final URL url) {
    this.url = url;
    this.adminIdent = createAdminIdentity(url);
  }

  private static PersonIdent createAdminIdentity(final URL url) {
    final GerritWebInterface web = new GerritWebInterface(url);
    try {
      web.logout();
      web.login(USER, "", PASSWORD);
      final PersonIdent adminIdent = web.getPersonIdent();
      web.logout();
      return adminIdent;
    } finally {
      web.close();
    }
  }

  @Override
  public String getAdminUser() {
    return USER;
  }

  public String getAdminPassword() {
    return PASSWORD;
  }

  @Override
  public PersonIdent getAdminIdent() {
    return adminIdent;
  }

  @Override
  public GerritWebInterface createWeb() {
    return new GerritWebInterface(url);
  }

  @Override
  public GerritSshInterface createSsh(final String user) throws JSchException {
    return new GerritSshInterface(url.getHost(), SSH_PORT, USER, SSH_KEY,
        PASSPHRASE);
  }

  @Override
  public void close() {
  }
}
