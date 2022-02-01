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
import {GrChangeListSection} from '../gr-change-list-section/gr-change-list-section';

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

  @state() private dynamicHeaderEndpoints?: string[];

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
        .sectionIndex=${sectionIndex}
        .labelNames=${labelNames}
        .dynamicHeaderEndpoints=${this.dynamicHeaderEndpoints}
        .isCursorMoving=${this.isCursorMoving}
        .config=${this.config}
        .account=${this.account}
        .sections=${this.sections}
        .selectedIndex=${this.selectedIndex}
        .visibleChangeTableColumns=${this.visibleChangeTableColumns}
      ></gr-change-list-section>
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

    const changes = (this.sections ?? [])
      .map(section => section.results)
      .flat();
    this.changeTableColumns = columnNames;
    this.showNumber = false;
    this.visibleChangeTableColumns = this.changeTableColumns.filter(col =>
      this._isColumnEnabled(col, this.config, changes)
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
          this._isColumnEnabled(col, this.config, changes)
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

    if (this.flagsService.isEnabled(KnownExperimentId.SUBMIT_REQUIREMENTS_UI)) {
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

  private async getListItems() {
    const items: GrChangeListItem[] = [];
    const sections = queryAll<GrChangeListSection>(
      this,
      'gr-change-list-section'
    );
    for (const section of sections) {
      await section.updateComplete;
      const res = queryAll<GrChangeListItem>(section, 'gr-change-list-item');
      items.push(...Array.from(res));
    }
    sections.forEach(section => {
      const res = queryAll<GrChangeListItem>(section, 'gr-change-list-item');
      items.push(...Array.from(res));
    });
    return items;
  }

  private async sectionsChanged() {
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
