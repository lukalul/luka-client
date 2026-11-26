package dev.luka.launcher;

import java.io.File;

/**
 * Test: SEND LOGS without the window. Builds the zip from a luka folder (read only) and sends it to an API.
 *   java -cp <launcher classes> dev.luka.launcher.SendLogsRun <luka folder> <api base> <install id> [note]
 */
public final class SendLogsRun {
  public static void main(String[] args) throws Exception {
    File root = new File(args[0]);
    Settings settings = new Settings(new File(root, "launcher.properties"));
    LogReport report = new LogReport(root, settings, LauncherFrame.VERSION, "test cpu | test gpu");
    byte[] zip = report.build(args.length > 3 ? args[3] : "");
    System.out.println("zip " + zip.length + " bytes, files: " + report.included());
    String id = LogReport.send(args[1], args[2], "1.6.5", zip);
    System.out.println("report " + id);
    System.exit(0);
  }
}
