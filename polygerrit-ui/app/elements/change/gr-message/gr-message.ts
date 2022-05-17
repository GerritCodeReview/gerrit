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
import '../../../styles/shared-styles';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-message_html';
import {MessageTag, SpecialFilePath} from '../../../constants/constants';
import {customElement, property, computed, observe} from '@polymer/decorators';
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
import {isServiceUser, replaceTemplates} from '../../../utils/account-util';

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
export class GrMessage extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

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
  message: ChangeMessage | undefined;

  @property({type: Array})
  commentThreads: CommentThread[] = [];

  @computed('message')
  get author() {
    return this.message?.author || this.message?.updated_by;
  }

  @property({type: Object})
  config?: ServerInfo;

  @property({type: Boolean})
  hideAutomated = false;

  @property({
    type: Boolean,
    reflectToAttribute: true,
    computed: '_computeIsHidden(hideAutomated, isAutomated)',
  })
  override hidden = false;

  @computed('message')
  get isAutomated() {
    return !!this.message && this._computeIsAutomated(this.message);
  }

  @computed('message')
  get showOnBehalfOf() {
    return !!this.message && this._computeShowOnBehalfOf(this.message);
  }

  @property({
    type: Boolean,
    computed: '_computeShowReplyButton(message, _loggedIn)',
  })
  showReplyButton = false;

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

  @property({type: Boolean, computed: '_computeExpanded(message.expanded)'})
  _expanded = false;

  @property({
    type: String,
    computed:
      '_computeMessageContentExpanded(_expanded, message.message,' +
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

  constructor() {
    super();
    this.addEventListener('click', e => this._handleClick(e));
  }

  override connectedCallback() {
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
    expanded: boolean,
    content?: string,
    accountsInMessage?: AccountInfo[],
    tag?: ReviewInputTag
  ) {
    if (!expanded) return '';
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
    // Content is under text-overflow, so it's always shorten
    const shortenedContent = content?.substring(0, 1000);
    const summary = this._computeMessageContent(
      false,
      shortenedContent,
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
      if (!isNewPatchSet) {
        const match = line.match(PATCH_SET_PREFIX_PATTERN)
        if (match && match[1] &&
          match[1].split(' ')
          .map(s => s.match(LABEL_TITLE_SCORE_PATTERN))
          .filter(ms => ms && ms.length === 4 && (ms[1] || ms[3])).length > 0) {
          return false;
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

  _computeAuthor(message: ChangeMessage) {
    return message.author || message.updated_by;
  }

  _computeShowOnBehalfOf(message: ChangeMessage) {
    const author = this._computeAuthor(message);
    return !!(
      author &&
      message.real_author &&
      author._account_id !== message.real_author._account_id
    );
  }

  _computeShowReplyButton(message?: ChangeMessage, loggedIn?: boolean) {
    return (
      message &&
      !!message.message &&
      loggedIn &&
      !this._computeIsAutomated(message)
    );
  }

  _computeExpanded(expanded: boolean) {
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

  _computeIsAutomated(message: ChangeMessage) {
    return !!(
      message.reviewer ||
      this._computeIsReviewerUpdate(message) ||
      (message.tag && message.tag.startsWith('autogenerated'))
    );
  }

  _computeIsHidden(hideAutomated: boolean, isAutomated: boolean) {
    return hideAutomated && isAutomated;
  }

  _computeIsReviewerUpdate(message: ChangeMessage) {
    return message.type === 'REVIEWER_UPDATE';
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

  _computeClass(expanded?: boolean, author?: AccountInfo) {
    const classes = [];
    classes.push(expanded ? 'expanded' : 'collapsed');
    if (isServiceUser(author)) classes.push('serviceUser');
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

  _computeExpandToggleIcon(expanded: boolean) {
    return expanded ? 'gr-icons:expand-less' : 'gr-icons:expand-more';
  }

  _toggleExpanded(e: Event) {
    e.stopPropagation();
    this.set('message.expanded', !this.message?.expanded);
  }
}
