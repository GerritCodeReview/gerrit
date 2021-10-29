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
import {SpecialFilePath} from '../../../constants/constants';
import {
  AccountDetailInfo,
  AccountInfo,
  NumericChangeId,
  UrlEncodedCommentId,
} from '../../../types/common';
import {
  CommentThread,
  getCommentAuthors,
  hasHumanReply,
  isDraftThread,
  isRobotThread,
  isUnresolved,
  lastUpdated,
} from '../../../utils/comment-util';
import {pluralize} from '../../../utils/string-util';
import {assertIsDefined} from '../../../utils/common-util';
import {CommentTabState, ValueChangedEvent} from '../../../types/events';
import {DropdownItem} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {GrAccountChip} from '../../shared/gr-account-chip/gr-account-chip';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, queryAll, state} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {subscribe} from '../../lit/subscription-controller';
import {change$, changeNum$} from '../../../services/change/change-model';
import {account$} from '../../../services/user/user-model';
import {ParsedChangeInfo} from '../../../types/types';
import {repeat} from 'lit/directives/repeat';

enum SortDropdownState {
  TIMESTAMP = 'Latest timestamp',
  FILES = 'Files',
}

export const __testOnly_SortDropdownState = SortDropdownState;

@customElement('gr-thread-list')
export class GrThreadList extends LitElement {
  @queryAll('gr-comment-thread')
  threadElements?: NodeList;

  /**
   * Raw list of threads for the component to show.
   *
   * ATTENTION! This should never be used directly in the component.
   *
   * Either use getAllThreads(), which applies filters that are inherent to what
   * the component is supposed to render,
   * e.g. onlyShowRobotCommentsWithHumanReply.
   *
   * Or use getDisplayedThreads(), which applies the currently selected filters
   * on top.
   */
  @property({type: Array})
  threads: CommentThread[] = [];

  @property({type: Boolean, attribute: 'show-comment-context'})
  showCommentContext = false;

  @property({type: Boolean, attribute: 'unresolved-only'})
  unresolvedOnly = false;

  @property({
    type: Boolean,
    attribute: 'only-show-robot-comments-with-human-reply',
  })
  onlyShowRobotCommentsWithHumanReply = false;

  @property({type: Boolean, attribute: 'hide-dropdown'})
  hideDropdown = false;

  @property({type: Object, attribute: 'comment-tab-state'})
  commentTabState?: CommentTabState;

  @property({type: String, attribute: 'scroll-comment-id'})
  scrollCommentId?: UrlEncodedCommentId;

  @state()
  changeNum?: NumericChangeId;

  @state()
  change?: ParsedChangeInfo;

  @state()
  account?: AccountDetailInfo;

  @state()
  selectedAuthors: AccountInfo[] = [];

  @state()
  sortDropdownValue: SortDropdownState = SortDropdownState.TIMESTAMP;

  @state()
  draftsOnly = false;

  /**
   * Maps thread root ids to whether the corresponding <gr-comment-thread>
   * component is currently in editing mode. This is important so that we can
   * avoid removing it from the list of threads.
   *
   * This is updated by listening to comment-thread-editing-changed events. Such
   * updates will call `requestUpdate()` so that a re-render is triggered.
   */
  private editingThreads = new Map<UrlEncodedCommentId, boolean>();

  constructor() {
    super();
    console.log('gr-thread-list.constructor');
    subscribe(this, changeNum$, x => (this.changeNum = x));
    subscribe(this, change$, x => (this.change = x));
    subscribe(this, account$, x => (this.account = x));
  }

  override willUpdate(changed: PropertyValues) {
    console.log(
      `gr-thread-list.willUpdate ${JSON.stringify([...changed.keys()])}`
    );
    if (changed.has('commentTabState')) this.commentTabStateChanged();
  }

