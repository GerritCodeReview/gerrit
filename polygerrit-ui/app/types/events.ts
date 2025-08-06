/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {
  AccountInfo,
  ChangeMessage,
  DropdownLink,
  FixSuggestionInfo,
  PatchSetNum,
} from './common';
import {FetchRequest} from './types';
import {LineNumberEventDetail, MovedLinkClickedEventDetail} from '../api/diff';
import {Category, RunStatus} from '../api/checks';

// TODO: Local events that are only fired by one component should also be
// declared and documented in that component. Don't collect ALL the events here.
// 'show-alert' for example is fine to keep, because it is fired all over the
// place. But 'line-cursor-moved-in' is only fired by <gr-diff-cursor>, so let's
// move it there.
declare global {
  interface HTMLElementEventMap {
    'add-reviewer': AddReviewerEvent;
    'bind-value-changed': BindValueChangeEvent;
    /** Fired when a 'cancel' button in a dialog was pressed. */
    // prettier-ignore
    'cancel': CustomEvent<{}>;
    // prettier-ignore
    'change': ChangeEvent;
    // prettier-ignore
    'changed': ChangedEvent;
    // prettier-ignore
    'close': CustomEvent<{}>;
    // prettier-ignore
    'commit': AutocompleteCommitEvent;
    /** Fired when a 'confirm' button in a dialog was pressed. */
    // prettier-ignore
    'confirm': CustomEvent<{}>;
    // prettier-ignore
    'drop': DropEvent;
    'hide-alert': CustomEvent<{}>;
    'location-change': LocationChangeEvent;
    'iron-announce': IronAnnounceEvent;
    'iron-resize': CustomEvent<{}>;
    'line-mouse-enter': LineNumberEvent;
    'line-mouse-leave': LineNumberEvent;
    'line-cursor-moved-in': LineNumberEvent;
    'line-cursor-moved-out': LineNumberEvent;
    'moved-link-clicked': MovedLinkClickedEvent;
    'open-fix-preview': OpenFixPreviewEvent;
    'reply-to-comment': ReplyToCommentEvent;
    // prettier-ignore
    'reload': CustomEvent<{}>;
    'remove-reviewer': RemoveReviewerEvent;
    'show-alert': ShowAlertEvent;
    'show-error': ShowErrorEvent;
    'show-tab': SwitchTabEvent;
    'show-secondary-tab': SwitchTabEvent;
    'tap-item': TapItemEvent;
  }
}

declare global {
  interface DocumentEventMap {
    'gr-rpc-log': RpcLogEvent;
    'network-error': NetworkErrorEvent;
    'page-error': PageErrorEvent;
    // prettier-ignore
    'reload': CustomEvent<{}>;
    'server-error': ServerErrorEvent;
    'show-alert': ShowAlertEvent;
    'show-error': ShowErrorEvent;
    'auth-error': AuthErrorEvent;
    'title-change': TitleChangeEvent;
  }
}

export interface AutocompleteCommitEventDetail {
  value: string;
}

export type AutocompleteCommitEvent =
  CustomEvent<AutocompleteCommitEventDetail>;

export interface AddAccountEventDetail {
  value: string;
}
export type AddAccountEvent = CustomEvent<AddAccountEventDetail>;

export interface AddReviewerEventDetail {
  reviewer: AccountInfo;
}
export type AddReviewerEvent = CustomEvent<AddReviewerEventDetail>;

export interface RemoveReviewerEventDetail {
  reviewer: AccountInfo;
}
export type RemoveReviewerEvent = CustomEvent<RemoveReviewerEventDetail>;

export interface BindValueChangeEventDetail {
  value: string | undefined;
}
export type BindValueChangeEvent = CustomEvent<BindValueChangeEventDetail>;

export type ChangeEvent = InputEvent;

// TODO: This event seems to be unused (no listener). Remove?
export type ChangedEvent = CustomEvent<string | undefined>;

export interface ChangeMessageDeletedEventDetail {
  message: ChangeMessage;
}
export type ChangeMessageDeletedEvent =
  CustomEvent<ChangeMessageDeletedEventDetail>;

export type DropEvent = DragEvent;

export interface EditableContentSaveEventDetail {
  content: string;
  committerEmail: string | null;
}
export type EditableContentSaveEvent =
  CustomEvent<EditableContentSaveEventDetail>;

export interface FileActionTapEventDetail {
  path: string;
  action: string;
}

export type FileActionTapEvent = CustomEvent<FileActionTapEventDetail>;

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
  patchNum: PatchSetNum;
  fixSuggestions: FixSuggestionInfo[];
  onCloseFixPreviewCallbacks: ((fixapplied: boolean) => void)[];
}
export type OpenFixPreviewEvent = CustomEvent<OpenFixPreviewEventDetail>;

export interface ReplyToCommentEventDetail {
  content: string;
  userWantsToEdit: boolean;
  unresolved: boolean;
}

export type ReplyToCommentEvent = CustomEvent<ReplyToCommentEventDetail>;

export interface PageErrorEventDetail {
  response?: Response;
}
export type PageErrorEvent = CustomEvent<PageErrorEventDetail>;

export interface RemoveAccountEventDetail {
  account: AccountInfo;
}
export type RemoveAccountEvent = CustomEvent<RemoveAccountEventDetail>;

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

export interface ShowReplyDialogEventDetail {
  value: {
    reviewersOnly: boolean;
    ccsOnly: boolean;
  };
}
export type ShowReplyDialogEvent = CustomEvent<ShowReplyDialogEventDetail>;

export interface AuthErrorEventDetail {
  message: string;
  action: string;
}
export type AuthErrorEvent = CustomEvent<AuthErrorEventDetail>;

// Type for the custom event to switch tab.
export interface SwitchTabEventDetail {
  // name of the tab to set as active, from custom event
  tab: string;
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
  MENTIONS = 'mentions',
}
export interface ChecksTabState {
  statusOrCategory?: RunStatus | Category;
  checkName?: string;
}
export type SwitchTabEvent = CustomEvent<SwitchTabEventDetail>;

export type TapItemEvent = CustomEvent<DropdownLink>;

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
