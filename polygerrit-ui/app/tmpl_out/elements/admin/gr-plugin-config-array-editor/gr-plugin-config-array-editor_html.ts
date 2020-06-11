import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrPluginConfigArrayEditor} from '../../../../elements/admin/gr-plugin-config-array-editor/gr-plugin-config-array-editor';

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

export class GrPluginConfigArrayEditorCheck extends GrPluginConfigArrayEditor
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `wrapper gr-form-styles`);
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (__f(__f(__f(this.pluginOption)!.info)!.values)!.length)
    {
      {
        const el: HTMLElementTagNameMap['div'] = null!;
        useVars(el);
        el.setAttribute('class', `existingItems`);
      }
      {
        const el: HTMLElementTagNameMap['dom-repeat'] = null!;
        useVars(el);
      }
      {
        const index = 0;
        const itemsIndexAs = 0;
        useVars(index, itemsIndexAs);
        for(const item of __f(__f(this.pluginOption)!.info)!.values!)
        {
          {
            const el: HTMLElementTagNameMap['div'] = null!;
            useVars(el);
            el.setAttribute('class', `row`);
          }
          {
            const el: HTMLElementTagNameMap['span'] = null!;
            useVars(el);
          }
          setTextContent(`${item}`);

          {
            const el: HTMLElementTagNameMap['gr-button'] = null!;
            useVars(el);
            el.link = true;
            el.setAttribute('disabled', `${this.disabled}`);
            el.setAttribute('dataItem', `${item}`);
            el.addEventListener('click', e => this._handleDelete.bind(this, wrapInPolymerDomRepeatEvent(e, item))());
          }
        }
      }
    }
    {
      const el: HTMLElementTagNameMap['dom-if'] = null!;
      useVars(el);
    }
    if (!__f(__f(__f(this.pluginOption)!.info)!.values)!.length)
    {
      {
        const el: HTMLElementTagNameMap['div'] = null!;
        useVars(el);
        el.setAttribute('class', `row placeholder`);
      }
    }
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `row ${this._computeShowInputRow(this.disabled)}`);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.addEventListener('keydown', this._handleInputKeydown.bind(this));
      el.bindValue = this._newValue;
      this._newValue = convert(el.bindValue);
    }
    {
      const el: HTMLElementTagNameMap['iron-input'] = null!;
      useVars(el);
      el.setAttribute('id', `input`);
      el.addEventListener('keydown', this._handleInputKeydown.bind(this));
      el.bindValue = this._newValue;
      this._newValue = convert(el.bindValue);
    }
    {
      const el: HTMLElementTagNameMap['gr-button'] = null!;
      useVars(el);
      el.setAttribute('id', `addButton`);
      el.setAttribute('disabled', `${!__f(this._newValue)!.length}`);
      el.link = true;
      el.addEventListener('click', this._handleAddTap.bind(this));
    }
  }
}

