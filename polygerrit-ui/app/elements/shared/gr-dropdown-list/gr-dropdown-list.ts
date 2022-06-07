/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import '@polymer/iron-dropdown/iron-dropdown';
import '@polymer/paper-item/paper-item';
import '@polymer/paper-listbox/paper-listbox';
import '../../../styles/shared-styles';
import '../gr-button/gr-button';
import '../gr-date-formatter/gr-date-formatter';
import '../gr-select/gr-select';
import '../gr-file-status-chip/gr-file-status-chip';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-dropdown-list_html';
import {customElement, property, observe} from '@polymer/decorators';
import {IronDropdownElement} from '@polymer/iron-dropdown/iron-dropdown';
import {Timestamp} from '../../../types/common';
import {NormalizedFileInfo} from '../../change/gr-file-list/gr-file-list';
import {GrButton} from '../gr-button/gr-button';

/**
 * Required values are text and value. mobileText and triggerText will
 * fall back to text if not provided.
 *
 * If bottomText is not provided, nothing will display on the second
 * line.
 *
 * If date is not provided, nothing will be displayed in its place.
 */
export interface DropdownItem {
  text: string;
  value: string | number;
  bottomText?: string;
  triggerText?: string;
  mobileText?: string;
  date?: Timestamp;
  disabled?: boolean;
  file?: NormalizedFileInfo;
}

export interface GrDropdownList {
  $: {
    dropdown: IronDropdownElement;
    trigger: GrButton;
  };
}

export interface ValueChangeDetail {
  value: string;
}

export type DropDownValueChangeEvent = CustomEvent<ValueChangeDetail>;

@customElement('gr-dropdown-list')
export class GrDropdownList extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the selected value changes
   *
   * @event value-change
   *
   * @property {string|number} value
   */

  @property({type: Number})
  initialCount = 75;

  @property({type: Array})
  items?: DropdownItem[];

  @property({type: String})
  text?: string;

  @property({type: Boolean})
  disabled = false;

  @property({type: String, notify: true})
  value: string | number = '';

  @property({type: Boolean})
  showCopyForTriggerText = false;

  /**
   * Handle a click on the iron-dropdown element.
   */
  _handleDropdownClick() {
    // async is needed so that that the click event is fired before the
    // dropdown closes (This was a bug for touch devices).
    setTimeout(() => {
      this.$.dropdown.close();
    }, 1);
  }

  /**
   * Handle a click on the button to open the dropdown.
   */
  _showDropdownTapHandler() {
    this.open();
  }

  /**
   * Open the dropdown.
   */
  open() {
    this.$.dropdown.open();
  }

  _computeMobileText(item: DropdownItem) {
    return item.mobileText ? item.mobileText : item.text;
  }

  computeStringValue(val: string | number) {
    return String(val);
  }

  @observe('value', 'items')
  _handleValueChange(value?: string, items?: DropdownItem[]) {
    if (!value || !items) {
      return;
    }
    const selectedObj = items.find(item => `${item.value}` === `${value}`);
    if (!selectedObj) {
      return;
    }
    this.text = selectedObj.triggerText
      ? selectedObj.triggerText
      : selectedObj.text;
    const detail: ValueChangeDetail = {value};
    this.dispatchEvent(
      new CustomEvent('value-change', {
        detail,
        bubbles: false,
      })
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-dropdown-list': GrDropdownList;
  }
}
