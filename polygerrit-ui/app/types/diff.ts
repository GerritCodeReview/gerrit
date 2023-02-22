/**
 * @fileoverview Types related to diffing.
 *
 * As gr-diff is an embeddable component, many of these types are actually
 * defined in api/diff.ts. This file re-exports them and adds any
 * internal fields that Gerrit may use.
 *
 * @license
 * Copyright 2020 Google LLC
 * SPDX-License-Identifier: Apache-2.0
 */
import {WebLinkInfo} from '../api/rest-api';
import {
  ChangeType,
  DiffContent as DiffContentApi,
  DiffFileMetaInfo as DiffFileMetaInfoApi,
  DiffInfo as DiffInfoApi,
  DiffIntralineInfo,
  DiffResponsiveMode,
  DiffPreferencesInfo as DiffPreferenceInfoApi,
  IgnoreWhitespaceType,
  MarkLength,
  MoveDetails,
  SkipLength,
} from '../api/diff';

export type {
  ChangeType,
  DiffIntralineInfo,
  DiffResponsiveMode,
  IgnoreWhitespaceType,
  MarkLength,
  MoveDetails,
  SkipLength,
  WebLinkInfo,
};

export interface DiffInfo extends DiffInfoApi {
  /** Meta information about the file on side A as a DiffFileMetaInfo entity. */
  meta_a?: DiffFileMetaInfo;
  /** Meta information about the file on side B as a DiffFileMetaInfo entity. */
  meta_b?: DiffFileMetaInfo;

  /**
   * Links to the file diff in external sites as a list of WebLinkInfo
   * entries.
   */
  web_links?: WebLinkInfo[];

  /**
   * Links to edit the file in external sites as a list of WebLinkInfo
   * entries.
   */
  edit_web_links?: WebLinkInfo[];
}

export interface DiffFileMetaInfo extends DiffFileMetaInfoApi {
  /** Links to the file in external sites as a list of WebLinkInfo entries. */
  web_links?: WebLinkInfo[];
}

export interface DiffContent extends DiffContentApi {
  // TODO: Undocumented, but used in code.
  keyLocation?: boolean;
}

export interface DiffPreferencesInfo extends DiffPreferenceInfoApi {
  expand_all_comments?: boolean;
  cursor_blink_rate?: number;
  manual_review?: boolean;
  retain_header?: boolean;
  skip_deleted?: boolean;
  hide_top_menu?: boolean;
  hide_line_numbers?: boolean;
  hide_empty_pane?: boolean;
  match_brackets?: boolean;
}

export declare type DiffPreferencesInfoKey = keyof DiffPreferencesInfo;
