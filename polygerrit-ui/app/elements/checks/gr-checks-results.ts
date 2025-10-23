/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '../shared/gr-icon/gr-icon';
import {classMap} from 'lit/directives/class-map.js';
import {repeat} from 'lit/directives/repeat.js';
import {ifDefined} from 'lit/directives/if-defined.js';
import {
  css,
  html,
  LitElement,
  nothing,
  PropertyValues,
  TemplateResult,
} from 'lit';
import {customElement, property, query, state} from 'lit/decorators.js';
import './gr-checks-action';
import './gr-hovercard-run';
import '../shared/gr-tooltip-content/gr-tooltip-content';
import {
  Action,
  Category,
  Link,
  LinkIcon,
  NOT_USEFUL,
  RunStatus,
  Tag,
  USEFUL,
} from '../../api/checks';
import {sharedStyles} from '../../styles/shared-styles';
import {CheckRun, RunResult, runResult} from '../../models/checks/checks-model';
import {
  ALL_ATTEMPTS,
  allResults,
  AttemptChoice,
  attemptChoiceLabel,
  computeIsExpandable,
  createFixAction,
  createGetAiFixAction,
  firstPrimaryLink,
  hasCompletedWithoutResults,
  iconFor,
  iconForLink,
  isAttemptChoice,
  isCategory,
  LATEST_ATTEMPT,
  otherPrimaryLinks,
  rectifyFix,
  secondaryLinks,
  sortAttemptChoices,
  stringToAttemptChoice,
  tooltipForLink,
} from '../../models/checks/checks-util';
import {assert, assertIsDefined, unique} from '../../utils/common-util';
import {modifierPressed, whenVisible} from '../../utils/dom-util';
import {durationString} from '../../utils/date-util';
import {charsOnly} from '../../utils/string-util';
import {isAttemptSelected, matches} from './gr-checks-util';
import {ChecksTabState, ValueChangedEvent} from '../../types/events';
import {
  DropdownLink,
  LabelNameToInfoMap,
  PARENT,
  PatchSetNumber,
} from '../../types/common';
import {spinnerStyles} from '../../styles/gr-spinner-styles';
import {
  getLabelStatus,
  getRepresentativeValue,
  valueString,
} from '../../utils/label-util';
import {subscribe} from '../lit/subscription-controller';
import {fontStyles} from '../../styles/gr-font-styles';
import {fire, fireAlert} from '../../utils/event-util';
import {resolve} from '../../models/dependency';
import {checksModelToken} from '../../models/checks/checks-model';
import {Interaction} from '../../constants/reporting';
import {Deduping} from '../../api/reporting';
import {changeModelToken} from '../../models/change/change-model';
import {getAppContext} from '../../services/app-context';
import {when} from 'lit/directives/when.js';
import {DropdownItem} from '../shared/gr-dropdown-list/gr-dropdown-list';
import './gr-checks-attempt';
import './gr-checks-fix-preview';
import {changeViewModelToken} from '../../models/views/change';
import {formStyles} from '../../styles/form-styles';
import {isDefined} from '../../types/types';
import {
  ReportSource,
  suggestionsServiceToken,
} from '../../services/suggestions/suggestions-service';
import {FixSuggestionInfo, RevisionPatchSetNum} from '../../api/rest-api';

/**
 * Firing this event sets the regular expression of the results filter.
 */
export interface ChecksResultsFilterDetail {
  filterRegExp?: string;
}
export type ChecksResultsFilterEvent = CustomEvent<ChecksResultsFilterDetail>;

import './gr-result-row';
import {GrResultRow} from './gr-result-row';

declare global {
  interface HTMLElementEventMap {
    'checks-results-filter': ChecksResultsFilterEvent;
  }
}

const CATEGORY_TOOLTIPS: Map<Category, string> = new Map();
CATEGORY_TOOLTIPS.set(Category.ERROR, 'Must be fixed and is blocking submit');
CATEGORY_TOOLTIPS.set(
  Category.WARNING,
  'Should be checked but is not blocking submit'
);
CATEGORY_TOOLTIPS.set(
  Category.INFO,
  'Does not have to be checked, for your information only'
);
CATEGORY_TOOLTIPS.set(
  Category.SUCCESS,
  'Successful runs without results and individual successful results'
);

@customElement('gr-checks-results')
export class GrChecksResults extends LitElement {
  @query('#filterInput')
  filterInput?: HTMLInputElement;

