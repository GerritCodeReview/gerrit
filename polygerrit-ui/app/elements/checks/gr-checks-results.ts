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
import {repeat} from 'lit-html/directives/repeat';
import {
  css,
  customElement,
  property,
  PropertyValues,
  query,
  state,
  TemplateResult,
} from 'lit-element';
import {GrLitElement} from '../lit/gr-lit-element';
import './gr-checks-action';
import './gr-checks-attempt';
import '@polymer/paper-tooltip/paper-tooltip';
import {
  Action,
  Category,
  Link,
  LinkIcon,
  RunStatus,
  Tag,
} from '../../api/checks';
import {sharedStyles} from '../../styles/shared-styles';
import {
  allActions$,
  allLinks$,
  CheckRun,
  checksPatchsetNumber$,
  RunResult,
  someProvidersAreLoading$,
} from '../../services/checks/checks-model';
import {
  allResults,
  fireActionTriggered,
  iconForCategory,
  iconForLink,
  primaryRunAction,
  tooltipForLink,
} from '../../services/checks/checks-util';
import {assertIsDefined, check} from '../../utils/common-util';
import {toggleClass, whenVisible} from '../../utils/dom-util';
import {durationString} from '../../utils/date-util';
import {charsOnly} from '../../utils/string-util';
import {isAttemptSelected} from './gr-checks-util';
import {ChecksTabState} from '../../types/events';
import {ConfigInfo, PatchSetNumber} from '../../types/common';
import {latestPatchNum$} from '../../services/change/change-model';
import {appContext} from '../../services/app-context';
import {repoConfig$} from '../../services/config/config-model';
import {spinnerStyles} from '../../styles/gr-spinner-styles';

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
        gr-result-expanded {
          cursor: default;
        }
        tr {
          border-top: 1px solid var(--border-color);
        }
        iron-icon.link {
          color: var(--link-color);
          margin-right: var(--spacing-m);
        }
        td.iconCol {
          padding-left: var(--spacing-l);
        }
        td.nameCol div.flex {
          display: flex;
        }
        td.nameCol .name {
          overflow: hidden;
          text-overflow: ellipsis;
          margin-right: var(--spacing-s);
        }
        td.nameCol .space {
          flex-grow: 1;
        }
        td.nameCol gr-checks-action {
          display: none;
        }
        tr:hover td.nameCol gr-checks-action {
          display: inline-block;
          /* The button should fit into the 20px line-height. The negative
             margin provides the extra space needed for the vertical padding.
             Alternatively we could have set the vertical padding to 0, but
             that would not have been a nice click target. */
          margin: calc(0px - var(--spacing-s)) 0px;
          margin-left: var(--spacing-s);
        }
        td {
          white-space: nowrap;
          padding: var(--spacing-s);
        }
        td .summary-cell {
          display: flex;
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
        tr:hover {
          background: var(--hover-background-color);
        }
        tr td .summary-cell .links,
        tr td .summary-cell .actions,
        tr.collapsed:hover td .summary-cell .links,
        tr.collapsed:hover td .summary-cell .actions,
        :host(.dropdown-open) tr td .summary-cell .links,
        :host(.dropdown-open) tr td .summary-cell .actions {
          display: inline-block;
          margin-left: var(--spacing-s);
        }
        tr.collapsed td .summary-cell .message {
          color: var(--deemphasized-text-color);
        }
        tr.collapsed td .summary-cell .links,
        tr.collapsed td .summary-cell .actions {
          display: none;
        }
        tr.collapsed:hover .summary-cell .hoverHide.tags,
        tr.collapsed:hover .summary-cell .hoverHide.label {
          display: none;
        }
        td .summary-cell .tags .tag {
          color: var(--primary-text-color);
          display: inline-block;
          border-radius: 20px;
          background-color: var(--tag-background);
          padding: 0 var(--spacing-m);
          margin-left: var(--spacing-s);
        }
        td .summary-cell .label {
          color: var(--primary-text-color);
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
        .actions gr-checks-action,
        .actions gr-dropdown {
          /* Fitting a 28px button into 20px line-height. */
          margin: -4px 0;
          vertical-align: top;
        }
        #moreActions iron-icon {
          color: var(--link-color);
        }
        #moreMessage {
          display: none;
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
      <tr class="${classMap({container: true, collapsed: !this.isExpanded})}">
        <td class="iconCol" @click="${this.toggleExpanded}">
          <div>${this.renderIcon()}</div>
        </td>
        <td class="nameCol" @click="${this.toggleExpanded}">
          <div class="flex">
            <gr-hovercard-run .run="${this.result}"></gr-hovercard-run>
            <div class="name">${this.result.checkName}</div>
            <gr-checks-attempt .run="${this.result}"></gr-checks-attempt>
            <div class="space"></div>
            ${this.renderPrimaryRunAction()}
          </div>
        </td>
        <td class="summaryCol">
          <div class="summary-cell">
            ${(this.result.links?.slice(0, 1) ?? []).map(this.renderLink)}
            ${this.renderSummary(this.result.summary)}
            <div class="message" @click="${this.toggleExpanded}">
              ${this.isExpanded ? '' : this.result.message}
            </div>
            <div class="tags ${this.hasLinksOrActions() ? 'hoverHide' : ''}">
              ${(this.result.tags ?? []).map(t => this.renderTag(t))}
            </div>
            ${this.renderLabel()} ${this.renderLinks()} ${this.renderActions()}
          </div>
          ${this.renderExpanded()}
        </td>
        <td class="expanderCol" @click="${this.toggleExpanded}">
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

  private hasLinksOrActions() {
    const linkCount = this.result?.links?.length ?? 0;
    const actionCount = this.result?.actions?.length ?? 0;
    // The primary link is rendered somewhere else, so it does not count here.
    return linkCount > 1 || actionCount > 0;
  }

  private renderPrimaryRunAction() {
    if (!this.result) return;
    const action = primaryRunAction(this.result);
    if (!action) return;
    return html`<gr-checks-action .action="${action}"></gr-checks-action>`;
  }

  private renderExpanded() {
    if (!this.isExpanded) return;
    return html`<gr-result-expanded
      .result="${this.result}"
    ></gr-result-expanded>`;
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
      <div class="summary" @click="${this.toggleExpanded}">${text}&nbsp;</div>
    `;
  }

  renderIcon() {
    if (this.result?.status !== RunStatus.RUNNING) return;
    return html`<iron-icon icon="gr-icons:timelapse"></iron-icon>`;
  }

  renderLabel() {
    const label = this.result?.labelName;
    if (!label) return;
    return html`
      <div class="label ${this.hasLinksOrActions() ? 'hoverHide' : ''}">
        ${label}
      </div>
    `;
  }

  renderLinks() {
    const links = (this.result?.links ?? []).slice(1);
    if (links.length === 0) return;
    return html`<div class="links">${links.map(this.renderLink)}</div>`;
  }

  renderLink(link: Link) {
    const tooltipText = link.tooltip ?? tooltipForLink(link.icon);
    return html`<a href="${link.url}" target="_blank"
      ><iron-icon
        aria-label="external link to details"
        class="link"
        icon="gr-icons:${iconForLink(link.icon)}"
      ></iron-icon
      ><paper-tooltip offset="5">${tooltipText}</paper-tooltip></a
    >`;
  }

  private renderActions() {
    const actions = this.result?.actions ?? [];
    if (actions.length === 0) return;
    const overflowItems = actions.slice(2).map(action => {
      return {...action, id: action.name};
    });
    const disabledItems = overflowItems
      .filter(action => action.disabled)
      .map(action => action.id);
    return html`<div class="actions">
      ${this.renderAction(actions[0])} ${this.renderAction(actions[1])}
      <gr-dropdown
        id="moreActions"
        link=""
        vertical-offset="32"
        horizontal-align="right"
        @tap-item="${this.handleAction}"
        @opened-changed="${(e: CustomEvent) =>
          toggleClass(this, 'dropdown-open', e.detail.value)}"
        ?hidden="${overflowItems.length === 0}"
        .items="${overflowItems}"
        .disabledIds="${disabledItems}"
      >
        <iron-icon icon="gr-icons:more-vert" aria-labelledby="moreMessage">
        </iron-icon>
        <span id="moreMessage">More</span>
      </gr-dropdown>
    </div>`;
  }

  private handleAction(e: CustomEvent<Action>) {
    fireActionTriggered(this, e.detail);
  }

  private renderAction(action?: Action) {
    if (!action) return;
    return html`<gr-checks-action .action="${action}"></gr-checks-action>`;
  }

  renderPrimaryActions() {
    const primaryActions = (this.result?.actions ?? []).slice(0, 2);
    if (primaryActions.length === 0) return;
    return html`
      <div class="primaryActions">${primaryActions.map(this.renderAction)}</div>
    `;
  }

  renderSecondaryActions() {
    const secondaryActions = (this.result?.actions ?? []).slice(2);
    if (secondaryActions.length === 0) return;
    return html`
      <div class="secondaryActions">
        ${secondaryActions.map(this.renderAction)}
      </div>
    `;
  }

  renderTag(tag: Tag) {
    return html`<div class="tag ${tag.color}">${tag.name}</div>`;
  }
}

@customElement('gr-result-expanded')
class GrResultExpanded extends GrLitElement {
  @property()
  result?: RunResult;

  @property()
  repoConfig?: ConfigInfo;

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

  constructor() {
    super();
    this.subscribe('repoConfig', repoConfig$);
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
        <gr-formatted-text
          no-trailing-margin=""
          class="message"
          content="${this.result.message}"
          config="${this.repoConfig}"
        ></gr-formatted-text>
      </gr-endpoint-decorator>
    `;
  }
}

