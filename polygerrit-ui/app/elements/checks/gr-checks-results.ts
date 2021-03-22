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
import {repeat} from 'lit-html/directives/repeat';
import {
  css,
  customElement,
  internalProperty,
  property,
  PropertyValues,
  query,
} from 'lit-element';
import {GrLitElement} from '../lit/gr-lit-element';
import '@polymer/paper-tooltip/paper-tooltip';
import {Category, Link, LinkIcon, RunStatus, Tag} from '../../api/checks';
import {sharedStyles} from '../../styles/shared-styles';
import {CheckRun, RunResult} from '../../services/checks/checks-model';
import {
  allResults,
  hasCompletedWithoutResults,
  hasResultsOf,
  iconForCategory,
} from '../../services/checks/checks-util';
import {assertIsDefined} from '../../utils/common-util';
import {whenVisible} from '../../utils/dom-util';
import {durationString} from '../../utils/date-util';
import {charsOnly, pluralize} from '../../utils/string-util';
import {fireRunSelectionReset} from './gr-checks-util';
import {ChecksTabState} from '../../types/events';

@customElement('gr-result-row')
class GrResultRow extends GrLitElement {
  @property()
  result?: RunResult;

  @property()
  isExpanded = false;

  @property({type: Boolean, reflect: true})
  isExpandable = false;

  @property()
  shouldRender = false;

