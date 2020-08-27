/**
 * @license
 * Copyright (C) 2020 The Android Open Source Project
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
import {GrAttributeHelper} from './gr-attribute-helper/gr-attribute-helper';
import {GrPluginRestApi} from '../shared/gr-js-api-interface/gr-plugin-rest-api';
import {GrEventHelper} from './gr-event-helper/gr-event-helper';
import {GrPopupInterface} from './gr-popup-interface/gr-popup-interface';
import {
  GrChangeActions,
  GrReplyDialog,
} from '../../services/services/gr-rest-api/gr-rest-api';
import {GrPluginActionContext} from '../shared/gr-js-api-interface/gr-plugin-action-context';
import {ConfigInfo} from '../../types/common';

interface GerritElementExtensions {
  content?: HTMLElement & {hidden?: boolean};
  change?: unknown;
  revision?: unknown;
  token?: string;
  repoName?: string;
  config?: ConfigInfo;
}
export type HookCallback = (el: HTMLElement & GerritElementExtensions) => void;

export interface HookApi {
  onAttached(callback: HookCallback): HookApi;
  onDetached(callback: HookCallback): HookApi;
  getAllAttached(): HTMLElement[];
  getLastAttached(): Promise<HTMLElement>;
  getModuleName(): string;
}

export enum ApiElement {
  CHANGE_ACTIONS = 'changeactions',
  REPLY_DIALOG = 'replydialog',
}

export interface RestApiTagNameMap {
  [ApiElement.REPLY_DIALOG]: GrReplyDialog;
  [ApiElement.CHANGE_ACTIONS]: GrChangeActions;
}

export interface JsApiService extends HTMLElement {
  getElement<K extends keyof RestApiTagNameMap>(
    elementKey: K
  ): RestApiTagNameMap[K];
  addEventCallback(eventName: string, callback: () => void): void;
}

export interface RegisterOptions {
  slot?: string;
  replace: unknown;
}

export interface PanelInfo {
  body: Element;
  p: {[key: string]: any};
  onUnload: () => void;
}

export interface SettingsInfo {
  body: Element;
  token?: string;
  onUnload: () => void;
  setTitle: () => void;
  setWindowTitle: () => void;
  show: () => void;
}

export interface PluginApi {
  _url?: URL;
  deprecated: PluginDeprecatedApi;
  hook(endpointName: string, opt_options?: RegisterOptions): HookApi;
  getPluginName(): string;
  on(eventName: string, target: any): void;
  attributeHelper(element: Element): GrAttributeHelper;
  restApi(): GrPluginRestApi;
  eventHelper(element: Node): GrEventHelper;
}

export interface PluginDeprecatedApi {
  _loadedGwt(): void;
  popup(element: Node): GrPopupInterface;
  onAction(
    type: string,
    action: string,
    callback: (ctx: GrPluginActionContext) => void
  ): void;
  panel(extensionpoint: string, callback: (panel: PanelInfo) => void): void;
  screen(pattern: string, callback: (settings: SettingsInfo) => void): void;
  settingsScreen(
    path: string,
    menu: string,
    callback: (settings: SettingsInfo) => void
  ): void;
}
