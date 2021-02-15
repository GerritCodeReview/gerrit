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
import {classMap} from 'lit-html/directives/class-map';
import {css, customElement, property} from 'lit-element';
import {GrLitElement} from '../lit/gr-lit-element';
import {Action, CheckRun, RunStatus} from '../../api/checks';
import {sharedStyles} from '../../styles/shared-styles';
import {
  compareByWorstCategory,
  iconForRun,
  primaryRunAction,
} from '../../services/checks/checks-util';
import {
  allRuns$,
  fakeRun0,
  fakeRun1,
  fakeRun2,
  fakeRun3,
  fakeRun4,
  updateStateSetResults,
} from '../../services/checks/checks-model';

/* The RunSelectedEvent is only used locally to communicate from <gr-checks-run>
   to its <gr-checks-runs> parent. */

interface RunSelectedEventDetail {
  checkName: string;
}

type RunSelectedEvent = CustomEvent<RunSelectedEventDetail>;

declare global {
  interface HTMLElementEventMap {
    'run-selected': RunSelectedEvent;
  }
}

function fireRunSelected(target: EventTarget, checkName: string) {
  target.dispatchEvent(
    new CustomEvent('run-selected', {
      detail: {checkName},
      composed: false,
      bubbles: false,
    })
  );
}

export interface ActionTriggeredEventDetail {
  action: Action;
  run: CheckRun;
}

export type ActionTriggeredEvent = CustomEvent<ActionTriggeredEventDetail>;

declare global {
  interface HTMLElementEventMap {
    'action-triggered': ActionTriggeredEvent;
  }
}

function fireActionTriggered(
  target: EventTarget,
  action: Action,
  run: CheckRun
) {
  target.dispatchEvent(
    new CustomEvent('action-triggered', {
      detail: {action, run},
      composed: true,
      bubbles: true,
    })
  );
}

