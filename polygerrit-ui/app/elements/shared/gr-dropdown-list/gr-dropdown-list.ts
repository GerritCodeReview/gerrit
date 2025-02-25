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
import '../gr-file-status/gr-file-status';
import {css, html, LitElement, PropertyValues} from 'lit';
import {customElement, property, query} from 'lit/decorators.js';
import {IronDropdownElement} from '@polymer/iron-dropdown/iron-dropdown';
import {CommentThread, Timestamp} from '../../../types/common';
import {NormalizedFileInfo} from '../../change/gr-file-list/gr-file-list';
import {GrButton} from '../gr-button/gr-button';
import {assertIsDefined} from '../../../utils/common-util';
import {sharedStyles} from '../../../styles/shared-styles';
import {ValueChangedEvent} from '../../../types/events';
import {incrementalRepeat} from '../../lit/incremental-repeat';
import {when} from 'lit/directives/when.js';
import {isMagicPath} from '../../../utils/path-list-util';
import {fireNoBubble} from '../../../utils/event-util';

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
  commentThreads?: CommentThread[];
}

declare global {
  interface HTMLElementEventMap {
    'value-change': ValueChangedEvent<string>;
  }
}
@customElement('gr-dropdown-list')
export class GrDropdownList extends LitElement {
  @query('#dropdown')
  dropdown?: IronDropdownElement;

  @query('#trigger')
  trigger?: GrButton;

  /**
   * Fired when the selected value changes
   *
   * @event value-change
   *
   * @property {string} value
   */

  @property({type: Number})
  initialCount = 75;

  @property({type: Array})
  items?: DropdownItem[];

  @property({type: String})
  text?: string;

  @property({type: Boolean})
  disabled = false;

  @property({type: String})
  value = '';

