/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the 'License');
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an 'AS IS' BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
import './gr-checks-chip';
import './gr-summary_chip';
import '../../shared/gr-avatar/gr-avatar';
import '../../checks/gr-checks-action';
import {LitElement, css, html} from 'lit';
import {customElement, state} from 'lit/decorators';
import {subscribe} from '../../lit/subscription-controller';
import {sharedStyles} from '../../../styles/shared-styles';
import {getAppContext} from '../../../services/app-context';
import {
  CheckResult,
  CheckRun,
  ErrorMessages,
} from '../../../models/checks/checks-model';
import {Action, Category, RunStatus} from '../../../api/checks';
import {fireShowPrimaryTab} from '../../../utils/event-util';
import {
  compareByWorstCategory,
  getResultsOf,
  hasCompletedWithoutResults,
  hasResults,
  hasResultsOf,
  isRunningOrScheduled,
  isRunningScheduledOrCompleted,
} from '../../../models/checks/checks-util';
import {ChangeComments} from '../../diff/gr-comment-api/gr-comment-api';
import {
  CommentThread,
  getFirstComment,
  hasHumanReply,
  isResolved,
  isRobotThread,
  isUnresolved,
} from '../../../utils/comment-util';
import {pluralize} from '../../../utils/string-util';
import {AccountInfo} from '../../../types/common';
import {notUndefined} from '../../../types/types';
import {uniqueDefinedAvatar} from '../../../utils/account-util';
import {PrimaryTab} from '../../../constants/constants';
import {ChecksTabState, CommentTabState} from '../../../types/events';
import {spinnerStyles} from '../../../styles/gr-spinner-styles';
import {modifierPressed} from '../../../utils/dom-util';
import {DropdownLink} from '../../shared/gr-dropdown/gr-dropdown';
import {commentsModelToken} from '../../../models/comments/comments-model';
import {resolve} from '../../../models/dependency';
import {checksModelToken} from '../../../models/checks/checks-model';
import {changeModelToken} from '../../../models/change/change-model';
import {Interaction} from '../../../constants/reporting';
import {roleDetails} from '../../../utils/change-util';

import {SummaryChipStyles} from './gr-summary_chip';

function handleSpaceOrEnter(e: KeyboardEvent, handler: () => void) {
  if (modifierPressed(e)) return;
  // Only react to `return` and `space`.
  if (e.keyCode !== 13 && e.keyCode !== 32) return;
  e.preventDefault();
  e.stopPropagation();
  handler();
}

/** What is the maximum number of detailed checks chips? */
const DETAILS_QUOTA: Map<RunStatus | Category, number> = new Map();
DETAILS_QUOTA.set(Category.ERROR, 7);
DETAILS_QUOTA.set(Category.WARNING, 2);
DETAILS_QUOTA.set(RunStatus.RUNNING, 2);

@customElement('gr-change-summary')
export class GrChangeSummary extends LitElement {
  @state()
  changeComments?: ChangeComments;

  @state()
  commentThreads?: CommentThread[];

  @state()
  selfAccount?: AccountInfo;

  @state()
  runs: CheckRun[] = [];

  @state()
  showChecksSummary = false;

  @state()
  someProvidersAreLoading = false;

  @state()
  errorMessages: ErrorMessages = {};

  @state()
  loginCallback?: () => void;

  @state()
  actions: Action[] = [];

  @state()
  messages: string[] = [];

  private readonly showAllChips = new Map<RunStatus | Category, boolean>();

  private readonly getCommentsModel = resolve(this, commentsModelToken);

  private readonly userModel = getAppContext().userModel;

