/**
 * @license
 * Copyright 2019 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {DiffLayer as DiffLayerApi} from '../api/diff';
import {MessageTag, Side} from '../constants/constants';
import {IronA11yAnnouncer} from '@polymer/iron-a11y-announcer/iron-a11y-announcer';
import {
  AccountInfo,
  BasePatchSetNum,
  ChangeViewChangeInfo,
  CommitInfo,
  EditPatchSet,
  PatchSetNum,
  ReviewerUpdateInfo,
  RevisionInfo,
  Timestamp,
} from './common';
import {AuthRequestInit} from '../services/gr-auth/gr-auth';

export function isDefined<T>(x: T): x is NonNullable<T> {
  return x !== undefined && x !== null;
}

export interface FixIronA11yAnnouncer extends IronA11yAnnouncer {
  requestAvailability(): void;
}

export type {CoverageRange} from '../api/diff';
export {CoverageType} from '../api/diff';

export enum ErrorType {
  AUTH = 'AUTH',
  NETWORK = 'NETWORK',
  GENERIC = 'GENERIC',
}

/*
export interface OwnerRoot {
  host?: HTMLElement;
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
  _number: EditPatchSet;
  basePatchNum: BasePatchSetNum;
  commit: CommitInfo;
}

export interface ParsedChangeInfo
  extends Omit<ChangeViewChangeInfo, 'reviewer_updates' | 'revisions'> {
  revisions: {[revisionId: string]: RevisionInfo | EditRevisionInfo};
  reviewer_updates?: ReviewerUpdateInfo[] | FormattedReviewerUpdateInfo[];
}
