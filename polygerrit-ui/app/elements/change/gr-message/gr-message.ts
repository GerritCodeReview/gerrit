/**
 * @license
 * Copyright 2015 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../../shared/gr-account-label/gr-account-label';
import '../../shared/gr-account-chip/gr-account-chip';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-icon/gr-icon';
import '../../shared/gr-date-formatter/gr-date-formatter';
import '../../shared/gr-formatted-text/gr-formatted-text';
import '../gr-message-scores/gr-message-scores';
import {css, html, LitElement, nothing} from 'lit';
import {MessageTag, SpecialFilePath} from '../../../constants/constants';
import {customElement, property, state} from 'lit/decorators.js';
import {hasOwnProperty} from '../../../utils/common-util';
import {
  ChangeInfo,
  ServerInfo,
  ReviewInputTag,
  NumericChangeId,
  ChangeMessageId,
  RevisionPatchSetNum,
  AccountInfo,
  BasePatchSetNum,
  LabelNameToInfoMap,
} from '../../../types/common';
import {
  ChangeMessage,
  CommentThread,
  isFormattedReviewerUpdate,
  LabelExtreme,
  PATCH_SET_PREFIX_PATTERN,
  isUnresolved,
} from '../../../utils/comment-util';
import {LABEL_TITLE_SCORE_PATTERN} from '../gr-message-scores/gr-message-scores';
import {getAppContext} from '../../../services/app-context';
import {pluralize} from '../../../utils/string-util';
import {navigationToken} from '../../core/gr-navigation/gr-navigation';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
  computePredecessor,
} from '../../../utils/patch-set-util';
import {isServiceUser, replaceTemplates} from '../../../utils/account-util';
import {assertIsDefined} from '../../../utils/common-util';
import {when} from 'lit/directives/when.js';
import {FormattedReviewerUpdateInfo} from '../../../types/types';
import {resolve} from '../../../models/dependency';
import {createChangeUrl} from '../../../models/views/change';

const UPLOADED_NEW_PATCHSET_PATTERN = /Uploaded patch set (\d+)./;
const MERGED_PATCHSET_PATTERN = /(\d+) is the latest approved patch-set/;
declare global {
  interface HTMLElementTagNameMap {
    'gr-message': GrMessage;
  }
}

export interface MessageAnchorTapDetail {
  id: ChangeMessageId;
}

@customElement('gr-message')
export class GrMessage extends LitElement {
  /**
   * Fired when this message's reply link is tapped.
   *
   * @event reply
   */

  /**
   * Fired when the message's timestamp is tapped.
   *
   * @event message-anchor-tap
   */

  /**
   * Fired when a change message is deleted.
   *
   * @event change-message-deleted
   */

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: Number})
  changeNum?: NumericChangeId;

  @property({type: Object})
  message?: ChangeMessage | (ChangeMessage & FormattedReviewerUpdateInfo);

  @property({type: Array})
  commentThreads: CommentThread[] = [];

  getAuthor() {
    return this.message?.author || this.message?.updated_by;
  }

  @property({type: Object})
  config?: ServerInfo;

  @property({type: Boolean})
  hideAutomated = false;

  /**
   * A mapping from label names to objects representing the minimum and
   * maximum possible values for that label.
   */
  @property({type: Object})
  labelExtremes?: LabelExtreme;

  @property({type: Boolean})
  loggedIn = false;

  @state()
  private isAdmin = false;

  @state()
  private isDeletingChangeMsg = false;

  private readonly restApiService = getAppContext().restApiService;

  private readonly getNavigation = resolve(this, navigationToken);

  // for COMMENTS_AUTOCLOSE logging purposes only
  readonly uid = performance.now().toString(36) + Math.random().toString(36);

  constructor() {
    super();
    this.addEventListener('click', e => this.handleClick(e));
  }

  override connectedCallback() {
    super.connectedCallback();
    this.restApiService.getConfig().then(config => {
      this.config = config;
    });
    this.restApiService.getLoggedIn().then(loggedIn => {
      this.loggedIn = loggedIn;
    });
    this.restApiService.getIsAdmin().then(isAdmin => {
      this.isAdmin = !!isAdmin;
    });
  }

  static override get styles() {
    return [
      css`
        :host {
          display: block;
          position: relative;
          cursor: pointer;
          overflow-y: hidden;
        }
        :host(.expanded) {
          cursor: auto;
        }
        .collapsed .contentContainer {
          align-items: center;
          color: var(--deemphasized-text-color);
          display: flex;
          white-space: nowrap;
        }
        .contentContainer {
          padding: var(--spacing-m) var(--spacing-l);
        }
        .expanded .contentContainer {
          background-color: var(--background-color-secondary);
        }
        .collapsed .contentContainer {
          background-color: var(--background-color-primary);
        }
        div.serviceUser.expanded div.contentContainer {
          background-color: var(
            --background-color-service-user,
            var(--background-color-secondary)
          );
        }
        div.serviceUser.collapsed div.contentContainer {
          background-color: var(
            --background-color-service-user,
            var(--background-color-primary)
          );
        }
        .name {
          font-weight: var(--font-weight-bold);
        }
        .message {
          --gr-formatted-text-prose-max-width: 120ch;
        }
        .collapsed .message {
          max-width: none;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .collapsed .author,
        .collapsed .content,
        .collapsed .message,
        .collapsed .updateCategory,
        gr-account-chip {
          display: inline;
        }
        gr-button {
          margin: 0 -4px;
        }
        .collapsed gr-thread-list,
        .collapsed .replyBtn,
        .collapsed .deleteBtn,
        .collapsed .hideOnCollapsed,
        .hideOnOpen {
          display: none;
        }
        .replyBtn {
          margin-right: var(--spacing-m);
        }
        .collapsed .hideOnOpen {
          display: block;
        }
        .collapsed .content {
          flex: 1;
          margin-right: var(--spacing-m);
          min-width: 0;
          overflow: hidden;
        }
        .collapsed .content.messageContent {
          text-overflow: ellipsis;
        }
        .collapsed .dateContainer {
          position: static;
        }
        .collapsed .author {
          overflow: hidden;
          color: var(--primary-text-color);
          margin-right: var(--spacing-s);
        }
        .authorLabel {
          min-width: 130px;
          --account-max-length: 120px;
          margin-right: var(--spacing-s);
        }
        .expanded .author {
          cursor: pointer;
          margin-bottom: var(--spacing-m);
        }
        .expanded .content {
          padding-left: 40px;
        }
        .dateContainer {
          position: absolute;
          /* right and top values should match .contentContainer padding */
          right: var(--spacing-l);
          top: var(--spacing-m);
        }
        .dateContainer gr-icon {
          margin-right: var(--spacing-m);
          color: var(--deemphasized-text-color);
        }
        .dateContainer .patchset:before {
          content: 'Patchset ';
        }
        .dateContainer .patchsetDiffButton {
          margin-right: var(--spacing-m);
          --gr-button-padding: 0 var(--spacing-m);
        }
        span.date {
          color: var(--deemphasized-text-color);
        }
        span.date:hover {
          text-decoration: underline;
        }
        .dateContainer gr-icon {
          cursor: pointer;
          vertical-align: top;
        }
        .commentsSummary {
          margin-right: var(--spacing-s);
        }
        .expanded .commentsSummary {
          display: none;
        }
        gr-icon.commentsIcon {
          vertical-align: top;
        }
        gr-icon.unresolved.commentsIcon {
          color: var(--warning-foreground);
        }
        .numberOfComments {
          padding-right: var(--spacing-m);
        }
        gr-account-label::part(gr-account-label-text) {
          font-weight: var(--font-weight-bold);
        }
        @media screen and (max-width: 50em) {
          .expanded .content {
            padding-left: 0;
          }
          .commentsSummary {
            min-width: 0px;
          }
          .authorLabel {
            width: 100px;
          }
          .dateContainer .patchset:before {
            content: 'PS ';
          }
        }
      `,
    ];
  }

  override render() {
    if (!this.message) return nothing;
    if (this.hideAutomated && this.computeIsAutomated()) return nothing;
    this.updateExpandedClass();
    return html` <div class=${this.computeClass()}>
      <div class="contentContainer">
        ${this.renderAuthor()} ${this.renderCommentsSummary()}
        ${this.renderMessageContent()} ${this.renderReviewerUpdate()}
        ${this.renderDateContainer()}
      </div>
    </div>`;
  }

  private renderAuthor() {
    assertIsDefined(this.message, 'message');
    return html` <div class="author" @click=${this.handleAuthorClick}>
      ${when(
        this.computeShowOnBehalfOf(),
        () => html`
          <span>
            <span class="name">${this.message?.real_author?.name}</span>
            on behalf of
          </span>
        `
      )}
      <gr-account-label
        .account=${this.getAuthor()}
        .change=${this.change}
        class="authorLabel"
      ></gr-account-label>
      <gr-message-scores
        .labelExtremes=${this.labelExtremes}
        .message=${this.message}
        .change=${this.change}
      ></gr-message-scores>
    </div>`;
  }

  private renderCommentIcon({
    commentThreadsCount,
    unresolved,
  }: {
    commentThreadsCount: number;
    unresolved: boolean;
  }) {
    if (commentThreadsCount === 0) {
      return nothing;
    }
    return html` <span
      class="numberOfComments"
      title=${pluralize(
        commentThreadsCount,
        (unresolved ? 'unresolved' : 'resolved') + ' comment'
      )}
    >
      <gr-icon
        small
        icon=${unresolved ? 'chat_bubble' : 'mark_chat_read'}
        ?filled=${unresolved}
        class="${unresolved ? 'unresolved ' : ''}commentsIcon"
      ></gr-icon>
      ${commentThreadsCount}</span
    >`;
  }

  private renderCommentsSummary() {
    if (!this.commentThreads?.length) return nothing;

    const unresolvedThreadsCount =
      this.commentThreads.filter(isUnresolved).length;
    const resolvedThreadsCount =
      this.commentThreads.length - unresolvedThreadsCount;

    return html`
      <div class="commentsSummary">
        ${this.renderCommentIcon({
          commentThreadsCount: unresolvedThreadsCount,
          unresolved: true,
        })}
        ${this.renderCommentIcon({
          commentThreadsCount: resolvedThreadsCount,
          unresolved: false,
        })}
      </div>
    `;
  }

  private renderMessageContent() {
    if (!this.message?.message) return nothing;
    const messageContentCollapsed =
      this.computeMessageContent(
        false,
        this.message.message.substring(0, 1000),
        this.message.accounts_in_message,
        this.message.tag,
        this.change?.labels
      ) || this.patchsetCommentSummary();
    return html` <div class="content messageContent">
      <div class="message hideOnOpen">${messageContentCollapsed}</div>
      ${this.renderExpandedMessageContent()}
    </div>`;
  }

  private renderExpandedMessageContent() {
    if (!this.message?.expanded) return nothing;
    const messageContentExpanded = this.computeMessageContent(
      true,
      this.message.message,
      this.message.accounts_in_message,
      this.message.tag,
      this.change?.labels
    );
    return html`
      <gr-formatted-text
        class="message hideOnCollapsed"
        .markdown=${true}
        .content=${messageContentExpanded}
      ></gr-formatted-text>
      ${when(messageContentExpanded, () => this.renderActionContainer())}
      <gr-thread-list
        ?hidden=${!this.commentThreads.length}
        .threads=${this.commentThreads}
        hide-dropdown
        show-comment-context
        .messageId=${this.message.id}
      >
      </gr-thread-list>
    `;
  }

  private renderActionContainer() {
    if (!this.computeShowReplyButton()) return nothing;
    return html` <div class="replyActionContainer">
      <gr-button class="replyBtn" link="" @click=${this.handleReplyTap}>
        Reply
      </gr-button>
      ${when(
        this.isAdmin,
        () => html`
          <gr-button
            ?disabled=${this.isDeletingChangeMsg}
            class="deleteBtn"
            link=""
            @click=${this.handleDeleteMessage}
          >
            Delete
          </gr-button>
        `
      )}
    </div>`;
  }

  private renderReviewerUpdate() {
    assertIsDefined(this.message, 'message');
    if (!isFormattedReviewerUpdate(this.message)) return;
    return html` <div class="content">
      ${this.message.updates.map(update => this.renderMessageUpdate(update))}
    </div>`;
  }

  private renderMessageUpdate(update: {
    message: string;
    reviewers: AccountInfo[];
  }) {
    return html`<div class="updateCategory">
      ${update.message}
      ${update.reviewers.map(
        reviewer => html`
          <gr-account-chip .account=${reviewer} .change=${this.change}>
          </gr-account-chip>
        `
      )}
    </div>`;
  }

  private renderDateContainer() {
    return html`<span class="dateContainer">
      ${this.renderDiffButton()}
      ${when(
        this.message?._revision_number,
        () => html`
          <span class="patchset">${this.message?._revision_number} |</span>
        `
      )}
      ${when(
        this.message?.id,
        () => html`
          <span class="date" @click=${this.handleAnchorClick}>
            <gr-date-formatter
              withTooltip
              showDateAndTime
              .dateStr=${this.message?.date}
            ></gr-date-formatter>
          </span>
        `,
        () => html`
          <span class="date">
            <gr-date-formatter
              withTooltip
              showDateAndTime
              .dateStr=${this.message?.date}
            ></gr-date-formatter>
          </span>
        `
      )}
      <gr-icon
        id="expandToggle"
        @click=${this.toggleExpanded}
        title="Toggle expanded state"
        icon=${this.computeExpandToggleIcon()}
      ></gr-icon>
    </span>`;
  }

  private renderDiffButton() {
    if (!this.showViewDiffButton()) return nothing;
    return html` <gr-button
      class="patchsetDiffButton"
      @click=${this.handleViewPatchsetDiff}
      link
    >
      View Diff
    </gr-button>`;
  }

  private updateExpandedClass() {
    if (this.message?.expanded) {
      this.classList.add('expanded');
    } else {
      this.classList.remove('expanded');
    }
  }

  // Private but used in tests.
  patchsetCommentSummary() {
    const id = this.message?.id;
    if (!id) return '';
    const patchsetThreads = (this.commentThreads ?? []).filter(
      thread => thread.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS
    );
    for (const thread of patchsetThreads) {
      // Find if there was a patchset level comment created through the reply
      // dialog and use it to determine the summary
      if (thread.comments[0].change_message_id === id) {
        return thread.comments[0].message;
      }
    }
    // Find if there is a reply to some patchset comment left
    for (const thread of patchsetThreads) {
      for (const comment of thread.comments) {
        if (comment.change_message_id === id) {
          return comment.message;
        }
      }
    }
    return '';
  }

  private showViewDiffButton() {
    return (
      this.isNewPatchsetTag(this.message?.tag) ||
      this.isMergePatchset(this.message)
    );
  }

  private isMergePatchset(message?: ChangeMessage) {
    return (
      message?.tag === MessageTag.TAG_MERGED &&
      message?.message.match(MERGED_PATCHSET_PATTERN)
    );
  }

  private isNewPatchsetTag(tag?: ReviewInputTag) {
    return (
      tag === MessageTag.TAG_NEW_PATCHSET ||
      tag === MessageTag.TAG_NEW_WIP_PATCHSET ||
      tag === MessageTag.TAG_NEW_PATCHSET_OUTDATED_VOTES
    );
  }

  // Private but used in tests
  handleViewPatchsetDiff(e: Event) {
    if (!this.message || !this.change) return;
    let patchNum: RevisionPatchSetNum;
    let basePatchNum: BasePatchSetNum;
    if (this.message.message.match(UPLOADED_NEW_PATCHSET_PATTERN)) {
      const match = this.message.message.match(UPLOADED_NEW_PATCHSET_PATTERN)!;
      if (isNaN(Number(match[1])))
        throw new Error('invalid patchnum in message');
      patchNum = Number(match[1]) as RevisionPatchSetNum;
      basePatchNum = computePredecessor(patchNum)!;
    } else if (this.message.message.match(MERGED_PATCHSET_PATTERN)) {
      const match = this.message.message.match(MERGED_PATCHSET_PATTERN)!;
      if (isNaN(Number(match[1])))
        throw new Error('invalid patchnum in message');
      basePatchNum = Number(match[1]) as BasePatchSetNum;
      patchNum = computeLatestPatchNum(computeAllPatchSets(this.change))!;
    } else {
      // Message is of the form "Commit Message was updated" or "Patchset X
      // was rebased"
      patchNum = computeLatestPatchNum(computeAllPatchSets(this.change))!;
      basePatchNum = computePredecessor(patchNum)!;
    }
    this.getNavigation().setUrl(
      createChangeUrl({change: this.change, patchNum, basePatchNum})
    );
    // stop propagation to stop message expansion
    e.stopPropagation();
  }

  // private but used in tests
  computeMessageContent(
    isExpanded: boolean,
    content?: string,
    accountsInMessage?: AccountInfo[],
    tag?: ReviewInputTag,
    labels?: LabelNameToInfoMap
  ) {
    if (!content) return '';
    const isNewPatchSet = this.isNewPatchsetTag(tag);

    if (accountsInMessage) {
      content = replaceTemplates(content, accountsInMessage, this.config);
    }

    const lines = content.split('\n');
    const filteredLines = lines.filter(line => {
      if (!isExpanded && line.startsWith('>')) {
        return false;
      }
      if (line.startsWith('(') && line.endsWith(' comment)')) {
        return false;
      }
      if (line.startsWith('(') && line.endsWith(' comments)')) {
        return false;
      }
      if (!isNewPatchSet && labels) {
        // Legacy change messages may contain the 'Patch Set' prefix
        // and a message(not containing label scores) on the same line.
        // To handle them correctly, only filter out lines which contain
        // the 'Patch Set' prefix and label scores.
        const match = line.match(PATCH_SET_PREFIX_PATTERN);
        if (match && match[1]) {
          const message = match[1].split(' ');
          if (
            message
              .map(s => s.match(LABEL_TITLE_SCORE_PATTERN))
              .filter(
                ms => ms && ms.length === 4 && hasOwnProperty(labels, ms[2])
              ).length === message.length
          ) {
            return false;
          }
        }
      }
      return true;
    });
    const mappedLines = filteredLines.map(line => {
      // The change message formatting is not very consistent, so
      // unfortunately we have to do a bit of tweaking here:
      //   Labels should be stripped from lines like this:
      //     Patch Set 29: Verified+1
      //   Rebase messages (which have a ':newPatchSet' tag) should be kept on
      //   lines like this:
      //     Patch Set 27: Patch Set 26 was rebased
      // Only make this replacement if the line starts with Patch Set, since if
      // it starts with "Uploaded patch set" (e.g for votes) we want to keep the
      // "Uploaded patch set".
      if (line.startsWith('Patch Set')) {
        line = line.replace(PATCH_SET_PREFIX_PATTERN, '$1');
      }
      return line;
    });
    return mappedLines.join('\n').trim();
  }

  // private but used in tests
  computeShowOnBehalfOf() {
    const author = this.getAuthor();
    const realAuthor = this.message?.real_author;
    return (
      !!author && !!realAuthor && author._account_id !== realAuthor._account_id
    );
  }

  // private but used in tests.
  computeShowReplyButton() {
    return (
      !!this.message &&
      !!this.message.message &&
      this.loggedIn &&
      !this.computeIsAutomated()
    );
  }

  private handleClick(e: Event) {
    if (!this.message || this.message?.expanded) {
      return;
    }
    e.stopPropagation();
    this.message.expanded = true;
    this.requestUpdate();
  }

  private handleAuthorClick(e: Event) {
    if (!this.message || !this.message?.expanded) {
      return;
    }
    e.stopPropagation();
    this.message.expanded = false;
    this.requestUpdate();
  }

  // private but used in tests.
  computeIsAutomated() {
    return !!(
      this.message?.reviewer ||
      this.computeIsReviewerUpdate() ||
      (this.message?.tag && this.message.tag.startsWith('autogenerated'))
    );
  }

  private computeIsReviewerUpdate() {
    return this.message?.type === 'REVIEWER_UPDATE';
  }

  private computeClass() {
    const expanded = this.message?.expanded;
    const classes = [];
    classes.push(expanded ? 'expanded' : 'collapsed');
    if (isServiceUser(this.getAuthor())) classes.push('serviceUser');
    return classes.join(' ');
  }

  private handleAnchorClick(e: Event) {
    e.preventDefault();
    // The element which triggers handleAnchorClick is rendered only if
    // message.id defined: the element is wrapped in dom-if if="[[message.id]]"
    const detail: MessageAnchorTapDetail = {
      id: this.message!.id,
    };
    this.dispatchEvent(
      new CustomEvent('message-anchor-tap', {
        bubbles: true,
        composed: true,
        detail,
      })
    );
  }

  private handleReplyTap(e: Event) {
    e.preventDefault();
    this.dispatchEvent(
      new CustomEvent('reply', {
        detail: {message: this.message},
        composed: true,
        bubbles: true,
      })
    );
  }

  private handleDeleteMessage(e: Event) {
    e.preventDefault();
    if (!this.message || !this.message.id || !this.changeNum) return;
    this.isDeletingChangeMsg = true;
    this.restApiService
      .deleteChangeCommitMessage(this.changeNum, this.message.id)
      .then(() => {
        this.isDeletingChangeMsg = false;
        this.dispatchEvent(
          new CustomEvent('change-message-deleted', {
            detail: {message: this.message},
            composed: true,
            bubbles: true,
          })
        );
      });
  }

  private computeExpandToggleIcon() {
    return this.message?.expanded ? 'expand_less' : 'expand_more';
  }

  private toggleExpanded(e: Event) {
    e.stopPropagation();
    if (!this.message) return;
    this.message = {...this.message, expanded: !this.message.expanded};
  }
}
