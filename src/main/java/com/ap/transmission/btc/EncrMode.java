package com.ap.transmission.btc;

/**
 * @author Andrey Pavlenko
 */
public enum EncrMode implements Localizable {
  Allow(R.string.encr_mode_allow),
  Prefer(R.string.encr_mode_prefer),
  Require(R.string.encr_mode_require);
  private static EncrMode[] values = values();
  private final int resId;

  private EncrMode(int resId) {this.resId = resId;}

  public static EncrMode get(int ordinal) {
    return values[ordinal];
  }

  @Override
  public int getResourceId() {
    return resId;
  }
}