const SHOW_ALL_THRESHOLDS: Map<Category, number> = new Map();
SHOW_ALL_THRESHOLDS.set(Category.ERROR, 20);
SHOW_ALL_THRESHOLDS.set(Category.WARNING, 10);
SHOW_ALL_THRESHOLDS.set(Category.INFO, 5);
SHOW_ALL_THRESHOLDS.set(Category.SUCCESS, 5);

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
export class GrChecksResults extends GrLitElement {
  @query('#filterInput')
  filterInput?: HTMLInputElement;

  @state()
  filterRegExp = new RegExp('');

  /** All runs. Shown should only the selected/filtered ones. */
  @property()
  runs: CheckRun[] = [];

  /**
   * Check names of runs that are selected in the runs panel. When this array
   * is empty, then no run is selected and all runs should be shown.
   */
  @property()
  selectedRuns: string[] = [];

  @property()
  actions: Action[] = [];

  @property()
  links: Link[] = [];

  @property()
  tabState?: ChecksTabState;

  @property()
  someProvidersAreLoading = false;

  @property()
  checksPatchsetNumber: PatchSetNumber | undefined = undefined;

  @property()
  latestPatchsetNumber: PatchSetNumber | undefined = undefined;

  /** Maps checkName to selected attempt number. `undefined` means `latest`. */
  @property()
  selectedAttempts: Map<string, number | undefined> = new Map<
    string,
    number | undefined
  >();

