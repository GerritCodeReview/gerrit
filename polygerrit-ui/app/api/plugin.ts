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

export declare interface PluginApi {
  _url?: URL;
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
  registerStyleModule(endpoint: string, moduleName: string): void;
  reporting(): ReportingPluginApi;
  restApi(): RestPluginApi;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  screen(screenName: string, moduleName?: string): any;
}
