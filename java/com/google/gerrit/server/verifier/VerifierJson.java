// Copyright (C) 2019 The Android Open Source Project
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

package com.google.gerrit.server.verifier;

import com.google.gerrit.extensions.api.verifiers.VerifierInfo;
import com.google.inject.Singleton;

/** Formats a {@link Verifier} as JSON. */
@Singleton
public class VerifierJson {
  public VerifierInfo format(Verifier verifier) {
    VerifierInfo info = new VerifierInfo();
    info.uuid = verifier.getUuid();
    info.name = verifier.getName();
    info.description = verifier.getDescription().orElse(null);
    info.createdOn = verifier.getCreatedOn();
    return info;
  }
}
