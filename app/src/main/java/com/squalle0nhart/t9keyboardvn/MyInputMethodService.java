package com.squalle0nhart.t9keyboardvn;

import android.inputmethodservice.InputMethodService;
import android.inputmethodservice.Keyboard;
import android.inputmethodservice.KeyboardView;
import android.os.Handler;
import android.text.InputType;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputConnection;

public class MyInputMethodService extends InputMethodService implements KeyboardView.OnKeyboardActionListener {

    private KeyboardView keyboardView;
    private Keyboard keyboard;

    public static final int MODE_LANG = 0;
    public static final int MODE_TEXT = 1;
    public static final int MODE_NUM = 2;
    private static final int[] MODE_CYCLE = { MODE_LANG, MODE_TEXT, MODE_NUM };
    private int mKeyMode;

    private int mCapsMode;
    private static final int CAPS_OFF = 0;
    private static final int CAPS_SINGLE = 1;
    private static final int CAPS_ALL = 2;
    private final static int[] CAPS_CYCLE = { CAPS_OFF, CAPS_SINGLE, CAPS_ALL };

    private int mPrevious;
    private int mCharIndex;

    private StringBuilder mComposing = new StringBuilder();
    private StringBuilder mComposingI = new StringBuilder();

    private final static int T9DELAY = 900;
    final Handler t9releasehandler = new Handler();
    Runnable mt9release = new Runnable() {
        @Override
        public void run() {
            commitReset();
        }
    };



    private InputConnection currentInputConnection = null;


    public MyInputMethodService() {
        super();
    }

    @Override
    public View onCreateInputView() {
        keyboardView = (KeyboardView) getLayoutInflater().inflate(R.layout.keyboard_view, null);
        keyboard = new Keyboard(this, R.xml.t9);
        keyboardView.setKeyboard(keyboard);
        keyboardView.setOnKeyboardActionListener(this);
        return keyboardView;
    }

