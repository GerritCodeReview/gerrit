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
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-labeled-autocomplete_html';
import {customElement, property} from '@polymer/decorators';
import {
  GrAutocomplete,
  AutocompleteQuery,
} from '../gr-autocomplete/gr-autocomplete';

export interface GrLabeledAutocomplete {
  $: {
    autocomplete: GrAutocomplete;
  };
}
@customElement('gr-labeled-autocomplete')
export class GrLabeledAutocomplete extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when a value is chosen.
   *
   * @event commit
   */

  @property({type: Object})
  query: AutocompleteQuery = () => Promise.resolve([]);

  @property({type: String, notify: true})
  text = '';

  @property({type: String})
  label?: string;

  @property({type: String})
  placeholder?: string;

  @property({type: Boolean})
  disabled?: boolean;

  _handleTriggerClick(e: Event) {
    // Stop propagation here so we don't confuse gr-autocomplete, which
    // listens for taps on body to try to determine when it's blurred.
    e.stopPropagation();
    this.$.autocomplete.focus();
  }

  setText(text: string) {
    this.$.autocomplete.setText(text);
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
