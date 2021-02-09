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
import {KnownExperimentId} from '../../../services/flags/flags';
import {
  allRuns$,
  aPluginHasRegistered,
} from '../../../services/checks/checks-model';
import {
  Category,
  CheckResult,
  CheckRun,
  Link,
  RunStatus,
} from '../../../api/checks';
import {fireShowPrimaryTab} from '../../../utils/event-util';
import '../../shared/gr-avatar/gr-avatar';
import {
  getResultsOf,
  hasCompletedWithoutResults,
  hasResultsOf,
  iconForCategory,
  iconForStatus,
  isRunning,
} from '../../../services/checks/checks-util';
import {ChangeComments} from '../../diff/gr-comment-api/gr-comment-api';
import {
  CommentThread,
  isResolved,
  isUnresolved,
  getFirstComment,
} from '../../../utils/comment-util';
import {pluralize} from '../../../utils/string-util';
import {AccountInfo} from '../../../types/common';
import {notUndefined} from '../../../types/types';
import {uniqueDefinedAvatar} from '../../../utils/account-util';
import {PrimaryTab} from '../../../constants/constants';

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

  static get styles() {
    return [
      sharedStyles,
      css`
        .summaryChip {
          color: var(--chip-color);
          cursor: pointer;
          display: inline-block;
          padding: var(--spacing-xxs) var(--spacing-s) var(--spacing-xxs)
            var(--spacing-s);
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
          background-color: var(--warning-background);
        }
        .summaryChip.warning iron-icon {
          color: var(--warning-foreground);
        }
        .summaryChip.check {
          border-color: var(--gray-foreground);
          background-color: var(--gray-background);
        }
        .summaryChip.check iron-icon {
          color: var(--gray-foreground);
        }
        .summaryChip.info {
          border-color: var(--info-deemphasized-foreground;
          background-color: var(--info-deemphasized-background);
        }
        .summaryChip.info iron-icon {
          color: var(--info-deemphasized-foreground);
        }
      `,
    ];
  }

  render() {
    const chipClass = `summaryChip font-small ${this.styleType}`;
    const grIcon = this.icon ? `gr-icons:${this.icon}` : '';
    return html`<div
      class="${chipClass}"
      role="button"
      @click="${this.handleClick}"
    >
      ${this.icon && html`<iron-icon icon="${grIcon}"></iron-icon>`}
      <slot></slot>
    </div>`;
  }

  private handleClick(e: MouseEvent) {
    e.stopPropagation();
    e.preventDefault();
    fireShowPrimaryTab(this, PrimaryTab.COMMENT_THREADS);
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
          background-color: var(--error-background);
        }
        .checksChip.error iron-icon {
          color: var(--error-foreground);
        }
        .checksChip.warning {
          border-color: var(--warning-foreground);
          background-color: var(--warning-background);
        }
        .checksChip.warning iron-icon {
          color: var(--warning-foreground);
        }
        .checksChip.info-outline {
          border-color: var(--info-foreground);
          background-color: var(--info-background);
        }
        .checksChip.info-outline iron-icon {
          color: var(--info-foreground);
        }
        .checksChip.check-circle {
          border-color: var(--success-foreground);
          background-color: var(--success-background);
        }
        .checksChip.check-circle iron-icon {
          color: var(--success-foreground);
        }
        .checksChip.timelapse {
        }
        .checksChip.timelapse {
          border-color: var(--gray-foreground);
          background-color: var(--gray-background);
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
      <div class="${chipClass}" role="button" @click="${this.handleClick}">
        <iron-icon icon="${grIcon}"></iron-icon>
        <div class="text">${this.text}</div>
        <slot></slot>
      </div>
    `;
  }

  private handleClick(e: MouseEvent) {
    e.stopPropagation();
    e.preventDefault();
    fireShowPrimaryTab(this, PrimaryTab.CHECKS);
  }
}

/** What is the maximum number of expanded checks chips? */
const DETAILS_QUOTA = 3;

/** What is the maximum number of links renderend within one chip? */
const MAX_LINKS_PER_CHIP = 3;

@customElement('gr-change-summary')
export class GrChangeSummary extends GrLitElement {
  private readonly newChangeSummaryUiEnabled = appContext.flagsService.isEnabled(
    KnownExperimentId.NEW_CHANGE_SUMMARY_UI
  );

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

  /** Is reset when rendering beings and decreases while chips are rendered. */
  private detailsQuota = DETAILS_QUOTA;

  constructor() {
    super();
    this.subscribe('runs', allRuns$);
    this.subscribe('showChecksSummary', aPluginHasRegistered);
  }

  static get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
          color: var(--deemphasized-text-color);
          /* temporary for old checks status */
        }
        :host.new-change-summary-true {
          margin-bottom: var(--spacing-m);
        }
        td.key {
          padding-right: var(--spacing-l);
        }
        td.value {
          padding-right: var(--spacing-l);
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
      `,
    ];
  }

  renderChecksChipForCategory(category: Category) {
    const icon = iconForCategory(category);
    const runs = this.runs.filter(run => hasResultsOf(run, category));
    const count = (run: CheckRun) => getResultsOf(run, category);
    return this.renderChecksChip(icon, runs, count);
  }

  renderChecksChipForStatus(
    status: RunStatus,
    filter: (run: CheckRun) => boolean
  ) {
    const icon = iconForStatus(status);
    const runs = this.runs.filter(filter);
    return this.renderChecksChip(icon, runs, () => []);
  }

  renderChecksChip(
    icon: string,
    runs: CheckRun[],
    resultFilter: (run: CheckRun) => CheckResult[]
  ) {
    if (runs.length === 0) {
      return html``;
    }
    if (runs.length <= this.detailsQuota) {
      this.detailsQuota -= runs.length;
      return runs.map(run => {
        const links = resultFilter(run)
          .reduce((links, result) => {
            return links.concat(result.links ?? []);
          }, [] as Link[])
          .filter(link => link.primary)
          .slice(0, MAX_LINKS_PER_CHIP);
        const count = resultFilter(run).length;
        const countText = count > 1 ? ` ${count}` : '';
        const text = `${run.checkName}${countText}`;
        return html`<gr-checks-chip
          class="${icon}"
          .icon="${icon}"
          .text="${text}"
          >${links.map(
            link => html`
              <a href="${link.url}" target="_blank" @click="${this.onClick}"
                ><iron-icon class="launch" icon="gr-icons:launch"></iron-icon
              ></a>
            `
          )}
        </gr-checks-chip>`;
      });
    }
    // runs.length > this.detailsQuota
    this.detailsQuota = 0;
    const sum = runs.reduce(
      (sum, run) => sum + (resultFilter(run).length || 1),
      0
    );
    if (sum === 0) return;
    return html`<gr-checks-chip
      class="${icon}"
      .icon="${icon}"
      .text="${sum}"
    ></gr-checks-chip>`;
  }

  private onClick(e: MouseEvent) {
    // Prevents handleClick() from reacting to <a> link clicks.
    e.stopPropagation();
  }

  render() {
    this.detailsQuota = DETAILS_QUOTA;
    const countResolvedComments =
      this.commentThreads?.filter(isResolved).length ?? 0;
    const unresolvedThreads = this.commentThreads?.filter(isUnresolved) ?? [];
    const countUnresolvedComments = unresolvedThreads.length;
    const unresolvedAuthors = this.getAccounts(unresolvedThreads);
    const draftCount = this.changeComments?.computeDraftCount() ?? 0;
    return html`
      <div>
        <table>
          <tr ?hidden=${!this.showChecksSummary}>
            <td class="key">Checks</td>
            <td class="value">
              ${this.renderChecksChipForCategory(
                Category.ERROR
              )}${this.renderChecksChipForCategory(
                Category.WARNING
              )}${this.renderChecksChipForCategory(
                Category.INFO
              )}${this.renderChecksChipForStatus(
                RunStatus.COMPLETED,
                hasCompletedWithoutResults
              )}${this.renderChecksChipForStatus(RunStatus.RUNNING, isRunning)}
            </td>
          </tr>
          <tr ?hidden=${!this.newChangeSummaryUiEnabled}>
            <td class="key">Comments</td>
            <td class="value">
              <gr-summary-chip
                styleType=${SummaryChipStyles.INFO}
                ?hidden=${!!countResolvedComments ||
                !!draftCount ||
                !!countUnresolvedComments}
              >
                No Comments</gr-summary-chip
              >
              <gr-summary-chip
                styleType=${SummaryChipStyles.WARNING}
                icon="edit"
                ?hidden=${!draftCount}
              >
                ${pluralize(draftCount, 'draft')}</gr-summary-chip
              >
              <gr-summary-chip
                styleType=${SummaryChipStyles.WARNING}
                icon="message"
                ?hidden=${!countUnresolvedComments}
              >
                ${unresolvedAuthors.map(
                  account =>
                    html`<gr-avatar
                      .account="${account}"
                      image-size="32"
                      aria-label="Account avatar"
                    ></gr-avatar>`
                )}
                ${countUnresolvedComments} unresolved</gr-summary-chip
              >
              <gr-summary-chip
                styleType=${SummaryChipStyles.CHECK}
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
