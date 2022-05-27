/**
 * @license
 * Copyright 2017 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {AttributeHelperPluginApi} from '../../../api/attribute-helper';
import {PluginApi} from '../../../api/plugin';
import {getAppContext} from '../../../services/app-context';

export class GrAttributeHelper implements AttributeHelperPluginApi {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  private readonly _promises = new Map<string, Promise<any>>();

  private readonly reporting = getAppContext().reportingService;

  // TODO(TS): Change any to something more like HTMLElement.
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  constructor(readonly plugin: PluginApi, public element: any) {
    this.reporting.trackApi(this.plugin, 'attribute', 'constructor');
  }

  _getChangedEventName(name: string): string {
    return name.replace(/([a-z])([A-Z])/g, '$1-$2').toLowerCase() + '-changed';
  }

  /**
   * Returns true if the property is defined on wrapped element.
   */
  _elementHasProperty(name: string) {
    return this.element[name] !== undefined;
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  _reportValue(callback: (value: any) => void, value: any) {
    try {
      callback(value);
    } catch (e) {
      console.info(e);
    }
  }

  /**
   * Binds callback to property updates.
   *
   * @param name Property name.
   * @return Unbind function.
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  bind(name: string, callback: (value: any) => void) {
    this.reporting.trackApi(this.plugin, 'attribute', 'bind');
    const attributeChangedEventName = this._getChangedEventName(name);
    const changedHandler = (e: CustomEvent) =>
      this._reportValue(callback, e.detail.value);
    const unbind = () =>
      this.element.removeEventListener(
        attributeChangedEventName,
        changedHandler
      );
    this.element.addEventListener(attributeChangedEventName, changedHandler);
    if (this._elementHasProperty(name)) {
      this._reportValue(callback, this.element[name]);
    }
    return unbind;
  }

  /**
   * Get value of the property from wrapped object. Waits for the property
   * to be initialized if it isn't defined.
   */
  get(name: string): Promise<unknown> {
    this.reporting.trackApi(this.plugin, 'attribute', 'get');
    if (this._elementHasProperty(name)) {
      return Promise.resolve(this.element[name]);
    }
    if (!this._promises.has(name)) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      let resolve: (value: any) => void;
      const promise = new Promise(r => (resolve = r));
      const unbind = this.bind(name, value => {
        resolve(value);
        unbind();
      });
      this._promises.set(name, promise);
    }
    return this._promises.get(name)!;
  }

  /**
   * Sets value of property (not attribute!) and dispatches event to force
   * notify.
   */
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  set(name: string, value: any) {
    this.reporting.trackApi(this.plugin, 'attribute', 'set');
    this.element[name] = value;
    this.element.dispatchEvent(
      new CustomEvent(this._getChangedEventName(name), {detail: {value}})
    );
  }
}
