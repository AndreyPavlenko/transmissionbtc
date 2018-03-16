package com.ap.transmission.btc.torrent;

import android.media.MediaMetadataRetriever;

import com.ap.transmission.btc.Utils;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import static java.util.concurrent.TimeUnit.HOURS;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.MINUTES;

/**
 * @author Andrey Pavlenko
 */
public class MediaInfo {
  private final String title;
  private final String album;
  private final String artist;
  private final String genre;
  private final String mimeType;
  private final String date;
  private final String duration;
  private final String resolution;

  MediaInfo(MediaMetadataRetriever r) {
    String v;
    title = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE);
    artist = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST);
    album = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM);
    genre = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_GENRE);
    mimeType = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE);

    if ((v = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DATE)) != null) {
      int idx = v.lastIndexOf('.');
      if (idx != -1) v = v.substring(0, idx);

      try {
        Date d = new SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US).parse(v);
        v = new SimpleDateFormat("yyyy-MM-dd", Locale.US).format(d);
      } catch (ParseException e) {
        Utils.warn(getClass().getName(), "Failed to parse date: %s", v);
        v = null;
      }

      date = v;
    } else {
      date = null;
    }

    if ((v = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)) != null) {
      long d = Long.parseLong(v);
      v = String.format(Locale.US, "%02d:%02d:%02d.000",
          MILLISECONDS.toHours(d),
          MILLISECONDS.toMinutes(d) - HOURS.toMinutes(MILLISECONDS.toHours(d)),
          MILLISECONDS.toSeconds(d) - MINUTES.toSeconds(MILLISECONDS.toMinutes(d)));
      duration = v;
    } else {
      duration = null;
    }

    if ((v = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_WIDTH)) != null) {
      String h = r.extractMetadata(MediaMetadataRetriever.METADATA_KEY_VIDEO_HEIGHT);
      resolution = v + 'x' + h;
    } else {
      resolution = null;
    }

    r.release();
  }

  public String getTitle() {
    return title;
  }

  public String getAlbum() {
    return album;
  }

  public String getArtist() {
    return artist;
  }

  public String getGenre() {
    return genre;
  }

  public String getMimeType() {
    return mimeType;
  }

  public String getDate() {
    return date;
  }

  public String getDuration() {
    return duration;
  }

  public String getResolution() {
    return resolution;
  }
}
