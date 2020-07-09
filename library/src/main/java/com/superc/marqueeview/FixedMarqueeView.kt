package com.superc.marqueeview

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.text.TextUtils
import android.text.TextUtils.TruncateAt
import android.util.AttributeSet
import android.util.Log
import android.util.LruCache
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.View.OnClickListener
import android.view.ViewGroup
import android.view.animation.Animation
import android.view.animation.Animation.AnimationListener
import android.view.animation.LinearInterpolator
import android.view.animation.TranslateAnimation
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.ColorInt
import java.util.*
import kotlin.collections.ArrayList

/**
 * Copyright (C), 2020, 重庆八进制有限公司

 * @author: SuperChen
 * @last-modifier: SuperChen
 * @version: 1.0
 * @create-date:  2020/7/8 16:37
 * @last-modify-date:  2020/7/8 16:37
 * @description:
 */
open class FixedMarqueeView<T : View, E> : FrameLayout, Observer {

    companion object {
        val LOGS_ENABLED = true
        val COMMAND_UPDATE_DATA = "UPDATE_DATA"
        //默认的滑入动画时间
        private val DEFAULT_ANI_IN_TIME: Long = 7000
        //默认的child滑倒parent的最左边时停顿的时间
        private val DEFAULT_PAUSE_TIME: Long = 1500
        //默认的下一个child提前进入的时间差，当childCount = 1时无效
        private val DEFAULT_FLIPING_INTERVAL_DIFFER_TIME: Long = 100
    }

    constructor(context: Context) : super(context)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        setOnClickListener(onClickListener)
    }

    //当前执行动画的child的index
    private var mWhichChild = 0
    //上一个执行动画的child的index
    private val mLastWhichChild = 0
    //是否是第一次的标志
    private var mFirstTime = true
    //动画是否初始化标志
    private var mAniIsInit = false
    //是否在下一个动作显示滑出的动画标志
    private var mShowOutAnimationAction = false
    //是否正在动画的标志
    private var mRunning = false
    //是否开始动画的标志
    private var mStarted = false
    //是否可见的标志
    private var mVisible = false
    private var mUserPresent = true

    //进入动画的cache
    private var mInAniCache: LruCache<Int, Animation>? = null
    //出去动画的cache
    private var mOutAniCache: LruCache<Int, Animation>? = null

    //下一个child提前进入的时间差，当childCount = 1时无效