  @state()
  filterRegExp = '';

  /** All runs. Shown should only the selected/filtered ones. */
  @property({attribute: false})
  runs: CheckRun[] = [];

  /**
   * Check names of runs that are selected in the runs panel. When this array
   * is empty, then no run is selected and all runs should be shown.
   */
  @state()
  selectedRuns: Set<string> = new Set();

  @state()
  actions: Action[] = [];

  @state()
  links: Link[] = [];

  @property({attribute: false})
  tabState?: ChecksTabState;

  @state()
  someProvidersAreLoading = false;

  @state()
  checksPatchsetNumber: PatchSetNumber | undefined = undefined;

  @state()
  latestPatchsetNumber: PatchSetNumber | undefined = undefined;

  @state()
  selectedAttempt: AttemptChoice = LATEST_ATTEMPT;

  /** Maintains the state of which result sections should show all results. */
  @state()
  isShowAll: Map<Category, boolean> = new Map();

  /** Maintains the state of which result sections has all results expanded. */
  @state()
  allResultsExpanded: Map<Category, boolean> = new Map();

  /**
   * This is the current state of whether a section is expanded or not. As long
   * as isSectionExpandedByUser is false this will be computed by a default rule
   * on every render.
   */
  private isSectionExpanded = new Map<Category, boolean>();

  /**
   * Keeps track of whether the user intentionally changed the expansion state.
   * Once this is true the default rule for showing a section expanded or not
   * is not applied anymore.
   */
  private isSectionExpandedByUser = new Map<Category, boolean>();

  private readonly getViewModel = resolve(this, changeViewModelToken);

  private readonly getChangeModel = resolve(this, changeModelToken);

  private readonly getChecksModel = resolve(this, checksModelToken);

  private readonly reporting = getAppContext().reportingService;

