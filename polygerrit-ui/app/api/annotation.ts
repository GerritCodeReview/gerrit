/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {CoverageRange, TokenHighlightEventDetails} from './diff';
import {BasePatchSetNum, ChangeInfo, RevisionPatchSetNum} from './rest-api';

/**
 * This is the callback object that Gerrit calls once for each diff. Gerrit
 * is then responsible for styling the diff according the returned array of
 * CoverageRanges.
 */
export type CoverageProvider = (
  changeNum: number,
  path: string,
  basePatchNum?: number,
  patchNum?: number,
  change?: ChangeInfo
) => Promise<Array<CoverageRange> | undefined>;

export declare interface DiffDetails {
  change: ChangeInfo;
  basePatchNum: BasePatchSetNum;
  patchNum: RevisionPatchSetNum;
  path: string;
}

export declare type TokenHoverListener = (
  diff: DiffDetails,
  highlight?: TokenHighlightEventDetails
) => void;

export declare interface AnnotationPluginApi {
  /**
   * The specified function will be called when a gr-diff component is built,
   * and feeds the returned coverage data into the diff. Optional.
   *
   * Be sure to call this only once and only from one plugin. Multiple coverage
   * providers are not supported. A second call will just overwrite the
   * provider of the first call.
   */
  setCoverageProvider(coverageProvider: CoverageProvider): void;

  /**
   * Experimental endpoint for calling a function when a gr-diff token is
   * hovered.
   *
   * The callback receives details of the diff itself and of the highlighted
   * token.
   *
   * TODO: Replace with a more general addDiffLayer() endpoint.
   */
  addTokenHoverListener(callback: TokenHoverListener): void;
}
