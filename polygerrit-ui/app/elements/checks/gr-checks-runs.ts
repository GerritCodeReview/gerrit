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
import '@polymer/iron-icon/iron-icon';
import {classMap} from 'lit/directives/class-map';
import './gr-hovercard-run';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators';
import './gr-checks-attempt';
import {Action, Link, RunStatus} from '../../api/checks';
import {sharedStyles} from '../../styles/shared-styles';
import {
  AttemptDetail,
  compareByWorstCategory,
  headerForStatus,
  iconFor,
  iconForRun,
  PRIMARY_STATUS_ACTIONS,
  primaryRunAction,
  worstCategory,
} from '../../models/checks/checks-util';
import {
  CheckRun,
  ChecksPatchset,
  ErrorMessages,
} from '../../models/checks/checks-model';
import {
  clearAllFakeRuns,
  fakeActions,
  fakeLinks,
  fakeRun0,
  fakeRun1,
  fakeRun2,
  fakeRun3,
  fakeRun4Att,
  fakeRun5,
  setAllFakeRuns,
} from '../../models/checks/checks-fakes';
import {assertIsDefined} from '../../utils/common-util';
import {modifierPressed, whenVisible} from '../../utils/dom-util';
import {
  fireAttemptSelected,
  fireRunSelected,
  fireRunSelectionReset,
} from './gr-checks-util';
import {ChecksTabState} from '../../types/events';
import {charsOnly} from '../../utils/string-util';
import {getAppContext} from '../../services/app-context';
import {KnownExperimentId} from '../../services/flags/flags';
import {subscribe} from '../lit/subscription-controller';
import {fontStyles} from '../../styles/gr-font-styles';
import {durationString} from '../../utils/date-util';
import {resolve} from '../../models/dependency';
import {checksModelToken} from '../../models/checks/checks-model';
import {Interaction} from '../../constants/reporting';
import {Deduping} from '../../api/reporting';

