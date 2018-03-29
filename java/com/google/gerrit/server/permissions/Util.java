package com.google.gerrit.server.permissions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class Util {

  static String ID=">>>>>><<<<<<";

  static void dumpPermission(String perm, boolean success) {
    try {
      throw new Exception("<>$#()%B");
    } catch (Exception e) {
      List<String> traces =
          Arrays.stream(e.getStackTrace())
              .filter(s -> s.getClassName().contains("ApiImpl"))
              .map(
                  s ->
                      s.getClassName()
                              .replace("com.google.gerrit.server.api.changes.", "")
                              .replace("com.google.gerrit.server.api.projects.", "")
                              .replace("com.google.gerrit.server.api.accounts.", "")
                              .replace("com.google.gerrit.server.api.config.", "")
                              .replace("ChangeApiImpl", "/changes/")
                              .replace("RevisionApiImpl", "/changes/revisions/")
                              .replace("ProjectApiImpl", "/projects/")
                              .replace("AccountApiImpl", "/accounts/")
                              .replace("\\.","")
                          + s.getMethodName()
                          + "#"
                          + perm.replace(" ","_")
                          + "#" + success)
              .collect(Collectors.toList());

      for (String t : traces) {
        System.out.println(ID+t);
        // try {
        //   Files.write(Paths.get("/usr/local/google/home/hiesel/API.txt"),t.getBytes(), StandardOpenOption.APPEND);
        // }catch (IOException ee) {
        //   //exception handling left as an exercise for the reader
        //   ee.printStackTrace();
        // }
      }


    }
  }

  static String formatPerm(String str) {
    if (str.startsWith("label")) {
      return str.replace("label","LABEL");
    }
    return str.toUpperCase();
  }

  public  static void newApiCall(String api) {
    System.out.println(ID+"-----------" + api + "-----------");
    // try {
    //   Files.write(Paths.get("/usr/local/google/home/hiesel/API.txt"), ("-----------" + api + "-----------").getBytes(), StandardOpenOption.APPEND);
    // }catch (IOException ee) {
    //   ee.printStackTrace();
    //   //exception handling left as an exercise for the reader
    // }
  }



}
