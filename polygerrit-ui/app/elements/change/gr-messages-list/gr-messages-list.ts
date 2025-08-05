/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@material/web/switch/switch';
import '../../shared/gr-button/gr-button';
import '../gr-message/gr-message';
import '../../../styles/gr-paper-styles';
import {parseDate} from '../../../utils/date-util';
import {MessageTag} from '../../../constants/constants';
import {getAppContext} from '../../../services/app-context';
import {customElement, property, state} from 'lit/decorators.js';
import {
  ChangeMessageId,
  ChangeMessageInfo,
  CombinedMessage,
  CommentThread,
  isChangeMessageInfo,
  LabelNameToInfoMap,
  NumericChangeId,
  PatchSetNum,
  PatchSetNumber,
  VotingRangeInfo,
} from '../../../types/common';
import {GrMessage, MessageAnchorTapDetail} from '../gr-message/gr-message';
import {getVotingRange} from '../../../utils/label-util';
import {
  FormattedReviewerUpdateInfo,
  isPatchSetNumber,
  ParsedChangeInfo,
} from '../../../types/types';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {changeModelToken} from '../../../models/change/change-model';
import {resolve} from '../../../models/dependency';
import {query, queryAll} from '../../../utils/common-util';
import {css, html, LitElement, PropertyValues} from 'lit';
import {sharedStyles} from '../../../styles/shared-styles';
import {subscribe} from '../../lit/subscription-controller';
import {paperStyles} from '../../../styles/gr-paper-styles';
import {when} from 'lit/directives/when.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {
  Shortcut,
  ShortcutSection,
  shortcutsServiceToken,
} from '../../../services/shortcuts/shortcuts-service';
import {GrFormattedText} from '../../shared/gr-formatted-text/gr-formatted-text';
import {waitUntil} from '../../../utils/async-util';
import {MdSwitch} from '@material/web/switch/switch';

/**
 * The content of the enum is also used in the UI for the button text.
 */
enum ExpandAllState {
  EXPAND_ALL = 'Expand All',
  COLLAPSE_ALL = 'Collapse All',
}

interface TagsCountReportInfo {
  [tag: string]: number;
  all: number;
}

function getMessageId(x: CombinedMessage): ChangeMessageId | undefined {
  return isChangeMessageInfo(x) ? x.id : undefined;
}

/**
 * Computes message author's comments for this change message. The backend
 * sets comment.change_message_id for matching, so this computation is fairly
 * straightforward.
 */
function computeThreads(
  message: CombinedMessage,
  allThreadsForChange: CommentThread[]
): CommentThread[] {
  if (message._index === undefined) return [];
  const messageId = getMessageId(message);
  return allThreadsForChange.filter(thread =>
    thread.comments.some(comment => comment.change_message_id === messageId)
  );
}

/**
 * If messages have the same tag, then that influences grouping and whether
 * a message is initially hidden or not, see isImportant(). So we are applying
 * some "magic" rules here in order to hide exactly the right messages.
 *
 * 1. Use the same tag for some of Gerrit's standard events, if they should be
 * considered one group, e.g. normal and wip patchset uploads.
 *
 * 2. Everything beyond the ~ character is cut off from the tag. That gives
 * tools control over which messages will be hidden.
 *
 * 3. (Non-WIP) patchset uploads get a separate tag when they invalidate any
 * votes.
 */
function computeTag(message: CombinedMessage) {
  if (!message.tag) {
    return undefined;
  }

  if (message.tag === MessageTag.TAG_NEW_PATCHSET) {
    const hasOutdatedVotes =
      isChangeMessageInfo(message) &&
      message.message.indexOf('\nOutdated Votes:\n') !== -1;

    return hasOutdatedVotes
      ? MessageTag.TAG_NEW_PATCHSET_OUTDATED_VOTES
      : MessageTag.TAG_NEW_PATCHSET;
  }
  if (message.tag === MessageTag.TAG_NEW_WIP_PATCHSET) {
    return MessageTag.TAG_NEW_PATCHSET;
  }
  if (message.tag === MessageTag.TAG_UNSET_PRIVATE) {
    return MessageTag.TAG_SET_PRIVATE;
  }
  if (message.tag === MessageTag.TAG_SET_WIP) {
    return MessageTag.TAG_SET_READY;
  }

  return message.tag.replace(/~.*/, '');
}