//    private long mNextChildAniInIntervalDifferTime;
    //child滑入的动画时间
    private var mAniInTime = DEFAULT_ANI_IN_TIME
    //child滑倒parent的最左边时停顿的时间
    private var mAfterInAniPauseTime = DEFAULT_PAUSE_TIME
    //是否自动开始
    private var mAutoStart = false
    //第一次是否启用动画
    private var mAnimateFirstTime = true

    protected var factory: MarqueeFactory<T, E>? = null

    private var onItemClickListener: MarqueeOnItemClickListener<T, E>? = null

    private var isJustOnceFlag = true

    private val mFlipRunnable = Runnable {
        if (mRunning) {
            log("mFlipRunnable ===>  , mShowOutAnimationAction = $mShowOutAnimationAction")
            if (mShowOutAnimationAction) {
                showOut()
            } else {
                showIn()
            }
        }
    }

    private val mReceiver: BroadcastReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            if (Intent.ACTION_SCREEN_OFF == action) {
                mUserPresent = false
                updateRunning()
            } else if (Intent.ACTION_USER_PRESENT == action) {
                mUserPresent = true
                updateRunning(false)
            }
        }
    }

    private val onClickListener =
        OnClickListener {
            if (onItemClickListener != null) {
                if (factory == null || factory?.getData() == null || factory?.getData()?.size == 0 || childCount == 0) {
                    return@OnClickListener
                }
                val mData = factory?.getData()?.get(mWhichChild) ?: return@OnClickListener
                val view = getCurrentDisplayView()
                view?.let {
                    onItemClickListener?.onItemClickListener(
                        view as T,
                        mData,
                        mWhichChild
                    )
                }
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val width = MeasureSpec.getSize(widthMeasureSpec)
        val height = MeasureSpec.getSize(heightMeasureSpec)
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            measureChildWithMargins(child, widthMeasureSpec, 0, heightMeasureSpec, 0)
            val lp = child.layoutParams as MarginLayoutParams
            child.measure(
                MeasureSpec.makeMeasureSpec(width, MeasureSpec.UNSPECIFIED),
                heightMeasureSpec
            )
        }
        setMeasuredDimension(width, height)
    }

    override fun addView(
        child: View,
        index: Int,
        params: ViewGroup.LayoutParams?
    ) {
        super.addView(child, index, params)
        if (childCount == 1) {
            child.visibility = View.VISIBLE
        } else {
            child.visibility = View.GONE
        }
        if (index in 0..mWhichChild) {
            showIn()
        }
    }

    override fun removeAllViews() {
        super.removeAllViews()
        mWhichChild = 0
        mFirstTime = true
    }

    override fun removeView(view: View?) {
        val index = indexOfChild(view)
        if (index >= 0) {
            removeViewAt(index)
        }
    }

    override fun removeViewAt(index: Int) {
        super.removeViewAt(index)
        val childCount = childCount
        when {
            childCount == 0 -> {
                mWhichChild = 0
                mFirstTime = true
            }
            mWhichChild >= childCount -> {
                mWhichChild = childCount - 1
                showIn()
            }
            mWhichChild == index -> {
                showOut()
            }
        }
    }

    override fun removeViewInLayout(view: View?) {
        removeView(view)
    }

    override fun removeViews(start: Int, count: Int) {
        super.removeViews(start, count)
        if (childCount == 0) {
            mWhichChild = 0
            mFirstTime = true
        } else if (mWhichChild >= start && mWhichChild < start + count) {
            showOut()
        }
    }

    override fun removeViewsInLayout(start: Int, count: Int) {
        removeViews(start, count)
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_SCREEN_OFF)
        filter.addAction(Intent.ACTION_USER_PRESENT)
        context.registerReceiver(mReceiver, filter, null, handler)
        if (mAutoStart) {
            startFlipping()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        mVisible = false
        context.unregisterReceiver(mReceiver)
        updateRunning()
    }

    override fun onWindowVisibilityChanged(visibility: Int) {
        super.onWindowVisibilityChanged(visibility)
        mVisible = visibility == View.VISIBLE
        log("onWindowVisibilityChanged ===>  , mVisible = $mVisible")
        updateRunning(true)
    }

    override fun setOnClickListener(l: OnClickListener?) {
        isJustOnceFlag = if (isJustOnceFlag) {
            super.setOnClickListener(l)
            false
        } else {
            throw UnsupportedOperationException("The setOnClickListener method is not supported,please use setOnItemClickListener method.")
        }
    }

    override fun update(o: Observable?, arg: Any?) {
        if (arg == null) return
        if (COMMAND_UPDATE_DATA == arg.toString()) {
            if (mInAniCache != null) {
                val animation = mInAniCache?.get(mWhichChild)
                if (animation != null && animation.hasStarted()) {
                    animation.setAnimationListener(object : AnimationListener {
                        override fun onAnimationStart(animation: Animation) {}
                        override fun onAnimationEnd(animation: Animation) {
                            refreshChildViews()
                            animation.setAnimationListener(null)
                        }

                        override fun onAnimationRepeat(animation: Animation) {}
                    })
                } else {
                    refreshChildViews()
                }
            }
        }
    }

    private fun initAniCache() {
        if (childCount > 0 && !mAniIsInit) {
            mInAniCache = LruCache(childCount)
            mOutAniCache = LruCache(childCount)
            val mLinearInterpolator = LinearInterpolator()
            var inAni: TranslateAnimation
            var outAni: TranslateAnimation
            var previousDuration: Long = 0
            for (i in 0 until childCount) {
                val child = getChildAt(i)
                inAni = TranslateAnimation(right.toFloat(), 0f, 0f, 0f)
                inAni.interpolator = mLinearInterpolator
                inAni.duration = mAniInTime
                mInAniCache?.put(i, inAni)
                outAni = TranslateAnimation(0f, (-child.measuredWidth).toFloat(), 0f, 0f)
                outAni.interpolator = mLinearInterpolator
                previousDuration =
                    (child.measuredWidth.toFloat() / measuredWidth * mAniInTime).toLong()
                outAni.duration = previousDuration
                mOutAniCache?.put(i, outAni)
                log("initAniCache ===> " + " , i = " + i + " , outAni.duration = " + outAni.duration)
            }
            mAniIsInit = true
        }
    }

    protected open fun refreshChildViews() {
        if (childCount > 0) {
            removeAllViews()
        }
        val mViews: MutableList<T>? = factory?.getMarqueeViews()
        if (mViews != null) {
            for (i in mViews.indices) {
                val child: View = mViews[i]
                addView(child)
                child.visibility = View.GONE
            }
        }
        if (mViews?.isEmpty() == true) {
            stopFlipping()
        }
        mAniIsInit = false
    }

    open fun showIn() {
        log("showIn ===> ")
        showByWhichChild(mWhichChild + 1, true)
    }

    fun showOut() {
        log("showOut ===> ")
        showByWhichChild(mWhichChild, false)
    }

    private fun showByWhichChild(
        whichChild: Int,
        showInAnimationAction: Boolean
    ) {
        mWhichChild = whichChild
        if (whichChild >= childCount) {
            mWhichChild = 0
        } else if (whichChild < 0) {
            mWhichChild = childCount - 1
        }
        val hasFocus = focusedChild != null
        showOnly(mWhichChild, showInAnimationAction)
        if (hasFocus) {
            requestFocus(View.FOCUS_FORWARD)
        }
    }

    private fun showOnly(childIndex: Int, showInAnimationAction: Boolean) {
        log("showOnly 2 ===>  , childIndex = $childIndex , showInAnimationAction = $showInAnimationAction , mFirstTime = $mFirstTime , mAnimateFirstTime = $mAnimateFirstTime")
        val animate = !mFirstTime || mAnimateFirstTime
        showOnly(childIndex, animate, showInAnimationAction)
    }

    private fun showOnly(
        childIndex: Int,
        animate: Boolean,
        showInAnimationAction: Boolean
    ) {
        initAniCache()
        if (!mAniIsInit) {
            return
        }
        if (mInAniCache == null || mOutAniCache == null) return
        val count = childCount
        if (count == 0) return
        for (i in 0 until count) {
            val child = getChildAt(i)
            child?.clearAnimation()
        }
        log("showOnly 3 ===>  , childIndex = $childIndex , showInAnimationAction = $showInAnimationAction , animate = $animate")
        val child = getChildAt(childIndex)
        if (child != null) {
            if (showInAnimationAction) {
                if (animate) {
                    child.startAnimation(mInAniCache?.get(childIndex))
                    mShowOutAnimationAction = true
                    log("showOnly 3 ===>  , inAni = $showInAnimationAction , InDuration = $mAniInTime$mAfterInAniPauseTime")
                    postDelayed(mFlipRunnable, mAniInTime + mAfterInAniPauseTime)
                }
                child.visibility = View.VISIBLE
                mFirstTime = false
            } else {
                if (animate) {
                    child.startAnimation(mOutAniCache?.get(childIndex))
                    mShowOutAnimationAction = false
                    //                    postDelayed(mFlipRunnable, mOutAniCache.get(childIndex).getDuration() - (count == 1 ? 0 : mNextChildAniInIntervalDifferTime));
                    log(
                        "showOnly 3 ===>  , outAni = $showInAnimationAction , OutDuration = " + mOutAniCache?.get(
                            childIndex
                        )?.duration
                    )
                    postDelayed(
                        mFlipRunnable,
                        mOutAniCache?.get(childIndex)?.duration ?: DEFAULT_ANI_IN_TIME
                    )
                }
                child.visibility = View.GONE
            }
        }
    }

    private fun updateRunning() {
        updateRunning(true)
    }

    private fun updateRunning(flipNow: Boolean) {
        val running = mVisible && mStarted && mUserPresent
        log("updateRunning ===>  , mRunning = $mRunning , running = $running , mVisible = $mVisible , mStarted = $mStarted , mUserPresent = $mUserPresent , flipNow = $flipNow , getChildCount() = $childCount")
        if (running != mRunning) {
            if (running) {
                log("updateRunning ===> running ")
                showOnly(mWhichChild, flipNow, true)
            } else {
                log("updateRunning ===> removeCallbacks ")
                removeCallbacks(mFlipRunnable)
            }
            mRunning = running
        }
    }

    public open fun setMarqueeFactory(factory: MarqueeFactory<T, E>) {
        this.factory = factory
        factory.attachedToMarqueeView(this)
        refreshChildViews()
    }

    fun startFlipping() {
        log("startFlipping ===> ")
        mStarted = true
        updateRunning()
    }

    fun stopFlipping() {
        log("stopFlipping ===> ")
        mStarted = false
        updateRunning()
    }

    fun getCurrentDisplayView(): View? {
        return getChildAt(mWhichChild)
    }

    /**
     * 设置第一次是否启用动画
     */
    fun setAnimateFirstTime(animateFirstTime: Boolean) {
        mAnimateFirstTime = animateFirstTime
    }

    /**
     * 设置是否自动开始动画
     */
    fun setAutoStart(autoStart: Boolean) {
        mAutoStart = autoStart
    }

    /**
     * 设置动画进入时间
     */
    fun setAniInTime(aniInTime: Long) {
        mAniInTime = aniInTime
    }

