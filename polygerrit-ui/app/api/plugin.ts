/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {AdminPluginApi} from './admin';
import {AnnotationPluginApi} from './annotation';
import {AttributeHelperPluginApi} from './attribute-helper';
import {ChangeReplyPluginApi} from './change-reply';
import {ChecksPluginApi} from './checks';
import {EventHelperPluginApi} from './event-helper';
import {PopupPluginApi} from './popup';
import {ReportingPluginApi} from './reporting';
import {ChangeActionsPluginApi} from './change-actions';
import {RestPluginApi} from './rest';
import {HookApi, RegisterOptions} from './hook';
import {StylePluginApi} from './styles';

export enum TargetElement {
  CHANGE_ACTIONS = 'changeactions',
  REPLY_DIALOG = 'replydialog',
}

// Note: for new events, naming convention should be: `a-b`
export enum EventType {
  LABEL_CHANGE = 'labelchange',
  SHOW_CHANGE = 'showchange',
  SUBMIT_CHANGE = 'submitchange',
  SHOW_REVISION_ACTIONS = 'show-revision-actions',
  COMMIT_MSG_EDIT = 'commitmsgedit',
  REVERT = 'revert',
  REVERT_SUBMISSION = 'revert_submission',
  POST_REVERT = 'postrevert',
  ADMIN_MENU_LINKS = 'admin-menu-links',
}

export declare interface PluginApi {
  /**
   * The raw URL of the plugin's js bundle, e.g.:
   * https://cdn.googlesource.com/polygerrit_assets/533.0/plugins/codemirror_editor/static/codemirror_editor.js'
   */
  _url?: URL;
  /**
   * The base path of plugin related resources. Depends on whether the plugin
   * was loaded from the same origin as the Gerrit web app itself.
   *
   * Same origin: The base path of all Gerrit URLs, e.g.:
   * https://gerrit-review.googlesource.com/
   *
   * Different origin: The root path of plugin files, e.g.:
   * https://cdn.googlesource.com/polygerrit_assets/533.0/plugins/codemirror_editor/'
   */
  url(): string;
  admin(): AdminPluginApi;
  annotationApi(): AnnotationPluginApi;
  attributeHelper(element: Element): AttributeHelperPluginApi;
  changeActions(): ChangeActionsPluginApi;
  changeReply(): ChangeReplyPluginApi;
  checks(): ChecksPluginApi;
  eventHelper(element: Node): EventHelperPluginApi;
  getPluginName(): string;
  hook<T extends HTMLElement>(
    endpointName: string,
    opt_options?: RegisterOptions
  ): HookApi<T>;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  on(eventName: EventType, target: any): void;
  popup(): Promise<PopupPluginApi>;
  popup(moduleName: string): Promise<PopupPluginApi>;
  popup(moduleName?: string): Promise<PopupPluginApi | null>;
  registerCustomComponent<T extends HTMLElement>(
    endpointName: string,
    moduleName?: string,
    options?: RegisterOptions
  ): HookApi<T>;
  registerDynamicCustomComponent<T extends HTMLElement>(
    endpointName: string,
    moduleName?: string,
    options?: RegisterOptions
  ): HookApi<T>;
  // DEPRECATED: Just add <style> elements to `document.head`.
  registerStyleModule(endpoint: string, moduleName: string): void;
  reporting(): ReportingPluginApi;
  restApi(): RestPluginApi;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  screen(screenName: string, moduleName?: string): any;
  styleApi(): StylePluginApi;
}
