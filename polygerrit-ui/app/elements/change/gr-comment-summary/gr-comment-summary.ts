/**
 * @license
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
import {html} from 'lit-html';
import {GrLitElement} from '../../lit/gr-lit-element';
import {customElement, property} from 'lit-element';
import {sharedStyles} from '../../../styles/shared-styles';
import {cssTemplate} from './gr-comment-summary.css';
import {ChangeInfo} from '../../../types/common';
import {ParsedChangeInfo} from '../../shared/gr-rest-api-interface/gr-reviewer-updates-parser';
import {CommentThread, isUnresolved} from '../../../utils/comment-util';

declare global {
  interface HTMLElementTagNameMap {
    'gr-comment-summary': GrCommentSummary;
  }
}

@customElement('gr-comment-summary')
export class GrCommentSummary extends GrLitElement {
  static get styles() {
    return [sharedStyles, cssTemplate];
  }

  @property({type: Array})
  commentThreads?: CommentThread[];

  @property({type: Object})
  change?: ChangeInfo | ParsedChangeInfo;

  render() {
    return html`<div class="comment-summary">
      <span class="title">Comments</span>
      <span
        class="status ${this.showWarning(this.change, this.commentThreads)
          ? 'warning'
          : ''}"
      >
        ${this.statusMessage(this.change, this.commentThreads)}
      </span>
    </div>`;
  }

  showWarning(
    change?: ChangeInfo | ParsedChangeInfo,
    commentThreads?: CommentThread[]
  ) {
    const unresolvedCommentsAfterReply = commentThreads?.filter(isUnresolved)
      .length;
    return change?.unresolved_comment_count && unresolvedCommentsAfterReply;
  }

  statusMessage(
    change?: ChangeInfo | ParsedChangeInfo,
    commentThreads?: CommentThread[]
  ) {
    if (change?.unresolved_comment_count) {
      const unresolvedComments = change?.unresolved_comment_count;
      const unresolvedCommentsAfterReply = commentThreads?.filter(isUnresolved)
        .length;
      let msgAfterReply = '';
      if (
        unresolvedCommentsAfterReply !== undefined &&
        unresolvedComments > unresolvedCommentsAfterReply
      ) {
        msgAfterReply = ` (${unresolvedCommentsAfterReply} unresolved after draft is sent)`;
      }

      return `Unresolved - ${unresolvedComments} comments${msgAfterReply}`;
    }
    if (change?.total_comment_count) {
      return 'Resolved comments';
    }
    return 'No comments';
  }
}
