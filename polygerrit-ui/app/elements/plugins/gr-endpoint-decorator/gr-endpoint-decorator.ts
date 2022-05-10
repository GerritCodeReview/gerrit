/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {html, LitElement} from 'lit';
import {customElement, property} from 'lit/decorators';
import {
  getPluginEndpoints,
  ModuleInfo,
} from '../../shared/gr-js-api-interface/gr-plugin-endpoints';
import {getPluginLoader} from '../../shared/gr-js-api-interface/gr-plugin-loader';
import {PluginApi} from '../../../api/plugin';
import {HookApi, PluginElement} from '../../../api/hook';
import {getAppContext} from '../../../services/app-context';
import {assertIsDefined} from '../../../utils/common-util';

const INIT_PROPERTIES_TIMEOUT_MS = 10000;

@customElement('gr-endpoint-decorator')
export class GrEndpointDecorator extends LitElement {
  /**
   * If set, then this endpoint only invokes callbacks registered by the target
   * plugin. For example this is used for the `check-result-expanded` endpoint.
   * In that case Gerrit knows which plugin has provided the check result, and
   * only that plugin has an interest to hook into the endpoint.
   */
  @property({type: String})
  targetPlugin?: string;

  /** Required. */
  @property({type: String})
  name?: string;

  private readonly domHooks = new Map<PluginElement, HookApi<PluginElement>>();

  private readonly initializedPlugins = new Map<string, boolean>();

  private readonly reporting = getAppContext().reportingService;

  override render() {
    return html`<slot></slot>`;
  }

  override connectedCallback() {
    super.connectedCallback();
    assertIsDefined(this.name);
    getPluginEndpoints().onNewEndpoint(this.name, this.initModule);
    getPluginLoader()
      .awaitPluginsLoaded()
      .then(() => {
        assertIsDefined(this.name);
        const modules = getPluginEndpoints().getDetails(this.name);
        for (const module of modules) {
          this.initModule(module);
        }
      });
  }

  override disconnectedCallback() {
    for (const [el, domHook] of this.domHooks) {
      domHook.handleInstanceDetached(el);
    }
    assertIsDefined(this.name);
    getPluginEndpoints().onDetachedEndpoint(this.name, this.initModule);
    super.disconnectedCallback();
  }

  private initDecoration(
    name: string,
    plugin: PluginApi,
    slot?: string
  ): Promise<HTMLElement> {
    const el = document.createElement(name) as PluginElement;
    return this.initProperties(
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
        this.appendChild(el);
      }
      return el;
    });
  }

  private initReplacement(
    name: string,
    plugin: PluginApi
  ): Promise<HTMLElement> {
    // The direct children are slotted into <slot>, so they are identical to
    // this.shadowRoot.querySelector('slot').assignedElements().
    const directChildren = [...this.childNodes];
    const shadowChildren = [...(this.shadowRoot?.childNodes ?? [])];
    [...directChildren, ...shadowChildren]
      .filter(node => node.nodeName !== 'GR-ENDPOINT-PARAM')
      .filter(node => node.nodeName !== 'SLOT')
      .forEach(node => node.remove());
    const el = document.createElement(name);
    return this.initProperties(el, plugin).then((el: HTMLElement) =>
      this.appendChild(el)
    );
  }

  private getEndpointParams() {
    return Array.from(this.querySelectorAll('gr-endpoint-param'));
  }

  private initProperties(
    el: PluginElement,
    plugin: PluginApi,
    content?: Element | null
  ) {
    const pluginName = plugin.getPluginName();
    el.plugin = plugin;
    // The content is (only?) used in ChangeReplyPluginApi.
    // Maybe it would be better for the consumer side to figure out the content
    // with something like el.getRootNode().host, etc.
    // Also note that the content element could easily end up being an instance
    // of <gr-endpoint-param>.
    if (content) {
      el.content = content as HTMLElement;
    }
    const expectProperties = this.getEndpointParams().map(paramEl => {
      const helper = plugin.attributeHelper(paramEl);
      const paramName = paramEl.name;
      if (!paramName) {
        this.reporting.error(
          new Error(
            `plugin '${pluginName}' endpoint '${this.name}': param is missing a name.`
          )
        );
        return;
      }
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
          this.reporting.error(
            new Error(
              'Timeout waiting for endpoint properties initialization: ' +
                `plugin ${pluginName}, endpoint ${this.name}`
            )
          );
        }, INIT_PROPERTIES_TIMEOUT_MS))
    );
    return Promise.race([timeout, Promise.all(expectProperties)])
      .then(() => el)
      .finally(() => {
        if (timeoutId) clearTimeout(timeoutId);
      });
  }

  private readonly initModule = ({
    moduleName,
    plugin,
    type,
    domHook,
    slot,
  }: ModuleInfo) => {
    const name = plugin.getPluginName() + '.' + moduleName;
    if (this.targetPlugin) {
      if (this.targetPlugin !== plugin.getPluginName()) return;
    }
    if (this.initializedPlugins.get(name)) {
      return;
    }
    let initPromise;
    switch (type) {
      case 'decorate':
        initPromise = this.initDecoration(moduleName, plugin, slot);
        break;
      case 'replace':
        initPromise = this.initReplacement(moduleName, plugin);
        break;
    }
    if (!initPromise) {
      throw Error(`unknown endpoint type ${type} used by plugin ${name}`);
    }
    this.initializedPlugins.set(name, true);
    initPromise.then(el => {
      if (domHook) {
        domHook.handleInstanceAttached(el);
        this.domHooks.set(el, domHook);
      }
    });
  };
}

declare global {
  interface HTMLElementTagNameMap {
    'gr-endpoint-decorator': GrEndpointDecorator;
  }
}
