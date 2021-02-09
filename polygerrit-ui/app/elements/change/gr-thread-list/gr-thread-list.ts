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
import '@polymer/paper-toggle-button/paper-toggle-button';
import '../../../styles/shared-styles';
import '../../shared/gr-comment-thread/gr-comment-thread';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-thread-list_html';
import {parseDate} from '../../../utils/date-util';

import {CommentSide, SpecialFilePath} from '../../../constants/constants';
import {customElement, observe, property} from '@polymer/decorators';
import {
  PolymerSpliceChange,
  PolymerDeepPropertyChange,
} from '@polymer/polymer/interfaces';
import {ChangeInfo} from '../../../types/common';
import {
  CommentThread,
  isDraft,
  UIRobot,
  isUnresolved,
  isDraftThread,
} from '../../../utils/comment-util';
import {pluralize} from '../../../utils/string-util';
import {fireThreadListModifiedEvent} from '../../../utils/event-util';
import {KnownExperimentId} from '../../../services/flags/flags';
import {appContext} from '../../../services/app-context';

interface CommentThreadWithInfo {
  thread: CommentThread;
  hasRobotComment: boolean;
  hasHumanReplyToRobotComment: boolean;
  unresolved: boolean;
  isEditing: boolean;
  hasDraft: boolean;
  updated?: Date;
}

