package io.gleap;

import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.drawable.Animatable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.InsetDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.widget.AppCompatImageView;
import androidx.core.view.AccessibilityDelegateCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat;

/**
 * The look of the SDK's capture UI (bar, caption and recording preview), one visual language
 * with the web SDK's bar: a dark pill with a white hairline that reads well over any app, 36 dp
 * buttons with a radius of 10 dp, the widget's color for the main action and red for Stop.
 */
final class GleapCaptureUi {
    // #16161A at 97 %, a white 10 % hairline.
    static final int PILL_COLOR = 0xF716161A;
    static final int HAIRLINE_COLOR = 0x1AFFFFFF;
    // Icon buttons and secondary buttons: white 12 %, pressed 20 %.
    static final int SECONDARY_COLOR = 0x1FFFFFFF;
    static final int SECONDARY_PRESSED_COLOR = 0x33FFFFFF;
    static final int GRIP_COLOR = 0x73FFFFFF;
    static final int TEXT_COLOR = 0xFFFFFFFF;
    static final int TIMER_COLOR = 0xD9FFFFFF;
    static final int CAPTION_TEXT_COLOR = 0xE6FFFFFF;
    static final int MUTED_TEXT_COLOR = 0x99FFFFFF;
    static final int ERROR_TEXT_COLOR = 0xFFFF9A9D;
    static final int STOP_COLOR = 0xFFE5484D;
    static final int STOP_PRESSED_COLOR = 0xFFC93C41;
    static final int RECORDING_DOT_COLOR = 0xFFFF4D4F;
    static final int BACKDROP_COLOR = 0xFF0B0B0C;
    private static final int DARK_TEXT_COLOR = 0xFF16161A;

    static final float BUTTON_HEIGHT_DP = 36;
    // Fully rounded: the bar (44 dp high), its buttons (36 dp) and the circular icon buttons.
    static final float BUTTON_RADIUS_DP = BUTTON_HEIGHT_DP / 2;
    static final float PILL_RADIUS_DP = 22;
    static final float ICON_DP = 18;

    private GleapCaptureUi() {
    }

    /**
     * The dark pill: #16161A at 97 % with a white 10 % hairline.
     */
    static GradientDrawable pill(Context context, float radiusDp) {
        GradientDrawable background = new GradientDrawable();
        background.setColor(PILL_COLOR);
        background.setCornerRadius(dp(context, radiusDp));
        background.setStroke(Math.max(1, dp(context, 1)), HAIRLINE_COLOR);
        return background;
    }

    static TextView text(Context context, String value, float sizeSp, int color, boolean semibold) {
        TextView view = new TextView(context);
        view.setText(value);
        view.setTextColor(color);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setIncludeFontPadding(false);
        if (semibold) {
            view.setTypeface(semibold());
        }
        return view;
    }

