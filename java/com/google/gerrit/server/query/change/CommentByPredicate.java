// Copyright (C) 2015 The Android Open Source Project
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

package com.google.gerrit.server.query.change;

import com.google.gerrit.entities.Account;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.entities.Comment;
import com.google.gerrit.server.index.change.ChangeField;
import java.util.Objects;

public class CommentByPredicate extends ChangeIndexPredicate {
  protected final Account.Id id;

  public CommentByPredicate(Account.Id id) {
    super(ChangeField.COMMENTBY, id.toString());
    this.id = id;
  }

  Account.Id getAccountId() {
    return id;
  }

  @Override
  public boolean match(ChangeData cd) {
    for (ChangeMessage m : cd.messages()) {
      if (Objects.equals(m.getAuthor(), id)) {
        return true;
      }
    }
    for (Comment c : cd.publishedComments()) {
      if (Objects.equals(c.author.getId(), id)) {
        return true;
      }
    }
    return false;
  }

  @Override
  public int getCost() {
    return 1;
  }
}
