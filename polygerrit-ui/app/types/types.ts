/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {DiffLayer as DiffLayerApi} from '../api/diff';
import {DiffViewMode, MessageTag, Side} from '../constants/constants';
import {IronA11yAnnouncer} from '@polymer/iron-a11y-announcer/iron-a11y-announcer';
import {FlattenedNodesObserver} from '@polymer/polymer/lib/utils/flattened-nodes-observer';
import {PaperInputElement} from '@polymer/paper-input/paper-input';
import {
  AccountInfo,
  BasePatchSetNum,
  ChangeId,
  ChangeViewChangeInfo,
  CommitId,
  CommitInfo,
  NumericChangeId,
  PatchRange,
  PatchSetNum,
  ReviewerUpdateInfo,
  RevisionInfo,
  Timestamp,
} from './common';
import {PolymerSpliceChange} from '@polymer/polymer/interfaces';
import {AuthRequestInit} from '../services/gr-auth/gr-auth';

export function notUndefined<T>(x: T | undefined): x is T {
  return x !== undefined;
}

export interface FixIronA11yAnnouncer extends IronA11yAnnouncer {
  requestAvailability(): void;
}

export interface CommitRange {
  baseCommit: CommitId;
  commit: CommitId;
}

export {CoverageRange, CoverageType} from '../api/diff';

export enum ErrorType {
  AUTH = 'AUTH',
  NETWORK = 'NETWORK',
  GENERIC = 'GENERIC',
}

/**
 * We would like to access the the typed `nativeInput` of PaperInputElement, so
 * we are creating this wrapper.
 */
export type PaperInputElementExt = PaperInputElement & {
  $: {nativeInput?: Element};
};

/**
 * If Polymer would have exported DomApiNative from its dom.js utility, then we
 * would probably not need this type. We just use it for casting the return
 * value of dom(element).
 */
export interface PolymerDomWrapper {
  getOwnerRoot(): Node & OwnerRoot;
  getEffectiveChildNodes(): Node[];
  observeNodes(
    callback: (p0: {
      target: HTMLElement;
      addedNodes: Element[];
      removedNodes: Element[];
    }) => void
  ): FlattenedNodesObserver;
  unobserveNodes(observerHandle: FlattenedNodesObserver): void;
}

export interface OwnerRoot {
  host?: HTMLElement;
}

/**
 * Event type for an event fired by Polymer for an element generated from a
 * dom-repeat template.
 */
export interface PolymerDomRepeatEvent<TModel = unknown> extends Event {
  model: PolymerDomRepeatEventModel<TModel>;
}

/**
 * Event type for an event fired by Polymer for an element generated from a
 * dom-repeat template.
 */
export interface PolymerDomRepeatCustomEvent<
  TModel = unknown,
  TDetail = unknown
> extends CustomEvent<TDetail> {
  model: PolymerDomRepeatEventModel<TModel>;
}

/**
 * Model containing additional information about the dom-repeat element
 * that fired an event.
 *
 * Note: This interface is valid only if both dom-repeat properties 'as' and
 * 'indexAs' have default values ('item' and 'index' correspondingly)
 */
export interface PolymerDomRepeatEventModel<T> {
  /**
   * The item corresponding to the element in the dom-repeat.
   */
  item: T;

  /**
   * The index of the element in the dom-repeat.
   */
  index: number;
  get(name: 'item'): T;
  // Typed get for item.prop_name
  get<K extends keyof T>(name: `item.${K extends string ? K : never}`): T[K];
  // Typed get for item.prop_name.nested_prop_name
  get<K1 extends keyof T, K2 extends keyof T[K1]>(
    name: `item.${K1 extends string ? K1 : never}.${K2 extends string
      ? K2
      : never}`
  ): T[K1][K2];
  // Untyped get for other cases
  get(name: string): unknown; // force get(...) as Type for nested properties

