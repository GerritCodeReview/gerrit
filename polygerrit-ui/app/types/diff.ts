/**
 * @fileoverview Types related to diffing.
 *
 * As gr-diff is an embeddable component, many of these types are actually
 * defined in api/diff.ts. This file re-exports them and adds any
 * internal fields that Gerrit may use.
 *
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

export {
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
   * Links to the file diff in external sites as a list of DiffWebLinkInfo
   * entries.
   */
  web_links?: DiffWebLinkInfo[];

  /**
   * Links to edit the file in external sites as a list of WebLinkInfo
   * entries.
   */
  edit_web_links?: WebLinkInfo[];
}

/**
 * The DiffWebLinkInfo entity describes a link on a diff screen to an external
 * site.
 * https://gerrit-review.googlesource.com/Documentation/rest-api-changes.html#diff-web-link-info
 */
export declare interface DiffWebLinkInfo {
  /** The link name. */
  name: string;
  /** The link URL. */
  url: string;
  /** URL to the icon of the link. */
  image_url: string;
  // TODO: Are these really of type string? Not able to trigger them, but the
  // docs sound more like boolean.
  show_on_side_by_side_diff_view: string;
  show_on_unified_diff_view: string;
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
