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

package com.google.gerrit.pgm.backup;

import com.google.gwtorm.client.Column;
import com.google.gwtorm.protobuf.CodecFactory;
import com.google.gwtorm.protobuf.ProtobufCodec;

public class Counters {
  public static final ProtobufCodec<Counters> CODEC =
      CodecFactory.encoder(Counters.class);

  @Column(id = 1)
  public int accountGroupId;

  @Column(id = 2)
  public int accountId;

  @Column(id = 3)
  public int changeId;

  @Column(id = 4)
  public int changeMessageId;

  @Column(id = 5)
  public int contributorAgreementId;
}
