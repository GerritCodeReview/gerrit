import {PolymerDeepPropertyChange} from '@polymer/polymer/interfaces';
import '@polymer/polymer/lib/elements/dom-if';
import '@polymer/polymer/lib/elements/dom-repeat';
import {GrEmailEditor} from '../../../../elements/settings/gr-email-editor/gr-email-editor';

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

export class GrEmailEditorCheck extends GrEmailEditor
{
  templateCheck()
  {
    {
      const el: HTMLElementTagNameMap['div'] = null!;
      useVars(el);
      el.setAttribute('class', `gr-form-styles`);
    }
    {
      const el: HTMLElementTagNameMap['table'] = null!;
      useVars(el);
      el.setAttribute('id', `emailTable`);
    }
    {
      const el: HTMLElementTagNameMap['thead'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tr'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `emailColumn`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
      el.setAttribute('class', `preferredHeader`);
    }
    {
      const el: HTMLElementTagNameMap['th'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['tbody'] = null!;
      useVars(el);
    }
    {
      const el: HTMLElementTagNameMap['dom-repeat'] = null!;
      useVars(el);
    }
    {
      const index = 0;
      const itemsIndexAs = 0;
      useVars(index, itemsIndexAs);
      for(const item of this._emails!)
      {
        {
          const el: HTMLElementTagNameMap['tr'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `emailColumn`);
        }
        setTextContent(`${__f(item)!.email}`);

        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
          el.setAttribute('class', `preferredControl`);
          el.addEventListener('click', e => this._handlePreferredControlClick.bind(this, wrapInPolymerDomRepeatEvent(e, item))());
        }
        {
          const el: HTMLElementTagNameMap['iron-input'] = null!;
          useVars(el);
          el.setAttribute('class', `preferredRadio`);
          el.addEventListener('change', e => this._handlePreferredChange.bind(this, wrapInPolymerDomRepeatEvent(e, item))());
          el.bindValue = __f(item)!.email;
          el.setAttribute('checked', `${__f(item)!.preferred}`);
        }
        {
          const el: HTMLElementTagNameMap['input'] = null!;
          useVars(el);
          el.setAttribute('class', `preferredRadio`);
          el.addEventListener('change', e => this._handlePreferredChange.bind(this, wrapInPolymerDomRepeatEvent(e, item))());
          el.setAttribute('checked', `${__f(item)!.preferred}`);
        }
        {
          const el: HTMLElementTagNameMap['td'] = null!;
          useVars(el);
        }
        {
          const el: HTMLElementTagNameMap['gr-button'] = null!;
          useVars(el);
          el.setAttribute('dataIndex', `${index}`);
          el.addEventListener('click', e => this._handleDeleteButton.bind(this, wrapInPolymerDomRepeatEvent(e, item))());
          el.disabled = this._checkPreferred(__f(item)!.preferred);
          el.setAttribute('class', `remove-button`);
        }
      }
    }
  }
}

