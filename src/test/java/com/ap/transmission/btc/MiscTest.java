package com.ap.transmission.btc;

import com.ap.transmission.btc.views.PathItem;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class MiscTest {
  @Test
  public void testPathItem() {
    String[] ls = new String[]{
        "/a/b/c/d",
        "a/b/c/e",
        "c",
        "a/c",
        "a/f",
        "e",
        "b/c",
        "b/d/",
    };
    String sorted = "a/\n" +
        "a/b/\n" +
        "a/b/c/\n" +
        "a/b/c/d\n" +
        "a/b/c/e\n" +
        "a/c\n" +
        "a/f\n" +
        "b/\n" +
        "b/c\n" +
        "b/d\n" +
        "c\n" +
        "e\n";

    Map<String, PathItem> roots = PathItem.split(ls);
    ByteArrayOutputStream baos = new ByteArrayOutputStream();
    PrintStream out = new PrintStream(baos);

    print(roots, out);
    out.flush();
    assertEquals(sorted, baos.toString());

    baos.reset();
    for (PathItem i : PathItem.ls(roots)) {
      out.print(i);
      out.print('\n');
    }
    out.flush();
    assertEquals(sorted, baos.toString());
  }

  private static void print(Map<String, PathItem> items, PrintStream out) {
    List<PathItem> l = new ArrayList<>(items.values());
    Collections.sort(l);

    for (PathItem i : l) {
      out.print(i);
      out.print('\n');
      print(i.getChildren(), out);
    }
  }

  @Test
  public void testNaturalOrderComparator() {
    NaturalOrderComparator cmp = new NaturalOrderComparator();
    String[] unsorted = new String[]{
        "a", "b", "abc", "zxx", "aaa1", "aaa10a", "aaa01", "aaa2", "aaa3", "aaa2aa", "aaa11",
        "aaa11382193812093", "aaa12382193812093", "aaa" + Long.MAX_VALUE
    };
    String[] sorted = new String[]{
        "a", "aaa1", "aaa01", "aaa2", "aaa2aa", "aaa3", "aaa10a", "aaa11", "aaa11382193812093",
        "aaa12382193812093", "aaa9223372036854775807", "abc", "b", "zxx"
    };

    Arrays.sort(unsorted, cmp);
    // for (String s : unsorted) System.out.print("\"" + s + "\", ");
    assertTrue(Arrays.equals(unsorted, sorted));
  }
}