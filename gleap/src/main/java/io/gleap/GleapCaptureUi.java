package io.gleap;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/**
 * The look of the SDK's capture UI (bar and recording preview): a dark card with the widget's
 * color for the main action, readable on top of any app.
 */
final class GleapCaptureUi {
    static final int CARD_COLOR = 0xF2141821;
    static final int STOP_COLOR = 0xFFE5484D;
    static final int TEXT_COLOR = 0xFFFFFFFF;
    static final int SECONDARY_TEXT_COLOR = 0xE6FFFFFF;
    private static final int DEFAULT_PRIMARY = 0xFF485BFF;

    private GleapCaptureUi() {
    }

    static GradientDrawable card(Context context) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(CARD_COLOR);
        background.setCornerRadius(dp(context, 18));
        return background;
    }

    static TextView text(Context context, String value, int sizeSp, int color, boolean bold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        if (bold) {
            view.setTypeface(view.getTypeface(), Typeface.BOLD);
        }
        return view;
    }

    /**
     * A button with at least a 44 x 64 dp touch target, announced as a button.
     *
     * @param color its background; transparent for a text button
     */
    static TextView button(Context context, String label, int color, int textColor) {
        TextView button = text(context, label, 14, textColor, true);
        button.setGravity(Gravity.CENTER);
        button.setMinHeight(dp(context, 44));
        button.setMinWidth(dp(context, 64));
        button.setPadding(dp(context, 16), dp(context, 8), dp(context, 16), dp(context, 8));
        button.setMaxLines(2);
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(context, 22));
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(dp(context, 22));
        button.setBackground(new RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), shape, mask));
        button.setClickable(true);
        button.setFocusable(true);
        // Taps are ignored while another app's window covers the button.
        button.setFilterTouchesWhenObscured(true);
        ViewCompat.setAccessibilityDelegate(button, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        });
        return button;
    }

    /**
     * Enables or disables the buttons in {@code view}.
     */
    static void setButtonsEnabled(View view, boolean enabled) {
        if (view.isClickable() && view instanceof TextView) {
            view.setEnabled(enabled);
            view.setAlpha(enabled ? 1f : 0.5f);
        }
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                setButtonsEnabled(group.getChildAt(i), enabled);
            }
        }
    }

    // The widget's color (the dashboard's, with the color scheme applied).
    static int primaryColor() {
        try {
            return Color.parseColor(GleapConfig.getInstance().getColor());
        } catch (Throwable ignore) {
            return DEFAULT_PRIMARY;
        }
    }

    // Dark text on light colors, white text otherwise.
    static int contrastingText(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255;
        return luminance > 0.65 ? 0xFF111827 : 0xFFFFFFFF;
    }

    static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }
}
