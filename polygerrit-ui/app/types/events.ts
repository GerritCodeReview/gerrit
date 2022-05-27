/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {PatchSetNum} from './common';
import {ChangeMessage, Comment} from '../utils/comment-util';
import {FetchRequest} from './types';
import {LineNumberEventDetail, MovedLinkClickedEventDetail} from '../api/diff';
import {Category, RunStatus} from '../api/checks';

export enum EventType {
  BIND_VALUE_CHANGED = 'bind-value-changed',
  CHANGE = 'change',
  CHANGED = 'changed',
  CHANGE_MESSAGE_DELETED = 'change-message-deleted',
  COMMIT = 'commit',
  DIALOG_CHANGE = 'dialog-change',
  DROP = 'drop',
  EDITABLE_CONTENT_SAVE = 'editable-content-save',
  GR_RPC_LOG = 'gr-rpc-log',
  IRON_ANNOUNCE = 'iron-announce',
  KEYDOWN = 'keydown',
  KEYPRESS = 'keypress',
  LOCATION_CHANGE = 'location-change',
  MOVED_LINK_CLICKED = 'moved-link-clicked',
  NETWORK_ERROR = 'network-error',
  OPEN_FIX_PREVIEW = 'open-fix-preview',
  CLOSE_FIX_PREVIEW = 'close-fix-preview',
  PAGE_ERROR = 'page-error',
  RECREATE_CHANGE_VIEW = 'recreate-change-view',
  RECREATE_DIFF_VIEW = 'recreate-diff-view',
  RELOAD = 'reload',
  REPLY = 'reply',
  SERVER_ERROR = 'server-error',
  SHORTCUT_TRIGGERERD = 'shortcut-triggered',
  SHOW_ALERT = 'show-alert',
  SHOW_ERROR = 'show-error',
  SHOW_PRIMARY_TAB = 'show-primary-tab',
  SHOW_SECONDARY_TAB = 'show-secondary-tab',
  TAP_ITEM = 'tap-item',
  TITLE_CHANGE = 'title-change',
}

declare global {
  interface HTMLElementEventMap {
    /* prettier-ignore */
    'bind-value-changed': BindValueChangeEvent;
    /* prettier-ignore */
    'change': ChangeEvent;
    /* prettier-ignore */
    'changed': ChangedEvent;
    'change-message-deleted': ChangeMessageDeletedEvent;
    /* prettier-ignore */
    'commit': CommitEvent;
    'dialog-change': DialogChangeEvent;
    /* prettier-ignore */
    'drop': DropEvent;
    'editable-content-save': EditableContentSaveEvent;
    'location-change': LocationChangeEvent;
    'iron-announce': IronAnnounceEvent;
    'line-mouse-enter': LineNumberEvent;
    'line-mouse-leave': LineNumberEvent;
    'line-cursor-moved-in': LineNumberEvent;
    'line-cursor-moved-out': LineNumberEvent;
    'moved-link-clicked': MovedLinkClickedEvent;
    'open-fix-preview': OpenFixPreviewEvent;
    'close-fix-preview': CloseFixPreviewEvent;
    'create-fix-comment': CreateFixCommentEvent;
    /* prettier-ignore */
    'reload': ReloadEvent;
    /* prettier-ignore */
    'reply': ReplyEvent;
    'show-alert': ShowAlertEvent;
    'show-error': ShowErrorEvent;
    'show-primary-tab': SwitchTabEvent;
    'show-secondary-tab': SwitchTabEvent;
    'tap-item': TapItemEvent;
    'title-change': TitleChangeEvent;
  }
}

declare global {
  interface DocumentEventMap {
    'gr-rpc-log': RpcLogEvent;
    'network-error': NetworkErrorEvent;
    'page-error': PageErrorEvent;
    /* prettier-ignore */
    'reload': ReloadEvent;
    'server-error': ServerErrorEvent;
    'show-alert': ShowAlertEvent;
    'show-error': ShowErrorEvent;
  }
}

export interface BindValueChangeEventDetail {
  value: string;
}
export type BindValueChangeEvent = CustomEvent<BindValueChangeEventDetail>;

export type ChangeEvent = InputEvent;

export type ChangedEvent = CustomEvent<string>;

export interface ChangeMessageDeletedEventDetail {
  message: ChangeMessage;
}
export type ChangeMessageDeletedEvent =
  CustomEvent<ChangeMessageDeletedEventDetail>;

export type CommitEvent = CustomEvent;

// TODO(milutin) - remove once new gr-dialog will do it out of the box
// This informs gr-app-element to remove footer, header from a11y tree
export interface DialogChangeEventDetail {
  canceled?: boolean;
  opened?: boolean;
}
export type DialogChangeEvent = CustomEvent<DialogChangeEventDetail>;

export type DropEvent = DragEvent;

export interface EditableContentSaveEventDetail {
  content: string;
}
export type EditableContentSaveEvent =
  CustomEvent<EditableContentSaveEventDetail>;

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

export type LineNumberEvent = CustomEvent<LineNumberEventDetail>;

export interface NetworkErrorEventDetail {
  error: Error;
}
export type NetworkErrorEvent = CustomEvent<NetworkErrorEventDetail>;

export interface OpenFixPreviewEventDetail {
  patchNum?: PatchSetNum;
  comment?: Comment;
}
export type OpenFixPreviewEvent = CustomEvent<OpenFixPreviewEventDetail>;

export interface CloseFixPreviewEventDetail {
  fixApplied: boolean;
}
export type CloseFixPreviewEvent = CustomEvent<CloseFixPreviewEventDetail>;
export interface CreateFixCommentEventDetail {
  patchNum?: PatchSetNum;
  comment?: Comment;
}
export type CreateFixCommentEvent = CustomEvent<CreateFixCommentEventDetail>;

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
  /** regular expression for filtering runs */
  filter?: string;
  /** regular expression for selecting runs */
  select?: string;
  /** selected attempt for selected runs */
  attempt?: number;
}
export type SwitchTabEvent = CustomEvent<SwitchTabEventDetail>;

export type TapItemEvent = CustomEvent;

export interface TitleChangeEventDetail {
  title: string;
}
export type TitleChangeEvent = CustomEvent<TitleChangeEventDetail>;

/**
 * This event can be used for Polymer properties that have `notify: true` set.
 * But it is also generally recommended when you want to notify your parent
 * elements about a property update, also for Lit elements.
 *
 * The name of the event should be `prop-name-changed`.
 */
export type ValueChangedEvent<T = string> = CustomEvent<{value: T}>;
