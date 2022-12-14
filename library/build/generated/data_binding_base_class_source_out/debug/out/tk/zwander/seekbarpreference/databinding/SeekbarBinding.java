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
import tk.zwander.seekbarpreference.SeekBarView;

public final class SeekbarBinding implements ViewBinding {
  @NonNull
  private final SeekBarView rootView;

  @NonNull
  public final SeekBarView seekbarRoot;

  private SeekbarBinding(@NonNull SeekBarView rootView, @NonNull SeekBarView seekbarRoot) {
    this.rootView = rootView;
    this.seekbarRoot = seekbarRoot;
  }

  @Override
  @NonNull
  public SeekBarView getRoot() {
    return rootView;
  }

  @NonNull
  public static SeekbarBinding inflate(@NonNull LayoutInflater inflater) {
    return inflate(inflater, null, false);
  }

  @NonNull
  public static SeekbarBinding inflate(@NonNull LayoutInflater inflater, @Nullable ViewGroup parent,
      boolean attachToParent) {
    View root = inflater.inflate(R.layout.seekbar, parent, false);
    if (attachToParent) {
      parent.addView(root);
    }
    return bind(root);
  }

  @NonNull
  public static SeekbarBinding bind(@NonNull View rootView) {
    if (rootView == null) {
      throw new NullPointerException("rootView");
    }

    SeekBarView seekbarRoot = (SeekBarView) rootView;

    return new SeekbarBinding((SeekBarView) rootView, seekbarRoot);
  }
}
