// Copyright (C) 2010 The Android Open Source Project
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

import com.google.gwtorm.client.Column;

import java.util.Collection;
import java.util.Collections;

/** Wrapper around a Collection<SshKeyCacheEntry> */
class SshKeyCacheEntryCollection {
  public enum Type {
    VALID_HAS_KEYS, INVALID_USER, NO_SUCH_USER, NO_KEYS
  }

  @Column(id = 1)
  protected Collection<SshKeyCacheEntry> sshKeyIter;

  @Column(id = 2)
  protected Type type;

  SshKeyCacheEntryCollection() {
    sshKeyIter = Collections.emptyList();
  }

  SshKeyCacheEntryCollection(Collection<SshKeyCacheEntry> sshKeyIter) {
    this.sshKeyIter = sshKeyIter;
    this.type = Type.VALID_HAS_KEYS;
  }

  SshKeyCacheEntryCollection(Type type) {
    sshKeyIter = Collections.emptyList();
    this.type = type;
  }

  Iterable<SshKeyCacheEntry> getSshKeyCacheEntries() {
    return sshKeyIter;
  }

  Type getType() {
    return type;
  }
}