  @property({type: Boolean, attribute: 'show-copy-for-trigger-text'})
  showCopyForTriggerText = false;

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          display: inline-block;
        }
        #triggerText {
          -moz-user-select: text;
          -ms-user-select: text;
          -webkit-user-select: text;
          user-select: text;
        }
        .dropdown-trigger {
          cursor: pointer;
          padding: 0;
        }
        .dropdown-content {
          background-color: var(--dropdown-background-color);
          box-shadow: var(--elevation-level-2);
          max-height: 70vh;
          min-width: 266px;
        }
        paper-item:hover {
          background-color: var(--hover-background-color);
        }
        paper-item:not(:last-of-type) {
          border-bottom: 1px solid var(--border-color);
        }
        .bottomContent {
          color: var(--deemphasized-text-color);
          white-space: pre-wrap;
        }
        .bottomContent,
        .topContent {
          display: flex;
          justify-content: space-between;
          flex-direction: row;
          width: 100%;
        }
        gr-button {
          font-family: var(--trigger-style-font-family);
          --gr-button-text-color: var(--trigger-style-text-color);
        }
        gr-date-formatter {
          color: var(--deemphasized-text-color);
          margin-left: var(--spacing-xxl);
          white-space: nowrap;
        }
        gr-select {
          display: none;
        }
        /* Because the iron dropdown 'area' includes the trigger, and the entire
          width of the dropdown, we want to treat tapping the area above the
          dropdown content as if it is tapping whatever content is underneath
          it. The next two styles allow this to happen. */
        iron-dropdown {
          max-width: none;
          pointer-events: none;
          z-index: 120;
        }
        paper-listbox {
          pointer-events: auto;
          --paper-listbox_-_padding: 0;
        }
        paper-item {
          cursor: pointer;
          flex-direction: column;
          font-size: inherit;
          /* This variable was introduced in Dec 2019. We keep both min-height
            * rules around, because --paper-item-min-height is not yet
            * upstreamed.
            */
          --paper-item-min-height: 0;
          --paper-item_-_min-height: 0;
          --paper-item_-_padding: 10px 16px;
          --paper-item-focused-before_-_background-color: var(
            --selection-background-color
          );
          --paper-item-focused_-_background-color: var(
            --selection-background-color
          );
        }
        gr-comments-summary {
          padding-left: var(--spacing-s);
        }
        @media only screen and (max-width: 50em) {
          gr-select {
            display: var(--gr-select-style-display, inline-block);
            width: var(--gr-select-style-width);
          }
          gr-button,
          iron-dropdown {
            display: none;
          }
          select {
            width: var(--native-select-style-width);
          }
        }
      `,
    ];
  }

  protected override willUpdate(changedProperties: PropertyValues): void {
    if (changedProperties.has('value') || changedProperties.has('items')) {
      this.updateText();
    }
    if (changedProperties.has('value')) {
      fireNoBubble(this, 'value-change', {value: this.value});
    }
  }

  override render() {
    return html`
      <gr-button
        id="trigger"
        ?disabled=${this.disabled}
        down-arrow
        link
        class="dropdown-trigger"
        slot="dropdown-trigger"
        @click=${this.showDropdownTapHandler}
      >
        <span id="triggerText">${this.text}</span>
        <gr-copy-clipboard
          ?hidden=${!this.showCopyForTriggerText}
          hideInput
          .text=${this.text}
        ></gr-copy-clipboard>
      </gr-button>
      <iron-dropdown
        id="dropdown"
        .verticalAlign=${'top'}
        .horizontalAlign=${'left'}
        .dynamicAlign=${true}
        .noOverlap=${true}
        .allowOutsideScroll=${true}
        @click=${this.handleDropdownClick}
      >
        <paper-listbox
          class="dropdown-content"
          slot="dropdown-content"
          .attrForSelected=${'data-value'}
          .selected=${this.value}
          @selected-changed=${this.selectedChanged}
        >
          ${incrementalRepeat({
            values: this.items ?? [],
            initialCount: this.initialCount,
            mapFn: item => this.renderPaperItem(item as DropdownItem),
          })}
        </paper-listbox>
      </iron-dropdown>
      <gr-select
        .bindValue=${this.value}
        @bind-value-changed=${this.selectedChanged}
      >
        <select>
          ${this.items?.map(
            item => html`
              <option ?disabled=${item.disabled} value=${`${item.value}`}>
                ${this.computeMobileText(item)}
              </option>
            `
          )}
        </select>
      </gr-select>
    `;
  }

  private renderPaperItem(item: DropdownItem) {
    return html`
      <paper-item ?disabled=${item.disabled} data-value=${item.value}>
        <div class="topContent">
          <div>
            <span>${item.text}</span>
            ${when(
              item.commentThreads,
              () => html`<gr-comments-summary
                .commentThreads=${item.commentThreads}
                emptyWhenNoComments
                showAvatarForResolved
              ></gr-comments-summary>`
            )}
          </div>
          ${when(
            item.date,
            () => html`
              <gr-date-formatter .dateStr=${item.date}></gr-date-formatter>
            `
          )}
          ${when(
            item.file?.status && !isMagicPath(item.file?.__path),
            () => html`
              <gr-file-status .status=${item.file?.status}></gr-file-status>
            `
          )}
        </div>
        ${when(
          item.bottomText,
          () => html`
            <div class="bottomContent">
              <div>${item.bottomText}</div>
            </div>
          `
        )}
      </paper-item>
    `;
  }

  private selectedChanged(e: ValueChangedEvent<string>) {
    this.value = e.detail.value;
  }

  /**
   * Handle a click on the iron-dropdown element.
   */
  private handleDropdownClick() {
    // async is needed so that that the click event is fired before the
    // dropdown closes (This was a bug for touch devices).
    setTimeout(() => {
      assertIsDefined(this.dropdown);
      this.dropdown.close();
    }, 1);
  }

  private updateText() {
    if (this.value === undefined || this.items === undefined) {
      return;
    }
    const selectedObj = this.items.find(item => `${item.value}` === this.value);
    if (!selectedObj) {
      return;
    }
    this.text = selectedObj.triggerText
      ? selectedObj.triggerText
      : selectedObj.text;
  }

  /**
   * Handle a click on the button to open the dropdown.
   */
  private showDropdownTapHandler() {
    this.open();
  }

  /**
   * Open the dropdown.
   */
  open() {
    assertIsDefined(this.dropdown);
    this.dropdown.open();
  }

  // Private but used in tests.
  computeMobileText(item: DropdownItem) {
    return item.mobileText ? item.mobileText : item.text;
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-dropdown-list': GrDropdownList;
  }
}
