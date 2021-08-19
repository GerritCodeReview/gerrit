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
import {html} from 'lit-html';
import {css, customElement, property} from 'lit-element';
import {GrLitElement} from '../../lit/gr-lit-element';
import {sharedStyles} from '../../../styles/shared-styles';
import {appContext} from '../../../services/app-context';
import {
  allRunsLatestPatchsetLatestAttempt$,
  aPluginHasRegistered$,
  CheckResult,
  CheckRun,
  errorMessageLatest$,
  loginCallbackLatest$,
  someProvidersAreLoadingLatest$,
} from '../../../services/checks/checks-model';
import {Category, RunStatus} from '../../../api/checks';
import {fireShowPrimaryTab} from '../../../utils/event-util';
import '../../shared/gr-avatar/gr-avatar';
import {
  firstPrimaryLink,
  getResultsOf,
  hasCompletedWithoutResults,
  hasResults,
  hasResultsOf,
  iconFor,
  isRunning,
  isRunningOrHasCompleted,
  isStatus,
  labelFor,
} from '../../../services/checks/checks-util';
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

export enum SummaryChipStyles {
  INFO = 'info',
  WARNING = 'warning',
  CHECK = 'check',
  UNDEFINED = '',
}

function handleSpaceOrEnter(e: KeyboardEvent, handler: () => void) {
  if (modifierPressed(e)) return;
  // Only react to `return` and `space`.
  if (e.keyCode !== 13 && e.keyCode !== 32) return;
  e.preventDefault();
  e.stopPropagation();
  handler();
}

@customElement('gr-summary-chip')
export class GrSummaryChip extends GrLitElement {
  @property()
  icon = '';

  @property()
  styleType = SummaryChipStyles.UNDEFINED;

  @property()
  category?: CommentTabState;

  private readonly reporting = appContext.reportingService;

  static override get styles() {
    return [
      sharedStyles,
      css`
        .summaryChip {
          color: var(--chip-color);
          cursor: pointer;
          display: inline-block;
          padding: var(--spacing-xxs) var(--spacing-m) var(--spacing-xxs)
            var(--spacing-s);
          margin-right: var(--spacing-s);
          border-radius: 12px;
          border: 1px solid gray;
          vertical-align: top;
        }
        iron-icon {
          width: var(--line-height-small);
          height: var(--line-height-small);
          vertical-align: top;
        }
        .summaryChip.warning {
          border-color: var(--warning-foreground);
          background: var(--warning-background);
        }
        .summaryChip.warning:hover {
          background: var(--warning-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .summaryChip.warning:focus-within {
          background: var(--warning-background-focus);
        }
        .summaryChip.warning iron-icon {
          color: var(--warning-foreground);
        }
        .summaryChip.check {
          border-color: var(--gray-foreground);
          background: var(--gray-background);
        }
        .summaryChip.check:hover {
          background: var(--gray-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .summaryChip.check:focus-within {
          background: var(--gray-background-focus);
        }
        .summaryChip.check iron-icon {
          color: var(--gray-foreground);
        }
      `,
    ];
  }

  override render() {
    const chipClass = `summaryChip font-small ${this.styleType}`;
    const grIcon = this.icon ? `gr-icons:${this.icon}` : '';
    return html`<button class="${chipClass}" @click="${this.handleClick}">
      ${this.icon && html`<iron-icon icon="${grIcon}"></iron-icon>`}
      <slot></slot>
    </button>`;
  }

  private handleClick(e: MouseEvent) {
    e.stopPropagation();
    e.preventDefault();
    this.reporting.reportInteraction('comment chip click', {
      category: this.category,
    });
    fireShowPrimaryTab(this, PrimaryTab.COMMENT_THREADS, true, {
      commentTab: this.category,
    });
  }
}

@customElement('gr-checks-chip')
export class GrChecksChip extends GrLitElement {
  @property()
  statusOrCategory?: Category | RunStatus;

