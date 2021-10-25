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
import '../../../styles/shared-styles';
import '../../shared/gr-comment-thread/gr-comment-thread';
import '../../shared/gr-dropdown-list/gr-dropdown-list';

import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-thread-list_html';
import {parseDate} from '../../../utils/date-util';

import {CommentSide, SpecialFilePath} from '../../../constants/constants';
import {computed, customElement, observe, property} from '@polymer/decorators';
import {
  PolymerSpliceChange,
  PolymerDeepPropertyChange,
} from '@polymer/polymer/interfaces';
import {
  AccountDetailInfo,
  AccountInfo,
  ChangeInfo,
  NumericChangeId,
  UrlEncodedCommentId,
} from '../../../types/common';
import {
  CommentThread,
  isDraft,
  isUnresolved,
  isDraftThread,
  isRobotThread,
  hasHumanReply,
  getCommentAuthors,
  computeId,
  UIComment,
} from '../../../utils/comment-util';
import {pluralize} from '../../../utils/string-util';
import {assertIsDefined, assertNever} from '../../../utils/common-util';
import {CommentTabState} from '../../../types/events';
import {DropdownItem} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {GrAccountChip} from '../../shared/gr-account-chip/gr-account-chip';
import {commentLocation} from '../../../utils/dom-util';

interface CommentThreadWithInfo {
  thread: CommentThread;
  hasRobotComment: boolean;
  hasHumanReplyToRobotComment: boolean;
  unresolved: boolean;
  isEditing: boolean;
  hasDraft: boolean;
  updated?: Date;
}

enum SortDropdownState {
  TIMESTAMP = 'Latest timestamp',
  FILES = 'Files',
}

export const __testOnly_SortDropdownState = SortDropdownState;