/**
 * Try to set a revision number that makes sense, if none is set. Just copy
 * over the revision number of the next older message. This is mostly relevant
 * for reviewer updates. Other messages should typically have the revision
 * number already set.
 */
function computeRevision(
  message: CombinedMessage,
  allMessages: CombinedMessage[]
): PatchSetNum | undefined {
  if (isPatchSetNumber(message._revision_number)) {
    return message._revision_number;
  }
  let revision: PatchSetNumber | undefined = undefined;
  for (const m of allMessages) {
    if (m.date > message.date) break;
    if (isPatchSetNumber(m._revision_number)) {
      revision = m._revision_number;
    }
  }
  return revision;
}

/**
 * Merges change messages and reviewer updates into one array. Also processes
 * all messages and updates, aligns or massages some of the properties.
 */
function computeCombinedMessages(
  messages: ChangeMessageInfo[],
  reviewerUpdates: FormattedReviewerUpdateInfo[],
  commentThreads: CommentThread[]
): CombinedMessage[] {
  let mi = 0;
  let ri = 0;
  let combinedMessages: CombinedMessage[] = [];
  let mDate;
  let rDate;
  for (let i = 0; i < messages.length; i++) {
    // TODO(TS): clone message instead and avoid API object mutation
    (messages[i] as CombinedMessage)._index = i;
  }

  while (mi < messages.length || ri < reviewerUpdates.length) {
    if (mi >= messages.length) {
      combinedMessages = combinedMessages.concat(reviewerUpdates.slice(ri));
      break;
    }
    if (ri >= reviewerUpdates.length) {
      combinedMessages = combinedMessages.concat(messages.slice(mi));
      break;
    }
    mDate = mDate || parseDate(messages[mi].date);
    rDate = rDate || parseDate(reviewerUpdates[ri].date);
    if (rDate < mDate) {
      combinedMessages.push(reviewerUpdates[ri++]);
      rDate = null;
    } else {
      combinedMessages.push(messages[mi++]);
      mDate = null;
    }
  }

  for (let i = 0; i < combinedMessages.length; i++) {
    const message = combinedMessages[i];
    if (message.expanded === undefined) {
      message.expanded = false;
    }
    message.commentThreads = computeThreads(message, commentThreads);
    message._revision_number = computeRevision(message, combinedMessages);
    message.tag = computeTag(message);
  }
  // computeIsImportant() depends on tags and revision numbers already being
  // updated for all messages, so we have to compute this in its own forEach
  // loop.
  combinedMessages.forEach(m => {
    m.isImportant = computeIsImportant(m, combinedMessages);
  });
  return combinedMessages;
}

/**
 * Unimportant messages are initially hidden.
 *
 * Human messages are always important. They have an undefined tag.
 *
 * Autogenerated messages are unimportant, if there is a message with the same
 * tag and a higher revision number.
 */
function computeIsImportant(
  message: CombinedMessage,
  allMessages: CombinedMessage[]
) {
  if (!message.tag) return true;

  const hasSameTag = (m: CombinedMessage) => m.tag === message.tag;
  const revNumber = message._revision_number || 0;
  const hasHigherRevisionNumber = (m: CombinedMessage) =>
    (m._revision_number || 0) > revNumber;
  return !allMessages.filter(hasSameTag).some(hasHigherRevisionNumber);
}

export const TEST_ONLY = {
  computeTag,
  computeRevision,
  computeIsImportant,
};