@customElement('gr-checks-run')
export class GrChecksRun extends LitElement {
  static override get styles() {
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
          flex-shrink: 1;
        }
        .middle {
          /* extra space must go between middle and right */
          flex-grow: 1;
          white-space: nowrap;
        }
        .middle gr-checks-attempt {
          margin-left: var(--spacing-s);
        }
        .name {
          font-weight: var(--font-weight-bold);
        }
        .eta {
          color: var(--deemphasized-text-color);
          padding-left: var(--spacing-s);
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
        .chip.timelapse,
        .chip.scheduled {
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
        div.chip:hover {
          background-color: var(--hover-background-color);
        }
        div.chip:focus-within {
          background-color: var(--selection-background-color);
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
        gr-checks-action {
          /* The button should fit into the 20px line-height. The negative
             margin provides the extra space needed for the vertical padding.
             Alternatively we could have set the vertical padding to 0, but
             that would not have been a nice click target. */
          margin: calc(0px - var(--spacing-s));
          margin-left: var(--spacing-s);
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
        .statusLinkIcon {
          color: var(--link-color);
          margin-left: var(--spacing-s);
        }
      `,
    ];
  }

  @query('.chip')
  chipElement?: HTMLElement;

  @property({attribute: false})
  run!: CheckRun;

  @property({attribute: false})
  selected = false;

  @property({attribute: false})
  selectedAttempt?: number;

  @property({attribute: false})
  deselected = false;

  @state()
  shouldRender = false;

  private readonly reporting = getAppContext().reportingService;

  override firstUpdated() {
    assertIsDefined(this.chipElement, 'chip element');
    whenVisible(this.chipElement, () => (this.shouldRender = true), 200);
  }

  protected override updated(changedProperties: PropertyValues) {
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

  override render() {
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
      <div
        @click=${this.handleChipClick}
        @keydown=${this.handleChipKey}
        class=${classMap(classes)}
        tabindex="0"
      >
        <div class="left">
          <gr-hovercard-run .run=${this.run}></gr-hovercard-run>
          ${this.renderFilterIcon()}
          <iron-icon class=${icon} icon="gr-icons:${icon}"></iron-icon>
          ${this.renderAdditionalIcon()}
          <span class="name">${this.run.checkName}</span>
          ${this.renderETA()}
        </div>
        <div class="middle">
          <gr-checks-attempt .run=${this.run}></gr-checks-attempt>
          ${this.renderStatusLink()}
        </div>
        <div class="right">
          ${action
            ? html`<gr-checks-action
                context="runs"
                .action=${action}
              ></gr-checks-action>`
            : ''}
        </div>
      </div>
      <div
        class="attemptDetails"
        ?hidden=${this.run.isSingleAttempt || !this.selected}
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
    const wasNotRun = icon === iconFor(RunStatus.RUNNABLE);
    return html`<div class="attemptDetail">
      <input
        type="radio"
        id=${id}
        name=${`${checkNameId}-attempt-choice`}
        ?checked=${this.isSelected(detail)}
        ?disabled=${!this.isSelected(detail) && wasNotRun}
        @change=${() => this.handleAttemptChange(detail)}
      />
      <iron-icon class=${icon} icon="gr-icons:${icon}"></iron-icon>
      <label for=${id}>
        Attempt ${detail.attempt}${wasNotRun ? ' (not run)' : ''}
      </label>
    </div>`;
  }

  handleAttemptChange(detail: AttemptDetail) {
    if (!this.isSelected(detail)) {
      fireAttemptSelected(this, this.run.checkName, detail.attempt);
    }
  }

  renderETA() {
    if (this.run.status !== RunStatus.RUNNING) return;
    if (!this.run.finishedTimestamp) return;
    const now = new Date();
    if (this.run.finishedTimestamp.getTime() < now.getTime()) return;
    const eta = durationString(new Date(), this.run.finishedTimestamp, true);
    return html`<span class="eta">ETA: ${eta}</span>`;
  }

  renderStatusLink() {
    const link = this.run.statusLink;
    if (!link) return;
    return html`
      <a href=${link} target="_blank" @click=${this.onLinkClick}
        ><iron-icon
          class="statusLinkIcon"
          icon="gr-icons:launch"
          aria-label="external link to run status details"
        ></iron-icon>
        <paper-tooltip offset="5">Link to run status details</paper-tooltip>
      </a>
    `;
  }

  private onLinkClick(e: MouseEvent) {
    // Prevents handleChipClick() from reacting to <a> link clicks.
    e.stopPropagation();
    this.reporting.reportInteraction(Interaction.CHECKS_RUN_LINK_CLICKED, {
      checkName: this.run.checkName,
      status: this.run.status,
    });
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
    const icon = iconFor(category);
    return html`
      <iron-icon class=${icon} icon="gr-icons:${icon}"></iron-icon>
    `;
  }

  private handleChipClick(e: MouseEvent) {
    e.stopPropagation();
    e.preventDefault();
    fireRunSelected(this, this.run.checkName);
  }

  private handleChipKey(e: KeyboardEvent) {
    if (modifierPressed(e)) return;
    // Only react to `return` and `space`.
    if (e.keyCode !== 13 && e.keyCode !== 32) return;
    e.preventDefault();
    e.stopPropagation();
    fireRunSelected(this, this.run.checkName);
  }
}

@customElement('gr-checks-runs')
export class GrChecksRuns extends LitElement {
  @query('#filterInput')
  filterInput?: HTMLInputElement;

  /**
   * We prefer `undefined` over a RegExp with '', because `.source` yields
   * a strange '(?:)' for ''.
   */
  @state()
  filterRegExp?: RegExp;

  @property({attribute: false})
  runs: CheckRun[] = [];

  @property({type: Boolean, reflect: true})
  collapsed = false;

  @property({attribute: false})
  selectedRuns: string[] = [];

  /** Maps checkName to selected attempt number. `undefined` means `latest`. */
  @property({attribute: false})
  selectedAttempts: Map<string, number | undefined> = new Map<
    string,
    number | undefined
  >();

  @property({attribute: false})
  tabState?: ChecksTabState;

  @state()
  errorMessages: ErrorMessages = {};

  @state()
  loginCallback?: () => void;

  private isSectionExpanded = new Map<RunStatus, boolean>();

  private flagService = getAppContext().flagsService;

  private getChecksModel = resolve(this, checksModelToken);

  private readonly reporting = getAppContext().reportingService;

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChecksModel().allRunsSelectedPatchset$,
      x => (this.runs = x)
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
  }

  static override get styles() {
    return [
      sharedStyles,
      fontStyles,
      css`
        :host {
          display: block;
        }
        :host(:not([collapsed])) {
          min-width: 320px;
          padding: var(--spacing-l) var(--spacing-xl) var(--spacing-xl)
            var(--spacing-xl);
        }
        :host([collapsed]) {
          padding: var(--spacing-l) 0;
        }
        .title {
          display: flex;
        }
        .title .flex-space {
          flex-grow: 1;
        }
        .title gr-button {
          --gr-button-padding: var(--spacing-s) var(--spacing-m);
          white-space: nowrap;
        }
        .title gr-button.expandButton {
          --gr-button-padding: var(--spacing-xs) var(--spacing-s);
        }
        :host(:not([collapsed])) .expandButton {
          margin-right: calc(0px - var(--spacing-m));
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
        .zero {
          padding: var(--spacing-m) 0;
          color: var(--primary-text-color);
          margin-top: var(--spacing-m);
        }
        .login,
        .error {
          padding: var(--spacing-m);
          color: var(--primary-text-color);
          margin-top: var(--spacing-m);
          max-width: 400px;
        }
        .error {
          display: flex;
          background-color: var(--error-background);
        }
        .error iron-icon {
          color: var(--error-foreground);
          margin-right: var(--spacing-m);
        }
        .login {
          background: var(--info-background);
        }
        .login iron-icon {
          color: var(--info-foreground);
        }
        .login .buttonRow {
          text-align: right;
          margin-top: var(--spacing-xl);
        }
        .login gr-button {
          margin: 0 var(--spacing-s);
        }
      `,
    ];
  }

  protected override updated(changedProperties: PropertyValues) {
    super.updated(changedProperties);
    // This update is done is response to setting this.filterRegExp below, but
    // this.filterInput not yet being available at that point.
    if (this.filterInput && !this.filterInput.value && this.filterRegExp) {
      this.filterInput.value = this.filterRegExp.source;
    }
    if (changedProperties.has('tabState') && this.tabState) {
      // Note that tabState.select and tabState.attempt are processed by
      // <gr-checks-tab>.
      if (
        this.tabState.filter &&
        this.tabState.filter !== this.filterRegExp?.source
      ) {
        this.filterRegExp = new RegExp(this.tabState.filter, 'i');
      }
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

  override render() {
    if (this.collapsed) {
      return html`${this.renderCollapseButton()}`;
    }
    return html`
      <h2 class="title">
        <div class="heading-2">Runs</div>
        <div class="flex-space"></div>
        ${this.renderTitleButtons()} ${this.renderCollapseButton()}
      </h2>
      ${this.renderErrors()} ${this.renderSignIn()} ${this.renderZeroState()}
      <input
        id="filterInput"
        type="text"
        placeholder="Filter runs by regular expression"
        ?hidden=${!this.showFilter()}
        @input=${this.onInput}
      />
      ${this.renderSection(RunStatus.RUNNING)}
      ${this.renderSection(RunStatus.COMPLETED)}
      ${this.renderSection(RunStatus.RUNNABLE)} ${this.renderFakeControls()}
    `;
  }

  private renderZeroState() {
    if (this.runs.length > 0) return;
    return html`<div class="zero">No Check Run to show</div>`;
  }

  private renderErrors() {
    return Object.entries(this.errorMessages).map(
      ([plugin, message]) =>
        html`
          <div class="error">
            <div class="left">
              <iron-icon icon="gr-icons:error"></iron-icon>
            </div>
            <div class="right">
              <div class="message">
                Error while fetching results for ${plugin}:<br />${message}
              </div>
            </div>
          </div>
        `
    );
  }

  private renderSignIn() {
    if (!this.loginCallback) return;
    return html`
      <div class="login">
        <div>
          <iron-icon
            class="info-outline"
            icon="gr-icons:info-outline"
          ></iron-icon>
          Sign in to Checks Plugin to see runs and results
        </div>
        <div class="buttonRow">
          <gr-button @click=${this.loginCallback} link>Sign in</gr-button>
        </div>
      </div>
    `;
  }

  private renderTitleButtons() {
    if (this.selectedRuns.length < 2) return;
    const actions = this.selectedRuns.map(selected => {
      const run = this.runs.find(
        run => run.isLatestAttempt && run.checkName === selected
      );
      return primaryRunAction(run);
    });
    const runButtonDisabled = !actions.every(
      action =>
        action?.name === PRIMARY_STATUS_ACTIONS.RUN ||
        action?.name === PRIMARY_STATUS_ACTIONS.RERUN
    );
    return html`
      <gr-button
        class="font-normal"
        link
        @click=${() => fireRunSelectionReset(this)}
        >Unselect All</gr-button
      >
      <gr-tooltip-content
        title=${runButtonDisabled
          ? 'Disabled. Unselect checks without a "Run" action to enable the button.'
          : ''}
        ?has-tooltip=${runButtonDisabled}
      >
        <gr-button
          class="font-normal"
          link
          ?disabled=${runButtonDisabled}
          @click=${() => {
            actions.forEach(action => {
              if (!action) return;
              this.getChecksModel().triggerAction(
                action,
                undefined,
                'run-selected'
              );
            });
            this.reporting.reportInteraction(
              Interaction.CHECKS_RUNS_SELECTED_TRIGGERED
            );
          }}
          >Run Selected</gr-button
        >
      </gr-tooltip-content>
    `;
  }

  private renderCollapseButton() {
    return html`
      <gr-tooltip-content
        has-tooltip
        title=${this.collapsed ? 'Expand runs panel' : 'Collapse runs panel'}
      >
        <gr-button
          link
          class="expandButton"
          role="switch"
          aria-checked=${this.collapsed ? 'true' : 'false'}
          aria-label=${this.collapsed
            ? 'Expand runs panel'
            : 'Collapse runs panel'}
          @click=${this.toggleCollapsed}
          ><iron-icon
            class="expandIcon"
            icon=${this.collapsed
              ? 'gr-icons:chevron-right'
              : 'gr-icons:chevron-left'}
          ></iron-icon>
        </gr-button>
      </gr-tooltip-content>
    `;
  }

  private toggleCollapsed() {
    this.collapsed = !this.collapsed;
    this.reporting.reportInteraction(Interaction.CHECKS_RUNS_PANEL_TOGGLE, {
      collapsed: this.collapsed,
    });
  }

  onInput() {
    assertIsDefined(this.filterInput, 'filter <input> element');
    this.reporting.reportInteraction(
      Interaction.CHECKS_RUN_FILTER_CHANGED,
      {},
      {deduping: Deduping.EVENT_ONCE_PER_CHANGE}
    );
    if (this.filterInput.value) {
      this.filterRegExp = new RegExp(this.filterInput.value, 'i');
    } else {
      this.filterRegExp = undefined;
    }
  }

  toggle(
    plugin: string,
    runs: CheckRun[],
    actions: Action[] = [],
    links: Link[] = [],
    summaryMessage: string | undefined = undefined
  ) {
    const newRuns = this.runs.includes(runs[0]) ? [] : runs;
    this.getChecksModel().updateStateSetResults(
      plugin,
      newRuns,
      actions,
      links,
      summaryMessage,
      ChecksPatchset.LATEST
    );
  }

  renderSection(status: RunStatus) {
    const runs = this.runs
      .filter(r => r.isLatestAttempt)
      .filter(
        r =>
          r.status === status ||
          (status === RunStatus.RUNNING && r.status === RunStatus.SCHEDULED)
      )
      .filter(r => !this.filterRegExp || this.filterRegExp.test(r.checkName))
      .sort(compareByWorstCategory);
    if (runs.length === 0) return;
    const expanded = this.isSectionExpanded.get(status) ?? true;
    const expandedClass = expanded ? 'expanded' : 'collapsed';
    const icon = expanded ? 'gr-icons:expand-less' : 'gr-icons:expand-more';
    let header = headerForStatus(status);
    if (runs.some(r => r.status === RunStatus.SCHEDULED)) {
      header = `${header} / ${headerForStatus(RunStatus.SCHEDULED)}`;
    }
    return html`
      <div class="${status.toLowerCase()} ${expandedClass}">
        <div class="sectionHeader" @click=${() => this.toggleExpanded(status)}>
          <iron-icon class="expandIcon" icon=${icon}></iron-icon>
          <h3 class="heading-3">${header}</h3>
        </div>
        <div class="sectionRuns">${runs.map(run => this.renderRun(run))}</div>
      </div>
    `;
  }

  toggleExpanded(status: RunStatus) {
    const expanded = this.isSectionExpanded.get(status) ?? true;
    this.isSectionExpanded.set(status, !expanded);
    this.reporting.reportInteraction(Interaction.CHECKS_RUN_SECTION_TOGGLE, {
      status,
      expanded: !expanded,
    });
    this.requestUpdate();
  }

  renderRun(run: CheckRun) {
    const selectedRun = this.selectedRuns.includes(run.checkName);
    const selectedAttempt = this.selectedAttempts.get(run.checkName);
    const deselected = !selectedRun && this.selectedRuns.length > 0;
    return html`<gr-checks-run
      .run=${run}
      .selected=${selectedRun}
      .selectedAttempt=${selectedAttempt}
      .deselected=${deselected}
    ></gr-checks-run>`;
  }

  showFilter(): boolean {
    return this.runs.length > 10 || !!this.filterRegExp;
  }

  renderFakeControls() {
    if (!this.flagService.isEnabled(KnownExperimentId.CHECKS_DEVELOPER)) return;
    return html`
      <div class="testing">
        <div>Toggle fake runs by clicking buttons:</div>
        <gr-button link @click=${() => clearAllFakeRuns(this.getChecksModel())}
          >none</gr-button
        >
        <gr-button
          link
          @click=${() =>
            this.toggle('f0', [fakeRun0], fakeActions, fakeLinks, 'ETA: 1 min')}
          >0</gr-button
        >
        <gr-button link @click=${() => this.toggle('f1', [fakeRun1])}
          >1</gr-button
        >
        <gr-button link @click=${() => this.toggle('f2', [fakeRun2])}
          >2</gr-button
        >
        <gr-button link @click=${() => this.toggle('f3', [fakeRun3])}
          >3</gr-button
        >
        <gr-button link @click="${() => this.toggle('f4', fakeRun4Att)}}"
          >4</gr-button
        >
        <gr-button link @click=${() => this.toggle('f5', [fakeRun5])}
          >5</gr-button
        >
        <gr-button link @click=${() => setAllFakeRuns(this.getChecksModel())}
          >all</gr-button
        >
      </div>
    `;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-checks-run': GrChecksRun;
    'gr-checks-runs': GrChecksRuns;
  }
}