@customElement('gr-thread-list')
export class GrThreadList extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  @property({type: Object})
  change?: ChangeInfo;

  @property({type: Array})
  threads: CommentThread[] = [];

  @property({type: String})
  changeNum?: NumericChangeId;

  @property({type: Boolean})
  loggedIn?: boolean;

  @property({type: Array})
  _sortedThreads: CommentThread[] = [];

  @property({type: Boolean})
  showCommentContext = false;

  @property({
    computed:
      '_computeDisplayedThreads(_sortedThreads.*, unresolvedOnly, ' +
      '_draftsOnly, onlyShowRobotCommentsWithHumanReply, selectedAuthors)',
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
  hideDropdown = false;

  @property({type: Object, observer: '_commentTabStateChange'})
  commentTabState?: CommentTabState;

  @property({type: Object})
  sortDropdownValue: SortDropdownState = SortDropdownState.TIMESTAMP;

  @property({type: Array, notify: true})
  selectedAuthors: AccountInfo[] = [];

  @property({type: Object})
  account?: AccountDetailInfo;

  @computed('unresolvedOnly', '_draftsOnly')
  get commentsDropdownValue() {
    // set initial value and triggered when comment summary chips are clicked
    if (this._draftsOnly) return CommentTabState.DRAFTS;
    return this.unresolvedOnly
      ? CommentTabState.UNRESOLVED
      : CommentTabState.SHOW_ALL;
  }

  @property({type: String})
  scrollCommentId?: UrlEncodedCommentId;

  _showEmptyThreadsMessage(
    threads: CommentThread[],
    displayedThreads: CommentThread[],
    unresolvedOnly: boolean
  ) {
    if (!threads || !displayedThreads) return false;
    return !threads.length || (unresolvedOnly && !displayedThreads.length);
  }

  _computeEmptyThreadsMessage(threads: CommentThread[]) {
    return !threads.length ? 'No comments' : 'No unresolved comments';
  }

  _showPartyPopper(threads: CommentThread[]) {
    return !!threads.length;
  }

  _computeResolvedCommentsMessage(
    threads: CommentThread[],
    displayedThreads: CommentThread[],
    unresolvedOnly: boolean,
    onlyShowRobotCommentsWithHumanReply: boolean
  ) {
    if (onlyShowRobotCommentsWithHumanReply) {
      threads = this.filterRobotThreadsWithoutHumanReply(threads) ?? [];
    }
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

  getSortDropdownEntires() {
    return [
      {text: SortDropdownState.FILES, value: SortDropdownState.FILES},
      {text: SortDropdownState.TIMESTAMP, value: SortDropdownState.TIMESTAMP},
    ];
  }

  getCommentsDropdownEntires(threads: CommentThread[], loggedIn?: boolean) {
    const items: DropdownItem[] = [
      {
        text: `Unresolved (${this._countUnresolved(threads)})`,
        value: CommentTabState.UNRESOLVED,
      },
      {
        text: `All (${this._countAllThreads(threads)})`,
        value: CommentTabState.SHOW_ALL,
      },
    ];
    if (loggedIn)
      items.splice(1, 0, {
        text: `Drafts (${this._countDrafts(threads)})`,
        value: CommentTabState.DRAFTS,
      });
    return items;
  }

  getCommentAuthors(threads?: CommentThread[], account?: AccountDetailInfo) {
    return getCommentAuthors(threads, account);
  }

  handleAccountClicked(e: MouseEvent) {
    const account = (e.target as GrAccountChip).account;
    assertIsDefined(account, 'account');
    const index = this.selectedAuthors.findIndex(
      author => author._account_id === account._account_id
    );
    if (index === -1) this.push('selectedAuthors', account);
    else this.splice('selectedAuthors', index, 1);
    // re-assign so that isSelected template method is called
    this.selectedAuthors = [...this.selectedAuthors];
  }

  isSelected(author: AccountInfo, selectedAuthors: AccountInfo[]) {
    return selectedAuthors.some(a => a._account_id === author._account_id);
  }

  computeShouldScrollIntoView(
    comments: UIComment[],
    scrollCommentId?: UrlEncodedCommentId
  ) {
    const comment = comments?.[0];
    if (!comment) return false;
    return computeId(comment) === scrollCommentId;
  }

  handleSortDropdownValueChange(e: CustomEvent) {
    this.sortDropdownValue = e.detail.value;
    /*
     * Ideally we would have updateSortedThreads observe on sortDropdownValue
     * but the method triggered re-render only when the length of threads
     * changes, hence keep the explicit resortThreads method
     */
    this.resortThreads(this.threads);
  }

  handleCommentsDropdownValueChange(e: CustomEvent) {
    const value = e.detail.value;
    if (value === CommentTabState.UNRESOLVED) this._handleOnlyUnresolved();
    else if (value === CommentTabState.DRAFTS) this._handleOnlyDrafts();
    else this._handleAllComments();
  }

  _compareThreads(c1: CommentThreadWithInfo, c2: CommentThreadWithInfo) {
    if (
      this.sortDropdownValue === SortDropdownState.TIMESTAMP &&
      !this.hideDropdown
    ) {
      if (c1.updated && c2.updated) return c1.updated > c2.updated ? -1 : 1;
    }

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

  resortThreads(threads: CommentThread[]) {
    const threadsWithInfo = threads.map(thread =>
      this._getThreadWithStatusInfo(thread)
    );
    this._sortedThreads = threadsWithInfo
      .sort((t1, t2) => this._compareThreads(t1, t2))
      .map(threadInfo => threadInfo.thread);
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
    // If there is an unsaved draftThread which is supposed to be replaced with
    // a saved draftThread then resort all threads
    const unsavedThread = this._sortedThreads.some(thread =>
      thread.rootId?.includes('draft__')
    );
    if (this._sortedThreads.length === threads.length && !unsavedThread) {
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

    this.resortThreads(threads);
  }

  _computeDisplayedThreads(
    sortedThreadsRecord?: PolymerDeepPropertyChange<
      CommentThread[],
      CommentThread[]
    >,
    unresolvedOnly?: boolean,
    draftsOnly?: boolean,
    onlyShowRobotCommentsWithHumanReply?: boolean,
    selectedAuthors?: AccountInfo[]
  ) {
    if (!sortedThreadsRecord || !sortedThreadsRecord.base) return [];
    console.log(
      `COMMENT _computeDisplayedThreads: ${unresolvedOnly} ${draftsOnly} ${selectedAuthors} ${commentLocation(
        this
      )}`
    );
    return sortedThreadsRecord.base.filter(t =>
      this._shouldShowThread(
        t,
        unresolvedOnly,
        draftsOnly,
        onlyShowRobotCommentsWithHumanReply,
        selectedAuthors
      )
    );
  }

  _isFirstThreadWithFileName(
    displayedThreads: CommentThread[],
    thread: CommentThread,
    unresolvedOnly?: boolean,
    draftsOnly?: boolean,
    onlyShowRobotCommentsWithHumanReply?: boolean,
    selectedAuthors?: AccountInfo[]
  ) {
    const threads = displayedThreads.filter(t =>
      this._shouldShowThread(
        t,
        unresolvedOnly,
        draftsOnly,
        onlyShowRobotCommentsWithHumanReply,
        selectedAuthors
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
    onlyShowRobotCommentsWithHumanReply?: boolean,
    selectedAuthors?: AccountInfo[]
  ) {
    const threads = displayedThreads.filter(t =>
      this._shouldShowThread(
        t,
        unresolvedOnly,
        draftsOnly,
        onlyShowRobotCommentsWithHumanReply,
        selectedAuthors
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
        onlyShowRobotCommentsWithHumanReply,
        selectedAuthors
      )
    );
  }

  _shouldShowThread(
    thread: CommentThread,
    unresolvedOnly?: boolean,
    draftsOnly?: boolean,
    onlyShowRobotCommentsWithHumanReply?: boolean,
    selectedAuthors?: AccountInfo[]
  ) {
    if (
      [
        thread,
        unresolvedOnly,
        draftsOnly,
        onlyShowRobotCommentsWithHumanReply,
        selectedAuthors,
      ].includes(undefined)
    ) {
      return false;
    }

    if (selectedAuthors!.length) {
      if (
        !thread.comments.some(
          c =>
            c.author &&
            selectedAuthors!.some(
              author => c.author!._account_id === author._account_id
            )
        )
      ) {
        return false;
      }
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
    const hasRobotComment = isRobotThread(thread);
    const hasHumanReplyToRobotComment =
      hasRobotComment && hasHumanReply(thread);
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
    return (
      this.filterRobotThreadsWithoutHumanReply(threads)?.filter(isUnresolved)
        .length ?? 0
    );
  }

  _countAllThreads(threads?: CommentThread[]) {
    return this.filterRobotThreadsWithoutHumanReply(threads)?.length ?? 0;
  }

  _countDrafts(threads?: CommentThread[]) {
    return (
      this.filterRobotThreadsWithoutHumanReply(threads)?.filter(isDraftThread)
        .length ?? 0
    );
  }

  filterRobotThreadsWithoutHumanReply(threads?: CommentThread[]) {
    return threads?.filter(t => !isRobotThread(t) || hasHumanReply(t));
  }

  _commentTabStateChange(
    newValue?: CommentTabState,
    oldValue?: CommentTabState
  ) {
    if (!newValue || newValue === oldValue) return;
    let focusTo: string | undefined;
    switch (newValue) {
      case CommentTabState.UNRESOLVED:
        this._handleOnlyUnresolved();
        // input is null because it's not rendered yet.
        focusTo = '#unresolvedRadio';
        break;
      case CommentTabState.DRAFTS:
        this._handleOnlyDrafts();
        focusTo = '#draftsRadio';
        break;
      case CommentTabState.SHOW_ALL:
        this._handleAllComments();
        focusTo = '#allRadio';
        break;
      default:
        assertNever(newValue, 'Unsupported preferred state');
    }
    const selector = focusTo;
    window.setTimeout(() => {
      const input = this.shadowRoot?.querySelector<HTMLInputElement>(selector);
      input?.focus();
    }, 0);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-thread-list': GrThreadList;
  }
}