@customElement('gr-messages-list')
export class GrMessagesList extends LitElement {
  // TODO: Evaluate if we still need to have display: flex on the :host and
  // .header.
  static override get styles() {
    return [
      sharedStyles,
      paperStyles,
      css`
        :host {
          display: flex;
          justify-content: space-between;
        }
        .header {
          align-items: center;
          border-bottom: 1px solid var(--border-color);
          display: flex;
          justify-content: space-between;
          padding: var(--spacing-s) var(--spacing-l);
        }
        .highlighted {
          animation: 3s fadeOut;
        }
        @keyframes fadeOut {
          0% {
            background-color: var(--emphasis-color);
          }
          100% {
            background-color: var(--view-background-color);
          }
        }
        .container {
          align-items: center;
          display: flex;
        }
        .hiddenEntries {
          color: var(--deemphasized-text-color);
        }
        gr-message:not(:last-of-type) {
          border-bottom: 1px solid var(--border-color);
        }
      `,
    ];
  }

  @property({type: Array})
  messages: ChangeMessageInfo[] = [];

  @property({type: Array})
  reviewerUpdates: FormattedReviewerUpdateInfo[] = [];

  @property({type: Object})
  labels?: LabelNameToInfoMap;

  @state()
  private change?: ParsedChangeInfo;

  @state()
  private changeNum?: NumericChangeId;

  @state()
  private commentThreads: CommentThread[] = [];

  @state()
  expandAllState = ExpandAllState.EXPAND_ALL;

  // Private but used in tests.
  @state()
  showAllActivity = false;

  @state()
  private combinedMessages: CombinedMessage[] = [];

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly changeModel = resolve(this, changeModelToken);

  private readonly reporting = getAppContext().reportingService;

