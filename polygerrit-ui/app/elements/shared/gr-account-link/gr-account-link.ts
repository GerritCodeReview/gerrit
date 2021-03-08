/**
 * @license
 * Copyright (C) 2015 The Android Open Source Project
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

import '../gr-account-label/gr-account-label';
import '../../../styles/shared-styles';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-account-link_html';
import {GerritNav} from '../../core/gr-navigation/gr-navigation';
import {customElement, property} from '@polymer/decorators';
import {AccountInfo, ChangeInfo} from '../../../types/common';

@customElement('gr-account-link')
class GrAccountLink extends LegacyElementMixin(PolymerElement) {
  static get template() {
    return htmlTemplate;
  }

  @property({type: String})
  voteableText?: string;

  @property({type: Object})
  account?: AccountInfo;

  /**
   * Optional ChangeInfo object, typically comes from the change page or
   * from a row in a list of search results. This is needed for some change
   * related features like adding the user as a reviewer.
   */
  @property({type: Object})
  change?: ChangeInfo;

  /**
   * Should this user be considered to be in the attention set, regardless
   * of the current state of the change object?
   */
  @property({type: Boolean})
  forceAttention = false;

  /**
   * Should attention set related features be shown in the component? Note
   * that the information whether the user is in the attention set or not is
   * part of the ChangeInfo object in the change property.
   */
  @property({type: Boolean})
  highlightAttention = false;

  @property({type: Boolean})
  hideAvatar = false;

  @property({type: Boolean})
  hideStatus = false;

  /**
   * Only show the first name in the account label.
   */
  @property({type: Boolean})
  firstName = false;

  _computeOwnerLink(account?: AccountInfo) {
    if (!account) {
      return;
    }
    return GerritNav.getUrlForOwner(
      account.email ||
        account.username ||
        account.name ||
        `${account._account_id}`
    );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-account-link': GrAccountLink;
  }
}
