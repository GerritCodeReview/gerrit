// Copyright (C) 2014 The Android Open Source Project
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

package com.google.gerrit.server.api.changes;

import com.google.common.base.Function;
import com.google.gerrit.extensions.common.CommentInfo;

public class CommentInfoMapper implements
  Function<com.google.gerrit.server.change.CommentInfo, CommentInfo> {
  public static final CommentInfoMapper INSTANCE = new CommentInfoMapper();

  @Override
  public CommentInfo apply(com.google.gerrit.server.change.CommentInfo i) {
    CommentInfo o = new CommentInfo();
    o.id = i.id;
    o.path = i.path;
    o.side = i.side;
    o.line = i.line;
    o.inReplyTo = i.inReplyTo;
    o.message = i.message;
    o.updated = i.updated;
    // TODO(davido): AccountInfoMapper was removed on master
    //o.author = AccountInfoMapper.fromAcountInfo(i.author);
    o.range = i.range;
    return o;
  }
}
