/**
 * @license
 * Copyright (C) 2016 The Android Open Source Project
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
import '../../shared/gr-autocomplete/gr-autocomplete';
import '../../shared/gr-dialog/gr-dialog';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import '../../../styles/shared-styles';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-confirm-rebase-dialog_html';
import {customElement, property, observe} from '@polymer/decorators';
import {hasOwnProperty} from '../../../utils/common-util';
import {NumericChangeId, BranchName} from '../../../types/common';
import {
  GrAutocomplete,
  AutocompleteQuery,
  AutocompleteSuggestion,
} from '../../shared/gr-autocomplete/gr-autocomplete';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';

interface RebaseChange {
  name: string;
  value: NumericChangeId;
}

export interface ConfirmRebaseEventDetail {
  base: string | null;
}

export interface GrConfirmRebaseDialog {
  $: {
    restAPI: RestApiService & Element;
    parentInput: GrAutocomplete;
    rebaseOnParentInput: HTMLInputElement;
    rebaseOnOtherInput: HTMLInputElement;
    rebaseOnTipInput: HTMLInputElement;
  };
}

@customElement('gr-confirm-rebase-dialog')
export class GrConfirmRebaseDialog extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
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
  branch?: BranchName;

  @property({type: Number})
  changeNumber?: NumericChangeId;

  @property({type: Boolean})
  hasParent?: boolean;

  @property({type: Boolean})
  rebaseOnCurrent?: boolean;

  @property({type: String})
  _text?: string;

  @property({type: Object})
  _query?: AutocompleteQuery;

  @property({type: Array})
  _recentChanges?: RebaseChange[];

  constructor() {
    super();
    this._query = input => this._getChangeSuggestions(input);
  }

  // This is called by gr-change-actions every time the rebase dialog is
  // re-opened. Unlike other autocompletes that make a request with each
  // updated input, this one gets all recent changes once and then filters
  // them by the input. The query is re-run each time the dialog is opened
  // in case there are new/updated changes in the generic query since the
  // last time it was run.
  fetchRecentChanges() {
    return this.$.restAPI
      .getChanges(undefined, 'is:open -age:90d')
      .then(response => {
        if (!response) return [];
        const changes: RebaseChange[] = [];
        for (const key in response) {
          if (!hasOwnProperty(response, key)) {
            continue;
          }
          changes.push({
            name: `${response[key]._number}: ${response[key].subject}`,
            value: response[key]._number,
          });
        }
        this._recentChanges = changes;
        return this._recentChanges;
      });
  }

  _getRecentChanges() {
    if (this._recentChanges) {
      return Promise.resolve(this._recentChanges);
    }
    return this.fetchRecentChanges();
  }

  _getChangeSuggestions(input: string) {
    return this._getRecentChanges().then(changes =>
      this._filterChanges(input, changes)
    );
  }

  _filterChanges(
    input: string,
    changes: RebaseChange[]
  ): AutocompleteSuggestion[] {
    return changes
      .filter(
        change =>
          change.name.includes(input) && change.value !== this.changeNumber
      )
      .map(
        change =>
          ({
            name: change.name,
            value: `${change.value}`,
          } as AutocompleteSuggestion)
      );
  }

  _displayParentOption(rebaseOnCurrent: boolean, hasParent: boolean) {
    return hasParent && rebaseOnCurrent;
  }

  _displayParentUpToDateMsg(rebaseOnCurrent: boolean, hasParent: boolean) {
    return hasParent && !rebaseOnCurrent;
  }

  _displayTipOption(rebaseOnCurrent: boolean, hasParent: boolean) {
    return !(!rebaseOnCurrent && !hasParent);
  }

  /**
   * There is a subtle but important difference between setting the base to an
   * empty string and omitting it entirely from the payload. An empty string
   * implies that the parent should be cleared and the change should be
   * rebased on top of the target branch. Leaving out the base implies that it
   * should be rebased on top of its current parent.
   */
  _getSelectedBase() {
    if (this.$.rebaseOnParentInput.checked) {
      return null;
    }
    if (this.$.rebaseOnTipInput.checked) {
      return '';
    }
    if (!this._text) {
      return '';
    }
    // Change numbers will have their description appended by the
    // autocomplete.
    return this._text.split(':')[0];
  }

  _handleConfirmTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    const detail: ConfirmRebaseEventDetail = {
      base: this._getSelectedBase(),
    };
    this.dispatchEvent(new CustomEvent('confirm', {detail}));
    this._text = '';
  }

  _handleCancelTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(new CustomEvent('cancel'));
    this._text = '';
  }

  _handleRebaseOnOther() {
    this.$.parentInput.focus();
  }

  _handleEnterChangeNumberClick() {
    this.$.rebaseOnOtherInput.checked = true;
  }

  /**
   * Sets the default radio button based on the state of the app and
   * the corresponding value to be submitted.
   */
  @observe('rebaseOnCurrent', 'hasParent')
  _updateSelectedOption(rebaseOnCurrent?: boolean, hasParent?: boolean) {
    // Polymer 2: check for undefined
    if (rebaseOnCurrent === undefined || hasParent === undefined) {
      return;
    }

    if (this._displayParentOption(rebaseOnCurrent, hasParent)) {
      this.$.rebaseOnParentInput.checked = true;
    } else if (this._displayTipOption(rebaseOnCurrent, hasParent)) {
      this.$.rebaseOnTipInput.checked = true;
    } else {
      this.$.rebaseOnOtherInput.checked = true;
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-confirm-rebase-dialog': GrConfirmRebaseDialog;
  }
}
