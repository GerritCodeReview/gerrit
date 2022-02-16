/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import '../../shared/gr-cursor-manager/gr-cursor-manager';
import '../gr-change-list-item/gr-change-list-item';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import {getAppContext} from '../../../services/app-context';
import {
  GerritNav,
  YOUR_TURN,
  CLOSED,
} from '../../core/gr-navigation/gr-navigation';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {GrCursorManager} from '../../shared/gr-cursor-manager/gr-cursor-manager';
import {
  AccountInfo,
  ChangeInfo,
  ServerInfo,
  PreferencesInput,
} from '../../../types/common';
import {fire, fireEvent, fireReload} from '../../../utils/event-util';
import {ScrollMode} from '../../../constants/constants';
import {
  getRequirements,
  showNewSubmitRequirements,
} from '../../../utils/label-util';
import {addGlobalShortcut, Key} from '../../../utils/dom-util';
import {unique} from '../../../utils/common-util';
import {changeListStyles} from '../../../styles/gr-change-list-styles';
import {fontStyles} from '../../../styles/gr-font-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {ShortcutController} from '../../lit/shortcut-controller';
import {Shortcut} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {queryAll} from '../../../utils/common-util';
import {ValueChangedEvent} from '../../../types/events';
import {KnownExperimentId} from '../../../services/flags/flags';

const NUMBER_FIXED_COLUMNS = 3;
const LABEL_PREFIX_INVALID_PROLOG = 'Invalid-Prolog-Rules-Label-Name--';
const MAX_SHORTCUT_CHARS = 5;

export const columnNames = [
  'Subject',
  // TODO(milutin) - remove once Submit Requirements are rolled out.
  'Status',
  'Owner',
  'Reviewers',
  'Comments',
  'Repo',
  'Branch',
  'Updated',
  'Size',
  ' Status ', // spaces to differentiate from old 'Status'
];

export interface ChangeListSection {
  countLabel?: string;
  emptyStateSlotName?: string;
  name?: string;
  query?: string;
  results: ChangeInfo[];
}

@customElement('gr-change-list')
export class GrChangeList extends LitElement {
  /**
   * Fired when next page key shortcut was pressed.
   *
   * @event next-page
   */

  /**
   * Fired when previous page key shortcut was pressed.
   *
   * @event previous-page
   */

  /**
   * The logged-in user's account, or an empty object if no user is logged
   * in.
   */
  @property({type: Object})
  account: AccountInfo | undefined = undefined;

  @property({type: Array})
  changes?: ChangeInfo[];

  /**
   * ChangeInfo objects grouped into arrays. The sections and changes
   * properties should not be used together.
   */
  @property({type: Array})
  sections: ChangeListSection[] = [];

  @state() private dynamicHeaderEndpoints?: string[];

  @property({type: Number, attribute: 'selected-index'})
  selectedIndex?: number;

  @property({type: Boolean})
  showNumber?: boolean; // No default value to prevent flickering.

  @property({type: Boolean})
  showStar = false;

  @property({type: Boolean})
  showReviewedState = false;

  @property({type: Array})
  changeTableColumns?: string[];

  @property({type: Array})
  visibleChangeTableColumns?: string[];

  @property({type: Object})
  preferences?: PreferencesInput;

  @property({type: Boolean})
  isCursorMoving = false;

  // private but used in test
  @state() config?: ServerInfo;

  private readonly flagsService = getAppContext().flagsService;

  private readonly restApiService = getAppContext().restApiService;

  private readonly shortcuts = new ShortcutController(this);

  private cursor = new GrCursorManager();

  constructor() {
    super();
    this.cursor.scrollMode = ScrollMode.KEEP_VISIBLE;
    this.cursor.focusOnMove = true;
    this.shortcuts.addAbstract(Shortcut.CURSOR_NEXT_CHANGE, () =>
      this.nextChange()
    );
    this.shortcuts.addAbstract(Shortcut.CURSOR_PREV_CHANGE, () =>
      this.prevChange()
    );
    this.shortcuts.addAbstract(Shortcut.NEXT_PAGE, () => this.nextPage());
    this.shortcuts.addAbstract(Shortcut.PREV_PAGE, () => this.prevPage());
    this.shortcuts.addAbstract(Shortcut.OPEN_CHANGE, () => this.openChange());
    this.shortcuts.addAbstract(Shortcut.TOGGLE_CHANGE_STAR, () =>
      this.toggleChangeStar()
    );
    this.shortcuts.addAbstract(Shortcut.REFRESH_CHANGE_LIST, () =>
      this.refreshChangeList()
    );
    addGlobalShortcut({key: Key.ENTER}, () => this.openChange());
  }

