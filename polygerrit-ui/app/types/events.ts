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
import {PatchSetNum} from './common';
import {UIComment} from '../utils/comment-util';

export interface TitleChangeEventDetail {
  title: string;
}

export type TitleChangeEvent = CustomEvent<TitleChangeEventDetail>;

declare global {
  interface HTMLElementEventMap {
    'title-change': TitleChangeEvent;
  }
}

export interface PageErrorEventDetail {
  response: Response;
}

export type PageErrorEvent = CustomEvent<PageErrorEventDetail>;

declare global {
  interface HTMLElementEventMap {
    'page-error': PageErrorEvent;
  }
}

export interface LocationChangeEventDetail {
  hash: string;
  pathname: string;
}

export type LocationChangeEvent = CustomEvent<LocationChangeEventDetail>;

declare global {
  interface HTMLElementEventMap {
    'location-change': LocationChangeEvent;
  }
}

export interface RpcLogEventDetail {
  status: number | null;
  method: string;
  elapsed: number;
  anonymizedUrl: string;
}

export type RpcLogEvent = CustomEvent<RpcLogEventDetail>;

declare global {
  interface HTMLElementEventMap {
    'rpc-log': RpcLogEvent;
  }
}

export interface ShortcutTriggeredEventDetail {
  event: CustomKeyboardEvent;
  goKey: boolean;
  vKey: boolean;
}

export type ShortcutTriggeredEvent = CustomEvent<ShortcutTriggeredEventDetail>;

declare global {
  interface HTMLElementEventMap {
    'shortcut-triggered': ShortcutTriggeredEvent;
  }
}

export interface EditableContentSaveEventDetail {
  content: string;
}

export type EditableContentSaveEvent = CustomEvent<
  EditableContentSaveEventDetail
>;

declare global {
  interface HTMLElementEventMap {
    'editable-content-save': EditableContentSaveEvent;
  }
}

export interface OpenFixPreviewEventDetail {
  patchNum?: PatchSetNum;
  comment?: UIComment;
}

export type OpenFixPreviewEvent = CustomEvent<OpenFixPreviewEventDetail>;

declare global {
  interface HTMLElementEventMap {
    'open-fix-preview': OpenFixPreviewEvent;
  }
}

// Type for the custom event to switch tab.
interface SwitchTabEventDetail {
  // name of the tab to set as active, from custom event
  tab?: string;
  // index of tab to set as active, from paper-tabs event
  value?: number;
  // scroll into the tab afterwards, from custom event
  scrollIntoView?: boolean;
}

export type SwitchTabEvent = CustomEvent<SwitchTabEventDetail>;

declare global {
  interface HTMLElementEventMap {
    'show-primary-tab': SwitchTabEvent;
    'show-secondary-tab': SwitchTabEvent;
  }
}

export interface ReloadEventDetail {
  clearPatchset: boolean;
}

export type ReloadEvent = CustomEvent<ReloadEventDetail>;

declare global {
  interface HTMLElementEventMap {
    reload: ReloadEvent;
  }
}

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