  /** Maintains the state of which result sections should show all results. */
  @state()
  isShowAll: Map<Category, boolean> = new Map();

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

  private readonly checksService = appContext.checksService;

  constructor() {
    super();
    this.subscribe('actions', allActions$);
    this.subscribe('links', allLinks$);
    this.subscribe('checksPatchsetNumber', checksPatchsetNumber$);
    this.subscribe('latestPatchsetNumber', latestPatchNum$);
    this.subscribe('someProvidersAreLoading', someProvidersAreLoading$);
  }

  static get styles() {
    return [
      sharedStyles,
      spinnerStyles,
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
        .headerTopRow,
        .headerBottomRow {
          max-width: 1600px;
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
        .headerBottomRow .right {
          display: flex;
          align-items: center;
        }
        .headerBottomRow .links iron-icon {
          color: var(--link-color);
          margin-right: var(--spacing-l);
        }
        #moreActions iron-icon {
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
          max-width: 1600px;
          margin-top: var(--spacing-m);
          background-color: var(--background-color-primary);
          box-shadow: var(--elevation-level-1);
          padding: var(--spacing-s)
            calc(20px + var(--spacing-l) + var(--spacing-m) + var(--spacing-s));
        }
        table.resultsTable {
          width: 100%;
          max-width: 1600px;
          table-layout: fixed;
          margin-top: var(--spacing-m);
          background-color: var(--background-color-primary);
          box-shadow: var(--elevation-level-1);
        }
        tr.headerRow th {
          text-align: left;
          font-weight: var(--font-weight-bold);
          padding: var(--spacing-s);
        }
        th.iconCol {
          width: 40px;
        }
        th.nameCol {
          width: 200px;
        }
        th.summaryCol {
          width: 99%;
        }
        th.expanderCol {
          width: 30px;
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

  private scrollElIntoView(selector: string) {
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
      <div class="header">
        <div class="headerTopRow">
          <div class="left">
            <h2 class="heading-2">Results</h2>
            <div class="loading" ?hidden="${!this.someProvidersAreLoading}">
              <span>Loading results </span>
              <span class="loadingSpin"></span>
            </div>
          </div>
          <div class="right">
            <gr-dropdown-list
              value="${this.checksPatchsetNumber}"
              .items="${this.createPatchsetDropdownItems()}"
              @value-change="${this.onPatchsetSelected}"
            ></gr-dropdown-list>
          </div>
        </div>
        <div class="headerBottomRow">
          <div class="left">${this.renderFilter()}</div>
          <div class="right">${this.renderLinks()}${this.renderActions()}</div>
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

  private renderLinks() {
    const links = (this.links ?? []).slice(0, 4);
    if (links.length === 0) return;
    return html`<div class="links">${links.map(this.renderLink)}</div>`;
  }

  private renderLink(link: Link) {
    const tooltipText = link.tooltip ?? tooltipForLink(link.icon);
    return html`<a href="${link.url}" target="_blank"
      ><iron-icon
        aria-label="${tooltipText}"
        class="link"
        icon="gr-icons:${iconForLink(link.icon)}"
      ></iron-icon
      ><paper-tooltip offset="5">${tooltipText}</paper-tooltip></a
    >`;
  }

  private renderActions() {
    const overflowItems = this.actions.slice(2).map(action => {
      return {...action, id: action.name};
    });
    const disabledItems = overflowItems
      .filter(action => action.disabled)
      .map(action => action.id);
    return html`
      ${this.renderAction(this.actions[0])}
      ${this.renderAction(this.actions[1])}
      <gr-dropdown
        id="moreActions"
        link=""
        vertical-offset="32"
        horizontal-align="right"
        @tap-item="${this.handleAction}"
        ?hidden="${overflowItems.length === 0}"
        .items="${overflowItems}"
        .disabledIds="${disabledItems}"
      >
        <iron-icon icon="gr-icons:more-vert" aria-labelledby="moreMessage">
        </iron-icon>
        <span id="moreMessage">More</span>
      </gr-dropdown>
    `;
  }

  private handleAction(e: CustomEvent<Action>) {
    fireActionTriggered(this, e.detail);
  }

  private renderAction(action?: Action) {
    if (!action) return;
    return html`<gr-checks-action .action="${action}"></gr-checks-action>`;
  }

  private onPatchsetSelected(e: CustomEvent<{value: string}>) {
    const patchset = Number(e.detail.value);
    check(!isNaN(patchset), 'selected patchset must be a number');
    this.checksService.setPatchset(patchset as PatchSetNumber);
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
    return (
      this.selectedRuns.length === 0 ||
      this.selectedRuns.includes(run.checkName)
    );
  }

  renderFilter() {
    const runs = this.runs.filter(
      run =>
        this.isRunSelected(run) && isAttemptSelected(this.selectedAttempts, run)
    );
    if (this.selectedRuns.length === 0 && allResults(runs).length <= 3) {
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
      </div>
    `;
  }

  onInput() {
    assertIsDefined(this.filterInput, 'filter <input> element');
    this.filterRegExp = new RegExp(this.filterInput.value, 'i');
  }

  renderSection(category: Category) {
    const catString = category.toString().toLowerCase();
    const allRuns = this.runs.filter(run =>
      isAttemptSelected(this.selectedAttempts, run)
    );
    const all = allRuns.reduce(
      (results: RunResult[], run) => [
        ...results,
        ...this.computeRunResults(category, run),
      ],
      []
    );
    const selected = all.filter(result => this.isRunSelected(result));
    const filtered = selected.filter(
      result =>
        this.filterRegExp.test(result.checkName) ||
        this.filterRegExp.test(result.summary) ||
        this.filterRegExp.test(result.message ?? '')
    );
    let expanded = this.isSectionExpanded.get(category);
    const expandedByUser = this.isSectionExpandedByUser.get(category) ?? false;
    if (!expandedByUser || expanded === undefined) {
      expanded = selected.length > 0;
      this.isSectionExpanded.set(category, expanded);
    }
    const expandedClass = expanded ? 'expanded' : 'collapsed';
    const icon = expanded ? 'gr-icons:expand-less' : 'gr-icons:expand-more';
    const isShowAll = this.isShowAll.get(category) ?? false;
    const showAllThreshold = SHOW_ALL_THRESHOLDS.get(category) ?? 5;
    const resultCount = filtered.length;
    const resultLimit = isShowAll ? 1000 : showAllThreshold;
    const showAllButton = this.renderShowAllButton(
      category,
      isShowAll,
      showAllThreshold,
      resultCount
    );
    return html`
      <div class="${expandedClass}">
        <h3
          class="categoryHeader ${catString} heading-3"
          @click="${() => this.toggleExpanded(category)}"
        >
          <iron-icon class="expandIcon" icon="${icon}"></iron-icon>
          <div class="statusIconWrapper">
            <iron-icon
              icon="gr-icons:${iconForCategory(category)}"
              class="statusIcon ${catString}"
            ></iron-icon>
            <span class="title">${catString}</span>
            <paper-tooltip offset="5"
              >${CATEGORY_TOOLTIPS.get(category)}</paper-tooltip
            >
          </div>
          <span class="count"
            >${this.renderCount(all, selected, filtered)}</span
          >
        </h3>
        ${this.renderResults(
          all,
          selected,
          filtered,
          resultLimit,
          showAllButton
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
        <td colspan="4">
          <gr-button class="showAll" link @click="${handler}"
            >${message}</gr-button
          >
        </td>
      </tr>
    `;
  }

  toggleShowAll(category: Category) {
    const current = this.isShowAll.get(category) ?? false;
    this.isShowAll.set(category, !current);
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
            result => result.internalResultId,
            result => html`
              <gr-result-row
                class="${charsOnly(result.checkName)}"
                .result="${result}"
              ></gr-result-row>
            `
          )}
          ${showAll}
        </tbody>
      </table>
    `;
  }

  renderCount(all: RunResult[], selected: RunResult[], filtered: RunResult[]) {
    if (all.length === filtered.length) {
      return html`(${all.length})`;
    }
    if (all.length !== selected.length) {
      return html`<span class="filtered"> - filtered</span>`;
    }
    return html`(${filtered.length} of ${all.length})`;
  }

  toggleExpanded(category: Category) {
    const expanded = this.isSectionExpanded.get(category);
    assertIsDefined(expanded, 'expanded must have been set in initial render');
    this.isSectionExpanded.set(category, !expanded);
    this.isSectionExpandedByUser.set(category, true);
    this.requestUpdate();
  }

  computeRunResults(category: Category, run: CheckRun) {
    const noResults = (run.results ?? []).length === 0;
    if (noResults && category === Category.SUCCESS) {
      return [this.computeSuccessfulRunResult(run)];
    }
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
      internalResultId: run.internalRunId + '-0',
      category: Category.SUCCESS,
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