  override connectedCallback() {
    super.connectedCallback();
    this.restApiService.getConfig().then(config => {
      this.config = config;
    });
    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        this.dynamicHeaderEndpoints =
          getPluginEndpoints().getDynamicEndpoints('change-list-header');
      });
  }

  override disconnectedCallback() {
    this.cursor.unsetCursor();
    super.disconnectedCallback();
  }

  static override get styles() {
    return [
      changeListStyles,
      fontStyles,
      sharedStyles,
      css`
        #changeList {
          border-collapse: collapse;
          width: 100%;
        }
        .section-count-label {
          color: var(--deemphasized-text-color);
          font-family: var(--font-family);
          font-size: var(--font-size-small);
          font-weight: var(--font-weight-normal);
          line-height: var(--line-height-small);
        }
        a.section-title:hover {
          text-decoration: none;
        }
        a.section-title:hover .section-count-label {
          text-decoration: none;
        }
        a.section-title:hover .section-name {
          text-decoration: underline;
        }
      `,
    ];
  }

  override render() {
    const labelNames = this.computeLabelNames(this.sections);
    return html`
      <table id="changeList">
        ${this.sections.map((changeSection, sectionIndex) =>
          this.renderSections(changeSection, sectionIndex, labelNames)
        )}
      </table>
    `;
  }

  private renderSections(
    changeSection: ChangeListSection,
    sectionIndex: number,
    labelNames: string[]
  ) {
    return html`
      ${this.renderSectionHeader(changeSection, labelNames)}
      <tbody class="groupContent">
        ${this.isEmpty(changeSection)
          ? this.renderNoChangesRow(changeSection, labelNames)
          : this.renderColumnHeaders(changeSection, labelNames)}
        ${changeSection.results.map((change, index) =>
          this.renderChangeRow(
            changeSection,
            change,
            index,
            sectionIndex,
            labelNames
          )
        )}
      </tbody>
    `;
  }

  private renderSelectionHeader() {
    if (!this.flagsService.isEnabled(KnownExperimentId.BULK_ACTIONS)) return;
    return html`<td aria-hidden="true" class="selection"></td>`;
  }

  private renderSectionHeader(
    changeSection: ChangeListSection,
    labelNames: string[]
  ) {
    if (!changeSection.name) return;

    return html`
      <tbody>
        <tr class="groupHeader">
          <td aria-hidden="true" class="leftPadding"></td>
          ${this.renderSelectionHeader()}
          <td aria-hidden="true" class="star" ?hidden=${!this.showStar}></td>
          <td
            class="cell"
            colspan="${this.computeColspan(changeSection, labelNames)}"
          >
            <h2 class="heading-3">
              <a
                href="${this.sectionHref(changeSection.query)}"
                class="section-title"
              >
                <span class="section-name">${changeSection.name}</span>
                <span class="section-count-label"
                  >${changeSection.countLabel}</span
                >
              </a>
            </h2>
          </td>
        </tr>
      </tbody>
    `;
  }

  private renderNoChangesRow(
    changeSection: ChangeListSection,
    labelNames: string[]
  ) {
    return html`
      <tr class="noChanges">
        <td class="leftPadding" aria-hidden="true"></td>
        <td
          class="star"
          ?aria-hidden=${!this.showStar}
          ?hidden=${!this.showStar}
        ></td>
        <td
          class="cell"
          colspan="${this.computeColspan(changeSection, labelNames)}"
        >
          ${changeSection.emptyStateSlotName
            ? html`<slot name="${changeSection.emptyStateSlotName}"></slot>`
            : 'No changes'}
        </td>
      </tr>
    `;
  }

  private renderColumnHeaders(
    changeSection: ChangeListSection,
    labelNames: string[]
  ) {
    return html`
      <tr class="groupTitle">
        <td class="leftPadding" aria-hidden="true"></td>
        ${this.renderSelectionHeader()}
        <td
          class="star"
          aria-label="Star status column"
          ?hidden=${!this.showStar}
        ></td>
        <td class="number" ?hidden=${!this.showNumber}>#</td>
        ${this.computeColumns(changeSection).map(item =>
          this.renderHeaderCell(item)
        )}
        ${labelNames?.map(labelName => this.renderLabelHeader(labelName))}
        ${this.dynamicHeaderEndpoints?.map(pluginHeader =>
          this.renderEndpointHeader(pluginHeader)
        )}
      </tr>
    `;
  }

  private renderHeaderCell(item: string) {
    return html`<td class="${item.toLowerCase()}">${item}</td>`;
  }

  private renderLabelHeader(labelName: string) {
    return html`
      <td class="label" title="${labelName}">
        ${this.computeLabelShortcut(labelName)}
      </td>
    `;
  }

  private renderEndpointHeader(pluginHeader: string) {
    return html`
      <td class="endpoint">
        <gr-endpoint-decorator .name="${pluginHeader}"></gr-endpoint-decorator>
      </td>
    `;
  }

  private renderChangeRow(
    changeSection: ChangeListSection,
    change: ChangeInfo,
    index: number,
    sectionIndex: number,
    labelNames: string[]
  ) {
    const ariaLabel = this.computeAriaLabel(change, changeSection.name);
    const selected = this.computeItemSelected(
      sectionIndex,
      index,
      this.selectedIndex
    );
    const tabindex = this.computeTabIndex(
      sectionIndex,
      index,
      this.isCursorMoving,
      this.selectedIndex
    );
    const visibleChangeTableColumns = this.computeColumns(changeSection);
    return html`
      <gr-change-list-item
        .account=${this.account}
        ?selected=${selected}
        .change=${change}
        .config=${this.config}
        .sectionName=${changeSection.name}
        .visibleChangeTableColumns=${visibleChangeTableColumns}
        .showNumber=${this.showNumber}
        .showStar=${this.showStar}
        ?tabindex=${tabindex}
        .labelNames=${labelNames}
        aria-label=${ariaLabel}
      ></gr-change-list-item>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (
      changedProperties.has('account') ||
      changedProperties.has('preferences') ||
      changedProperties.has('config') ||
      changedProperties.has('sections')
    ) {
      this.computePreferences();
    }

    if (changedProperties.has('changes')) {
      this.changesChanged();
    }
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('sections')) {
      this.sectionsChanged();
    }
  }

  private computePreferences() {
    if (!this.config) return;

    this.changeTableColumns = columnNames;
    this.showNumber = false;
    this.visibleChangeTableColumns = this.changeTableColumns.filter(col =>
      this._isColumnEnabled(col, this.config)
    );
    if (this.account && this.preferences) {
      this.showNumber = !!this.preferences?.legacycid_in_change_table;
      if (
        this.preferences?.change_table &&
        this.preferences.change_table.length > 0
      ) {
        const prefColumns = this.preferences.change_table.map(column =>
          column === 'Project' ? 'Repo' : column
        );
        this.visibleChangeTableColumns = prefColumns.filter(col =>
          this._isColumnEnabled(col, this.config)
        );
      }
    }
  }

  /**
   * Is the column disabled by a server config or experiment?
   */
  _isColumnEnabled(column: string, config?: ServerInfo) {
    if (!columnNames.includes(column)) return false;
    if (!config || !config.change) return true;
    if (column === 'Comments')
      return this.flagsService.isEnabled('comments-column');
    if (column === 'Status') {
      return !showNewSubmitRequirements(this.flagsService);
    }
    if (column === ' Status ')
      return showNewSubmitRequirements(this.flagsService);
    return true;
  }

  /**
   * This methods allows us to customize the columns per section.
   *
   * @param visibleColumns are the columns according to configs and user prefs
   */
  private computeColumns(section?: ChangeListSection): string[] {
    if (!section || !this.visibleChangeTableColumns) return [];
    const cols = [...this.visibleChangeTableColumns];
    const updatedIndex = cols.indexOf('Updated');
    if (section.name === YOUR_TURN.name && updatedIndex !== -1) {
      cols[updatedIndex] = 'Waiting';
    }
    if (section.name === CLOSED.name && updatedIndex !== -1) {
      cols[updatedIndex] = 'Submitted';
    }
    return cols;
  }

  // private but used in test
  computeColspan(section?: ChangeListSection, labelNames?: string[]) {
    const cols = this.computeColumns(section);
    if (!cols || !labelNames) return 1;
    return cols.length + labelNames.length + NUMBER_FIXED_COLUMNS;
  }

  // private but used in test
  computeLabelNames(sections: ChangeListSection[]) {
    if (!sections) return [];
    let labels: string[] = [];
    const nonExistingLabel = function (item: string) {
      return !labels.includes(item);
    };
    for (const section of sections) {
      if (!section.results) {
        continue;
      }
      for (const change of section.results) {
        if (!change.labels) {
          continue;
        }
        const currentLabels = Object.keys(change.labels);
        labels = labels.concat(currentLabels.filter(nonExistingLabel));
      }
    }

    if (showNewSubmitRequirements(this.flagsService)) {
      if (this.config?.submit_requirement_dashboard_columns?.length) {
        return this.config?.submit_requirement_dashboard_columns;
      } else {
        const changes = sections.map(section => section.results).flat();
        labels = (changes ?? [])
          .map(change => getRequirements(change))
          .flat()
          .map(requirement => requirement.name)
          .filter(unique);
      }
    }
    return labels.sort();
  }

  // private but used in test
  computeLabelShortcut(labelName: string) {
    if (labelName.startsWith(LABEL_PREFIX_INVALID_PROLOG)) {
      labelName = labelName.slice(LABEL_PREFIX_INVALID_PROLOG.length);
    }
    return labelName
      .split('-')
      .reduce((a, i) => {
        if (!i) {
          return a;
        }
        return a + i[0].toUpperCase();
      }, '')
      .slice(0, MAX_SHORTCUT_CHARS);
  }

  private changesChanged() {
    this.sections = this.changes ? [{results: this.changes}] : [];
  }

  // private but used in test
  processQuery(query: string) {
    let tokens = query.split(' ');
    const invalidTokens = ['limit:', 'age:', '-age:'];
    tokens = tokens.filter(
      token =>
        !invalidTokens.some(invalidToken => token.startsWith(invalidToken))
    );
    return tokens.join(' ');
  }

  private sectionHref(query?: string) {
    if (!query) return;
    return GerritNav.getUrlForSearchQuery(this.processQuery(query));
  }

  /**
   * Maps an index local to a particular section to the absolute index
   * across all the changes on the page.
   *
   * private but used in test
   *
   * @param sectionIndex index of section
   * @param localIndex index of row within section
   * @return absolute index of row in the aggregate dashboard
   */
  computeItemAbsoluteIndex(sectionIndex: number, localIndex: number) {
    let idx = 0;
    for (let i = 0; i < sectionIndex; i++) {
      idx += this.sections[i].results.length;
    }
    return idx + localIndex;
  }

  private computeItemSelected(
    sectionIndex: number,
    index: number,
    selectedIndex?: number
  ) {
    const idx = this.computeItemAbsoluteIndex(sectionIndex, index);
    return idx === selectedIndex;
  }

  private computeTabIndex(
    sectionIndex: number,
    index: number,
    isCursorMoving: boolean,
    selectedIndex?: number
  ) {
    if (isCursorMoving) return 0;
    return this.computeItemSelected(sectionIndex, index, selectedIndex)
      ? 0
      : undefined;
  }

  private nextChange() {
    this.isCursorMoving = true;
    this.cursor.next();
    this.isCursorMoving = false;
    this.selectedIndex = this.cursor.index;
    fire(this, 'selected-index-changed', {value: this.cursor.index});
  }

  private prevChange() {
    this.isCursorMoving = true;
    this.cursor.previous();
    this.isCursorMoving = false;
    this.selectedIndex = this.cursor.index;
    fire(this, 'selected-index-changed', {value: this.cursor.index});
  }

  private openChange() {
    const change = this.changeForIndex(this.selectedIndex);
    if (change) GerritNav.navigateToChange(change);
  }

  private nextPage() {
    fireEvent(this, 'next-page');
  }

  private prevPage() {
    fireEvent(this, 'previous-page');
  }

  private refreshChangeList() {
    fireReload(this);
  }

  private toggleChangeStar() {
    this.toggleStarForIndex(this.selectedIndex);
  }

  private toggleStarForIndex(index?: number) {
    const changeEls = this.getListItems();
    if (index === undefined || index >= changeEls.length || !changeEls[index]) {
      return;
    }

    const changeEl = changeEls[index];
    const grChangeStar = changeEl?.shadowRoot?.querySelector('gr-change-star');
    if (grChangeStar) grChangeStar.toggleStar();
  }

  private changeForIndex(index?: number) {
    const changeEls = this.getListItems();
    if (index !== undefined && index < changeEls.length && changeEls[index]) {
      return changeEls[index].change;
    }
    return null;
  }

  private getListItems() {
    const items = queryAll<GrChangeListItem>(this, 'gr-change-list-item');
    return !items ? [] : Array.from(items);
  }

  private sectionsChanged() {
    this.cursor.stops = this.getListItems();
    this.cursor.moveToStart();
    if (this.selectedIndex) this.cursor.setCursorAtIndex(this.selectedIndex);
  }

  private isEmpty(section: ChangeListSection) {
    return !section.results?.length;
  }

  private computeAriaLabel(change?: ChangeInfo, sectionName?: string) {
    if (!change) return '';
    return change.subject + (sectionName ? `, section: ${sectionName}` : '');
  }
}

declare global {
  interface HTMLElementEventMap {
    'selected-index-changed': ValueChangedEvent<number>;
  }
  interface HTMLElementTagNameMap {
    'gr-change-list': GrChangeList;
  }
}
