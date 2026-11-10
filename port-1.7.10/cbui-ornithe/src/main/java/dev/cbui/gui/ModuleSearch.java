package dev.cbui.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Shared allocation-at-rebuild-time search for native and bridge cards. */
public final class ModuleSearch {
  public interface Entry {
    String searchText();

    /** The module's own name; a search matches names first and only falls back to option text when no name matches. */
    default String name() {
      return searchText();
    }

    String category();
  }

  public static <T extends Entry> List<T> filter(List<T> entries, String query, String category) {
    String needle = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
    String wanted = category == null ? "ALL" : category;
    List<T> result = new ArrayList<>();
    for (T entry : entries)
      if (("ALL".equals(wanted) || wanted.equalsIgnoreCase(entry.category()))
          && (needle.isEmpty() || entry.name().toLowerCase(Locale.ROOT).contains(needle)))
        result.add(entry);
    if (!result.isEmpty() || needle.isEmpty()) return result;
    // nothing is called that: look inside the options (names, tooltips) instead
    for (T entry : entries)
      if (("ALL".equals(wanted) || wanted.equalsIgnoreCase(entry.category()))
          && entry.searchText().toLowerCase(Locale.ROOT).contains(needle))
        result.add(entry);
    return result;
  }

  private ModuleSearch() {}
}
