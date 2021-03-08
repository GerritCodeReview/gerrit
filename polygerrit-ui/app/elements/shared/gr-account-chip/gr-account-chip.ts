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
import '../gr-account-link/gr-account-link';
import '../gr-button/gr-button';
import '../gr-icons/gr-icons';
import '../../../styles/shared-styles';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin';
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-account-chip_html';
import {customElement, property} from '@polymer/decorators';
import {AccountInfo, ChangeInfo} from '../../../types/common';
import {appContext} from '../../../services/app-context';

@customElement('gr-account-chip')
export class GrAccountChip extends LegacyElementMixin(PolymerElement) {
  static get template() {
    return htmlTemplate;
  }

  /**
   * Fired to indicate a key was pressed while this chip was focused.
   *
   * @event account-chip-keydown
   */

  /**
   * Fired to indicate this chip should be removed, i.e. when the x button is
   * clicked or when the remove function is called.
   *
   * @event remove
   */

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

  @property({type: String})
  voteableText?: string;

  @property({type: Boolean, reflectToAttribute: true})
  disabled = false;

  @property({type: Boolean, reflectToAttribute: true})
  removable = false;

  /**
   * Should attention set related features be shown in the component? Note
   * that the information whether the user is in the attention set or not is
   * part of the ChangeInfo object in the change property.
   */
  @property({type: Boolean})
  highlightAttention = false;

  @property({type: Boolean, reflectToAttribute: true})
  showAvatar?: boolean;

  @property({type: Boolean})
  transparentBackground = false;

  private readonly restApiService = appContext.restApiService;

  /** @override */
  ready() {
    super.ready();
    this._getHasAvatars().then(hasAvatars => {
      this.showAvatar = hasAvatars;
    });
  }

  _getBackgroundClass(transparent: boolean) {
    return transparent ? 'transparentBackground' : '';
  }

  _handleRemoveTap(e: MouseEvent) {
    e.preventDefault();
    this.dispatchEvent(
      new CustomEvent('remove', {
        detail: {account: this.account},
        composed: true,
        bubbles: true,
      })
    );
  }

  _getHasAvatars() {
    return this.restApiService
      .getConfig()
      .then(cfg =>
        Promise.resolve(!!(cfg && cfg.plugin && cfg.plugin.has_avatars))
      );
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-account-chip': GrAccountChip;
  }
}
