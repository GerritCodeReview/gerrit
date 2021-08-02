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
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-dialog/gr-dialog';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-confirm-move-dialog_html';
import {KeyboardShortcutMixin} from '../../../mixins/keyboard-shortcut-mixin/keyboard-shortcut-mixin';
import {customElement, property} from '@polymer/decorators';
import {BranchName, RepoName} from '../../../types/common';
import {appContext} from '../../../services/app-context';
import {GrTypedAutocomplete} from '../../shared/gr-autocomplete/gr-autocomplete';

const SUGGESTIONS_LIMIT = 15;

// This avoids JSC_DYNAMIC_EXTENDS_WITHOUT_JSDOC closure compiler error.
const base = KeyboardShortcutMixin(PolymerElement);

// This is used to make sure 'branch'
// can be typed as BranchName.
export interface GrConfirmMoveDialog {
  $: {
    branchInput: GrTypedAutocomplete<BranchName>;
  };
}

@customElement('gr-confirm-move-dialog')
export class GrConfirmMoveDialog extends base {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the confirm button is pressed.
   *
   * @event confirm
   */

  /**
   * Fired when the cancel button is pressed.
   *
   * @event cancel
   */

  @property({type: String})
  branch = '' as BranchName;

  @property({type: String})
  message = '';

  @property({type: String})
  project?: RepoName;

  @property({type: Object})
  _query?: (input: string) => Promise<{name: BranchName}[]>;

  get keyBindings() {
    return {
      'ctrl+enter meta+enter': '_handleConfirmTap',
    };
  }

  private readonly restApiService = appContext.restApiService;

  constructor() {
    super();
    this._query = (text: string) => this._getProjectBranchesSuggestions(text);
  }

  _handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('confirm', {
        composed: true,
        bubbles: false,
      })
    );
  }

  _handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('cancel', {
        composed: true,
        bubbles: false,
      })
    );
  }

  _getProjectBranchesSuggestions(input: string) {
    if (!this.project) return Promise.reject(new Error('Missing project'));
    if (input.startsWith('refs/heads/')) {
      input = input.substring('refs/heads/'.length);
    }
    return this.restApiService
      .getRepoBranches(input, this.project, SUGGESTIONS_LIMIT)
      .then(response => {
        if (!response) return [];
        const branches: Array<{name: BranchName}> = [];
        for (const branchInfo of response) {
          let name: string = branchInfo.ref;
          if (name.startsWith('refs/heads/')) {
            name = name.substring('refs/heads/'.length);
          }
          branches.push({name: name as BranchName});
        }
        return branches;
      });
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-move-dialog': GrConfirmMoveDialog;
  }
}
