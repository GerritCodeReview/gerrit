// Copyright (C) 2013 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package net.codemirror.mode;

import com.google.gwt.core.client.GWT;
import com.google.gwt.resources.client.ClientBundle;
import com.google.gwt.resources.client.DataResource;
import com.google.gwt.resources.client.DataResource.DoNotEmbed;

public interface Modes extends ClientBundle {
  Modes I = GWT.create(Modes.class);

  @Source("apl.js")
  @DoNotEmbed
  DataResource apl();

  @Source("asciiarmor.js")
  @DoNotEmbed
  DataResource asciiarmor();

  @Source("asn.1.js")
  @DoNotEmbed
  DataResource asn_1();

  @Source("asterisk.js")
  @DoNotEmbed
  DataResource asterisk();

  @Source("brainfuck.js")
  @DoNotEmbed
  DataResource brainfuck();

  @Source("clike.js")
  @DoNotEmbed
  DataResource clike();

  @Source("clojure.js")
  @DoNotEmbed
  DataResource clojure();

  @Source("cmake.js")
  @DoNotEmbed
  DataResource cmake();

  @Source("cobol.js")
  @DoNotEmbed
  DataResource cobol();

  @Source("coffeescript.js")
  @DoNotEmbed
  DataResource coffeescript();

  @Source("commonlisp.js")
  @DoNotEmbed
  DataResource commonlisp();

  @Source("crystal.js")
  @DoNotEmbed
  DataResource crystal();

  @Source("css.js")
  @DoNotEmbed
  DataResource css();

  @Source("cypher.js")
  @DoNotEmbed
  DataResource cypher();

  @Source("d.js")
  @DoNotEmbed
  DataResource d();

  @Source("dart.js")
  @DoNotEmbed
  DataResource dart();

  @Source("diff.js")
  @DoNotEmbed
  DataResource diff();

  @Source("django.js")
  @DoNotEmbed
  DataResource django();

  @Source("dockerfile.js")
  @DoNotEmbed
  DataResource dockerfile();

  @Source("dtd.js")
  @DoNotEmbed
  DataResource dtd();

  @Source("dylan.js")
  @DoNotEmbed
  DataResource dylan();

  @Source("ebnf.js")
  @DoNotEmbed
  DataResource ebnf();

  @Source("ecl.js")
  @DoNotEmbed
  DataResource ecl();

  @Source("eiffel.js")
  @DoNotEmbed
  DataResource eiffel();

  @Source("elm.js")
  @DoNotEmbed
  DataResource elm();

  @Source("erlang.js")
  @DoNotEmbed
  DataResource erlang();

  @Source("factor.js")
  @DoNotEmbed
  DataResource factor();

  @Source("fcl.js")
  @DoNotEmbed
  DataResource fcl();

  @Source("forth.js")
  @DoNotEmbed
  DataResource forth();

  @Source("fortran.js")
  @DoNotEmbed
  DataResource fortran();

  @Source("gas.js")
  @DoNotEmbed
  DataResource gas();

  @Source("gerrit/commit.js")
  @DoNotEmbed
  DataResource gerrit_commit();

  @Source("gfm.js")
  @DoNotEmbed
  DataResource gfm();

  @Source("gherkin.js")
  @DoNotEmbed
  DataResource gherkin();

  @Source("go.js")
  @DoNotEmbed
  DataResource go();

  @Source("groovy.js")
  @DoNotEmbed
  DataResource groovy();

  @Source("haml.js")
  @DoNotEmbed
  DataResource haml();

  @Source("handlebars.js")
  @DoNotEmbed
  DataResource handlebars();

  @Source("haskell-literate.js")
  @DoNotEmbed
  DataResource haskell_literate();

  @Source("haskell.js")
  @DoNotEmbed
  DataResource haskell();

  @Source("haxe.js")
  @DoNotEmbed
  DataResource haxe();

  @Source("htmlembedded.js")
  @DoNotEmbed
  DataResource htmlembedded();

  @Source("htmlmixed.js")
  @DoNotEmbed
  DataResource htmlmixed();

  @Source("http.js")
  @DoNotEmbed
  DataResource http();

  @Source("idl.js")
  @DoNotEmbed
  DataResource idl();

  @Source("javascript.js")
  @DoNotEmbed
  DataResource javascript();

  @Source("jinja2.js")
  @DoNotEmbed
  DataResource jinja2();

  @Source("jsx.js")
  @DoNotEmbed
  DataResource jsx();

  @Source("julia.js")
  @DoNotEmbed
  DataResource julia();

  @Source("livescript.js")
  @DoNotEmbed
  DataResource livescript();

  @Source("lua.js")
  @DoNotEmbed
  DataResource lua();

  @Source("markdown.js")
  @DoNotEmbed
  DataResource markdown();

  @Source("mathematica.js")
  @DoNotEmbed
  DataResource mathematica();

  @Source("mbox.js")
  @DoNotEmbed
  DataResource mbox();

  @Source("mirc.js")
  @DoNotEmbed
  DataResource mirc();

  @Source("mllike.js")
  @DoNotEmbed
  DataResource mllike();

  @Source("modelica.js")
  @DoNotEmbed
  DataResource modelica();

  @Source("mscgen.js")
  @DoNotEmbed
  DataResource mscgen();