  static override get styles() {
    return [
      sharedStyles,
      css`
        #threads {
          display: block;
        }
        gr-comment-thread {
          display: block;
          margin-bottom: var(--spacing-m);
        }
        .header {
          align-items: center;
          background-color: var(--background-color-primary);
          border-bottom: 1px solid var(--border-color);
          border-top: 1px solid var(--border-color);
          display: flex;
          justify-content: left;
          padding: var(--spacing-s) var(--spacing-l);
        }
        .draftsOnly:not(.unresolvedOnly) gr-comment-thread[has-draft],
        .unresolvedOnly:not(.draftsOnly) gr-comment-thread[unresolved],
        .draftsOnly.unresolvedOnly gr-comment-thread[has-draft][unresolved] {
          display: block;
        }
        .thread-separator {
          border-top: 1px solid var(--border-color);
          margin-top: var(--spacing-xl);
        }
        .show-resolved-comments {
          box-shadow: none;
          padding-left: var(--spacing-m);
        }
        .partypopper {
          margin-right: var(--spacing-s);
        }
        gr-dropdown-list {
          --trigger-style-text-color: var(--primary-text-color);
          --trigger-style-font-family: var(--font-family);
        }
        .filter-text,
        .sort-text,
        .author-text {
          margin-right: var(--spacing-s);
          color: var(--deemphasized-text-color);
        }
        .author-text {
          margin-left: var(--spacing-m);
        }
        gr-account-label {
          --account-max-length: 120px;
          display: inline-block;
          user-select: none;
          --label-border-radius: 8px;
          margin: 0 var(--spacing-xs);
          padding: var(--spacing-xs) var(--spacing-m);
          line-height: var(--line-height-normal);
          cursor: pointer;
        }
        gr-account-label:focus {
          outline: none;
        }
        gr-account-label:hover,
        gr-account-label:hover {
          box-shadow: var(--elevation-level-1);
          cursor: pointer;
        }
      `,
    ];
  }

  override render() {
    console.log('gr-thread-list.render');
    return html`
      ${this.renderDropdown()}
      <div id="threads" part="threads">
        ${this.renderEmptyThreadsMessage()} ${this.renderCommentThreads()}
      </div>
    `;
  }

  private renderDropdown() {
    if (this.hideDropdown) return;
    return html`
      <div class="header">
        <span class="sort-text">Sort By:</span>
        <gr-dropdown-list
          id="sortDropdown"
          .value="${this.sortDropdownValue}"
          @value-change="${(e: CustomEvent) =>
            (this.sortDropdownValue = e.detail.value)}"
          .items="${this.getSortDropdownEntries()}"
        >
        </gr-dropdown-list>
        <span class="separator"></span>
        <span class="filter-text">Filter By:</span>
        <gr-dropdown-list
          id="filterDropdown"
          .value="${this.getCommentsDropdownValue()}"
          @value-change="${this.handleCommentsDropdownValueChange}"
          .items="${this.getCommentsDropdownEntries()}"
        >
        </gr-dropdown-list>
        ${this.renderAuthorChips()}
      </div>
    `;
  }

  private renderAuthorChips() {
    const authors = getCommentAuthors(this.getDisplayedThreads(), this.account);
    if (authors.length === 0) return;
    const chips = authors.map(author => {
      const selected = this.selectedAuthors.some(
        a => a._account_id === author._account_id
      );
      return html`
        <gr-account-label
          .account="${author}"
          @click="${this.handleAccountClicked}"
          selectionChipStyle
          ?selected="${selected}"
        ></gr-account-label>
      `;
    });
    return html`<span class="author-text">From:</span>${chips}`;
  }

  private renderEmptyThreadsMessage() {
    const threads = this.getAllThreads();
    const threadsEmpty = threads.length === 0;
    const displayedEmpty = this.getDisplayedThreads().length === 0;
    if (!this.unresolvedOnly && !threadsEmpty) return;
    if (this.unresolvedOnly && !displayedEmpty) return;
    const popper = html`<span class="partypopper">\&#x1F389</span>`;
    const showPopper = this.showPartyPopper(threads);
    const showButton = this.unresolvedOnly && displayedEmpty && !threadsEmpty;
    const button = html`
      <gr-button
        class="show-resolved-comments"
        link
        @click="${this.handleAllComments}"
        >Show ${pluralize(threads.length, 'resolved comment')}</gr-button
      >
    `;
    return html`
      <div>
        <span>
          ${showPopper ? popper : ''}
          ${threadsEmpty ? 'No comments' : 'No unresolved comments'}
          ${showButton ? button : ''}
        </span>
      </div>
    `;
  }