//    /**
//     * 默认的动画情况是上一个child滑出屏幕后，下一个child才会滑进屏幕，设置此值>0 就表示下一个child可以提前进入 默认=0，当childCount = 1时无效
//     */
//    public void setNextChildAniInIntervalDifferTime(long nextChildAniInIntervalDifferTime) {
//        this.mNextChildAniInIntervalDifferTime = nextChildAniInIntervalDifferTime;
//    }

    //    /**
//     * 默认的动画情况是上一个child滑出屏幕后，下一个child才会滑进屏幕，设置此值>0 就表示下一个child可以提前进入 默认=0，当childCount = 1时无效
//     */
//    public void setNextChildAniInIntervalDifferTime(long nextChildAniInIntervalDifferTime) {
//        this.mNextChildAniInIntervalDifferTime = nextChildAniInIntervalDifferTime;
//    }
    /**
     * 设置child滑倒parent的最左边时停顿的时间
     */
    fun setAfterInAniPauseTime(afterInAniPauseTime: Long) {
        mAfterInAniPauseTime = afterInAniPauseTime
    }

    fun setOnItemClickListener(mOnItemClickListener: MarqueeOnItemClickListener<T, E>?) {
        onItemClickListener = mOnItemClickListener
    }

    fun canMarquee(): Boolean {
        return factory != null && factory!!.canMarquee()
    }

    override fun getBaseline(): Int {
        return getCurrentDisplayView()?.baseline ?: super.getBaseline()
    }

    private fun log(message: String) {
        if (LOGS_ENABLED) {
            Log.d(javaClass.simpleName, message)
        }
    }

}


