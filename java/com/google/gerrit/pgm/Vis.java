package com.google.gerrit.pgm;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.grapher.graphviz.GraphvizGrapher;
import com.google.inject.grapher.graphviz.GraphvizModule;
import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.HashSet;
import java.util.Set;

public class Vis {
  public static void main(String[] args) throws IOException {
    System.out.println("Hello, world!!!");
    Passwd passwd = new Passwd();
    Injector injector = passwd.getSysInjectorOld();
    SetPasswd setPasswd = injector.getInstance(SetPasswd.class);
    Set<Key<?>> set = new HashSet<>();
    set.add(Key.get(SetPasswd.class));
    graph("/Users/dmfilippov/abc.dot", injector, set);
    System.out.println("Hello, world!!!");
  }

  private static void graph(String filename, Injector demoInjector, Set<Key<?>> root) throws IOException {
    PrintWriter out = new PrintWriter(new File(filename), "UTF-8");

    Injector injector = Guice.createInjector(new GraphvizModule());
    GraphvizGrapher grapher = injector.getInstance(GraphvizGrapher.class);
    grapher.setOut(out);
    grapher.setRankdir("TB");
    grapher.graph(demoInjector, root);
  }
}
