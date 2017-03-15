// Copyright (C) 2017 The Android Open Source Project
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

package com.google.gerrit.server.notedb;

import org.eclipse.jgit.revwalk.FooterKey;

/** State of an unregistered CC on a change. */
public enum UnregisteredCcState {
  /** The unregistered CC was added to the change. */
  ADDED(new FooterKey("Unregistered-cc")),

  /** The unregistered CC was previously added to the change, but was removed. */
  REMOVED(new FooterKey("Unregistered-cc-removed"));

  private final FooterKey footerKey;

  UnregisteredCcState(FooterKey footerKey) {
    this.footerKey = footerKey;
  }

  FooterKey getFooterKey() {
    return footerKey;
  }
}
