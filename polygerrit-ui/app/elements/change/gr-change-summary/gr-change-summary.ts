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
import {html, TemplateResult} from 'lit-html';
import {css, customElement, property} from 'lit-element';
import {GrLitElement} from '../../lit/gr-lit-element';
import {sharedStyles} from '../../../styles/shared-styles';
import {appContext} from '../../../services/app-context';
import {KnownExperimentId} from '../../../services/flags/flags';
import {Category, CheckRun, Link} from '../../../api/checks';
import {
  allRuns$,
  aPluginHasRegistered,
  RunResult,
} from '../../../services/checks/checks-model';
import {fireShowPrimaryTab} from '../../../utils/event-util';
import '../../shared/gr-avatar/gr-avatar';
import {
  hasCompleted,
  isRunning,
  isRunningOrHasCompleted,
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

function filterResults(runs: CheckRun[], category: Category): RunResult[] {
  return runs.filter(isRunningOrHasCompleted).reduce((results, run) => {
    return results.concat(
      (run.results ?? [])
        .filter(result => result.category === category)
        .map(result => {
          return {...run, ...result};
        })
    );
  }, [] as RunResult[]);
}

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
    return html`
      <div class="${chipClass}" role="button">
        ${this.icon && html`<iron-icon icon="${grIcon}"></iron-icon>`}
        <slot></slot>
      </div>
    `;
  }
}

@customElement('gr-checks-chip')
export class GrChecksChip extends GrLitElement {
  @property()
  icon = '';

  @property()
  expandMax = 0;

  @property()
  runs: CheckRun[] = [];

  @property()
  results: RunResult[] = [];

  static get styles() {
    return [
      sharedStyles,
      css`
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
        .checksChip.check {
          border-color: var(--gray-foreground);
          background-color: var(--gray-background);
        }
        .checksChip.check iron-icon {
          color: var(--gray-foreground);
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
    const count = this.runs.length || this.results.length;
    if (count === 0) return;
    if (count > this.expandMax || !this.results.length) {
      return this.renderChip(html`${count}`);
    }
    return this.results.map(result =>
      this.renderChip(this.renderNameAndLinks(result))
    );
  }

  private renderChip(content: TemplateResult) {
    const chipClass = `checksChip font-small ${this.icon}`;
    const grIcon = `gr-icons:${this.icon}`;
    return html`
      <div class="${chipClass}" role="button" @click="${this.handleClick}">
        <iron-icon icon="${grIcon}"></iron-icon>
        ${content}
      </div>
    `;
  }

  private renderNameAndLinks(result: RunResult) {
    return html`
      <div class="checkName">${result.checkName}</div>
      ${this.renderResultLinks(result.links ?? [])}
    `;
  }

  private renderResultLinks(links: Link[]) {
    return links
      .filter(link => link.primary)
      .slice(0, 2)
      .map(
        link => html`
          <a
            href="${link.url}"
            target="_blank"
            @click="${this.handleClickLink}"
          >
            <iron-icon class="launch" icon="gr-icons:launch"></iron-icon>
          </a>
        `
      );
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
        .runs {
          margin-right: var(--spacing-s);
          margin-left: var(--spacing-m);
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

  render() {
    const runs: CheckRun[] = this.runs;
    const errors = filterResults(runs, Category.ERROR);
    const warnings = filterResults(runs, Category.WARNING);
    const infos = filterResults(runs, Category.INFO);
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
              <gr-checks-chip
                icon="error"
                .results="${errors}"
                expandMax="2"
              ></gr-checks-chip>
              <gr-checks-chip
                icon="warning"
                .results="${warnings}"
                expandMax="${2 - errors.length}"
              ></gr-checks-chip>
              <gr-checks-chip
                icon="info-outline"
                .results="${infos}"
              ></gr-checks-chip>
              <span ?hidden=${!runs.some(isRunningOrHasCompleted)} class="runs"
                >Runs</span
              >
              <gr-checks-chip
                icon="check"
                .runs="${runs.filter(hasCompleted)}"
              ></gr-checks-chip>
              <gr-checks-chip
                icon="timelapse"
                .runs="${runs.filter(isRunning)}"
              ></gr-checks-chip>
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
