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

import '../../../styles/gr-change-list-styles';
import '../../shared/gr-cursor-manager/gr-cursor-manager';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../gr-change-list-item/gr-change-list-item';
import '../../../styles/shared-styles';
import '../../plugins/gr-endpoint-decorator/gr-endpoint-decorator';
import {afterNextRender} from '@polymer/polymer/lib/utils/render-status';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-change-list_html';
import {appContext} from '../../../services/app-context';
import {ChangeTableMixin} from '../../../mixins/gr-change-table-mixin/gr-change-table-mixin';
import {
  KeyboardShortcutMixin,
  Shortcut,
  CustomKeyboardEvent,
  Modifier,
} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {
  GerritNav,
  DashboardSection,
  YOUR_TURN,
} from '../../core/gr-navigation/gr-navigation';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {changeIsOpen, isOwner} from '../../../utils/change-util';
import {customElement, property, observe} from '@polymer/decorators';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {GrCursorManager} from '../../shared/gr-cursor-manager/gr-cursor-manager';
import {
  AccountInfo,
  ChangeInfo,
  ServerInfo,
  PreferencesInput,
} from '../../../types/common';
import {hasAttention, isAttentionSetEnabled} from '../../../utils/account-util';

const NUMBER_FIXED_COLUMNS = 3;
const CLOSED_STATUS = ['MERGED', 'ABANDONED'];
const LABEL_PREFIX_INVALID_PROLOG = 'Invalid-Prolog-Rules-Label-Name--';
const MAX_SHORTCUT_CHARS = 5;

