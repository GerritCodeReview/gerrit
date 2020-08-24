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
import '@polymer/iron-icon/iron-icon';
import '@polymer/iron-a11y-announcer/iron-a11y-announcer';
import '../../../styles/shared-styles';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import {DiffViewMode} from '../../../constants/constants';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-diff-mode-selector_html';
import {customElement, property} from '@polymer/decorators';
import {IronA11yAnnouncer} from '@polymer/iron-a11y-announcer/iron-a11y-announcer';

export interface GrDiffModeSelector {
  $: {
    restAPI: RestApiService & Element;
  };
}

interface FixIronA11yAnnouncer extends IronA11yAnnouncer {
  requestAvailability(): void;
}

@customElement('gr-diff-mode-selector')
export class GrDiffModeSelector extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String, notify: true})
  mode?: DiffViewMode;

  /**
   * If set to true, the user's preference will be updated every time a
   * button is tapped. Don't set to true if there is no user.
   */
  @property({type: Boolean})
  saveOnChange = false;

  attached() {
    ((IronA11yAnnouncer as unknown) as FixIronA11yAnnouncer).requestAvailability();
  }

  /**
   * Set the mode. If save on change is enabled also update the preference.
   */
  setMode(newMode: DiffViewMode) {
    if (this.saveOnChange && this.mode && this.mode !== newMode) {
      this.$.restAPI.savePreferences({diff_view: newMode});
    }
    this.mode = newMode;
    let annoucement;
    if (this.isUnifiedSelected(newMode)) {
      annoucement = 'Changed diff view to unified';
    } else if (this.isSideBySideSelected(newMode)) {
      annoucement = 'Changed diff view to side by side';
    }
    if (annoucement) {
      this.fire(
        'iron-announce',
        {
          text: annoucement,
        },
        {bubbles: true}
      );
    }
  }

  _computeSideBySideSelected(mode: DiffViewMode) {
    return mode === DiffViewMode.SIDE_BY_SIDE ? 'selected' : '';
  }

  _computeUnifiedSelected(mode: DiffViewMode) {
    return mode === DiffViewMode.UNIFIED ? 'selected' : '';
  }

  isSideBySideSelected(mode: DiffViewMode) {
    return mode === DiffViewMode.SIDE_BY_SIDE;
  }

  isUnifiedSelected(mode: DiffViewMode) {
    return mode === DiffViewMode.UNIFIED;
  }

  _handleSideBySideTap() {
    this.setMode(DiffViewMode.SIDE_BY_SIDE);
  }

  _handleUnifiedTap() {
    this.setMode(DiffViewMode.UNIFIED);
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-diff-mode-selector': GrDiffModeSelector;
  }
}