open class SimpleMarqueeView<E> : FixedMarqueeView<TextView, E> {

    private var smvTextColor: ColorStateList? = null
    private var smvTextSize = 15f
    private var smvTextGravity = Gravity.NO_GRAVITY
    private var smvTextSingleLine = false
    private var smvTextEllipsize: TextUtils.TruncateAt? = null

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        var ellipsize = -1
        var maxLines = 1
        attrs?.let {
            val typeArray =
                context.obtainStyledAttributes(attrs, R.styleable.SimpleMarqueeView, 0, 0)
            smvTextColor = typeArray.getColorStateList(R.styleable.SimpleMarqueeView_smvTextColor)
            if (typeArray.hasValue(R.styleable.SimpleMarqueeView_smvTextSize)) {
                smvTextSize =
                    typeArray.getDimension(R.styleable.SimpleMarqueeView_smvTextSize, smvTextSize)
            }
            smvTextGravity =
                typeArray.getInt(R.styleable.SimpleMarqueeView_smvTextGravity, smvTextGravity)
            smvTextSingleLine =
                typeArray.getBoolean(
                    R.styleable.SimpleMarqueeView_smvTextSingleLine,
                    smvTextSingleLine
                )
            ellipsize = typeArray.getInt(R.styleable.SimpleMarqueeView_smvTextEllipsize, ellipsize)
            maxLines = typeArray.getInt(R.styleable.SimpleMarqueeView_maxLines, maxLines)
            typeArray.recycle()
        }
        if (smvTextSingleLine && ellipsize < 0 && maxLines == 1) {
            ellipsize = 3
        }
        when (ellipsize) {
            1 -> smvTextEllipsize = TextUtils.TruncateAt.START
            2 -> smvTextEllipsize = TextUtils.TruncateAt.MIDDLE
            3 -> smvTextEllipsize = TextUtils.TruncateAt.END
        }
    }

    override fun refreshChildViews() {
        super.refreshChildViews()
        val views: MutableList<TextView>? = factory?.getMarqueeViews()
        views?.let {
            for (v in views) {
                v.setTextSize(TypedValue.COMPLEX_UNIT_PX, smvTextSize)
                v.gravity = smvTextGravity
                smvTextColor?.let { v.setTextColor(it) }
                v.isSingleLine = smvTextSingleLine
                v.ellipsize = smvTextEllipsize
            }
        }
    }

    open fun setTextSize(textSize: Float, isSpValue: Boolean) {
        this.smvTextSize = textSize
        factory?.let {
            factory?.getMarqueeViews()?.let {
                for (v in it) {
                    if (isSpValue)
                        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, smvTextSize)
                    v.textSize = smvTextSize
                }
            }
        }
    }

    open fun setTextColor(@ColorInt textColor: Int) {
        setTextColor(ColorStateList.valueOf(textColor))
    }

    open fun setTextColor(textColorStateList: ColorStateList) {
        this.smvTextColor = textColorStateList
        factory?.let {
            factory?.getMarqueeViews()?.let {
                for (v in it) {
                    v.setTextColor(smvTextColor)
                }
            }
        }
    }

    open fun setTextGravity(gravity: Int) {
        smvTextGravity = gravity
        factory?.let {
            factory?.getMarqueeViews()?.let {
                for (v in it) {
                    v.gravity = smvTextGravity
                }
            }
        }
    }

    open fun setTextSingleLine(singleLine: Boolean) {
        smvTextSingleLine = singleLine
        factory?.let {
            factory?.getMarqueeViews()?.let {
                for (v in it) {
                    v.isSingleLine = smvTextSingleLine
                }
            }
        }
    }

    open fun setTextEllipsize(where: TruncateAt) {
        if (where == TruncateAt.MARQUEE) {
            throw java.lang.UnsupportedOperationException("The type MARQUEE is not supported!")
        }
        smvTextEllipsize = where
        factory?.let {
            factory?.getMarqueeViews()?.let {
                for (v in it) {
                    v.ellipsize = smvTextEllipsize
                }
            }
        }
    }
}


