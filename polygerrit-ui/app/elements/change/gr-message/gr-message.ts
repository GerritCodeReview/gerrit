/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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

import '@polymer/iron-icon/iron-icon';
import '../../shared/gr-account-label/gr-account-label';
import '../../shared/gr-account-chip/gr-account-chip';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-date-formatter/gr-date-formatter';
import '../../shared/gr-formatted-text/gr-formatted-text';
import '../../../styles/gr-voting-styles';
import {
  ChangeMessageTemplate,
  MessageTag,
  SpecialFilePath,
} from '../../../constants/constants';
import {
  ChangeInfo,
  ChangeMessageInfo,
  ServerInfo,
  ConfigInfo,
  RepoName,
  ReviewInputTag,
  VotingRangeInfo,
  NumericChangeId,
  ChangeMessageId,
  PatchSetNum,
  AccountInfo,
  BasePatchSetNum,
  AccountId,
} from '../../../types/common';
import {CommentThread} from '../../../utils/comment-util';
import {hasOwnProperty} from '../../../utils/common-util';
import {appContext} from '../../../services/app-context';
import {pluralize} from '../../../utils/string-util';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {
  computeAllPatchSets,
  computeLatestPatchNum,
  computePredecessor,
} from '../../../utils/patch-set-util';
import {isServiceUser} from '../../../utils/account-util';
import {GrLitElement} from '../../lit/gr-lit-element';
import {css, customElement, html, property} from 'lit-element';
import {classMap} from 'lit-html/directives/class-map';
import {
  CombinedMessage,
  isChangeMessageInfo,
  isFormattedReviewerUpdateInfo,
} from '../gr-messages-list/gr-messages-list';

const PATCH_SET_PREFIX_PATTERN = /^(?:Uploaded\s*)?[Pp]atch [Ss]et \d+:\s*(.*)/;
const LABEL_TITLE_SCORE_PATTERN = /^(-?)([A-Za-z0-9-]+?)([+-]\d+)?[.]?$/;
const UPLOADED_NEW_PATCHSET_PATTERN = /Uploaded patch set (\d+)./;
const MERGED_PATCHSET_PATTERN = /(\d+) is the latest approved patch-set/;
const VOTE_RESET_TEXT = '0 (vote reset)';

declare global {
  interface HTMLElementTagNameMap {
    'gr-message': GrMessage;
  }
}

export interface MessageAnchorTapDetail {
  id: ChangeMessageId;
}

export interface ChangeMessage extends ChangeMessageInfo {
  // TODO(TS): maybe should be an enum instead
  type: string;
  expanded: boolean;
  commentThreads: CommentThread[];
}

export type LabelExtreme = {[labelName: string]: VotingRangeInfo};

interface Score {
  label?: string;
  value?: string;
}

@customElement('gr-message')
export class GrMessage extends GrLitElement {
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
  message: CombinedMessage | undefined;

  @property({type: Array})
  commentThreads: CommentThread[] = [];

  @property({type: Object})
  config?: ServerInfo;

  @property({type: Boolean})
  hideAutomated = false;

  @property({
    type: Boolean,
    reflect: true,
  })
  hidden = false;

  @property({type: String})
  projectName?: string;

  /**
   * A mapping from label names to objects representing the minimum and
   * maximum possible values for that label.
   */
  @property({type: Object})
  labelExtremes?: LabelExtreme;

  @property({type: Object})
  _projectConfig?: ConfigInfo;

  @property({type: Boolean})
  _loggedIn = false;

  @property({type: Boolean})
  _isAdmin = false;

  @property({type: Boolean})
  _isDeletingChangeMsg = false;

  @property({
    type: String,
    computed:
      '_computeMessageContentExpanded(message.message,' +
      ' message.accounts_in_message,' +
      ' message.tag)',
  })
  _messageContentExpanded = '';

