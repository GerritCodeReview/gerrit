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
(function() {
  'use strict';

  const AUTH = [
    'OPENID',
    'OAUTH',
  ];

  /**
   * @appliesMixin Gerrit.BaseUrlMixin
   */
  class GrIdentities extends Polymer.mixinBehaviors( [
    Gerrit.BaseUrlBehavior,
  ], Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element))) {
    static get is() { return 'gr-identities'; }

    static get properties() {
      return {
        _identities: Object,
        _idName: String,
        serverConfig: Object,
        _showLinkAnotherIdentity: {
          type: Boolean,
          computed: '_computeShowLinkAnotherIdentity(serverConfig)',
        },
      };
    }

    loadData() {
      return this.$.restAPI.getExternalIds().then(id => {
        this._identities = id;
      });
    }

    _computeIdentity(id) {
      return id && id.startsWith('mailto:') ? '' : id;
    }

    _computeHideDeleteClass(canDelete) {
      return canDelete ? 'show' : '';
    }

    _handleDeleteItemConfirm() {
      this.$.overlay.close();
      return this.$.restAPI.deleteAccountIdentity([this._idName])
          .then(() => { this.loadData(); });
    }

    _handleConfirmDialogCancel() {
      this.$.overlay.close();
    }

    _handleDeleteItem(e) {
      const name = e.model.get('item.identity');
      if (!name) { return; }
      this._idName = name;
      this.$.overlay.open();
    }

    _computeIsTrusted(item) {
      return item ? '' : 'Untrusted';
    }

    filterIdentities(item) {
      return !item.identity.startsWith('username:');
    }

    _computeShowLinkAnotherIdentity(config) {
      if (config && config.auth &&
          config.auth.git_basic_auth_policy) {
        return AUTH.includes(
            config.auth.git_basic_auth_policy.toUpperCase());
      }

      return false;
    }

    _computeLinkAnotherIdentity() {
      const baseUrl = this.getBaseUrl() || '';
      let pathname = window.location.pathname;
      if (baseUrl) {
        pathname = '/' + pathname.substring(baseUrl.length);
      }
      return baseUrl + '/login/' + encodeURIComponent(pathname) + '?link';
    }
  }

  customElements.define(GrIdentities.is, GrIdentities);
})();
