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
import '../../shared/gr-button/gr-button';
import '../../shared/gr-dropdown/gr-dropdown';
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-edit-file-controls_html';
import {GrEditConstants} from '../gr-edit-constants';
import {customElement, property} from '@polymer/decorators';

interface EditAction {
  label: string;
  id: string;
}

/** @extends PolymerElement */
@customElement('gr-edit-file-controls')
class GrEditFileControls extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when an action in the overflow menu is tapped.
   *
   * @event file-action-tap
   */

  @property({type: String})
  filePath?: string;

  @property({type: Array})
  _allFileActions = Object.values(GrEditConstants.Actions);

  @property({type: Array, computed: '_computeFileActions(_allFileActions)'})
  _fileActions?: EditAction[];

  _handleActionTap(e: CustomEvent) {
    e.preventDefault();
    e.stopPropagation();
    this._dispatchFileAction(e.detail.id, this.filePath);
  }

  _dispatchFileAction(action: EditAction, path?: string) {
    this.dispatchEvent(
      new CustomEvent('file-action-tap', {
        detail: {action, path},
        bubbles: true,
        composed: true,
      })
    );
  }

  _computeFileActions(actions: EditAction[]) {
    // TODO(kaspern): conditionally disable some actions based on file status.
    return actions.map(action => {
      return {
        name: action.label,
        id: action.id,
      };
    });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-edit-file-controls': GrEditFileControls;
  }
}
