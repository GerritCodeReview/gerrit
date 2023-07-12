/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../shared/gr-icon/gr-icon';
import {classMap} from 'lit/directives/class-map.js';
import './gr-hovercard-run';
import {css, html, LitElement, nothing, PropertyValues} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import './gr-checks-attempt';
import {Action, Link, RunStatus} from '../../api/checks';
import {sharedStyles} from '../../styles/shared-styles';
import {
  ALL_ATTEMPTS,
  AttemptChoice,
  attemptChoiceLabel,
  LATEST_ATTEMPT,
  AttemptDetail,
  compareByWorstCategory,
  headerForStatus,
  iconFor,
  iconForRun,
  PRIMARY_STATUS_ACTIONS,
  primaryRunAction,
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
import {fireRunSelected, RunSelectedEvent} from './gr-checks-util';
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
import {when} from 'lit/directives/when.js';
import {changeViewModelToken} from '../../models/views/change';

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
        :host([condensed]) .eta,
        :host([condensed]) .middle,
        :host([condensed]) .right {
          display: none;
        }
        :host([condensed]) * {
          pointer-events: none;
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
        .chip.info {
          border-left: var(--thick-border) solid var(--info-foreground);
        }
        .chip.check_circle {
          border-left: var(--thick-border) solid var(--success-foreground);
        }
        .chip.timelapse,
        .chip.pending_actions {
          border-left: var(--thick-border) solid var(--border-color);
        }
        .chip.placeholder {
          border-left: var(--thick-border) solid var(--border-color);
        }
        .chip.placeholder gr-icon {
          display: none;
        }
        gr-icon.error {
          color: var(--error-foreground);
        }
        gr-icon.warning {
          color: var(--warning-foreground);
        }
        gr-icon.info {
          color: var(--info-foreground);
        }
        gr-icon.check_circle {
          color: var(--success-foreground);
        }
        :host(:not([condensed])) div.chip:hover {
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
        div.chip.selected gr-icon.filter {
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

  @state()
  selectedAttempt: AttemptChoice = LATEST_ATTEMPT;

  @property({attribute: false})
  deselected = false;

  @property({type: Boolean})
  condensed = false;

  @state()
  shouldRender = false;

  private readonly reporting = getAppContext().reportingService;

  private getChecksModel = resolve(this, checksModelToken);

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChecksModel().checksSelectedAttemptNumber$,
      x => (this.selectedAttempt = x)
    );
  }

  override firstUpdated() {
    assertIsDefined(this.chipElement, 'chip element');
    whenVisible(this.chipElement, () => (this.shouldRender = true), 200);
  }

  override render() {
    if (!this.shouldRender) return html`<div class="chip">Loading ...</div>`;

    const icon = iconForRun(this.run);
    const classes = {
      chip: true,
      [icon.name]: true,
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
        <div class="left" tabindex="0">
          <gr-hovercard-run .run=${this.run}></gr-hovercard-run>
          ${this.renderFilterIcon()}
          <gr-icon
            class=${icon.name}
            icon=${icon.name}
            ?filled=${icon.filled}
          ></gr-icon>
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
        ${this.renderAttempt({attempt: LATEST_ATTEMPT})}
        ${this.renderAttempt({attempt: ALL_ATTEMPTS})}
        ${this.run.attemptDetails.map(a => this.renderAttempt(a))}
      </div>
    `;
  }

  renderAttempt(detail: AttemptDetail) {
    const attempt = detail.attempt ?? 0;
    const checkNameId = charsOnly(this.run.checkName).toLowerCase();
    const id = `attempt-${detail.attempt}`;
    const icon = detail.icon ?? {name: ''};
    const wasNotRun =
      icon?.name === iconFor(RunStatus.RUNNABLE)?.name &&
      attempt !== LATEST_ATTEMPT &&
      attempt !== ALL_ATTEMPTS;
    const selected = this.selectedAttempt === attempt;
    return html`<div class="attemptDetail">
      <input
        type="radio"
        id=${id}
        name=${`${checkNameId}-attempt-choice`}
        .checked=${selected}
        ?disabled=${!selected && wasNotRun}
        @change=${() => this.handleAttemptChange(attempt)}
      />
      <gr-icon
        icon=${icon.name}
        class=${icon.name}
        ?filled=${icon.filled}
      ></gr-icon>
      <label for=${id}>
        ${attemptChoiceLabel(attempt)}${wasNotRun ? ' (not run)' : ''}
      </label>
    </div>`;
  }

  handleAttemptChange(attempt: AttemptChoice) {
    this.getChecksModel().updateStateSetAttempt(attempt);
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
      <a
        href=${link}
        target="_blank"
        rel="noopener noreferrer"
        @click=${this.onLinkClick}
        ><gr-icon
          icon="open_in_new"
          class="statusLinkIcon"
          aria-label="external link to run status details"
        ></gr-icon>
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
    return html`<gr-icon icon="filter_alt" filled class="filter"></gr-icon>`;
  }

  /**
   * For RUNNING we also want to render an icon representing the worst result
   * that has been reported until now - if there are any results already.
   */
  renderAdditionalIcon() {
    if (this.run.status !== RunStatus.RUNNING) return nothing;
    const category = this.run.worstCategory;
    if (!category) return nothing;
    const icon = iconFor(category);
    return html`
      <gr-icon
        icon=${icon.name}
        class=${icon.name}
        ?filled=${icon.filled}
      ></gr-icon>
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
    if (e.key !== 'Enter' && e.key !== ' ') return;
    e.preventDefault();
    e.stopPropagation();
    fireRunSelected(this, this.run.checkName);
  }
}

@customElement('gr-checks-runs')
export class GrChecksRuns extends LitElement {
  @query('#filterInput')
  filterInput?: HTMLInputElement;

  @state()
  filterRegExp = '';

  @property({attribute: false})
  runs: CheckRun[] = [];

  @property({type: Boolean, reflect: true})
  collapsed = false;

  @state()
  selectedRuns: Set<string> = new Set();

  @state()
  selectedAttempt: AttemptChoice = LATEST_ATTEMPT;

  @property({attribute: false})
  tabState?: ChecksTabState;

  @state()
  errorMessages: ErrorMessages = {};

  @state()
  loginCallback?: () => void;

  private isSectionExpanded = new Map<RunStatus, boolean>();

  private flagService = getAppContext().flagsService;

  private getChecksModel = resolve(this, checksModelToken);

  private readonly getViewModel = resolve(this, changeViewModelToken);

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
    subscribe(
      this,
      () => this.getChecksModel().checksSelectedAttemptNumber$,
      x => (this.selectedAttempt = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().runFilterRegexp$,
      x => (this.filterRegExp = x)
    );
    subscribe(
      this,
      () => this.getViewModel().checksRunsSelected$,
      x => (this.selectedRuns = x)
    );
    this.addEventListener('click', () => {
      if (this.collapsed) this.toggleCollapsed();
    });
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
          width: 20%;
          padding: var(--spacing-l) var(--spacing-xl) var(--spacing-xl)
            var(--spacing-xl);
        }
        :host([collapsed]) {
          width: 90px;
          padding: var(--spacing-l) var(--spacing-l) var(--spacing-xl)
            var(--spacing-l);
          max-height: 600px;
          overflow: hidden;
        }
        :host([collapsed]) * {
          pointer-events: none;
        }
        :host([collapsed]:hover) {
          cursor: pointer;
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
        :host .expandButton {
          margin-right: calc(0px - var(--spacing-m));
        }
        :host([collapsed]:hover) .expandButton {
          background: var(--gray-background-hover);
          border-radius: var(--border-radius);
        }
        .sectionHeader {
          padding-top: var(--spacing-l);
          text-transform: capitalize;
          cursor: default;
        }
        :host([collapsed]) .sectionHeader {
          cursor: pointer;
        }
        .sectionHeader h3 {
          display: inline-block;
        }
        :host(:not([collapsed])) .collapsed .sectionRuns {
          display: none;
        }
        :host(:not([collapsed])) .collapsed {
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
        .error gr-icon {
          color: var(--error-foreground);
          margin-right: var(--spacing-m);
        }
        .login {
          background: var(--info-background);
        }
        .login gr-icon {
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

  override render() {
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
        .value=${this.filterRegExp}
        @input=${this.onInput}
      />
      ${this.renderSection(RunStatus.RUNNING)}
      ${this.renderSection(RunStatus.COMPLETED)}
      ${this.renderSection(RunStatus.RUNNABLE)} ${this.renderFakeControls()}
    `;
  }

  private renderZeroState() {
    if (this.collapsed) return;
    if (this.runs.length > 0) return;
    return html`<div class="zero">No Check Run to show</div>`;
  }

  private renderErrors() {
    return Object.entries(this.errorMessages).map(([plugin, message]) => {
      const msg = this.collapsed
        ? 'Error'
        : html`Error while fetching results for ${plugin}:<br />${message}`;
      return html`
        <div class="error">
          <div class="left">
            <gr-icon icon="error" filled></gr-icon>
          </div>
          <div class="right">
            <div class="message">${msg}</div>
          </div>
        </div>
      `;
    });
  }

  private renderSignIn() {
    if (!this.loginCallback) return;
    const message = this.collapsed
      ? 'Sign in'
      : 'Sign in to Checks Plugin to see runs and results';
    return html`
      <div class="login">
        <div>
          <gr-icon icon="info"></gr-icon>
          ${message}
        </div>
        <div class="buttonRow">
          <gr-button @click=${this.loginCallback} link>Sign in</gr-button>
        </div>
      </div>
    `;
  }

  private renderTitleButtons() {
    if (this.collapsed) return;
    if (this.selectedRuns.size < 2) return;
    const actions = [...this.selectedRuns].map(selected => {
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
        @click=${() =>
          this.getViewModel().updateState({checksRunsSelected: undefined})}
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
          class="expandButton font-normal"
          role="switch"
          aria-checked=${this.collapsed ? 'true' : 'false'}
          aria-label=${this.collapsed
            ? 'Expand runs panel'
            : 'Collapse runs panel'}
          @click=${this.toggleCollapsed}
        >
          <div>
            <gr-icon
              icon=${this.collapsed ? 'chevron_right' : 'chevron_left'}
              class="expandIcon"
            >
            </gr-icon>
          </div>
        </gr-button>
      </gr-tooltip-content>
    `;
  }

  private toggleCollapsed(event?: Event) {
    if (event) event.stopPropagation();
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
    const value = this.filterInput.value;
    this.getChecksModel().updateStateSetRunFilter(value ?? '');
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
    const regExp = new RegExp(this.filterRegExp, 'i');
    const runs = this.runs
      .filter(r => r.isLatestAttempt)
      .filter(
        r =>
          r.status === status ||
          (status === RunStatus.RUNNING && r.status === RunStatus.SCHEDULED)
      )
      .filter(r => regExp.test(r.checkName))
      .sort(compareByWorstCategory);
    if (runs.length === 0) return;
    const expanded = this.isSectionExpanded.get(status) ?? true;
    const expandedClass = expanded ? 'expanded' : 'collapsed';
    const icon = expanded ? 'expand_less' : 'expand_more';
    let header = headerForStatus(status);
    if (runs.some(r => r.status === RunStatus.SCHEDULED)) {
      header = `${header} / ${headerForStatus(RunStatus.SCHEDULED)}`;
    }
    const count = when(!this.collapsed, () => html` (${runs.length})`);
    const grIcon = when(
      !this.collapsed,
      () => html`<gr-icon icon=${icon} class="expandIcon"></gr-icon>`
    );
    return html`
      <div class="${status.toLowerCase()} ${expandedClass}">
        <div class="sectionHeader" @click=${() => this.toggleExpanded(status)}>
          ${grIcon}
          <h3 class="heading-3">${header}${count}</h3>
        </div>
        <div class="sectionRuns">${runs.map(run => this.renderRun(run))}</div>
      </div>
    `;
  }

  toggleExpanded(status: RunStatus) {
    if (this.collapsed) return;
    const expanded = this.isSectionExpanded.get(status) ?? true;
    this.isSectionExpanded.set(status, !expanded);
    this.reporting.reportInteraction(Interaction.CHECKS_RUN_SECTION_TOGGLE, {
      status,
      expanded: !expanded,
    });
    this.requestUpdate();
  }

  renderRun(run: CheckRun) {
    const selectedRun = this.selectedRuns.has(run.checkName);
    const deselected = !selectedRun && this.selectedRuns.size > 0;
    return html`<gr-checks-run
      .run=${run}
      ?condensed=${this.collapsed}
      .selected=${selectedRun}
      .deselected=${deselected}
      @run-selected=${this.handleRunSelected}
    ></gr-checks-run>`;
  }

  handleRunSelected(e: RunSelectedEvent) {
    if (e.detail.checkName) {
      this.getViewModel().toggleSelectedCheckRun(e.detail.checkName);
    }
  }

  showFilter(): boolean {
    if (this.collapsed) return false;
    return this.runs.length > 10 || !!this.filterRegExp;
  }

  renderFakeControls() {
    if (!this.flagService.isEnabled(KnownExperimentId.CHECKS_DEVELOPER)) return;
    if (this.collapsed) return;
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
