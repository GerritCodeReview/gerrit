// Copyright (C) 2023 The Android Open Source Project
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

package com.google.gerrit.server.account.externalids;

import static com.google.common.base.Preconditions.checkState;

import com.google.gerrit.git.ObjectIds;
import com.google.inject.Singleton;

@Singleton
public class ExternalIdSerializerNoteDbImpl implements ExternalIdSerializer {
  @Override
  public byte[] toByteArray(ExternalId extId) {
    checkState(extId.blobId() != null, "Missing blobId in external ID %s", extId.key().get());
    byte[] b = new byte[2 * ObjectIds.STR_LEN + 1];
    extId.key().sha1().copyTo(b, 0);
    b[ObjectIds.STR_LEN] = ':';
    extId.blobId().copyTo(b, ObjectIds.STR_LEN + 1);
    return b;
  }
}
