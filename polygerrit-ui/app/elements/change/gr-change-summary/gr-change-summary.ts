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
import {Category, Link, RunStatus} from '../../../api/checks';
import {fireShowPrimaryTab} from '../../../utils/event-util';
import '../../shared/gr-avatar/gr-avatar';
import {
  getResultsOf,
  hasCompletedWithoutResults,
  hasResultsOf,
  iconForCategory,
  iconForStatus,
  isRunning,
  isRunningOrHasCompleted,
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

export enum SummaryChipStyles {
  INFO = 'info',
  WARNING = 'warning',
  CHECK = 'check',
  UNDEFINED = '',
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

  static get styles() {
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
        .summaryChip.check iron-icon {
          color: var(--gray-foreground);
        }
      `,
    ];
  }

  render() {
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
  icon = '';

  @property()
  text = '';

  static get styles() {
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
        .checksChip.timelapse iron-icon {
          color: var(--gray-foreground);
        }
      `,
    ];
  }

  render() {
    if (!this.text) return;
    const chipClass = `checksChip font-small ${this.icon}`;
    const grIcon = `gr-icons:${this.icon}`;
    return html`
      <div class="${chipClass}" role="button">
        <iron-icon icon="${grIcon}"></iron-icon>
        <div class="text">${this.text}</div>
        <slot></slot>
      </div>
    `;
  }
}

