/*
 * This is the source code of Telegram for Android v. 3.x.x.
 * It is licensed under GNU GPL v. 2 or later.
 * You should have received a copy of the license in this archive (see LICENSE).
 *
 * Copyright Nikolai Kudashov, 2013-2016.
 */

package kani.aitkulov.telegram_emoji.ui;

import android.annotation.TargetApi;
import android.app.Activity;
import android.content.Context;
import android.content.SharedPreferences;
import android.database.DataSetObserver;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.os.Build;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.AttributeSet;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.BaseAdapter;
import android.widget.FrameLayout;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;

import kani.aitkulov.telegram_emoji.AndroidUtilities;
import kani.aitkulov.telegram_emoji.Emoji;
import kani.aitkulov.telegram_emoji.EmojiData;
import kani.aitkulov.telegram_emoji.LayoutHelper;
import kani.aitkulov.telegram_emoji.R;
import kani.aitkulov.telegram_emoji.Utilities;

public class EmojiView extends FrameLayout {

    public interface Listener {
        boolean onBackspace();

        void onEmojiSelected(String emoji);

        void onClearEmojiRecent();
    }

    private static final Field superListenerField;

    static {
        Field f = null;
        try {
            f = PopupWindow.class.getDeclaredField("mOnScrollChangedListener");
            f.setAccessible(true);
        } catch (NoSuchFieldException e) {
            /* ignored */
        }
        superListenerField = f;
    }

    private static final ViewTreeObserver.OnScrollChangedListener NOP = new ViewTreeObserver.OnScrollChangedListener() {
        @Override
        public void onScrollChanged() {
            /* do nothing */
        }
    };

    private static String addColorToCode(String code, String color) {
        String end = null;
        if (code.endsWith("\u200D\u2640") || code.endsWith("\u200D\u2642")) {
            end = code.substring(code.length() - 2);
            code = code.substring(0, code.length() - 2);
        }
        code += color;
        if (end != null) {
            code += end;
        }
        return code;
    }

    private class ImageViewEmoji extends ImageView {

        private boolean touched;
        private float lastX;
        private float lastY;
        private float touchedX;
        private float touchedY;

