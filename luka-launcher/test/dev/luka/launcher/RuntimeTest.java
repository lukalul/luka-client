package dev.luka.launcher;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/** Offline throwaway checks for Java version parsing and safe Temurin archive extraction. */
public final class RuntimeTest {
  public static void main(String[] args) throws Exception {
    check(Installer.parseJavaMajor("openjdk version \"25.0.1\" 2025-10-21") == 25, "Java 25");
    check(Installer.parseJavaMajor("java version \"1.8.0_391\"") == 8, "Java 8");
    check(Installer.parseJavaMajor("openjdk version \"21.0.4\"") == 21, "Java 21");
    check(Installer.parseJavaMajor("openjdk version \"25-ea\" 2025-09-16") == 25, "Java 25 EA");

    File temp = Files.createTempDirectory("luka-runtime-test-").toFile();
    try {
      File good = new File(temp, "good.zip");
      zip(good, "jdk-25/bin/javaw.exe", "javaw");
      File extracted = new File(temp, "extracted");
      Installer.extractRuntimeZip(good, extracted);
      check(new File(extracted, "bin/javaw.exe").isFile(), "top-level folder was stripped");

      File bad = new File(temp, "bad.zip");
      zip(bad, "jdk-25/../../escaped.txt", "escaped");
      boolean rejected = false;
      try { Installer.extractRuntimeZip(bad, new File(temp, "unsafe")); }
      catch (java.io.IOException expected) { rejected = true; }
      check(rejected, "zip-slip was rejected");
      check(!new File(temp, "escaped.txt").exists(), "zip-slip wrote no file");
      System.out.println("RuntimeTest passed");
    } finally { delete(temp); }
  }

  private static void zip(File file, String name, String contents) throws Exception {
    try (ZipOutputStream out = new ZipOutputStream(new FileOutputStream(file))) {
      out.putNextEntry(new ZipEntry(name));
      out.write(contents.getBytes(StandardCharsets.UTF_8));
      out.closeEntry();
    }
  }

  private static void check(boolean value, String message) {
    if (!value) throw new AssertionError(message);
  }

  private static void delete(File file) {
    File[] children = file.listFiles();
    if (children != null) for (File child : children) delete(child);
    file.delete();
  }
}
