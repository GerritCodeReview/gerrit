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

package com.google.gerrit.extensions.common;

/** Request-time verification of a stored Git commit's signature. */
public class CommitSignatureInfo {
  public enum Status {
    VERIFIED,
    UNSIGNED,
    UNVERIFIED
  }

  public enum Reason {
    UNSUPPORTED_FORMAT,
    MALFORMED_SIGNATURE,
    UNKNOWN_KEY,
    INVALID_SIGNATURE,
    INVALID_KEY,
    IDENTITY_MISMATCH
  }

  public Status status;
  public String signingKeyFingerprint;
  public String primaryKeyFingerprint;
  public AccountInfo signer;
  public Reason reason;
  public String message;
}
