package com.holderzone.utils

import android.R
import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.Rect
import android.os.Build
import android.os.IBinder
import android.text.InputType
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.InputMethodManager
import android.widget.AbsListView
import android.widget.EditText
import android.widget.ScrollView
import java.lang.reflect.Method


/**
 * 键盘工具类
 * @author terry
 * @date 2018/08/20 下午9:16
 */
object HolderSoftKeyboardUtils {

    /**
     * Android定义了一个属性，名字为windowSoftInputMode, 这个属性用于设置Activity主窗口与软键盘的交互模式
     * 该属性可选的值有两部分，一部分为软键盘的状态控制，控制软键盘是隐藏还是显示，另一部分是Activity窗口的调整，以便腾出空间展示软键盘
     *
     * stateUnspecified-未指定状态：当我们没有设置android:windowSoftInputMode属性的时候，软件默认采用的就是这种交互方式，系统会根据界面采取相应的软键盘的显示模式。
     * stateUnchanged-不改变状态：当前界面的软键盘状态，取决于上一个界面的软键盘状态，无论是隐藏还是显示。
     * stateHidden-隐藏状态：当设置该状态时，软键盘总是被隐藏，不管是否有输入的需求。
     * stateAlwaysHidden-总是隐藏状态：当设置该状态时，软键盘总是被隐藏，和stateHidden不同的是，当我们跳转到下个界面，如果下个页面的软键盘是显示的，而我们再次回来的时候，软键盘就会隐藏起来。
     * stateVisible-可见状态：当设置为这个状态时，软键盘总是可见的，即使在界面上没有输入框的情况下也可以强制弹出来出来。
     * stateAlwaysVisible-总是显示状态：当设置为这个状态时，软键盘总是可见的，和stateVisible不同的是，当我们跳转到下个界面，如果下个页面软键盘是隐藏的，而我们再次回来的时候，软键盘就会显示出来。
     *
     * adjustUnspecified-未指定模式：设置软键盘与软件的显示内容之间的显示关系。当你跟我们没有设置这个值的时候，这个选项也是默认的设置模式。在这中情况下，系统会根据界面选择不同的模式。
     * adjustResize-调整模式：该模式下窗口总是调整屏幕的大小用以保证软键盘的显示空间；这个选项不能和adjustPan同时使用，如果这两个属性都没有被设置，系统会根据窗口中的布局自动选择其中一个。
     * adjustPan-默认模式：该模式下通过不会调整来保证软键盘的空间，而是采取了另外一种策略，系统会通过布局的移动，来保证用户要进行输入的输入框肯定在用户的视野范围里面，从而让用户可以看到自己输入的内容。
     */

    /**
     * 进入Activity后不希望系统自动弹出软键盘
     *
     * 方法一：
     * Android:windowSoftInputMode="adjustUnspecified|stateHidden"
     *
     * 方法二：
     * EditText edit = (EditText) findViewById(R.id.edit); edit.clearFocus();
     *
     * 方法三：
     * EditText edit = (EditText) findViewById(R.id.edit);
     * InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
     * imm.hideSoftInputFromWindow(edit.getWindowToken(),0);
     *
     * 方法四：
     * EditText edit = (EditText) findViewById(R.id.edit);
     * edit.setInputType(InputType.TYPE_NULL);
     */


    /**
     * 当软键盘已经显示， 调用该方法软键盘将隐藏
     * 当软键盘已经隐藏， 调用该方法软键盘将重新显示(两次调用需要有点延时，需当软键盘已经隐藏时才是这么一个特性)
     *
     * @param activity
     */
    fun hideSoftKeyboardByFlag(activity: Activity) {
        val imm = activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        imm?.toggleSoftInput(0, InputMethodManager.HIDE_NOT_ALWAYS)
    }

