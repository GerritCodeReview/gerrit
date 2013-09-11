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

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.asciidoctor.Asciidoctor;
import org.asciidoctor.AttributesBuilder;
import org.asciidoctor.Options;
import org.asciidoctor.OptionsBuilder;
import org.asciidoctor.internal.JRubyAsciidoctor;

import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class Main {

  private static final char ATTRIBUTE_SEPARATOR = '=';

  private static final String DOCTYPE = "article";
  private static final String ERUBY = "erb";

  @Option(name = "-b", usage = "set output format backend (default: html5)")
  private String backend = "html5";

  @Option(name = "-z", usage = "output zip file")
  private String zipFile;

  @Option(name = "--in-ext", usage = "extension for input files (default: .txt)")
  private String inExt = ".txt";

  @Option(name = "--out-ext", usage = "extension for output files (default: .html)")
  private String outExt = ".html";

  @Option(name = "-a", usage = "a list of attributes, in the form key or key=value pair, to set on the document")
  private List<String> attributes = new ArrayList<String>();

  @Argument(usage = "input files")
  private List<String> inputFiles = new ArrayList<String>();

  private String mapInFileToOutFile(String inFile) {
    String basename = new File(inFile).getName();
    if (basename.endsWith(inExt)) {
      basename = basename.substring(0, basename.length() - inExt.length());
    } else {
      // Strip out the last extension
      int pos = basename.lastIndexOf('.');
      if (pos > 0) {
        basename = basename.substring(0, pos);
      }
    }
    return basename + outExt;
  }

  public Options parse() {
    OptionsBuilder optionsBuilder = OptionsBuilder.options();

    optionsBuilder.backend(backend).docType(DOCTYPE).eruby(ERUBY);

    AttributesBuilder attributesBuilder = AttributesBuilder.attributes();
    attributesBuilder.attributes(getAttributes());
    optionsBuilder.attributes(attributesBuilder.get());

    return optionsBuilder.get();
  }

  private Map<String, Object> getAttributes() {
    Map<String, Object> attributeValues = new HashMap<String, Object>();

    for (String attribute : attributes) {
      int equalsIndex = attribute.indexOf(ATTRIBUTE_SEPARATOR);
      if(equalsIndex > -1) {
        String attributeName = attribute.substring(0, equalsIndex);
        String attributeValue = attribute.substring(equalsIndex+1, attribute.length());

        attributeValues.put(attributeName, attributeValue);
      } else {
        attributeValues.put(attribute, "");
      }
    }

    return attributeValues;
  }

  public void invoke(String... parameters) {
    CmdLineParser parser = new CmdLineParser(this);
    try {
      try {
        parser.parseArgument(parameters);
        if (inputFiles.isEmpty()) {
          throw new CmdLineException(parser, "asciidoctor: FAILED: input file missing");
        }
      } catch (CmdLineException e) {
        System.err.println(e.getMessage());
        parser.printUsage(System.err);
        return;
      }
      Options options = parse();
      ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile));
      for (String inputFile : inputFiles) {
        String output = renderInput(options, inputFile);

        zip.putNextEntry(new ZipEntry(mapInFileToOutFile(inputFile)));
        zip.write(output.getBytes());
        zip.closeEntry();
      }
      zip.close();
    } catch (java.io.IOException e) {
      System.err.println(e.getMessage());
    }
  }

  private String renderInput(Options options, String inputFile) {
    Asciidoctor asciidoctor = JRubyAsciidoctor.create();

    return asciidoctor.renderFile(new File(inputFile), options);
  }

  public static void main(String[] args) {
    new Main().invoke(args);
  }
}
