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
import '../gr-change-list-section/gr-change-list-section';
import {GrChangeListItem} from '../gr-change-list-item/gr-change-list-item';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import {getAppContext} from '../../../services/app-context';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
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
import {ColumnNames, ScrollMode} from '../../../constants/constants';
import {getRequirements} from '../../../utils/label-util';
import {addGlobalShortcut, Key} from '../../../utils/dom-util';
import {unique} from '../../../utils/common-util';
import {changeListStyles} from '../../../styles/gr-change-list-styles';
import {fontStyles} from '../../../styles/gr-font-styles';
import {sharedStyles} from '../../../styles/shared-styles';
import {LitElement, PropertyValues, html, css, nothing} from 'lit';
import {customElement, property, state} from 'lit/decorators';
import {ShortcutController} from '../../lit/shortcut-controller';
import {Shortcut} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {queryAll} from '../../../utils/common-util';
import {ValueChangedEvent} from '../../../types/events';
import {GrChangeListSection} from '../gr-change-list-section/gr-change-list-section';
import {Execution} from '../../../constants/reporting';

export interface ChangeListSection {
  countLabel?: string;
  emptyStateSlotName?: string;
  name?: string;
  query?: string;
  results: ChangeInfo[];
}

/**
 * Calculate the relative index of the currently selected change wrt to the
 * section it belongs to.
 * The 10th change in the overall list may be the 4th change in it's section
 * so this method maps 10 to 4.
 * selectedIndex contains the index of the change wrt the entire change list.
 * Private but used in test
 *
 */
export function computeRelativeIndex(
  selectedIndex?: number,
  sectionIndex?: number,
  sections?: ChangeListSection[]
) {
  if (
    selectedIndex === undefined ||
    sectionIndex === undefined ||
    sections === undefined
  )
    return;
  for (let i = 0; i < sectionIndex; i++)
    selectedIndex -= sections[i].results.length;
  if (selectedIndex < 0) return; // selected change lies in previous sections

  // the selectedIndex lies in the current section
  if (selectedIndex < sections[sectionIndex].results.length)
    return selectedIndex;
  return; // selected change lies in future sections
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
  sections?: ChangeListSection[] = [];

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

  private readonly reporting = getAppContext().reportingService;

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
    if (!this.sections) return;
    const labelNames = this.computeLabelNames(this.sections);
    return html`
      <table id="changeList">
        ${this.sections.map((changeSection, sectionIndex) =>
          this.renderSection(changeSection, sectionIndex, labelNames)
        )}
      </table>
    `;
  }

  private renderSection(
    changeSection: ChangeListSection,
    sectionIndex: number,
    labelNames: string[]
  ) {
    return html`
      <gr-change-list-section
        .changeSection=${changeSection}
        .labelNames=${labelNames}
        .dynamicHeaderEndpoints=${this.dynamicHeaderEndpoints}
        .isCursorMoving=${this.isCursorMoving}
        .config=${this.config}
        .account=${this.account}
        .selectedIndex=${computeRelativeIndex(
          this.selectedIndex,
          sectionIndex,
          this.sections
        )}
        ?showStar=${this.showStar}
        .showNumber=${this.showNumber}
        .visibleChangeTableColumns=${this.visibleChangeTableColumns}
      >
        ${changeSection.emptyStateSlotName
          ? html`<slot
              slot=${changeSection.emptyStateSlotName}
              name=${changeSection.emptyStateSlotName}
            ></slot>`
          : nothing}
      </gr-change-list-section>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (
      changedProperties.has('account') ||
      changedProperties.has('preferences') ||
      changedProperties.has('config') ||
      changedProperties.has('sections')
    ) {
      this.computeVisibleChangeTableColumns();
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

  private computeVisibleChangeTableColumns() {
    if (!this.config) return;

    this.changeTableColumns = Object.values(ColumnNames);
    this.showNumber = false;
    this.visibleChangeTableColumns = this.changeTableColumns.filter(col =>
      this.isColumnEnabled(col, this.config)
    );
    if (this.account && this.preferences) {
      this.showNumber = !!this.preferences?.legacycid_in_change_table;
      if (
        this.preferences?.change_table &&
        this.preferences.change_table.length > 0
      ) {
        const prefColumns = this.preferences.change_table
          .map(column => (column === 'Project' ? ColumnNames.REPO : column))
          .map(column =>
            column === ColumnNames.STATUS ? ColumnNames.STATUS2 : column
          );
        this.reporting.reportExecution(Execution.USER_PREFERENCES_COLUMNS, {
          statusColumn: prefColumns.includes(ColumnNames.STATUS2),
        });
        // Order visible column names by columnNames, filter only one that
        // are in prefColumns and enabled by config
        this.visibleChangeTableColumns = Object.values(ColumnNames)
          .filter(col => prefColumns.includes(col))
          .filter(col => this.isColumnEnabled(col, this.config));
      }
    }
  }

  /**
   * Is the column disabled by a server config or experiment?
   */
  isColumnEnabled(column: string, config?: ServerInfo) {
    if (!Object.values(ColumnNames).includes(column as unknown as ColumnNames))
      return false;
    if (!config || !config.change) return true;
    if (column === 'Comments')
      return this.flagsService.isEnabled('comments-column');
    if (column === 'Status') return false;
    if (column === ColumnNames.STATUS2) return true;
    return true;
  }

  // private but used in test
  computeLabelNames(sections: ChangeListSection[]) {
    if (!sections) return [];
    if (this.config?.submit_requirement_dashboard_columns?.length) {
      return this.config?.submit_requirement_dashboard_columns;
    }
    const changes = sections.map(section => section.results).flat();
    const labels = (changes ?? [])
      .map(change => getRequirements(change))
      .flat()
      .map(requirement => requirement.name)
      .filter(unique);
    return labels.sort();
  }

  private changesChanged() {
    this.sections = this.changes ? [{results: this.changes}] : [];
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

  private async openChange() {
    const change = await this.changeForIndex(this.selectedIndex);
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

  private async toggleStarForIndex(index?: number) {
    const changeEls = await this.getListItems();
    if (index === undefined || index >= changeEls.length || !changeEls[index]) {
      return;
    }

    const changeEl = changeEls[index];
    const grChangeStar = changeEl?.shadowRoot?.querySelector('gr-change-star');
    if (grChangeStar) grChangeStar.toggleStar();
  }

  private async changeForIndex(index?: number) {
    const changeEls = await this.getListItems();
    if (index !== undefined && index < changeEls.length && changeEls[index]) {
      return changeEls[index].change;
    }
    return null;
  }

  // Private but used in tests
  async getListItems() {
    const items: GrChangeListItem[] = [];
    const sections = queryAll<GrChangeListSection>(
      this,
      'gr-change-list-section'
    );
    await Promise.all(Array.from(sections).map(s => s.updateComplete));
    for (const section of sections) {
      // getListItems() is triggered when sectionsChanged observer is triggered
      // In some cases <gr-change-list-item> has not been attached to the DOM
      // yet and hence queryAll returns []
      // Once the items have been attached, sectionsChanged() is not called
      // again and the cursor stops are not updated to have the correct value
      // hence wait for section to render before querying for items
      const res = queryAll<GrChangeListItem>(section, 'gr-change-list-item');
      items.push(...res);
    }
    return items;
  }

  // Private but used in tests
  async sectionsChanged() {
    this.cursor.stops = await this.getListItems();
    this.cursor.moveToStart();
    if (this.selectedIndex) this.cursor.setCursorAtIndex(this.selectedIndex);
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