        public ImageViewEmoji(Context context) {
            super(context);
            setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View v) {
                    sendEmoji(null);
                }
            });
            setOnLongClickListener(new OnLongClickListener() {
                @Override
                public boolean onLongClick(View view) {
                    String code = (String) view.getTag();
                    if (EmojiData.emojiColoredMap.containsKey(code)) {
                        touched = true;
                        touchedX = lastX;
                        touchedY = lastY;

                        String color = emojiColor.get(code);
                        if (color != null) {
                            switch (color) {
                                case "\uD83C\uDFFB":
                                    pickerView.setSelection(1);
                                    break;
                                case "\uD83C\uDFFC":
                                    pickerView.setSelection(2);
                                    break;
                                case "\uD83C\uDFFD":
                                    pickerView.setSelection(3);
                                    break;
                                case "\uD83C\uDFFE":
                                    pickerView.setSelection(4);
                                    break;
                                case "\uD83C\uDFFF":
                                    pickerView.setSelection(5);
                                    break;
                            }
                        } else {
                            pickerView.setSelection(0);
                        }
                        view.getLocationOnScreen(location);
                        int x = emojiSize * pickerView.getSelection() + AndroidUtilities.dp(4 * pickerView.getSelection() - (AndroidUtilities.isTablet() ? 5 : 1));
                        if (location[0] - x < AndroidUtilities.dp(5)) {
                            x += (location[0] - x) - AndroidUtilities.dp(5);
                        } else if (location[0] - x + popupWidth > AndroidUtilities.displaySize.x - AndroidUtilities.dp(5)) {
                            x += (location[0] - x + popupWidth) - (AndroidUtilities.displaySize.x - AndroidUtilities.dp(5));
                        }
                        int xOffset = -x;
                        int yOffset = view.getTop() < 0 ? view.getTop() : 0;

                        pickerView.setEmoji(code, AndroidUtilities.dp(AndroidUtilities.isTablet() ? 30 : 22) - xOffset + (int) AndroidUtilities.dpf2(0.5f));

                        pickerViewPopup.setFocusable(true);
                        pickerViewPopup.showAsDropDown(view, xOffset, -view.getMeasuredHeight() - popupHeight + (view.getMeasuredHeight() - emojiSize) / 2 - yOffset);
                        view.getParent().requestDisallowInterceptTouchEvent(true);
                        return true;
                    } else if (pager.getCurrentItem() == 0) {
                        listener.onClearEmojiRecent();
                    }
                    return false;
                }
            });
            setBackgroundResource(R.drawable.list_selector);
            setScaleType(ImageView.ScaleType.CENTER);
        }

        private void sendEmoji(String override) {
            String code = override != null ? override : (String) getTag();
            if (override == null) {
                if (pager.getCurrentItem() != 0) {
                    String color = emojiColor.get(code);
                    if (color != null) {
                        code = addColorToCode(code, color);
                    }
                }
                Integer count = emojiUseHistory.get(code);
                if (count == null) {
                    count = 0;
                }
                if (count == 0 && emojiUseHistory.size() > 50) {
                    for (int a = recentEmoji.size() - 1; a >= 0; a--) {
                        String emoji = recentEmoji.get(a);
                        emojiUseHistory.remove(emoji);
                        recentEmoji.remove(a);
                        if (emojiUseHistory.size() <= 50) {
                            break;
                        }
                    }
                }
                emojiUseHistory.put(code, ++count);
                if (pager.getCurrentItem() != 0) {
                    sortEmoji();
                }
                saveRecentEmoji();
                adapters.get(0).notifyDataSetChanged();
                if (listener != null) {
                    listener.onEmojiSelected(Emoji.fixEmoji(code));
                }
            } else {
                if (listener != null) {
                    listener.onEmojiSelected(Emoji.fixEmoji(override));
                }
            }
        }

        @Override
        public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            setMeasuredDimension(View.MeasureSpec.getSize(widthMeasureSpec), View.MeasureSpec.getSize(widthMeasureSpec));
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            if (touched) {
                if (event.getAction() == MotionEvent.ACTION_UP || event.getAction() == MotionEvent.ACTION_CANCEL) {
                    if (pickerViewPopup != null && pickerViewPopup.isShowing()) {
                        pickerViewPopup.dismiss();

                        String color = null;
                        switch (pickerView.getSelection()) {
                            case 1:
                                color = "\uD83C\uDFFB";
                                break;
                            case 2:
                                color = "\uD83C\uDFFC";
                                break;
                            case 3:
                                color = "\uD83C\uDFFD";
                                break;
                            case 4:
                                color = "\uD83C\uDFFE";
                                break;
                            case 5:
                                color = "\uD83C\uDFFF";
                                break;
                        }
                        String code = (String) getTag();
                        if (pager.getCurrentItem() != 0) {
                            if (color != null) {
                                emojiColor.put(code, color);
                                code = addColorToCode(code, color);
                            } else {
                                emojiColor.remove(code);
                            }
                            setImageDrawable(Emoji.getEmojiBigDrawable(code));
                            sendEmoji(null);
                            saveEmojiColors();
                        } else {
                            sendEmoji(code + (color != null ? color : ""));
                        }
                    }
                    touched = false;
                    touchedX = -10000;
                    touchedY = -10000;
                } else if (event.getAction() == MotionEvent.ACTION_MOVE) {
                    boolean ignore = false;
                    if (touchedX != -10000) {
                        if (Math.abs(touchedX - event.getX()) > AndroidUtilities.getPixelsInCM(0.2f, true) || Math.abs(touchedY - event.getY()) > AndroidUtilities.getPixelsInCM(0.2f, false)) {
                            touchedX = -10000;
                            touchedY = -10000;
                        } else {
                            ignore = true;
                        }
                    }
                    if (!ignore) {
                        getLocationOnScreen(location);
                        float x = location[0] + event.getX();
                        pickerView.getLocationOnScreen(location);
                        x -= location[0] + AndroidUtilities.dp(3);
                        int position = (int) (x / (emojiSize + AndroidUtilities.dp(4)));
                        if (position < 0) {
                            position = 0;
                        } else if (position > 5) {
                            position = 5;
                        }
                        pickerView.setSelection(position);
                    }
                }
            }
            lastX = event.getX();
            lastY = event.getY();
            return super.onTouchEvent(event);
        }
    }

    private class EmojiPopupWindow extends PopupWindow {

        private ViewTreeObserver.OnScrollChangedListener mSuperScrollListener;
        private ViewTreeObserver mViewTreeObserver;

        public EmojiPopupWindow() {
            super();
            init();
        }

        public EmojiPopupWindow(Context context) {
            super(context);
            init();
        }

        public EmojiPopupWindow(int width, int height) {
            super(width, height);
            init();
        }

        public EmojiPopupWindow(View contentView) {
            super(contentView);
            init();
        }

        public EmojiPopupWindow(View contentView, int width, int height, boolean focusable) {
            super(contentView, width, height, focusable);
            init();
        }

        public EmojiPopupWindow(View contentView, int width, int height) {
            super(contentView, width, height);
            init();
        }

        private void init() {
            if (superListenerField != null) {
                try {
                    mSuperScrollListener = (ViewTreeObserver.OnScrollChangedListener) superListenerField.get(this);
                    superListenerField.set(this, NOP);
                } catch (Exception e) {
                    mSuperScrollListener = null;
                }
            }
        }

        private void unregisterListener() {
            if (mSuperScrollListener != null && mViewTreeObserver != null) {
                if (mViewTreeObserver.isAlive()) {
                    mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
                }
                mViewTreeObserver = null;
            }
        }

        private void registerListener(View anchor) {
            if (mSuperScrollListener != null) {
                ViewTreeObserver vto = (anchor.getWindowToken() != null) ? anchor.getViewTreeObserver() : null;
                if (vto != mViewTreeObserver) {
                    if (mViewTreeObserver != null && mViewTreeObserver.isAlive()) {
                        mViewTreeObserver.removeOnScrollChangedListener(mSuperScrollListener);
                    }
                    if ((mViewTreeObserver = vto) != null) {
                        vto.addOnScrollChangedListener(mSuperScrollListener);
                    }
                }
            }
        }

        @Override
        public void showAsDropDown(View anchor, int xoff, int yoff) {
            try {
                super.showAsDropDown(anchor, xoff, yoff);
                registerListener(anchor);
            } catch (Exception e) {
                Log.e("tmessages", e.toString());
            }
        }

        @Override
        public void update(View anchor, int xoff, int yoff, int width, int height) {
            super.update(anchor, xoff, yoff, width, height);
            registerListener(anchor);
        }

        @Override
        public void update(View anchor, int width, int height) {
            super.update(anchor, width, height);
            registerListener(anchor);
        }

        @Override
        public void showAtLocation(View parent, int gravity, int x, int y) {
            super.showAtLocation(parent, gravity, x, y);
            unregisterListener();
        }

        @Override
        public void dismiss() {
            setFocusable(false);
            try {
                super.dismiss();
            } catch (Exception e) {
                //don't promt
            }
            unregisterListener();
        }
    }

    private class EmojiColorPickerView extends View {

        private Drawable backgroundDrawable;
        private Drawable arrowDrawable;
        private String currentEmoji;
        private int arrowX;
        private int selection;
        private Paint rectPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private RectF rect = new RectF();

        public void setEmoji(String emoji, int arrowPosition) {
            currentEmoji = emoji;
            arrowX = arrowPosition;
            rectPaint.setColor(0x2f000000);
            invalidate();
        }

        public String getEmoji() {
            return currentEmoji;
        }

        public void setSelection(int position) {
            if (selection == position) {
                return;
            }
            selection = position;
            invalidate();
        }

        public int getSelection() {
            return selection;
        }

        public EmojiColorPickerView(Context context) {
            super(context);

            backgroundDrawable = getResources().getDrawable(R.drawable.stickers_back_all);
            arrowDrawable = getResources().getDrawable(R.drawable.stickers_back_arrow);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            backgroundDrawable.setBounds(0, 0, getMeasuredWidth(), AndroidUtilities.dp(AndroidUtilities.isTablet() ? 60 : 52));
            backgroundDrawable.draw(canvas);

            arrowDrawable.setBounds(arrowX - AndroidUtilities.dp(9), AndroidUtilities.dp(AndroidUtilities.isTablet() ? 55.5f : 47.5f), arrowX + AndroidUtilities.dp(9), AndroidUtilities.dp((AndroidUtilities.isTablet() ? 55.5f : 47.5f) + 8));
            arrowDrawable.draw(canvas);

            if (currentEmoji != null) {
                String code;
                for (int a = 0; a < 6; a++) {
                    int x = emojiSize * a + AndroidUtilities.dp(5 + 4 * a);
                    int y = AndroidUtilities.dp(9);
                    if (selection == a) {
                        rect.set(x, y - (int) AndroidUtilities.dpf2(3.5f), x + emojiSize, y + emojiSize + AndroidUtilities.dp(3));
                        canvas.drawRoundRect(rect, AndroidUtilities.dp(4), AndroidUtilities.dp(4), rectPaint);
                    }
                    code = currentEmoji;
                    if (a != 0) {
                        String color;
                        switch (a) {
                            case 1:
                                color = "\uD83C\uDFFB";
                                break;
                            case 2:
                                color = "\uD83C\uDFFC";
                                break;
                            case 3:
                                color = "\uD83C\uDFFD";
                                break;
                            case 4:
                                color = "\uD83C\uDFFE";
                                break;
                            case 5:
                                color = "\uD83C\uDFFF";
                                break;
                            default:
                                color = "";
                        }
                        code = addColorToCode(code, color);
                    }
                    Drawable drawable = Emoji.getEmojiBigDrawable(code);
                    if (drawable != null) {
                        drawable.setBounds(x, y, x + emojiSize, y + emojiSize);
                        drawable.draw(canvas);
                    }
                }
            }
        }
    }

    private ArrayList<EmojiGridAdapter> adapters = new ArrayList<>();
    private HashMap<String, Integer> emojiUseHistory = new HashMap<>();
    private static HashMap<String, String> emojiColor = new HashMap<>();
    private ArrayList<String> recentEmoji = new ArrayList<>();

    private int[] icons = {
            R.drawable.ic_emoji_recent,
            R.drawable.ic_emoji_smile,
            R.drawable.ic_emoji_flower,
            R.drawable.ic_emoji_bell,
            R.drawable.ic_emoji_car,
            R.drawable.ic_emoji_symbol};

    private Listener listener;
    private ViewPager pager;
    private ArrayList<View> views = new ArrayList<>();
    private ArrayList<GridView> emojiGrids = new ArrayList<>();
    private ImageView backspaceButton;
    private LinearLayout emojiTab;
    private PagerSlidingTabStrip pagerSlidingTabStrip;

    private int currentPage;

    private EmojiColorPickerView pickerView;
    private EmojiPopupWindow pickerViewPopup;
    private int popupWidth;
    private int popupHeight;
    private int emojiSize;
    private int location[] = new int[2];

    private boolean isLayout;
    private int currentBackgroundType = -1;
    private Object outlineProvider;

    private int oldWidth;

    private boolean backspacePressed;
    private boolean backspaceOnce;


    public EmojiView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public EmojiView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init(context);
    }

    public EmojiView(Context context) {
        super(context);
        init(context);
    }

    private void init(Context context) {

        if (Build.VERSION.SDK_INT >= 21) {
            outlineProvider = new ViewOutlineProvider() {
                @TargetApi(Build.VERSION_CODES.LOLLIPOP)
                @Override
                public void getOutline(View view, Outline outline) {
                    outline.setRoundRect(view.getPaddingLeft(), view.getPaddingTop(), view.getMeasuredWidth() - view.getPaddingRight(), view.getMeasuredHeight() - view.getPaddingBottom(), AndroidUtilities.dp(6));
                }
            };
        }

        for (int i = 0; i < EmojiData.dataColored.length + 1; i++) {
            GridView gridView = new GridView(context);
            if (AndroidUtilities.isTablet()) {
                gridView.setColumnWidth(AndroidUtilities.dp(60));
            } else {
                gridView.setColumnWidth(AndroidUtilities.dp(45));
            }
            gridView.setNumColumns(-1);
            EmojiGridAdapter emojiGridAdapter = new EmojiGridAdapter(i - 1);
            AndroidUtilities.setListViewEdgeEffectColor(gridView, 0xfff5f6f7);
            gridView.setAdapter(emojiGridAdapter);
            adapters.add(emojiGridAdapter);
            emojiGrids.add(gridView);
            FrameLayout frameLayout = new FrameLayout(context);
            frameLayout.addView(gridView, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.TOP | Gravity.LEFT, 0, 48, 0, 0));

            views.add(frameLayout);
        }

        pager = new ViewPager(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return super.onInterceptTouchEvent(ev);
            }
        };
        pager.setAdapter(new EmojiPagesAdapter());

        emojiTab = new LinearLayout(context) {
            @Override
            public boolean onInterceptTouchEvent(MotionEvent ev) {
                if (getParent() != null) {
                    getParent().requestDisallowInterceptTouchEvent(true);
                }
                return super.onInterceptTouchEvent(ev);
            }
        };
        emojiTab.setOrientation(LinearLayout.HORIZONTAL);
        addView(emojiTab, LayoutHelper.createFrame(LayoutParams.MATCH_PARENT, 48));

        pagerSlidingTabStrip = new PagerSlidingTabStrip(context);
        pagerSlidingTabStrip.setViewPager(pager);
        pagerSlidingTabStrip.setShouldExpand(true);
        pagerSlidingTabStrip.setIndicatorHeight(AndroidUtilities.dp(2));
        pagerSlidingTabStrip.setUnderlineHeight(AndroidUtilities.dp(1));
        pagerSlidingTabStrip.setIndicatorColor(0xff2b96e2);
        pagerSlidingTabStrip.setUnderlineColor(0xffe2e5e7);
        emojiTab.addView(pagerSlidingTabStrip, LayoutHelper.createLinear(0, 48, 1.0f));
        pagerSlidingTabStrip.setOnPageChangeListener(new ViewPager.OnPageChangeListener() {
            @Override
            public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {
            }

            @Override
            public void onPageSelected(int position) {
                saveNewPage();
            }

            @Override
            public void onPageScrollStateChanged(int state) {

            }
        });

        FrameLayout frameLayout = new FrameLayout(context);
        emojiTab.addView(frameLayout, LayoutHelper.createLinear(52, 48));

        backspaceButton = new ImageView(context) {
            @Override
            public boolean onTouchEvent(MotionEvent event) {
                if (event.getAction() == MotionEvent.ACTION_DOWN) {
                    backspacePressed = true;
                    backspaceOnce = false;
                    postBackspaceRunnable(350);
                } else if (event.getAction() == MotionEvent.ACTION_CANCEL || event.getAction() == MotionEvent.ACTION_UP) {
                    backspacePressed = false;
                    if (!backspaceOnce) {
                        if (listener != null && listener.onBackspace()) {
                            backspaceButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                        }
                    }
                }
                super.onTouchEvent(event);
                return true;
            }
        };
        backspaceButton.setImageResource(R.drawable.ic_smiles_backspace);
        backspaceButton.setBackgroundResource(R.drawable.ic_emoji_backspace);
        backspaceButton.setScaleType(ImageView.ScaleType.CENTER);
        frameLayout.addView(backspaceButton, LayoutHelper.createFrame(52, 48));

        View view = new View(context);
        view.setBackgroundColor(0xffe2e5e7);
        frameLayout.addView(view, LayoutHelper.createFrame(52, 1, Gravity.LEFT | Gravity.BOTTOM));

        TextView textView = new TextView(context);
        textView.setText("No Recent");
        textView.setTextSize(18);
        textView.setTextColor(0xff888888);
        textView.setGravity(Gravity.CENTER);
        textView.setClickable(false);
        textView.setFocusable(false);
        ((FrameLayout) views.get(0)).addView(textView, LayoutHelper.createFrame(LayoutHelper.WRAP_CONTENT, LayoutHelper.WRAP_CONTENT, Gravity.CENTER, 0, 48, 0, 0));
        emojiGrids.get(0).setEmptyView(textView);

        addView(pager, 0, LayoutHelper.createFrame(LayoutHelper.MATCH_PARENT, LayoutHelper.MATCH_PARENT, Gravity.LEFT | Gravity.TOP));

        emojiSize = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 40 : 32);
        pickerView = new EmojiColorPickerView(context);
        pickerViewPopup = new EmojiPopupWindow(pickerView, popupWidth = AndroidUtilities.dp((AndroidUtilities.isTablet() ? 40 : 32) * 6 + 10 + 4 * 5), popupHeight = AndroidUtilities.dp(AndroidUtilities.isTablet() ? 64 : 56));
        pickerViewPopup.setOutsideTouchable(true);
        pickerViewPopup.setClippingEnabled(true);
        pickerViewPopup.setInputMethodMode(EmojiPopupWindow.INPUT_METHOD_NOT_NEEDED);
        pickerViewPopup.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_UNSPECIFIED);
        pickerViewPopup.getContentView().setFocusableInTouchMode(true);
        pickerViewPopup.getContentView().setOnKeyListener(new OnKeyListener() {
            @Override
            public boolean onKey(View v, int keyCode, KeyEvent event) {
                if (keyCode == KeyEvent.KEYCODE_MENU && event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_UP && pickerViewPopup != null && pickerViewPopup.isShowing()) {
                    pickerViewPopup.dismiss();
                    return true;
                }
                return false;
            }
        });
        currentPage = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE).getInt("selected_page", 0);
        loadRecents();
        Emoji.onEmojiLoadListener = new Emoji.OnEmojiLoadListener() {
            @Override
            public void emojiLoaded() {
                invalidateViews();
            }
        };
    }

    private void saveNewPage() {
        int newPage = 0;
        if (currentPage != newPage) {
            currentPage = newPage;
            getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE).edit().putInt("selected_page", newPage).commit();
        }
    }

    public void clearRecentEmoji() {
        SharedPreferences preferences = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE);
        preferences.edit().putBoolean("filled_default", true).commit();
        emojiUseHistory.clear();
        recentEmoji.clear();
        saveRecentEmoji();
        adapters.get(0).notifyDataSetChanged();
    }

    private void postBackspaceRunnable(final int time) {
        AndroidUtilities.runOnUIThread(new Runnable() {
            @Override
            public void run() {
                if (!backspacePressed) {
                    return;
                }
                if (listener != null && listener.onBackspace()) {
                    backspaceButton.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
                }
                backspaceOnce = true;
                postBackspaceRunnable(Math.max(50, time - 100));
            }
        }, time);
    }

    private String convert(long paramLong) {
        String str = "";
        for (int i = 0; ; i++) {
            if (i >= 4) {
                return str;
            }
            int j = (int) (0xFFFF & paramLong >> 16 * (3 - i));
            if (j != 0) {
                str = str + (char) j;
            }
        }
    }

    private void saveRecentEmoji() {
        SharedPreferences preferences = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE);
        StringBuilder stringBuilder = new StringBuilder();
        for (HashMap.Entry<String, Integer> entry : emojiUseHistory.entrySet()) {
            if (stringBuilder.length() != 0) {
                stringBuilder.append(",");
            }
            stringBuilder.append(entry.getKey());
            stringBuilder.append("=");
            stringBuilder.append(entry.getValue());
        }
        preferences.edit().putString("emojis2", stringBuilder.toString()).commit();
    }

    private void saveEmojiColors() {
        SharedPreferences preferences = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE);
        StringBuilder stringBuilder = new StringBuilder();
        for (HashMap.Entry<String, String> entry : emojiColor.entrySet()) {
            if (stringBuilder.length() != 0) {
                stringBuilder.append(",");
            }
            stringBuilder.append(entry.getKey());
            stringBuilder.append("=");
            stringBuilder.append(entry.getValue());
        }
        preferences.edit().putString("color", stringBuilder.toString()).commit();
    }

    private void sortEmoji() {
        recentEmoji.clear();
        for (HashMap.Entry<String, Integer> entry : emojiUseHistory.entrySet()) {
            recentEmoji.add(entry.getKey());
        }
        Collections.sort(recentEmoji, new Comparator<String>() {
            @Override
            public int compare(String lhs, String rhs) {
                Integer count1 = emojiUseHistory.get(lhs);
                Integer count2 = emojiUseHistory.get(rhs);
                if (count1 == null) {
                    count1 = 0;
                }
                if (count2 == null) {
                    count2 = 0;
                }
                if (count1 > count2) {
                    return -1;
                } else if (count1 < count2) {
                    return 1;
                }
                return 0;
            }
        });
        while (recentEmoji.size() > 50) {
            recentEmoji.remove(recentEmoji.size() - 1);
        }
    }

    public void loadRecents() {
        SharedPreferences preferences = getContext().getSharedPreferences("emoji", Activity.MODE_PRIVATE);

        String str;
        try {
            emojiUseHistory.clear();
            if (preferences.contains("emojis")) {
                str = preferences.getString("emojis", "");
                if (str != null && str.length() > 0) {
                    String[] args = str.split(",");
                    for (String arg : args) {
                        String[] args2 = arg.split("=");
                        long value = Utilities.parseLong(args2[0]);
                        String string = "";
                        for (int a = 0; a < 4; a++) {
                            char ch = (char) value;
                            string = String.valueOf(ch) + string;
                            value >>= 16;
                            if (value == 0) {
                                break;
                            }
                        }
                        if (string.length() > 0) {
                            emojiUseHistory.put(string, Utilities.parseInt(args2[1]));
                        }
                    }
                }
                preferences.edit().remove("emojis").commit();
                saveRecentEmoji();
            } else {
                str = preferences.getString("emojis2", "");
                if (str != null && str.length() > 0) {
                    String[] args = str.split(",");
                    for (String arg : args) {
                        String[] args2 = arg.split("=");
                        emojiUseHistory.put(args2[0], Utilities.parseInt(args2[1]));
                    }
                }
            }
            if (emojiUseHistory.isEmpty()) {
                if (!preferences.getBoolean("filled_default", false)) {
                    String[] newRecent = new String[]{
                            "\uD83D\uDE02", "\uD83D\uDE18", "\u2764", "\uD83D\uDE0D", "\uD83D\uDE0A", "\uD83D\uDE01",
                            "\uD83D\uDC4D", "\u263A", "\uD83D\uDE14", "\uD83D\uDE04", "\uD83D\uDE2D", "\uD83D\uDC8B",
                            "\uD83D\uDE12", "\uD83D\uDE33", "\uD83D\uDE1C", "\uD83D\uDE48", "\uD83D\uDE09", "\uD83D\uDE03",
                            "\uD83D\uDE22", "\uD83D\uDE1D", "\uD83D\uDE31", "\uD83D\uDE21", "\uD83D\uDE0F", "\uD83D\uDE1E",
                            "\uD83D\uDE05", "\uD83D\uDE1A", "\uD83D\uDE4A", "\uD83D\uDE0C", "\uD83D\uDE00", "\uD83D\uDE0B",
                            "\uD83D\uDE06", "\uD83D\uDC4C", "\uD83D\uDE10", "\uD83D\uDE15"};
                    for (int i = 0; i < newRecent.length; i++) {
                        emojiUseHistory.put(newRecent[i], newRecent.length - i);
                    }
                    preferences.edit().putBoolean("filled_default", true).commit();
                    saveRecentEmoji();
                }
            }
            sortEmoji();
            adapters.get(0).notifyDataSetChanged();
        } catch (Exception e) {
            Log.e("tmessages", e.toString());
        }

        try {
            str = preferences.getString("color", "");
            if (str != null && str.length() > 0) {
                String[] args = str.split(",");
                for (int a = 0; a < args.length; a++) {
                    String arg = args[a];
                    String[] args2 = arg.split("=");
                    emojiColor.put(args2[0], args2[1]);
                }
            }
        } catch (Exception e) {
            Log.e("tmessages", e.toString());
        }
    }

    @Override
    public void requestLayout() {
        if (isLayout) {
            return;
        }
        super.requestLayout();
    }

    @Override
    public void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        isLayout = true;
        if (AndroidUtilities.isInMultiwindow) {
            if (currentBackgroundType != 1) {
                if (Build.VERSION.SDK_INT >= 21) {
                    setOutlineProvider((ViewOutlineProvider) outlineProvider);
                    setClipToOutline(true);
                    setElevation(AndroidUtilities.dp(2));
                }
                setBackgroundResource(R.drawable.smiles_popup);
                emojiTab.setBackgroundDrawable(null);
                currentBackgroundType = 1;
            }
        } else {
            if (currentBackgroundType != 0) {
                if (Build.VERSION.SDK_INT >= 21) {
                    setOutlineProvider(null);
                    setClipToOutline(false);
                    setElevation(0);
                }
                setBackgroundColor(0xfff5f6f7);
                emojiTab.setBackgroundColor(0xfff5f6f7);
                currentBackgroundType = 0;
            }
        }

        FrameLayout.LayoutParams layoutParams = (FrameLayout.LayoutParams) emojiTab.getLayoutParams();
        FrameLayout.LayoutParams layoutParams1 = null;
        layoutParams.width = View.MeasureSpec.getSize(widthMeasureSpec);
        if (layoutParams.width != oldWidth) {
            emojiTab.setLayoutParams(layoutParams);
            oldWidth = layoutParams.width;
        }
        super.onMeasure(View.MeasureSpec.makeMeasureSpec(layoutParams.width, MeasureSpec.EXACTLY), View.MeasureSpec.makeMeasureSpec(View.MeasureSpec.getSize(heightMeasureSpec), MeasureSpec.EXACTLY));
        isLayout = false;
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
    }

    public void setListener(Listener value) {
        listener = value;
    }

    public void invalidateViews() {
        for (int a = 0; a < emojiGrids.size(); a++) {
            emojiGrids.get(a).invalidateViews();
        }
    }

    @Override
    public void setVisibility(int visibility) {
        super.setVisibility(visibility);
        if (visibility != GONE) {
            sortEmoji();
            adapters.get(0).notifyDataSetChanged();
        }
    }

    public int getCurrentPage() {
        return currentPage;
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (pickerViewPopup != null && pickerViewPopup.isShowing()) {
            pickerViewPopup.dismiss();
        }
        Emoji.onEmojiLoadListener = null;
    }

    private class EmojiGridAdapter extends BaseAdapter {

        private int emojiPage;

        public EmojiGridAdapter(int page) {
            emojiPage = page;
        }

        @Override
        public Object getItem(int position) {
            return null;
        }

        @Override
        public int getCount() {
            if (emojiPage == -1) {
                return recentEmoji.size();
            }
            return EmojiData.dataColored[emojiPage].length;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View view, ViewGroup paramViewGroup) {
            ImageViewEmoji imageView = (ImageViewEmoji) view;
            if (imageView == null) {
                imageView = new ImageViewEmoji(getContext());
            }
            String code;
            String coloredCode;
            if (emojiPage == -1) {
                coloredCode = code = recentEmoji.get(position);
            } else {
                coloredCode = code = EmojiData.dataColored[emojiPage][position];
                String color = emojiColor.get(code);
                if (color != null) {
                    coloredCode = addColorToCode(coloredCode, color);
                }
            }
            imageView.setImageDrawable(Emoji.getEmojiBigDrawable(coloredCode));
            imageView.setTag(code);
            return imageView;
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            if (observer != null) {
                super.unregisterDataSetObserver(observer);
            }
        }
    }

    private class EmojiPagesAdapter extends PagerAdapter implements PagerSlidingTabStrip.IconTabProvider {

        public void destroyItem(ViewGroup viewGroup, int position, Object object) {
            View view = views.get(position);
            viewGroup.removeView(view);
        }

        public int getCount() {
            return views.size();
        }

        public int getPageIconResId(int paramInt) {
            return icons[paramInt];
        }

        @Override
        public void customOnDraw(Canvas canvas, int position) {
            invalidateViews();
        }

        public Object instantiateItem(ViewGroup viewGroup, int position) {
            View view = views.get(position);
            viewGroup.addView(view);
            return view;
        }

        public boolean isViewFromObject(View view, Object object) {
            return view == object;
        }

        @Override
        public void unregisterDataSetObserver(DataSetObserver observer) {
            if (observer != null) {
                super.unregisterDataSetObserver(observer);
            }
        }
    }
}