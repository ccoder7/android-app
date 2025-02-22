package one.mixin.android.widget

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.util.AttributeSet
import android.util.SparseArray
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.animation.AlphaAnimation
import android.view.animation.AnimationSet
import android.view.animation.OvershootInterpolator
import android.view.animation.TranslateAnimation
import android.widget.LinearLayout
import android.widget.TextView
import kotlinx.android.synthetic.main.layout_pin.view.*
import one.mixin.android.R
import one.mixin.android.extension.colorFromAttribute
import org.jetbrains.anko.dip
import org.jetbrains.anko.hintTextColor
import org.jetbrains.anko.textColor

class PinView : LinearLayout {

    companion object {
        const val DEFAULT_COUNT = 6
        const val STAR = "*"
    }

    private var color = context.colorFromAttribute(R.attr.text_primary)
    private var count = DEFAULT_COUNT
    // control tip_tv and line visibility
    private var tipVisible = true

    private val views = ArrayList<TextView>()
    private val codes = SparseArray<String>()
    private var mid = count / 2
    private var index = 0
    private var listener: OnPinListener? = null
    private val textSize = 26f
    private val starSize = 18f

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : this(context, attrs, 0)
    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) : super(
        context,
        attrs,
        defStyleAttr
    ) {
        LayoutInflater.from(context).inflate(R.layout.layout_pin, this, true) as LinearLayout
        val ta = context.obtainStyledAttributes(attrs, R.styleable.PinView)
        if (ta.hasValue(R.styleable.PinView_pin_color)) {
            color = ta.getColor(R.styleable.PinView_pin_color, Color.BLACK)
        }
        if (ta.hasValue(R.styleable.PinView_pin_count)) {
            count = ta.getInt(R.styleable.PinView_pin_count, DEFAULT_COUNT)
        }
        if (ta.hasValue(R.styleable.PinView_pin_tipVisible)) {
            tipVisible = ta.getBoolean(R.styleable.PinView_pin_tipVisible, true)
            if (!tipVisible) {
                tip_tv.visibility = View.GONE
                line.visibility = View.GONE
            }
        }
        ta.recycle()
        orientation = VERTICAL
        mid = count / 2
        for (i in 0..count) {
            if (i == mid) {
                container_ll.addView(View(context), LayoutParams(context.dip(20), MATCH_PARENT))
            } else {
                val item = TextView(context)
                item.textSize = starSize
                item.textColor = color
                item.typeface = Typeface.DEFAULT_BOLD
                item.hintTextColor = context.colorFromAttribute(R.attr.text_minor)
                item.hint = STAR
                item.gravity = Gravity.CENTER

                val params = LayoutParams(0, MATCH_PARENT)
                params.weight = 1f
                params.gravity = Gravity.BOTTOM
                container_ll.addView(item, params)
                views.add(item)
            }
        }
    }

    fun append(s: String) {
        if (index >= views.size) return
        if (tipVisible && tip_tv.visibility == View.VISIBLE) {
            tip_tv.visibility = View.INVISIBLE
        }

        if (index > 0) {
            val preItem = views[index - 1]
            toStar(preItem)
        }

        val curItem = views[index]
        animIn(curItem, s)
        codes.append(index, s)
        toStar(curItem, 1500)
        index++

        listener?.onUpdate(index)
    }

    fun set(s: String) {
        if (s.length != count) return
        for (i in 0 until count) {
            val c = s[i]
            toStar(views[i])
            codes.append(i, c.toString())
        }
        listener?.onUpdate(count)
    }

    fun delete() {
        if (index <= 0) return
        index--
        val codeView = views[index]
        codeView.text = ""
        codeView.textSize = starSize

        listener?.onUpdate(index)
    }

    fun clear() {
        if (index != 0) {
            views.forEach { code -> code.text = "" }
            codes.clear()
            index = 0
        }

        listener?.onUpdate(index)
    }

    fun code(): String {
        val sb = StringBuilder()
        for (i in 0 until codes.size()) {
            sb.append(codes[i])
        }
        return sb.toString()
    }

    fun error(tip: String) {
        if (!tipVisible) return

        tip_tv.text = tip
        tip_tv.visibility = View.VISIBLE
        clear()
    }

    fun setListener(listener: OnPinListener) {
        this.listener = listener
    }

    fun getCount() = count

    private fun animIn(codeView: TextView, s: String) {
        val translateIn = TranslateAnimation(0f, 0f, codeView.height.toFloat(), 0f)
        translateIn.interpolator = OvershootInterpolator()
        translateIn.duration = 500

        val fadeIn = AlphaAnimation(0f, 1f)
        fadeIn.duration = 200

        val animationSet = AnimationSet(false)
        animationSet.addAnimation(fadeIn)
        animationSet.addAnimation(translateIn)
        animationSet.reset()
        animationSet.startTime = 0

        codeView.text = s
        codeView.textSize = textSize
        codeView.clearAnimation()
        codeView.startAnimation(animationSet)
    }

    private fun toStar(codeView: TextView, delay: Long = 0) {
        codeView.postDelayed(
            {
                if (codeView.text.isNotEmpty()) {
                    codeView.text = STAR
                }
            },
            delay
        )
    }

    interface OnPinListener {
        fun onUpdate(index: Int)
    }
}
