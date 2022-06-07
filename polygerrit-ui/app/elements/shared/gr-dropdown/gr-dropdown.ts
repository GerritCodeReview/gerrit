/**
 * @license
 * Copyright 2016 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-dropdown/iron-dropdown';
import '../gr-button/gr-button';
import {GrButton} from '../gr-button/gr-button';
import '../gr-cursor-manager/gr-cursor-manager';
import '../gr-tooltip-content/gr-tooltip-content';
import '../../../styles/shared-styles';
import {flush} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-dropdown_html';
import {getBaseUrl} from '../../../utils/url-util';
import {IronDropdownElement} from '@polymer/iron-dropdown/iron-dropdown';
import {GrCursorManager} from '../gr-cursor-manager/gr-cursor-manager';
import {property, customElement, observe} from '@polymer/decorators';
import {addShortcut, Key} from '../../../utils/dom-util';

const REL_NOOPENER = 'noopener';
const REL_EXTERNAL = 'external';

declare global {
  interface HTMLElementEventMap {
    'opened-changed': CustomEvent;
  }
  interface HTMLElementTagNameMap {
    'gr-dropdown': GrDropdown;
  }
}

export interface GrDropdown {
  $: {
    dropdown: IronDropdownElement;
    trigger: GrButton;
  };
}

export interface DropdownLink {
  url?: string;
  name?: string;
  external?: boolean;
  target?: string | null;
  download?: boolean;
  id?: string;
  tooltip?: string;
}

interface DisableIdsRecord {
  base: string[];
}

export interface DropdownContent {
  text: string;
  bold?: boolean;
}

@customElement('gr-dropdown')
export class GrDropdown extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

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
  items?: DropdownLink[];

  @property({type: Boolean})
  downArrow = false;

  @property({type: Array})
  topContent?: DropdownContent[];

  @property({type: String})
  horizontalAlign = 'left';

  /**
   * Style the dropdown trigger as a link (rather than a button).
   */

  @property({type: Boolean})
  link = false;

  @property({type: Number})
  verticalOffset = 40;

  /** Propagates/Reflects the `opened` property of the <iron-dropdown> */
  @property({type: Boolean, notify: true})
  opened = false;

  /**
   * List the IDs of dropdown buttons to be disabled. (Note this only
   * disables buttons and not link entries.)
   */
  @property({type: Array})
  disabledIds: string[] = [];

  /** Called in disconnectedCallback. */
  private cleanups: (() => void)[] = [];

  // Used within the tests so needs to be non-private.
  cursor = new GrCursorManager();

  constructor() {
    super();
    this.cursor.cursorTargetClass = 'selected';
    this.cursor.focusOnMove = true;
  }

  override connectedCallback() {
    super.connectedCallback();
    this.cleanups.push(
      addShortcut(this, {key: Key.UP}, () => this._handleUp())
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.DOWN}, () => this._handleDown())
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.ENTER}, () => this._handleEnter())
    );
    this.cleanups.push(
      addShortcut(this, {key: Key.SPACE}, () => this._handleEnter())
    );
  }

  override disconnectedCallback() {
    this.cursor.unsetCursor();
    for (const cleanup of this.cleanups) cleanup();
    this.cleanups = [];
    super.disconnectedCallback();
  }

  /**
   * Handle the up key.
   */
  _handleUp() {
    if (this.$.dropdown.opened) {
      this.cursor.previous();
    } else {
      this._open();
    }
  }

  /**
   * Handle the down key.
   */
  _handleDown() {
    if (this.$.dropdown.opened) {
      this.cursor.next();
    } else {
      this._open();
    }
  }

  /**
   * Handle the enter key.
   */
  _handleEnter() {
    if (this.$.dropdown.opened) {
      // TODO(milutin): This solution is not particularly robust in general.
      // Since gr-tooltip-content click on shadow dom is not propagated down,
      // we have to target `a` inside it.
      if (this.cursor.target !== null) {
        const el = this.cursor.target.querySelector(':not([hidden]) a');
        if (el) {
          (el as HTMLElement).click();
        }
      }
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

  handleOpenedChanged(e: CustomEvent) {
    this.opened = e.detail.value;
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
    this.cursor.setCursorAtIndex(0);
    if (this.cursor.target !== null) this.cursor.target.focus();
  }

  _close() {
    // async is needed so that that the click event is fired before the
    // dropdown closes (This was a bug for touch devices).
    setTimeout(() => {
      this.$.dropdown.close();
    }, 1);
  }

  /**
   * Get the class for a top-content item based on the given boolean.
   *
   * @param bold Whether the item is bold.
   * @return The class for the top-content item.
   */
  _getClassIfBold(bold?: boolean) {
    return bold ? 'bold-text' : '';
  }

  /**
   * Build a URL for the given host and path. The base URL will be only added,
   * if it is not already included in the path.
   *
   * @return The scheme-relative URL.
   */
  _computeURLHelper(host: string, path: string) {
    const base = path.startsWith(getBaseUrl()) ? '' : getBaseUrl();
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
   */
  _computeLinkURL(link: DropdownLink) {
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
   */
  _computeLinkRel(link: DropdownLink) {
    // Note: noopener takes precedence over external.
    if (link.target) {
      return REL_NOOPENER;
    }
    if (link.external) {
      return REL_EXTERNAL;
    }
    return null;
  }

  /**
   * Handle a click on an item of the dropdown.
   */
  _handleItemTap(e: MouseEvent) {
    if (e.target === null || !this.items) {
      return;
    }
    const id = (e.target as Element).getAttribute('data-id');
    const item = this.items.find(item => item.id === id);
    if (id && !this.disabledIds.includes(id)) {
      if (item) {
        this.dispatchEvent(
          new CustomEvent('tap-item', {
            detail: item,
            bubbles: true,
            composed: true,
          })
        );
      }
      this.dispatchEvent(new CustomEvent('tap-item-' + id));
    }
  }

  /**
   * If a dropdown item is shown as a button, get the class for the button.
   *
   * @param disabledIdsRecord The change record for the disabled IDs
   *     list.
   * @return The class for the item button.
   */
  _computeDisabledClass(disabledIdsRecord: DisableIdsRecord, id?: string) {
    return id && disabledIdsRecord.base.includes(id) ? 'disabled' : '';
  }

  /**
   * Recompute the stops for the dropdown item cursor.
   */
  @observe('items')
  _resetCursorStops() {
    if (this.items && this.items.length > 0 && this.$.dropdown.opened) {
      flush();
      this.cursor.stops =
        this.root !== null ? Array.from(this.root.querySelectorAll('li')) : [];
    }
  }

  _computeHasTooltip(tooltip?: string) {
    return !!tooltip;
  }

  _computeIsDownload(link: DropdownLink) {
    return !!link.download;
  }
}
