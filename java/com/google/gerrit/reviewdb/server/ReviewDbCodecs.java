// Copyright (C) 2018 The Android Open Source Project
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

package com.google.gerrit.reviewdb.server;

import com.google.gerrit.reviewdb.client.Change;
import com.google.gerrit.reviewdb.client.ChangeMessage;
import com.google.gerrit.reviewdb.client.PatchSet;
import com.google.gerrit.reviewdb.client.PatchSetApproval;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;

/** {@link ProtobufCodec} instances for ReviewDb types. */
public class ReviewDbCodecs {
  public static final ProtobufCodec<PatchSetApproval> APPROVAL_CODEC =
      CodecFactory.encoder(PatchSetApproval.class);

  public static final ProtobufCodec<Change> CHANGE_CODEC = CodecFactory.encoder(Change.class);

  public static final ProtobufCodec<ChangeMessage> MESSAGE_CODEC =
      CodecFactory.encoder(ChangeMessage.class);

  public static final ProtobufCodec<PatchSet> PATCH_SET_CODEC =
      CodecFactory.encoder(PatchSet.class);

  private ReviewDbCodecs() {}
}
