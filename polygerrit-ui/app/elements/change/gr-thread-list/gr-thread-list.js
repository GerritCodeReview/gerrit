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
import '@polymer/paper-toggle-button/paper-toggle-button.js';
import '../../../styles/shared-styles.js';
import '../../shared/gr-comment-thread/gr-comment-thread.js';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-thread-list_html.js';
import {parseDate} from '../../../utils/date-util.js';

import {NO_THREADS_MSG} from '../../../constants/messages.js';
import {SpecialFilePath} from '../../../constants/constants.js';
import {
  computeLatestPatchNum,
  computeAllPatchSets,
} from '../../../utils/patch-set-util.js';

/**
 * Fired when a comment is saved or deleted
 *
 * @event thread-list-modified
 * @extends PolymerElement
 */
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
      emptyThreadMsg: {
        type: String,
        value: NO_THREADS_MSG,
      },
    };
  }

  static get observers() {
    return ['_updateSortedThreads(threads, threads.splices)'];
  }

  _computeLatestPatchNum(change) {
    if (!change || !change.revisions) return 0;
    return computeLatestPatchNum(computeAllPatchSets(change));
  }

  _computeShowDraftToggle(loggedIn) {
    return loggedIn ? 'show' : '';
  }

  _compareThreads(c1, c2) {
    if (c1.thread.path !== c2.thread.path) {
      // '/PATCHSET' will not come before '/COMMIT' when sorting
      // alphabetically so move it to the front explicitly
      if (c1.thread.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS) {
        return -1;
      }
      if (c2.thread.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS) {
        return 1;
      }
      return c1.thread.path.localeCompare(c2.thread.path);
    }

    // Patchset comments have no line/range associated with them
    if (c1.thread.line !== c2.thread.line) {
      if (!c1.thread.line || !c2.thread.line) {
        // one of them is a file level comment, show first
        return c1.thread.line ? 1 : -1;
      }
      return c1.thread.line < c2.thread.line ? -1 : 1;
    }

    if (c1.thread.patchNum !== c2.thread.patchNum) {
      return c1.thread.patchNum > c2.thread.patchNum ? -1 : 1;
    }

    if (c2.unresolved !== c1.unresolved) {
      if (!c1.unresolved) { return 1; }
      if (!c2.unresolved) { return -1; }
    }

    if (c2.hasDraft !== c1.hasDraft) {
      if (!c1.hasDraft) { return 1; }
      if (!c2.hasDraft) { return -1; }
    }

    const c1Date = c1.__date || parseDate(c1.updated);
    const c2Date = c2.__date || parseDate(c2.updated);
    const dateCompare = c2Date - c1Date;
    if (dateCompare === 0 && (!c1.id || !c1.id.localeCompare)) {
      return 0;
    }
    return dateCompare ? dateCompare : c1.id.localeCompare(c2.id);
  }

  /**
   * Observer on threads and update _sortedThreads when needed.
   * Order as follows:
   *  - Patchset level threads (descending based on patchset number)
   *    - unresolved
          - comments with drafts
          - comments without drafts
   *    - resolved
          - comments with drafts
          - comments without drafts
   *  - File name
   *    - Line number
   *      - Unresolved (descending based on patchset number)
   *        - comments with drafts
   *        - comments without drafts
   *      - Resolved (descending based on patchset number)
   *        - comments with drafts
   *        - comments without drafts
   *
   * @param {Array<Object>} threads
   * @param {!Object} spliceRecord
   */
  _updateSortedThreads(threads, spliceRecord) {
    if (!threads) {
      this._sortedThreads = [];
      return;
    }
    // We only want to sort on thread additions / removals to avoid
    // re-rendering on modifications (add new reply / edit draft etc)
    //  https://polymer-library.polymer-project.org/3.0/docs/devguide/observers#array-observation
    const isArrayMutation = spliceRecord &&
      (spliceRecord.indexSplices.addedCount !== 0
        || spliceRecord.indexSplices.removed.length);

    if (this._sortedThreads
        && this._sortedThreads.length === threads.length
        && !isArrayMutation) {
      // Instead of replacing the _sortedThreads which will trigger a re-render,
      // we override all threads inside of it

      for (const thread of threads) {
        const idxInSortedThreads = this._sortedThreads
            .findIndex(t => t.rootId === thread.rootId);
        this.set(`_sortedThreads.${idxInSortedThreads}`, {...thread});
      }
      return;
    }

    const threadsWithInfo = threads
        .map(thread => this._getThreadWithStatusInfo(thread));
    this._sortedThreads = threadsWithInfo.sort((t1, t2) =>
      this._compareThreads(t1, t2)).map(threadInfo => threadInfo.thread);
  }

  _isFirstThreadWithFileName(sortedThreads, thread, unresolvedOnly, draftsOnly,
      onlyShowRobotCommentsWithHumanReply) {
    const threads = sortedThreads.filter(t => this._shouldShowThread(
        t, unresolvedOnly, draftsOnly,
        onlyShowRobotCommentsWithHumanReply));
    const index = threads.findIndex(t => t.rootId === thread.rootId);
    if (index === -1) {
      return false;
    }
    return index === 0 || (threads[index - 1].path !== threads[index].path);
  }

  _shouldRenderSeparator(sortedThreads, thread, unresolvedOnly, draftsOnly,
      onlyShowRobotCommentsWithHumanReply) {
    const threads = sortedThreads.filter(t => this._shouldShowThread(
        t, unresolvedOnly, draftsOnly,
        onlyShowRobotCommentsWithHumanReply));
    const index = threads.findIndex(t => t.rootId === thread.rootId);
    if (index === -1) {
      return false;
    }
    return index > 0 && this._isFirstThreadWithFileName(sortedThreads,
        thread, unresolvedOnly, draftsOnly,
        onlyShowRobotCommentsWithHumanReply);
  }

  _shouldShowThread(thread, unresolvedOnly, draftsOnly,
      onlyShowRobotCommentsWithHumanReply) {
    if ([
      thread,
      unresolvedOnly,
      draftsOnly,
      onlyShowRobotCommentsWithHumanReply,
    ].includes(undefined)) {
      return false;
    }

    if (!draftsOnly
        && !unresolvedOnly
        && !onlyShowRobotCommentsWithHumanReply) {
      return true;
    }

    const threadInfo = this._getThreadWithStatusInfo(thread);

    if (threadInfo.isEditing) {
      return true;
    }

    if (threadInfo.hasRobotComment
       && onlyShowRobotCommentsWithHumanReply
       && !threadInfo.hasHumanReplyToRobotComment) {
      return false;
    }

    let filtersCheck = true;
    if (draftsOnly && unresolvedOnly) {
      filtersCheck = threadInfo.hasDraft && threadInfo.unresolved;
    } else if (draftsOnly) {
      filtersCheck = threadInfo.hasDraft;
    } else if (unresolvedOnly) {
      filtersCheck = threadInfo.unresolved;
    }

    return filtersCheck;
  }

  _getThreadWithStatusInfo(thread) {
    const comments = thread.comments;
    const lastComment = comments[comments.length - 1] || {};
    let hasRobotComment = false;
    let hasHumanReplyToRobotComment = false;
    comments.forEach(comment => {
      if (comment.robot_id) {
        hasRobotComment = true;
      } else if (hasRobotComment) {
        hasHumanReplyToRobotComment = true;
      }
    });

    return {
      thread,
      hasRobotComment,
      hasHumanReplyToRobotComment,
      unresolved: !!lastComment.unresolved,
      isEditing: !!lastComment.__editing,
      hasDraft: !!lastComment.__draft,
      updated: lastComment.updated || lastComment.__date,
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
    this.dispatchEvent(new CustomEvent('thread-list-modified',
        {detail: {rootId: e.detail.rootId, path: e.detail.path}}));
  }

  _isOnParent(side) {
    return !!side;
  }

  /**
   * Work around a issue on iOS when clicking turns into double tap
   */
  _onTapUnresolvedToggle(e) {
    e.preventDefault();
  }
}

customElements.define(GrThreadList.is, GrThreadList);
