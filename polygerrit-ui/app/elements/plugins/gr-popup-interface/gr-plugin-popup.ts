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
import '../../shared/gr-overlay/gr-overlay';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-plugin-popup_html';
import {GrOverlay} from '../../shared/gr-overlay/gr-overlay';
import {customElement} from '@polymer/decorators';

declare global {
  interface HTMLElementTagNameMap {
    'gr-plugin-popup': GrPluginPopup;
  }
}

export interface GrPluginPopup {
  $: {
    overlay: GrOverlay;
  };
}
@customElement('gr-plugin-popup')
export class GrPluginPopup extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  get opened() {
    return this.$.overlay.opened;
  }

  open() {
    return this.$.overlay.open();
  }

  close() {
    this.$.overlay.close();
  }
}