  static get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: contents;
        }
        :host([isexpandable]) {
          cursor: pointer;
        }
        tr {
          border-top: 1px solid var(--border-color);
        }
        iron-icon.launch {
          color: var(--link-color);
          margin-right: var(--spacing-s);
        }
        td.iconCol {
          padding-left: var(--spacing-l);
          padding-right: var(--spacing-m);
        }
        .iconCol div {
          width: 20px;
        }
        .nameCol div {
          width: 165px;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        .summaryCol {
          /* Forces this column to get the remaining space that is left over by
             the other columns. */
          width: 99%;
        }
        .expanderCol div {
          width: 20px;
        }
        td {
          white-space: nowrap;
          padding: var(--spacing-s);
        }
        td .summary-cell {
          display: flex;
          max-width: calc(100vw - 700px);
        }
        td .summary-cell .summary {
          font-weight: var(--font-weight-bold);
          flex-shrink: 1;
          overflow: hidden;
          text-overflow: ellipsis;
          margin-right: var(--spacing-s);
        }
        td .summary-cell .message {
          flex-grow: 1;
          /* Looks a bit stupid, but the idea is that .message shrinks first,
             and only when that has shrunken to 0, then .summary should also
             start shrinking (substantially). */
          flex-shrink: 1000000;
          overflow: hidden;
          text-overflow: ellipsis;
        }
        td .summary-cell .tags .tag {
          color: var(--deemphasized-text-color);
          display: inline-block;
          border-radius: 20px;
          background-color: var(--tag-background);
          padding: 0 var(--spacing-m);
          margin-left: var(--spacing-s);
        }
        td .summary-cell .label {
          color: var(--deemphasized-text-color);
          display: inline-block;
          border-radius: 20px;
          background-color: var(--label-background);
          padding: 0 var(--spacing-m);
          margin-left: var(--spacing-s);
        }
        .tag.gray {
          background-color: var(--tag-gray);
        }
        .tag.yellow {
          background-color: var(--tag-yellow);
        }
        .tag.pink {
          background-color: var(--tag-pink);
        }
        .tag.purple {
          background-color: var(--tag-purple);
        }
        .tag.cyan {
          background-color: var(--tag-cyan);
        }
        .tag.brown {
          background-color: var(--tag-brown);
        }
      `,
    ];
  }

  update(changedProperties: PropertyValues) {
    if (changedProperties.has('result')) {
      this.isExpandable = !!this.result?.summary && !!this.result?.message;
    }
    super.update(changedProperties);
  }

  firstUpdated() {
    const loading = this.shadowRoot?.querySelector('.container');
    assertIsDefined(loading, '"Loading" element');
    whenVisible(loading, () => this.setAttribute('shouldRender', 'true'), 200);
  }

  render() {
    if (!this.result) return '';
    if (!this.shouldRender) {
      return html`
        <tr class="container">
          <td class="iconCol"></td>
          <td class="nameCol">
            <div><span class="loading">Loading...</span></div>
          </td>
          <td class="summaryCol"></td>
          <td class="expanderCol"></td>
        </tr>
      `;
    }
    return html`
      <tr class="container" @click="${this.toggleExpanded}">
        <td class="iconCol">
          <div>${this.renderIcon()}</div>
        </td>
        <td class="nameCol">
          <div><span>${this.result.checkName}</span></div>
        </td>
        <td class="summaryCol">
          <div class="summary-cell">
            ${(this.result.links?.slice(0, 5) ?? []).map(this.renderLink)}
            ${this.renderSummary(this.result.summary)}
            <div class="message">
              ${this.isExpanded ? '' : this.result.message}
            </div>
            <div class="tags">
              ${(this.result.tags ?? []).map(t => this.renderTag(t))}
            </div>
            ${this.renderLabel()}
          </div>
          <gr-result-expanded
            .result="${this.result}"
            ?hidden="${!this.isExpanded}"
          ></gr-result-expanded>
        </td>
        <td class="expanderCol">
          <div
            class="show-hide"
            role="switch"
            tabindex="0"
            ?hidden="${!this.isExpandable}"
            ?aria-checked="${this.isExpanded}"
            aria-label="${this.isExpanded
              ? 'Collapse result row'
              : 'Expand result row'}"
            @keydown="${this.toggleExpanded}"
          >
            <iron-icon
              icon="${this.isExpanded
                ? 'gr-icons:expand-less'
                : 'gr-icons:expand-more'}"
            ></iron-icon>
          </div>
        </td>
      </tr>
    `;
  }

  private toggleExpanded() {
    if (!this.isExpandable) return;
    this.isExpanded = !this.isExpanded;
  }

  renderSummary(text?: string) {
    if (!text) return;
    return html`
      <!-- The &nbsp; is for being able to shrink a tiny amount without
       the text itself getting shrunk with an ellipsis. -->
      <div class="summary">${text}&nbsp;</div>
    `;
  }

  renderLink(link: Link) {
    const tooltipText = link.tooltip ?? 'Link to details';
    return html`
      <a href="${link.url}" target="_blank">
        <iron-icon
          aria-label="external link to details"
          class="launch"
          icon="gr-icons:launch"
        ></iron-icon>
        <paper-tooltip offset="5">${tooltipText}</paper-tooltip>
      </a>
    `;
  }

  renderIcon() {
    if (this.result?.status !== RunStatus.RUNNING) return;
    return html`<iron-icon icon="gr-icons:timelapse"></iron-icon>`;
  }

  renderLabel() {
    const label = this.result?.labelName;
    if (!label) return;
    return html`<div class="label">${label}</div>`;
  }

  renderTag(tag: Tag) {
    return html`<div class="tag ${tag.color}">${tag.name}</div>`;
  }
}

@customElement('gr-result-expanded')
class GrResultExpanded extends GrLitElement {
  @property()
  result?: RunResult;

  static get styles() {
    return [
      sharedStyles,
      css`
        .message {
          padding: var(--spacing-m) var(--spacing-m) var(--spacing-m) 0;
        }
      `,
    ];
  }

  render() {
    if (!this.result) return '';
    return html`
      <gr-endpoint-decorator name="check-result-expanded">
        <gr-endpoint-param
          name="run"
          .value="${this.result}"
        ></gr-endpoint-param>
        <gr-endpoint-param
          name="result"
          .value="${this.result}"
        ></gr-endpoint-param>
        <div class="message">
          ${this.result.message}
        </div>
      </gr-endpoint-decorator>
    `;
  }
}

@customElement('gr-checks-results')
export class GrChecksResults extends GrLitElement {
  @query('#filterInput')
  filterInput?: HTMLInputElement;

  @internalProperty()
  filterRegExp = new RegExp('');

  @property()
  runs: CheckRun[] = [];

  @property()
  tabState?: ChecksTabState;

  /**
   * How many runs are selected in the runs panel?
   * If 0, then the `runs` property contains all the runs there are.
   * If >0, then it only contains the data of certain selected runs.
   */
  @property()
  selectedRunsCount = 0;

  /**
   * This is the current state of whether a section is expanded or not. As long
   * as isSectionExpandedByUser is false this will be computed by a default rule
   * on every render.
   */
  private isSectionExpanded = new Map<Category | 'SUCCESS', boolean>();

  /**
   * Keeps track of whether the user intentionally changed the expansion state.
   * Once this is true the default rule for showing a section expanded or not
   * is not applied anymore.
   */
  private isSectionExpandedByUser = new Map<Category | 'SUCCESS', boolean>();

  static get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: block;
          padding: var(--spacing-xl);
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
        .filterDiv iron-icon.filter {
          color: var(--selected-foreground);
        }
        .filterDiv gr-button.reset {
          margin: calc(0px - var(--spacing-s)) var(--spacing-l);
        }
        .categoryHeader {
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
          max-width: 1280px;
          margin-top: var(--spacing-m);
          background-color: var(--background-color-primary);
          box-shadow: var(--elevation-level-1);
          padding: var(--spacing-s)
            calc(20px + var(--spacing-l) + var(--spacing-m) + var(--spacing-s));
        }
        table.resultsTable {
          width: 100%;
          max-width: 1280px;
          margin-top: var(--spacing-m);
          background-color: var(--background-color-primary);
          box-shadow: var(--elevation-level-1);
        }
        tr.headerRow th {
          text-align: left;
          font-weight: var(--font-weight-bold);
          padding: var(--spacing-s);
        }
      `,
    ];
  }

  protected updated(changedProperties: PropertyValues) {
    super.updated(changedProperties);
    if (changedProperties.has('tabState') && this.tabState) {
      const {statusOrCategory, checkName} = this.tabState;
      if (
        statusOrCategory &&
        statusOrCategory !== RunStatus.RUNNING &&
        statusOrCategory !== RunStatus.RUNNABLE
      ) {
        let cat = statusOrCategory.toString().toLowerCase();
        if (statusOrCategory === RunStatus.COMPLETED) cat = 'success';
        this.scrollElIntoView(`.categoryHeader .${cat}`);
      } else if (checkName) {
        this.scrollElIntoView(`gr-result-row.${charsOnly(checkName)}`);
      }
    }
  }

  scrollElIntoView(selector: string) {
    this.updateComplete.then(() => {
      let el = this.shadowRoot?.querySelector(selector);
      // <gr-result-row> has display:contents and cannot be scrolled into view
      // itself. Thus we are preferring to scroll the first child into view.
      el = el?.shadowRoot?.firstElementChild ?? el;
      el?.scrollIntoView({block: 'center'});
    });
  }

  render() {
    return html`
      <div><h2 class="heading-2">Results</h2></div>
      ${this.renderFilter()} ${this.renderSection(Category.ERROR)}
      ${this.renderSection(Category.WARNING)}
      ${this.renderSection(Category.INFO)} ${this.renderSection('SUCCESS')}
    `;
  }

  renderFilter() {
    if (this.selectedRunsCount === 0 && allResults(this.runs).length <= 3) {
      if (this.filterRegExp.source.length > 0) {
        this.filterRegExp = new RegExp('');
      }
      return;
    }
    return html`
      <div class="filterDiv">
        <input
          id="filterInput"
          type="text"
          placeholder="Filter results by regular expression"
          @input="${this.onInput}"
        />
        <div class="selection">
          ${this.renderSelectionFilter()}
        </div>
      </div>
    `;
  }

  renderSelectionFilter() {
    const count = this.selectedRunsCount;
    if (count === 0) return;
    return html`
      <iron-icon class="filter" icon="gr-icons:filter"></iron-icon>
      <span>Filtered by ${pluralize(count, 'run')}</span>
      <gr-button link class="reset" @click="${this.handleClick}"
        >Reset View</gr-button
      >
    `;
  }

  handleClick() {
    this.filterRegExp = new RegExp('');
    fireRunSelectionReset(this);
  }

  onInput() {
    assertIsDefined(this.filterInput, 'filter <input> element');
    this.filterRegExp = new RegExp(this.filterInput.value, 'i');
  }

  renderSection(category: Category | 'SUCCESS') {
    const catString = category.toString().toLowerCase();
    let runs = this.runs;
    if (category === 'SUCCESS') {
      runs = runs.filter(hasCompletedWithoutResults);
    } else {
      runs = runs.filter(r => hasResultsOf(r, category));
    }
    const all = runs.reduce((allResults: RunResult[], run) => {
      return [...allResults, ...this.computeRunResults(category, run)];
    }, []);
    const filtered = all.filter(
      result =>
        this.filterRegExp.test(result.checkName) ||
        this.filterRegExp.test(result.summary)
    );
    let expanded = this.isSectionExpanded.get(category);
    const expandedByUser = this.isSectionExpandedByUser.get(category) ?? false;
    if (!expandedByUser || expanded === undefined) {
      expanded = all.length > 0;
      this.isSectionExpanded.set(category, expanded);
    }
    const expandedClass = expanded ? 'expanded' : 'collapsed';
    const icon = expanded ? 'gr-icons:expand-less' : 'gr-icons:expand-more';
    return html`
      <div class="${expandedClass}">
        <h3
          class="categoryHeader ${catString} heading-3"
          @click="${() => this.toggleExpanded(category)}"
        >
          <iron-icon class="expandIcon" icon="${icon}"></iron-icon>
          <iron-icon
            icon="gr-icons:${iconForCategory(category)}"
            class="statusIcon ${catString}"
          ></iron-icon>
          <span class="title">${catString}</span>
          <span class="count">${this.renderCount(all, filtered)}</span>
        </h3>
        ${this.renderResults(all, filtered)}
      </div>
    `;
  }

  renderResults(all: RunResult[], filtered: RunResult[]) {
    if (all.length === 0 && this.selectedRunsCount > 0) {
      return html`<div class="noResultsMessage">
        No results for this filtered view
      </div>`;
    }
    if (all.length === 0) {
      return html`<div class="noResultsMessage">No results</div>`;
    }
    if (filtered.length === 0) {
      return html`<div class="noResultsMessage">
        No results match the regular expression
      </div>`;
    }
    return html`
      <table class="resultsTable">
        <thead>
          <tr class="headerRow">
            <th class="iconCol"></th>
            <th class="nameCol">Run</th>
            <th class="summaryCol">Summary</th>
            <th class="expanderCol"></th>
          </tr>
        </thead>
        <tbody>
          ${repeat(
            filtered,
            result => result.externalId,
            result => html`
              <gr-result-row
                class="${charsOnly(result.checkName)}"
                .result="${result}"
              ></gr-result-row>
            `
          )}
        </tbody>
      </table>
    `;
  }

  renderCount(all: RunResult[], filtered: RunResult[]) {
    if (this.selectedRunsCount > 0) {
      return html`<span class="filtered"> - filtered</span>`;
    }
    if (all.length === filtered.length) {
      return html`(${all.length})`;
    } else {
      return html`(${filtered.length} of ${all.length})`;
    }
  }

  toggleExpanded(category: Category | 'SUCCESS') {
    const expanded = this.isSectionExpanded.get(category);
    assertIsDefined(expanded, 'expanded must have been set in initial render');
    this.isSectionExpanded.set(category, !expanded);
    this.isSectionExpandedByUser.set(category, true);
    this.requestUpdate();
  }

  computeRunResults(category: Category | 'SUCCESS', run: CheckRun) {
    if (category === 'SUCCESS') return [this.computeSuccessfulRunResult(run)];
    return (
      run.results
        ?.filter(result => result.category === category)
        .map(result => {
          return {...run, ...result};
        }) ?? []
    );
  }

  computeSuccessfulRunResult(run: CheckRun): RunResult {
    const adaptedRun: RunResult = {
      category: Category.INFO, // will not be used, but is required
      summary: run.statusDescription ?? '',
      ...run,
    };
    if (!run.statusDescription) {
      const start = run.scheduledTimestamp ?? run.startedTimestamp;
      const end = run.finishedTimestamp;
      let duration = '';
      if (start && end) {
        duration = ` in ${durationString(start, end, true)}`;
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
