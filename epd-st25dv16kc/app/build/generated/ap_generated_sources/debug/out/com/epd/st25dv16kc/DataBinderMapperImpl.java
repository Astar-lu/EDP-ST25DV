package com.epd.st25dv16kc;

import android.util.SparseArray;
import android.util.SparseIntArray;
import android.view.View;
import androidx.databinding.DataBinderMapper;
import androidx.databinding.DataBindingComponent;
import androidx.databinding.ViewDataBinding;
import com.epd.st25dv16kc.databinding.ActivityBinaryBindingImpl;
import com.epd.st25dv16kc.databinding.ActivityCartoonBindingImpl;
import com.epd.st25dv16kc.databinding.ActivityFourGrayBindingImpl;
import com.epd.st25dv16kc.databinding.ActivityGrayScaleBindingImpl;
import com.epd.st25dv16kc.databinding.ActivityNoteBindingImpl;
import com.epd.st25dv16kc.databinding.ActivityQrCodeBindingImpl;
import java.lang.IllegalArgumentException;
import java.lang.Integer;
import java.lang.Object;
import java.lang.Override;
import java.lang.RuntimeException;
import java.lang.String;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DataBinderMapperImpl extends DataBinderMapper {
  private static final int LAYOUT_ACTIVITYBINARY = 1;

  private static final int LAYOUT_ACTIVITYCARTOON = 2;

  private static final int LAYOUT_ACTIVITYFOURGRAY = 3;

  private static final int LAYOUT_ACTIVITYGRAYSCALE = 4;

  private static final int LAYOUT_ACTIVITYNOTE = 5;

  private static final int LAYOUT_ACTIVITYQRCODE = 6;

  private static final SparseIntArray INTERNAL_LAYOUT_ID_LOOKUP = new SparseIntArray(6);

  static {
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.epd.st25dv16kc.R.layout.activity_binary, LAYOUT_ACTIVITYBINARY);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.epd.st25dv16kc.R.layout.activity_cartoon, LAYOUT_ACTIVITYCARTOON);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.epd.st25dv16kc.R.layout.activity_four_gray, LAYOUT_ACTIVITYFOURGRAY);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.epd.st25dv16kc.R.layout.activity_gray_scale, LAYOUT_ACTIVITYGRAYSCALE);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.epd.st25dv16kc.R.layout.activity_note, LAYOUT_ACTIVITYNOTE);
    INTERNAL_LAYOUT_ID_LOOKUP.put(com.epd.st25dv16kc.R.layout.activity_qr_code, LAYOUT_ACTIVITYQRCODE);
  }

  @Override
  public ViewDataBinding getDataBinder(DataBindingComponent component, View view, int layoutId) {
    int localizedLayoutId = INTERNAL_LAYOUT_ID_LOOKUP.get(layoutId);
    if(localizedLayoutId > 0) {
      final Object tag = view.getTag();
      if(tag == null) {
        throw new RuntimeException("view must have a tag");
      }
      switch(localizedLayoutId) {
        case  LAYOUT_ACTIVITYBINARY: {
          if ("layout/activity_binary_0".equals(tag)) {
            return new ActivityBinaryBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_binary is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYCARTOON: {
          if ("layout/activity_cartoon_0".equals(tag)) {
            return new ActivityCartoonBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_cartoon is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYFOURGRAY: {
          if ("layout/activity_four_gray_0".equals(tag)) {
            return new ActivityFourGrayBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_four_gray is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYGRAYSCALE: {
          if ("layout/activity_gray_scale_0".equals(tag)) {
            return new ActivityGrayScaleBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_gray_scale is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYNOTE: {
          if ("layout/activity_note_0".equals(tag)) {
            return new ActivityNoteBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_note is invalid. Received: " + tag);
        }
        case  LAYOUT_ACTIVITYQRCODE: {
          if ("layout/activity_qr_code_0".equals(tag)) {
            return new ActivityQrCodeBindingImpl(component, view);
          }
          throw new IllegalArgumentException("The tag for activity_qr_code is invalid. Received: " + tag);
        }
      }
    }
    return null;
  }

  @Override
  public ViewDataBinding getDataBinder(DataBindingComponent component, View[] views, int layoutId) {
    if(views == null || views.length == 0) {
      return null;
    }
    int localizedLayoutId = INTERNAL_LAYOUT_ID_LOOKUP.get(layoutId);
    if(localizedLayoutId > 0) {
      final Object tag = views[0].getTag();
      if(tag == null) {
        throw new RuntimeException("view must have a tag");
      }
      switch(localizedLayoutId) {
      }
    }
    return null;
  }

  @Override
  public int getLayoutId(String tag) {
    if (tag == null) {
      return 0;
    }
    Integer tmpVal = InnerLayoutIdLookup.sKeys.get(tag);
    return tmpVal == null ? 0 : tmpVal;
  }

  @Override
  public String convertBrIdToString(int localId) {
    String tmpVal = InnerBrLookup.sKeys.get(localId);
    return tmpVal;
  }

  @Override
  public List<DataBinderMapper> collectDependencies() {
    ArrayList<DataBinderMapper> result = new ArrayList<DataBinderMapper>(1);
    result.add(new androidx.databinding.library.baseAdapters.DataBinderMapperImpl());
    return result;
  }

  private static class InnerBrLookup {
    static final SparseArray<String> sKeys = new SparseArray<String>(1);

    static {
      sKeys.put(0, "_all");
    }
  }

  private static class InnerLayoutIdLookup {
    static final HashMap<String, Integer> sKeys = new HashMap<String, Integer>(6);

    static {
      sKeys.put("layout/activity_binary_0", com.epd.st25dv16kc.R.layout.activity_binary);
      sKeys.put("layout/activity_cartoon_0", com.epd.st25dv16kc.R.layout.activity_cartoon);
      sKeys.put("layout/activity_four_gray_0", com.epd.st25dv16kc.R.layout.activity_four_gray);
      sKeys.put("layout/activity_gray_scale_0", com.epd.st25dv16kc.R.layout.activity_gray_scale);
      sKeys.put("layout/activity_note_0", com.epd.st25dv16kc.R.layout.activity_note);
      sKeys.put("layout/activity_qr_code_0", com.epd.st25dv16kc.R.layout.activity_qr_code);
    }
  }
}