  private readonly getShortcutsService = resolve(this, shortcutsServiceToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getCommentsModel().threadsSaved$,
      x => {
        this.commentThreads = x;
      }
    );
    subscribe(
      this,
      () => this.changeModel().change$,
      x => {
        this.change = x;
      }
    );
    subscribe(
      this,
      () => this.changeModel().changeNum$,
      x => {
        this.changeNum = x;
      }
    );
  }

  override willUpdate(changedProperties: PropertyValues): void {
    if (
      changedProperties.has('messages') ||
      changedProperties.has('reviewerUpdates') ||
      changedProperties.has('commentThreads')
    ) {
      this.combinedMessages = computeCombinedMessages(
        this.messages ?? [],
        this.reviewerUpdates ?? [],
        this.commentThreads ?? []
      );
      this.combinedMessagesChanged();
    }
  }

  override render() {
    const labelExtremes = this.computeLabelExtremes();
    return html`${this.renderHeader()}
    ${this.combinedMessages
      .filter(m => this.showAllActivity || m.isImportant)
      .map(
        message => html`<gr-message
          .change=${this.change}
          .changeNum=${this.changeNum}
          .message=${message}
          .commentThreads=${message.commentThreads ?? []}
          @message-anchor-tap=${this.handleAnchorClick}
          .labelExtremes=${labelExtremes}
          data-message-id=${ifDefined(getMessageId(message) as string)}
        ></gr-message>`
      )}`;
  }

  private renderHeader() {
    return html`<div class="header">
      <div id="showAllActivityToggleContainer" class="container">
        ${when(
          this.combinedMessages.some(m => !m.isImportant),
          () => html`
            <md-switch
              class="showAllActivityToggle"
              ?selected=${this.showAllActivity}
              @change=${this.handleShowAllActivityChanged}
              aria-labelledby="showAllEntriesLabel"
              role="switch"
            ></md-switch>
            <div id="showAllEntriesLabel" aria-hidden="true">
              <span>Show all entries</span>
              <span class="hiddenEntries" ?hidden=${this.showAllActivity}>
                (${this.combinedMessages.filter(m => !m.isImportant).length}
                hidden)
              </span>
            </div>
            <span class="transparent separator"></span>
          `
        )}
      </div>
      <gr-button
        id="collapse-messages"
        link
        .title=${this.computeExpandAllTitle()}
        @click=${this.handleExpandCollapseTap}
      >
        ${this.expandAllState}
      </gr-button>
    </div>`;
  }

  async scrollToMessage(messageID: string) {
    await waitUntil(() => this.messages && this.messages.length > 0);
    await this.updateComplete;

    const selector = `[data-message-id="${messageID}"]`;
    const el = this.shadowRoot!.querySelector(selector) as
      | GrMessage
      | undefined;

    if (!el && this.showAllActivity) {
      this.reporting.error(
        'GrMessagesList scroll',
        new Error(`Failed to scroll to message: ${messageID}`)
      );
      return;
    }
    if (!el || !el.message) {
      this.showAllActivity = true;
      setTimeout(() => this.scrollToMessage(messageID));
      return;
    }

    el.message.expanded = true;
    // Must wait for message to expand and render before we can scroll to it
    el.requestUpdate();
    await el.updateComplete;
    await query<GrFormattedText>(el, 'gr-formatted-text.message')
      ?.updateComplete;
    el.scrollIntoView();
    this.highlightEl(el);
  }

  private handleShowAllActivityChanged(e: Event) {
    this.showAllActivity = (e.target as MdSwitch).selected ?? false;
  }

  private refreshMessages() {
    for (const message of queryAll<GrMessage>(this, 'gr-message')) {
      message.requestUpdate();
    }
  }

  private computeExpandAllTitle() {
    if (this.expandAllState === ExpandAllState.COLLAPSE_ALL) {
      return this.getShortcutsService().createTitle(
        Shortcut.COLLAPSE_ALL_MESSAGES,
        ShortcutSection.ACTIONS
      );
    }
    if (this.expandAllState === ExpandAllState.EXPAND_ALL) {
      return this.getShortcutsService().createTitle(
        Shortcut.EXPAND_ALL_MESSAGES,
        ShortcutSection.ACTIONS
      );
    }
    return '';
  }

  // Private but used in tests.
  highlightEl(el: HTMLElement) {
    const highlightedEls =
      this.shadowRoot?.querySelectorAll('.highlighted') ?? [];
    for (const highlightedEl of highlightedEls) {
      highlightedEl.classList.remove('highlighted');
    }
    function handleAnimationEnd() {
      el.removeEventListener('animationend', handleAnimationEnd);
      el.classList.remove('highlighted');
    }
    el.addEventListener('animationend', handleAnimationEnd);
    el.classList.add('highlighted');
  }

  // Private but used in tests.
  handleExpandCollapse(expand: boolean) {
    this.expandAllState = expand
      ? ExpandAllState.COLLAPSE_ALL
      : ExpandAllState.EXPAND_ALL;
    if (!this.combinedMessages) return;
    for (let i = 0; i < this.combinedMessages.length; i++) {
      this.combinedMessages[i].expanded = expand;
    }
    this.refreshMessages();
  }

  private handleExpandCollapseTap(e: Event) {
    e.preventDefault();
    this.handleExpandCollapse(
      this.expandAllState === ExpandAllState.EXPAND_ALL
    );
  }

  private handleAnchorClick(e: CustomEvent<MessageAnchorTapDetail>) {
    this.scrollToMessage(e.detail.id);
  }

  /**
   * Called when this.combinedMessages has changed.
   */
  private combinedMessagesChanged() {
    if (this.combinedMessages.length === 0) return;
    this.refreshMessages();
    const tags = this.combinedMessages.map(
      message =>
        message.tag || (message as FormattedReviewerUpdateInfo).type || 'none'
    );
    const tagsCounted = tags.reduce(
      (acc, val) => {
        acc[val] = (acc[val] || 0) + 1;
        return acc;
      },
      {all: this.combinedMessages.length} as TagsCountReportInfo
    );
    this.reporting.reportInteraction('messages-count', tagsCounted);
  }

  /**
   * Compute a mapping from label name to objects representing the minimum and
   * maximum possible values for that label.
   * Private but used in tests.
   */
  computeLabelExtremes() {
    const extremes: {[labelName: string]: VotingRangeInfo} = {};
    if (!this.labels) {
      return extremes;
    }
    for (const key of Object.keys(this.labels)) {
      const range = getVotingRange(this.labels[key]);
      if (range) {
        extremes[key] = range;
      }
    }
    return extremes;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-messages-list': GrMessagesList;
  }
}
