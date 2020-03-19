/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import '../../../scripts/bundled-polymer.js';

import '@polymer/paper-toggle-button/paper-toggle-button.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-comment-thread/gr-comment-thread.js';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-thread-list_html.js';

/**
 * Fired when a comment is saved or deleted
 *
 * @event thread-list-modified
 * @extends Polymer.Element
 */
const NO_THREADS_MESSAGE = 'There are no inline comment threads on any diff '
  + 'for this change.';
const NO_ROBOT_COMMENTS_THREADS_MESSAGE = 'There are no findings for this ' +
  'patchset.';
const FINDINGS_TAB_NAME = '__gerrit_internal_findings';

class GrThreadList extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-thread-list'; }

  static get properties() {
    return {
    /** @type {?} */
      change: Object,
      threads: Array,
      changeNum: String,
      loggedIn: Boolean,
      _sortedThreads: {
        type: Array,
      },
      _filteredThreads: {
        type: Array,
        computed: '_computeFilteredThreads(_sortedThreads, ' +
          '_unresolvedOnly, _draftsOnly,' +
          'onlyShowRobotCommentsWithHumanReply)',
      },
      _unresolvedOnly: {
        type: Boolean,
        value: false,
      },
      _draftsOnly: {
        type: Boolean,
        value: false,
      },
      /* Boolean properties used must default to false if passed as attribute
      by the parent */
      onlyShowRobotCommentsWithHumanReply: {
        type: Boolean,
        value: false,
      },
      hideToggleButtons: {
        type: Boolean,
        value: false,
      },
      tab: {
        type: String,
        value: '',
      },
    };
  }

  static get observers() { return ['_computeSortedThreads(threads.*)']; }

  _computeShowDraftToggle(loggedIn) {
    return loggedIn ? 'show' : '';
  }

  _computeNoThreadsMessage(tab) {
    if (tab === FINDINGS_TAB_NAME) {
      return NO_ROBOT_COMMENTS_THREADS_MESSAGE;
    }
    return NO_THREADS_MESSAGE;
  }

  /**
   * Order as follows:
   *  - Unresolved threads with drafts (reverse chronological)
   *  - Unresolved threads without drafts (reverse chronological)
   *  - Resolved threads with drafts (reverse chronological)
   *  - Resolved threads without drafts (reverse chronological)
   *
   * @param {!Object} changeRecord
   */
  _computeSortedThreads(changeRecord) {
    const threads = changeRecord.base;
    if (!threads) { return []; }
    this._updateSortedThreads(threads);
  }

  _updateSortedThreads(threads) {
    this._sortedThreads =
        threads.map(this._getThreadWithSortInfo).sort((c1, c2) => {
          const c1Date = c1.__date || util.parseDate(c1.updated);
          const c2Date = c2.__date || util.parseDate(c2.updated);
          const dateCompare = c2Date - c1Date;
          if (c2.unresolved || c1.unresolved) {
            if (!c1.unresolved) { return 1; }
            if (!c2.unresolved) { return -1; }
          }
          if (c2.hasDraft || c1.hasDraft) {
            if (!c1.hasDraft) { return 1; }
            if (!c2.hasDraft) { return -1; }
          }

          if (dateCompare === 0 && (!c1.id || !c1.id.localeCompare)) {
            return 0;
          }
          return dateCompare ? dateCompare : c1.id.localeCompare(c2.id);
        });
  }

  _computeFilteredThreads(sortedThreads, unresolvedOnly, draftsOnly,
      onlyShowRobotCommentsWithHumanReply) {
    // Polymer 2: check for undefined
    if ([
      sortedThreads,
      unresolvedOnly,
      draftsOnly,
      onlyShowRobotCommentsWithHumanReply,
    ].some(arg => arg === undefined)) {
      return undefined;
    }

    return sortedThreads.filter(c => {
      if (draftsOnly) {
        return c.hasDraft;
      } else if (unresolvedOnly) {
        return c.unresolved;
      } else {
        const comments = c && c.thread && c.thread.comments;
        let robotComment = false;
        let humanReplyToRobotComment = false;
        comments.forEach(comment => {
          if (comment.robot_id) {
            robotComment = true;
          } else if (robotComment) {
            // Robot comment exists and human comment exists after it
            humanReplyToRobotComment = true;
          }
        });
        if (robotComment && onlyShowRobotCommentsWithHumanReply) {
          return humanReplyToRobotComment;
        }
        return c;
      }
    }).map(threadInfo => threadInfo.thread);
  }

  _getThreadWithSortInfo(thread) {
    const lastComment = thread.comments[thread.comments.length - 1] || {};

    const lastNonDraftComment =
        (lastComment.__draft && thread.comments.length > 1) ?
          thread.comments[thread.comments.length - 2] :
          lastComment;

    // when lastComment is a draft, updated may not available yet
    let threadUpdated = lastComment.updated;
    if (!lastComment.updated) {
      threadUpdated = (lastComment.__date || new Date()).toISOString();
    }

    return {
      thread,
      // Use the unresolved bit for the last non draft comment. This is what
      // anybody other than the current user would see.
      unresolved: !!lastNonDraftComment.unresolved,
      hasDraft: !!lastComment.__draft,
      updated: threadUpdated,
    };
  }

  removeThread(rootId) {
    for (let i = 0; i < this.threads.length; i++) {
      if (this.threads[i].rootId === rootId) {
        this.splice('threads', i, 1);
        // Needed to ensure threads get re-rendered in the correct order.
        flush();
        return;
      }
    }
  }

  _handleThreadDiscard(e) {
    this.removeThread(e.detail.rootId);
  }

  _handleCommentsChanged(e) {
    // Reset threads so thread computations occur on deep array changes to
    // threads comments that are not observed naturally.
    this._updateSortedThreads(this.threads);

    this.dispatchEvent(new CustomEvent('thread-list-modified',
        {detail: {rootId: e.detail.rootId, path: e.detail.path}}));
  }

  _isOnParent(side) {
    return !!side;
  }
}

customElements.define(GrThreadList.is, GrThreadList);
