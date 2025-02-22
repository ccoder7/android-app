package one.mixin.android.widget

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Checkable
import android.widget.LinearLayout
import kotlinx.android.synthetic.main.view_call_button.view.*
import one.mixin.android.R
import org.jetbrains.anko.backgroundResource

class CallButton(context: Context, attr: AttributeSet) : LinearLayout(context, attr), Checkable {

    private var checkable: Boolean = false
    private var checked: Boolean = true
    private var bgChecked: Int = 0
    private var bgUnchecked: Int = 0
    private var srcChecked = 0
    private var srcUnchecked = 0
    private var srcDisable = 0

    init {
        LayoutInflater.from(context).inflate(R.layout.view_call_button, this, true)
        val ta = context.obtainStyledAttributes(attr, R.styleable.CallButton)
        checkable = ta.getBoolean(R.styleable.CallButton_android_checkable, false)
        checked = ta.getBoolean(R.styleable.CallButton_android_checked, true)
        bgChecked = ta.getResourceId(R.styleable.CallButton_bg_circle_checked, 0)
        bgUnchecked = ta.getResourceId(R.styleable.CallButton_bg_circle_unchecked, 0)
        srcChecked = ta.getResourceId(R.styleable.CallButton_ic_checked, 0)
        srcUnchecked = ta.getResourceId(R.styleable.CallButton_ic_unchecked, 0)
        srcDisable = ta.getResourceId(R.styleable.CallButton_ic_disable, 0)

        ta.recycle()

        update(isChecked)

        if (checkable) {
            setOnClickListener {
                toggle()
            }
        }
    }

    override fun isChecked() = checked

    override fun toggle() {
        if (!isEnabled) return

        isChecked = !checked
    }

    override fun setChecked(checked: Boolean) {
        if (this.checked != checked) {
            listener?.onCheckedChanged(id, checked)
        }
        this.checked = checked
        update(checked)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        update(isChecked)
    }

    private fun update(isChecked: Boolean) {
        if (!isEnabled) {
            icon.backgroundResource = bgUnchecked
            icon.setImageResource(srcDisable)
            return
        }

        if (isChecked) {
            icon.backgroundResource = bgChecked
            icon.setImageResource(srcChecked)
        } else {
            icon.backgroundResource = bgUnchecked
            icon.setImageResource(srcUnchecked)
        }
    }

    private var listener: OnCheckedChangeListener? = null

    interface OnCheckedChangeListener {
        fun onCheckedChanged(id: Int, checked: Boolean)
    }

    fun setOnCheckedChangeListener(listener: OnCheckedChangeListener?) {
        this.listener = listener
    }
}
