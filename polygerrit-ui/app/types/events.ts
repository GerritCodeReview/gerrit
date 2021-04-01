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
import {EventApi} from '@polymer/polymer/lib/legacy/polymer.dom';
import {PatchSetNum, UrlEncodedCommentId} from './common';
import {UIComment} from '../utils/comment-util';
import {FetchRequest} from './types';
import {MovedLinkClickedEventDetail} from '../api/diff';
import {Category, RunStatus} from '../api/checks';
import {ChangeMessage} from '../elements/change/gr-message/gr-message';

export enum EventType {
  CHANGE_MESSAGE_DELETED = 'change-message-deleted',
  DIALOG_CHANGE = 'dialog-change',
  EDITABLE_CONTENT_SAVE = 'editable-content-save',
  GR_RPC_LOG = 'gr-rpc-log',
  LOCATION_CHANGE = 'location-change',
  IRON_ANNOUNCE = 'iron-announce',
  MOVED_LINK_CLICKED = 'moved-link-clicked',
  NETWORK_ERROR = 'network-error',
  OPEN_FIX_PREVIEW = 'open-fix-preview',
  CLOSE_FIX_PREVIEW = 'close-fix-preview',
  PAGE_ERROR = 'page-error',
  RELOAD = 'reload',
  REPLY = 'reply',
  SERVER_ERROR = 'server-error',
  SHORTCUT_TRIGGERERD = 'shortcut-triggered',
  SHOW_ALERT = 'show-alert',
  SHOW_ERROR = 'show-error',
  SHOW_PRIMARY_TAB = 'show-primary-tab',
  SHOW_SECONDARY_TAB = 'show-secondary-tab',
  THREAD_LIST_MODIFIED = 'thread-list-modified',
  TITLE_CHANGE = 'title-change',
}

declare global {
  interface HTMLElementEventMap {
    'change-message-deleted': ChangeMessageDeletedEvent;
    'dialog-change': DialogChangeEvent;
    'editable-content-save': EditableContentSaveEvent;
    'location-change': LocationChangeEvent;
    'iron-announce': IronAnnounceEvent;
    'moved-link-clicked': MovedLinkClickedEvent;
    'open-fix-preview': OpenFixPreviewEvent;
    'close-fix-preview': CloseFixPreviewEvent;
    /* prettier-ignore */
    'reload': ReloadEvent;
    /* prettier-ignore */
    'reply': ReplyEvent;
    'shortcut-triggered': ShortcutTriggeredEvent;
    'show-alert': ShowAlertEvent;
    'show-error': ShowErrorEvent;
    'show-primary-tab': SwitchTabEvent;
    'show-secondary-tab': SwitchTabEvent;
    'thread-list-modified': ThreadListModifiedEvent;
    'title-change': TitleChangeEvent;
  }
}

declare global {
  interface DocumentEventMap {
    'gr-rpc-log': RpcLogEvent;
    'network-error': NetworkErrorEvent;
    'page-error': PageErrorEvent;
    'server-error': ServerErrorEvent;
    'show-alert': ShowAlertEvent;
    'show-error': ShowErrorEvent;
  }
}

export interface ChangeMessageDeletedEventDetail {
  message: ChangeMessage;
}
export type ChangeMessageDeletedEvent = CustomEvent<
  ChangeMessageDeletedEventDetail
>;

// TODO(milutin) - remove once new gr-dialog will do it out of the box
// This informs gr-app-element to remove footer, header from a11y tree
export interface DialogChangeEventDetail {
  canceled?: boolean;
  opened?: boolean;
}
export type DialogChangeEvent = CustomEvent<DialogChangeEventDetail>;

export interface EditableContentSaveEventDetail {
  content: string;
}
export type EditableContentSaveEvent = CustomEvent<
  EditableContentSaveEventDetail
>;

export interface RpcLogEventDetail {
  status: number | null;
  method: string;
  elapsed: number;
  anonymizedUrl: string;
}
export type RpcLogEvent = CustomEvent<RpcLogEventDetail>;

