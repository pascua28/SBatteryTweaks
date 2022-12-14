// Generated by view binder compiler. Do not edit!
package tk.zwander.seekbarpreference.databinding;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewbinding.ViewBinding;
import java.lang.NullPointerException;
import java.lang.Override;
import tk.zwander.seekbarpreference.R;
import tk.zwander.seekbarpreference.SeekBarViewNew;

public final class SeekbarNewBinding implements ViewBinding {
  @NonNull
  private final SeekBarViewNew rootView;

  @NonNull
  public final SeekBarViewNew seekbarRoot;

  private SeekbarNewBinding(@NonNull SeekBarViewNew rootView, @NonNull SeekBarViewNew seekbarRoot) {
    this.rootView = rootView;
    this.seekbarRoot = seekbarRoot;
  }

  @Override
  @NonNull
  public SeekBarViewNew getRoot() {
    return rootView;
  }

  @NonNull
  public static SeekbarNewBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static SeekbarNewBinding inflate(@NonNull LayoutInflater inflater,
      @Nullable ViewGroup parent, boolean attachToParent) {
    View root = inflater.inflate(R.layout.seekbar_new, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static SeekbarNewBinding bind(@NonNull View rootView) {
    if (rootView == null) {
      throw new NullPointerException("rootView");
    }

    SeekBarViewNew seekbarRoot = (SeekBarViewNew) rootView;

    return new SeekbarNewBinding((SeekBarViewNew) rootView, seekbarRoot);
  }
}
