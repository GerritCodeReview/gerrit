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

import '../../shared/gr-dropdown/gr-dropdown';
import {GrEditConstants} from '../gr-edit-constants';
import {sharedStyles} from '../../../styles/shared-styles';
import {GrLitElement} from '../../lit/gr-lit-element';
import {css, customElement, html, property} from 'lit-element';

interface EditAction {
  label: string;
  id: string;
}

@customElement('gr-edit-file-controls')
export class GrEditFileControls extends GrLitElement {
  /**
   * Fired when an action in the overflow menu is tapped.
   *
   * @event file-action-tap
   */

  @property({type: String})
  filePath?: string;

  @property({type: Array})
  _allFileActions = Object.values(GrEditConstants.Actions);

  static override get styles() {
    return [
      sharedStyles,
      css`
        :host {
          align-items: center;
          display: flex;
          justify-content: flex-end;
        }
        #actions {
          margin-right: var(--spacing-l);
        }
      `,
    ];
  }

  override render() {
    // To pass CSS mixins for @apply to Polymer components, they need to appear
    // in <style> inside the template.
    const customStyle = html`
      <style>
        gr-button,
        gr-dropdown {
          --gr-button: {
            height: 1.8em;
          }
        }
        gr-dropdown {
          --gr-dropdown-item: {
            background-color: transparent;
            border: none;
            color: var(--link-color);
            text-transform: uppercase;
          }
        }
      </style>
    `;
    const fileActions = this._computeFileActions(this._allFileActions);
    return html`${customStyle}
      <gr-dropdown
        id="actions"
        .items=${fileActions}
        down-arrow=""
        vertical-offset="20"
        @tap-item="${this._handleActionTap}"
        link=""
        >Actions</gr-dropdown
      >`;
  }

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
