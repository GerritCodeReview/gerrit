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

package com.google.gerrit.common.data;

/** Description of the SSH daemon host key used by Gerrit. */
public class SshHostKey {
  protected String hostIdent;
  protected String hostKey;
  protected String fingerprint;

  protected SshHostKey() {}

  public SshHostKey(final String hi, final String hk, final String fp) {
    hostIdent = hi;
    hostKey = hk;
    fingerprint = fp;
  }

  /** @return host name string, to appear in a known_hosts file. */
  public String getHostIdent() {
    return hostIdent;
  }

  /** @return base 64 encoded host key string, starting with key type. */
  public String getHostKey() {
    return hostKey;
  }

  /** @return the key fingerprint, as displayed by a connecting client. */
  public String getFingerprint() {
    return fingerprint;
  }
}
