/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../gr-change-summary/gr-summary-chip';
import '../../shared/gr-avatar/gr-avatar-stack';
import '../../shared/gr-icon/gr-icon';
import {LitElement, css, html, nothing} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {sharedStyles} from '../../../styles/shared-styles';
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
import {spinnerStyles} from '../../../styles/gr-spinner-styles';
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
      sharedStyles,
      spinnerStyles,
      css`
        :host {
          display: block;
          color: var(--deemphasized-text-color);
          max-width: 625px;
          margin-bottom: var(--spacing-m);
        }
        .zeroState {
          color: var(--deemphasized-text-color);
        }
        .loading.zeroState {
          margin-right: var(--spacing-m);
        }
        div.info,
        div.error,
        .login {
          display: flex;
          color: var(--primary-text-color);
          padding: 0 var(--spacing-s);
          margin: var(--spacing-xs) 0;
          width: 490px;
        }
        div.info {
          background-color: var(--info-background);
        }
        div.error {
          background-color: var(--error-background);
        }
        div.info gr-icon,
        div.error gr-icon {
          font-size: 16px;
          position: relative;
          top: 4px;
          margin-right: var(--spacing-s);
        }
        div.info gr-icon {
          color: var(--info-foreground);
        }
        div.error gr-icon {
          color: var(--error-foreground);
        }
        div.info .right,
        div.error .right {
          overflow: hidden;
        }
        div.info .right .message,
        div.error .right .message {
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        .login {
          justify-content: space-between;
          background: var(--info-background);
        }
        .login gr-icon {
          color: var(--info-foreground);
        }
        .login gr-button {
          margin: -4px var(--spacing-s);
        }
        td.key {
          padding-right: var(--spacing-l);
          padding-bottom: var(--spacing-s);
          line-height: calc(var(--line-height-normal) + var(--spacing-s));
        }
        td.value {
          padding-right: var(--spacing-l);
          padding-bottom: var(--spacing-s);
          line-height: calc(var(--line-height-normal) + var(--spacing-s));
        }
        gr-avatar-stack {
          --avatar-size: var(--line-height-small, 16px);
          --stack-border-color: var(--warning-background);
        }
        .unresolvedIcon {
          font-size: var(--line-height-small);
          color: var(--warning-foreground);
        }
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
        .actions {
          margin-left: calc(0px - var(--spacing-m));
          line-height: var(--line-height-normal);
        }
        .actions gr-checks-action,
        .actions gr-dropdown {
          vertical-align: top;
          --gr-button-padding: 0 var(--spacing-m);
        }
        .actions #moreMessage {
          display: none;
        }
        .summaryMessage {
          line-height: var(--line-height-normal);
          color: var(--primary-text-color);
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
