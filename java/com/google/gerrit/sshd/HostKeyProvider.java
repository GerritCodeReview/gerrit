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

import com.google.common.flogger.FluentLogger;
import com.google.gerrit.server.config.SitePaths;
import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.inject.ProvisionException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.keyprovider.FileKeyPairProvider;
import org.apache.sshd.common.keyprovider.KeyPairProvider;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;

class HostKeyProvider implements Provider<KeyPairProvider> {
  private static final FluentLogger logger = FluentLogger.forEnclosingClass();
  private final SitePaths site;

  @Inject
  HostKeyProvider(SitePaths site) {
    this.site = site;
  }

  @Override
  public KeyPairProvider get() {
    Path objKey = site.ssh_key;
    Path rsaKey = site.ssh_rsa;
    Path ecdsaKey_256 = site.ssh_ecdsa_256;
    Path ecdsaKey_384 = site.ssh_ecdsa_384;
    Path ecdsaKey_521 = site.ssh_ecdsa_521;
    Path ed25519Key = site.ssh_ed25519;

    final List<Path> stdKeys = new ArrayList<>(6);
    if (Files.exists(rsaKey)) {
      stdKeys.add(rsaKey);
    }
    if (Files.exists(ecdsaKey_256)) {
      stdKeys.add(ecdsaKey_256);
    }
    if (Files.exists(ecdsaKey_384)) {
      stdKeys.add(ecdsaKey_384);
    }
    if (Files.exists(ecdsaKey_521)) {
      stdKeys.add(ecdsaKey_521);
    }
    if (Files.exists(ed25519Key)) {
      stdKeys.add(ed25519Key);
    }

    if (Files.exists(objKey)) {
      if (Files.exists(rsaKey)) {
        // Both formats of host key exist, we don't know which format
        // should be authoritative. Complain and abort.
        throw new ProvisionException("Multiple host ssh-rsa keys exist: " + stdKeys);
      }
      if (stdKeys.isEmpty()) {
        SimpleGeneratorHostKeyProvider p = new SimpleGeneratorHostKeyProvider();
        p.setAlgorithm(KeyUtils.RSA_ALGORITHM);
        p.setPath(objKey.toAbsolutePath());
        logger.atWarning().log("Only an ssh-rsa host key type exists. "
            + "This is a weak key type, consider adding newer key types by running gerrit init.");
        return p;
      }
      stdKeys.add(objKey);
    }
    if (stdKeys.isEmpty()) {
      throw new ProvisionException("No SSH keys under " + site.etc_dir);
    }
    FileKeyPairProvider kp = new FileKeyPairProvider();
    kp.setPaths(stdKeys);
    return kp;
  }
}
