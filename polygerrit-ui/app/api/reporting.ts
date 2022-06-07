/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
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
