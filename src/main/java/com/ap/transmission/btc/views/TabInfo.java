package com.ap.transmission.btc.views;

/**
 * @author Andrey Pavlenko
 */
public class TabInfo {
  private final int title;
  private final int icon;
  private final int activeIcon;
  private final int layout;


  public TabInfo(int title, int icon, int activeIcon, int layout) {
    this.title = title;
    this.icon = icon;
    this.activeIcon = activeIcon;
    this.layout = layout;
  }

  public int getTitle() {
    return title;
  }

  public int getIcon() {
    return icon;
  }

  public int getActiveIcon() {
    return activeIcon;
  }

  public int getLayout() {
    return layout;
  }
}
