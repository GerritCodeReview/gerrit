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

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;

import java.io.File;
import java.util.Arrays;

public class JschIdentity {

  private String userName;
  private byte[] prvKeyData;

  private File privateKeyFile;
  private String passphrase;

  public JschIdentity(final String userName, final byte[] prvKeyData) {
    this.userName = userName;
    this.prvKeyData = prvKeyData;
  }

  public JschIdentity(final File privateKeyFile, final String passphrase) {
    this.privateKeyFile = privateKeyFile;
    this.passphrase = passphrase;
  }

  public void addTo(final JSch jsch) throws JSchException {
    if (prvKeyData != null) {
      // make defensive copy as the private key will be nulled by JSch
      jsch.addIdentity(userName, Arrays.copyOf(prvKeyData, prvKeyData.length),
          null, null);
    } else {
      jsch.addIdentity(privateKeyFile.getAbsolutePath(), passphrase);
    }
  }
}