  @property()
  text = '';

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: inline-block;
        }
        .checksChip {
          color: var(--chip-color);
          cursor: pointer;
          display: inline-block;
          margin-right: var(--spacing-s);
          padding: var(--spacing-xxs) var(--spacing-m) var(--spacing-xxs)
            var(--spacing-s);
          border-radius: 12px;
          border: 1px solid gray;
          vertical-align: top;
        }
        .checksChip .text {
          display: inline-block;
          max-width: 120px;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
          vertical-align: top;
        }
        iron-icon {
          width: var(--line-height-small);
          height: var(--line-height-small);
          vertical-align: top;
        }
        .checksChip.error {
          color: var(--error-foreground);
          border-color: var(--error-foreground);
          background: var(--error-background);
        }
        .checksChip.error:hover {
          background: var(--error-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.error:focus-within {
          background: var(--error-background-focus);
        }
        .checksChip.error iron-icon {
          color: var(--error-foreground);
        }
        .checksChip.warning {
          border-color: var(--warning-foreground);
          background: var(--warning-background);
        }
        .checksChip.warning:hover {
          background: var(--warning-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.warning:focus-within {
          background: var(--warning-background-focus);
        }
        .checksChip.warning iron-icon {
          color: var(--warning-foreground);
        }
        .checksChip.info-outline {
          border-color: var(--info-foreground);
          background: var(--info-background);
        }
        .checksChip.info-outline:hover {
          background: var(--info-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.info-outline:focus-within {
          background: var(--info-background-focus);
        }
        .checksChip.info-outline iron-icon {
          color: var(--info-foreground);
        }
        .checksChip.check-circle-outline {
          border-color: var(--success-foreground);
          background: var(--success-background);
        }
        .checksChip.check-circle-outline:hover {
          background: var(--success-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.check-circle-outline:focus-within {
          background: var(--success-background-focus);
        }
        .checksChip.check-circle-outline iron-icon {
          color: var(--success-foreground);
        }
        .checksChip.timelapse {
        }
        .checksChip.timelapse {
          border-color: var(--gray-foreground);
          background: var(--gray-background);
        }
        .checksChip.timelapse:hover {
          background: var(--gray-background-hover);
          box-shadow: var(--elevation-level-1);
        }
        .checksChip.timelapse:focus-within {
          background: var(--gray-background-focus);
        }
        .checksChip.timelapse iron-icon {
          color: var(--gray-foreground);
        }
      `,
    ];
  }

  override render() {
    if (!this.text) return;
    if (!this.statusOrCategory) return;
    const icon = iconFor(this.statusOrCategory);
    const label = labelFor(this.statusOrCategory);
    const count = Number(this.text);
    let ariaLabel = label;
    if (!isNaN(count)) {
      const type = isStatus(this.statusOrCategory) ? 'run' : 'result';
      const plural = count > 1 ? 's' : '';
      ariaLabel = `${this.text} ${label} ${type}${plural}`;
    }
    const chipClass = `checksChip font-small ${icon}`;
    const grIcon = `gr-icons:${icon}`;
    return html`
      <div
        class="${chipClass}"
        role="link"
        tabindex="0"
        aria-label="${ariaLabel}"
      >
        <iron-icon icon="${grIcon}"></iron-icon>
        <div class="text">${this.text}</div>
        <slot></slot>
      </div>
    `;
  }
}

/** What is the maximum number of detailed checks chips? */
const DETAILS_QUOTA: Map<RunStatus | Category, number> = new Map();
DETAILS_QUOTA.set(Category.ERROR, 7);
DETAILS_QUOTA.set(Category.WARNING, 2);
DETAILS_QUOTA.set(RunStatus.RUNNING, 2);

@customElement('gr-change-summary')
export class GrChangeSummary extends GrLitElement {
  @property({type: Object})
  changeComments?: ChangeComments;

  @property({type: Array})
  commentThreads?: CommentThread[];

  @property({type: Object})
  selfAccount?: AccountInfo;

  @property()
  runs: CheckRun[] = [];

  @property()
  showChecksSummary = false;

  @property()
  someProvidersAreLoading = false;

  @property()
  errorMessage?: string;

  @property()
  loginCallback?: () => void;

  private showAllChips = new Map<RunStatus | Category, boolean>();

  constructor() {
    super();
    this.subscribe('runs', allRunsLatestPatchsetLatestAttempt$);
    this.subscribe('showChecksSummary', aPluginHasRegistered$);
    this.subscribe('someProvidersAreLoading', someProvidersAreLoadingLatest$);
    this.subscribe('errorMessage', errorMessageLatest$);
    this.subscribe('loginCallback', loginCallbackLatest$);
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
          padding: var(--spacing-s);
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
          top: 2px;
          margin-right: var(--spacing-s);
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
          padding-bottom: var(--spacing-m);
          vertical-align: top;
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
      `,
    ];
  }

  renderChecksError() {
    if (!this.errorMessage) return;
    return html`
      <div class="error zeroState">
        <div class="left">
          <iron-icon icon="gr-icons:error"></iron-icon>
        </div>
        <div class="right">
          <div>Error while fetching check results</div>
          <div>${this.errorMessage}</div>
        </div>
      </div>
    `;
  }

  renderChecksLogin() {
    if (this.errorMessage || !this.loginCallback) return;
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
          <gr-button @click="${this.loginCallback}" link>Sign in</gr-button>
        </div>
      </div>
    `;
  }

  renderChecksZeroState() {
    if (this.errorMessage || this.loginCallback) return;
    if (this.runs.some(isRunningOrHasCompleted)) return;
    const msg = this.someProvidersAreLoading ? 'Loading results' : 'No results';
    return html`<span role="status" class="loading zeroState">${msg}</span>`;
  }

  renderChecksChipForCategory(category: Category) {
    if (this.errorMessage || this.loginCallback) return;
    const runs = this.runs.filter(run => {
      if (hasResultsOf(run, category)) return true;
      return category === Category.SUCCESS && hasCompletedWithoutResults(run);
    });
    const count = (run: CheckRun) => getResultsOf(run, category);
    if (category === Category.SUCCESS || category === Category.INFO) {
      return this.renderChecksChipsCollapsed(runs, category, count);
    }
    return this.renderChecksChipsExpanded(runs, category, count);
  }

  renderChecksChipRunning() {
    if (this.errorMessage || this.loginCallback) return;
    const runs = this.runs.filter(isRunning);
    return this.renderChecksChipsExpanded(runs, RunStatus.RUNNING, () => []);
  }

  renderChecksChipsExpanded(
    runs: CheckRun[],
    statusOrCategory: RunStatus | Category,
    resultFilter: (run: CheckRun) => CheckResult[]
  ) {
    if (runs.length === 0) return;
    const showAll = this.showAllChips.get(statusOrCategory) ?? false;
    let count = showAll ? 999 : DETAILS_QUOTA.get(statusOrCategory) ?? 2;
    if (count === runs.length - 1) count = runs.length;
    const more = runs.length - count;
    return html`${runs
      .slice(0, count)
      .map(run =>
        this.renderChecksChipDetailed(run, statusOrCategory, resultFilter)
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
      .statusOrCategory="${statusOrCategory}"
      .text="${count}"
      @click="${handler}"
      @keydown="${(e: KeyboardEvent) => handleSpaceOrEnter(e, handler)}"
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
      .statusOrCategory="${statusOrCategory}"
      .text="+ ${count} more"
      @click="${handler}"
      @keydown="${(e: KeyboardEvent) => handleSpaceOrEnter(e, handler)}"
    ></gr-checks-chip>`;
  }

  private renderChecksChipDetailed(
    run: CheckRun,
    statusOrCategory: RunStatus | Category,
    resultFilter: (run: CheckRun) => CheckResult[]
  ) {
    const allPrimaryLinks = resultFilter(run)
      .map(firstPrimaryLink)
      .filter(notUndefined);
    const links = allPrimaryLinks.length === 1 ? allPrimaryLinks : [];
    const text = `${run.checkName}`;
    const tabState: ChecksTabState = {
      checkName: run.checkName,
      statusOrCategory,
    };
    const handler = () => this.onChipClick(tabState);
    return html`<gr-checks-chip
      .statusOrCategory="${statusOrCategory}"
      .text="${text}"
      @click="${handler}"
      @keydown="${(e: KeyboardEvent) => handleSpaceOrEnter(e, handler)}"
      >${links.map(
        link => html`
          <a
            href="${link.url}"
            target="_blank"
            @click="${this.onLinkClick}"
            @keydown="${this.onLinkKeyDown}"
            aria-label="Link to check details"
            ><iron-icon class="launch" icon="gr-icons:launch"></iron-icon
          ></a>
        `
      )}
    </gr-checks-chip>`;
  }

  private onChipClick(state: ChecksTabState) {
    fireShowPrimaryTab(this, PrimaryTab.CHECKS, false, {
      checksTab: state,
    });
  }

  private onLinkKeyDown(e: KeyboardEvent) {
    // Prevents onConChipKeyDown() from reacting to <a> link keyboard events.
    e.stopPropagation();
  }

  private onLinkClick(e: MouseEvent) {
    // Prevents onChipClick() from reacting to <a> link clicks.
    e.stopPropagation();
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
    const hasRunningChip = this.runs.some(isRunning);
    return html`
      <div>
        <table>
          <tr ?hidden=${!this.showChecksSummary}>
            <td class="key">Checks</td>
            <td class="value">
              <div class="checksSummary">
                ${this.renderChecksError()}${this.renderChecksLogin()}
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
                  ?hidden="${!this.someProvidersAreLoading}"
                ></span>
              </div>
            </td>
          </tr>
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
                      .account="${account}"
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
    'gr-checks-chip': GrChecksChip;
    'gr-summary-chip': GrSummaryChip;
  }
}
