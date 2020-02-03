/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.gerrit.server.restapi.change;

import static com.google.common.truth.Truth.assertThat;

import com.google.common.collect.Lists;
import com.google.gerrit.entities.Change;
import com.google.gerrit.entities.ChangeMessage;
import com.google.gerrit.extensions.common.CommentInfo;
import java.sql.Timestamp;
import java.util.List;
import org.junit.Test;

public class ListChangeCommentsTest {

  @Test
  public void commentsLinkedToChangeMessages() {
    CommentInfo c1 = getNewCommentInfo("c1", Timestamp.valueOf("2018-01-01 09:01:00"));
    CommentInfo c2 = getNewCommentInfo("c2", Timestamp.valueOf("2018-01-01 09:01:15"));
    CommentInfo c3 = getNewCommentInfo("c3", Timestamp.valueOf("2018-01-01 09:01:25"));

    ChangeMessage cm1 =
        getNewChangeMessage("cm1key", "cm1", Timestamp.valueOf("2018-01-01 00:00:00"));
    ChangeMessage cm2 =
        getNewChangeMessage("cm2key", "cm2", Timestamp.valueOf("2018-01-01 09:01:15"));
    ChangeMessage cm3 =
        getNewChangeMessage("cm3key", "cm3", Timestamp.valueOf("2018-01-01 09:01:27"));
    ChangeMessage cm4 =
        getNewChangeMessage("cm4key", "cm4", Timestamp.valueOf("2018-01-01 09:01:32"));

    assertThat(c1.changeMessageId).isNull();
    assertThat(c2.changeMessageId).isNull();
    assertThat(c3.changeMessageId).isNull();

    List<CommentInfo> comments = Lists.newArrayList(c1, c2, c3);
    List<ChangeMessage> changeMessages = Lists.newArrayList(cm1, cm2, cm3);

    new ListChangeComments(null, null, null, null)
        .linkCommentsToChangeMessages(comments, changeMessages);

    assertThat(c1.changeMessageId).isEqualTo(changeMessageKey(cm2));
    assertThat(c2.changeMessageId).isEqualTo(changeMessageKey(cm2));
    assertThat(c3.changeMessageId).isEqualTo(changeMessageKey(cm3));
  }

  private CommentInfo getNewCommentInfo(String message, Timestamp ts) {
    CommentInfo c = new CommentInfo();
    c.message = message;
    c.updated = ts;
    return c;
  }

  private ChangeMessage getNewChangeMessage(String id, String message, Timestamp ts) {
    ChangeMessage.Key key = ChangeMessage.key(Change.id(1), id);
    ChangeMessage cm = new ChangeMessage(key, null, ts, null);
    cm.setMessage(message);
    return cm;
  }

  private String changeMessageKey(ChangeMessage changeMessage) {
    return changeMessage.getKey().uuid();
  }
}
