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
import '../../shared/gr-js-api-interface/gr-js-api-interface.js';
import {dom} from '@polymer/polymer/lib/legacy/polymer.dom.js';
import {GestureEventListeners} from '@polymer/polymer/lib/mixins/gesture-event-listeners.js';
import {LegacyElementMixin} from '@polymer/polymer/lib/legacy/legacy-element-mixin.js';
import {PolymerElement} from '@polymer/polymer/polymer-element.js';
import {htmlTemplate} from './gr-endpoint-decorator_html.js';
import {getPluginEndpoints} from '../../shared/gr-js-api-interface/gr-plugin-endpoints.js';
import {pluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader.js';

const INIT_PROPERTIES_TIMEOUT_MS = 10000;

/** @extends PolymerElement */
class GrEndpointDecorator extends GestureEventListeners(
    LegacyElementMixin(
        PolymerElement)) {
  static get template() { return htmlTemplate; }

  static get is() { return 'gr-endpoint-decorator'; }

  static get properties() {
    return {
      name: String,
      /** @type {!Map} */
      _domHooks: {
        type: Map,
        value() { return new Map(); },
      },
      /**
       * This map prevents importing the same endpoint twice.
       * Without caching, if a plugin is loaded after the loaded plugins
       * callback fires, it will be imported twice and appear twice on the page.
       *
       * @type {!Map}
       */
      _initializedPlugins: {
        type: Map,
        value() { return new Map(); },
      },
    };
  }

  /** @override */
  detached() {
    super.detached();
    for (const [el, domHook] of this._domHooks) {
      domHook.handleInstanceDetached(el);
    }
    getPluginEndpoints().onDetachedEndpoint(this.name, this._endpointCallBack);
  }

  _initDecoration(name, plugin, slot) {
    const el = document.createElement(name);
    return this._initProperties(el, plugin,
        this.getContentChildren().find(
            el => el.nodeName !== 'GR-ENDPOINT-PARAM'))
        .then(el => {
          const slotEl = slot ?
            dom(this).querySelector(`gr-endpoint-slot[name=${slot}]`) :
            null;
          if (slot && slotEl) {
            slotEl.parentNode.insertBefore(el, slotEl.nextSibling);
          } else {
            this._appendChild(el);
          }
          return el;
        });
  }

  _initReplacement(name, plugin) {
    this.getContentChildNodes()
        .filter(node => node.nodeName !== 'GR-ENDPOINT-PARAM')
        .forEach(node => node.remove());
    const el = document.createElement(name);
    return this._initProperties(el, plugin).then(
        el => this._appendChild(el));
  }

  _getEndpointParams() {
    return Array.from(
        dom(this).querySelectorAll('gr-endpoint-param'));
  }

  /**
   * @param {!Element} el
   * @param {!Object} plugin
   * @param {!Element=} opt_content
   * @return {!Promise<Element>}
   */
  _initProperties(el, plugin, opt_content) {
    el.plugin = plugin;
    if (opt_content) {
      el.content = opt_content;
    }
    const expectProperties = this._getEndpointParams().map(paramEl => {
      const helper = plugin.attributeHelper(paramEl);
      const paramName = paramEl.getAttribute('name');
      return helper.get('value').then(
          value => helper.bind('value',
              value => plugin.attributeHelper(el).set(paramName, value))
      );
    });
    let timeoutId;
    const timeout = new Promise(
        resolve => timeoutId = setTimeout(() => {
          console.warn(
              'Timeout waiting for endpoint properties initialization: ' +
            `plugin ${plugin.getPluginName()}, endpoint ${this.name}`);
        }, INIT_PROPERTIES_TIMEOUT_MS));
    return Promise.race([timeout, Promise.all(expectProperties)])
        .then(() => el)
        .finally(() => {
          if (timeoutId) clearTimeout(timeoutId);
        });
  }

  _appendChild(el) {
    return dom(this.root).appendChild(el);
  }

  _initModule({moduleName, plugin, type, domHook, slot}) {
    const name = plugin.getPluginName() + '.' + moduleName;
    if (this._initializedPlugins.get(name)) {
      return;
    }
    let initPromise;
    switch (type) {
      case 'decorate':
        initPromise = this._initDecoration(moduleName, plugin, slot);
        break;
      case 'replace':
        initPromise = this._initReplacement(moduleName, plugin);
        break;
    }
    if (!initPromise) {
      console.warn('Unable to initialize module ' + name);
    }
    this._initializedPlugins.set(name, true);
    initPromise.then(el => {
      domHook.handleInstanceAttached(el);
      this._domHooks.set(el, domHook);
    });
  }

  /** @override */
  ready() {
    super.ready();
    this._endpointCallBack = this._initModule.bind(this);
    getPluginEndpoints().onNewEndpoint(this.name, this._endpointCallBack);
    if (this.name) {
      pluginLoader.awaitPluginsLoaded()
          .then(() => getPluginEndpoints().getAndImportPlugins(this.name))
          .then(() =>
            getPluginEndpoints()
                .getDetails(this.name)
                .forEach(this._initModule, this)
          );
    }
  }
}

customElements.define(GrEndpointDecorator.is, GrEndpointDecorator);
