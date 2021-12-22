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

// eslint-disable-next-line @typescript-eslint/no-explicit-any
export type EventDetails = any;

export enum Deduping {
  /**
   * Only report the event once per session, even if the event details are
   * different.
   */
  EVENT_ONCE_PER_SESSION = 'EVENT_ONCE_PER_SESSION',
  /**
   * Only report the event once per change, even if the event details are
   * different.
   */
  EVENT_ONCE_PER_CHANGE = 'EVENT_ONCE_PER_CHANGE',
  /** Only report these exact event details once per session. */
  DETAILS_ONCE_PER_SESSION = 'DETAILS_ONCE_PER_SESSION',
  /** Only report these exact event details once per change. */
  DETAILS_ONCE_PER_CHANGE = 'DETAILS_ONCE_PER_CHANGE',
}
export declare interface ReportingOptions {
  /** Set this, if you don't want to report *every* time. */
  deduping?: Deduping;
}

export declare interface ReportingPluginApi {
  reportInteraction(eventName: string, details?: EventDetails): void;

  reportLifeCycle(eventName: string, details?: EventDetails): void;
}
