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
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../../../styles/shared-styles';
import '../../../styles/gr-voting-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-message_html';
import {SpecialFilePath} from '../../../constants/constants';
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
} from '../../../types/common';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {CommentThread} from '../../../utils/comment-util';
import {hasOwnProperty} from '../../../utils/common-util';

const PATCH_SET_PREFIX_PATTERN = /^(?:Uploaded\s*)?(?:P|p)atch (?:S|s)et \d+:\s*(.*)/;
const LABEL_TITLE_SCORE_PATTERN = /^(-?)([A-Za-z0-9-]+?)([+-]\d+)?[.]?$/;

declare global {
  interface HTMLElementTagNameMap {
    'gr-message': GrMessage;
  }
}

export interface MessageAnchorTapDetail {
  id: ChangeMessageId;
}

export interface GrMessage {
  $: {
    restAPI: RestApiService & Element;
  };
}

interface ChangeMessage extends ChangeMessageInfo {
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
export class GrMessage extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
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
  hidden = false;

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
    computed: '_computeMessageContentExpanded(message.message, message.tag)',
  })
  _messageContentExpanded = '';

  @property({
    type: String,
    computed:
      '_computeMessageContentCollapsed(message.message, message.tag,' +
      ' message.commentThreads)',
  })
  _messageContentCollapsed = '';

  @property({
    type: String,
    computed: '_computeCommentCountText(message.commentThreads.length)',
  })
  _commentCountText = '';

  created() {
    super.created();
    this.addEventListener('click', e => this._handleClick(e));
  }

  attached() {
    super.attached();
    this.$.restAPI.getConfig().then(config => {
      this.config = config;
    });
    this.$.restAPI.getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
    });
    this.$.restAPI.getIsAdmin().then(isAdmin => {
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

  _computeCommentCountText(threadsLength?: number) {
    if (threadsLength === 0) {
      return undefined;
    } else if (threadsLength === 1) {
      return '1 comment';
    } else {
      return `${threadsLength} comments`;
    }
  }

  _onThreadListModified() {
    // TODO(taoalpha): this won't propagate the changes to the files
    // should consider replacing this with either top level events
    // or gerrit level events

    // emit the event so change-view can also get updated with latest changes
    this.dispatchEvent(
      new CustomEvent('comment-refresh', {
        composed: true,
        bubbles: true,
      })
    );
  }

  _computeMessageContentExpanded(content?: string, tag?: ReviewInputTag) {
    return this._computeMessageContent(content, tag, true);
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
    tag?: ReviewInputTag,
    commentThreads?: CommentThread[]
  ) {
    const summary = this._computeMessageContent(content, tag, false);
    if (summary || !commentThreads) return summary;
    return this._patchsetCommentSummary(commentThreads);
  }

  _computeMessageContent(
    content = '',
    tag: ReviewInputTag = '' as ReviewInputTag,
    isExpanded: boolean
  ) {
    const isNewPatchSet =
      tag.endsWith(':newPatchSet') || tag.endsWith(':newWipPatchSet');
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
        const value = ms?.[1] === '-' ? 'removed' : ms?.[3];
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
    if (score.value === 'removed') {
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

  _computeClass(expanded: boolean) {
    const classes = [];
    classes.push(expanded ? 'expanded' : 'collapsed');
    return classes.join(' ');
  }

  _handleAnchorClick(e: Event) {
    e.preventDefault();
    // The element which triggers _handleAnchorClick is rendered only if
    // message.id defined: the elemenet is wrapped in dom-if if="[[message.id]]"
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
    this.$.restAPI
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
    this.$.restAPI.getProjectConfig(name as RepoName).then(config => {
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