  @property({
    type: String,
    computed:
      '_computeMessageContentCollapsed(message.message,' +
      ' message.accounts_in_message,' +
      ' message.tag,' +
      ' commentThreads)',
  })
  _messageContentCollapsed = '';

  @property({
    type: String,
    computed: '_computeCommentCountText(commentThreads)',
  })
  _commentCountText = '';

  private readonly restApiService = appContext.restApiService;

  static get styles() {
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
        .dateContainer gr-button {
          margin-right: var(--spacing-m);
          color: var(--deemphasized-text-color);
        }
        .dateContainer .patchset:before {
          content: 'Patchset ';
        }
        .dateContainer .patchsetDiffButton {
          margin-right: var(--spacing-m);
        }
        span.date {
          color: var(--deemphasized-text-color);
        }
        span.date:hover {
          text-decoration: underline;
        }
        .dateContainer iron-icon {
          cursor: pointer;
          vertical-align: top;
        }
        .score {
          box-sizing: border-box;
          border-radius: var(--border-radius);
          color: var(--vote-text-color);
          display: inline-block;
          padding: 0 var(--spacing-s);
          text-align: center;
        }
        .score,
        .commentsSummary {
          margin-right: var(--spacing-s);
          min-width: 115px;
        }
        .expanded .commentsSummary {
          display: none;
        }
        .commentsIcon {
          vertical-align: top;
        }
        .score.removed {
          background-color: var(--vote-color-neutral);
        }
        .score.negative {
          background-color: var(--vote-color-disliked);
          border: 1px solid var(--vote-outline-disliked);
          line-height: calc(var(--line-height-normal) - 2px);
          color: var(--chip-color);
        }
        .score.negative.min {
          background-color: var(--vote-color-rejected);
          border: none;
          padding-top: 1px;
          padding-bottom: 1px;
          color: var(--vote-text-color);
        }
        .score.positive {
          background-color: var(--vote-color-recommended);
          border: 1px solid var(--vote-outline-recommended);
          line-height: calc(var(--line-height-normal) - 2px);
          color: var(--chip-color);
        }
        .score.positive.max {
          background-color: var(--vote-color-approved);
          border: none;
          padding-top: 1px;
          padding-bottom: 1px;
          color: var(--vote-text-color);
        }
        gr-account-label::part(gr-account-label-text) {
          font-weight: var(--font-weight-bold);
        }
        @media screen and (max-width: 50em) {
          .expanded .content {
            padding-left: 0;
          }
          .score,
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

  render() {
    const {message} = this;
    if (!message) return;
    const expanded = !!message?.expanded;
    const author = this._computeAuthor(message);
    this.hidden = this._computeIsHidden(
      this.hideAutomated,
      this._computeIsAutomated(message)
    );
    const customStyle = html`<style include="gr-voting-styles">
      .message {
        --gr-formatted-text-prose-max-width: 120ch;
      }
      .dateContainer .patchsetDiffButton {
        --padding: 0 var(--spacing-m);
      }
      .authorLabel {
        --account-max-length: 120px;
      }
      iron-icon {
        --iron-icon-height: 20px;
        --iron-icon-width: 20px;
      }
    </style>`;

    return html`${customStyle}
      <div
        class="${classMap({
          expanded,
          collapsed: !expanded,
          serviceUser: isServiceUser(author),
        })}"
      >
        <div class="contentContainer">
          <div class="author" @click=${this._handleAuthorClick}>
            <span ?hidden="${!this._computeShowOnBehalfOf(message)}">
              <span class="name">${message?.real_author?.name}</span>
              on behalf of
            </span>
            <gr-account-label
              .account="${author}"
              class="authorLabel"
            ></gr-account-label>
            ${this._getScores(message, this.labelExtremes).map(
              score =>
                html`
                  <span
                    class$="score [[_computeScoreClass(score, labelExtremes)]]"
                  >
                    ${score.label} ${score.value}
                  </span>
                `
            )}
          </div>
          ${this._commentCountText
            ? html`<div class="commentsSummary">
                <iron-icon
                  icon="gr-icons:comment"
                  class="commentsIcon"
                ></iron-icon>
                <span class="numberOfComments">${this._commentCountText}</span>
              </div>`
            : ''}
          ${isChangeMessageInfo(message)
            ? html` <div class="content messageContent">
                <div class="message hideOnOpen">
                  ${this._messageContentCollapsed}
                </div>
                <gr-formatted-text
                  noTrailingMargin
                  class="message hideOnCollapsed"
                  .content="${this._messageContentExpanded}"
                  .config="${this._projectConfig?.commentlinks}"
                ></gr-formatted-text>
                ${expanded
                  ? html`
                      ${this._messageContentExpanded
                        ? html`
                            <div
                              class="replyActionContainer"
                              ?hidden=${!this._computeShowReplyButton(
                                message,
                                this._loggedIn
                              )}
                            >
                              <gr-button
                                class="replyBtn"
                                link=""
                                small=""
                                @click=${this._handleReplyTap}
                              >
                                Reply
                              </gr-button>
                              <gr-button
                                ?disabled=${this._isDeletingChangeMsg}
                                class="deleteBtn"
                                ?hidden=${!this._isAdmin}
                                link=""
                                small=""
                                @click=${this._handleDeleteMessage}
                              >
                                Delete
                              </gr-button>
                            </div>
                          `
                        : ''}
                      <gr-thread-list
                        change="${this.change}"
                        ?hidden=${!this.message?.commentThreads.length}
                        threads="${message.commentThreads}"
                        change-num="${this.changeNum}"
                        logged-in="${this._loggedIn}"
                        hide-dropdown
                        show-comment-context
                      >
                      </gr-thread-list>
                    `
                  : ''}
              </div>`
            : ''}
          ${isFormattedReviewerUpdateInfo(message)
            ? html`
                <div class="content">
                  ${message.updates.map(
                    update =>
                      html`<div class="updateCategory">
                        ${update.message}
                        ${update.reviewers.map(
                          reviewer =>
                            html`<gr-account-chip
                              .account="${reviewer}"
                              .change="${this.change}"
                            >
                            </gr-account-chip> `
                        )}
                      </div>`
                  )}
                </div>
              `
            : ''}
          <span class="dateContainer">
            ${this._showViewDiffButton(message)
              ? html`<gr-button
                  class="patchsetDiffButton"
                  @click=${this._handleViewPatchsetDiff}
                  link
                >
                  View Diff
                </gr-button>`
              : ''}
            ${message?._revision_number
              ? html`<span class="patchset"
                  >${message._revision_number} |</span
                >`
              : ''}
            ${!message?.id
              ? html`<span class="date">
                  <gr-date-formatter
                    has-tooltip=""
                    show-date-and-time=""
                    date-str="${message?.date}"
                  ></gr-date-formatter>
                </span>`
              : html`<span class="date" @click=${this._handleAnchorClick}>
                  <gr-date-formatter
                    has-tooltip=""
                    show-date-and-time=""
                    date-str="${message.date}"
                  ></gr-date-formatter>
                </span>`}
            <iron-icon
              id="expandToggle"
              @click=${this._toggleExpanded}
              title="Toggle expanded state"
              icon="${expanded
                ? 'gr-icons:expand-less'
                : 'gr-icons:expand-more'}"
            ></iron-icon>
          </span>
        </div>
      </div>`;
  }

  constructor() {
    super();
    this.addEventListener('click', e => this._handleClick(e));
  }

  connectedCallback() {
    super.connectedCallback();
    this.restApiService.getConfig().then(config => {
      this.config = config;
    });
    this.restApiService.getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
    });
    this.restApiService.getIsAdmin().then(isAdmin => {
      this._isAdmin = !!isAdmin;
    });
  }

