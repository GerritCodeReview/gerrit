/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-change-summary/gr-summary-chip';
import '../../shared/gr-avatar/gr-avatar-stack';
import '../../shared/gr-icon/gr-icon';
import {LitElement, css, html, nothing} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {
  getFirstComment,
  hasHumanReply,
  isResolved,
  isRobotThread,
  isUnresolved,
} from '../../../utils/comment-util';
import {pluralize} from '../../../utils/string-util';
import {AccountInfo, CommentThread} from '../../../types/common';
import {isDefined} from '../../../types/types';
import {CommentTabState} from '../../../types/events';
import {SummaryChipStyles} from '../gr-change-summary/gr-summary-chip';
import {subscribe} from '../../lit/subscription-controller';
import {resolve} from '../../../models/dependency';
import {userModelToken} from '../../../models/user/user-model';

@customElement('gr-comments-summary')
export class GrCommentsSummary extends LitElement {
  @property({type: Object})
  commentThreads?: CommentThread[];

  @property({type: Number})
  draftCount = 0;

  @property({type: Number})
  mentionCount = 0;

  @state()
  selfAccount?: AccountInfo;

  private readonly getUserModel = resolve(this, userModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getUserModel().account$,
      x => (this.selfAccount = x)
    );
  }

  static override get styles() {
    return [
      css`
        .zeroState {
          color: var(--deemphasized-text-color);
        }
        gr-avatar-stack {
          --avatar-size: var(--line-height-small, 16px);
          --stack-border-color: var(--warning-background);
        }
        .unresolvedIcon {
          font-size: var(--line-height-small);
          color: var(--warning-foreground);
        }
      `,
    ];
  }

  override render() {
    const commentThreads =
      this.commentThreads?.filter(t => !isRobotThread(t) || hasHumanReply(t)) ??
      [];
    const countResolvedComments = commentThreads.filter(isResolved).length;
    const unresolvedThreads = commentThreads.filter(isUnresolved);
    const countUnresolvedComments = unresolvedThreads.length;
    const unresolvedAuthors = this.getAccounts(unresolvedThreads);
    return html`
      ${this.renderZeroState(countResolvedComments, countUnresolvedComments)}
      ${this.renderDraftChip()} ${this.renderMentionChip()}
      ${this.renderUnresolvedCommentsChip(
        countUnresolvedComments,
        unresolvedAuthors
      )}
      ${this.renderResolvedCommentsChip(countResolvedComments)}
    `;
  }

  private renderZeroState(
    countResolvedComments: number,
    countUnresolvedComments: number
  ) {
    if (
      !!countResolvedComments ||
      !!this.draftCount ||
      !!countUnresolvedComments
    )
      return nothing;
    return html`<span class="zeroState"> No comments</span>`;
  }

  private renderMentionChip() {
    if (!this.mentionCount) return nothing;
    return html` <gr-summary-chip
      class="mentionSummary"
      styleType=${SummaryChipStyles.WARNING}
      category=${CommentTabState.MENTIONS}
      icon="alternate_email"
    >
      ${pluralize(this.mentionCount, 'mention')}</gr-summary-chip
    >`;
  }

  private renderDraftChip() {
    if (!this.draftCount) return nothing;
    return html` <gr-summary-chip
      styleType=${SummaryChipStyles.INFO}
      category=${CommentTabState.DRAFTS}
      icon="rate_review"
      iconFilled
    >
      ${pluralize(this.draftCount, 'draft')}</gr-summary-chip
    >`;
  }

  private renderUnresolvedCommentsChip(
    countUnresolvedComments: number,
    unresolvedAuthors: AccountInfo[]
  ) {
    if (!countUnresolvedComments) return nothing;
    return html` <gr-summary-chip
      styleType=${SummaryChipStyles.WARNING}
      category=${CommentTabState.UNRESOLVED}
      ?hidden=${!countUnresolvedComments}
    >
      <gr-avatar-stack .accounts=${unresolvedAuthors} imageSize="32">
        <gr-icon
          slot="fallback"
          icon="chat_bubble"
          filled
          class="unresolvedIcon"
        >
        </gr-icon>
      </gr-avatar-stack>
      ${countUnresolvedComments} unresolved</gr-summary-chip
    >`;
  }

  private renderResolvedCommentsChip(countResolvedComments: number) {
    if (!countResolvedComments) return nothing;
    return html` <gr-summary-chip
      styleType=${SummaryChipStyles.CHECK}
      category=${CommentTabState.SHOW_ALL}
      icon="mark_chat_read"
      >${countResolvedComments} resolved</gr-summary-chip
    >`;
  }

  getAccounts(commentThreads: CommentThread[]): AccountInfo[] {
    return commentThreads
      .map(getFirstComment)
      .map(comment => comment?.author ?? this.selfAccount)
      .filter(isDefined);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-comments-summary': GrCommentsSummary;
  }
}
