/**
 * @license
 * Copyright (C) 2017 The Android Open Source Project
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
import '../../../styles/shared-styles';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-default-editor_html';
import {customElement, property} from '@polymer/decorators';

export interface GrDefaultEditor {
  $: {};
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-default-editor': GrDefaultEditor;
  }
}

@customElement('gr-default-editor')
/** @extends PolymerElement */
export class GrDefaultEditor extends LegacyElementMixin(PolymerElement) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the content of the editor changes.
   *
   * @event content-change
   */

  @property({type: String})
  fileContent: string | null = null;

  _handleTextareaInput(e: Event) {
    this.dispatchEvent(
      new CustomEvent('content-change', {
        detail: {value: (e.target as HTMLTextAreaElement).value},
        bubbles: true,
        composed: true,
      })
    );
  }
}