  private renderCommentThreads() {
    const displayedThreads = this.getDisplayedThreads();
    return repeat(
      displayedThreads,
      thread => thread.rootId,
      (thread, index) => {
        const isFirst =
          index === 0 ||
          displayedThreads[index - 1].path !== displayedThreads[index].path;
        console.log(`render comment-thread ${index} ${isFirst}`);
        return html`
          ${isFirst && index !== 0
            ? html`<div class="thread-separator"></div>`
            : ''}
          <gr-comment-thread
            .thread="${thread}"
            show-file-path
            ?show-ported-comment="${thread.ported}"
            ?show-comment-context="${this.showCommentContext}"
            ?show-file-name="${isFirst}"
            ?should-scroll-into-view="${thread.rootId === this.scrollCommentId}"
            @comment-thread-editing-changed="${(
              e: ValueChangedEvent<boolean>
            ) => {
              console.log(
                `comment-thread-editing-changed: ${thread.rootId} ${e.detail.value}`
              );
              assertIsDefined(thread.rootId, 'thread.rootId');
              this.editingThreads.set(thread.rootId, e.detail.value);
            }}"
          ></gr-comment-thread>
        `;
      }
    );
  }

  private getCommentsDropdownValue() {
    if (this.draftsOnly) return CommentTabState.DRAFTS;
    if (this.unresolvedOnly) return CommentTabState.UNRESOLVED;
    return CommentTabState.SHOW_ALL;
  }

  private showPartyPopper(threads: CommentThread[]) {
    return !!threads.length;
  }

  private getSortDropdownEntries() {
    return [
      {text: SortDropdownState.FILES, value: SortDropdownState.FILES},
      {text: SortDropdownState.TIMESTAMP, value: SortDropdownState.TIMESTAMP},
    ];
  }

  private getCommentsDropdownEntries() {
    const items: DropdownItem[] = [];
    items.push({
      text: `Unresolved (${this.countUnresolved()})`,
      value: CommentTabState.UNRESOLVED,
    });
    if (this.account) {
      items.push({
        text: `Drafts (${this.countDrafts()})`,
        value: CommentTabState.DRAFTS,
      });
    }
    items.push({
      text: `All (${this.countAllThreads()})`,
      value: CommentTabState.SHOW_ALL,
    });
    return items;
  }

  private handleAccountClicked(e: MouseEvent) {
    const account = (e.target as GrAccountChip).account;
    assertIsDefined(account, 'account');
    const predicate = (a: AccountInfo) => a._account_id === account._account_id;
    const found = this.selectedAuthors.find(predicate);
    if (found) {
      this.selectedAuthors = this.selectedAuthors.filter(a => !predicate(a));
    } else {
      this.selectedAuthors = [...this.selectedAuthors, account];
    }
  }

  private handleCommentsDropdownValueChange(e: CustomEvent) {
    const value = e.detail.value;
    if (value === CommentTabState.UNRESOLVED) this.handleOnlyUnresolved();
    else if (value === CommentTabState.DRAFTS) this.handleOnlyDrafts();
    else this.handleAllComments();
  }