export interface IronAnnounceEventDetail {
  text: string;
}
export type IronAnnounceEvent = CustomEvent<IronAnnounceEventDetail>;

export interface LocationChangeEventDetail {
  hash: string;
  pathname: string;
}
export type LocationChangeEvent = CustomEvent<LocationChangeEventDetail>;

export type MovedLinkClickedEvent = CustomEvent<MovedLinkClickedEventDetail>;

export interface NetworkErrorEventDetail {
  error: Error;
}
export type NetworkErrorEvent = CustomEvent<NetworkErrorEventDetail>;

export interface OpenFixPreviewEventDetail {
  patchNum?: PatchSetNum;
  comment?: UIComment;
}
export type OpenFixPreviewEvent = CustomEvent<OpenFixPreviewEventDetail>;

export interface CloseFixPreviewEventDetail {
  fixApplied: boolean;
}
export type CloseFixPreviewEvent = CustomEvent<CloseFixPreviewEventDetail>;

export interface PageErrorEventDetail {
  response?: Response;
}
export type PageErrorEvent = CustomEvent<PageErrorEventDetail>;

export interface ReloadEventDetail {
  clearPatchset: boolean;
}
export type ReloadEvent = CustomEvent<ReloadEventDetail>;

export interface ReplyEventDetail {
  message: ChangeMessage;
}
export type ReplyEvent = CustomEvent<ReplyEventDetail>;

export interface ServerErrorEventDetail {
  request?: FetchRequest;
  response: Response;
}
export type ServerErrorEvent = CustomEvent<ServerErrorEventDetail>;

export interface ShortcutTriggeredEventDetail {
  event: CustomKeyboardEvent;
  goKey: boolean;
  vKey: boolean;
}
export type ShortcutTriggeredEvent = CustomEvent<ShortcutTriggeredEventDetail>;

export interface ShowAlertEventDetail {
  message: string;
  dismissOnNavigation?: boolean;
  showDismiss?: boolean;
  action?: string;
  callback?: () => void;
}
export type ShowAlertEvent = CustomEvent<ShowAlertEventDetail>;

export interface ShowErrorEventDetail {
  message: string;
}
export type ShowErrorEvent = CustomEvent<ShowErrorEventDetail>;

// Type for the custom event to switch tab.
export interface SwitchTabEventDetail {
  // name of the tab to set as active, from custom event
  tab?: string;
  // index of tab to set as active, from paper-tabs event
  value?: number;
  // scroll into the tab afterwards, from custom event
  scrollIntoView?: boolean;
  // define state of tab after opening
  tabState?: TabState;
}
export interface TabState {
  commentTab?: CommentTabState;
  checksTab?: ChecksTabState;
}
export enum CommentTabState {
  UNRESOLVED = 'unresolved',
  DRAFTS = 'drafts',
  SHOW_ALL = 'show all',
}
export interface ChecksTabState {
  statusOrCategory?: RunStatus | Category;
  checkName?: string;
}
export type SwitchTabEvent = CustomEvent<SwitchTabEventDetail>;

export interface ThreadListModifiedDetail {
  rootId: UrlEncodedCommentId;
  path: string;
}
export type ThreadListModifiedEvent = CustomEvent<ThreadListModifiedDetail>;

export interface TitleChangeEventDetail {
  title: string;
}
export type TitleChangeEvent = CustomEvent<TitleChangeEventDetail>;

/**
 * Keyboard events emitted from polymer elements.
 */
export interface CustomKeyboardEvent extends CustomEvent, EventApi {
  event: CustomKeyboardEvent;
  detail: {
    keyboardEvent?: CustomKeyboardEvent;
    // TODO(TS): maybe should mark as optional and check before accessing
    key: string;
  };
  readonly altKey: boolean;
  readonly changedTouches: TouchList;
  readonly ctrlKey: boolean;
  readonly metaKey: boolean;
  readonly shiftKey: boolean;
  readonly keyCode: number;
  readonly repeat: boolean;
}
