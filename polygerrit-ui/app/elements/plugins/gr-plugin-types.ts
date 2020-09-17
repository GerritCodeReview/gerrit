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
  handleInstanceDetached(instance: HTMLElement): void;
  handleInstanceAttached(instance: HTMLElement): void;
}

export enum TargetElement {
  CHANGE_ACTIONS = 'changeactions',
  REPLY_DIALOG = 'replydialog',
}

// Note: for new events, naming convention should be: `a-b`
export enum EventType {
  HISTORY = 'history',
  LABEL_CHANGE = 'labelchange',
  SHOW_CHANGE = 'showchange',
  SUBMIT_CHANGE = 'submitchange',
  SHOW_REVISION_ACTIONS = 'show-revision-actions',
  COMMIT_MSG_EDIT = 'commitmsgedit',
  COMMENT = 'comment',
  REVERT = 'revert',
  REVERT_SUBMISSION = 'revert_submission',
  POST_REVERT = 'postrevert',
  ANNOTATE_DIFF = 'annotatediff',
  ADMIN_MENU_LINKS = 'admin-menu-links',
  HIGHLIGHTJS_LOADED = 'highlightjs-loaded',
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
  popup(): Promise<GrPopupInterface>;
  popup(moduleName: string): Promise<GrPopupInterface>;
  popup(moduleName?: string): Promise<GrPopupInterface | null>;
  hook(endpointName: string, opt_options?: RegisterOptions): HookApi;
  getPluginName(): string;
  on(eventName: string, target: any): void;
  attributeHelper(element: Element): GrAttributeHelper;
  restApi(): GrPluginRestApi;
  eventHelper(element: Node): GrEventHelper;
}