  /**
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
   */
  private compareThreads(c1: CommentThread, c2: CommentThread) {
    if (
      this.sortDropdownValue === SortDropdownState.TIMESTAMP &&
      !this.hideDropdown
    ) {
      const c1Time = lastUpdated(c1)?.getTime() ?? 0;
      const c2Time = lastUpdated(c2)?.getTime() ?? 0;
      return c2Time - c1Time;
    }

    if (c1.path !== c2.path) {
      // '/PATCHSET' will not come before '/COMMIT' when sorting
      // alphabetically so move it to the front explicitly
      if (c1.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS) {
        return -1;
      }
      if (c2.path === SpecialFilePath.PATCHSET_LEVEL_COMMENTS) {
        return 1;
      }
      return c1.path.localeCompare(c2.path);
    }

    // Patchset comments have no line/range associated with them
    if (c1.line !== c2.line) {
      if (!c1.line || !c2.line) {
        // one of them is a file level comment, show first
        return c1.line ? 1 : -1;
      }
      return c1.line < c2.line ? -1 : 1;
    }

    if (c1.patchNum !== c2.patchNum) {
      if (!c1.patchNum) return 1;
      if (!c2.patchNum) return -1;
      // Threads left on Base when comparing Base vs X have patchNum = X
      // and CommentSide = PARENT
      // Threads left on 'edit' have patchNum set as latestPatchNum
      return c1.patchNum > c2.patchNum ? -1 : 1;
    }

    if (isUnresolved(c2) !== isUnresolved(c1)) {
      if (!isUnresolved(c1)) return 1;
      if (!isUnresolved(c2)) return -1;
    }

    if (isDraftThread(c2) !== isDraftThread(c1)) {
      if (!isDraftThread(c1)) return 1;
      if (!isDraftThread(c2)) return -1;
    }

    const c1Time = lastUpdated(c1)?.getTime() ?? 0;
    const c2Time = lastUpdated(c2)?.getTime() ?? 0;
    if (c2Time !== c1Time) {
      return c2Time - c1Time;
    }

    if (c2.rootId !== c1.rootId) {
      if (!c1.rootId) return 1;
      if (!c2.rootId) return -1;
      return c1.rootId.localeCompare(c2.rootId);
    }

    return 0;
  }

  /**
   * Returns all threads that the list may show.
   */
  private getAllThreads() {
    return this.threads.filter(
      t =>
        !this.onlyShowRobotCommentsWithHumanReply ||
        !isRobotThread(t) ||
        hasHumanReply(t)
    );
  }

  /**
   * Returns all threads that are currently shown in the list, respecting the
   * currently selected filter.
   */
  private getDisplayedThreads() {
    console.log('gr-thread-list.getDisplayedThreads');
    return this.getAllThreads()
      .sort((t1, t2) => this.compareThreads(t1, t2))
      .filter(t => this.shouldShowThread(t));
  }

  private isASelectedAuthor(account?: AccountInfo) {
    if (!account) return false;
    return this.selectedAuthors.some(
      author => account._account_id === author._account_id
    );
  }

  private shouldShowThread(thread: CommentThread) {
    assertIsDefined(thread.rootId, 'thread.rootId');
    // Never make a thread disappear while the user is editing it.
    if (isDraftThread(thread) && this.editingThreads.get(thread.rootId)) {
      return true;
    }

    if (this.selectedAuthors.length > 0) {
      const hasACommentFromASelectedAuthor = thread.comments.some(c =>
        this.isASelectedAuthor(c.author)
      );
      if (!hasACommentFromASelectedAuthor) return false;
    }

    // This is probably redundant, because getAllThreads() filters this out.
    if (this.onlyShowRobotCommentsWithHumanReply) {
      if (isRobotThread(thread) && !hasHumanReply(thread)) return false;
    }

    if (this.draftsOnly && !isDraftThread(thread)) return false;

    if (this.unresolvedOnly && !isUnresolved(thread)) return false;

    return true;
  }

  private handleOnlyUnresolved() {
    this.unresolvedOnly = true;
    this.draftsOnly = false;
  }

  private handleOnlyDrafts() {
    this.draftsOnly = true;
    this.unresolvedOnly = false;
  }

  private handleAllComments() {
    this.draftsOnly = false;
    this.unresolvedOnly = false;
  }

  private countUnresolved() {
    return this.getAllThreads().filter(isUnresolved).length;
  }

  private countAllThreads() {
    return this.getAllThreads().length;
  }

  private countDrafts() {
    return this.getAllThreads().filter(isDraftThread).length;
  }

  private async commentTabStateChanged() {
    const state = this.commentTabState;
    if (state === CommentTabState.UNRESOLVED) this.handleOnlyUnresolved();
    if (state === CommentTabState.DRAFTS) this.handleOnlyDrafts();
    if (state === CommentTabState.SHOW_ALL) this.handleAllComments();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-thread-list': GrThreadList;
  }
}
