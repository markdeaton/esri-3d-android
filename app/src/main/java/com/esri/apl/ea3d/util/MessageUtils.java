package com.esri.apl.ea3d.util;

import android.content.Context;
import android.support.v7.app.AlertDialog;
import android.view.Gravity;
import android.widget.Toast;

import static android.widget.Toast.LENGTH_LONG;

public class MessageUtils {
  public static void showToast(Context ctx, final String message, final int length) {
    showToast(ctx, message, Gravity.CENTER_VERTICAL, length);
  }
  /** Show toast with long duration and centered vertically */
  public static void showToast(Context ctx, String message) {
    showToast(ctx, message, LENGTH_LONG);
  }
  public static void showToast(Context ctx, final String message, final int gravity, final int length) {
    Toast toast = Toast.makeText(ctx, message, length);
    toast.setGravity(gravity, 0, 0);
    toast.show();
  }

  /** Show a simple dialog with a string message and a single "OK" button */
  public static void showErrorDialog(Context ctx, String message) {
    new AlertDialog.Builder(ctx)
      .setTitle("Error")
      .setMessage(message)
      .setPositiveButton(android.R.string.ok, null)
      .show();
  }
}