    @Override
    public void onStartInput(EditorInfo attribute, boolean restarting) {
        super.onStartInput(attribute, restarting);
        currentInputConnection = getCurrentInputConnection();
        mKeyMode = MODE_TEXT;

        switch (attribute.inputType & InputType.TYPE_MASK_CLASS) {
            case InputType.TYPE_CLASS_NUMBER:
            case InputType.TYPE_CLASS_DATETIME:
                // Numbers and dates default to the symbols keyboard, with
                // no extra features.
                mKeyMode = MODE_NUM;
                showStatusIcon(R.drawable.ime_number);
                break;

            case InputType.TYPE_CLASS_PHONE:
                // Phones will also default to the symbols keyboard, though
                // often you will want to have a dedicated phone keyboard.
                mKeyMode = MODE_NUM;
                showStatusIcon(R.drawable.ime_number);
                break;

            case InputType.TYPE_CLASS_TEXT:
                // This is general text editing. We will default to the
                // normal alphabetic keyboard, and assume that we should
                // be doing predictive text (showing candidates as the
                // user types).
                mKeyMode = MODE_TEXT;

                // We now look for a few special variations of text that will
                // modify our behavior.
                int variation = attribute.inputType & InputType.TYPE_MASK_VARIATION;
                if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD
                        || variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD) {
                    // Do not display predictions / what the user is typing
                    // when they are entering a password.
                    mKeyMode = MODE_TEXT;
                }
                showStatusIcon(R.drawable.ime_en_text_lower);


                // We also want to look at the current state of the editor
                // to decide whether our alphabetic keyboard should start out
                // shifted.
                updateShiftKeyState(attribute);
                break;

            default:
                // For all unknown input types, default to the alphabetic
                // keyboard with no special features.
                updateShiftKeyState(attribute);
        }
    }



    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return true;
    }

    @Override
    public boolean onKeyLongPress(int keyCode, KeyEvent event) {
        return super.onKeyLongPress(keyCode, event);
    }

    @Override
    public void onPress(int i) {

    }

    @Override
    public void onRelease(int i) {

    }

    @Override
    public void onKey(int keyCode, int[] keyCodes) {
        Log.e("", "keycode: " + keyCode);
        if (keyCode == KeyEvent.KEYCODE_DEL) {
            CharSequence selectedText = currentInputConnection.getSelectedText(0);

            if (TextUtils.isEmpty(selectedText)) {
                currentInputConnection.deleteSurroundingText(1, 0);
            } else {
                currentInputConnection.commitText("", 1);
            }
        } else if (keyCode == KeyEvent.KEYCODE_STAR) {
            // change case
            if (mKeyMode == MODE_NUM) {
                handleCharacter(KeyEvent.KEYCODE_STAR);
            } else {
                if (mCapsMode == CAPS_CYCLE.length - 1) {
                    mCapsMode = 0;
                    showStatusIcon(R.drawable.ime_en_text_lower);
                } else {
                    mCapsMode++;
                    showStatusIcon(R.drawable.ime_en_text_upper);
                }
            }
        } else if (keyCode == KeyEvent.KEYCODE_POUND) {
            // space
            handleCharacter(KeyEvent.KEYCODE_POUND);
        } else {
            if (keyCode >= KeyEvent.KEYCODE_0 && keyCode <= KeyEvent.KEYCODE_9) {
                handleCharacter(keyCode);
            } else {
                Log.e("onKey", "This shouldn't happen, unknown key");
            }
        }
    }

    @Override
    public void onText(CharSequence charSequence) {
        if (currentInputConnection == null)
            return;
        currentInputConnection.beginBatchEdit();
        if (mComposing.length() > 0 || mComposingI.length() > 0) {
            commitTyped();
        }
        currentInputConnection.commitText(charSequence, 1);
        currentInputConnection.endBatchEdit();
        updateShiftKeyState(getCurrentInputEditorInfo());
    }

    @Override
    public void swipeLeft() {

    }

    @Override
    public void swipeRight() {

    }

    @Override
    public void swipeDown() {

    }

    @Override
    public void swipeUp() {

    }

    private  void handleCharacter(int keyCode) {
        Log.e("" + mKeyMode, "Keycode:" + keyCode);
        switch (mKeyMode) {
            case MODE_TEXT:
                t9releasehandler.removeCallbacks(mt9release);
                if (keyCode == KeyEvent.KEYCODE_POUND) {
                    keyCode = 10;
                } else {
                    keyCode = keyCode - KeyEvent.KEYCODE_0;
                }

                boolean newChar = false;
                if (mPrevious == keyCode) {
                    mCharIndex++;
                } else {
                    //Log.d("handleChar", "COMMITING:" + mComposing.toString());
                    commitTyped();
                    // updateShiftKeyState(getCurrentInputEditorInfo());
                    newChar = true;
                    mCharIndex = 0;
                    mPrevious = keyCode;
                }

                // start at caps if CapMode
                // Log.d("handleChar", "Cm: " + mCapsMode);
                if (mCharIndex == 0 && mCapsMode != CAPS_OFF) {
                    mCharIndex = CharMap.T9CAPSTART[0][keyCode];
                }

                mComposing.setLength(0);
                mComposingI.setLength(0);
                char[] ca = CharMap.T9TABLE[0][keyCode];
                if (mCharIndex >= ca.length) {
                    mCharIndex = 0;
                }
                //Log.d("handleChar", "Index: " + mCharIndex);
                mComposing.append(ca[mCharIndex]);
                //Log.d("handleChar", "settingCompose: " + mComposing.toString());
                currentInputConnection.setComposingText(mComposing, 1);

                t9releasehandler.postDelayed(mt9release, T9DELAY);
                if (newChar) {
                    // consume single caps
                    if (mCapsMode == CAPS_SINGLE) {
                        mCapsMode = CAPS_OFF;
                    }
                }

            case MODE_NUM:
                if (keyCode == KeyEvent.KEYCODE_POUND) {
                    onText("#");
                } else if (keyCode == KeyEvent.KEYCODE_STAR) {
                    onText("*");
                } else {
                    onText(String.valueOf(keyCode - KeyEvent.KEYCODE_0));
                }
                break;
        }
    }


    /**
     * Helper to update the shift state of our keyboard based on the initial
     * editor state.
     */
    private void updateShiftKeyState(EditorInfo attr) {
        if (attr != null && mCapsMode != CAPS_ALL) {
            int caps = 0;
            if (attr.inputType != InputType.TYPE_NULL) {
                caps = currentInputConnection.getCursorCapsMode(attr.inputType);
            }
            if ((caps & TextUtils.CAP_MODE_CHARACTERS) == TextUtils.CAP_MODE_CHARACTERS) {
                mCapsMode = CAPS_ALL;
                showStatusIcon(R.drawable.ime_en_text_upper);
            } else if ((caps & TextUtils.CAP_MODE_SENTENCES) == TextUtils.CAP_MODE_SENTENCES) {
                mCapsMode = CAPS_SINGLE;
                showStatusIcon(R.drawable.ime_en_text_upper);
            } else if ((caps & TextUtils.CAP_MODE_WORDS) == TextUtils.CAP_MODE_WORDS) {
                mCapsMode = CAPS_SINGLE;
                showStatusIcon(R.drawable.ime_en_text_upper);
            } else {
                showStatusIcon(R.drawable.ime_en_text_lower);
                mCapsMode = CAPS_OFF;
            }
        }
    }

    private void commitReset() {
        commitTyped();
        charReset();
        if (mCapsMode == CAPS_SINGLE) {
            mCapsMode = CAPS_OFF;
        }
        // Log.d("commitReset", "CM pre: " + mCapsMode);
        updateShiftKeyState(getCurrentInputEditorInfo());
        // Log.d("commitReset", "CM post: " + mCapsMode);
    }

    private void charReset() {
        t9releasehandler.removeCallbacks(mt9release);
        mPrevious = -1;
        mCharIndex = 0;
    }

    private void commitTyped() {
        clearState();
    }

    private void clearState() {
        mComposing.setLength(0);
        mComposingI.setLength(0);
    }

}