  set(name: 'item', val: T): void;
  // Typed set for item.prop_name
  set<K extends keyof T>(
    name: `item.${K extends string ? K : never}`,
    val: T[K]
  ): void;
  // Typed get for item.prop_name.nested_prop_name
  set<K1 extends keyof T, K2 extends keyof T[K1]>(
    name: `item.${K1 extends string ? K1 : never}.${K2 extends string
      ? K2
      : never}`,
    val: T[K1][K2]
  ): void;
  // Untyped set for other cases
  set(name: string, val: unknown): void;
}

/** https://highlightjs.readthedocs.io/en/latest/api.html */
export interface HighlightJSResult {
  value: string;
  top: unknown;
}

/** https://highlightjs.readthedocs.io/en/latest/api.html */
export interface HighlightJS {
  configure(options: {classPrefix: string}): void;
  getLanguage(languageName: string): unknown | undefined;
  highlight(
    languageName: string,
    code: string,
    ignore_illegals: boolean,
    continuation?: unknown
  ): HighlightJSResult;
}

export type DiffLayerListener = (
  /** 1-based inclusive */
  start: number,
  /** 1-based inclusive */
  end: number,
  side: Side
) => void;

export interface DiffLayer extends DiffLayerApi {
  addListener?(listener: DiffLayerListener): void;
  removeListener?(listener: DiffLayerListener): void;
}

export interface ChangeViewState {
  changeNum: NumericChangeId | null;
  patchRange: PatchRange | null;
  selectedFileIndex: number;
  showReplyDialog: boolean;
  diffMode: DiffViewMode | null;
  numFilesShown: number | null;
}

export interface ChangeListViewState {
  changeNum?: ChangeId;
  patchRange?: PatchRange;
  // TODO(TS): seems only one of 2 selected... is required
  selectedFileIndex?: number;
  selectedChangeIndex?: number;
  showReplyDialog?: boolean;
  diffMode?: DiffViewMode;
  numFilesShown?: number;
  scrollTop?: number;
  query?: string | null;
  offset?: number;
}

export interface DashboardViewState {
  [key: string]: number;
}

export interface ViewState {
  changeView: ChangeViewState;
  changeListView: ChangeListViewState;
  dashboardView: DashboardViewState;
}

export interface PatchSetFile {
  path: string;
  basePath?: string;
  patchNum?: PatchSetNum;
}

export interface PatchNumOnly {
  patchNum: PatchSetNum;
}

export function isPatchSetFile(
  x: PatchSetFile | PatchNumOnly
): x is PatchSetFile {
  return !!(x as PatchSetFile).path;
}

export interface FileRange {
  basePath?: string;
  path: string;
}

export function isPolymerSpliceChange<
  T,
  U extends Array<{} | null | undefined>
>(x: T | PolymerSpliceChange<U>): x is PolymerSpliceChange<U> {
  return (x as PolymerSpliceChange<U>).indexSplices !== undefined;
}

export interface FetchRequest {
  url: string;
  fetchOptions?: AuthRequestInit;
  anonymizedUrl?: string;
}

export interface FormattedReviewerUpdateInfo {
  author: AccountInfo;
  date: Timestamp;
  type: 'REVIEWER_UPDATE';
  tag: MessageTag.TAG_REVIEWER_UPDATE;
  updates: {message: string; reviewers: AccountInfo[]}[];
}

export interface EditRevisionInfo extends Partial<RevisionInfo> {
  // EditRevisionInfo has less required properties then RevisionInfo
  _number: PatchSetNum;
  basePatchNum: BasePatchSetNum;
  commit: CommitInfo;
}

export interface ParsedChangeInfo
  extends Omit<ChangeViewChangeInfo, 'reviewer_updates' | 'revisions'> {
  revisions: {[revisionId: string]: RevisionInfo | EditRevisionInfo};
  reviewer_updates?: ReviewerUpdateInfo[] | FormattedReviewerUpdateInfo[];
}