@customElement('gr-thread-list')
export class GrThreadList extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: Array})
  threads: CommentThread[] = [];

  @property({type: String})
  changeNum?: string;

  @property({type: Boolean})
  loggedIn?: boolean;

  @property({type: Array})
  _sortedThreads: CommentThread[] = [];

  @property({
    computed:
      '_computeDisplayedThreads(_sortedThreads.*, unresolvedOnly, ' +
      '_draftsOnly, onlyShowRobotCommentsWithHumanReply)',
    type: Array,
  })
  _displayedThreads: CommentThread[] = [];

  // thread-list is used in multiple places like the change log, hence
  // keeping the default to be false. When used in comments tab, it's
  // set as true.
  @property({type: Boolean})
  unresolvedOnly = false;

  @property({type: Boolean})
  _draftsOnly = false;

  @property({type: Boolean})
  onlyShowRobotCommentsWithHumanReply = false;

  @property({type: Boolean})
  hideToggleButtons = false;

  @property({type: Boolean})
  _isNewChangeSummaryUiEnabled = false;

  flagsService = appContext.flagsService;

  /** @override */
  ready() {
    super.ready();
    this._isNewChangeSummaryUiEnabled = this.flagsService.isEnabled(
      KnownExperimentId.NEW_CHANGE_SUMMARY_UI
    );
  }

  _computeShowDraftToggle(loggedIn?: boolean) {
    return loggedIn ? 'show' : '';
  }

  _showEmptyThreadsMessage(
    threads: CommentThread[],
    displayedThreads: CommentThread[],
    unresolvedOnly: boolean
  ) {
    if (!threads || !displayedThreads) return false;
    return !threads.length || (unresolvedOnly && !displayedThreads.length);
  }

  _computeEmptyThreadsMessage(threads: CommentThread[]) {
    return !threads.length ? 'No comments.' : 'No unresolved comments';
  }

  _showPartyPopper(threads: CommentThread[]) {
    return !!threads.length;
  }

  _computeResolvedCommentsMessage(
    threads: CommentThread[],
    displayedThreads: CommentThread[],
    unresolvedOnly: boolean
  ) {
    if (unresolvedOnly && threads.length && !displayedThreads.length) {
      return `Show ${pluralize(threads.length, 'resolved comment')}`;
    }
    return '';
  }

  _showResolvedCommentsButton(
    threads: CommentThread[],
    displayedThreads: CommentThread[],
    unresolvedOnly: boolean
  ) {
    return unresolvedOnly && threads.length && !displayedThreads.length;
  }

  _handleResolvedCommentsMessageClick() {
    this.unresolvedOnly = !this.unresolvedOnly;
  }

  _compareThreads(c1: CommentThreadWithInfo, c2: CommentThreadWithInfo) {
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
      if (!c1.thread.patchNum) return 1;
      if (!c2.thread.patchNum) return -1;
      // Threads left on Base when comparing Base vs X have patchNum = X
      // and CommentSide = PARENT
      // Threads left on 'edit' have patchNum set as latestPatchNum
      return c1.thread.patchNum > c2.thread.patchNum ? -1 : 1;
    }

    if (c2.unresolved !== c1.unresolved) {
      if (!c1.unresolved) return 1;
      if (!c2.unresolved) return -1;
    }

    if (c2.hasDraft !== c1.hasDraft) {
      if (!c1.hasDraft) return 1;
      if (!c2.hasDraft) return -1;
    }

    if (c2.updated !== c1.updated) {
      if (!c1.updated) return 1;
      if (!c2.updated) return -1;
      return c2.updated.getTime() - c1.updated.getTime();
    }

    if (c2.thread.rootId !== c1.thread.rootId) {
      if (!c1.thread.rootId) return 1;
      if (!c2.thread.rootId) return -1;
      return c1.thread.rootId.localeCompare(c2.thread.rootId);
    }

    return 0;
  }

  /**
   * Observer on threads and update _sortedThreads when needed.
   * Order as follows:
   * - Patchset level threads (descending based on patchset number)
   * - unresolved
   * - comments with drafts
   * - comments without drafts
   * - resolved
   * - comments with drafts
   * - comments without drafts
   * - File name
   * - Line number
   * - Unresolved (descending based on patchset number)
   * - comments with drafts
   * - comments without drafts
   * - Resolved (descending based on patchset number)
   * - comments with drafts
   * - comments without drafts
   *
   * @param threads
   * @param spliceRecord
   */
  @observe('threads', 'threads.splices')
  _updateSortedThreads(
    threads: CommentThread[],
    _: PolymerSpliceChange<CommentThread[]>
  ) {
    if (!threads || threads.length === 0) {
      this._sortedThreads = [];
      this._displayedThreads = [];
      return;
    }
    // We only want to sort on thread additions / removals to avoid
    // re-rendering on modifications (add new reply / edit draft etc.).
    // https://polymer-library.polymer-project.org/3.0/docs/devguide/observers#array-observation
    // TODO(TS): We have removed a buggy check of the splices here. A splice
    // with addedCount > 0 or removed.length > 0 should also cause re-sorting
    // and re-rendering, but apparently spliceRecord is always undefined for
    // whatever reason.
    if (this._sortedThreads.length === threads.length) {
      // Instead of replacing the _sortedThreads which will trigger a re-render,
      // we override all threads inside of it.

      for (const thread of threads) {
        const idxInSortedThreads = this._sortedThreads.findIndex(
          t => t.rootId === thread.rootId
        );
        this.set(`_sortedThreads.${idxInSortedThreads}`, {...thread});
      }
      return;
    }

    const threadsWithInfo = threads.map(thread =>
      this._getThreadWithStatusInfo(thread)
    );
    this._sortedThreads = threadsWithInfo
      .sort((t1, t2) => this._compareThreads(t1, t2))
      .map(threadInfo => threadInfo.thread);
  }

  _computeDisplayedThreads(
    sortedThreadsRecord?: PolymerDeepPropertyChange<
      CommentThread[],
      CommentThread[]
    >,
    unresolvedOnly?: boolean,
    draftsOnly?: boolean,
    onlyShowRobotCommentsWithHumanReply?: boolean
  ) {
    if (!sortedThreadsRecord || !sortedThreadsRecord.base) return [];
    return sortedThreadsRecord.base.filter(t =>
      this._shouldShowThread(
        t,
        unresolvedOnly,
        draftsOnly,
        onlyShowRobotCommentsWithHumanReply
      )
    );
  }

  _isFirstThreadWithFileName(
    displayedThreads: CommentThread[],
    thread: CommentThread,
    unresolvedOnly?: boolean,
    draftsOnly?: boolean,
    onlyShowRobotCommentsWithHumanReply?: boolean
  ) {
    const threads = displayedThreads.filter(t =>
      this._shouldShowThread(
        t,
        unresolvedOnly,
        draftsOnly,
        onlyShowRobotCommentsWithHumanReply
      )
    );
    const index = threads.findIndex(t => t.rootId === thread.rootId);
    if (index === -1) {
      return false;
    }
    return index === 0 || threads[index - 1].path !== threads[index].path;
  }

  _shouldRenderSeparator(
    displayedThreads: CommentThread[],
    thread: CommentThread,
    unresolvedOnly?: boolean,
    draftsOnly?: boolean,
    onlyShowRobotCommentsWithHumanReply?: boolean
  ) {
    const threads = displayedThreads.filter(t =>
      this._shouldShowThread(
        t,
        unresolvedOnly,
        draftsOnly,
        onlyShowRobotCommentsWithHumanReply
      )
    );
    const index = threads.findIndex(t => t.rootId === thread.rootId);
    if (index === -1) {
      return false;
    }
    return (
      index > 0 &&
      this._isFirstThreadWithFileName(
        displayedThreads,
        thread,
        unresolvedOnly,
        draftsOnly,
        onlyShowRobotCommentsWithHumanReply
      )
    );
  }

  _shouldShowThread(
    thread: CommentThread,
    unresolvedOnly?: boolean,
    draftsOnly?: boolean,
    onlyShowRobotCommentsWithHumanReply?: boolean
  ) {
    if (
      [
        thread,
        unresolvedOnly,
        draftsOnly,
        onlyShowRobotCommentsWithHumanReply,
      ].includes(undefined)
    ) {
      return false;
    }

    if (
      !draftsOnly &&
      !unresolvedOnly &&
      !onlyShowRobotCommentsWithHumanReply
    ) {
      return true;
    }

    const threadInfo = this._getThreadWithStatusInfo(thread);

    if (threadInfo.isEditing) {
      return true;
    }

    if (
      threadInfo.hasRobotComment &&
      onlyShowRobotCommentsWithHumanReply &&
      !threadInfo.hasHumanReplyToRobotComment
    ) {
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

  _getThreadWithStatusInfo(thread: CommentThread): CommentThreadWithInfo {
    const comments = thread.comments;
    const lastComment = comments.length
      ? comments[comments.length - 1]
      : undefined;
    let hasRobotComment = false;
    let hasHumanReplyToRobotComment = false;
    comments.forEach(comment => {
      if ((comment as UIRobot).robot_id) {
        hasRobotComment = true;
      } else if (hasRobotComment) {
        hasHumanReplyToRobotComment = true;
      }
    });
    let updated = undefined;
    if (lastComment) {
      if (isDraft(lastComment)) updated = lastComment.__date;
      if (lastComment.updated) updated = parseDate(lastComment.updated);
    }

    return {
      thread,
      hasRobotComment,
      hasHumanReplyToRobotComment,
      unresolved: !!lastComment && !!lastComment.unresolved,
      isEditing: isDraft(lastComment) && !!lastComment.__editing,
      hasDraft: !!lastComment && isDraft(lastComment),
      updated,
    };
  }

  removeThread(rootId: string) {
    for (let i = 0; i < this.threads.length; i++) {
      if (this.threads[i].rootId === rootId) {
        this.splice('threads', i, 1);
        // Needed to ensure threads get re-rendered in the correct order.
        flush();
        return;
      }
    }
  }

  _handleThreadDiscard(e: CustomEvent) {
    this.removeThread(e.detail.rootId);
  }

  _handleCommentsChanged(e: CustomEvent) {
    fireThreadListModifiedEvent(this, e.detail.rootId, e.detail.path);
  }

  _isOnParent(side?: CommentSide) {
    // TODO(TS): That looks like a bug? CommentSide.REVISION will also be
    // classified as parent??
    return !!side;
  }

  _handleOnlyUnresolved() {
    this.unresolvedOnly = true;
    this._draftsOnly = false;
  }

  _handleOnlyDrafts() {
    this._draftsOnly = true;
    this.unresolvedOnly = false;
  }

  _handleAllComments() {
    this._draftsOnly = false;
    this.unresolvedOnly = false;
  }

  _showAllComments(draftsOnly?: boolean, unresolvedOnly?: boolean) {
    return !draftsOnly && !unresolvedOnly;
  }

  _countUnresolved(threads?: CommentThread[]) {
    return threads?.filter(isUnresolved).length ?? 0;
  }

  _countAllThreads(threads?: CommentThread[]) {
    return threads?.length ?? 0;
  }

  _countDrafts(threads?: CommentThread[]) {
    return threads?.filter(isDraftThread).length ?? 0;
  }

  /**
   * Work around a issue on iOS when clicking turns into double tap
   */
  _onTapUnresolvedToggle(e: Event) {
    e.preventDefault();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-thread-list': GrThreadList;
  }
}
