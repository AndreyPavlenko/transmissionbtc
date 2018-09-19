package com.ap.transmission.btc.views;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Build.VERSION_CODES;
import android.util.AttributeSet;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnLongClickListener;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.PopupMenu.OnMenuItemClickListener;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import com.ap.transmission.btc.R;
import com.ap.transmission.btc.Utils;
import com.ap.transmission.btc.activities.ActivityBase;
import com.ap.transmission.btc.activities.ActivityResultHandler;
import com.ap.transmission.btc.activities.SelectFileActivity;
import com.ap.transmission.btc.func.Consumer;
import com.ap.transmission.btc.torrent.NoSuchTorrentException;
import com.ap.transmission.btc.torrent.Torrent;
import com.ap.transmission.btc.torrent.TorrentDir;
import com.ap.transmission.btc.torrent.TorrentFile;
import com.ap.transmission.btc.torrent.TorrentItem;
import com.ap.transmission.btc.torrent.TorrentStat;

import java.io.File;
import java.util.List;

import static android.graphics.PorterDuff.Mode.SRC_IN;
import static android.support.v4.content.ContextCompat.getColor;
import static com.ap.transmission.btc.Utils.bytesToString;
import static com.ap.transmission.btc.Utils.err;
import static com.ap.transmission.btc.Utils.getActivity;
import static com.ap.transmission.btc.Utils.toPx;
import static com.ap.transmission.btc.torrent.TorrentFs.sortByName;
import static com.ap.transmission.btc.torrent.TorrentStat.Status.STOPPED;

/**
 * @author Andrey Pavlenko
 */
