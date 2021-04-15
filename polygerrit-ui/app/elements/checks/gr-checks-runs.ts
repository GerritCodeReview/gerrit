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
import {html, nothing} from 'lit-html';
import {classMap} from 'lit-html/directives/class-map';
import './gr-hovercard-run';
import {
  css,
  customElement,
  internalProperty,
  property,
  PropertyValues,
  query,
} from 'lit-element';
import {GrLitElement} from '../lit/gr-lit-element';
import {Action, RunStatus} from '../../api/checks';
import {sharedStyles} from '../../styles/shared-styles';
import {
  AttemptDetail,
  compareByWorstCategory,
  fireActionTriggered,
  iconForCategory,
  iconForRun,
  primaryRunAction,
  worstCategory,
} from '../../services/checks/checks-util';
import {
  CheckRun,
  allRuns$,
  fakeActions,
  fakeRun0,
  fakeRun1,
  fakeRun2,
  fakeRun3,
  fakeRun4_1,
  fakeRun4_2,
  fakeRun4_3,
  fakeRun4_4,
  updateStateSetResults,
} from '../../services/checks/checks-model';
import {assertIsDefined} from '../../utils/common-util';
import {whenVisible} from '../../utils/dom-util';
import {fireAttemptSelected, fireRunSelected} from './gr-checks-util';
import {ChecksTabState} from '../../types/events';
import {charsOnly} from '../../utils/string-util';

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
          cursor: pointer;
        }
        .left {
          overflow: hidden;
          white-space: nowrap;
          text-overflow: ellipsis;
        }
        .name {
          font-weight: var(--font-weight-bold);
        }
        .attempt {
          display: inline-block;
          background-color: var(--tag-gray);
          border-radius: var(--line-height-normal);
          height: var(--line-height-normal);
          width: var(--line-height-normal);
          text-align: center;
          vertical-align: top;
          font-size: var(--font-size-small);
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
        .chip.placeholder iron-icon {
          display: none;
        }
        iron-icon.error {
          color: var(--error-foreground);
        }
        iron-icon.warning {
          color: var(--warning-foreground);
        }
        iron-icon.info-outline {
          color: var(--info-foreground);
        }
        iron-icon.check-circle-outline {
          color: var(--success-foreground);
        }
        /* Additional 'div' for increased specificity. */
        div.chip.selected {
          border: 1px solid var(--selected-background);
          background-color: var(--selected-background);
          padding-left: calc(var(--spacing-m) + var(--thick-border) - 1px);
        }
        div.chip.selected .name,
        div.chip.selected iron-icon.filter {
          color: var(--selected-foreground);
        }
        .chip.selected gr-button.action,
        .chip.deselected gr-button.action {
          display: none;
        }
        gr-button.action {
          --padding: var(--spacing-xs) var(--spacing-m);
          /* The button should fit into the 20px line-height. The negative
             margin provides the extra space needed for the vertical padding.
             Alternatively we could have set the vertical padding to 0, but
             that would not have been a nice click target. */
          margin: calc(0px - var(--spacing-xs));
        }
        .attemptDetails {
          padding-bottom: var(--spacing-s);
        }
        .attemptDetail {
          /* This is thick-border (6) + spacing-m (8) + icon (20) + padding. */
          padding-left: 39px;
          padding-top: var(--spacing-s);
        }
        .attemptDetail input {
          width: 14px;
          height: 14px;
          /* The next 3 are for placing in the middle of 20px line-height. */
          vertical-align: top;
          position: relative;
          top: 3px;
          margin-right: var(--spacing-s);
        }
      `,
    ];
  }

  @query('.chip')
  chipElement?: HTMLElement;

  @property()
  run!: CheckRun;

  @property()
  selected = false;

  @property()
  selectedAttempt?: number;

  @property()
  deselected = false;

  @property()
  shouldRender = false;

  firstUpdated() {
    assertIsDefined(this.chipElement, 'chip element');
    whenVisible(
      this.chipElement,
      () => this.setAttribute('shouldRender', 'true'),
      200
    );
  }

  protected updated(changedProperties: PropertyValues) {
    super.updated(changedProperties);

    // For some reason the browser does not pick up the correct `checked` state
    // that is set in renderAttempt(). So we have to set it programmatically
    // here.
    const selectedAttempt = this.selectedAttempt ?? this.run.attempt;
    const inputToBeSelected = this.shadowRoot?.querySelector(
      `.attemptDetails input#attempt-${selectedAttempt}`
    ) as HTMLInputElement | undefined;
    if (inputToBeSelected) {
      inputToBeSelected.checked = true;
    }
  }

  render() {
    if (!this.shouldRender) return html`<div class="chip">Loading ...</div>`;

    const icon = iconForRun(this.run);
    const classes = {
      chip: true,
      [icon]: true,
      selected: this.selected,
      deselected: this.deselected,
    };
    const action = primaryRunAction(this.run);

    return html`
      <div @click="${this.handleChipClick}" class="${classMap(classes)}">
        <gr-hovercard-run .run="${this.run}"></gr-hovercard-run>
        <div class="left">
          ${this.renderFilterIcon()}
          <iron-icon class="${icon}" icon="gr-icons:${icon}"></iron-icon>
          ${this.renderAdditionalIcon()}
          <span class="name">${this.run.checkName}</span>
          <span class="attempt" ?hidden="${this.run.isSingleAttempt}"
            >${this.run.attempt}</span
          >
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
      <div
        class="attemptDetails"
        ?hidden="${this.run.isSingleAttempt || !this.selected}"
      >
        ${this.run.attemptDetails.map(a => this.renderAttempt(a))}
      </div>
    `;
  }

  isSelected(detail: AttemptDetail) {
    // this.selectedAttempt may be undefined, then choose the latest attempt,
    // which is what this.run has.
    const selectedAttempt = this.selectedAttempt ?? this.run.attempt;
    return detail.attempt === selectedAttempt;
  }

  renderAttempt(detail: AttemptDetail) {
    const checkNameId = charsOnly(this.run.checkName).toLowerCase();
    const id = `attempt-${detail.attempt}`;
    const icon = detail.icon;
    return html`<div class="attemptDetail">
      <input
        type="radio"
        id="${id}"
        name="${`${checkNameId}-attempt-choice`}"
        ?checked="${this.isSelected(detail)}"
        @change="${() => this.handleAttemptChange(detail)}"
      />
      <iron-icon class="${icon}" icon="gr-icons:${icon}"></iron-icon>
      <label for="${id}">Attempt ${detail.attempt}</label>
    </div>`;
  }

  handleAttemptChange(detail: AttemptDetail) {
    if (!this.isSelected(detail)) {
      fireAttemptSelected(this, this.run.checkName, detail.attempt);
    }
  }

  renderFilterIcon() {
    if (!this.selected) return;
    return html`
      <iron-icon class="filter" icon="gr-icons:filter"></iron-icon>
    `;
  }

  /**
   * For RUNNING we also want to render an icon representing the worst result
   * that has been reported until now - if there are any results already.
   */
  renderAdditionalIcon() {
    if (this.run.status !== RunStatus.RUNNING) return nothing;
    const category = worstCategory(this.run);
    if (!category) return nothing;
    const icon = iconForCategory(category);
    return html`
      <iron-icon class="${icon}" icon="gr-icons:${icon}"></iron-icon>
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
  @query('#filterInput')
  filterInput?: HTMLInputElement;

  @internalProperty()
  filterRegExp = new RegExp('');

  @property()
  runs: CheckRun[] = [];

  @property()
  selectedRuns: string[] = [];

  /** Maps checkName to selected attempt number. `undefined` means `latest`. */
  @property()
  selectedAttempts: Map<string, number | undefined> = new Map<
    string,
    number | undefined
  >();

  @property()
  tabState?: ChecksTabState;

  private isSectionExpanded = new Map<RunStatus, boolean>();

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
          padding: var(--spacing-l) var(--spacing-xl) var(--spacing-xl)
            var(--spacing-xl);
        }
        .expandIcon {
          width: var(--line-height-h3);
          height: var(--line-height-h3);
        }
        .sectionHeader {
          padding-top: var(--spacing-l);
          text-transform: capitalize;
          cursor: default;
        }
        .sectionHeader h3 {
          display: inline-block;
        }
        .collapsed .sectionRuns {
          display: none;
        }
        .collapsed {
          border-bottom: 1px solid var(--border-color);
          padding-bottom: var(--spacing-m);
        }
        input#filterInput {
          margin-top: var(--spacing-m);
          padding: var(--spacing-s) var(--spacing-m);
          width: 100%;
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

  protected updated(changedProperties: PropertyValues) {
    super.updated(changedProperties);
    if (changedProperties.has('tabState') && this.tabState) {
      const {statusOrCategory} = this.tabState;
      if (
        statusOrCategory === RunStatus.RUNNING ||
        statusOrCategory === RunStatus.RUNNABLE
      ) {
        this.updateComplete.then(() => {
          const s = statusOrCategory.toString().toLowerCase();
          const el = this.shadowRoot?.querySelector(`.${s} .sectionHeader`);
          el?.scrollIntoView({block: 'center'});
        });
      }
    }
  }

  render() {
    return html`
      <h2 class="heading-2">Runs</h2>
      <input
        id="filterInput"
        type="text"
        placeholder="Filter runs by regular expression"
        ?hidden="${!this.showFilter()}"
        @input="${this.onInput}"
      />
      ${this.renderSection(RunStatus.COMPLETED)}
      ${this.renderSection(RunStatus.RUNNING)}
      ${this.renderSection(RunStatus.RUNNABLE)}
      <div class="testing">
        <div>Toggle fake runs by clicking buttons:</div>
        <gr-button link @click="${this.none}">none</gr-button>
        <gr-button
          link
          @click="${() => this.toggle('f0', [fakeRun0], fakeActions)}"
          >0</gr-button
        >
        <gr-button link @click="${() => this.toggle('f1', [fakeRun1])}"
          >1</gr-button
        >
        <gr-button link @click="${() => this.toggle('f2', [fakeRun2])}"
          >2</gr-button
        >
        <gr-button link @click="${() => this.toggle('f3', [fakeRun3])}"
          >3</gr-button
        >
        <gr-button
          link
          @click="${() => {
            this.toggle('f4', [fakeRun4_1, fakeRun4_2, fakeRun4_3, fakeRun4_4]);
          }}"
          >4</gr-button
        >
        <gr-button link @click="${this.all}">all</gr-button>
      </div>
    `;
  }

  onInput() {
    assertIsDefined(this.filterInput, 'filter <input> element');
    this.filterRegExp = new RegExp(this.filterInput.value, 'i');
  }

  none() {
    updateStateSetResults('f0', [], []);
    updateStateSetResults('f1', []);
    updateStateSetResults('f2', []);
    updateStateSetResults('f3', []);
    updateStateSetResults('f4', []);
  }

  all() {
    updateStateSetResults('f0', [fakeRun0], fakeActions);
    updateStateSetResults('f1', [fakeRun1]);
    updateStateSetResults('f2', [fakeRun2]);
    updateStateSetResults('f3', [fakeRun3]);
    updateStateSetResults('f4', [
      fakeRun4_1,
      fakeRun4_2,
      fakeRun4_3,
      fakeRun4_4,
    ]);
  }

  toggle(plugin: string, runs: CheckRun[], actions: Action[] = []) {
    const newRuns = this.runs.includes(runs[0]) ? [] : runs;
    updateStateSetResults(plugin, newRuns, actions);
  }

  renderSection(status: RunStatus) {
    const runs = this.runs
      .filter(r => r.isLatestAttempt)
      .filter(r => r.status === status)
      .filter(r => this.filterRegExp.test(r.checkName))
      .sort(compareByWorstCategory);
    if (runs.length === 0) return;
    const expanded = this.isSectionExpanded.get(status) ?? true;
    const expandedClass = expanded ? 'expanded' : 'collapsed';
    const icon = expanded ? 'gr-icons:expand-less' : 'gr-icons:expand-more';
    return html`
      <div class="${status.toLowerCase()} ${expandedClass}">
        <div
          class="sectionHeader"
          @click="${() => this.toggleExpanded(status)}"
        >
          <iron-icon class="expandIcon" icon="${icon}"></iron-icon>
          <h3 class="heading-3">${status.toLowerCase()}</h3>
        </div>
        <div class="sectionRuns">${runs.map(run => this.renderRun(run))}</div>
      </div>
    `;
  }

  toggleExpanded(status: RunStatus) {
    const expanded = this.isSectionExpanded.get(status) ?? true;
    this.isSectionExpanded.set(status, !expanded);
    this.requestUpdate();
  }

  renderRun(run: CheckRun) {
    const selectedRun = this.selectedRuns.includes(run.checkName);
    const selectedAttempt = this.selectedAttempts.get(run.checkName);
    const deselected = !selectedRun && this.selectedRuns.length > 0;
    return html`<gr-checks-run
      .run="${run}"
      .selected="${selectedRun}"
      .selectedAttempt="${selectedAttempt}"
      .deselected="${deselected}"
    ></gr-checks-run>`;
  }

  showFilter(): boolean {
    const show = this.runs.length > 10;
    if (!show && this.filterRegExp.source.length > 0) {
      this.filterRegExp = new RegExp('');
    }
    return show;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-run': GrChecksRun;
    'gr-checks-runs': GrChecksRuns;
  }
}
