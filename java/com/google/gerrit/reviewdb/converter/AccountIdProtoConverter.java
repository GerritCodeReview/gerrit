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

package com.google.gerrit.reviewdb.converter;

import com.google.gerrit.proto.Entities;
import com.google.gerrit.reviewdb.client.Account;
import com.google.protobuf.Parser;

public enum AccountIdProtoConverter implements ProtoConverter<Entities.Account_Id, Account.Id> {
  INSTANCE;

  @Override
  public Entities.Account_Id toProto(Account.Id accountId) {
    return Entities.Account_Id.newBuilder().setId(accountId.get()).build();
  }

  @Override
  public Account.Id fromProto(Entities.Account_Id proto) {
    return Account.id(proto.getId());
  }

  @Override
  public Parser<Entities.Account_Id> getParser() {
    return Entities.Account_Id.parser();
  }
}
