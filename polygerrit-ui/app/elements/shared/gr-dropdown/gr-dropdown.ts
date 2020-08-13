/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '@polymer/iron-dropdown/iron-dropdown.js';
import '../gr-button/gr-button.js';
import '../gr-cursor-manager/gr-cursor-manager.js';
import '../gr-rest-api-interface/gr-rest-api-interface.js';
import '../gr-tooltip-content/gr-tooltip-content.js';
import '../../../styles/shared-styles.js';
import {flush, dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-dropdown_html.js';
import {getBaseUrl} from '../../../utils/url-util.js';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin.js';
import { IronDropdownElement } from '@polymer/iron-dropdown/iron-dropdown.js';
import { GrCursorManager } from '../gr-cursor-manager/gr-cursor-manager.js';
import {property, customElement, observe} from '@polymer/decorators';
import { NavLink } from '../../../utils/admin-nav-util.js';
import { MenuLink } from '../../plugins/gr-admin-api/gr-admin-api.js';

const REL_NOOPENER = 'noopener';
const REL_EXTERNAL = 'external';

declare global {
  interface HTMLElementTagNameMap {
    'gr-dropdown': GrDropdown;
  }
}

export interface GrDropdown {
  $: {
    dropdown: IronDropdownElement;
    cursor: GrCursorManager;
  };
}

@customElement('gr-dropdown')
export class GrDropdown extends KeyboardShortcutMixin(GestureEventListeners(
    LegacyElementMixin(PolymerElement))) {
  static get template() { return htmlTemplate; }

  /**
   * Fired when a non-link dropdown item with the given ID is tapped.
   *
   * @event tap-item-<id>
   */

  /**
   * Fired when a non-link dropdown item is tapped.
   *
   * @event tap-item
   */

  @property({type: Array})
  items?: unknown[];

  @property({type: Boolean})
  downArrow?: boolean;
  @property({type: Object})
  topContent?: object;

  @property({type: String})
  horizontalAlign = 'left';

  /**
   * Style the dropdown trigger as a link (rather than a button).
   */
  
  link = false;
  @property({type: Number})
  verticalOffset = 40;
  /**
   * List the IDs of dropdown buttons to be disabled. (Note this only
   * disables buttons and not link entries.)
   */
  @property({type: Array})
  disabledIds: string[] = [];
  /**
   * The elements of the list.
   */
  @property({type: Array})
  _listElements: Element[] = [];

  get keyBindings() {
    return {
      'down': '_handleDown',
      'enter space': '_handleEnter',
      'tab': '_handleTab',
      'up': '_handleUp',
    };
  }

  /**
   * Handle the up key.
   */
  _handleUp(e: MouseEvent) {
    if (this.$.dropdown.opened) {
      e.preventDefault();
      e.stopPropagation();
      this.$.cursor.previous();
    } else {
      this._open();
    }
  }

  /**
   * Handle the down key.
   */
  _handleDown(e: MouseEvent) {
    if (this.$.dropdown.opened) {
      e.preventDefault();
      e.stopPropagation();
      this.$.cursor.next();
    } else {
      this._open();
    }
  }

  /**
   * Handle the tab key.
   */
  _handleTab(e: MouseEvent) {
    if (this.$.dropdown.opened) {
      // Tab in a native select is a no-op. Emulate this.
      e.preventDefault();
      e.stopPropagation();
    }
  }

  /**
   * Handle the enter key.
   */
  _handleEnter(e: MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (this.$.dropdown.opened) {
      // TODO(milutin): This solution is not particularly robust in general.
      // Since gr-tooltip-content click on shadow dom is not propagated down,
      // we have to target `a` inside it.
      const el = this.$.cursor.target.querySelector(':not([hidden]) a');
      if (el) { el.click(); }
    } else {
      this._open();
    }
  }

  /**
   * Handle a click on the iron-dropdown element.
   */
  _handleDropdownClick() {
    this._close();
  }

  /**
   * Handle a click on the button to open the dropdown.
   */
  _dropdownTriggerTapHandler(e: MouseEvent) {
    e.preventDefault();
    e.stopPropagation();
    if (this.$.dropdown.opened) {
      this._close();
    } else {
      this._open();
    }
  }

  /**
   * Open the dropdown and initialize the cursor.
   */
  _open() {
    this.$.dropdown.open();
    this._resetCursorStops();
    this.$.cursor.setCursorAtIndex(0);
    this.$.cursor.target.focus();
  }

  _close() {
    // async is needed so that that the click event is fired before the
    // dropdown closes (This was a bug for touch devices).
    this.async(() => {
      this.$.dropdown.close();
    }, 1);
  }

  /**
   * Get the class for a top-content item based on the given boolean.
   *
   * @param bold Whether the item is bold.
   * @return The class for the top-content item.
   */
  _getClassIfBold(bold: boolean) {
    return bold ? 'bold-text' : '';
  }

  /**
   * Build a URL for the given host and path. The base URL will be only added,
   * if it is not already included in the path.
   *
   * @return The scheme-relative URL.
   */
  _computeURLHelper(host: string, path: string) {
    const base = path.startsWith(getBaseUrl()) ?
      '' : getBaseUrl();
    return '//' + host + base + path;
  }

  /**
   * Build a scheme-relative URL for the current host. Will include the base
   * URL if one is present. Note: the URL will be scheme-relative but absolute
   * with regard to the host.
   *
   * @param path The path for the URL.
   * @return The scheme-relative URL.
   */
  _computeRelativeURL(path: string) {
    const host = window.location.host;
    return this._computeURLHelper(host, path);
  }

  /**
   * Compute the URL for a link object.
   *
   * @param {!Object} link The object describing the link.
   * @return {!string} The URL.
   */
  _computeLinkURL(link) {
    if (typeof link.url === 'undefined') {
      return '';
    }
    if (link.target || !link.url.startsWith('/')) {
      return link.url;
    }
    return this._computeRelativeURL(link.url);
  }

  /**
   * Compute the value for the rel attribute of an anchor for the given link
   * object. If the link has a target value, then the rel must be "noopener"
   * for security reasons.
   *
   * @param {!Object} link The object describing the link.
   * @return {?string} The rel value for the link.
   */
  _computeLinkRel(link) {
    // Note: noopener takes precedence over external.
    if (link.target) { return REL_NOOPENER; }
    if (link.external) { return REL_EXTERNAL; }
    return null;
  }

  /**
   * Handle a click on an item of the dropdown.
   */
  _handleItemTap(e: MouseEvent) {
    if (e.target === null) {
      return;
    }
    const id = (e.target as Element).getAttribute('data-id');
    const item = this.items.find(item => item.id === id);
    if (id && !this.disabledIds.includes(id)) {
      if (item) {
        this.dispatchEvent(new CustomEvent('tap-item',
            {detail: item, bubbles: true, composed: true}));
      }
      this.dispatchEvent(new CustomEvent('tap-item-' + id));
    }
  }

  /**
   * If a dropdown item is shown as a button, get the class for the button.
   *
   * @param {!Object} disabledIdsRecord The change record for the disabled IDs
   *     list.
   * @return {!string} The class for the item button.
   */
  _computeDisabledClass(id: string, disabledIdsRecord) {
    return disabledIdsRecord.base.includes(id) ? 'disabled' : '';
  }

  /**
   * Recompute the stops for the dropdown item cursor.
   */
  @observe('items')
  _resetCursorStops() {
    if (this.items && this.items.length > 0 && this.$.dropdown.opened) {
      flush();
      this._listElements = Array.from(
          dom(this.root).querySelectorAll('li'));
    }
  }

  _computeHasTooltip(tooltip) {
    return !!tooltip;
  }

  _computeIsDownload(link) {
    return !!link.download;
  }
}
