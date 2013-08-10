// Copyright (C) 2012 The Android Open Source Project
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

package com.google.gerrit.extensions.api2.changes;

import com.google.gerrit.extensions.restapi.DefaultInput;

import java.util.List;
import java.util.Map;

/** Input passed to {@code POST /changes/{id}/revisions/{id}/review}. */
public class ReviewInput {
  @DefaultInput
  public String message;

  public Map<String, Short> labels;
  public Map<String, List<Comment>> comments;

  /**
   * If true require all labels to be within the user's permitted ranges based
   * on access controls, attempting to use a label not granted to the user
   * will fail the entire modify operation early. If false the operation will
   * execute anyway, but the proposed labels given by the user will be
   * modified to be the "best" value allowed by the access controls, or
   * ignored if the label does not exist.
   */
  public boolean strictLabels = true;

  /**
   * How to process draft comments already in the database that were not also
   * described in this input request.
   */
  public DraftHandling drafts = DraftHandling.DELETE;

  /** Who to send email notifications to after review is stored. */
  public NotifyHandling notify = NotifyHandling.ALL;

  /**
   * Account ID, name, email address or username of another user. The review
   * will be posted/updated on behalf of this named user instead of the
   * caller. Caller must have the labelAs-$NAME permission granted for each
   * label that appears in {@link #labels}. This is in addition to the named
   * user also needing to have permission to use the labels.
   * <p>
   * {@link #strictLabels} impacts how labels is processed for the named user,
   * not the caller.
   */
  public String onBehalfOf;

  public static enum DraftHandling {
    DELETE, PUBLISH, KEEP;
  }

  public static enum NotifyHandling {
    NONE, OWNER, OWNER_REVIEWERS, ALL;
  }

  public static enum Side {
    PARENT, REVISION;
  }

  public static class Comment {
    public String id;
    public Side side;
    public int line;
    public String inReplyTo;
    public String message;
    public Range range;

    public static class Range {
      public int startLine;
      public int startCharacter;
      public int endLine;
      public int endCharacter;
    }
  }
}
