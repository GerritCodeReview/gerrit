// Copyright (C) 2026 The Android Open Source Project
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

package com.google.gerrit.server.git;

import com.google.inject.ImplementedBy;
import java.io.IOException;

/**
 * Optional server attestation of finalized, generated content, never of uploaded client objects.
 */
@ImplementedBy(ServerCommitSigner.Disabled.class)
public interface ServerCommitSigner {
  /**
   * Signs an unsigned payload if an active key is configured. Existing signature headers and their
   * bytes are never changed. The caller must independently verify the result before insertion.
   */
  byte[] signIfUnsigned(byte[] rawCommit) throws IOException;

  class Disabled implements ServerCommitSigner {
    @Override
    public byte[] signIfUnsigned(byte[] rawCommit) {
      return rawCommit;
    }
  }
}
