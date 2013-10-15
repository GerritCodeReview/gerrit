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

import com.google.common.io.Files;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.analysis.util.CharArraySet;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.Version;
import org.kohsuke.args4j.Argument;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipOutputStream;

public class DocIndexer {
  private static final Version LUCENE_VERSION = Version.LUCENE_44;
  private static final String DOC_FIELD = "doc";
  private static final String URL_FIELD = "url";
  private static final String TITLE_FIELD = "title";

  @Option(name = "-z", usage = "output zip file")
  private String zipFile;

  @Option(name = "--prefix", usage = "prefix for the html filepath")
  private String prefix = "";

  @Option(name = "--in-ext", usage = "extension for input files")
  private String inExt = ".txt";

  @Option(name = "--out-ext", usage = "extension for output files")
  private String outExt = ".html";

  @Argument(usage = "input files")
  private List<String> inputFiles = new ArrayList<String>();

  private void invoke(String... parameters) throws IOException {
    CmdLineParser parser = new CmdLineParser(this);
    try {
      parser.parseArgument(parameters);
      if (inputFiles.isEmpty()) {
        throw new CmdLineException(parser, "FAILED: input file missing");
      }
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      parser.printUsage(System.err);
      System.exit(1);
      return;
    }

    File tmp = Files.createTempDir();
    NIOFSDirectory directory = new NIOFSDirectory(tmp);
    IndexWriterConfig config = new IndexWriterConfig(
        LUCENE_VERSION,
        new StandardAnalyzer(LUCENE_VERSION, CharArraySet.EMPTY_SET));
    config.setOpenMode(OpenMode.CREATE);
    IndexWriter iwriter = new IndexWriter(directory, config);
    for (String inputFile : inputFiles) {
      File file = new File(inputFile);

      BufferedReader titleReader = new BufferedReader(
          new InputStreamReader(new FileInputStream(file), "UTF-8"));
      String title = titleReader.readLine();
      if (title != null && title.startsWith("[[")) {
        // Generally the first line of the txt is the title. In a few cases the
        // first line is a "[[tag]]" and the second line is the title.
        title = titleReader.readLine();
      }
      titleReader.close();

      String outputFile = AsciiDoctor.mapInFileToOutFile(
          inputFile, inExt, outExt);
      FileReader reader = new FileReader(file);
      Document doc = new Document();
      doc.add(new TextField(DOC_FIELD, reader));
      doc.add(new StringField(
            URL_FIELD, prefix + outputFile, Field.Store.YES));
      doc.add(new TextField(TITLE_FIELD, title, Field.Store.YES));
      iwriter.addDocument(doc);
      reader.close();
    }
    iwriter.close();

    ZipOutputStream zip = new ZipOutputStream(new FileOutputStream(zipFile));
    AsciiDoctor.zipDir(tmp, "", zip);
    zip.close();
  }

  public static void main(String[] args) {
    try {
      new DocIndexer().invoke(args);
    } catch (IOException e) {
      System.err.println(e.getMessage());
      System.exit(1);
    }
  }
}
