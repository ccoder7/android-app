package one.mixin.android.widget

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import android.view.Gravity
import android.view.ViewGroup
import android.widget.TextView
import kotlinx.android.synthetic.main.view_avatar.view.*
import one.mixin.android.R
import one.mixin.android.vo.User
import org.jetbrains.anko.collections.forEachReversedWithIndex

class AvatarsView : ViewGroup {
    companion object {
        const val DEFAULT_BORDER_WIDTH = 0
        const val DEFAULT_BORDER_COLOR = Color.WHITE
        const val DEFAULT_AVATAR_SIZE = 32

        private const val MAX_VISIBLE_USER_COUNT = 3
    }

    private var userList = arrayListOf<User>()

    private var borderWidth: Int
    private var borderColor: Int

    private var avatarSize: Int = 0
    private var ratio = 3f / 4

    constructor(context: Context) : this(context, null)
    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) {
        val ta = context.obtainStyledAttributes(attrs, R.styleable.AvatarsView)

        borderWidth = ta.getDimensionPixelSize(R.styleable.AvatarsView_avatar_border_width, DEFAULT_BORDER_WIDTH)
        borderColor = ta.getColor(R.styleable.AvatarsView_avatar_border_color, DEFAULT_BORDER_COLOR)
        avatarSize = ta.getDimensionPixelSize(R.styleable.AvatarsView_avatar_size, DEFAULT_AVATAR_SIZE)

        ta.recycle()
    }

    fun addUserList(list: List<User>) {
        userList.clear()
        userList.addAll(list)
        initWithUserList()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val parentWidth = avatarSize + (childCount - 1) * avatarSize * ratio
        setMeasuredDimension(
            MeasureSpec.makeMeasureSpec(parentWidth.toInt(), MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(avatarSize, MeasureSpec.EXACTLY)
        )

        for (i in 0 until childCount) {
            val c = getChildAt(i)
            c.measure(
                MeasureSpec.makeMeasureSpec(avatarSize, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(avatarSize, MeasureSpec.EXACTLY)
            )
        }
    }

    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        for (i in 0 until childCount) {
            val c = getChildAt(i)
            val offsetLeft = (childCount - i - 1) * avatarSize * ratio
            val offsetRight = i * avatarSize * ratio
            c.layout(offsetLeft.toInt(), t, (r - offsetRight - l).toInt(), b)
        }
    }

    private fun initWithUserList() {
        removeAllViews()
        val overSize = userList.size > MAX_VISIBLE_USER_COUNT
        if (overSize) {
            val textView = getTextView(userList.size - MAX_VISIBLE_USER_COUNT + 1)
            addView(textView)
        }
        val takeCount = if (overSize) MAX_VISIBLE_USER_COUNT - 1 else userList.size
        userList.toMutableList()
            .take(takeCount)
            .forEachReversedWithIndex { _, user ->
                val avatarView = AvatarView(context).apply {
                    setBorderWidth(borderWidth)
                    setBorderColor(borderColor)
                }
                avatarView.avatar_simple.setCircleBackgroundColorResource(R.color.white)
                addView(avatarView)
                avatarView.setInfo(user.fullName, user.avatarUrl, user.userId)
            }
    }

    @SuppressLint("SetTextI18n")
    private fun getTextView(num: Int) = TextView(context).apply {
        text = "+$num"
        setTextColor(resources.getColor(R.color.wallet_pending_text_color, null))
        setBackgroundResource(R.drawable.bg_multisigs_gray)
        gravity = Gravity.CENTER
    }
}
