// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.acceptance;

import java.io.ByteArrayOutputStream;

import com.jcraft.jsch.KeyPair;


public class TestAccount {
  final String username;
  final String email;
  final String fullName;
  final KeyPair sshKey;
  final String httpPassword;

  TestAccount(String username, String email, String fullName,
      KeyPair sshKey, String httpPassword) {
    this.username = username;
    this.email = email;
    this.fullName = fullName;
    this.sshKey = sshKey;
    this.httpPassword = httpPassword;
  }

  public byte[] privateKey() {
    ByteArrayOutputStream out = new ByteArrayOutputStream();
    sshKey.writePrivateKey(out);
    return out.toByteArray();
  }
}