  @observe('message.expanded')
  _updateExpandedClass(expanded: boolean) {
    if (expanded) {
      this.classList.add('expanded');
    } else {
      this.classList.remove('expanded');
    }
  }

  _computeCommentCountText(commentThreads?: CommentThread[]) {
    if (!commentThreads?.length) {
      return undefined;
    }

    return pluralize(commentThreads.length, 'comment');
  }

  _computeMessageContentExpanded(
    content?: string,
    accountsInMessage?: AccountInfo[],
    tag?: ReviewInputTag
  ) {
    return this._computeMessageContent(true, content, accountsInMessage, tag);
  }

  _patchsetCommentSummary(commentThreads: CommentThread[] = []) {
    const id = this.message?.id;
    if (!id) return '';
    const patchsetThreads = commentThreads.filter(
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

  _computeMessageContentCollapsed(
    content?: string,
    accountsInMessage?: AccountInfo[],
    tag?: ReviewInputTag,
    commentThreads?: CommentThread[]
  ) {
    const summary = this._computeMessageContent(
      false,
      content,
      accountsInMessage,
      tag
    );
    if (summary || !commentThreads) return summary;
    return this._patchsetCommentSummary(commentThreads);
  }

  _showViewDiffButton(message?: ChangeMessage) {
    return (
      this._isNewPatchsetTag(message?.tag) || this._isMergePatchset(message)
    );
  }

  _isMergePatchset(message?: ChangeMessage) {
    return (
      message?.tag === MessageTag.TAG_MERGED &&
      message?.message.match(MERGED_PATCHSET_PATTERN)
    );
  }

  _isNewPatchsetTag(tag?: ReviewInputTag) {
    return (
      tag === MessageTag.TAG_NEW_PATCHSET ||
      tag === MessageTag.TAG_NEW_WIP_PATCHSET
    );
  }

  _handleViewPatchsetDiff(e: Event) {
    if (!this.message || !this.change) return;
    let patchNum: PatchSetNum;
    let basePatchNum: PatchSetNum;
    if (this.message.message.match(UPLOADED_NEW_PATCHSET_PATTERN)) {
      const match = this.message.message.match(UPLOADED_NEW_PATCHSET_PATTERN)!;
      if (isNaN(Number(match[1])))
        throw new Error('invalid patchnum in message');
      patchNum = Number(match[1]) as PatchSetNum;
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
    GerritNav.navigateToChange(this.change, patchNum, basePatchNum);
    // stop propagation to stop message expansion
    e.stopPropagation();
  }

  _computeMessageContent(
    isExpanded: boolean,
    content?: string,
    accountsInMessage?: AccountInfo[],
    tag?: ReviewInputTag
  ) {
    if (!content) return '';
    const isNewPatchSet = this._isNewPatchsetTag(tag);

    if (accountsInMessage) {
      content = content.replace(
        new RegExp(ChangeMessageTemplate.ACCOUNT_TEMPLATE, 'g'),
        (_accountIdTemplate, accountId) =>
          accountsInMessage.find(
            account => account._account_id === (Number(accountId) as AccountId)
          )?.name || `Gerrit Account ${accountId}`
      );
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
      if (!isNewPatchSet && line.match(PATCH_SET_PREFIX_PATTERN)) {
        return false;
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
      if (isNewPatchSet) {
        line = line.replace(PATCH_SET_PREFIX_PATTERN, '$1');
      }
      return line;
    });
    return mappedLines.join('\n').trim();
  }

  _computeAuthor(message?: CombinedMessage) {
    return message?.author || (message as ChangeMessageInfo)?.updated_by;
  }

  _computeShowOnBehalfOf(message?: CombinedMessage) {
    if (!message) return false;
    const author = this._computeAuthor(message);
    return !!(
      author &&
      message.real_author &&
      author._account_id !== message.real_author._account_id
    );
  }

  _computeShowReplyButton(message?: CombinedMessage, loggedIn?: boolean) {
    return (
      message &&
      isChangeMessageInfo(message) &&
      !!message.message &&
      loggedIn &&
      !this._computeIsAutomated(message)
    );
  }

  _computeExpanded(expanded?: boolean) {
    return expanded;
  }

  _handleClick(e: Event) {
    if (this.message?.expanded) {
      return;
    }
    e.stopPropagation();
    this.set('message.expanded', true);
  }

  _handleAuthorClick(e: Event) {
    if (!this.message?.expanded) {
      return;
    }
    e.stopPropagation();
    this.set('message.expanded', false);
  }

  _computeIsAutomated(message?: CombinedMessage) {
    if (!message) return false;
    return !!(
      (isChangeMessageInfo(message) && message.reviewer) ||
      isFormattedReviewerUpdateInfo(message) ||
      (message.tag && message.tag.startsWith('autogenerated'))
    );
  }

  _computeIsHidden(hideAutomated: boolean, isAutomated: boolean) {
    return hideAutomated && isAutomated;
  }

  _getScores(message?: ChangeMessage, labelExtremes?: LabelExtreme): Score[] {
    if (!message || !message.message || !labelExtremes) {
      return [];
    }
    const line = message.message.split('\n', 1)[0];
    const patchSetPrefix = PATCH_SET_PREFIX_PATTERN;
    if (!line.match(patchSetPrefix)) {
      return [];
    }
    const scoresRaw = line.split(patchSetPrefix)[1];
    if (!scoresRaw) {
      return [];
    }
    return scoresRaw
      .split(' ')
      .map(s => s.match(LABEL_TITLE_SCORE_PATTERN))
      .filter(
        ms => ms && ms.length === 4 && hasOwnProperty(labelExtremes, ms[2])
      )
      .map(ms => {
        const label = ms?.[2];
        const value = ms?.[1] === '-' ? VOTE_RESET_TEXT : ms?.[3];
        return {label, value};
      });
  }

  _computeScoreClass(score?: Score, labelExtremes?: LabelExtreme) {
    // Polymer 2: check for undefined
    if (score === undefined || labelExtremes === undefined) {
      return '';
    }
    if (!score.value) {
      return '';
    }
    if (score.value.includes(VOTE_RESET_TEXT)) {
      return 'removed';
    }
    const classes = [];
    if (Number(score.value) > 0) {
      classes.push('positive');
    } else if (Number(score.value) < 0) {
      classes.push('negative');
    }
    if (score.label) {
      const extremes = labelExtremes[score.label];
      if (extremes) {
        const intScore = Number(score.value);
        if (intScore === extremes.max) {
          classes.push('max');
        } else if (intScore === extremes.min) {
          classes.push('min');
        }
      }
    }
    return classes.join(' ');
  }

  _handleAnchorClick(e: Event) {
    e.preventDefault();
    // The element which triggers _handleAnchorClick is rendered only if
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

  _handleReplyTap(e: Event) {
    e.preventDefault();
    this.dispatchEvent(
      new CustomEvent('reply', {
        detail: {message: this.message},
        composed: true,
        bubbles: true,
      })
    );
  }

  _handleDeleteMessage(e: Event) {
    e.preventDefault();
    if (!this.message || !this.message.id || !this.changeNum) return;
    this._isDeletingChangeMsg = true;
    this.restApiService
      .deleteChangeCommitMessage(this.changeNum, this.message.id)
      .then(() => {
        this._isDeletingChangeMsg = false;
        this.dispatchEvent(
          new CustomEvent('change-message-deleted', {
            detail: {message: this.message},
            composed: true,
            bubbles: true,
          })
        );
      });
  }

  @observe('projectName')
  _projectNameChanged(name: string) {
    this.restApiService.getProjectConfig(name as RepoName).then(config => {
      this._projectConfig = config;
    });
  }

  _toggleExpanded(e: Event) {
    e.stopPropagation();
    this.set('message.expanded', !this.message?.expanded);
  }
}
