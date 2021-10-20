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
import {PolymerElement} from '@polymer/polymer/polymer-element';
import {htmlTemplate} from './gr-endpoint-decorator_html';
import {
  getPluginEndpoints,
  ModuleInfo,
} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {customElement, property} from '@polymer/decorators';
import {PluginApi} from '../../../api/plugin';
import {HookApi, PluginElement} from '../../../api/hook';

const INIT_PROPERTIES_TIMEOUT_MS = 10000;

@customElement('gr-endpoint-decorator')
export class GrEndpointDecorator extends PolymerElement {
  static get template() {
    return htmlTemplate;
  }

  /**
   * If set, then this endpoint only invokes callbacks registered by the target
   * plugin. For example this is used for the `check-result-expanded` endpoint.
   * In that case Gerrit knows which plugin has provided the check result, and
   * only that plugin has an interest to hook into the endpoint.
   */
  @property({type: String})
  targetPlugin?: string;

  @property({type: String})
  name!: string;

  @property({type: Object})
  _domHooks = new Map<PluginElement, HookApi<PluginElement>>();

  @property({type: Object})
  _initializedPlugins = new Map<string, boolean>();

  /**
   * This is the callback that the plugin endpoint manager should be calling
   * when a new element is registered for this endpoint. It points to
   * _initModule().
   */
  _endpointCallBack: (info: ModuleInfo) => void = () => {};

  override disconnectedCallback() {
    for (const [el, domHook] of this._domHooks) {
      domHook.handleInstanceDetached(el);
    }
    getPluginEndpoints().onDetachedEndpoint(this.name, this._endpointCallBack);
    super.disconnectedCallback();
  }

  _initDecoration(
    name: string,
    plugin: PluginApi,
    slot?: string
  ): Promise<HTMLElement> {
    const el = document.createElement(name) as PluginElement;
    return this._initProperties(
      el,
      plugin,
      // The direct children are slotted into <slot>, so this is identical to
      // this.shadowRoot.querySelector('slot').assignedElements()[0].
      this.firstElementChild
    ).then(el => {
      const slotEl = slot
        ? this.querySelector(`gr-endpoint-slot[name=${slot}]`)
        : null;
      if (slot && slotEl?.parentNode) {
        slotEl.parentNode.insertBefore(el, slotEl.nextSibling);
      } else {
        this._appendChild(el);
      }
      return el;
    });
  }

  // As of March 2021 the only known plugin that replaces an endpoint instead
  // of decorating it is codemirror_editor.
  _initReplacement(name: string, plugin: PluginApi): Promise<HTMLElement> {
    // The direct children are slotted into <slot>, so they are identical to
    // this.shadowRoot.querySelector('slot').assignedElements().
    const directChildren = [...this.childNodes];
    const shadowChildren = [...(this.shadowRoot?.childNodes ?? [])];
    [...directChildren, ...shadowChildren]
      .filter(node => node.nodeName !== 'GR-ENDPOINT-PARAM')
      .filter(node => node.nodeName !== 'SLOT')
      .forEach(node => (node as ChildNode).remove());
    const el = document.createElement(name);
    return this._initProperties(el, plugin).then((el: HTMLElement) =>
      this._appendChild(el)
    );
  }

  _getEndpointParams() {
    return Array.from(this.querySelectorAll('gr-endpoint-param'));
  }

  _initProperties(
    el: PluginElement,
    plugin: PluginApi,
    content?: Element | null
  ) {
    el.plugin = plugin;
    // The content is (only?) used in ChangeReplyPluginApi.
    // Maybe it would be better for the consumer side to figure out the content
    // with something like el.getRootNode().host, etc.
    // Also note that the content element could easily end up being an instance
    // of <gr-endpoint-param>.
    if (content) {
      el.content = content as HTMLElement;
    }
    const expectProperties = this._getEndpointParams().map(paramEl => {
      const helper = plugin.attributeHelper(paramEl);
      // TODO: this should be replaced by accessing the property directly
      const paramName = paramEl.getAttribute('name');
      if (!paramName) throw Error('plugin endpoint parameter missing a name');
      return helper.get('value').then(() =>
        helper.bind('value', value =>
          // Note that despite the naming this sets the property, not the
          // attribute. :-)
          plugin.attributeHelper(el).set(paramName, value)
        )
      );
    });
    let timeoutId: number;
    const timeout = new Promise(
      () =>
        // specify window here so that TS pulls the correct setTimeout method
        // if window is not specified, then the function is pulled from node
        // and the return type is NodeJS.Timeout object
        (timeoutId = window.setTimeout(() => {
          console.warn(
            'Timeout waiting for endpoint properties initialization: ' +
              `plugin ${plugin.getPluginName()}, endpoint ${this.name}`
          );
        }, INIT_PROPERTIES_TIMEOUT_MS))
    );
    return Promise.race([timeout, Promise.all(expectProperties)])
      .then(() => el)
      .finally(() => {
        if (timeoutId) clearTimeout(timeoutId);
      });
  }

  _appendChild(el: HTMLElement): HTMLElement {
    if (!this.root) throw Error('plugin endpoint decorator missing root');
    return this.root.appendChild(el);
  }

  _initModule({moduleName, plugin, type, domHook, slot}: ModuleInfo) {
    const name = plugin.getPluginName() + '.' + moduleName;
    if (this.targetPlugin) {
      if (this.targetPlugin !== plugin.getPluginName()) return;
    }
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
      throw Error(`unknown endpoint type ${type} used by plugin ${name}`);
    }
    this._initializedPlugins.set(name, true);
    initPromise.then(el => {
      if (domHook) {
        domHook.handleInstanceAttached(el);
        this._domHooks.set(el, domHook);
      }
    });
  }

  override ready() {
    super.ready();
    this._endpointCallBack = (info: ModuleInfo) => this._initModule(info);
    getPluginEndpoints().onNewEndpoint(this.name, this._endpointCallBack);
    if (this.name) {
      getPluginLoader()
        .awaitPluginsLoaded()
        .then(() =>
          getPluginEndpoints()
            .getDetails(this.name)
            .forEach(this._initModule, this)
        );
    }
  }
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-endpoint-decorator': GrEndpointDecorator;
  }
}
