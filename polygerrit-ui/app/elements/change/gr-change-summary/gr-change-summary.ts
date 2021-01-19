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
  CheckRun,
  Link,
  RunStatus,
} from '../../plugins/gr-checks-api/gr-checks-api-types';
import {allRuns$, RunResult} from '../../../services/checks/checks-model';

function hasRunningOrCompleted(runs: CheckRun[]) {
  return runs.some(
    run =>
      run.status === RunStatus.COMPLETED || run.status === RunStatus.RUNNING
  );
}

function filterResults(runs: CheckRun[], category: Category): RunResult[] {
  return runs
    .filter(run => run.status === RunStatus.COMPLETED)
    .reduce((results, run) => {
      return results.concat(
        (run.results ?? [])
          .filter(result => result.category === category)
          .map(result => {
            return {...run, ...result};
          })
      );
    }, [] as RunResult[]);
}

@customElement('gr-checks-chip')
export class GrChecksChip extends GrLitElement {
  @property()
  icon = '';

  @property()
  expandSingleResult = false;

  @property()
  runs: CheckRun[] = [];

  @property()
  results: RunResult[] = [];

  static get styles() {
    return [
      sharedStyles,
      css`
        .checksChip {
          display: inline-block;
          line-height: var(--line-height-normal);
          margin-right: var(--spacing-s);
          padding: 0 var(--spacing-m) 0 var(--spacing-s);
          border-radius: 12px;
          border: 1px solid gray;
        }
        .checksChip .checkName {
          display: inline-block;
          max-width: 120px;
          white-space: nowrap;
          overflow: hidden;
          text-overflow: ellipsis;
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
          color: var(--primary-text-color);
          border-color: var(--warning-foreground);
          background-color: var(--warning-background);
        }
        .checksChip.warning iron-icon {
          color: var(--warning-foreground);
        }
        .checksChip.info-outline {
          color: var(--primary-text-color);
          border-color: var(--info-foreground);
          background-color: var(--info-background);
        }
        .checksChip.info-outline iron-icon {
          color: var(--info-foreground);
        }
        .checksChip.check {
          color: var(--primary-text-color);
          border-color: var(--gray-foreground);
          background-color: var(--gray-background);
        }
        .checksChip.check iron-icon {
          color: var(--gray-foreground);
        }
        .checksChip.timelapse {
          color: var(--primary-text-color);
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
    const result = this.results[0];
    if (count !== 1 || !result || !this.expandSingleResult) {
      return this.renderChipCount(count);
    }
    return this.renderChipFull(result);
  }

  private renderChipFull(result: RunResult) {
    const chipClass = `checksChip ${this.icon}`;
    const grIcon = `gr-icons:${this.icon}`;
    return html`
      <div class="${chipClass}">
        <iron-icon icon="${grIcon}"></iron-icon>
        <div class="checkName">${result.checkName}</div>
        ${this.renderResultLinks(result.links ?? [])}
      </div>
    `;
  }

  private renderResultLinks(links: Link[]) {
    return links
      .filter(link => link.primary)
      .slice(0, 2)
      .map(
        link => html`
          <a href="${link.url}" target="_blank">
            <iron-icon class="launch" icon="gr-icons:launch"></iron-icon>
          </a>
        `
      );
  }

  private renderChipCount(count: number) {
    const chipClass = `checksChip ${this.icon}`;
    const grIcon = `gr-icons:${this.icon}`;
    return html`
      <div class="${chipClass}">
        <iron-icon icon="${grIcon}"></iron-icon>
        ${count}
      </div>
    `;
  }
}

@customElement('gr-change-summary')
export class GrChangeSummary extends GrLitElement {
  private readonly ciRebootChecksEnabled = appContext.flagsService.isEnabled(
    KnownExperimentId.CI_REBOOT_CHECKS
  );

  @property()
  runs: CheckRun[] = [];

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
          /* TODO: Reasonably calculate that to match the 72ch commit message
                   width. */
          max-width: 620px;
        }
        td.key {
          padding-right: var(--spacing-l);
        }
        td.value {
          padding-right: var(--spacing-l);
          /* This is just for making it look nice when the content wraps to a
             second line. Maybe we always want to force just 1 line somehow? */
          line-height: 26px;
        }
        .runs {
          margin-right: var(--spacing-s);
          margin-left: var(--spacing-m);
        }
        iron-icon {
          vertical-align: top;
        }
      `,
    ];
  }

  render() {
    const runs: CheckRun[] = this.runs;
    const completed = runs.filter(run => run.status === RunStatus.COMPLETED);
    const running = runs.filter(run => run.status === RunStatus.RUNNING);
    const errors = filterResults(runs, Category.ERROR);
    const warnings = filterResults(runs, Category.WARNING);
    const infos = filterResults(runs, Category.INFO);
    return html`
      <div>
        <table>
          <tr ?hidden=${!this.ciRebootChecksEnabled}>
            <td class="key">Checks</td>
            <td class="value">
              <gr-checks-chip
                icon="error"
                .results="${errors}"
                expandSingleResult="true"
              ></gr-checks-chip>
              <gr-checks-chip
                icon="warning"
                .results="${warnings}"
                expandSingleResult="true"
              ></gr-checks-chip>
              <gr-checks-chip
                icon="info-outline"
                .results="${infos}"
              ></gr-checks-chip>
              <span ?hidden=${!hasRunningOrCompleted(runs)} class="runs"
                >Runs</span
              >
              <gr-checks-chip
                icon="check"
                .runs="${completed}"
              ></gr-checks-chip>
              <gr-checks-chip
                icon="timelapse"
                .runs="${running}"
              ></gr-checks-chip>
            </td>
          </tr>
          <tr hidden>
            <td class="key">Comments</td>
            <td class="value"></td>
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
    'gr-checks-chip': GrChecksChip;
  }
}