  @Source("mumps.js")
  @DoNotEmbed
  DataResource mumps();

  @Source("nginx.js")
  @DoNotEmbed
  DataResource nginx();

  @Source("nsis.js")
  @DoNotEmbed
  DataResource nsis();

  @Source("ntriples.js")
  @DoNotEmbed
  DataResource ntriples();

  @Source("octave.js")
  @DoNotEmbed
  DataResource octave();

  @Source("oz.js")
  @DoNotEmbed
  DataResource oz();

  @Source("pascal.js")
  @DoNotEmbed
  DataResource pascal();

  @Source("pegjs.js")
  @DoNotEmbed
  DataResource pegjs();

  @Source("perl.js")
  @DoNotEmbed
  DataResource perl();

  @Source("php.js")
  @DoNotEmbed
  DataResource php();

  @Source("pig.js")
  @DoNotEmbed
  DataResource pig();

  @Source("powershell.js")
  @DoNotEmbed
  DataResource powershell();

  @Source("properties.js")
  @DoNotEmbed
  DataResource properties();

  @Source("protobuf.js")
  @DoNotEmbed
  DataResource protobuf();

  @Source("pug.js")
  @DoNotEmbed
  DataResource pug();

  @Source("puppet.js")
  @DoNotEmbed
  DataResource puppet();

  @Source("python.js")
  @DoNotEmbed
  DataResource python();

  @Source("q.js")
  @DoNotEmbed
  DataResource q();

  @Source("r.js")
  @DoNotEmbed
  DataResource r();

  @Source("rpm.js")
  @DoNotEmbed
  DataResource rpm();

  @Source("rst.js")
  @DoNotEmbed
  DataResource rst();

  @Source("ruby.js")
  @DoNotEmbed
  DataResource ruby();

  @Source("rust.js")
  @DoNotEmbed
  DataResource rust();

  @Source("sas.js")
  @DoNotEmbed
  DataResource sas();

  @Source("sass.js")
  @DoNotEmbed
  DataResource sass();

  @Source("scheme.js")
  @DoNotEmbed
  DataResource scheme();

  @Source("shell.js")
  @DoNotEmbed
  DataResource shell();

  @Source("sieve.js")
  @DoNotEmbed
  DataResource sieve();

  @Source("slim.js")
  @DoNotEmbed
  DataResource slim();

  @Source("smalltalk.js")
  @DoNotEmbed
  DataResource smalltalk();

  @Source("smarty.js")
  @DoNotEmbed
  DataResource smarty();

  @Source("solr.js")
  @DoNotEmbed
  DataResource solr();

  @Source("soy.js")
  @DoNotEmbed
  DataResource soy();

  @Source("sparql.js")
  @DoNotEmbed
  DataResource sparql();

  @Source("spreadsheet.js")
  @DoNotEmbed
  DataResource spreadsheet();

  @Source("sql.js")
  @DoNotEmbed
  DataResource sql();

  @Source("stex.js")
  @DoNotEmbed
  DataResource stex();

  @Source("stylus.js")
  @DoNotEmbed
  DataResource stylus();

  @Source("swift.js")
  @DoNotEmbed
  DataResource swift();

  @Source("tcl.js")
  @DoNotEmbed
  DataResource tcl();

  @Source("textile.js")
  @DoNotEmbed
  DataResource textile();

  @Source("tiddlywiki.js")
  @DoNotEmbed
  DataResource tiddlywiki();

  @Source("tiki.js")
  @DoNotEmbed
  DataResource tiki();

  @Source("toml.js")
  @DoNotEmbed
  DataResource toml();

  @Source("tornado.js")
  @DoNotEmbed
  DataResource tornado();

  @Source("troff.js")
  @DoNotEmbed
  DataResource troff();

  @Source("ttcn-cfg.js")
  @DoNotEmbed
  DataResource ttcn_cfg();

  @Source("ttcn.js")
  @DoNotEmbed
  DataResource ttcn();

  @Source("turtle.js")
  @DoNotEmbed
  DataResource turtle();

  @Source("twig.js")
  @DoNotEmbed
  DataResource twig();

  @Source("vb.js")
  @DoNotEmbed
  DataResource vb();

  @Source("vbscript.js")
  @DoNotEmbed
  DataResource vbscript();

  @Source("velocity.js")
  @DoNotEmbed
  DataResource velocity();

  @Source("verilog.js")
  @DoNotEmbed
  DataResource verilog();

  @Source("vhdl.js")
  @DoNotEmbed
  DataResource vhdl();

  @Source("vue.js")
  @DoNotEmbed
  DataResource vue();

  @Source("webidl.js")
  @DoNotEmbed
  DataResource webidl();

  @Source("xml.js")
  @DoNotEmbed
  DataResource xml();

  @Source("xquery.js")
  @DoNotEmbed
  DataResource xquery();

  @Source("yacas.js")
  @DoNotEmbed
  DataResource yacas();

  @Source("yaml-frontmatter.js")
  @DoNotEmbed
  DataResource yaml_frontmatter();

  @Source("yaml.js")
  @DoNotEmbed
  DataResource yaml();

  @Source("z80.js")
  @DoNotEmbed
  DataResource z80();

  // When adding a resource, update static initializer in ModeInfo.
}
