package com.ap.transmission.btc.activities;

import android.Manifest.permission;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.databinding.DataBindingUtil;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.design.widget.TabLayout;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.widget.EditText;

import com.ap.transmission.btc.BindingHelper;
import com.ap.transmission.btc.Prefs;
import com.ap.transmission.btc.R;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.databinding.MainBinding;
import com.ap.transmission.btc.views.PageFragment;
import com.ap.transmission.btc.views.TabInfo;
import com.ap.transmission.btc.views.TorrentsList;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author Andrey Pavlenko
 */
public class MainActivity extends ActivityBase {
  private static final int GRANT_PERM = 10;
  private static final int ADD_FILE = 11;
  private static final TabInfo[] tabs;
  private TabLayout tabLayout;
  private BindingHelper bindingHelper;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Prefs prefs = getPrefs();
    MainBinding b = DataBindingUtil.setContentView(this, R.layout.main);
    final ViewPager p = findViewById(R.id.pager);
    tabLayout = findViewById(R.id.tabs);
    bindingHelper = new BindingHelper(this, b);
    tabLayout.addTab(tabLayout.newTab().setIcon(tabs[0].getActiveIcon()));
    setTitle(tabs[0].getTitle());

    for (int i = 1; i < tabs.length; i++) {
      tabLayout.addTab(tabLayout.newTab().setIcon(tabs[i].getIcon()));
    }

    b.setH(bindingHelper);
    b.setP(prefs);
    p.setAdapter(new PageFragment.Adapter(getSupportFragmentManager(), tabs));
    p.addOnPageChangeListener(new TabLayout.TabLayoutOnPageChangeListener(tabLayout));
    tabLayout.setTabGravity(TabLayout.GRAVITY_FILL);
    tabLayout.addOnTabSelectedListener(new TabLayout.OnTabSelectedListener() {

      @Override
      public void onTabSelected(TabLayout.Tab tab) {
        int pos = tab.getPosition();
        p.setCurrentItem(pos);
        tab.setIcon(tabs[pos].getActiveIcon());
        setTitle(tabs[pos].getTitle());

        if (!Utils.isBasic() && (tab.getPosition() == 0)) {
          TorrentsList l = findViewById(R.id.torrents_list);
          if (l != null) l.setActive(true);
        }
      }

      @Override
      public void onTabUnselected(TabLayout.Tab tab) {
        tab.setIcon(tabs[tab.getPosition()].getIcon());

        if (!Utils.isBasic() && (tab.getPosition() == 0)) {
          TorrentsList l = findViewById(R.id.torrents_list);
          if (l != null) l.setActive(false);
        }
      }

      @Override
      public void onTabReselected(TabLayout.Tab tab) {}
    });

