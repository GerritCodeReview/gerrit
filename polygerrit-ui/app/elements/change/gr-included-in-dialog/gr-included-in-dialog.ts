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
import '@polymer/iron-input/iron-input';
import '../../../styles/shared-styles';
import '../../shared/gr-button/gr-button';
import '../../shared/gr-rest-api-interface/gr-rest-api-interface';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-included-in-dialog_html';
import {customElement, property} from '@polymer/decorators';
import {IncludedInInfo, ChangeNum} from '../../../types/common';
import {RestApiService} from '../../../services/services/gr-rest-api/gr-rest-api';

export interface GrIncludedInDialog {
  $: {
    restAPI: RestApiService & Element;
  };
}

interface DisplayGroup {
  title: string;
  items: string[];
}

@customElement('gr-included-in-dialog')
export class GrIncludedInDialog extends GestureEventListeners(
  LegacyElementMixin(PolymerElement)
) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired when the user presses the close button.
   *
   * @event close
   */

  @property({type: Object, observer: '_resetData'})
  changeNum?: ChangeNum;

  @property({type: Object})
  _includedIn?: IncludedInInfo;

  @property({type: Boolean})
  _loaded = false;

  @property({type: String})
  _filterText = '';

  loadData() {
    if (!this.changeNum) {
      return;
    }
    this._filterText = '';
    return this.$.restAPI.getChangeIncludedIn(this.changeNum).then(configs => {
      if (!configs) {
        return;
      }
      this._includedIn = configs;
      this._loaded = true;
    });
  }

  _resetData() {
    this._includedIn = undefined;
    this._loaded = false;
  }

  _computeGroups(includedIn: IncludedInInfo | undefined, filterText: string) {
    if (!includedIn || filterText === undefined) {
      return [];
    }

    const filter = (item: string) =>
      !filterText.length ||
      item.toLowerCase().indexOf(filterText.toLowerCase()) !== -1;

    const groups: DisplayGroup[] = [
      {title: 'Branches', items: includedIn.branches.filter(filter)},
      {title: 'Tags', items: includedIn.tags.filter(filter)},
    ];
    if (includedIn.external) {
      for (const externalKey of Object.keys(includedIn.external)) {
        groups.push({
          title: externalKey,
          items: includedIn.external[externalKey].filter(filter),
        });
      }
    }
    return groups.filter(g => g.items.length);
  }

  _handleCloseTap(e: Event) {
    e.preventDefault();
    e.stopPropagation();
    this.dispatchEvent(
      new CustomEvent('close', {
        composed: true,
        bubbles: false,
      })
    );
  }

  _computeLoadingClass(loaded: boolean) {
    return loaded ? 'loading loaded' : 'loading';
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-included-in-dialog': GrIncludedInDialog;
  }
}
