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

import com.google.common.io.ByteStreams;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
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
import org.asciidoctor.SafeMode;
import org.asciidoctor.internal.JRubyAsciidoctor;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

public class AsciiDoctor {

  private static final String DOCTYPE = "article";
  private static final String ERUBY = "erb";
  private static final String REVNUMBER_NAME = "revnumber";

  @Option(name = "-b", usage = "set output format backend")
  private String backend = "html5";

  @Option(name = "-z", usage = "output zip file")
  private String zipFile;

  @Option(name = "--in-ext", usage = "extension for input files")
  private String inExt = ".txt";

  @Option(name = "--out-ext", usage = "extension for output files")
  private String outExt = ".html";

  @Option(name = "--base-dir", usage = "base directory")
  private File basedir;

  @Option(name = "--tmp", usage = "temporary output path")
  private File tmpdir;

  @Option(name = "--mktmp", usage = "create a temporary output path")
  private boolean mktmp;

  @Option(name = "-a", usage = "a list of attributes, in the form key or key=value pair")
  private List<String> attributes = new ArrayList<>();

  @Option(
    name = "--bazel",
    usage = "bazel mode: generate multiple output files instead of a single zip file"
  )
  private boolean bazel;

  @Option(name = "--revnumber-file", usage = "the file contains revnumber string")
  private File revnumberFile;

  @Argument(usage = "input files")
  private List<String> inputFiles = new ArrayList<>();

  private String revnumber;

  public static String mapInFileToOutFile(String inFile, String inExt, String outExt) {
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

  private Options createOptions(File base, File outputFile) {
    OptionsBuilder optionsBuilder = OptionsBuilder.options();

    optionsBuilder
        .backend(backend)
        .docType(DOCTYPE)
        .eruby(ERUBY)
        .safe(SafeMode.UNSAFE)
        .baseDir(base)
        .toFile(outputFile);

    AttributesBuilder attributesBuilder = AttributesBuilder.attributes();
    attributesBuilder.attributes(getAttributes());
    if (revnumber != null) {
      attributesBuilder.attribute(REVNUMBER_NAME, revnumber);
    }
    optionsBuilder.attributes(attributesBuilder.get());

    return optionsBuilder.get();
  }

  private Map<String, Object> getAttributes() {
    Map<String, Object> attributeValues = new HashMap<>();

    for (String attribute : attributes) {
      int equalsIndex = attribute.indexOf('=');
      if (equalsIndex > -1) {
        String name = attribute.substring(0, equalsIndex);
        String value = attribute.substring(equalsIndex + 1, attribute.length());

        attributeValues.put(name, value);
      } else {
        attributeValues.put(attribute, "");
      }
    }

    return attributeValues;
  }

  private void invoke(String... parameters) throws IOException {
    CmdLineParser parser = new CmdLineParser(this);
    try {
      parser.parseArgument(parameters);
      if (inputFiles.isEmpty()) {
        throw new CmdLineException(parser, "asciidoctor: FAILED: input file missing");
      }
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      parser.printUsage(System.err);
      System.exit(1);
      return;
    }

    if (revnumberFile != null) {
      try (BufferedReader reader = new BufferedReader(new FileReader(revnumberFile))) {
        revnumber = reader.readLine();
      }
    }

    if (mktmp) {
      tmpdir = Files.createTempDirectory("asciidoctor-").toFile();
    }

    if (bazel) {
      renderFiles(inputFiles, null);
    } else {
      try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(Paths.get(zipFile)))) {
        renderFiles(inputFiles, zip);

        File[] cssFiles =
            tmpdir.listFiles(
                new FilenameFilter() {
                  @Override
                  public boolean accept(File dir, String name) {
                    return name.endsWith(".css");
                  }
                });
        for (File css : cssFiles) {
          zipFile(css, css.getName(), zip);
        }
      }
    }
  }

  private void renderFiles(List<String> inputFiles, ZipOutputStream zip) throws IOException {
    Asciidoctor asciidoctor = JRubyAsciidoctor.create();
    for (String inputFile : inputFiles) {
      String outName = mapInFileToOutFile(inputFile, inExt, outExt);
      File out = bazel ? new File(outName) : new File(tmpdir, outName);
      if (!bazel) {
        out.getParentFile().mkdirs();
      }
      File input = new File(inputFile);
      Options options = createOptions(basedir != null ? basedir : input.getParentFile(), out);
      asciidoctor.renderFile(input, options);
      if (zip != null) {
        zipFile(out, outName, zip);
      }
    }
  }

  public static void zipFile(File file, String name, ZipOutputStream zip) throws IOException {
    zip.putNextEntry(new ZipEntry(name));
    try (InputStream input = Files.newInputStream(file.toPath())) {
      ByteStreams.copy(input, zip);
    }
    zip.closeEntry();
  }

  public static void main(String[] args) {
    try {
      new AsciiDoctor().invoke(args);
    } catch (IOException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }
}