open abstract class MarqueeFactory<T : View, E>(protected var mContext: Context) : Observable() {

    protected var mViews: MutableList<T> = ArrayList()
    protected var mDataList: MutableList<E> = ArrayList()
    protected var mObserver: Observer? = null

    abstract fun generateMarqueeItemView(data: E): T

    open fun getMarqueeViews(): MutableList<T> {
        return mViews
    }

    open fun setData(dataList: MutableList<E>?) {
        mDataList = dataList ?: ArrayList()
        for (i in mDataList.indices) {
            val data = mDataList[i]
            val mView = generateMarqueeItemView(data)
            mViews.add(mView)
        }
        notifyDataChanged()
    }

    open fun getData(): MutableList<E>? {
        return mDataList
    }

    private fun isAttachedToMarqueeView(): Boolean {
        return mObserver != null
    }

    open fun attachedToMarqueeView(observer: Observer?) {
        if (!isAttachedToMarqueeView()) {
            mObserver = observer!!
            addObserver(observer)
            return
        }
        throw IllegalStateException(
            String.format(
                "The %s has been attached to the %s!",
                toString(),
                mObserver.toString()
            )
        )
    }

    private fun notifyDataChanged() {
        if (isAttachedToMarqueeView()) {
            setChanged()
            notifyObservers(FixedMarqueeView.COMMAND_UPDATE_DATA)
        }
    }

    open fun canMarquee(): Boolean {
        return mDataList.size > 0 && mViews.size > 0 && mDataList.size == mViews.size
    }
}


open class SimpleMF<E : CharSequence>(mContext: Context) : MarqueeFactory<TextView, E>(mContext) {
    override fun generateMarqueeItemView(data: E): TextView {
        val mView = TextView(mContext)
        mView.text = data
        return mView
    }
}


open interface MarqueeOnItemClickListener<V : View, E> {
    fun onItemClickListener(mView: V, mData: E, mPosition: Int)
}