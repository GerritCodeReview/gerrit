/**
 * @license
 * Copyright 2025 Google LLC
 * SPDX-License-Identifier: BSD-3-Clause
 */

import {html, LitElement, PropertyValues} from 'lit';
import {customElement, property, state} from 'lit/decorators.js';
import {assertIsDefined} from '../../../utils/common-util';
import {fire} from '../../../utils/event-util';

/**
 * This is a replacement for iron-selector.
 * Based on https://github.com/chromium/chromium/blob/f7322d4ecf3ee3804ce7e80e1e9d4b98f23b9295/ui/webui/resources/cr_elements/cr_selectable_mixin.ts.
 * Modified for gerrit.
 */
@customElement('gr-selector')
export class GrSelector extends LitElement {
  @property({type: String})
  attrForSelected: string | null = null;

  @property({type: String})
  selected?: string | number;

  @property({type: String})
  selectedAttribute: string | null = null;

  @property({type: String})
  selectable?: string;

  @state()
  items: Element[] = [];

  private selectedItem_: Element | null = null;

  override render() {
    return html`<slot></slot>`;
  }

  override firstUpdated(changedProperties: PropertyValues<this>) {
    super.firstUpdated(changedProperties);
    this.addEventListener('click', e => this.onClick(e));
    this.observeItems();
  }

  // Override this method in client code to modify the observation logic,
  // or to turn it off completely. By default it listens for any changes on
  // the first <slot> node in this shadowRoot.
  observeItems() {
    this.getSlot().addEventListener('slotchange', () => this.itemsChanged());
  }

  override connectedCallback() {
    super.connectedCallback();
    this.updateItems();
  }

  override willUpdate(changedProperties: PropertyValues<this>) {
    super.willUpdate(changedProperties);

    if (changedProperties.has('attrForSelected')) {
      if (this.selectedItem_) {
        assertIsDefined(this.attrForSelected);
        const value = this.selectedItem_.getAttribute(this.attrForSelected);
        assertIsDefined(value);
        this.selected = value;
      }
    }
  }

  override updated(changedProperties: PropertyValues<this>) {
    super.updated(changedProperties);
    if (changedProperties.has('selected')) {
      this.updateSelectedItem();
    }
  }

  /**
   * Selects the given value.
   */
  select(value: string | number) {
    this.selected = value;
  }

  getSlot(): HTMLSlotElement {
    const slot = this.shadowRoot!.querySelector('slot');
    assertIsDefined(slot);
    return slot;
  }

  // Override this method in client code to modify this logic, for example to
  // grab children that don't reside in a <slot>.
  queryItems(): Element[] {
    const selectable = this.selectable === undefined ? '*' : this.selectable;
    return Array.from(this.querySelectorAll(`:scope > ${selectable}`));
  }

  // If overriding queryItems(), override this method to return the list item
  // element matching the CSS selector string |selector|.
  queryMatchingItem(selector: string): HTMLElement | null {
    const selectable = this.selectable || '*';
    return this.querySelector<HTMLElement>(
      `:scope > :is(${selectable})${selector}`
    );
  }

  private updateItems() {
    this.items = this.queryItems();
    this.items.forEach((item, index) =>
      item.setAttribute('data-selection-index', index.toString())
    );
  }

  get selectedItem(): Element | null {
    return this.selectedItem_;
  }

  private updateSelectedItem() {
    if (!this.items) {
      return;
    }

    const item =
      this.selected === null || this.selected === undefined
        ? null
        : this.items[this.valueToIndex_(this.selected)];
    if (!!item && this.selectedItem_ !== item) {
      this.setItemSelected_(this.selectedItem_, false);
      this.setItemSelected_(item, true);
    } else if (!item) {
      this.setItemSelected_(this.selectedItem_, false);
    }
  }

  private setItemSelected_(item: Element | null, isSelected: boolean) {
    if (!item) {
      return;
    }

    item.classList.toggle('selected', isSelected);
    if (this.selectedAttribute) {
      item.toggleAttribute(this.selectedAttribute, isSelected);
    }
    this.selectedItem_ = isSelected ? item : null;
    fire(this, isSelected ? 'select' : 'deselect', {item});
  }

  private valueToIndex_(value: string | number): number {
    if (!this.attrForSelected) {
      return Number(value);
    }

    const match = this.queryMatchingItem(
      `[${this.attrForSelected}="${value}"]`
    );
    return match ? Number(match.dataset['selectionIndex']) : -1;
  }

  private indexToValue_(index: number): string | number {
    if (!this.attrForSelected) {
      return index;
    }

    const item = this.items[index];
    if (!item) {
      return index;
    }

    return item.getAttribute(this.attrForSelected) || index;
  }

  itemsChanged() {
    this.updateItems();
    this.updateSelectedItem();

    // Let other interested parties know about the change.
    fire(this, 'items-changed', {});
  }

  private onClick(e: MouseEvent) {
    let element = e.target as HTMLElement;
    while (element && element !== this) {
      const idx = this.items.indexOf(element);
      if (idx >= 0) {
        const value = this.indexToValue_(idx);
        assertIsDefined(value);
        fire(this, 'activate', {item: element, selected: value});
        this.select(value);
        return;
      }
      element = element.parentNode as HTMLElement;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-selector': GrSelector;
  }
  interface HTMLElementEventMap {
    activate: CustomEvent<{}>;
    'items-changed': CustomEvent<{}>;
    select: CustomEvent<{}>;
    deselect: CustomEvent<{}>;
  }
}