    List<String> perms = Arrays.asList(permission.INTERNET, permission.ACCESS_NETWORK_STATE,
        permission.ACCESS_WIFI_STATE, permission.WRITE_EXTERNAL_STORAGE, permission.WAKE_LOCK);

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
      // In Android 8+ these permissions are required to get the WiFi SSID.
      List<String> l = new ArrayList<>(perms.size() + 2);
      l.addAll(perms);
      l.add(permission.ACCESS_COARSE_LOCATION);
      l.add(permission.ACCESS_FINE_LOCATION);
      perms = l;
    }

    checkPermission(perms.toArray(new String[perms.size()]));
    findViewById(R.id.button_start_stop).requestFocus();
  }

  @Override
  protected void onStop() {
    super.onStop();
    if (!Utils.isBasic() && (tabLayout.getSelectedTabPosition() == 0)) {
      TorrentsList l = findViewById(R.id.torrents_list);
      if (l != null) l.setActive(false);
    }
  }

  @Override
  protected void onStart() {
    super.onStart();
    if (!Utils.isBasic() && (tabLayout.getSelectedTabPosition() == 0)) {
      TorrentsList l = findViewById(R.id.torrents_list);
      if (l != null) l.setActive(true);
    }
  }

  @Override
  public boolean onOptionsItemSelected(final MenuItem item) {
    switch (item.getItemId()) {
      case R.id.add_file:
        addFile();
        return true;
      case R.id.add_link:
        addLink();
        return true;
      case R.id.suspend:
        item.setEnabled(false);
        bindingHelper.suspend(true, new Runnable() {
          @Override
          public void run() {
            item.setEnabled(true);
          }
        });
        return true;
      case R.id.resume:
        item.setEnabled(false);
        bindingHelper.suspend(false, new Runnable() {
          @Override
          public void run() {
            item.setEnabled(true);
          }
        });
        return true;
      case R.id.stop:
      case R.id.start:
        bindingHelper.startStopService(findViewById(R.id.button_start_stop), findViewById(R.id.button_web_ui));
        return true;
    }

    return false;
  }

  private void addFile() {
    Intent intent = new Intent(getApplicationContext(), SelectFileActivity.class);
    intent.putExtra(SelectFileActivity.REQUEST_FILE, true);
    intent.putExtra(SelectFileActivity.REQUEST_PATTERN, ".+\\.torrent");
    setActivityResultHandler(new ActivityResultHandler() {
      @Override
      public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (resultCode != SelectFileActivity.RESULT_OK) return true;
        File f = (File) data.getSerializableExtra(SelectFileActivity.RESULT_FILE);
        Intent intent = new Intent(getApplicationContext(), DownloadTorrentActivity.class);
        intent.setData(Uri.fromFile(f));
        startActivity(intent);
        return true;
      }
    });
    startActivityForResult(intent, ADD_FILE);
  }

  private void addLink() {
    AlertDialog.Builder builder = new AlertDialog.Builder(this);
    final EditText input = new EditText(this);
    builder.setTitle(R.string.torrent_link);
    builder.setView(input);

    builder.setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        String text = input.getText().toString().trim();

        if (text.isEmpty()) {
          Utils.showMsg(input, R.string.msg_enter_link);
          addLink();
        } else {
          Intent intent = new Intent(getApplicationContext(), DownloadTorrentActivity.class);
          intent.setData(Uri.parse(text));
          startActivity(intent);
        }
      }
    });
    builder.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
      @Override
      public void onClick(DialogInterface dialog, int which) {
        dialog.cancel();
      }
    });
    builder.show();
  }

  @Override
  public boolean onCreateOptionsMenu(Menu menu) {
    MenuInflater inflater = getMenuInflater();
    inflater.inflate(R.menu.main_menu, menu);
    return true;
  }

  @Override
  public boolean onMenuOpened(int featureId, Menu menu) {
    prepareMenu(menu);
    return true;
  }

  @Override
  public boolean onPrepareOptionsMenu(Menu menu) {
    prepareMenu(menu.getItem(0).getSubMenu());
    return true;
  }

  private void prepareMenu(Menu menu) {
    if (!bindingHelper.isServiceRunning()) {
      menu.getItem(0).setVisible(false); // Add file
      menu.getItem(1).setVisible(false); // Add link
      menu.getItem(2).setVisible(false); // Suspend
      menu.getItem(3).setVisible(false); // Resume
      menu.getItem(4).setVisible(false); // Stop
      menu.getItem(5).setVisible(true); // Start
      menu.getItem(5).setEnabled(!bindingHelper.isServiceStarting());
    } else {
      if (bindingHelper.isSuspended()) {
        menu.getItem(2).setVisible(false); // Suspend
        menu.getItem(3).setVisible(true); // Resume
      } else {
        menu.getItem(2).setVisible(true); // Suspend
        menu.getItem(3).setVisible(false); // Resume
      }

      menu.getItem(0).setVisible(true); // Add file
      menu.getItem(1).setVisible(true); // Add link
      menu.getItem(4).setVisible(true); // Stop
      menu.getItem(4).setEnabled(!bindingHelper.isServiceStarting());
      menu.getItem(5).setVisible(false); // Start
    }
  }

  @Override
  public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
    if (requestCode == GRANT_PERM) return;
    super.onRequestPermissionsResult(requestCode, permissions, grantResults);
  }

  private void checkPermission(String... perms) {
    for (String perm : perms) {
      if (ContextCompat.checkSelfPermission(this, perm) != PackageManager.PERMISSION_GRANTED) {
        ActivityCompat.requestPermissions(this, perms, GRANT_PERM);
      }
    }
  }

  static {
    if (Utils.isBasic()) {
      tabs = new TabInfo[]{
          new TabInfo(R.string.title_settings, R.drawable.settings, R.drawable.settings_active, R.layout.settings),
          new TabInfo(R.string.title_about, R.drawable.about, R.drawable.about_active, R.layout.about),
      };
    } else {
      tabs = new TabInfo[]{
          new TabInfo(R.string.title_downloads, R.drawable.downloads, R.drawable.downloads_active, R.layout.downloads),
          new TabInfo(R.string.title_settings, R.drawable.settings, R.drawable.settings_active, R.layout.settings),
          new TabInfo(R.string.title_watch_dirs, R.drawable.watch, R.drawable.watch_active, R.layout.watch_dirs),
          new TabInfo(R.string.title_proxy, R.drawable.proxy, R.drawable.proxy_active, R.layout.proxy),
          // TODO: implement
          // new TabInfo(R.string.title_rss, R.drawable.rss, R.drawable.rss_active, R.layout.rss),
          new TabInfo(R.string.title_about, R.drawable.about, R.drawable.about_active, R.layout.about),
      };
    }
  }
}
