/**
 * @license
 * Copyright 2023 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-change-summary/gr-summary-chip';
import '../../shared/gr-avatar/gr-avatar-stack';
import '../../shared/gr-icon/gr-icon';
import {css, html, LitElement, nothing} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {
  getFirstComment,
  isResolved,
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
import {when} from 'lit/directives/when.js';
import {spinnerStyles} from '../../../styles/gr-spinner-styles';

@customElement('gr-comments-summary')
export class GrCommentsSummary extends LitElement {
  @property({type: Object})
  commentThreads?: CommentThread[];

  @property({type: Boolean})
  commentsLoading = false;

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
      spinnerStyles,
      css`
        /* The basics of .loadingSpin are defined in shared styles. */
        .loadingSpin {
          width: calc(var(--line-height-normal) - 2px);
          height: calc(var(--line-height-normal) - 2px);
          display: inline-block;
          vertical-align: top;
          position: relative;
          /* Making up for the 2px reduced height above. */
          top: 1px;
        }
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
    const commentThreads = this.commentThreads ?? [];
    const countResolvedComments = commentThreads.filter(isResolved).length;
    const unresolvedThreads = commentThreads.filter(isUnresolved);
    const countUnresolvedComments = unresolvedThreads.length;
    const unresolvedAuthors = this.getAccounts(unresolvedThreads);
    const resolveAuthors = this.showAvatarForResolved
      ? this.getAccounts(commentThreads.filter(isResolved))
      : undefined;
    return html`
      ${when(
        this.commentsLoading,
        () => html`<span class="loadingSpin"></span>`
      )}
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
    if (this.commentsLoading) {
      return html`<span class="zeroState"> Loading comments...</span>`;
    }

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
      title=${this.showCommentCategoryName
        ? nothing
        : pluralize(this.draftCount, 'draft')}
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
      title=${this.showCommentCategoryName
        ? nothing
        : `${countUnresolvedComments} unresolved`}
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
        title=${this.showCommentCategoryName
          ? nothing
          : `${countResolvedComments} resolved`}
        icon="mark_chat_read"
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
      title=${this.showCommentCategoryName
        ? nothing
        : `${countResolvedComments} resolved`}
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