  constructor() {
    super();
    subscribe(
      this,
      () => this.getChecksModel().topLevelActionsSelected$,
      x => (this.actions = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().topLevelLinksSelected$,
      x => (this.links = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().checksSelectedPatchsetNumber$,
      x => (this.checksPatchsetNumber = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().checksSelectedAttemptNumber$,
      x => (this.selectedAttempt = x)
    );
    subscribe(
      this,
      () => this.getChangeModel().latestPatchNum$,
      x => (this.latestPatchsetNumber = x)
    );
    subscribe(
      this,
      () => this.getChecksModel().someProvidersAreLoadingSelected$,
      x => (this.someProvidersAreLoading = x)
    );
    subscribe(
      this,
      () => this.getViewModel().checksRunsSelected$,
      x => (this.selectedRuns = x)
    );
    subscribe(
      this,
      () => this.getViewModel().checksResultsFilter$,
      x => (this.filterRegExp = x)
    );
  }

  static override get styles() {
    return [
      formStyles,
      sharedStyles,
      spinnerStyles,
      fontStyles,
      css`
        :host {
          display: block;
          background-color: var(--background-color-secondary);
        }
        .header {
          display: block;
          background-color: var(--background-color-primary);
          padding: var(--spacing-l) var(--spacing-xl) var(--spacing-m)
            var(--spacing-xl);
          border-bottom: 1px solid var(--border-color);
        }
        .header.notLatest {
          background-color: var(--emphasis-color);
        }
        .headerTopRow,
        .headerBottomRow {
          display: flex;
          justify-content: space-between;
          align-items: flex-end;
        }
        .headerTopRow gr-dropdown-list {
          border: 1px solid var(--border-color);
          border-radius: var(--border-radius);
          padding: 0 var(--spacing-m);
        }
        .headerTopRow h2 {
          display: inline-block;
        }
        .headerTopRow .loading {
          display: inline-block;
          margin-left: var(--spacing-m);
          color: var(--deemphasized-text-color);
        }
        /* The basics of .loadingSpin are defined in shared styles. */
        .headerTopRow .loadingSpin {
          display: inline-block;
          margin-left: var(--spacing-s);
          width: 18px;
          height: 18px;
          vertical-align: top;
        }
        .headerBottomRow {
          margin-top: var(--spacing-s);
        }
        .headerTopRow .right,
        .headerBottomRow .right {
          display: flex;
          align-items: center;
        }
        .headerTopRow .right .goToLatest {
          display: none;
        }
        .notLatest .headerTopRow .right .goToLatest {
          display: block;
        }
        .headerTopRow .right > * {
          margin-left: var(--spacing-m);
        }
        .headerTopRow .right .goToLatest gr-button {
          --gr-button-padding: var(--spacing-s) var(--spacing-m);
        }
        .headerBottomRow gr-icon {
          color: var(--link-color);
        }
        .headerBottomRow .space {
          display: inline-block;
          width: var(--spacing-xl);
          height: var(--line-height-normal);
        }
        .headerBottomRow a {
          margin-right: var(--spacing-l);
        }
        #moreActions gr-icon {
          color: var(--link-color);
        }
        #moreMessage {
          display: none;
        }
        .body {
          display: block;
          padding: var(--spacing-s) var(--spacing-xl) var(--spacing-xl)
            var(--spacing-xl);
        }
        .filterDiv {
          display: flex;
          margin-top: var(--spacing-s);
          align-items: center;
        }
        .filterDiv input#filterInput {
          padding: var(--spacing-s) var(--spacing-m);
          min-width: 400px;
        }
        .filterDiv .selection {
          padding: var(--spacing-s) var(--spacing-m);
        }
        .categoryHeader {
          display: flex;
          justify-content: space-between;
          align-items: flex-end;
          margin-top: var(--spacing-l);
          margin-left: var(--spacing-l);
          cursor: default;
        }
        .categoryHeader .title {
          text-transform: capitalize;
        }
        .categoryHeader .expandIcon {
          width: var(--line-height-h3);
          height: var(--line-height-h3);
          margin-right: var(--spacing-s);
        }
        .categoryHeader .statusIconWrapper {
          display: inline-block;
        }
        .categoryHeader .statusIcon {
          position: relative;
          top: 2px;
        }
        .categoryHeader .statusIcon.error {
          color: var(--error-foreground);
        }
        .categoryHeader .statusIcon.warning {
          color: var(--warning-foreground);
        }
        .categoryHeader .statusIcon.info {
          color: var(--info-foreground);
        }
        .categoryHeader .statusIcon.success {
          color: var(--success-foreground);
        }
        .categoryHeader.empty gr-icon.statusIcon {
          color: var(--deemphasized-text-color);
        }
        .categoryHeader .filtered {
          color: var(--deemphasized-text-color);
        }
        .collapsed .noResultsMessage,
        .collapsed table {
          display: none;
        }
        .collapsed {
          border-bottom: 1px solid var(--border-color);
          padding-bottom: var(--spacing-m);
        }
        .noResultsMessage {
          width: 100%;
          margin-top: var(--spacing-m);
          background-color: var(--background-color-primary);
          box-shadow: var(--elevation-level-1);
          padding: var(--spacing-s)
            calc(20px + var(--spacing-l) + var(--spacing-m) + var(--spacing-s));
        }
        table.resultsTable {
          width: 100%;
          table-layout: fixed;
          margin-top: var(--spacing-m);
          background-color: var(--background-color-primary);
          box-shadow: var(--elevation-level-1);
        }
        tr.headerRow th {
          text-align: left;
          font-weight: var(--font-weight-medium);
          padding: var(--spacing-s);
        }
        tr.headerRow th.nameCol {
          padding-left: var(--spacing-l);
          width: 200px;
        }
        @media screen and (min-width: 1400px) {
          tr.headerRow th.nameCol.longNames {
            width: 300px;
          }
        }
        tr.headerRow th.summaryCol {
          width: 99%;
        }
        tr.headerRow th.expanderCol {
          width: 30px;
          padding-right: var(--spacing-l);
        }

        gr-button.showAll {
          margin: var(--spacing-m);
        }
        tr {
          border-top: 1px solid var(--border-color);
        }
      `,
    ];
  }

  protected override updated(changedProperties: PropertyValues) {
    super.updated(changedProperties);
    if (changedProperties.has('filterRegExp') && this.filterInput) {
      this.filterInput.value = this.filterRegExp;
    }
    if (changedProperties.has('tabState') && this.tabState) {
      const {statusOrCategory, checkName} = this.tabState;
      if (isCategory(statusOrCategory)) {
        const expanded = this.isSectionExpanded.get(statusOrCategory);
        if (!expanded) this.toggleExpanded(statusOrCategory);
      }
      if (checkName) {
        this.scrollElIntoView(`gr-result-row.${charsOnly(checkName)}`);
      } else if (
        statusOrCategory &&
        statusOrCategory !== RunStatus.RUNNING &&
        statusOrCategory !== RunStatus.RUNNABLE
      ) {
        const cat = statusOrCategory.toString().toLowerCase();
        this.scrollElIntoView(`.categoryHeader.${cat} + table gr-result-row`);
      }
    }
  }

  private scrollElIntoView(selector: string) {
    this.updateComplete.then(() => {
      let el = this.shadowRoot?.querySelector(selector);
      // el might be a <gr-result-row> with an empty shadowRoot. Let's wait a
      // moment before trying to find a child element in it.
      setTimeout(() => {
        if (el) (el as HTMLElement).focus();
        // If the target element is a <gr-result-row>, then expand it.
        (el as GrResultRow)?.toggleExpanded(true);
        // <gr-result-row> has display:contents and cannot be scrolled into view
        // itself. Thus we are preferring to scroll the first child into view.
        el = el?.shadowRoot?.firstElementChild ?? el;
        el?.scrollIntoView({block: 'center'});
      }, 0);
    });
  }

  override render() {
    const headerClasses = {
      header: true,
      notLatest:
        !!this.checksPatchsetNumber &&
        this.checksPatchsetNumber !== this.latestPatchsetNumber,
    };
    const attemptItems = this.createAttemptDropdownItems();
    return html`
      <div class=${classMap(headerClasses)}>
        <div class="headerTopRow">
          <div class="left">
            <h2 class="heading-2">Results</h2>
            <div class="loading" ?hidden=${!this.someProvidersAreLoading}>
              <span>Loading results </span>
              <span class="loadingSpin"></span>
            </div>
          </div>
          <div class="right">
            <div class="goToLatest">
              <gr-button @click=${this.goToLatestPatchset} link
                >Go To Latest Patchset</gr-button
              >
            </div>
            ${when(
              attemptItems.length > 0,
              () => html` <gr-dropdown-list
                value=${this.selectedAttempt ?? 0}
                .items=${attemptItems}
                @value-change=${this.onAttemptSelected}
              ></gr-dropdown-list>`
            )}
            <gr-dropdown-list
              value=${(this.checksPatchsetNumber ||
                this.latestPatchsetNumber) ??
              0}
              .items=${this.createPatchsetDropdownItems()}
              @value-change=${this.onPatchsetSelected}
            ></gr-dropdown-list>
          </div>
        </div>
        <div class="headerBottomRow">
          <div class="left">${this.renderFilter()}</div>
          <div class="right">${this.renderLinksAndActions()}</div>
        </div>
      </div>
      <div class="body">
        ${this.renderSection(Category.ERROR)}
        ${this.renderSection(Category.WARNING)}
        ${this.renderSection(Category.INFO)}
        ${this.renderSection(Category.SUCCESS)}
      </div>
    `;
  }

  private renderLinksAndActions() {
    const links = this.links ?? [];
    const primaryLinks = links
      .filter(a => a.primary)
      // Showing the same icons twice without text is super confusing.
      .filter(
        (link: Link, index: number, array: Link[]) =>
          array.findIndex(other => link.icon === other.icon) === index
      )
      .slice(0, 4);
    const overflowLinks = links.filter(a => !primaryLinks.includes(a));
    const overflowLinkItems: DropdownLink[] = overflowLinks.map(link => {
      return {
        ...link,
        id: link.tooltip,
        name: link.tooltip,
        target: '_blank',
        tooltip: undefined,
      };
    });

    const actions = this.actions ?? [];
    const primaryActions = actions.filter(a => a.primary).slice(0, 2);
    const overflowActions = actions.filter(a => !primaryActions.includes(a));
    const overflowActionItems = overflowActions.map(action => {
      return {...action, id: action.name};
    });
    const disabledActions = overflowActionItems
      .filter(action => action.disabled)
      .map(action => action.id);

    return html`
      ${primaryLinks.map(this.renderLink)}
      ${primaryLinks.length > 0 && primaryActions.length > 0
        ? html`<div class="space"></div>`
        : ''}
      ${primaryActions.map(this.renderAction)}
      ${this.renderOverflow(
        [...overflowLinkItems, ...overflowActionItems],
        disabledActions
      )}
    `;
  }

  private renderLink(link?: Link) {
    if (!link) return;
    const tooltipText = link.tooltip ?? tooltipForLink(link.icon);
    const icon = iconForLink(link.icon);
    return html`<gr-tooltip-content
      has-tooltip
      ?position-below=${true}
      title=${tooltipText}
    >
      <a href=${link.url} target="_blank" rel="noopener noreferrer">
        <gr-icon
          icon=${icon.name}
          aria-label=${tooltipText}
          class="link"
          ?filled=${!!icon.filled}
        ></gr-icon>
      </a>
    </gr-tooltip-content>`;
  }

  private renderOverflow(items: DropdownLink[], disabledIds: string[] = []) {
    if (items.length === 0) return;
    return html`
      <gr-dropdown
        id="moreActions"
        link=""
        vertical-offset="32"
        horizontal-align="right"
        @tap-item=${this.handleAction}
        .items=${items}
        .disabledIds=${disabledIds}
      >
        <gr-icon icon="more_vert" aria-labelledby="moreMessage"></gr-icon>
        <span id="moreMessage">More</span>
      </gr-dropdown>
    `;
  }

  private handleAction(e: CustomEvent<Action>) {
    this.getChecksModel().triggerAction(
      e.detail,
      undefined,
      'results-dropdown'
    );
  }

  private handleFilter(e: ChecksResultsFilterEvent) {
    const newValue = e.detail.filterRegExp ?? '';
    this.getViewModel().updateState({
      checksResultsFilter: this.filterRegExp === newValue ? '' : newValue,
    });
  }

  private renderAction(action?: Action) {
    if (!action) return;
    return html`<gr-checks-action
      context="results"
      .action=${action}
    ></gr-checks-action>`;
  }

  private onAttemptSelected(e: CustomEvent<{value: string | undefined}>) {
    const attempt = stringToAttemptChoice(e.detail.value);
    assertIsDefined(attempt, `unexpected attempt choice ${e.detail.value}`);
    this.getChecksModel().updateStateSetAttempt(attempt);
  }

  private onPatchsetSelected(e: CustomEvent<{value: string}>) {
    const patchset = Number(e.detail.value) as PatchSetNumber;
    assert(Number.isInteger(patchset), `patchset must be integer: ${patchset}`);
    this.getChecksModel().updateStateSetPatchset(patchset);
  }

  private goToLatestPatchset() {
    assertIsDefined(this.latestPatchsetNumber, 'latestPatchsetNumber');
    this.getChecksModel().updateStateSetPatchset(this.latestPatchsetNumber);
  }

  private createAttemptDropdownItems() {
    if (this.runs.every(run => run.isSingleAttempt)) return [];
    const attempts: AttemptChoice[] = this.runs
      .map(run => run.attempt ?? 0)
      .filter(isAttemptChoice)
      .filter(unique);
    attempts.push(LATEST_ATTEMPT);
    attempts.push(ALL_ATTEMPTS);
    const items: DropdownItem[] = attempts.sort(sortAttemptChoices).map(a => {
      return {
        value: a,
        text: attemptChoiceLabel(a),
      };
    });
    return items;
  }

  private createPatchsetDropdownItems() {
    if (!this.latestPatchsetNumber) return [];
    return Array.from(Array(this.latestPatchsetNumber), (_, i) => {
      assertIsDefined(this.latestPatchsetNumber, 'latestPatchsetNumber');
      const index = this.latestPatchsetNumber - i;
      const postfix = index === this.latestPatchsetNumber ? ' (latest)' : '';
      return {
        value: `${index}`,
        text: `Patchset ${index}${postfix}`,
      };
    });
  }

  isRunSelected(run: {checkName: string}) {
    return this.selectedRuns.size === 0 || this.selectedRuns.has(run.checkName);
  }

  renderFilter() {
    const runs = this.runs.filter(
      run =>
        this.isRunSelected(run) && isAttemptSelected(this.selectedAttempt, run)
    );
    if (
      this.selectedRuns.size === 0 &&
      allResults(runs).length <= 3 &&
      this.filterRegExp === ''
    ) {
      return;
    }
    return html`
      <div class="filterDiv">
        <input
          id="filterInput"
          type="text"
          placeholder="Filter results by tag or regular expression"
          @input=${this.onFilterInputChange}
        />
      </div>
    `;
  }

  onFilterInputChange() {
    assertIsDefined(this.filterInput, 'filter <input> element');
    this.reporting.reportInteraction(
      Interaction.CHECKS_RESULT_FILTER_CHANGED,
      {},
      {deduping: Deduping.EVENT_ONCE_PER_CHANGE}
    );
    this.getViewModel().updateState({
      checksResultsFilter: this.filterInput.value,
    });
  }

  renderSection(category: Category) {
    const catString = category.toString().toLowerCase();
    const isWarningOrError =
      category === Category.WARNING || category === Category.ERROR;
    const allRuns = this.runs.filter(run =>
      isAttemptSelected(this.selectedAttempt, run)
    );
    const all = allRuns.reduce(
      (results: RunResult[], run) => [
        ...results,
        ...this.computeRunResults(category, run),
      ],
      []
    );
    const isSelectionActive = this.selectedRuns.size > 0;
    const selected = all.filter(result => this.isRunSelected(result));
    const re = new RegExp(this.filterRegExp, 'i');
    const filtered = selected.filter(result => matches(result, re));
    const isFilterActiveWithResults =
      this.filterRegExp !== '' && filtered.length > 0;

    // The logic for deciding whether to expand a section by default is a bit
    // complicated, but we want to collapse empty and info/success sections by
    // default for a clean and focused user experience. However, as soon as the
    // user starts selecting or filtering we must take this into account and
    // prefer to expand the sections.
    let expanded = this.isSectionExpanded.get(category);
    const expandedByUser = this.isSectionExpandedByUser.get(category) ?? false;
    if (!expandedByUser || expanded === undefined) {
      // Note that we are using `selected` for `isEmpty` and not `filtered`,
      // because if the filter is what makes a section empty, then we want to
      // show an expanded section, which contains a message about this.
      const isEmpty = selected.length === 0;
      expanded =
        !isEmpty &&
        (isWarningOrError || isSelectionActive || isFilterActiveWithResults);
      this.isSectionExpanded.set(category, expanded);
    }
    const expandedClass = expanded ? 'expanded' : 'collapsed';

    const isShowAll = this.isShowAll.get(category) ?? false;
    const allExpanded = this.allResultsExpanded.get(category) ?? false;
    const hasExpandableResults = filtered.some(computeIsExpandable);
    const resultCount = filtered.length;
    const empty = resultCount === 0 ? 'empty' : '';
    const resultLimit = isShowAll ? 1000 : 20;
    const showAllButton = this.renderShowAllButton(
      category,
      isShowAll,
      resultLimit,
      resultCount
    );
    const icon = iconFor(category);
    return html`
      <div class="${expandedClass} ${catString}">
        <div
          class="categoryHeader ${catString} ${empty}"
          @click=${() => this.toggleExpanded(category)}
        >
          <h3 class="left heading-3">
            <gr-icon
              class="expandIcon"
              icon=${expanded ? 'expand_less' : 'expand_more'}
            ></gr-icon>
            <gr-tooltip-content
              has-tooltip
              ?position-below=${true}
              title=${ifDefined(CATEGORY_TOOLTIPS.get(category))}
            >
              <div class="statusIconWrapper">
                <gr-icon
                  icon=${icon.name}
                  ?filled=${!!icon.filled}
                  class="statusIcon ${catString}"
                ></gr-icon>
                <span class="title">${catString}</span>
                <span class="count">${this.renderCount(all, filtered)}</span>
              </div>
            </gr-tooltip-content>
          </h3>
          <div class="right">
            <gr-button
              link
              ?hidden=${!expanded || !hasExpandableResults}
              @click=${(e: MouseEvent) =>
                this.toggleResultsExpanded(e, category)}
            >
              ${allExpanded ? 'Collapse All' : 'Expand All'}</gr-button
            >
          </div>
        </div>
        ${when(expanded, () =>
          this.renderResults(
            all,
            selected,
            filtered,
            resultLimit,
            showAllButton
          )
        )}
      </div>
    `;
  }

  renderShowAllButton(
    category: Category,
    isShowAll: boolean,
    showAllThreshold: number,
    resultCount: number
  ) {
    if (resultCount <= showAllThreshold) return;
    const message = isShowAll ? 'Show Less' : `Show All (${resultCount})`;
    const handler = () => this.toggleShowAll(category);
    return html`
      <tr class="showAllRow">
        <td colspan="3">
          <gr-button class="showAll" link @click=${handler}
            >${message}</gr-button
          >
        </td>
      </tr>
    `;
  }

  toggleResultsExpanded(e: MouseEvent, category: Category) {
    e.preventDefault();
    // Clicking the header row would otherwise collapse the entire section.
    e.stopPropagation();
    const catString = category.toString().toLowerCase();
    const current = this.allResultsExpanded.get(category) ?? false;
    const desired = !current;
    this.allResultsExpanded.set(category, desired);
    // this.allResultsExpanded stays the same object, but changes its content
    this.requestUpdate();
    const rows = this.shadowRoot!.querySelectorAll<GrResultRow>(
      `.${catString} gr-result-row`
    );
    for (const row of rows) {
      row.toggleExpanded(desired);
    }
  }

  toggleShowAll(category: Category) {
    const current = this.isShowAll.get(category) ?? false;
    this.isShowAll.set(category, !current);
    this.reporting.reportInteraction(
      Interaction.CHECKS_RESULT_SECTION_SHOW_ALL,
      {
        category,
        showAll: !current,
      }
    );
    this.requestUpdate();
  }

  renderResults(
    all: RunResult[],
    selected: RunResult[],
    filtered: RunResult[],
    limit: number,
    showAll: TemplateResult | undefined
  ) {
    if (all.length === 0) {
      return html`<div class="noResultsMessage">No results</div>`;
    }
    if (selected.length === 0) {
      return html`<div class="noResultsMessage">
        No results for this filtered view
      </div>`;
    }
    if (filtered.length === 0) {
      return html`<div class="noResultsMessage">
        No results match the regular expression
      </div>`;
    }
    filtered = filtered.slice(0, limit);
    // Some hosts/plugins use really long check names. If we have space and the
    // check names are indeed very long, then set a more generous nameCol width.
    const longestNameLength = Math.max(...all.map(r => r.checkName.length));
    const nameColClasses = {nameCol: true, longNames: longestNameLength > 25};
    return html`
      <table class="resultsTable">
        <thead>
          <tr class="headerRow">
            <th class=${classMap(nameColClasses)}>Run</th>
            <th class="summaryCol">Summary</th>
            <th class="expanderCol"></th>
          </tr>
        </thead>
        <tbody @checks-results-filter=${this.handleFilter}>
          ${repeat(
            filtered,
            // @ts-ignore: temporarily unblock typescript 5.3 migration
            result => result.internalResultId,
            (result?: RunResult) => html`
              <gr-result-row
                class=${charsOnly(result!.checkName)}
                .result=${result}
              ></gr-result-row>
            `
          )}
          ${showAll}
        </tbody>
      </table>
    `;
  }

  renderCount(all: RunResult[], filtered: RunResult[]) {
    if (all.length === filtered.length) {
      return html`(${all.length})`;
    }
    return html`(${filtered.length} shown out of ${all.length})`;
  }

  toggleExpanded(category: Category) {
    const expanded = this.isSectionExpanded.get(category);
    assertIsDefined(expanded, 'expanded must have been set in initial render');
    this.isSectionExpanded.set(category, !expanded);
    this.isSectionExpandedByUser.set(category, true);
    this.reporting.reportInteraction(Interaction.CHECKS_RESULT_SECTION_TOGGLE, {
      expanded: !expanded,
      category,
    });
    this.requestUpdate();
  }

  computeRunResults(category: Category, run: CheckRun): RunResult[] {
    if (category === Category.SUCCESS && hasCompletedWithoutResults(run)) {
      return [this.computeSuccessfulRunResult(run)];
    }
    return (
      run.results
        ?.filter(result => result.category === category)
        .map(result => runResult(run, result)) ?? []
    );
  }

  computeSuccessfulRunResult(run: CheckRun): RunResult {
    const adaptedRun: RunResult = runResult(run, {
      internalResultId: run.internalRunId + '-0',
      category: Category.SUCCESS,
      summary: run.statusDescription ?? '',
    });
    if (!run.statusDescription) {
      const start = run.scheduledTimestamp ?? run.startedTimestamp;
      const end = run.finishedTimestamp;
      let duration = '';
      if (start && end) {
        duration = ` ${durationString(start, end, 'in')}`;
      }
      adaptedRun.message = `Completed without results${duration}.`;
    }
    if (run.statusLink) {
      adaptedRun.links = [
        {
          url: run.statusLink,
          primary: true,
          icon: LinkIcon.EXTERNAL,
        },
      ];
    }
    return adaptedRun;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-result-row': GrResultRow;
    'gr-result-expanded': GrResultExpanded;
    'gr-checks-results': GrChecksResults;
  }
}
