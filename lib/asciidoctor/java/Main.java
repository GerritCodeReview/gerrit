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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.common.io.Files;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.Version;

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

  private static final int BUFSIZ = 4096;
  private static final String DOCTYPE = "article";
  private static final String ERUBY = "erb";
  private static final Version LUCENE_VERSION = Version.LUCENE_43;

  @Option(name = "-b", usage = "set output format backend")
  private String backend = "html5";

  @Option(name = "-z", usage = "output zip file")
  private String zipFile;

  @Option(name = "--index-dir", usage = "directory to put the index")
  private String indexDir;

  @Option(name = "--prefix", usage = "prefix for the html filepath")
  private String prefix = "/Documentation/";

  @Option(name = "--in-ext", usage = "extension for input files")
  private String inExt = ".txt";

  @Option(name = "--out-ext", usage = "extension for output files")
  private String outExt = ".html";

  @Option(name = "-a", usage =
      "a list of attributes, in the form key or key=value pair")
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

  private Options createOptions(File tmpFile) {
    OptionsBuilder optionsBuilder = OptionsBuilder.options();

    optionsBuilder.backend(backend).docType(DOCTYPE).eruby(ERUBY);
    // XXX(fishywang): ideally we should just output to a string and add the
    // content into zip. But asciidoctor will actually ignore all attributes if
    // not output to a file. So we *have* to output to a file then read the
    // content of the file into zip.
    optionsBuilder.toFile(tmpFile);

    AttributesBuilder attributesBuilder = AttributesBuilder.attributes();
    attributesBuilder.attributes(getAttributes());
    optionsBuilder.attributes(attributesBuilder.get());

    return optionsBuilder.get();
  }

  private Map<String, Object> getAttributes() {
    Map<String, Object> attributeValues = new HashMap<String, Object>();

    for (String attribute : attributes) {
      int equalsIndex = attribute.indexOf('=');
      if(equalsIndex > -1) {
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
        throw new CmdLineException(parser,
            "asciidoctor: FAILED: input file missing");
      }
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      parser.printUsage(System.err);
      System.exit(1);
      return;
    }

    File index = Files.createTempDir();
    NIOFSDirectory directory = new NIOFSDirectory(index);
    IndexWriterConfig config = new IndexWriterConfig(
        LUCENE_VERSION,
        new StandardAnalyzer(LUCENE_VERSION, CharArraySet.EMPTY_SET));
    config.setOpenMode(OpenMode.CREATE);
    IndexWriter iwriter = new IndexWriter(directory, config);

    ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile));
    for (String inputFile : inputFiles) {
      File tmp = File.createTempFile("doc", ".html");
      Options options = createOptions(tmp);
      renderInput(options, inputFile);

      String outputFile = mapInFileToOutFile(inputFile);
      FileReader reader = new FileReader(tmp);
      Document doc = new Document();
      doc.add(new TextField(prefix + outputFile, reader));
      iwriter.addDocument(doc);
      reader.close();
      zipFileAndDelete(tmp, outputFile, zip);
    }
    iwriter.close();
    zipDirAndDelete(index, indexDir, zip);
    zip.close();
  }

  private void zipDirAndDelete(File dir, String dirPrefix, ZipOutputStream zip)
      throws IOException {
    String[] files = dir.list();
    for (String filename : files) {
      File file = new File(dir, filename);
      String fullname = dirPrefix + File.separator + filename;
      if (file.isDirectory()) {
        zipDirAndDelete(file, fullname, zip);
      } else {
        zipFileAndDelete(file, fullname, zip);
      }
    }
    dir.delete();
  }

  private void zipFileAndDelete(File file, String filename, ZipOutputStream zip)
      throws IOException {
    byte[] buf = new byte[BUFSIZ];
    FileInputStream input = new FileInputStream(file);
    int len;
    zip.putNextEntry(new ZipEntry(filename));
    while ((len = input.read(buf)) > 0) {
      zip.write(buf, 0, len);
    }
    input.close();
    zip.closeEntry();
    file.delete();
  }

  private void renderInput(Options options, String inputFile) {
    Asciidoctor asciidoctor = JRubyAsciidoctor.create();
    asciidoctor.renderFile(new File(inputFile), options);
  }

  public static void main(String[] args) {
    try {
      new Main().invoke(args);
    } catch (IOException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }
}