    /**
     * 隐藏Activity的虚拟导航栏，此方法为调用时候的
     * 注意:第二期正餐开始开放
     */
    @SuppressLint("ObsoleteSdkInt")
    fun hideNavKey(context: Context) {
        if (Build.VERSION.SDK_INT in 12..18) {
            val v = (context as Activity).window.decorView
            v.systemUiVisibility = View.GONE
        } else if (Build.VERSION.SDK_INT >= 19) {
            //for new api versions.
            val decorView = (context as Activity).window.decorView
            //透明化导航栏
            context.window.setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
            )
            //隐藏状态栏
            val uiOptions = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LOW_PROFILE)
            decorView.systemUiVisibility = uiOptions
        } else Unit
    }

    /**
     * 隐藏Dialog的虚拟导航栏的监听。虚拟按键的变更监听设置
     * 注意:第二期正餐开始开放
     */
    @SuppressLint("ObsoleteSdkInt")
    fun hideNavKeyDialog(dialog: Dialog) {
        //for new api versions.
        val decorView = dialog.window?.decorView
        //透明化导航栏
        dialog.window?.setFlags(
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
            WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
        )
        //隐藏状态栏
        val uiOptions = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                or View.SYSTEM_UI_FLAG_FULLSCREEN
                or View.SYSTEM_UI_FLAG_LOW_PROFILE)
        decorView?.systemUiVisibility = uiOptions
    }

    /**
     * 隐藏Activity的虚拟导航栏的监听。虚拟按键的变更监听设置
     * 注意:第二期正餐开始开放
     */
    @SuppressLint("ObsoleteSdkInt")
    fun setHideNavKeyChanagListener(act: Activity) {
        //绑定状态栏的监听
        act.window.decorView.setOnSystemUiVisibilityChangeListener {
            val decorView = act.window.decorView
            //透明化导航栏
            act.window.setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )
            val uiOptions = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LOW_PROFILE
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
            decorView.systemUiVisibility = uiOptions
        }
    }

    /**
     * 隐藏dialog的虚拟导航栏的监听。虚拟按键的变更监听设置
     * 注意:第二期正餐开始开放
     */
    @SuppressLint("ObsoleteSdkInt")
    fun setDialogHideNavKeyChanagListener(dialog: Dialog) {
        //绑定状态栏的监听
        dialog.window?.decorView?.setOnSystemUiVisibilityChangeListener {
            val decorView = dialog.window?.decorView
            //透明化导航栏
            dialog.window?.setFlags(
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION,
                WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION
            )
            val uiOptions = (View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_LOW_PROFILE)
            decorView?.systemUiVisibility = uiOptions
        }
    }

    /**
     * 显示虚拟导航栏,getWindow().getDecorView().getSystemUiVisibility() 或传入0也可一
     */
    fun showNavKey(context: Context, systemUiVisibility: Int) {
        (context as Activity).window.decorView.systemUiVisibility = systemUiVisibility
    }

    /**
     * 隐藏软键盘(只适用于Activity，不适用于Fragment，目前测试都适用)
     *
     * @param activity 当前Activity
     */
    fun hideSoftKeyboardByFocusView(activity: Activity) {
        val view = activity.currentFocus
        if (view != null) {
            val inputMethodManager =
                activity.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
            inputMethodManager?.hideSoftInputFromWindow(
                view.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
        }
    }

    /**
     * 隐藏软键盘(只适用于Activity，不适用于Fragment，目前测试都适用)
     *
     * @param activity 当前Activity
     */
    fun hideSoftKeyboardByDecorView(activity: Activity) {
        val inputMethodManager =
            activity.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager?
        inputMethodManager?.hideSoftInputFromWindow(activity.window.decorView.windowToken, 0)
    }

    /**
     * 隐藏软键盘(可用于Activity，Fragment)
     *
     * @param context
     * @param view    一般是传入EditText的引用
     */
    fun hideSoftKeyboardBySpecifiedView(context: Context, view: View?) {
        if (view == null) {
            return
        }
        val inputMethodManager =
            context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
        inputMethodManager?.hideSoftInputFromWindow(
            view.windowToken,
            InputMethodManager.HIDE_NOT_ALWAYS
        )
    }

    /**
     * 隐藏软键盘(可用于Activity，Fragment)
     *
     * @param context
     * @param viewList 一般是传入EditText的引用列表
     */
    fun hideSoftKeyboardBySpecifiedViews(context: Context, viewList: List<View>?) {
        if (viewList == null) {
            return
        }
        val inputMethodManager =
            context.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager?
        for (v in viewList) {
            inputMethodManager?.hideSoftInputFromWindow(
                v.windowToken,
                InputMethodManager.HIDE_NOT_ALWAYS
            )
        }
    }

    /**
     * 注册touchEvent事件，实现点击键盘区域外隐藏的效果
     *
     * @param activity
     * @param content
     */
    @SuppressLint("ClickableViewAccessibility")
    fun registerTouchEvent(activity: Activity, content: ViewGroup? = null) {
        var viewGroup = content
        if (viewGroup == null) {
            viewGroup = activity.findViewById<View>(R.id.content) as ViewGroup
        }
        getScrollView(viewGroup, activity)
        viewGroup.setOnTouchListener { _, motionEvent ->
            dispatchTouchEvent(activity, motionEvent)
            false
        }
    }

    /**
     * @param viewGroup
     * @param activity
     */
    @SuppressLint("ClickableViewAccessibility")
    private fun getScrollView(viewGroup: ViewGroup?, activity: Activity) {
        if (null == viewGroup) {
            return
        }
        val count = viewGroup.childCount
        for (i in 0 until count) {
            checkViewListener(viewGroup, i, activity)

        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun checkViewListener(
        viewGroup: ViewGroup,
        i: Int,
        activity: Activity
    ) {
        when (val view = viewGroup.getChildAt(i)) {
            is ScrollView, is AbsListView -> view.setOnTouchListener { _, motionEvent ->
                dispatchTouchEvent(activity, motionEvent)
                false
            }

            is ViewGroup -> getScrollView(view, activity)
            else -> Unit
        }
    }


    /**
     * @param dialog
     * @param ev
     * @return
     */
    fun dispatchTouchEvent(dialog: Activity, ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val v = dialog.currentFocus
            if (null != v && isShouldHideInput(v, ev)) {
                hideSoftInput(dialog.applicationContext, v.windowToken)
            }
        }
        return false
    }

    /**
     * @param dialog
     * @param ev
     * @return
     */
    fun dispatchTouchEvent(dialog: Dialog, ev: MotionEvent): Boolean {
        if (ev.action == MotionEvent.ACTION_DOWN) {
            val v = dialog.currentFocus
            if (null != v && HolderSoftKeyboardUtils.isShouldHideInput(
                    v,
                    ev
                )
            ) {
                HolderSoftKeyboardUtils.hideSoftInput(
                    dialog.context,
                    v.windowToken
                )
            }
        }
        return false
    }


    /**
     * @param v
     * @param event
     * @return
     */
    private fun isShouldHideInput(v: View, event: MotionEvent): Boolean {
        if (v is EditText) {
            val rect = Rect()
            v.getGlobalVisibleRect(rect)
            if (rect.contains(event.x.toInt(), event.y.toInt())) {
                return false
            }
        }
        return true
    }

    /**
     * @param context
     * @param token
     */
    private fun hideSoftInput(context: Context, token: IBinder?) {
        if (token != null) {
            val im = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            im.hideSoftInputFromWindow(token, InputMethodManager.HIDE_NOT_ALWAYS)
        }
    }

    fun isSoftInputShowing(context: Context): Boolean {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        return imm.isActive
    }

    /**
     * 不允许键盘弹出
     */
    fun disableShowSoftInput(editText: EditText) {
        if (Build.VERSION.SDK_INT <= 10) {
            editText.inputType = InputType.TYPE_NULL
        } else {
            val cls = EditText::class.java
            var method: Method
            try {
                method = cls.getMethod("setShowSoftInputOnFocus", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(editText, false)
            } catch (e: Exception) {
                e.printStackTrace()
            }

            try {
                method = cls.getMethod("setSoftInputShownOnFocus", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(editText, false)
            } catch (e: Exception) {
                e.printStackTrace()
            }

        }
    }
}