@customElement('gr-checks-run')
export class GrChecksRun extends GrLitElement {
  static get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
          --thick-border: 6px;
        }
        .chip {
          display: flex;
          justify-content: space-between;
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          padding: var(--spacing-s) var(--spacing-m);
          margin-top: var(--spacing-s);
          cursor: default;
        }
        .name {
          font-weight: var(--font-weight-bold);
        }
        .chip.error {
          border-left: var(--thick-border) solid var(--error-foreground);
        }
        .chip.warning {
          border-left: var(--thick-border) solid var(--warning-foreground);
        }
        .chip.info-outline {
          border-left: var(--thick-border) solid var(--info-foreground);
        }
        .chip.check-circle-outline {
          border-left: var(--thick-border) solid var(--success-foreground);
        }
        .chip.timelapse {
          border-left: var(--thick-border) solid var(--border-color);
        }
        .chip.placeholder {
          border-left: var(--thick-border) solid var(--border-color);
        }
        .chip.error iron-icon {
          color: var(--error-foreground);
        }
        .chip.warning iron-icon {
          color: var(--warning-foreground);
        }
        .chip.info-outline iron-icon {
          color: var(--info-foreground);
        }
        .chip.check-circle-outline iron-icon {
          color: var(--success-foreground);
        }
        /* Additional 'div' for increased specificity. */
        div.chip.selected {
          border: 1px solid var(--selected-foreground);
          background-color: var(--selected-background);
          padding-left: calc(var(--spacing-m) + var(--thick-border) - 1px);
        }
        div.chip.selected iron-icon {
          color: var(--selected-foreground);
        }
        gr-button.action {
          --padding: var(--spacing-xs) var(--spacing-m);
          /* The button should fit into the 20px line-height. The negative
             margin provides the extra space needed for the vertical padding.
             Alternatively we could have set the vertical padding to 0, but
             that would not have been a nice click target. */
          margin: calc(0px - var(--spacing-xs));
        }
      `,
    ];
  }

  @property()
  run!: CheckRun;

  @property()
  selected = false;

  render() {
    const icon = this.selected ? 'check-circle' : iconForRun(this.run);
    const classes = {chip: true, [icon]: true, selected: this.selected};
    const action = primaryRunAction(this.run);

    return html`
      <div @click="${this.handleChipClick}" class="${classMap(classes)}">
        <div class="left">
          <iron-icon icon="gr-icons:${icon}"></iron-icon>
          <span class="name">${this.run.checkName}</span>
        </div>
        <div class="right">
          ${action
            ? html`<gr-button
                class="action"
                link
                @click="${(e: MouseEvent) => this.handleAction(e, action)}"
                >${action.name}</gr-button
              >`
            : ''}
        </div>
      </div>
    `;
  }

  private handleChipClick(e: MouseEvent) {
    e.stopPropagation();
    e.preventDefault();
    fireRunSelected(this, this.run.checkName);
  }

  private handleAction(e: MouseEvent, action: Action) {
    e.stopPropagation();
    e.preventDefault();
    fireActionTriggered(this, action, this.run);
  }
}

@customElement('gr-checks-runs')
export class GrChecksRuns extends GrLitElement {
  @property()
  runs: CheckRun[] = [];

  private selectedRuns = new Set<string>();

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
          padding: var(--spacing-xl);
        }
        .statusHeader {
          padding-top: var(--spacing-l);
          text-transform: capitalize;
        }
        .testing {
          margin-top: var(--spacing-xxl);
          color: var(--deemphasized-text-color);
        }
        .testing gr-button {
          min-width: 25px;
        }
        .testing * {
          visibility: hidden;
        }
        .testing:hover * {
          visibility: visible;
        }
      `,
    ];
  }

  render() {
    return html`
      <h2 class="heading-2">Runs</h2>
      ${this.renderSection(RunStatus.COMPLETED)}
      ${this.renderSection(RunStatus.RUNNING)}
      ${this.renderSection(RunStatus.RUNNABLE)}
      <div class="testing">
        <div>Toggle fake runs by clicking buttons:</div>
        <gr-button link @click="${this.none}">none</gr-button>
        <gr-button link @click="${() => this.toggle('f0', fakeRun0)}"
          >0</gr-button
        >
        <gr-button link @click="${() => this.toggle('f1', fakeRun1)}"
          >1</gr-button
        >
        <gr-button link @click="${() => this.toggle('f2', fakeRun2)}"
          >2</gr-button
        >
        <gr-button link @click="${() => this.toggle('f3', fakeRun3)}"
          >3</gr-button
        >
        <gr-button link @click="${() => this.toggle('f4', fakeRun4)}"
          >4</gr-button
        >
        <gr-button link @click="${this.all}">all</gr-button>
      </div>
    `;
  }

  none() {
    updateStateSetResults('f0', []);
    updateStateSetResults('f1', []);
    updateStateSetResults('f2', []);
    updateStateSetResults('f3', []);
    updateStateSetResults('f4', []);
  }

  all() {
    updateStateSetResults('f0', [fakeRun0]);
    updateStateSetResults('f1', [fakeRun1]);
    updateStateSetResults('f2', [fakeRun2]);
    updateStateSetResults('f3', [fakeRun3]);
    updateStateSetResults('f4', [fakeRun4]);
  }

  toggle(plugin: string, run: CheckRun) {
    const newRuns = this.runs.includes(run) ? [] : [run];
    updateStateSetResults(plugin, newRuns);
  }

  renderSection(status: RunStatus) {
    const runs = this.runs
      .filter(r => r.status === status)
      .sort(compareByWorstCategory);
    if (runs.length === 0) return;
    return html`
      <div class="${status.toLowerCase()}">
        <h3 class="statusHeader heading-3">${status.toLowerCase()}</h3>
        ${runs.map(run => this.renderRun(run))}
      </div>
    `;
  }

  renderRun(run: CheckRun) {
    const selected = this.selectedRuns.has(run.checkName);
    return html`<gr-checks-run
      .run="${run}"
      .selected="${selected}"
      @run-selected="${this.handleRunSelected}"
    ></gr-checks-run>`;
  }

  handleRunSelected(e: RunSelectedEvent) {
    const checkName = e.detail.checkName;
    if (this.selectedRuns.has(checkName)) {
      this.selectedRuns.delete(checkName);
    } else {
      this.selectedRuns.add(checkName);
    }
    this.requestUpdate();
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-run': GrChecksRun;
    'gr-checks-runs': GrChecksRuns;
  }
}