  private readonly getChecksModel = resolve(this, checksModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly reporting = getAppContext().reportingService;

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChecksModel().allRunsLatestPatchsetLatestAttempt$,
      x => (this.runs = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().aPluginHasRegistered$,
      x => (this.showChecksSummary = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().someProvidersAreLoadingFirstTime$,
      x => (this.someProvidersAreLoading = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().errorMessagesLatest$,
      x => (this.errorMessages = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().loginCallbackLatest$,
      x => (this.loginCallback = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().topLevelActionsLatest$,
      x => (this.actions = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().topLevelMessagesLatest$,
      x => (this.messages = x)
    );
    subscribe(
      this,
      () => this.getCommentsModel().changeComments$,
      x => (this.changeComments = x)
    );
    subscribe(
      this,
      () => this.getCommentsModel().threads$,
      x => (this.commentThreads = x)
    );
    subscribe(
      this,
      () => this.userModel.account$,
      x => (this.selfAccount = x)
    );
  }

  static override get styles() {
    return [
      sharedStyles,
      spinnerStyles,
      css`
        :host {
          display: block;
          color: var(--deemphasized-text-color);
          max-width: 625px;
          margin-bottom: var(--spacing-m);
        }
        .zeroState {
          color: var(--deemphasized-text-color);
        }
        .loading.zeroState {
          margin-right: var(--spacing-m);
        }
        div.error,
        .login {
          display: flex;
          color: var(--primary-text-color);
          padding: 0 var(--spacing-s);
          margin: var(--spacing-xs) 0;
          width: 490px;
        }
        div.error {
          background-color: var(--error-background);
        }
        div.error iron-icon {
          color: var(--error-foreground);
          width: 16px;
          height: 16px;
          position: relative;
          top: 4px;
          margin-right: var(--spacing-s);
        }
        div.error .right {
          overflow: hidden;
        }
        div.error .right .message {
          overflow: hidden;
          text-overflow: ellipsis;
          white-space: nowrap;
        }
        .login {
          justify-content: space-between;
          background: var(--info-background);
        }
        .login iron-icon {
          color: var(--info-foreground);
        }
        .login gr-button {
          margin: -4px var(--spacing-s);
        }
        td.key {
          padding-right: var(--spacing-l);
          padding-bottom: var(--spacing-s);
          line-height: calc(var(--line-height-normal) + var(--spacing-s));
        }
        td.value {
          padding-right: var(--spacing-l);
          padding-bottom: var(--spacing-s);
          line-height: calc(var(--line-height-normal) + var(--spacing-s));
        }
        iron-icon.launch {
          color: var(--gray-foreground);
          width: var(--line-height-small);
          height: var(--line-height-small);
          vertical-align: top;
        }
        gr-avatar {
          height: var(--line-height-small, 16px);
          width: var(--line-height-small, 16px);
          vertical-align: top;
          margin-right: var(--spacing-xs);
        }
        /* The basics of .loadingSpin are defined in shared styles. */
        .loadingSpin {
          width: calc(var(--line-height-normal) - 2px);
          height: calc(var(--line-height-normal) - 2px);
          display: inline-block;
          vertical-align: top;
          position: relative;
          /* Making up for the 2px reduced height above. */
          top: 1px;
        }
        .actions {
          margin-left: calc(0px - var(--spacing-m));
          line-height: var(--line-height-normal);
        }
        .actions gr-checks-action,
        .actions gr-dropdown {
          vertical-align: top;
          --gr-button-padding: 0 var(--spacing-m);
        }
        .actions #moreMessage {
          display: none;
        }
        .summaryMessage {
          line-height: var(--line-height-normal);
          color: var(--primary-text-color);
        }
      `,
    ];
  }

  private renderSummaryMessage() {
    return this.messages.map(m => html`<div class="summaryMessage">${m}</div>`);
  }

  private renderActions() {
    const actions = this.actions ?? [];
    const summaryActions = actions.filter(a => a.summary).slice(0, 2);
    if (summaryActions.length === 0) return;
    const topActions = summaryActions.slice(0, 2);
    const overflowActions = summaryActions.slice(2).map(action => {
      return {...action, id: action.name};
    });
    const disabledActionIds = overflowActions
      .filter(action => action.disabled)
      .map(action => action.id);

    return html`
      <div class="actions">
        ${topActions.map(this.renderAction)}
        ${this.renderOverflow(overflowActions, disabledActionIds)}
      </div>
    `;
  }

  private renderAction(action?: Action) {
    if (!action) return;
    return html`<gr-checks-action
      context="summary"
      .action=${action}
    ></gr-checks-action>`;
  }

  private handleAction(e: CustomEvent<Action>) {
    this.getChecksModel().triggerAction(
      e.detail,
      undefined,
      'summary-dropdown'
    );
  }

  private renderOverflow(items: DropdownLink[], disabledIds: string[] = []) {
    if (items.length === 0) return;
    return html`
      <gr-dropdown
        id="moreActions"
        link=""
        vertical-offset="32"
        horizontal-align="right"
        @tap-item=${this.handleAction}
        .items=${items}
        .disabledIds=${disabledIds}
      >
        <iron-icon icon="gr-icons:more-vert" aria-labelledby="moreMessage">
        </iron-icon>
        <span id="moreMessage">More</span>
      </gr-dropdown>
    `;
  }

  renderErrorMessages() {
    return Object.entries(this.errorMessages).map(
      ([plugin, message]) =>
        html`
          <div class="error zeroState">
            <div class="left">
              <iron-icon icon="gr-icons:error"></iron-icon>
            </div>
            <div class="right">
              <div class="message" title=${message}>
                Error while fetching results for ${plugin}: ${message}
              </div>
            </div>
          </div>
        `
    );
  }

  renderChecksLogin() {
    if (!this.loginCallback) return;
    return html`
      <div class="login">
        <div class="left">
          <iron-icon
            class="info-outline"
            icon="gr-icons:info-outline"
          ></iron-icon>
          Not logged in
        </div>
        <div class="right">
          <gr-button @click=${this.loginCallback} link>Sign in</gr-button>
        </div>
      </div>
    `;
  }

  renderChecksZeroState() {
    if (Object.keys(this.errorMessages).length > 0) return;
    if (this.loginCallback) return;
    if (this.runs.some(isRunningScheduledOrCompleted)) return;
    const msg = this.someProvidersAreLoading ? 'Loading results' : 'No results';
    return html`<span role="status" class="loading zeroState">${msg}</span>`;
  }

  renderChecksChipForCategory(category: Category) {
    const runs = this.runs.filter(run => {
      if (hasResultsOf(run, category)) return true;
      return category === Category.SUCCESS && hasCompletedWithoutResults(run);
    });
    const count = (run: CheckRun) => getResultsOf(run, category);
    if (category === Category.SUCCESS || category === Category.INFO) {
      return this.renderChecksChipsCollapsed(runs, category, count);
    }
    return this.renderChecksChipsExpanded(runs, category);
  }

  renderChecksChipRunning() {
    const runs = this.runs
      .filter(isRunningOrScheduled)
      .sort(compareByWorstCategory);
    return this.renderChecksChipsExpanded(runs, RunStatus.RUNNING);
  }

  renderChecksChipsExpanded(
    runs: CheckRun[],
    statusOrCategory: RunStatus | Category
  ) {
    if (runs.length === 0) return;
    const showAll = this.showAllChips.get(statusOrCategory) ?? false;
    let count = showAll ? 999 : DETAILS_QUOTA.get(statusOrCategory) ?? 2;
    if (count === runs.length - 1) count = runs.length;
    const more = runs.length - count;
    return html`${runs
      .slice(0, count)
      .map(run =>
        this.renderChecksChipDetailed(run, statusOrCategory)
      )}${this.renderChecksChipPlusMore(statusOrCategory, more)}`;
  }

  private renderChecksChipsCollapsed(
    runs: CheckRun[],
    statusOrCategory: RunStatus | Category,
    resultFilter: (run: CheckRun) => CheckResult[]
  ) {
    const count = runs.reduce(
      (sum, run) => sum + (resultFilter(run).length || 1),
      0
    );
    if (count === 0) return;
    const handler = () => this.onChipClick({statusOrCategory});
    return html`<gr-checks-chip
      .statusOrCategory=${statusOrCategory}
      .text=${`${count}`}
      @click=${handler}
      @keydown=${(e: KeyboardEvent) => handleSpaceOrEnter(e, handler)}
    ></gr-checks-chip>`;
  }

  private renderChecksChipPlusMore(
    statusOrCategory: RunStatus | Category,
    count: number
  ) {
    if (count <= 0) return;
    if (this.showAllChips.get(statusOrCategory) === true) return;
    const handler = () => {
      this.showAllChips.set(statusOrCategory, true);
      this.requestUpdate();
    };
    return html`<gr-checks-chip
      .statusOrCategory=${statusOrCategory}
      .text="+ ${count} more"
      @click=${handler}
      @keydown=${(e: KeyboardEvent) => handleSpaceOrEnter(e, handler)}
    ></gr-checks-chip>`;
  }

  private renderChecksChipDetailed(
    run: CheckRun,
    statusOrCategory: RunStatus | Category
  ) {
    const links = [];
    if (run.statusLink) links.push(run.statusLink);
    const text = `${run.checkName}`;
    const tabState: ChecksTabState = {
      checkName: run.checkName,
      statusOrCategory,
    };
    // Scheduled runs are rendered in the RUNNING section, but the icon of the
    // chip must be the one for SCHEDULED.
    if (
      statusOrCategory === RunStatus.RUNNING &&
      run.status === RunStatus.SCHEDULED
    ) {
      statusOrCategory = RunStatus.SCHEDULED;
    }
    const handler = () => this.onChipClick(tabState);
    return html`<gr-checks-chip
      .statusOrCategory=${statusOrCategory}
      .text=${text}
      .links=${links}
      @click=${handler}
      @keydown=${(e: KeyboardEvent) => handleSpaceOrEnter(e, handler)}
    ></gr-checks-chip>`;
  }

  private onChipClick(state: ChecksTabState) {
    this.reporting.reportInteraction(Interaction.CHECKS_CHIP_CLICKED, {
      statusOrCategory: state.statusOrCategory,
      checkName: state.checkName,
      ...roleDetails(this.getChangeModel().getChange(), this.selfAccount),
    });
    fireShowPrimaryTab(this, PrimaryTab.CHECKS, false, {
      checksTab: state,
    });
  }

  override render() {
    const commentThreads =
      this.commentThreads?.filter(t => !isRobotThread(t) || hasHumanReply(t)) ??
      [];
    const countResolvedComments = commentThreads.filter(isResolved).length;
    const unresolvedThreads = commentThreads.filter(isUnresolved);
    const countUnresolvedComments = unresolvedThreads.length;
    const unresolvedAuthors = this.getAccounts(unresolvedThreads);
    const draftCount = this.changeComments?.computeDraftCount() ?? 0;
    const hasNonRunningChip = this.runs.some(
      run => hasCompletedWithoutResults(run) || hasResults(run)
    );
    const hasRunningChip = this.runs.some(isRunningOrScheduled);
    return html`
      <div>
        <table>
          <tr>
            <td class="key">Comments</td>
            <td class="value">
              <span
                class="zeroState"
                ?hidden=${!!countResolvedComments ||
                !!draftCount ||
                !!countUnresolvedComments}
              >
                No comments</span
              ><gr-summary-chip
                styleType=${SummaryChipStyles.WARNING}
                category=${CommentTabState.DRAFTS}
                icon="edit"
                ?hidden=${!draftCount}
              >
                ${pluralize(draftCount, 'draft')}</gr-summary-chip
              ><gr-summary-chip
                styleType=${SummaryChipStyles.WARNING}
                category=${CommentTabState.UNRESOLVED}
                ?hidden=${!countUnresolvedComments}
              >
                ${unresolvedAuthors.map(
                  account =>
                    html`<gr-avatar
                      .account=${account}
                      imageSize="32"
                    ></gr-avatar>`
                )}
                ${countUnresolvedComments} unresolved</gr-summary-chip
              ><gr-summary-chip
                styleType=${SummaryChipStyles.CHECK}
                category=${CommentTabState.SHOW_ALL}
                icon="markChatRead"
                ?hidden=${!countResolvedComments}
                >${countResolvedComments} resolved</gr-summary-chip
              >
            </td>
          </tr>
          <tr ?hidden=${!this.showChecksSummary}>
            <td class="key">Checks</td>
            <td class="value">
              <div class="checksSummary">
                ${this.renderChecksZeroState()}${this.renderChecksChipForCategory(
                  Category.ERROR
                )}${this.renderChecksChipForCategory(
                  Category.WARNING
                )}${this.renderChecksChipForCategory(
                  Category.INFO
                )}${this.renderChecksChipForCategory(
                  Category.SUCCESS
                )}${hasNonRunningChip && hasRunningChip
                  ? html`<br />`
                  : ''}${this.renderChecksChipRunning()}
                <span
                  class="loadingSpin"
                  ?hidden=${!this.someProvidersAreLoading}
                ></span>
                ${this.renderErrorMessages()} ${this.renderChecksLogin()}
                ${this.renderSummaryMessage()} ${this.renderActions()}
              </div>
            </td>
          </tr>
          <tr hidden>
            <td class="key">Findings</td>
            <td class="value"></td>
          </tr>
        </table>
      </div>
    `;
  }

  getAccounts(commentThreads: CommentThread[]): AccountInfo[] {
    const uniqueAuthors = commentThreads
      .map(getFirstComment)
      .map(comment => comment?.author ?? this.selfAccount)
      .filter(notUndefined)
      .filter(account => !!account?.avatars?.[0]?.url)
      .filter(uniqueDefinedAvatar);
    return uniqueAuthors.slice(0, 3);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-summary': GrChangeSummary;
  }
}