    static Typeface semibold() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            return Typeface.create(Typeface.DEFAULT, 600, false);
        }
        return Typeface.create("sans-serif-medium", Typeface.NORMAL);
    }

    /**
     * The widget's color, once the SDK has its configuration; null before (then the main action
     * is white with dark text).
     */
    static Integer configuredPrimaryColor() {
        try {
            GleapConfig config = GleapConfig.getInstance();
            if (config.getThemedFlowConfig() == null) {
                return null;
            }
            return Color.parseColor(config.getColor().trim());
        } catch (Throwable ignore) {
            return null;
        }
    }

    static int primaryFill() {
        Integer color = configuredPrimaryColor();
        return color != null ? color : Color.WHITE;
    }

    // Dark text on light colors, white text otherwise (the web bar's rule).
    static int onColor(int color) {
        int brightness = (Color.red(color) * 299 + Color.green(color) * 587 + Color.blue(color) * 114) / 1000;
        return brightness >= 160 ? DARK_TEXT_COLOR : Color.WHITE;
    }

    /**
     * A rounded background that darkens or lightens while pressed.
     */
    static Drawable buttonBackground(int color, float radiusPx, int textColor) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(radiusPx);
        GradientDrawable mask = new GradientDrawable();
        mask.setColor(Color.WHITE);
        mask.setCornerRadius(radiusPx);
        int pressed = textColor == DARK_TEXT_COLOR ? 0x1F000000 : 0x33FFFFFF;
        return new RippleDrawable(ColorStateList.valueOf(pressed), shape, mask);
    }

    /**
     * A fully rounded button with an icon and a semibold 15 sp label (36 dp high in the bar).
     *
     * @param icon         one of the {@link Icon} kinds, or -1 for none
     * @param iconTrailing the icon after the label (Send), else before it
     */
    static Button button(Context context, String label, int icon, boolean iconTrailing, int fill, float radiusDp) {
        return new Button(context, label, icon, iconTrailing, fill, onColor(fill), dp(context, radiusDp));
    }

    /**
     * A secondary button (white 12 %, white text), e.g. Retake.
     */
    static Button secondaryButton(Context context, String label, float radiusDp) {
        return new Button(context, label, -1, false, SECONDARY_COLOR, TEXT_COLOR, dp(context, radiusDp));
    }

    /**
     * A circular 36 dp icon button (white 12 %, 20 % while pressed), announced with its label. Its
     * view can be larger than the circle: a bigger touch target, the circle in the middle.
     */
    static CircleButton iconButton(Context context, int icon, String label, int widthPx, int heightPx) {
        return new CircleButton(context, icon, ICON_DP, SECONDARY_COLOR, SECONDARY_PRESSED_COLOR, label, widthPx, heightPx);
    }

    /**
     * Stop: a red 36 dp circle with a white rounded square.
     */
    static CircleButton stopButton(Context context, String label, int widthPx, int heightPx) {
        return new CircleButton(context, Icon.STOP, 12, STOP_COLOR, STOP_PRESSED_COLOR, label, widthPx, heightPx);
    }

    // The circle, centered in a view of the given size, in its color and while pressed.
    private static Drawable circle(int color, int pressedColor, int diameter, int width, int height) {
        StateListDrawable states = new StateListDrawable();
        states.setEnterFadeDuration(60);
        states.setExitFadeDuration(120);
        states.addState(new int[]{android.R.attr.state_pressed}, insetCircle(pressedColor, diameter, width, height));
        states.addState(new int[0], insetCircle(color, diameter, width, height));
        return states;
    }

    private static Drawable insetCircle(int color, int diameter, int width, int height) {
        GradientDrawable oval = new GradientDrawable();
        oval.setShape(GradientDrawable.OVAL);
        oval.setColor(color);
        int insetX = Math.max(0, (width - diameter) / 2);
        int insetY = Math.max(0, (height - diameter) / 2);
        return new InsetDrawable(oval, insetX, insetY, insetX, insetY);
    }

    // Clickable, focusable, announced as a button; taps are ignored while another app's window
    // covers it.
    private static void makeButton(View view) {
        view.setClickable(true);
        view.setFocusable(true);
        view.setFilterTouchesWhenObscured(true);
        ViewCompat.setAccessibilityDelegate(view, new AccessibilityDelegateCompat() {
            @Override
            public void onInitializeAccessibilityNodeInfo(View host, AccessibilityNodeInfoCompat info) {
                super.onInitializeAccessibilityNodeInfo(host, info);
                info.setClassName("android.widget.Button");
            }
        });
    }

    /**
     * Enables or disables a button: a disabled one is dimmed.
     */
    static void setEnabled(View view, boolean enabled) {
        if (view == null) {
            return;
        }
        view.setEnabled(enabled);
        view.setAlpha(enabled ? 1f : 0.5f);
    }

    static boolean animationsEnabled() {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.O || ValueAnimator.areAnimatorsEnabled();
    }

    static int dp(Context context, float value) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, value,
                context.getResources().getDisplayMetrics()));
    }

    // ---------------------------------------------------------------------------------------------

    /**
     * A pill or rounded button: an optional icon (the spinner while busy, in its place so nothing
     * moves) and a single-line label, centered.
     */
    static final class Button extends LinearLayout implements Busy {
        private final ImageView iconView;
        private final TextView label;
        private final int textColor;
        private Icon icon;
        private Spinner spinner;
        private boolean busy;

        Button(Context context, String text, int iconKind, boolean iconTrailing, int fill, int textColor, float radiusPx) {
            super(context);
            this.textColor = textColor;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER);
            setBackground(buttonBackground(fill, radiusPx, textColor));
            int padding = dp(context, 16);
            int iconSide = dp(context, 13);
            setPaddingRelative(iconKind >= 0 && !iconTrailing ? iconSide : padding, 0,
                    iconKind >= 0 && iconTrailing ? iconSide : padding, 0);
            makeButton(this);

            iconView = new ImageView(context);
            iconView.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
            int iconSize = dp(context, ICON_DP);
            LayoutParams iconParams = new LayoutParams(iconSize, iconSize);
            if (iconTrailing) {
                iconParams.setMarginStart(dp(context, 6));
            } else {
                iconParams.setMarginEnd(dp(context, 6));
            }
            if (iconKind >= 0) {
                icon = new Icon(iconKind, textColor);
                iconView.setImageDrawable(icon);
            } else {
                iconView.setVisibility(GONE);
            }

            label = text(context, text, 15, textColor, true);
            label.setSingleLine(true);
            label.setEllipsize(TextUtils.TruncateAt.END);
            label.setGravity(Gravity.CENTER);
            LayoutParams labelParams = new LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (iconTrailing) {
                addView(label, labelParams);
                addView(iconView, iconParams);
            } else {
                addView(iconView, iconParams);
                addView(label, labelParams);
            }
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            LayoutParams labelParams = (LayoutParams) label.getLayoutParams();
            labelParams.weight = 0f;
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            int needed = getPaddingLeft() + getPaddingRight();
            for (int i = 0; i < getChildCount(); i++) {
                View child = getChildAt(i);
                if (child.getVisibility() != GONE) {
                    LayoutParams params = (LayoutParams) child.getLayoutParams();
                    needed += child.getMeasuredWidth() + params.leftMargin + params.rightMargin;
                }
            }
            if (needed > getMeasuredWidth()) {
                // Too narrow: the label gives way (it is cut), never the icon.
                labelParams.weight = 1f;
                super.onMeasure(widthMeasureSpec, heightMeasureSpec);
            }
        }

        void setText(CharSequence text) {
            label.setText(text);
        }

        CharSequence getText() {
            return label.getText();
        }

        /**
         * Shows a spinner instead of the icon (or before the label) and ignores taps.
         */
        @Override
        public void setBusy(boolean value) {
            if (busy == value) {
                return;
            }
            busy = value;
            if (value) {
                if (spinner == null) {
                    spinner = new Spinner(textColor, dp(getContext(), 2));
                }
                iconView.setImageDrawable(spinner);
                iconView.setVisibility(VISIBLE);
                spinner.start();
            } else {
                if (spinner != null) {
                    spinner.stop();
                }
                iconView.setImageDrawable(icon);
                iconView.setVisibility(icon != null ? VISIBLE : GONE);
            }
            setClickable(!value);
        }

        boolean isBusy() {
            return busy;
        }

        @Override
        public boolean performClick() {
            if (busy) {
                return false;
            }
            return super.performClick();
        }
    }

    /**
     * A button that shows a spinner (and ignores taps) while the SDK works.
     */
    interface Busy {
        void setBusy(boolean busy);
    }

    /**
     * A circular icon button; while busy a spinner replaces the icon (nothing moves).
     */
    static final class CircleButton extends AppCompatImageView implements Busy {
        private final Icon icon;
        private final int iconSize;
        private Spinner spinner;
        private boolean busy;

        CircleButton(Context context, int iconKind, float iconDp, int color, int pressedColor, String label,
                     int width, int height) {
            super(context);
            icon = new Icon(iconKind, TEXT_COLOR);
            iconSize = dp(context, iconDp);
            setImageDrawable(icon);
            setScaleType(ScaleType.FIT_CENTER);
            // The background first: its insets would replace the padding that sizes the icon.
            setBackground(circle(color, pressedColor, dp(context, BUTTON_HEIGHT_DP), width, height));
            setIconSize(iconSize, width, height);
            setContentDescription(label);
            makeButton(this);
        }

        private void setIconSize(int size, int width, int height) {
            int horizontal = Math.max(0, (width - size) / 2);
            int vertical = Math.max(0, (height - size) / 2);
            setPadding(horizontal, vertical, horizontal, vertical);
        }

        @Override
        public void setBusy(boolean value) {
            if (busy == value) {
                return;
            }
            busy = value;
            int width = getLayoutParams() != null && getLayoutParams().width > 0 ? getLayoutParams().width : getWidth();
            int height = getLayoutParams() != null && getLayoutParams().height > 0 ? getLayoutParams().height : getHeight();
            if (value) {
                if (spinner == null) {
                    spinner = new Spinner(TEXT_COLOR, dp(getContext(), 2));
                }
                setIconSize(dp(getContext(), ICON_DP), width, height);
                setImageDrawable(spinner);
                spinner.start();
            } else {
                if (spinner != null) {
                    spinner.stop();
                }
                setIconSize(iconSize, width, height);
                setImageDrawable(icon);
            }
            setClickable(!value);
        }

        @Override
        public boolean performClick() {
            if (busy) {
                return false;
            }
            return super.performClick();
        }
    }

    /**
     * A small ring that turns while the SDK works (static when the system turned animations off).
     */
    static final class Spinner extends Drawable implements Animatable {
        private static final long TURN_MS = 800;
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF oval = new RectF();
        private boolean running;

        Spinner(int color, float strokePx) {
            paint.setColor(color);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(strokePx);
            paint.setStrokeCap(Paint.Cap.ROUND);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float size = Math.min(bounds.width(), bounds.height()) * 0.82f;
            float inset = paint.getStrokeWidth() / 2;
            oval.set(bounds.exactCenterX() - size / 2 + inset, bounds.exactCenterY() - size / 2 + inset,
                    bounds.exactCenterX() + size / 2 - inset, bounds.exactCenterY() + size / 2 - inset);
            boolean turning = running && animationsEnabled();
            float angle = turning ? (SystemClock.uptimeMillis() % TURN_MS) * 360f / TURN_MS : 0;
            canvas.drawArc(oval, angle - 90, 270, false, paint);
            if (turning && isVisible()) {
                invalidateSelf();
            }
        }

        @Override
        public void start() {
            running = true;
            invalidateSelf();
        }

        @Override
        public void stop() {
            running = false;
        }

        @Override
        public boolean isRunning() {
            return running;
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            paint.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * The capture UI's icons, drawn on a 24 x 24 grid like the web bar's.
     */
    static final class Icon extends Drawable {
        static final int CAMERA = 0;
        static final int RECORD = 1;
        static final int STOP = 2;
        static final int CLOSE = 3;
        static final int PLAY = 4;
        static final int SEND = 5;

        private final int kind;
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path path = new Path();
        private final RectF rect = new RectF();

        Icon(int kind, int color) {
            this.kind = kind;
            // Points the other way right to left.
            setAutoMirrored(kind == SEND);
            stroke.setColor(color);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(1.8f);
            stroke.setStrokeCap(Paint.Cap.ROUND);
            stroke.setStrokeJoin(Paint.Join.ROUND);
            fill.setColor(color);
            fill.setStyle(Paint.Style.FILL);
        }

        @Override
        public void draw(Canvas canvas) {
            Rect bounds = getBounds();
            float scale = Math.min(bounds.width(), bounds.height()) / 24f;
            if (scale <= 0) {
                return;
            }
            int save = canvas.save();
            if (isAutoMirrored() && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                    && getLayoutDirection() == View.LAYOUT_DIRECTION_RTL) {
                canvas.scale(-1f, 1f, bounds.exactCenterX(), bounds.exactCenterY());
            }
            canvas.translate(bounds.exactCenterX() - 12 * scale, bounds.exactCenterY() - 12 * scale);
            canvas.scale(scale, scale);
            switch (kind) {
                case CAMERA:
                    path.reset();
                    path.moveTo(4, 8);
                    path.lineTo(7, 8);
                    path.lineTo(8.6f, 5.6f);
                    path.quadTo(9f, 5f, 9.9f, 5f);
                    path.lineTo(14.1f, 5f);
                    path.quadTo(15f, 5f, 15.4f, 5.6f);
                    path.lineTo(17, 8);
                    path.lineTo(20, 8);
                    path.quadTo(21, 8, 21, 9);
                    path.lineTo(21, 18);
                    path.quadTo(21, 19, 20, 19);
                    path.lineTo(4, 19);
                    path.quadTo(3, 19, 3, 18);
                    path.lineTo(3, 9);
                    path.quadTo(3, 8, 4, 8);
                    path.close();
                    canvas.drawPath(path, stroke);
                    canvas.drawCircle(12, 13, 3.4f, stroke);
                    break;
                case RECORD:
                    canvas.drawCircle(12, 12, 8, stroke);
                    canvas.drawCircle(12, 12, 3.6f, fill);
                    break;
                case STOP:
                    // The whole box: a rounded square (corners 2.5 / 12 of its size).
                    rect.set(0, 0, 24, 24);
                    canvas.drawRoundRect(rect, 5f, 5f, fill);
                    break;
                case CLOSE:
                    stroke.setStrokeWidth(2f);
                    canvas.drawLine(7, 7, 17, 17, stroke);
                    canvas.drawLine(17, 7, 7, 17, stroke);
                    stroke.setStrokeWidth(1.8f);
                    break;
                case PLAY:
                    path.reset();
                    path.moveTo(8.5f, 6.2f);
                    path.lineTo(18.2f, 12f);
                    path.lineTo(8.5f, 17.8f);
                    path.close();
                    canvas.drawPath(path, fill);
                    stroke.setStrokeWidth(1.6f);
                    canvas.drawPath(path, stroke);
                    stroke.setStrokeWidth(1.8f);
                    break;
                case SEND:
                    path.reset();
                    path.moveTo(4.2f, 4.6f);
                    path.lineTo(20.6f, 12f);
                    path.lineTo(4.2f, 19.4f);
                    path.lineTo(6.6f, 12f);
                    path.close();
                    canvas.drawPath(path, fill);
                    stroke.setStrokeWidth(1.4f);
                    canvas.drawPath(path, stroke);
                    stroke.setStrokeWidth(1.8f);
                    break;
                default:
                    break;
            }
            canvas.restoreToCount(save);
        }

        @Override
        public void setAlpha(int alpha) {
            stroke.setAlpha(alpha);
            fill.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter filter) {
            stroke.setColorFilter(filter);
            fill.setColorFilter(filter);
        }

        @Override
        public int getOpacity() {
            return PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * The drag handle: six dots, two columns of three, white 45 %.
     */
    static final class Grip extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final float radius;
        private final float spacing;

        Grip(Context context) {
            super(context);
            paint.setColor(GRIP_COLOR);
            radius = dp(context, 1.5f);
            spacing = dp(context, 5.5f);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            for (int column = -1; column <= 1; column += 2) {
                for (int row = -1; row <= 1; row++) {
                    canvas.drawCircle(cx + column * spacing / 2, cy + row * spacing, radius, paint);
                }
            }
        }
    }
}
