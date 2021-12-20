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
import {isOwner} from '../../../utils/change-util';
import {GrCursorManager} from '../../shared/gr-cursor-manager/gr-cursor-manager';
import {
  AccountInfo,
  ChangeInfo,
  ServerInfo,
  PreferencesInput,
} from '../../../types/common';
import {hasAttention} from '../../../utils/attention-set-util';
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
import {
  customElement,
  property /* ,
  state,*/,
} from 'lit/decorators';
import {ShortcutController} from '../../lit/shortcut-controller';
import {Shortcut} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {queryAll} from '../../../utils/common-util';
import {ValueChangedEvent} from '../../../types/events';

const NUMBER_FIXED_COLUMNS = 3;
const CLOSED_STATUS = ['MERGED', 'ABANDONED'];
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
  isOutgoing?: boolean;
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

  @property({type: Array})
  _dynamicHeaderEndpoints?: string[];

  @property({type: Number})
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

  @property({type: Object})
  _config?: ServerInfo;

  private readonly flagsService = getAppContext().flagsService;

  private readonly restApiService = getAppContext().restApiService;

  private readonly shortcuts = new ShortcutController(this);

  private cursor = new GrCursorManager();

  constructor() {
    super();
    this.cursor.scrollMode = ScrollMode.KEEP_VISIBLE;
    this.cursor.focusOnMove = true;
    this.shortcuts.addAbstract(Shortcut.CURSOR_NEXT_CHANGE, () =>
      this._nextChange()
    );
    this.shortcuts.addAbstract(Shortcut.CURSOR_PREV_CHANGE, () =>
      this._prevChange()
    );
    this.shortcuts.addAbstract(Shortcut.NEXT_PAGE, () => this._nextPage());
    this.shortcuts.addAbstract(Shortcut.PREV_PAGE, () => this._prevPage());
    this.shortcuts.addAbstract(Shortcut.OPEN_CHANGE, () => this.openChange());
    this.shortcuts.addAbstract(Shortcut.TOGGLE_CHANGE_STAR, () =>
      this._toggleChangeStar()
    );
    this.shortcuts.addAbstract(Shortcut.REFRESH_CHANGE_LIST, () =>
      this._refreshChangeList()
    );
    addGlobalShortcut({key: Key.ENTER}, () => this.openChange());
  }

  override connectedCallback() {
    super.connectedCallback();
    this.restApiService.getConfig().then(config => {
      this._config = config;
    });
    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        this._dynamicHeaderEndpoints =
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
    const labelNames = this._computeLabelNames(this.sections);
    return html`
      <table id="changeList">
        ${this.sections.map((changeSection, sectionIndex) =>
          this.renderChangeSections(changeSection, sectionIndex, labelNames)
        )}
      </table>
    `;
  }

  private renderChangeSections(
    changeSection: ChangeListSection,
    sectionIndex: number,
    labelNames: string[]
  ) {
    return html`
      ${this.renderCHangeSectionName(changeSection, labelNames)}
      <tbody class="groupContent">
        ${this._isEmpty(changeSection)
          ? this.renderEmptyChangeSection(changeSection, labelNames)
          : this.renderChangeSection(changeSection)}
        ${changeSection.results.map((change, index) =>
          this.renderChangeSectionResults(
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

  private renderCHangeSectionName(
    changeSection: ChangeListSection,
    labelNames: string[]
  ) {
    if (!changeSection.name) return;

    return html`
      <tbody>
        <tr class="groupHeader">
          <td aria-hidden="true" class="leftPadding"></td>
          <td aria-hidden="true" class="star" ?hidden=${!this.showStar}></td>
          <td
            class="cell"
            colspan="${this._computeColspan(
              changeSection,
              this.visibleChangeTableColumns,
              labelNames
            )}"
          >
            <h2 class="heading-3">
              <a
                href="${this._sectionHref(changeSection.query)}"
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

  private renderEmptyChangeSection(
    changeSection: ChangeListSection,
    labelNames: string[]
  ) {
    return html`
      <tr class="noChanges">
        <td class="leftPadding" ?aria-hidden="true"></td>
        <td
          class="star"
          ?aria-hidden=${!this.showStar}
          ?hidden=${!this.showStar}
        ></td>
        <td
          class="cell"
          colspan="${this._computeColspan(
            changeSection,
            this.visibleChangeTableColumns,
            labelNames
          )}"
        >
          ${this._getSpecialEmptySlot(changeSection)
            ? html`<slot
                name="${this._getSpecialEmptySlot(changeSection)}"
              ></slot>`
            : 'No changes'}
        </td>
      </tr>
    `;
  }

  private renderChangeSection(changeSection: ChangeListSection) {
    return html`
      <tr class="groupTitle">
        <td class="leftPadding" ?aria-hidden="true"></td>
        <td
          class="star"
          aria-label="Star status column"
          hidden=${!this.showStar}
        ></td>
        <td class="number" ?hidden=${!this.showNumber}>#</td>
        ${this._computeColumns(
          changeSection,
          this.visibleChangeTableColumns
        ).map(item => this.renderColumn(item))}
        ${this._computeLabelNames(this.sections).map(labelName =>
          this.renderLabelNames(labelName)
        )}
        ${this._dynamicHeaderEndpoints?.map(pluginHeader =>
          this.renderPluginEndpoint(pluginHeader)
        )}
      </tr>
    `;
  }

  private renderColumn(item: string) {
    return html`<td class="${this._lowerCase(item)}">${item}</td>`;
  }

  private renderLabelNames(labelName: string) {
    return html`
      <td class="label" title="${labelName}">
        ${this._computeLabelShortcut(labelName)}
      </td>
    `;
  }

  private renderPluginEndpoint(pluginHeader: string) {
    return html`
      <td class="endpoint">
        <gr-endpoint-decorator .name="${pluginHeader}"></gr-endpoint-decorator>
      </td>
    `;
  }

  private renderChangeSectionResults(
    changeSection: ChangeListSection,
    change: ChangeInfo,
    index: number,
    sectionIndex: number,
    labelNames: string[]
  ) {
    return html`
      <gr-change-list-item
        .account=${this.account}
        .selected=${this._computeItemSelected(
          sectionIndex,
          index,
          this.selectedIndex
        )}
        .highlight=${this._computeItemHighlight(
          this.account,
          change,
          changeSection.name
        )}
        .change=${change}
        .config=${this._config}
        .sectionName=${changeSection.name}
        .visibleChangeTableColumns=${this._computeColumns(
          changeSection,
          this.visibleChangeTableColumns
        )}
        .showNumber=${this.showNumber}
        .showStar=${this.showStar}
        .tabindex=${this._computeTabIndex(
          sectionIndex,
          index,
          this.isCursorMoving,
          this.selectedIndex
        )}
        .labelNames=${labelNames}
        aria-label=${this._computeAriaLabel(change, changeSection.name)}
      ></gr-change-list-item>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (
      changedProperties.has('account') ||
      changedProperties.has('preferences') ||
      changedProperties.has('_config') ||
      changedProperties.has('sections')
    ) {
      this._computePreferences();
    }

    if (changedProperties.has('changes')) {
      this._changesChanged();
    }
  }

  override updated(changedProperties: PropertyValues) {
    if (changedProperties.has('sections')) {
      this._sectionsChanged();
    }
  }

  _lowerCase(column: string) {
    return column.toLowerCase();
  }

  _computePreferences() {
    if (!this._config) return;

    const changes = (this.sections ?? [])
      .map(section => section.results)
      .flat();
    this.changeTableColumns = columnNames;
    this.showNumber = false;
    this.visibleChangeTableColumns = this.changeTableColumns.filter(col =>
      this._isColumnEnabled(col, this._config, changes)
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
          this._isColumnEnabled(col, this._config, changes)
        );
      }
    }
  }

  /**
   * Is the column disabled by a server config or experiment?
   */
  _isColumnEnabled(
    column: string,
    config?: ServerInfo,
    changes?: ChangeInfo[]
  ) {
    if (!columnNames.includes(column)) return false;
    if (!config || !config.change) return true;
    if (column === 'Comments')
      return this.flagsService.isEnabled('comments-column');
    if (column === 'Status') {
      return (changes ?? []).every(
        change => !showNewSubmitRequirements(this.flagsService, change)
      );
    }
    if (column === ' Status ')
      return (changes ?? []).some(change =>
        showNewSubmitRequirements(this.flagsService, change)
      );
    return true;
  }

  /**
   * This methods allows us to customize the columns per section.
   *
   * @param visibleColumns are the columns according to configs and user prefs
   */
  _computeColumns(
    section?: ChangeListSection,
    visibleColumns?: string[]
  ): string[] {
    if (!section || !visibleColumns) return [];
    const cols = [...visibleColumns];
    const updatedIndex = cols.indexOf('Updated');
    if (section.name === YOUR_TURN.name && updatedIndex !== -1) {
      cols[updatedIndex] = 'Waiting';
    }
    if (section.name === CLOSED.name && updatedIndex !== -1) {
      cols[updatedIndex] = 'Submitted';
    }
    return cols;
  }

  _computeColspan(
    section?: ChangeListSection,
    visibleColumns?: string[],
    labelNames?: string[]
  ) {
    const cols = this._computeColumns(section, visibleColumns);
    if (!cols || !labelNames) return 1;
    return cols.length + labelNames.length + NUMBER_FIXED_COLUMNS;
  }

  _computeLabelNames(sections: ChangeListSection[]) {
    if (!sections) {
      return [];
    }
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
    const changes = sections.map(section => section.results).flat();
    if (
      (changes ?? []).some(change =>
        showNewSubmitRequirements(this.flagsService, change)
      )
    ) {
      labels = (changes ?? [])
        .map(change => getRequirements(change))
        .flat()
        .map(requirement => requirement.name)
        .filter(unique);
    }
    return labels.sort();
  }

  _computeLabelShortcut(labelName: string) {
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

  _changesChanged() {
    this.sections = this.changes ? [{results: this.changes}] : [];
  }

  _processQuery(query: string) {
    let tokens = query.split(' ');
    const invalidTokens = ['limit:', 'age:', '-age:'];
    tokens = tokens.filter(
      token =>
        !invalidTokens.some(invalidToken => token.startsWith(invalidToken))
    );
    return tokens.join(' ');
  }

  _sectionHref(query?: string) {
    if (!query) return;
    return GerritNav.getUrlForSearchQuery(this._processQuery(query));
  }

  /**
   * Maps an index local to a particular section to the absolute index
   * across all the changes on the page.
   *
   * @param sectionIndex index of section
   * @param localIndex index of row within section
   * @return absolute index of row in the aggregate dashboard
   */
  _computeItemAbsoluteIndex(localIndex: number, sectionIndex?: number) {
    if (sectionIndex === undefined) return 0 + localIndex;
    let idx = 0;
    for (let i = 0; i < sectionIndex; i++) {
      idx += this.sections[i].results.length;
    }
    return idx + localIndex;
  }

  _computeItemSelected(
    sectionIndex: number,
    index: number,
    selectedIndex?: number
  ) {
    const idx = this._computeItemAbsoluteIndex(index, sectionIndex);
    return idx === selectedIndex;
  }

  _computeTabIndex(
    sectionIndex: number,
    index: number,
    isCursorMoving: boolean,
    selectedIndex?: number
  ) {
    if (isCursorMoving) return 0;
    return this._computeItemSelected(sectionIndex, index, selectedIndex)
      ? 0
      : undefined;
  }

  _computeItemHighlight(
    account?: AccountInfo,
    change?: ChangeInfo,
    sectionName?: string
  ) {
    if (!change || !account) return false;
    if (CLOSED_STATUS.indexOf(change.status) !== -1) return false;
    return (
      hasAttention(account, change) &&
      !isOwner(change, account) &&
      sectionName === YOUR_TURN.name
    );
  }

  _nextChange() {
    this.isCursorMoving = true;
    this.cursor.next();
    this.isCursorMoving = false;
    this.selectedIndex = this.cursor.index;
    fire(this, 'selected-index-changed', {value: String(this.cursor.index)});
  }

  _prevChange() {
    this.isCursorMoving = true;
    this.cursor.previous();
    this.isCursorMoving = false;
    this.selectedIndex = this.cursor.index;
    fire(this, 'selected-index-changed', {value: String(this.cursor.index)});
  }

  openChange() {
    const change = this._changeForIndex(this.selectedIndex);
    if (change) GerritNav.navigateToChange(change);
  }

  _nextPage() {
    fireEvent(this, 'next-page');
  }

  _prevPage() {
    fireEvent(this, 'previous-page');
  }

  _refreshChangeList() {
    fireReload(this);
  }

  _toggleChangeStar() {
    this._toggleStarForIndex(this.selectedIndex);
  }

  _toggleStarForIndex(index?: number) {
    const changeEls = this._getListItems();
    if (index === undefined || index >= changeEls.length || !changeEls[index]) {
      return;
    }

    const changeEl = changeEls[index];
    const grChangeStar = changeEl?.shadowRoot?.querySelector('gr-change-star');
    if (grChangeStar) grChangeStar.toggleStar();
  }

  _changeForIndex(index?: number) {
    const changeEls = this._getListItems();
    if (index !== undefined && index < changeEls.length && changeEls[index]) {
      return changeEls[index].change;
    }
    return null;
  }

  _getListItems() {
    const items = queryAll<GrChangeListItem>(this, 'gr-change-list-item');
    return !items ? [] : Array.from(items);
  }

  _sectionsChanged() {
    this.cursor.stops = this._getListItems();
    this.cursor.moveToStart();
    if (this.selectedIndex) this.cursor.setCursorAtIndex(this.selectedIndex);
  }

  _getSpecialEmptySlot(section: ChangeListSection) {
    if (section.isOutgoing) return 'empty-outgoing';
    if (section.name === YOUR_TURN.name) return 'empty-your-turn';
    return '';
  }

  _isEmpty(section: ChangeListSection) {
    return !section.results?.length;
  }

  _computeAriaLabel(change?: ChangeInfo, sectionName?: string) {
    if (!change) return '';
    return change.subject + (sectionName ? `, section: ${sectionName}` : '');
  }
}

declare global {
  interface HTMLElementEventMap {
    'selected-index-changed': ValueChangedEvent;
  }
  interface HTMLElementTagNameMap {
    'gr-change-list': GrChangeList;
  }
}
