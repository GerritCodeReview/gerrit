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

  @property({type: Boolean})
  showCommentCategoryName = false;

  @property({type: Boolean})
  clickableChips = false;

  @property({type: Boolean})
  emptyWhenNoComments = false;

  @property({type: Boolean})
  showAvatarForResolved = false;

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
    const resolveAuthors = this.showAvatarForResolved
      ? this.getAccounts(commentThreads.filter(isResolved))
      : undefined;
    return html`
      ${this.renderZeroState(countResolvedComments, countUnresolvedComments)}
      ${this.renderDraftChip()} ${this.renderMentionChip()}
      ${this.renderUnresolvedCommentsChip(
        countUnresolvedComments,
        unresolvedAuthors
      )}
      ${this.renderResolvedCommentsChip(countResolvedComments, resolveAuthors)}
    `;
  }

  private renderZeroState(
    countResolvedComments: number,
    countUnresolvedComments: number
  ) {
    if (
      this.emptyWhenNoComments ||
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
      .clickable=${this.clickableChips}
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
      .clickable=${this.clickableChips}
    >
      ${this.showCommentCategoryName
        ? pluralize(this.draftCount, 'draft')
        : this.draftCount}</gr-summary-chip
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
      .clickable=${this.clickableChips}
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
      ${this.showCommentCategoryName
        ? `${countUnresolvedComments} unresolved`
        : `${countUnresolvedComments}`}</gr-summary-chip
    >`;
  }

  private renderResolvedCommentsChip(
    countResolvedComments: number,
    resolvedAuthors?: AccountInfo[]
  ) {
    if (!countResolvedComments) return nothing;
    if (resolvedAuthors) {
      return html` <gr-summary-chip
        styleType=${SummaryChipStyles.CHECK}
        category=${CommentTabState.SHOW_ALL}
        .clickable=${this.clickableChips}
        ><gr-avatar-stack .accounts=${resolvedAuthors} imageSize="32">
          <gr-icon
            slot="fallback"
            icon="chat_bubble"
            filled
            class="unresolvedIcon"
          >
          </gr-icon> </gr-avatar-stack
        >${this.showCommentCategoryName
          ? `${countResolvedComments} resolved`
          : `${countResolvedComments}`}</gr-summary-chip
      >`;
    }
    return html` <gr-summary-chip
      styleType=${SummaryChipStyles.CHECK}
      category=${CommentTabState.SHOW_ALL}
      .clickable=${this.clickableChips}
      icon="mark_chat_read"
      >${this.showCommentCategoryName
        ? `${countResolvedComments} resolved`
        : `${countResolvedComments}`}</gr-summary-chip
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
