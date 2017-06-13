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

package com.google.gerrit.sshd;

import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

class HostKeyProvider implements Provider<KeyPairProvider> {
  private final SitePaths site;

  @Inject
  HostKeyProvider(SitePaths site) {
    this.site = site;
  }

  @Override
  public KeyPairProvider get() {
    Path objKey = site.ssh_key;
    Path rsaKey = site.ssh_rsa;
    Path dsaKey = site.ssh_dsa;
    Path ecdsaKey_256 = site.ssh_ecdsa_256;
    Path ecdsaKey_384 = site.ssh_ecdsa_384;
    Path ecdsaKey_521 = site.ssh_ecdsa_521;
    Path ed25519Key = site.ssh_ed25519;

    final List<File> stdKeys = new ArrayList<>(6);
    if (Files.exists(rsaKey)) {
      stdKeys.add(rsaKey.toAbsolutePath().toFile());
    }
    if (Files.exists(dsaKey)) {
      stdKeys.add(dsaKey.toAbsolutePath().toFile());
    }
    if (Files.exists(ecdsaKey_256)) {
      stdKeys.add(ecdsaKey_256.toAbsolutePath().toFile());
    }
    if (Files.exists(ecdsaKey_384)) {
      stdKeys.add(ecdsaKey_384.toAbsolutePath().toFile());
    }
    if (Files.exists(ecdsaKey_521)) {
      stdKeys.add(ecdsaKey_521.toAbsolutePath().toFile());
    }
    if (Files.exists(ed25519Key)) {
      stdKeys.add(ed25519Key.toAbsolutePath().toFile());
    }

    if (Files.exists(objKey)) {
      if (stdKeys.isEmpty()) {
        SimpleGeneratorHostKeyProvider p = new SimpleGeneratorHostKeyProvider();
        p.setPath(objKey.toAbsolutePath());
        return p;
      }
      // Both formats of host key exist, we don't know which format
      // should be authoritative. Complain and abort.
      //
      stdKeys.add(objKey.toAbsolutePath().toFile());
      throw new ProvisionException("Multiple host keys exist: " + stdKeys);
    }
    if (stdKeys.isEmpty()) {
      throw new ProvisionException("No SSH keys under " + site.etc_dir);
    }
    FileKeyPairProvider kp = new FileKeyPairProvider();
    kp.setFiles(stdKeys);
    return kp;
  }
}
