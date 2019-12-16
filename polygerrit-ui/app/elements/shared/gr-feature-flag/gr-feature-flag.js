/**
 * @license
 * Copyright (C) 2019 The Android Open Source Project
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
  const Defs = {};

  /**
   * @typedef {{
   *   name: string;
   *   description: string;
   *   percentage: number;
   *   authOnly: boolean;
   * }}
   */
  Defs.FeatureFlag;

  /**
   * FeatureFlagProvider
   */
  class FeatureFlagProvider {
    constructor() {
      this._featureStore = window.FEATURES || {topHeader: {percentage: 0}};
    }

    /**
     * Returns if feature enabled or not
     * @param {string} feature - name of the feature
     */
    isEnabled(feature, user) {
      const featureFlag = this._featureStore[feature];
      if (!featureFlag) return false;
      if (featureFlag.authOnly && !user) return false;
      if (featureFlag.percentage >= 100) return true;

      // no user
      if (!user || !user.email) return Math.random() * 100 < feature.percentage;

      // Whitelist user
      if (window.FEATURE_FLAG_WHITELIST &&
        window.FEATURE_FLAG_WHITELIST.includes(user.email)) {
        return true;
      }

      // Allocate based on user id hash
      return (this._hashCode(user.email) % 100) < feature.percentage;
    }

    /**
     * Generates a hash from a string.
     * @param {string} str
     */
    _hashCode(str = '') {
      let hash = 0; let i; let chr;
      if (str.length === 0) return hash;
      for (i = 0; i < str.length; i++) {
        chr = str.charCodeAt(i);
        hash = ((hash << 5) - hash) + chr;
        hash |= 0;
      }
      return hash;
    }
  }

  /**
   * GrFeatureFlag
   *
   * @example
   *
   * ```
   * <gr-feature-flag feature="newFeature">
   *   <div class="newFeature">test</div>
   * </gr-feature-flag>
   * ```
   */
  class GrFeatureFlag extends Polymer.GestureEventListeners(
      Polymer.LegacyElementMixin(
          Polymer.Element)) {
    static get is() { return 'gr-feature-flag'; }

    static get properties() {
      return {
        feature: {
          type: String,
          value: '',
        },

        _user: {
          type: Object,
          value() { return null; },
        },

        _isEnabled: {
          type: Boolean,
          computed: '_getEnabled(feature, _user, _provider)',
        },

        _provider: {
          type: Object,
          value() { return null; },
        },
      };
    }

    ready() {
      super.ready();
      Promise.all([
        Gerrit.awaitPluginsLoaded(),
        this.$.restAPI.getAccount(),
      ]).then((_, user) => {
        // TODO(taoalpha): use provider if plugin provides
        // fallback to use the default one
        this._provider = new FeatureFlagProvider();
        this._user = user;
      });
    }

    _getEnabled() {
      return this._provider ?
        this._provider.isEnabled(this.feature, this.user) : false;
    }
  }

  customElements.define(GrFeatureFlag.is, GrFeatureFlag);
})();
