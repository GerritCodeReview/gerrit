/**
 * @license
 * Copyright (C) 2018 The Android Open Source Project
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
import '../gr-autocomplete/gr-autocomplete';
import '../../../styles/shared-styles';
import {LitElement, css, html, PropertyValues} from 'lit';
import {customElement, property, query} from 'lit/decorators';
import {
  GrAutocomplete,
  AutocompleteQuery,
} from '../gr-autocomplete/gr-autocomplete';
import {assertIsDefined} from '../../../utils/common-util';
import {fire} from '../../../utils/event-util';

@customElement('gr-labeled-autocomplete')
export class GrLabeledAutocomplete extends LitElement {
  @query('#autocomplete')
  autocomplete?: GrAutocomplete;

  /**
   * Fired when a value is chosen.
   *
   * @event commit
   */

  @property({type: Object})
  query: AutocompleteQuery = () => Promise.resolve([]);

  @property({type: String})
  text = '';

  @property({type: String})
  label?: string;

  @property({type: String})
  placeholder = '';

  @property({type: Boolean})
  disabled = false;

  static override get styles() {
    return css`
      :host {
        display: block;
        width: 12em;
      }
      #container {
        background: var(--chip-background-color);
        border-radius: 1em;
        padding: var(--spacing-m);
      }
      #header {
        color: var(--deemphasized-text-color);
        font-weight: var(--font-weight-bold);
        font-size: var(--font-size-small);
      }
      #body {
        display: flex;
      }
      #trigger {
        color: var(--deemphasized-text-color);
        cursor: pointer;
        padding-left: var(--spacing-s);
      }
      #trigger:hover {
        color: var(--primary-text-color);
      }
    `;
  }

  override render() {
    return html`
      <div id="container">
        <div id="header">${this.label}</div>
        <div id="body">
          <gr-autocomplete
            id="autocomplete"
            threshold="0"
            .query=${this.query}
            ?disabled=${this.disabled}
            .placeholder=${this.placeholder}
            borderless=""
          ></gr-autocomplete>
          <div id="trigger" @click=${this._handleTriggerClick}>▼</div>
        </div>
      </div>
    `;
  }

  override willUpdate(changedProperties: PropertyValues) {
    if (changedProperties.has('text')) {
      fire(this, 'text-changed', {value: this.text});
    }
  }

  // Private but used in tests.
  _handleTriggerClick = (e: Event) => {
    // Stop propagation here so we don't confuse gr-autocomplete, which
    // listens for taps on body to try to determine when it's blurred.
    e.stopPropagation();
    assertIsDefined(this.autocomplete);
    this.autocomplete.focus();
  };

  setText(text: string) {
    assertIsDefined(this.autocomplete);
    this.autocomplete.setText(text);
  }

  clear() {
    this.setText('');
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-labeled-autocomplete': GrLabeledAutocomplete;
  }
}