public class TorrentView extends RelativeLayout
    implements OnMenuItemClickListener, OnClickListener, OnLongClickListener {
  private Torrent torrent;
  private boolean paused;
  private boolean hasError;
  private int detailsColor;
  private int progressColor;
  private byte hasMedia = -1;

  @SuppressLint("ObsoleteSdkInt")
  public TorrentView(Context context, AttributeSet attrs) {
    super(context, attrs);

    LayoutInflater i = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    if (i == null) throw new RuntimeException("Inflater is null");
    i.inflate(R.layout.torrent_view, this, true);
    setPadding(0, toPx(10), 0, 0);
    setFocusable(true);
    setClickable(true);
    setLongClickable(true);
    setOnClickListener(this);
    setOnLongClickListener(this);
    getMenuButton().setOnClickListener(this);

    if (Build.VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
      setBackground(getResources().getDrawable(R.drawable.focusable));
    }
  }

  @Override
  public boolean onKeyLongPress(int keyCode, KeyEvent event) {
    if (keyCode == KeyEvent.KEYCODE_ENTER) {
      showMenu();
      return true;
    }
    return false;
  }

  public void showMenu() {
    showMenu(getMenuButton());
  }

  private void showMenu(View v) {
    Torrent tor = torrent;
    PopupMenu popup = new PopupMenu(getContext(), v);
    MenuInflater inflater = popup.getMenuInflater();
    inflater.inflate(R.menu.torrent_menu, popup.getMenu());
    Menu m = popup.getMenu();

    try {
      boolean preloaded = tor.preloadIndex(3);

      if (!preloaded || !tor.hasFiles()) {
        m.getItem(0).setVisible(false); // Expand
        m.getItem(1).setVisible(false); // Collapse
        m.getItem(2).setVisible(false); // Play
      } else if (isExpanded()) {
        m.getItem(0).setVisible(false); // Expand
        if (tor.hasMediaFiles()) m.getItem(2).setVisible(false); // Play
      } else {
        m.getItem(1).setVisible(false); // Collapse
        if (tor.hasMediaFiles()) m.getItem(2).setVisible(false); // Play
      }
    } catch (NoSuchTorrentException ex) {
      tor.getFs().reportNoSuchTorrent(ex);
    } catch (IllegalStateException ex) {
      err(getClass().getName(), ex, "Invalid Torrent or TorrentFs");
    }

    if (isPaused()) {
      m.getItem(3).setVisible(false); // Pause
    } else {
      m.getItem(4).setVisible(false); // Resume
    }

    popup.setOnMenuItemClickListener(this);
    Utils.enableMenuIcons(popup);
    popup.show();
  }

  @Override
  public boolean onMenuItemClick(MenuItem item) {
    Torrent tor = torrent;
    if (tor == null) return false;

    try {
      switch (item.getItemId()) {
        case R.id.expand:
        case R.id.collapse:
          collapseExpand();
          break;
        case R.id.play:
          tor.play(getActivity(this), this);
          break;
        case R.id.pause:
          tor.stop();
          break;
        case R.id.resume:
          tor.start();
          break;
        case R.id.set_location:
          setLocation();
          break;
        case R.id.verify:
          tor.verify();
          break;
        case R.id.remove:
          remove(false);
          break;
        case R.id.remove_and_trash:
          remove(true);
          break;
      }

      return true;
    } catch (Exception ex) {
      err(getClass().getName(), ex, "Torrent menu action failed");
      return false;
    }
  }

  @Override
  public void onClick(View v) {
    if (v == this) collapseExpand();
    else showMenu(v);
  }

  @Override
  public boolean onLongClick(View v) {
    showMenu();
    return true;
  }

  public TextView getTitle() {
    return (TextView) getChildAt(0);
  }

  public ImageView getPlayButton() {
    return (ImageView) getChildAt(1);
  }

  public ImageView getMenuButton() {
    return (ImageView) getChildAt(2);
  }

  public TextView getDetails() {
    return (TextView) getChildAt(3);
  }

  public ProgressBar getProgress() {
    return (ProgressBar) getChildAt(4);
  }

  public LinearLayout getContent() {
    return (LinearLayout) getChildAt(5);
  }

  public void setTorrent(Torrent torrent) {
    this.torrent = torrent;
    getTitle().setText(torrent.getName());
    update();
  }

  public boolean isPaused() {
    Torrent tor = torrent;
    TorrentStat stat;
    return (tor != null) && ((stat = tor.getStat(false, 3)) != null)
        && (stat.getStatus() == STOPPED);
  }

  void update() {
    TextView details = getDetails();
    ProgressBar pb = getProgress();
    TorrentStat stat = torrent.getStat(false, 3);
    if (stat == null) return;

    StringBuilder sb = new StringBuilder(100);
    int progress = stat.getProgress();
    int dc = android.R.color.black;
    paused = hasError = false;

    switch (stat.getStatus()) {
      case STOPPED:
        trafficStat(sb, stat);
        if (progress != 100) sb.append(" - ").append(progress).append('%');
        sb.append(" - ").append(getResources().getText(R.string.paused));
        dc = android.R.color.darker_gray;
        paused = true;
        break;
      case CHECK:
        sb.append(getResources().getText(R.string.verifying_data));
        sb.append(" - ").append(progress).append('%');
        break;
      case DOWNLOAD:
        trafficStat(sb, stat);
        speedStat(sb, stat, true).append(" - ").append(progress).append('%');
        break;
      case SEED:
        trafficStat(sb, stat);
        speedStat(sb, stat, false);
        break;
      case ERROR:
        sb.append(stat.getError());
        dc = R.color.error;
        hasError = true;
        break;
    }

    if (dc != detailsColor) {
      detailsColor = dc;
      details.setTextColor(getColor(getContext(), dc));
    }

    details.setText(sb.toString());
    progressColor = setProgress(pb, progress, progressColor);
    updatePlayButton();
    updateContent();
  }

  void updatePlayButton() {
    if ((hasMedia == -1) && torrent.preloadIndex(1)) {
      try {
        if (torrent.hasFiles()) {
          if (torrent.hasMediaFiles()) {
            hasMedia = 1;
            ImageView img = getPlayButton();
            img.setVisibility(VISIBLE);
            img.setLongClickable(true);
            img.setOnClickListener(new OnClickListener() {
              @Override
              public void onClick(View v) {
                torrent.play(Utils.getActivity(v), v);
              }
            });
            img.setOnLongClickListener(new OnLongClickListener() {
              @Override
              public boolean onLongClick(View v) {
                copyUrl(torrent.getPlaylistUri());
                return true;
              }
            });
          } else {
            hasMedia = (byte) 0;
          }
        }
      } catch (NoSuchTorrentException ex) {
        torrent.getFs().reportNoSuchTorrent(ex);
      } catch (IllegalStateException ex) {
        err(getClass().getName(), ex, "Invalid Torrent or TorrentFs");
      }
    }
  }

  void updateContent() {
    LinearLayout content = getContent();
    if (content.getVisibility() != VISIBLE) return;
    int count = content.getChildCount();

    for (int i = 0; i < count; i++) {
      View v = content.getChildAt(i);
      if (v instanceof ItemView) ((ItemView) v).update();
    }
  }

  private int setProgress(ProgressBar pb, int progress, int curColor) {
    int color = hasError ? R.color.error : paused ? R.color.progress_pause :
        (progress == 100) ? R.color.progress_done : R.color.progress;

    if (color != curColor) {
      LayerDrawable ld = (LayerDrawable) pb.getProgressDrawable();
      Drawable d = ld.findDrawableByLayerId(android.R.id.progress);
      d.setColorFilter(getColor(getContext(), color), SRC_IN);
    }

    pb.setProgress(progress);
    return color;
  }

  @SuppressWarnings("UnusedReturnValue")
  private StringBuilder trafficStat(StringBuilder sb, TorrentStat stat) {
    long total = stat.getTotalLength();
    long remaining = stat.getRemainingLength();
    long uploaded = stat.getUploadedLength();
    sb.append('↓');

    if (remaining == 0) {
      bytesToString(total, sb);
    } else {
      bytesToString(total - remaining, sb).append(" of ");
      bytesToString(total, sb);
    }

    sb.append(" ↑");
    return bytesToString(uploaded, sb);
  }

  private StringBuilder speedStat(StringBuilder sb, TorrentStat stat, boolean downloading) {
    if (downloading) {
      sb.append(" - ↓").append(stat.getPeersDown()).append(" (");
      bytesToString(stat.getSpeedDown(), sb).append("/s)");
    }

    int peersUp = stat.getPeersUp();

    if (peersUp != 0) {
      if (downloading) sb.append(" ↑");
      else sb.append(" - ↑");
      sb.append(peersUp).append(" (");
      bytesToString(stat.getSpeedUp(), sb).append("/s)");
    }

    return sb;
  }

  private boolean isExpanded() {
    return getContent().getVisibility() == VISIBLE;
  }

  private void collapseExpand() {
    LinearLayout content = getContent();

    if (content.getVisibility() == VISIBLE) {
      content.setVisibility(GONE);
      return;
    }

    if (content.getChildCount() == 0) {
      content.addView(new ProgressBar(getContext()));
      torrent.ls(new Consumer<List<TorrentItem>>() {
        @Override
        public void accept(List<TorrentItem> ls) {
          try {
            setContent(getContent(), ls);
            updateContent();
          } catch (IllegalStateException ex) {
            err(TorrentView.class.getName(), ex, "Failed to update content");
          }
        }
      });
    }

    content.setVisibility(VISIBLE);
    updateContent();
  }

  private void setContent(LinearLayout content, List<TorrentItem> ls) {
    content.removeAllViews();

    if ((ls == null) || ls.isEmpty()) {
      content.setVisibility(GONE);
      return;
    }

    LayoutInflater infl = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    int margin = Utils.toPx(10);
    int padding = Utils.toPx(5);
    Context ctx = getContext();

    for (TorrentItem i : ls) {
      View v;
      if (i instanceof TorrentFile) v = new FileView(ctx, (TorrentFile) i, infl);
      else v = new DirView(ctx, (TorrentDir) i, infl, margin, padding);
      content.addView(v);
      MarginLayoutParams lp = (MarginLayoutParams) v.getLayoutParams();
      lp.setMargins(margin, 0, 0, 0);
      v.setPadding(0, padding, 0, 0);
    }
  }

  private void remove(boolean removeLocalData) {
    torrent.remove(removeLocalData);
    ((ViewGroup) getParent()).removeView(this);
  }

  private void setLocation() {
    ActivityBase a = Utils.getActivity(this);
    Intent intent = new Intent(getContext(), SelectFileActivity.class);
    intent.putExtra(SelectFileActivity.REQUEST_DIR, true);
    intent.putExtra(SelectFileActivity.REQUEST_WRITABLE, true);
    a.setActivityResultHandler(new ActivityResultHandler() {
      @Override
      public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if ((data == null) || (resultCode != SelectFileActivity.RESULT_OK)) return true;
        File dir = (File) data.getSerializableExtra(SelectFileActivity.RESULT_FILE);
        torrent.setLocation(dir);
        return true;
      }
    });
    a.startActivityForResult(intent, 1);
  }

  private void copyUrl(Uri uri) {
    if (uri == null) return;
    ClipboardManager clipboard = (ClipboardManager) getContext().getSystemService(Context.CLIPBOARD_SERVICE);
    if (clipboard == null) return;
    String url = uri.toString();
    ClipData clip = ClipData.newPlainText(url, url);
    clipboard.setPrimaryClip(clip);
    Utils.showMsg(this, R.string.msg_playlist_copied);
  }

  private abstract class ItemView<I extends TorrentItem> extends RelativeLayout
      implements OnCheckedChangeListener, OnClickListener {
    protected final I item;
    protected boolean ignoreChecked;

    @SuppressLint("ObsoleteSdkInt")
    public ItemView(Context ctx, I item, LayoutInflater infl) {
      super(ctx);
      this.item = item;
      int viewId = (item instanceof TorrentFile) ? R.layout.file_view : R.layout.dir_view;
      infl.inflate(viewId, this, true);

      TextView title = getTitle();
      title.setText(item.getName());
      setFocusable(true);
      setClickable(true);
      setOnClickListener(this);
      getCheckBox().setOnCheckedChangeListener(this);
      title.setClickable(true);
      title.setOnClickListener(this);

      if (Build.VERSION.SDK_INT >= VERSION_CODES.JELLY_BEAN) {
        setBackground(getResources().getDrawable(R.drawable.focusable));
      }
    }

    abstract void update();

    abstract CheckBox getCheckBox();

    TextView getTitle() {
      return (TextView) ((ViewGroup) getChildAt(0)).getChildAt(0);
    }


    @Override
    public void onCheckedChanged(CompoundButton cb, boolean isChecked) {
      try {
        if (ignoreChecked) return;
        if (item instanceof TorrentFile) ((TorrentFile) item).setDnd(!isChecked);
        else ((TorrentDir) item).setDnd(!isChecked);
        TorrentView.this.update();
      } catch (NoSuchTorrentException ex) {
        torrent.getFs().reportNoSuchTorrent(ex);
      } catch (IllegalStateException ex) {
        err(getClass().getName(), ex, "Invalid Torrent or TorrentFs");
      }
    }

    @Override
    public void onClick(View v) {
      CheckBox cb = getCheckBox();
      cb.setChecked(!cb.isChecked());
    }

    protected void checkDnd(boolean dnd) {
      CheckBox cb = getCheckBox();
      boolean checked = cb.isChecked();

      //noinspection DoubleNegation
      if (checked != !dnd) {
        ignoreChecked = true;
        try {
          cb.setChecked(!dnd);
        } finally {
          ignoreChecked = false;
        }
      }
    }
  }

  private final class FileView extends ItemView<TorrentFile> implements OnLongClickListener {
    private int progressColor;
    private final boolean isMedia;

    public FileView(Context ctx, TorrentFile file, LayoutInflater infl) {
      super(ctx, file, infl);
      isMedia = file.isVideo() || file.isAudio();

      if (isMedia) {
        TextView title = getTitle();
        ImageView play = getPlayButton();
        setLongClickable(true);
        setOnLongClickListener(this);
        title.setLongClickable(true);
        title.setOnLongClickListener(this);
        play.setOnClickListener(this);
        play.setLongClickable(true);
        play.setOnLongClickListener(new OnLongClickListener() {
          @Override
          public boolean onLongClick(View v) {
            copyUrl(item.getHttpUri());
            return true;
          }
        });
      }
    }

    @Override
    CheckBox getCheckBox() {
      return (CheckBox) getChildAt(2);
    }

    ImageView getPlayButton() {
      return (ImageView) getChildAt(1);
    }

    void update() {
      ProgressBar pb = (ProgressBar) getChildAt(3);
      boolean dnd = item.isDnd();
      checkDnd(dnd);
      if (dnd) getPlayButton().setVisibility(GONE);
      else if (isMedia) getPlayButton().setVisibility(VISIBLE);

      try {
        progressColor = setProgress(pb, dnd ? 0 : item.getProgress(false), progressColor);
      } catch (NoSuchTorrentException ex) {
        torrent.getFs().reportNoSuchTorrent(ex);
      } catch (IllegalStateException ex) {
        err(getClass().getName(), ex, "Invalid Torrent or TorrentFs");
      }
    }

    @Override
    public void onClick(View v) {
      if (v == getPlayButton()) play();
      else super.onClick(v);
    }

    @Override
    public boolean onLongClick(View v) {
      play();
      return true;
    }

    private void play() {
      Activity a = Utils.getActivity(this);
      item.open(a, this);
    }
  }

  private static final int strokeWidth = Utils.toPx(1);

  private final class DirView extends ItemView<TorrentDir> {
    private final int margin;
    private final Paint paint = new Paint();

    public DirView(Context ctx, final TorrentDir dir, LayoutInflater infl, int margin, int padding) {
      super(ctx, dir, infl);
      this.margin = margin;
      LinearLayout children = getChildren();
      MarginLayoutParams lp = (MarginLayoutParams) children.getLayoutParams();
      lp.setMargins(margin, padding, 0, 0);

      if (dir.hasMediaFiles()) {
        ImageView img = (ImageView) getChildAt(1);
        img.setVisibility(VISIBLE);
        img.setLongClickable(true);
        img.setOnClickListener(new OnClickListener() {
          @Override
          public void onClick(View v) {
            dir.play(Utils.getActivity(v), v);
          }
        });
        img.setOnLongClickListener(new OnLongClickListener() {
          @Override
          public boolean onLongClick(View v) {
            copyUrl(item.getPlaylistUri());
            return true;
          }
        });
      }

      for (TorrentItem i : sortByName(dir.ls(), false)) {
        View v;
        if (i instanceof TorrentFile) v = new FileView(ctx, (TorrentFile) i, infl);
        else v = new DirView(ctx, (TorrentDir) i, infl, margin, padding);
        children.addView(v);
        v.setPadding(0, padding, 0, 0);
      }
    }

    @Override
    CheckBox getCheckBox() {
      return (CheckBox) getChildAt(2);
    }

    LinearLayout getChildren() {
      return (LinearLayout) getChildAt(3);
    }

    void update() {
      LinearLayout children = getChildren();
      int count = children.getChildCount();
      boolean dnd = item.isDnd();
      checkDnd(dnd);

      for (int i = 0; i < count; i++) {
        ((ItemView) children.getChildAt(i)).update();
      }
    }

    protected void onDraw(Canvas canvas) {
      super.onDraw(canvas);
      LinearLayout children = getChildren();
      int count = children.getChildCount();
      int top = children.getTop();

      paint.setStrokeWidth(strokeWidth);
      paint.setStyle(Paint.Style.STROKE);
      paint.setColor(getResources().getColor(R.color.border));
      canvas.drawLine(0, top, children.getRight(), top, paint);
      canvas.drawLine(0, top, 0, children.getBottom(), paint);

      for (int i = 0; i < count; i++) {
        View c = children.getChildAt(i);
        int y;

        if (c instanceof DirView) {
          y = c.getTop() + ((DirView) c).getChildren().getTop();
        } else {
          y = c.getBottom() - strokeWidth;
        }

        y = top + y;
        canvas.drawLine(0, y, margin, y, paint);
      }
    }
  }
}
