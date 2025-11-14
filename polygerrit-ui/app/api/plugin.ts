/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {AdminPluginApi} from './admin';
import {AnnotationPluginApi} from './annotation';
import {ChangeReplyPluginApi} from './change-reply';
import {ChecksPluginApi} from './checks';
import {EventHelperPluginApi} from './event-helper';
import {PluginElement} from './hook';
import {PopupPluginApi} from './popup';
import {ReportingPluginApi} from './reporting';
import {ChangeActionsPluginApi} from './change-actions';
import {RestPluginApi} from './rest';
import {HookApi, RegisterOptions} from './hook';
import {StylePluginApi} from './styles';
import {SuggestionsPluginApi} from './suggestions';
import {ChangeUpdatesPluginApi} from './change-updates';
import {AiCodeReviewPluginApi} from './ai-code-review';

export enum TargetElement {
  CHANGE_ACTIONS = 'changeactions',
  REPLY_DIALOG = 'replydialog',
}

// Note: for new events, naming convention should be: `a-b`
export enum EventType {
  BEFORE_CHANGE_ACTION = 'before-change-action',
  LABEL_CHANGE = 'labelchange',
  SHOW_CHANGE = 'showchange',
  SUBMIT_CHANGE = 'submitchange',
  // Fires GerritView values such as 'change', 'dashboard', 'admin', ...
  VIEW_CHANGE = 'view-change',
  SHOW_REVISION_ACTIONS = 'show-revision-actions',
  BEFORE_COMMIT_MSG_EDIT = 'before-commit-msg-edit',
  COMMIT_MSG_EDIT = 'commitmsgedit',
  CUSTOM_EMOJIS = 'custom-emojis',
  REVERT = 'revert',
  REVERT_SUBMISSION = 'revert_submission',
  POST_REVERT = 'postrevert',
  ADMIN_MENU_LINKS = 'admin-menu-links',
  SHOW_DIFF = 'showdiff',
  BEFORE_REPLY_SENT = 'before-reply-sent',
  REPLY_SENT = 'replysent',
  BEFORE_PUBLISH_EDIT = 'before-publish-edit',
  PUBLISH_EDIT = 'publish-edit',
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
  aiCodeReview(): AiCodeReviewPluginApi;
  annotationApi(): AnnotationPluginApi;
  changeActions(): ChangeActionsPluginApi;
  changeReply(): ChangeReplyPluginApi;
  changeUpdates(): ChangeUpdatesPluginApi;
  checks(): ChecksPluginApi;
  suggestions(): SuggestionsPluginApi;
  eventHelper(element: Node): EventHelperPluginApi;
  getPluginName(): string;
  hook<T extends PluginElement>(
    endpointName: string,
    opt_options?: RegisterOptions
  ): HookApi<T>;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  on(eventName: EventType, target: any): void;
  popup(): Promise<PopupPluginApi>;
  popup(moduleName: string): Promise<PopupPluginApi>;
  popup(moduleName?: string): Promise<PopupPluginApi | null>;
  registerCustomComponent<T extends PluginElement>(
    endpointName: string,
    moduleName?: string,
    options?: RegisterOptions
  ): HookApi<T>;
  registerDynamicCustomComponent<T extends PluginElement>(
    endpointName: string,
    moduleName?: string,
    options?: RegisterOptions
  ): HookApi<T>;
  reporting(): ReportingPluginApi;
  restApi(): RestPluginApi;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  screen(screenName: string, moduleName?: string): any;
  styleApi(): StylePluginApi;
}
