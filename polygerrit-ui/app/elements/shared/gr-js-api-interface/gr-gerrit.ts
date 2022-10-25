/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */

/**
 * This defines the Gerrit instance. All methods directly attached to Gerrit
 * should be defined or linked here.
 */
import {PluginLoader, PluginOptionMap} from './gr-plugin-loader';
import {PluginApi} from '../../../api/plugin';
import {AuthService} from '../../../services/gr-auth/gr-auth';
import {RestApiService} from '../../../services/gr-rest-api/gr-rest-api';
import {ReportingService} from '../../../services/gr-reporting/gr-reporting';
import {
  EventCallback,
  EventEmitterService,
} from '../../../services/gr-event-interface/gr-event-interface';
import {Gerrit} from '../../../api/gerrit';
import {fontStyles} from '../../../styles/gr-font-styles';
import {formStyles} from '../../../styles/gr-form-styles';
import {menuPageStyles} from '../../../styles/gr-menu-page-styles';
import {spinnerStyles} from '../../../styles/gr-spinner-styles';
import {subpageStyles} from '../../../styles/gr-subpage-styles';
import {tableStyles} from '../../../styles/gr-table-styles';
import {assertIsDefined} from '../../../utils/common-util';
import {iconStyles} from '../../../styles/gr-icon-styles';

/**
 * These are the methods and properties that are exposed explicitly in the
 * public global `Gerrit` interface. In reality JavaScript plugins do depend
 * on some of this "internal" stuff. But we want to convert plugins to
 * TypeScript one by one and while doing that remove those dependencies.
 */
export interface GerritInternal extends EventEmitterService, Gerrit {
  install(
    callback: (plugin: PluginApi) => void,
    opt_version?: string,
    src?: string
  ): void;
}

const fakeApi = {
  getPluginName: () => 'global',
};

/**
 * TODO(brohlfs): Reduce this step by step until it only contains install().
 * Exported only for tests and gr-app.ts
 */
export class GerritImpl implements GerritInternal {
  public readonly styles = {
    font: fontStyles,
    form: formStyles,
    icon: iconStyles,
    menuPage: menuPageStyles,
    spinner: spinnerStyles,
    subPage: subpageStyles,
    table: tableStyles,
  };

  constructor(
    private readonly reportingService: ReportingService,
    private readonly eventEmitter: EventEmitterService,
    private readonly restApiService: RestApiService,
    // Private but used and overriden in tests
    public pluginLoader: PluginLoader
  ) {
    assertIsDefined(this.reportingService, 'reportingService');
    assertIsDefined(this.eventEmitter, 'eventEmitter');
    assertIsDefined(this.restApiService, 'restApiService');
  }

  finalize() {}

  install(
    callback: (plugin: PluginApi) => void,
    version?: string,
    src?: string
  ) {
    this.pluginLoader.install(callback, version, src);
  }

  /**
   * Enabling EventEmitter interface on Gerrit.
   *
   * This will enable to signal across different parts of js code without relying on DOM,
   * including core to core, plugin to plugin and also core to plugin.
   *
   * @example
   *
   * // Emit this event from pluginA
   * Gerrit.install(pluginA => {
   *   fetch("some-api").then(() => {
   *     Gerrit.on("your-special-event", {plugin: pluginA});
   *   });
   * });
   *
   * // Listen on your-special-event from pluginB
   * Gerrit.install(pluginB => {
   *   Gerrit.on("your-special-event", ({plugin}) => {
   *     // do something, plugin is pluginA
   *   });
   * });
   */
  addListener(eventName: string, cb: EventCallback) {
    this.reportingService.trackApi(fakeApi, 'global', 'addListener');
    return this.eventEmitter.addListener(eventName, cb);
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  dispatch(eventName: string, detail: any) {
    this.reportingService.trackApi(fakeApi, 'global', 'dispatch');
    return this.eventEmitter.dispatch(eventName, detail);
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  emit(eventName: string, detail: any) {
    this.reportingService.trackApi(fakeApi, 'global', 'emit');
    return this.eventEmitter.emit(eventName, detail);
  }

  off(eventName: string, cb: EventCallback) {
    this.reportingService.trackApi(fakeApi, 'global', 'off');
    this.eventEmitter.off(eventName, cb);
  }

  on(eventName: string, cb: EventCallback) {
    this.reportingService.trackApi(fakeApi, 'global', 'on');
    return this.eventEmitter.on(eventName, cb);
  }

  once(eventName: string, cb: EventCallback) {
    this.reportingService.trackApi(fakeApi, 'global', 'once');
    return this.eventEmitter.once(eventName, cb);
  }

  removeAllListeners(eventName: string) {
    this.reportingService.trackApi(fakeApi, 'global', 'removeAllListeners');
    this.eventEmitter.removeAllListeners(eventName);
  }

  removeListener(eventName: string, cb: EventCallback) {
    this.reportingService.trackApi(fakeApi, 'global', 'removeListener');
    this.eventEmitter.removeListener(eventName, cb);
  }
}
