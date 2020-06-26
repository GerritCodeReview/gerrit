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

import '@polymer/iron-icon/iron-icon.js';
import '../../shared/gr-account-label/gr-account-label.js';
import '../../shared/gr-account-chip/gr-account-chip.js';
import '../../shared/gr-button/gr-button.js';
import '../../shared/gr-date-formatter/gr-date-formatter.js';
import '../../shared/gr-formatted-text/gr-formatted-text.js';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface.js';
import '../../../styles/shared-styles.js';
import '../../../styles/gr-voting-styles.js';
import '../gr-comment-list/gr-comment-list.js';
import {appContext} from '../../../services/app-context.js';
import {ExperimentIds} from '../../../services/flags.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-message_html.js';
import {SpecialFilePath} from '../../../constants/constants.js';

const PATCH_SET_PREFIX_PATTERN = /^Patch Set \d+:\s*(.*)/;
const LABEL_TITLE_SCORE_PATTERN = /^(-?)([A-Za-z0-9-]+?)([+-]\d+)?$/;

/**
 * @extends PolymerElement
 */
class GrMessage extends GestureEventListeners(
    LegacyElementMixin(PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-message'; }
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

  static get properties() {
    return {
      /** @type {?} */
      change: Object,
      changeNum: Number,
      /** @type {?} */
      message: Object,
      author: {
        type: Object,
        computed: '_computeAuthor(message)',
      },
      /**
       * TODO(taoalpha): remove once the change log experiment is launched
       *
       * @type {Object} - a map on file and comments on it
       */
      comments: {
        type: Object,
      },
      config: Object,
      hideAutomated: {
        type: Boolean,
        value: false,
      },
      hidden: {
        type: Boolean,
        computed: '_computeIsHidden(hideAutomated, isAutomated)',
        reflectToAttribute: true,
      },
      isAutomated: {
        type: Boolean,
        computed: '_computeIsAutomated(message)',
      },
      showOnBehalfOf: {
        type: Boolean,
        computed: '_computeShowOnBehalfOf(message)',
      },
      showReplyButton: {
        type: Boolean,
        computed: '_computeShowReplyButton(message, _loggedIn)',
      },
      projectName: {
        type: String,
        observer: '_projectNameChanged',
      },

      /**
       * A mapping from label names to objects representing the minimum and
       * maximum possible values for that label.
       */
      labelExtremes: Object,

      /**
       * @type {{ commentlinks: Array }}
       */
      _projectConfig: Object,
      // Computed property needed to trigger Polymer value observing.
      _expanded: {
        type: Object,
        computed: '_computeExpanded(message.expanded)',
      },
      _messageContentExpanded: {
        type: String,
        computed:
            '_computeMessageContentExpanded(message.message, message.tag)',
      },
      _messageContentCollapsed: {
        type: String,
        computed:
            '_computeMessageContentCollapsed(message.message, message.tag,' +
            ' message.commentThreads)',
      },
      _commentCountText: {
        type: Number,
        computed: '_computeCommentCountText(comments,'
            + ' message.commentThreads.length, _isCleanerLogExperimentEnabled)',
      },
      _loggedIn: {
        type: Boolean,
        value: false,
      },
      _isAdmin: {
        type: Boolean,
        value: false,
      },
      _isDeletingChangeMsg: {
        type: Boolean,
        value: false,
      },
      _isCleanerLogExperimentEnabled: Boolean,
    };
  }

  static get observers() {
    return [
      '_updateExpandedClass(message.expanded)',
    ];
  }

  constructor() {
    super();
    this.flagsService = appContext.flagsService;
  }

  /** @override */
  created() {
    super.created();
    this.addEventListener('click',
        e => this._handleClick(e));
  }

  /** @override */
  ready() {
    super.ready();
    this._isCleanerLogExperimentEnabled = this.flagsService
        .isEnabled(ExperimentIds.CLEANER_CHANGELOG);
    this.$.restAPI.getConfig().then(config => {
      this.config = config;
    });
    this.$.restAPI.getLoggedIn().then(loggedIn => {
      this._loggedIn = loggedIn;
    });
    this.$.restAPI.getIsAdmin().then(isAdmin => {
      this._isAdmin = isAdmin;
    });
  }

  _updateExpandedClass(expanded) {
    if (expanded) {
      this.classList.add('expanded');
    } else {
      this.classList.remove('expanded');
    }
  }

  _computeCommentCountText(
      comments, threadsLength, isCleanerLogExperimentEnabled) {
    // TODO(taoalpha): clean up after cleaner-changelog experiment launched
    if (isCleanerLogExperimentEnabled) {
      if (threadsLength === 0) {
        return undefined;
      } else if (threadsLength === 1) {
        return '1 comment';
      } else {
        return `${threadsLength} comments`;
      }
    } else {
      if (!comments) return undefined;
      let count = 0;
      for (const file in comments) {
        if (comments.hasOwnProperty(file)) {
          const commentArray = comments[file] || [];
          count += commentArray.length;
        }
      }
      if (count === 0) {
        return undefined;
      } else if (count === 1) {
        return '1 comment';
      } else {
        return `${count} comments`;
      }
    }
  }

  _onThreadListModified() {
    // TODO(taoalpha): this won't propagate the changes to the files
    // should consider replacing this with either top level events
    // or gerrit level events

    // emit the event so change-view can also get updated with latest changes
    this.fire('comment-refresh');
  }

  _computeMessageContentExpanded(content, tag) {
    return this._computeMessageContent(content, tag, true);
  }

  _patchsetCommentSummary(commentThreads) {
    const id = this.message.id;
    if (!id) return '';
    const patchsetThreads = commentThreads.filter(thread =>
      thread.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS);
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
        if (comment.change_message_id === id) { return comment.message; }
      }
    }
    return '';
  }

  _computeMessageContentCollapsed(content, tag, commentThreads) {
    const summary =
      this._computeMessageContent(content, tag, false);
    if (summary || !commentThreads) return summary;
    return this._patchsetCommentSummary(commentThreads);
  }

  _computeMessageContent(content, tag, isExpanded) {
    content = content || '';
    tag = tag || '';
    const isNewPatchSet = tag.endsWith(':newPatchSet') ||
        tag.endsWith(':newWipPatchSet');
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

  _computeAuthor(message) {
    return message.author || message.updated_by;
  }

  _computeShowOnBehalfOf(message) {
    const author = message.author || message.updated_by;
    return !!(author && message.real_author &&
        author._account_id != message.real_author._account_id);
  }

  _computeShowReplyButton(message, loggedIn) {
    return message && !!message.message && loggedIn &&
        !this._computeIsAutomated(message);
  }

  _computeExpanded(expanded) {
    return expanded;
  }

  _handleClick(e) {
    if (this.message.expanded) { return; }
    e.stopPropagation();
    this.set('message.expanded', true);
  }

  _handleAuthorClick(e) {
    if (!this.message.expanded) { return; }
    e.stopPropagation();
    this.set('message.expanded', false);
  }

  _computeIsAutomated(message) {
    return !!(message.reviewer ||
        this._computeIsReviewerUpdate(message) ||
        (message.tag && message.tag.startsWith('autogenerated')));
  }

  _computeIsHidden(hideAutomated, isAutomated) {
    return hideAutomated && isAutomated;
  }

  _computeIsReviewerUpdate(message) {
    return message.type === 'REVIEWER_UPDATE';
  }

  _getScores(message, labelExtremes) {
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
    return scoresRaw.split(' ')
        .map(s => s.match(LABEL_TITLE_SCORE_PATTERN))
        .filter(ms =>
          ms && ms.length === 4 && labelExtremes.hasOwnProperty(ms[2]))
        .map(ms => {
          const label = ms[2];
          const value = ms[1] === '-' ? 'removed' : ms[3];
          return {label, value};
        });
  }

  _computeScoreClass(score, labelExtremes) {
    // Polymer 2: check for undefined
    if ([score, labelExtremes].includes(undefined)) {
      return '';
    }
    if (score.value === 'removed') {
      return 'removed';
    }
    const classes = [];
    if (score.value > 0) {
      classes.push('positive');
    } else if (score.value < 0) {
      classes.push('negative');
    }
    const extremes = labelExtremes[score.label];
    if (extremes) {
      const intScore = parseInt(score.value, 10);
      if (intScore === extremes.max) {
        classes.push('max');
      } else if (intScore === extremes.min) {
        classes.push('min');
      }
    }
    return classes.join(' ');
  }

  _computeClass(expanded) {
    const classes = [];
    classes.push(expanded ? 'expanded' : 'collapsed');
    return classes.join(' ');
  }

  _handleAnchorClick(e) {
    e.preventDefault();
    this.dispatchEvent(new CustomEvent('message-anchor-tap', {
      bubbles: true,
      composed: true,
      detail: {id: this.message.id},
    }));
  }

  _handleReplyTap(e) {
    e.preventDefault();
    this.dispatchEvent(new CustomEvent('reply', {
      detail: {message: this.message},
      composed: true, bubbles: true,
    }));
  }

  _handleDeleteMessage(e) {
    e.preventDefault();
    if (!this.message || !this.message.id) return;
    this._isDeletingChangeMsg = true;
    this.$.restAPI.deleteChangeCommitMessage(this.changeNum, this.message.id)
        .then(() => {
          this._isDeletingChangeMsg = false;
          this.dispatchEvent(new CustomEvent('change-message-deleted', {
            detail: {message: this.message},
            composed: true, bubbles: true,
          }));
        });
  }

  _projectNameChanged(name) {
    this.$.restAPI.getProjectConfig(name).then(config => {
      this._projectConfig = config;
    });
  }

  _computeExpandToggleIcon(expanded) {
    return expanded ? 'gr-icons:expand-less' : 'gr-icons:expand-more';
  }

  _toggleExpanded(e) {
    e.stopPropagation();
    this.set('message.expanded', !this.message.expanded);
  }
}

customElements.define(GrMessage.is, GrMessage);
