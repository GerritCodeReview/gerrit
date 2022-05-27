/**
 * @license
 * Copyright 2018 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
import {ChangeMessageId} from '../../../api/rest-api';
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
import {CommentTabState, TabState} from '../../../types/events';
import {DropdownItem} from '../../shared/gr-dropdown-list/gr-dropdown-list';
import {GrAccountChip} from '../../shared/gr-account-chip/gr-account-chip';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, queryAll, state} from 'lit/decorators';
import {sharedStyles} from '../../../styles/shared-styles';
import {subscribe} from '../../lit/subscription-controller';
import {ParsedChangeInfo} from '../../../types/types';
import {repeat} from 'lit/directives/repeat';
import {GrCommentThread} from '../../shared/gr-comment-thread/gr-comment-thread';
import {getAppContext} from '../../../services/app-context';
import {resolve} from '../../../models/dependency';
import {changeModelToken} from '../../../models/change/change-model';

enum SortDropdownState {
  TIMESTAMP = 'Latest timestamp',
  FILES = 'Files',
}

export const __testOnly_SortDropdownState = SortDropdownState;

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
export function compareThreads(
  c1: CommentThread,
  c2: CommentThread,
  byTimestamp = false
) {
  if (byTimestamp) {
    const c1Time = lastUpdated(c1)?.getTime() ?? 0;
    const c2Time = lastUpdated(c2)?.getTime() ?? 0;
    const timeDiff = c2Time - c1Time;
    if (timeDiff !== 0) return c2Time - c1Time;
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

  // Convert 'FILE' and 'LOST' to undefined.
  const line1 = typeof c1.line === 'number' ? c1.line : undefined;
  const line2 = typeof c2.line === 'number' ? c2.line : undefined;
  if (line1 !== line2) {
    // one of them is a FILE/LOST comment, show first
    if (line1 === undefined) return -1;
    if (line2 === undefined) return 1;
    // Lower line numbers first.
    return line1 < line2 ? -1 : 1;
  }

  if (c1.patchNum !== c2.patchNum) {
    // `patchNum` should be required, but show undefined first.
    if (c1.patchNum === undefined) return -1;
    if (c2.patchNum === undefined) return 1;
    // Higher patchset numbers first.
    return c1.patchNum > c2.patchNum ? -1 : 1;
  }

  // Sorting should not be based on the thread being unresolved or being a draft
  // thread, because that would be a surprising re-sort when the thread changes
  // state.

  const c1Time = lastUpdated(c1)?.getTime() ?? 0;
  const c2Time = lastUpdated(c2)?.getTime() ?? 0;
  if (c2Time !== c1Time) {
    // Newer comments first.
    return c2Time - c1Time;
  }

  return 0;
}

@customElement('gr-thread-list')
export class GrThreadList extends LitElement {
  @queryAll('gr-comment-thread')
  threadElements?: NodeList;

  /**
   * Raw list of threads for the component to show.
   *
   * ATTENTION! this.threads should never be used directly within the component.
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

  /** Along with `draftsOnly` is the currently selected filter. */
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
  commentTabState?: TabState;

  @property({type: String, attribute: 'scroll-comment-id'})
  scrollCommentId?: UrlEncodedCommentId;

  /**
   * Optional context information when threads are being displayed for a
   * specific change message. That influences which comments are expanded or
   * collapsed by default.
   */
  @property({type: String, attribute: 'message-id'})
  messageId?: ChangeMessageId;

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

  /** Along with `unresolvedOnly` is the currently selected filter. */
  @state()
  draftsOnly = false;

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly userModel = getAppContext().userModel;

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChangeModel().changeNum$,
      x => (this.changeNum = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().change$,
      x => (this.change = x)
    );
    subscribe(
      this,
      () => this.userModel.account$,
      x => (this.account = x)
    );
  }

  override willUpdate(changed: PropertyValues) {
    if (changed.has('commentTabState')) this.onCommentTabStateUpdate();
    if (changed.has('scrollCommentId')) this.onScrollCommentIdUpdate();
  }

  private onCommentTabStateUpdate() {
    switch (this.commentTabState?.commentTab) {
      case CommentTabState.UNRESOLVED:
        this.handleOnlyUnresolved();
        break;
      case CommentTabState.DRAFTS:
        this.handleOnlyDrafts();
        break;
      case CommentTabState.SHOW_ALL:
        this.handleAllComments();
        break;
    }
  }

  /**
   * When user wants to scroll to a comment, render all comments so that the
   * appropriate comment can be scrolled into view.
   */
  private onScrollCommentIdUpdate() {
    if (this.scrollCommentId) this.handleAllComments();
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
          .value=${this.sortDropdownValue}
          @value-change=${(e: CustomEvent) =>
            (this.sortDropdownValue = e.detail.value)}
          .items=${this.getSortDropdownEntries()}
        >
        </gr-dropdown-list>
        <span class="separator"></span>
        <span class="filter-text">Filter By:</span>
        <gr-dropdown-list
          id="filterDropdown"
          .value=${this.getCommentsDropdownValue()}
          @value-change=${this.handleCommentsDropdownValueChange}
          .items=${this.getCommentsDropdownEntries()}
        >
        </gr-dropdown-list>
        ${this.renderAuthorChips()}
      </div>
    `;
  }

  private renderEmptyThreadsMessage() {
    const threads = this.getAllThreads();
    const threadsEmpty = threads.length === 0;
    const displayedEmpty = this.getDisplayedThreads().length === 0;
    if (!displayedEmpty) return;
    const showPopper = this.unresolvedOnly && !threadsEmpty;
    const popper = html`<span class="partypopper">&#x1F389;</span>`;
    const showButton = this.unresolvedOnly && !threadsEmpty;
    const button = html`
      <gr-button
        class="show-resolved-comments"
        link
        @click=${this.handleAllComments}
        >Show ${pluralize(threads.length, 'resolved comment')}</gr-button
      >
    `;
    return html`
      <div>
        <span>
          ${showPopper ? popper : undefined}
          ${threadsEmpty ? 'No comments' : 'No unresolved comments'}
          ${showButton ? button : undefined}
        </span>
      </div>
    `;
  }

  private renderCommentThreads() {
    const threads = this.getDisplayedThreads();
    return repeat(
      threads,
      thread => thread.rootId,
      (thread, index) => {
        const isFirst =
          index === 0 || threads[index - 1].path !== threads[index].path;
        const separator =
          index !== 0 && isFirst
            ? html`<div class="thread-separator"></div>`
            : undefined;
        const commentThread = this.renderCommentThread(thread, isFirst);
        return html`${separator}${commentThread}`;
      }
    );
  }

  private renderCommentThread(thread: CommentThread, isFirst: boolean) {
    return html`
      <gr-comment-thread
        .thread=${thread}
        show-file-path
        ?show-ported-comment=${thread.ported}
        ?show-comment-context=${this.showCommentContext}
        ?show-file-name=${isFirst}
        .messageId=${this.messageId}
        ?should-scroll-into-view=${thread.rootId === this.scrollCommentId}
        @comment-thread-editing-changed=${() => {
          this.requestUpdate();
        }}
      ></gr-comment-thread>
    `;
  }

  private renderAuthorChips() {
    const authors = getCommentAuthors(this.getDisplayedThreads(), this.account);
    if (authors.length === 0) return;
    return html`<span class="author-text">From:</span>${authors.map(author =>
        this.renderAccountChip(author)
      )}`;
  }

  private renderAccountChip(account: AccountInfo) {
    const selected = this.selectedAuthors.some(
      a => a._account_id === account._account_id
    );
    return html`
      <gr-account-label
        .account=${account}
        @click=${this.handleAccountClicked}
        selectionChipStyle
        noStatusIcons
        ?selected=${selected}
      ></gr-account-label>
    `;
  }

  private getCommentsDropdownValue() {
    if (this.draftsOnly) return CommentTabState.DRAFTS;
    if (this.unresolvedOnly) return CommentTabState.UNRESOLVED;
    return CommentTabState.SHOW_ALL;
  }

  private getSortDropdownEntries() {
    return [
      {text: SortDropdownState.FILES, value: SortDropdownState.FILES},
      {text: SortDropdownState.TIMESTAMP, value: SortDropdownState.TIMESTAMP},
    ];
  }

  // private, but visible for testing
  getCommentsDropdownEntries() {
    const items: DropdownItem[] = [];
    const threads = this.getAllThreads();
    items.push({
      text: `Unresolved (${threads.filter(isUnresolved).length})`,
      value: CommentTabState.UNRESOLVED,
    });
    if (this.account) {
      items.push({
        text: `Drafts (${threads.filter(isDraftThread).length})`,
        value: CommentTabState.DRAFTS,
      });
    }
    items.push({
      text: `All (${threads.length})`,
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

  // private, but visible for testing
  handleCommentsDropdownValueChange(e: CustomEvent) {
    const value = e.detail.value;
    switch (value) {
      case CommentTabState.UNRESOLVED:
        this.handleOnlyUnresolved();
        break;
      case CommentTabState.DRAFTS:
        this.handleOnlyDrafts();
        break;
      default:
        this.handleAllComments();
    }
  }

  /**
   * Returns all threads that the list may show.
   */
  // private, but visible for testing
  getAllThreads() {
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
  // private, but visible for testing
  getDisplayedThreads() {
    const byTimestamp =
      this.sortDropdownValue === SortDropdownState.TIMESTAMP &&
      !this.hideDropdown;
    return this.getAllThreads()
      .sort((t1, t2) => compareThreads(t1, t2, byTimestamp))
      .filter(t => this.shouldShowThread(t));
  }

  private isASelectedAuthor(account?: AccountInfo) {
    if (!account) return false;
    return this.selectedAuthors.some(
      author => account._account_id === author._account_id
    );
  }

  private shouldShowThread(thread: CommentThread) {
    // Never make a thread disappear while the user is editing it.
    assertIsDefined(thread.rootId, 'thread.rootId');
    const el = this.queryThreadElement(thread.rootId);
    if (el?.editing) return true;

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

  private queryThreadElement(rootId: string): GrCommentThread | undefined {
    const els = [...(this.threadElements ?? [])] as GrCommentThread[];
    return els.find(el => el.rootId === rootId);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-thread-list': GrThreadList;
  }
}
