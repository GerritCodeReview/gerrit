import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrEditorView} from '../../../../elements/edit/gr-editor-view/gr-editor-view';

export interface PolymerDomRepeatEventModel<T> {
  /**
   * The item corresponding to the element in the dom-repeat.
   */
  item: T;

  /**
   * The index of the element in the dom-repeat.
   */
  index: number;
  get: (name: string) => T;
  set: (name: string, val: T) => void;
}

declare function wrapInPolymerDomRepeatEvent<T, U>(event: T, item: U): T & {model: PolymerDomRepeatEventModel<U>};
declare function setTextContent(content: unknown): void;
declare function useVars(...args: unknown[]): void;

type UnionToIntersection<T> = (
  T extends any ? (v: T) => void : never
  ) extends (v: infer K) => void
  ? K
  : never;

type AddNonDefinedProperties<T, P> = {
  [K in keyof P]: K extends keyof T ? T[K] : undefined;
};

type FlatUnion<T, TIntersect> = T extends any
  ? AddNonDefinedProperties<T, TIntersect>
  : never;

type AllUndefined<T> = {
  [P in keyof T]: undefined;
}

type UnionToAllUndefined<T> = T extends any ? AllUndefined<T> : any

type Flat<T> = FlatUnion<T, UnionToIntersection<UnionToAllUndefined<T>>>;

declare function __f<T>(obj: T): Flat<NonNullable<T>>;

declare function pc<T>(obj: T): PolymerDeepPropertyChange<T, T>;

declare function convert<T, U extends T>(obj: T): U;

export class GrEditorViewCheck extends GrEditorView
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `stickyHeader`);
    }
    {
      const el: HTMLElementTagNameMap['header'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `controlGroup`);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `separator`);
    }
    {
      const el: HTMLElementTagNameMap['gr-editable-label'] = null!;
      useVars(el);
      el.labelText = `File path`;
      el.value = this._path;
      el.placeholder = `File path...`;
      el.addEventListener('changed', this._handlePathChanged.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['span'] = null!;
      useVars(el);
      el.setAttribute('class', `controlGroup rightControls`);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `close`);
      el.link = true;
      el.addEventListener('click', this._handleCloseTap.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `save`);
      el.setAttribute('disabled', `${this._saveDisabled}`);
      el.link = true;
      el.addEventListener('click', this._handleSaveTap.bind(this));
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `publish`);
      el.link = true;
      el.addEventListener('click', this._handlePublishTap.bind(this));
      el.setAttribute('disabled', `${this._saveDisabled}`);
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `textareaWrapper`);
    }
    {
      const el: HTMLElementTagNameMap['gr-endpoint-decorator'] = null!;
      useVars(el);
      el.setAttribute('id', `editorEndpoint`);
      el.name = `editor`;
    }
    {
      const el: HTMLElementTagNameMap['gr-endpoint-param'] = null!;
      useVars(el);
      el.name = `fileContent`;
      el.value = this._newContent;
    }
    {
      const el: HTMLElementTagNameMap['gr-endpoint-param'] = null!;
      useVars(el);
      el.name = `prefs`;
      el.value = this._prefs;
    }
    {
      const el: HTMLElementTagNameMap['gr-endpoint-param'] = null!;
      useVars(el);
      el.name = `fileType`;
      el.value = this._type;
    }
    {
      const el: HTMLElementTagNameMap['gr-endpoint-param'] = null!;
      useVars(el);
      el.name = `lineNum`;
      el.value = this._lineNum;
    }
    {
      const el: HTMLElementTagNameMap['gr-default-editor'] = null!;
      useVars(el);
      el.setAttribute('id', `file`);
      el.fileContent = this._newContent;
    }
  }
}