export interface ChangeListSection {
  results: ChangeInfo[];
}
export interface GrChangeList {
  $: {
    restAPI: RestApiService & Element;
    cursor: GrCursorManager;
  };
}
@customElement('gr-change-list')
export class GrChangeList extends ChangeTableMixin(
  KeyboardShortcutMixin(
    GestureEventListeners(LegacyElementMixin(PolymerElement))
  )
) {
  static get template() {
    return htmlTemplate;
  }

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

  @property({type: Array, observer: '_changesChanged'})
  changes?: ChangeInfo[];

  /**
   * ChangeInfo objects grouped into arrays. The sections and changes
   * properties should not be used together.
   */
  @property({type: Array})
  sections: ChangeListSection[] = [];

  @property({type: Array, computed: '_computeLabelNames(sections)'})
  labelNames?: string[];

  @property({type: Array})
  _dynamicHeaderEndpoints?: string[];

  @property({type: Number, notify: true})
  selectedIndex?: number;

  @property({type: Boolean})
  showNumber?: boolean; // No default value to prevent flickering.

  @property({type: Boolean})
  showStar = false;

  @property({type: Boolean})
  showReviewedState = false;

  @property({type: Object})
  keyEventTarget: HTMLElement = document.body;

  @property({type: Array})
  changeTableColumns?: string[];

  @property({type: Array})
  visibleChangeTableColumns?: string[];

  @property({type: Object})
  preferences?: PreferencesInput;

  @property({type: Object})
  _config?: ServerInfo;

  flagsService = appContext.flagsService;

  keyboardShortcuts() {
    return {
      [Shortcut.CURSOR_NEXT_CHANGE]: '_nextChange',
      [Shortcut.CURSOR_PREV_CHANGE]: '_prevChange',
      [Shortcut.NEXT_PAGE]: '_nextPage',
      [Shortcut.PREV_PAGE]: '_prevPage',
      [Shortcut.OPEN_CHANGE]: '_openChange',
      [Shortcut.TOGGLE_CHANGE_REVIEWED]: '_toggleChangeReviewed',
      [Shortcut.TOGGLE_CHANGE_STAR]: '_toggleChangeStar',
      [Shortcut.REFRESH_CHANGE_LIST]: '_refreshChangeList',
    };
  }

  /** @override */
  created() {
    super.created();
    this.addEventListener('keydown', e => this._scopedKeydownHandler(e));
  }

  /** @override */
  ready() {
    super.ready();
    this.$.restAPI.getConfig().then(config => {
      this._config = config;
    });
  }

  /** @override */
  attached() {
    super.attached();
    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        this._dynamicHeaderEndpoints = getPluginEndpoints().getDynamicEndpoints(
          'change-list-header'
        );
      });
  }

  /**
   * Iron-a11y-keys-behavior catches keyboard events globally. Some keyboard
   * events must be scoped to a component level (e.g. `enter`) in order to not
   * override native browser functionality.
   *
   * Context: Issue 7294
   */
  _scopedKeydownHandler(e: KeyboardEvent) {
    if (e.keyCode === 13) {
      // Enter.
      this._openChange((e as unknown) as CustomKeyboardEvent);
    }
  }

  _lowerCase(column: string) {
    return column.toLowerCase();
  }

  @observe('account', 'preferences', '_config')
  _computePreferences(
    account?: AccountInfo,
    preferences?: PreferencesInput,
    config?: ServerInfo
  ) {
    if (!config) {
      return;
    }

    this.changeTableColumns = this.columnNames;
    this.showNumber = false;
    this.visibleChangeTableColumns = this.getEnabledColumns(
      this.columnNames,
      config,
      this.flagsService.enabledExperiments
    );

    if (account && preferences) {
      this.showNumber = !!(
        preferences && preferences.legacycid_in_change_table
      );
      if (preferences.change_table && preferences.change_table.length > 0) {
        const prefColumns = this.getVisibleColumns(preferences.change_table);
        this.visibleChangeTableColumns = this.getEnabledColumns(
          prefColumns,
          config,
          this.flagsService.enabledExperiments
        );
      }
    }
  }

  _computeColspan(changeTableColumns: string[], labelNames: string[]) {
    if (!changeTableColumns || !labelNames) return;
    return changeTableColumns.length + labelNames.length + NUMBER_FIXED_COLUMNS;
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

  _changesChanged(changes: ChangeInfo[]) {
    this.sections = changes ? [{results: changes}] : [];
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

  _sectionHref(query: string) {
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
  _computeItemAbsoluteIndex(sectionIndex: number, localIndex: number) {
    let idx = 0;
    for (let i = 0; i < sectionIndex; i++) {
      idx += this.sections[i].results.length;
    }
    return idx + localIndex;
  }

  _computeItemSelected(
    sectionIndex: number,
    index: number,
    selectedIndex: number
  ) {
    const idx = this._computeItemAbsoluteIndex(sectionIndex, index);
    return idx === selectedIndex;
  }

  _computeTabIndex(sectionIndex: number, index: number, selectedIndex: number) {
    return this._computeItemSelected(sectionIndex, index, selectedIndex)
      ? 0
      : undefined;
  }

  _computeItemNeedsReview(
    account: AccountInfo | undefined,
    change: ChangeInfo,
    showReviewedState: boolean,
    config?: ServerInfo
  ) {
    const isAttentionSetEnabled =
      !!config && !!config.change && config.change.enable_attention_set;
    return (
      !isAttentionSetEnabled &&
      showReviewedState &&
      !change.reviewed &&
      !change.work_in_progress &&
      changeIsOpen(change) &&
      (!account || account._account_id !== change.owner._account_id)
    );
  }

  _computeItemHighlight(
    account?: AccountInfo,
    change?: ChangeInfo,
    config?: ServerInfo,
    sectionName?: string
  ) {
    if (!change || !account) return false;
    if (CLOSED_STATUS.indexOf(change.status) !== -1) return false;
    return isAttentionSetEnabled(config)
      ? hasAttention(config, account, change) &&
          !isOwner(change, account) &&
          sectionName === YOUR_TURN.name
      : account._account_id === change.assignee?._account_id;
  }

  _nextChange(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
    this.$.cursor.next();
  }

  _prevChange(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
    this.$.cursor.previous();
  }

  _openChange(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
    const change = this._changeForIndex(this.selectedIndex);
    if (change) GerritNav.navigateToChange(change);
  }

  _nextPage(e: CustomKeyboardEvent) {
    if (
      this.shouldSuppressKeyboardShortcut(e) ||
      (this.modifierPressed(e) &&
        !this.isModifierPressed(e, Modifier.SHIFT_KEY))
    ) {
      return;
    }

    e.preventDefault();
    this.dispatchEvent(
      new CustomEvent('next-page', {
        composed: true,
        bubbles: true,
      })
    );
  }

  _prevPage(e: CustomKeyboardEvent) {
    if (
      this.shouldSuppressKeyboardShortcut(e) ||
      (this.modifierPressed(e) &&
        !this.isModifierPressed(e, Modifier.SHIFT_KEY))
    ) {
      return;
    }

    e.preventDefault();
    this.dispatchEvent(
      new CustomEvent('previous-page', {
        composed: true,
        bubbles: true,
      })
    );
  }

  _toggleChangeReviewed(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
    this._toggleReviewedForIndex(this.selectedIndex);
  }

  _toggleReviewedForIndex(index?: number) {
    const changeEls = this._getListItems();
    if (index === undefined || index >= changeEls.length || !changeEls[index]) {
      return;
    }

    const changeEl = changeEls[index];
    changeEl.toggleReviewed();
  }

  _refreshChangeList(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e)) {
      return;
    }

    e.preventDefault();
    this._reloadWindow();
  }

  _reloadWindow() {
    window.location.reload();
  }

  _toggleChangeStar(e: CustomKeyboardEvent) {
    if (this.shouldSuppressKeyboardShortcut(e) || this.modifierPressed(e)) {
      return;
    }

    e.preventDefault();
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
    const items = this.root?.querySelectorAll('gr-change-list-item');
    return !items ? [] : Array.from(items);
  }

  @observe('sections.*')
  _sectionsChanged() {
    // Flush DOM operations so that the list item elements will be loaded.
    afterNextRender(this, () => {
      this.$.cursor.stops = this._getListItems();
      this.$.cursor.moveToStart();
    });
  }

  _getSpecialEmptySlot(section: DashboardSection) {
    if (section.isOutgoing) return 'empty-outgoing';
    if (section.name === YOUR_TURN.name) return 'empty-your-turn';
    return '';
  }

  _isEmpty(section: DashboardSection) {
    return !section.results?.length;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-change-list': GrChangeList;
  }
}
