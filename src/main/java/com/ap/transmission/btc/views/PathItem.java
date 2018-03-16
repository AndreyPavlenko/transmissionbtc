package com.ap.transmission.btc.views;

import android.support.annotation.NonNull;
import android.view.View;
import android.widget.CompoundButton;

import com.ap.transmission.btc.R;
import com.ap.transmission.btc.databinding.PathViewBinding;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringTokenizer;

/**
 * @author Andrey Pavlenko
 */
public class PathItem implements Comparable<PathItem> {
  private final PathItem parent;
  private final String name;
  private final String path;
  private final int index;
  private final int level;
  private final Map<String, PathItem> children = new HashMap<>();
  private boolean visible = true;
  private boolean checked = true;
  private boolean collapsed = false;
  private PathViewBinding binding;

  public PathItem(PathItem parent, String name, String path, int index, int level) {
    this.parent = parent;
    this.name = name;
    this.path = path;
    this.index = index;
    this.level = level;
  }

  public static Map<String, PathItem> split(String... items) {
    Map<String, PathItem> roots = new HashMap<>();
    StringBuilder sb = new StringBuilder();

    for (int i = 0; i < items.length; i++) {
      int level = 0;
      String item = items[i];
      PathItem parent = null;
      Map<String, PathItem> m = roots;
      sb.setLength(0);
      if (item.startsWith("/")) item = item.substring(1);

      for (StringTokenizer st = new StringTokenizer(item, "/"); st.hasMoreTokens(); ) {
        if (sb.length() != 0) sb.append('/');
        String name = st.nextToken();
        String path = sb.append(name).toString();
        PathItem pi = m.get(path);

        if (pi == null) {
          pi = new PathItem(parent, name, path, i, level);
          m.put(path, pi);
        }

        parent = pi;
        m = pi.children;
        level++;
      }
    }

    return roots;
  }

  public static List<PathItem> ls(Map<String, PathItem> roots) {
    return ls(roots, null);
  }

  public static List<PathItem> ls(Map<String, PathItem> roots, Comparator<PathItem> cmp) {
    List<PathItem> ls = new ArrayList<>(roots.size());
    ls(roots, ls, cmp);
    return ls;
  }

  private static void ls(Map<String, PathItem> roots, List<PathItem> ls, Comparator<PathItem> cmp) {
    List<PathItem> l = new ArrayList<>(roots.values());
    Collections.sort(l, cmp);

    for (PathItem i : l) {
      ls.add(i);
      ls(i.getChildren(), ls, cmp);
    }
  }

  public PathItem getParent() {
    return parent;
  }

  public String getName() {
    return name;
  }

  public String getLabelText() {
    return isDir() ? (" " + getName()) : getName();
  }

  public String getPath() {
    return path;
  }

  public int getIndex() {
    return index;
  }

  public int getLevel() {
    return level;
  }

  public Map<String, PathItem> getChildren() {
    return children;
  }

  public boolean isDir() {
    return !getChildren().isEmpty();
  }

  public int getIcon() {
    if (isDir()) {
      return isCollapsed() ? R.drawable.expand : R.drawable.collapse;
    } else {
      return -1;
    }
  }

  public boolean isVisible() {
    return visible;
  }

  public void setVisible(boolean visible) {
    this.visible = visible;
    if (!isCollapsed()) {
      for (PathItem i : getChildren().values()) i.setVisible(visible);
    }
    resetBinding();
  }

  public boolean isCollapsed() {
    return collapsed;
  }

  public void setCollapsed(boolean collapsed) {
    this.collapsed = collapsed;
    for (PathItem i : getChildren().values()) i.setVisible(!collapsed);
    resetBinding();
  }

  public boolean isChecked() {
    return checked;
  }

  public void setChecked(boolean checked) {
    this.checked = checked;
    for (PathItem i : getChildren().values()) i.setChecked(checked);
    resetBinding();
  }

  public void onClick(@SuppressWarnings("unused") View v) {
    if (isDir()) setCollapsed(!isCollapsed());
    else setChecked(!isChecked());
  }

  public void onCheckedChanged(@SuppressWarnings("unused") CompoundButton v, boolean isChecked) {
    setChecked(isChecked);
  }

  public void setBinding(PathViewBinding binding) {
    this.binding = binding;
    binding.setItem(this);
  }

  private void resetBinding() {
    if (binding != null) {
      binding.setItem(null);
      binding.setItem(this);
    }
  }

  @Override
  public int hashCode() {
    return getPath().hashCode();
  }

  @Override
  public boolean equals(Object obj) {
    if (obj == this) {
      return true;
    } else if (obj instanceof PathItem) {
      return getPath().equals(((PathItem) obj).getPath());
    } else {
      return false;
    }
  }

  @Override
  public String toString() {
    return isDir() ? (getPath() + '/') : getPath();
  }

  @Override
  public int compareTo(@NonNull PathItem i) {
    int l1 = getLevel();
    int l2 = i.getLevel();

    if (l1 == l2) {
      boolean d1 = isDir();
      boolean d2 = i.isDir();

      if (d1 == d2) {
        return getName().compareTo(i.getName());
      } else {
        return d1 ? -1 : 1;
      }
    } else {
      return (l1 < l2) ? -1 : 1;
    }
  }
}
