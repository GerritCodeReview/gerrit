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
  Category,
  CheckResult,
  CheckRun,
  Link,
  RunStatus,
} from '../../../api/checks';
import {allRuns$} from '../../../services/checks/checks-model';
import {fireShowPrimaryTab} from '../../../utils/event-util';
import {
  getResultsOf,
  hasCompletedWithoutResults,
  hasResultsOf,
  iconForCategory,
  iconForStatus,
  isRunning,
} from '../../../services/checks/checks-util';
import {ChangeComments} from '../../diff/gr-comment-api/gr-comment-api';
import {CommentThread, isResolved} from '../../../utils/comment-util';

@customElement('gr-summary-chip')
export class GrSummaryChip extends GrLitElement {
  @property()
  icon = '';

  static get styles() {
    return [
      sharedStyles,
      css`
        .summaryChip {
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
        iron-icon {
          width: var(--line-height-small);
          height: var(--line-height-small);
          vertical-align: top;
        }
      `,
    ];
  }

  render() {
    const chipClass = `summaryChip font-small ${this.icon ?? ''}`;
    const grIcon = this.icon ? `gr-icons:${this.icon}` : '';
    return html`
      <div class="${chipClass}" role="button">
        ${this.icon && html`<iron-icon icon="${grIcon}"></iron-icon>`}
        <slot></slot>
      </div>
    `;
  }
}

const chipStyles = css`
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
  .checksChip .checkName {
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
  div.checksChip iron-icon.launch {
    color: var(--gray-foreground);
  }
  .checksChip.error {
    color: var(--error-color);
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
`;

@customElement('gr-checks-count-chip')
export class GrChecksCountChip extends GrLitElement {
  @property()
  icon = '';

  @property()
  count = 0;

  static get styles() {
    return [sharedStyles, chipStyles];
  }

  render() {
    if (this.count === 0) return;
    const chipClass = `checksChip font-small ${this.icon}`;
    const grIcon = `gr-icons:${this.icon}`;
    return html`
      <div class="${chipClass}" role="button" @click="${this.handleClick}">
        <iron-icon icon="${grIcon}"></iron-icon>
        ${this.count}
      </div>
    `;
  }

  private handleClick() {
    fireShowPrimaryTab(this, 'checks');
  }
}

@customElement('gr-checks-detail-chip')
export class GrChecksDetailChip extends GrLitElement {
  @property()
  icon = '';

  @property()
  text = '';

  @property()
  links: Link[] = [];

  static get styles() {
    return [sharedStyles, chipStyles];
  }

  render() {
    const chipClass = `checksChip font-small ${this.icon}`;
    const grIcon = `gr-icons:${this.icon}`;
    return html`
      <div class="${chipClass}" role="button" @click="${this.handleClick}">
        <iron-icon icon="${grIcon}"></iron-icon>
        <div class="checkName">${this.text}</div>
        ${this.links.map(link => this.renderLink(link))}
      </div>
    `;
  }

  private renderLink(link: Link) {
    return html`
      <a href="${link.url}" target="_blank" @click="${this.handleClickLink}"
        ><iron-icon class="launch" icon="gr-icons:launch"></iron-icon
      ></a>
    `;
  }

  private handleClick() {
    fireShowPrimaryTab(this, 'checks');
  }

  private handleClickLink(e: Event) {
    // Prevents handleClick() from reacting to <a> link clicks.
    e.stopPropagation();
  }
}

@customElement('gr-change-summary')
export class GrChangeSummary extends GrLitElement {
  private readonly ciRebootChecksEnabled = appContext.flagsService.isEnabled(
    KnownExperimentId.CI_REBOOT_CHECKS
  );

  private readonly newChangeSummaryUiEnabled = appContext.flagsService.isEnabled(
    KnownExperimentId.NEW_CHANGE_SUMMARY_UI
  );

  @property({type: Array})
  changeComments?: ChangeComments;

  @property({type: Object})
  commentThreads?: CommentThread[];

  @property()
  runs: CheckRun[] = [];

  /** What is the maximum number of expanded checks chips? */
  private detailsQuota = 3;

  constructor() {
    super();
    this.subscribe('runs', allRuns$);
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
        .runningSeparator {
          display: inline-block;
          width: var(--spacing-s);
        }
        .runningSeparator:first-child {
          display: none;
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
      // TODO: Render the link icons.
      return runs.map(run => {
        const links = resultFilter(run)
          .reduce((links, result) => {
            return links.concat(result.links ?? []);
          }, [] as Link[])
          .filter(link => link)
          .slice(0, 3);
        const count = resultFilter(run).length;
        const countText = count > 1 ? ` ${count}` : '';
        const text = `${run.checkName}${countText}`;
        return html`
          <gr-checks-detail-chip
            class="${icon}"
            .icon="${icon}"
            .text="${text}"
            .links="${links}"
          ></gr-checks-detail-chip>
        `;
      });
    }
    // runs.length > this.detailsQuota
    this.detailsQuota = 0;
    const sum = runs.reduce(
      (sum, run) => sum + (resultFilter(run).length || 1),
      0
    );
    return html`
      <gr-checks-count-chip
        class="${icon}"
        .icon="${icon}"
        .count="${sum}"
      ></gr-checks-count-chip>
    `;
  }

  render() {
    this.detailsQuota = 3;
    const numResolvedComments =
      this.commentThreads?.filter(isResolved).length ?? 0;
    const draftCount = this.changeComments?.computeDraftCount() ?? 0;
    return html`
      <div>
        <table>
          <tr ?hidden=${!this.ciRebootChecksEnabled}>
            <td class="key">Checks</td>
            <td class="value">
              ${this.renderChecksChipForCategory(Category.ERROR)}
              ${this.renderChecksChipForCategory(Category.WARNING)}
              ${this.renderChecksChipForCategory(Category.INFO)}
              ${this.renderChecksChipForStatus(
                RunStatus.COMPLETED,
                hasCompletedWithoutResults
              )}
              <div class="runningSeparator"></div>
              ${this.renderChecksChipForStatus(RunStatus.RUNNING, isRunning)}
            </td>
          </tr>
          <tr ?hidden=${!this.newChangeSummaryUiEnabled}>
            <td class="key">Comments</td>
            <td class="value">
              <gr-summary-chip ?hidden=${!!numResolvedComments || !!draftCount}>
                No Comments</gr-summary-chip
              >
              <gr-summary-chip icon="edit" ?hidden=${!draftCount}>
                ${draftCount} draft</gr-summary-chip
              >
              <gr-summary-chip
                icon="markChatRead"
                ?hidden=${!numResolvedComments}
                >${numResolvedComments} resolved</gr-summary-chip
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
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-summary': GrChangeSummary;
    'gr-checks-detail-chip': GrChecksDetailChip;
    'gr-checks-count-chip': GrChecksCountChip;
    'gr-summary-chip': GrSummaryChip;
  }
}
