import java.io.File;
import java.io.FileWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  @Option(name = "-o", usage = "output file (default: based on input file path); use - to output to STDOUT")
  private String outFile;

  @Option(name = "--multi-out", usage = "generate multiple output files when there are multiple input files (default: true)")
  private boolean multiOut = true;

  @Option(name = "--in-ext", usage = "extension for input files, only used with multi-out (default: .txt)")
  private String inExt = ".txt";

  @Option(name = "--out-ext", usage = "extension for output files, only used with multi-out (default: .html)")
  private String outExt = ".html";

  @Option(name = "-a", usage = "a list of attributes, in the form key or key=value pair, to set on the document")
  private List<String> attributes = new ArrayList<String>();

  @Argument(usage = "input files")
  private List<String> parameters = new ArrayList<String>();

  public List<String> getParameters() {
    return parameters;
  }

  public String getBackend() {
    return backend;
  }

  public String getOutFile() {
    return outFile;
  }

  public String getOutFile(String inFile) {
    if (!multiOut || parameters.size() == 1) {
      return outFile;
    }
    File f = new File(outFile);
    String dir = f.getParentFile().getAbsolutePath() + File.separatorChar;
    String basename = new File(inFile).getName();
    if (basename.endsWith(inExt)) {
      basename = basename.substring(0, basename.length() - inExt.length());
    } else {
      // Strip out the last extension
      int pos = basename.lastIndexOf(".");
      if (pos > 0) {
        basename = basename.substring(0, pos);
      }
    }
    return dir + basename + outExt;
  }

  public boolean isOutFileOption() {
    return outFile != null;
  }

  public boolean isMultiOut() {
    return multiOut;
  }

  public Options parse(String inputFile) {
    OptionsBuilder optionsBuilder = OptionsBuilder.options();
    AttributesBuilder attributesBuilder = AttributesBuilder.attributes();

    optionsBuilder.backend(this.backend).docType(DOCTYPE).eruby(ERUBY);

    if(isOutFileOption()) {
      optionsBuilder.toFile(new File(getOutFile(inputFile)));
    }

    attributesBuilder.attributes(getAttributes());
    optionsBuilder.attributes(attributesBuilder.get());
    return optionsBuilder.get();

  }

  private Map<String, Object> getAttributes() {

    Map<String, Object> attributeValues = new HashMap<String, Object>();

    for (String attribute : this.attributes) {
      int equalsIndex = -1;
      if((equalsIndex = attribute.indexOf(ATTRIBUTE_SEPARATOR)) > -1) {
        extractAttributeNameAndValue(attributeValues, attribute, equalsIndex);
      } else {
        attributeValues.put(attribute, "");
      }
    }

    return attributeValues;
  }

  private void extractAttributeNameAndValue(Map<String, Object> attributeValues, String attribute, int equalsIndex) {
    String attributeName = attribute.substring(0, equalsIndex);
    String attributeValue = attribute.substring(equalsIndex+1, attribute.length());

    attributeValues.put(attributeName, attributeValue);
  }

  public void invoke(String... parameters) {

    CmdLineParser parser = new CmdLineParser(this);

    try {
      parser.parseArgument(parameters);
      if (getParameters().isEmpty()) {
        throw new CmdLineException(parser, "asciidoctor: FAILED: input file missing");
      }
    } catch (CmdLineException e) {
      System.err.println(e.getMessage());
      parser.printUsage(System.err);
      return;
    }

    List<String> inputFiles = getParameters();

    for (String inputFile : inputFiles) {
      Options options = parse(inputFile);
      String output = renderInput(options, inputFile);

      if(output != null) {
        System.out.println(output);
      }
    }

    if (isMultiOut()) {
      File file = new File(getOutFile());
      if (file.exists()) {
        file.setLastModified(System.currentTimeMillis());
      } else {
        file.getParentFile().mkdirs();
        try {
          FileWriter writer = new FileWriter(file, true);
          writer.close();
        } catch (java.io.IOException e) {
          throw new IllegalArgumentException(String.format(
                "asciidoctor: FAILED: can't touch output file \"%s\"", getOutFile()));
        }
      }
    }
  }

  private String renderInput(Options options, String inputFile) {
    Asciidoctor asciidoctor = JRubyAsciidoctor.create();

    return asciidoctor.renderFile(new File(inputFile), options);
  }

  public static void main(String args[]) {
    new Main().invoke(args);
  }

}
