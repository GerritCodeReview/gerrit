// Copyright (C) 2013 The Android Open Source Project
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

package com.google.gerrit.server.group;

import com.google.gerrit.extensions.restapi.RestView;
import com.google.gerrit.server.IdentifiedUser;
import com.google.inject.TypeLiteral;

public class MemberResource extends GroupResource {
  public static final TypeLiteral<RestView<MemberResource>> MEMBER_KIND =
      new TypeLiteral<RestView<MemberResource>>() {};

  private final IdentifiedUser user;

  public MemberResource(GroupResource group, IdentifiedUser user) {
    super(group);
    this.user = user;
  }

  public IdentifiedUser getMember() {
    return user;
  }
}