/** What is the maximum number of expanded checks chips? */
const DETAILS_QUOTA = 2;

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

  /**
   * How many check chips may still be rendered as a detailed chip. Is reset
   * when rendering begins and decreases while chips are rendered. So when
   * there are two ERRORs, then those would consume 2 from this quota and then
   * there would only be DETAILS_QUOTA - 2 left for the other summary chips.
   * Once there are more results than quota left we will stop rendering
   * detailed chips and fall back to just icon+number rendering.
   */
  private detailsQuota = DETAILS_QUOTA;

  /**
   * Is reset when rendering begins and contains the check names of runs that
   * have a detailed chip. We keep track of this such that we can ensure to not
   * show two detailed chips with the same name.
   */
  private detailsCheckNames: string[] = [];

  constructor() {
    super();
    this.subscribe('runs', allRunsLatestPatchsetLatestAttempt$);
    this.subscribe('showChecksSummary', aPluginHasRegistered$);
    this.subscribe('someProvidersAreLoading', someProvidersAreLoadingLatest$);
    this.subscribe('errorMessage', errorMessageLatest$);
    this.subscribe('loginCallback', loginCallbackLatest$);
  }

  static get styles() {
    return [
      sharedStyles,
      spinnerStyles,
      css`
        :host {
          display: block;
          color: var(--deemphasized-text-color);
          max-width: 650px;
          margin-bottom: var(--spacing-m);
        }
        .zeroState {
          color: var(--primary-text-color);
        }
        .loading.zeroState {
          color: var(--deemphasized-text-color);
          margin-right: var(--spacing-m);
        }
        div.error {
          background-color: var(--error-background);
          display: flex;
          padding: var(--spacing-s);
        }
        div.error iron-icon {
          color: var(--error-foreground);
          width: 16px;
          height: 16px;
          position: relative;
          top: 2px;
          margin-right: var(--spacing-s);
        }
        .login gr-button {
          margin: -4px var(--spacing-s);
        }
        td.key {
          padding-right: var(--spacing-l);
          padding-bottom: var(--spacing-m);
        }
        td.value {
          padding-right: var(--spacing-l);
          padding-bottom: var(--spacing-m);
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
      <div class="login zeroState">
        Not logged in
        <gr-button @click="${this.loginCallback}" link>Sign in</gr-button>
      </div>
    `;
  }

  renderChecksZeroState() {
    if (this.errorMessage || this.loginCallback) return;
    if (this.runs.some(isRunningOrHasCompleted)) return;
    const msg = this.someProvidersAreLoading ? 'Loading results' : 'No results';
    return html`<span class="loading zeroState">${msg}</span>`;
  }

  renderChecksChipForCategory(category: Category) {
    if (this.errorMessage || this.loginCallback) return;
    const icon = iconForCategory(category);
    const runs = this.runs.filter(run => {
      if (hasResultsOf(run, category)) return true;
      return category === Category.SUCCESS && hasCompletedWithoutResults(run);
    });
    const count = (run: CheckRun) => getResultsOf(run, category);
    return this.renderChecksChip(icon, runs, category, count);
  }

  renderChecksChipForStatus(
    status: RunStatus,
    filter: (run: CheckRun) => boolean
  ) {
    if (this.errorMessage || this.loginCallback) return;
    const icon = iconForStatus(status);
    const runs = this.runs.filter(filter);
    return this.renderChecksChip(icon, runs, status, () => []);
  }

  renderChecksChip(
    icon: string,
    runs: CheckRun[],
    statusOrCategory: RunStatus | Category,
    resultFilter: (run: CheckRun) => CheckResult[]
  ) {
    if (runs.length === 0) {
      return html``;
    }
    // If a run has both an error and a warning result, then we only want to
    // show a detailed chip with the expanded checkName once. For simplicity
    // just stop rendering detailed chips completely as soon as we run into
    // this by setting detailsQuota to 0 (after the if-block).
    const hasDetailChipAlready = runs.some(run =>
      this.detailsCheckNames.includes(run.checkName)
    );
    if (!hasDetailChipAlready && runs.length <= this.detailsQuota) {
      this.detailsQuota -= runs.length;
      return runs.map(run => {
        this.detailsCheckNames.push(run.checkName);
        const allLinks = resultFilter(run)
          .reduce(
            (links, result) => links.concat(result.links ?? []),
            [] as Link[]
          )
          .filter(link => link.primary);
        const links = allLinks.length === 1 ? allLinks : [];
        const text = `${run.checkName}`;
        return html`<gr-checks-chip
          class="${icon}"
          .icon="${icon}"
          .text="${text}"
          @click="${() => this.onChipClick({checkName: run.checkName})}"
          >${links.map(
            link => html`
              <a href="${link.url}" target="_blank" @click="${this.onLinkClick}"
                ><iron-icon class="launch" icon="gr-icons:launch"></iron-icon
              ></a>
            `
          )}
        </gr-checks-chip>`;
      });
    }
    this.detailsQuota = 0;
    this.detailsCheckNames = [];
    const sum = runs.reduce(
      (sum, run) => sum + (resultFilter(run).length || 1),
      0
    );
    if (sum === 0) return;
    return html`<gr-checks-chip
      class="${icon}"
      .icon="${icon}"
      .text="${sum}"
      @click="${() => this.onChipClick({statusOrCategory})}"
    ></gr-checks-chip>`;
  }

  private onChipClick(state: ChecksTabState) {
    fireShowPrimaryTab(this, PrimaryTab.CHECKS, false, {
      checksTab: state,
    });
  }

  private onLinkClick(e: MouseEvent) {
    // Prevents onChipClick() from reacting to <a> link clicks.
    e.stopPropagation();
  }

  render() {
    this.detailsQuota = DETAILS_QUOTA;
    this.detailsCheckNames = [];
    const commentThreads =
      this.commentThreads?.filter(t => !isRobotThread(t) || hasHumanReply(t)) ??
      [];
    const countResolvedComments = commentThreads.filter(isResolved).length;
    const unresolvedThreads = commentThreads.filter(isUnresolved);
    const countUnresolvedComments = unresolvedThreads.length;
    const unresolvedAuthors = this.getAccounts(unresolvedThreads);
    const draftCount = this.changeComments?.computeDraftCount() ?? 0;
    return html`
      <div>
        <table>
          <tr ?hidden=${!this.showChecksSummary}>
            <td class="key">Checks</td>
            <td class="value">
              ${this.renderChecksError()}${this.renderChecksLogin()}
              ${this.renderChecksZeroState()}${this.renderChecksChipForCategory(
                Category.ERROR
              )}${this.renderChecksChipForCategory(
                Category.WARNING
              )}${this.renderChecksChipForCategory(
                Category.INFO
              )}${this.renderChecksChipForCategory(
                Category.SUCCESS
              )}${this.renderChecksChipForStatus(RunStatus.RUNNING, isRunning)}
              <span
                class="loadingSpin"
                ?hidden="${!this.someProvidersAreLoading}"
              ></span>
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
                      image-size="32"
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
