/**
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {CoverageRange, Side} from './diff';
import {ChangeInfo} from './rest-api';

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

export declare interface AnnotationPluginApi {
  /**
   * The specified function will be called when a gr-diff component is built,
   * and feeds the returned coverage data into the diff. Optional.
   *
   * Be sure to call this only once and only from one plugin. Multiple coverage
   * providers are not supported. A second call will just overwrite the
   * provider of the first call.
   */
  setCoverageProvider(coverageProvider: CoverageProvider): AnnotationPluginApi;

  /**
   * For plugins notifying Gerrit about new annotations being ready to be
   * applied for a certain range. Gerrit will then re-render the relevant lines
   * of the diff and call back to the layer annotation function that was
   * registered in addLayer().
   *
   * @param path The file path whose listeners should be notified.
   * @param start The line where the update starts.
   * @param end The line where the update ends.
   * @param side The side of the update ('left' or 'right').
   */
  notify(path: string, start: number, end: number, side: Side): void;
}
