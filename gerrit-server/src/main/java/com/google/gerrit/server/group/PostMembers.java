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

import com.google.common.collect.Lists;
import com.google.gerrit.extensions.restapi.AuthException;
import com.google.gerrit.extensions.restapi.BadRequestException;
import com.google.gerrit.extensions.restapi.MethodNotAllowedException;
import com.google.gerrit.extensions.restapi.RestModifyView;
import com.google.gwtorm.server.OrmException;
import com.google.inject.Inject;
import com.google.inject.Provider;

import java.util.List;

class PostMembers implements RestModifyView<GroupResource, PostMembers.Input> {
  static enum Method {
    PUT, DELETE;
  }

  static class Input {
    Method method = Method.PUT;
    List<String> members;

    static Input init(Input in) {
      if (in == null) {
        in = new Input();
      }
      if (in.members == null) {
        in.members = Lists.newArrayListWithCapacity(1);
      }
      return in;
    }
  }

  private final Provider<PutMembers> put;
  private final Provider<DeleteMembers> delete;

  @Inject
  PostMembers(Provider<PutMembers> put, Provider<DeleteMembers> delete) {
    this.put = put;
    this.delete = delete;
  }

  @Override
  public Class<Input> inputType() {
    return Input.class;
  }

  @Override
  public Object apply(GroupResource resource, Input input)
      throws AuthException, MethodNotAllowedException, BadRequestException,
      OrmException {
    input = Input.init(input);
    if (input.method == null) {
      throw new BadRequestException("invalid method");
    }
    switch (input.method) {
      case PUT:
        return put.get().apply(resource, cast(input));
      case DELETE:
        return delete.get().apply(resource, cast(input));
      default:
        throw new BadRequestException("invalid method");
    }
  }

  private static PutMembers.Input cast(Input input) {
    PutMembers.Input r = new PutMembers.Input();
    r.members = input.members;
    return r;
  }
}
