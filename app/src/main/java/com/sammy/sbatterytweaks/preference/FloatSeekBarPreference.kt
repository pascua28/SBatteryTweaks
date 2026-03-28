package com.sammy.sbatterytweaks.preference

import android.content.Context
import android.content.res.TypedArray
import android.graphics.Paint
import android.text.InputType
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.widget.doAfterTextChanged
import androidx.preference.Preference
import androidx.preference.PreferenceViewHolder
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.sammy.sbatterytweaks.R
import java.util.Locale
import kotlin.math.roundToInt

class FloatSeekBarPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = androidx.preference.R.attr.preferenceStyle,
    defStyleRes: Int = 0
) : Preference(context, attrs, defStyleAttr, defStyleRes) {

    private companion object {
        private const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }

    var minFloat = 0f
        private set
    var maxFloat = 100f
        private set
    var stepFloat = 1f
        private set
    var decimals = 2
        private set
    var showReset = true
        private set
    var dialogTitleText: String? = null
        private set
    var unit: String? = null
        private set

    private var valueTextView: TextView? = null
    private var resetButton: ImageButton? = null
    private var seekBarView: SeekBar? = null
    private var summaryText: CharSequence? = null
    private var isTracking = false
    private var defaultValueInternal = 0f

    var value: Float = 0f
        private set

    init {
        layoutResource = R.layout.preference_float_seekbar
        isPersistent = true

        context.obtainStyledAttributes(
            attrs,
            R.styleable.FloatSeekBarPreference,
            defStyleAttr,
            defStyleRes
        ).use {
            minFloat = it.getFloat(R.styleable.FloatSeekBarPreference_minFloat, 0f)
            maxFloat = it.getFloat(R.styleable.FloatSeekBarPreference_maxFloat, 100f)
            stepFloat = it.getFloat(R.styleable.FloatSeekBarPreference_stepFloat, 1f)
            decimals = it.getInt(R.styleable.FloatSeekBarPreference_decimals, 2)
            showReset = it.getBoolean(R.styleable.FloatSeekBarPreference_showReset, true)
            dialogTitleText = it.getString(R.styleable.FloatSeekBarPreference_dialogTitleText)
            unit = it.getString(R.styleable.FloatSeekBarPreference_unit)
        }

        if (stepFloat <= 0f) stepFloat = 1f
        if (maxFloat < minFloat) maxFloat = minFloat

        val xmlDefault = attrs
            ?.getAttributeValue(ANDROID_NS, "defaultValue")
            ?.toFloatOrNull()

        defaultValueInternal = clampAndSnap(xmlDefault ?: minFloat)
        value = defaultValueInternal

        summaryText = summary
    }

    override fun onGetDefaultValue(a: TypedArray, index: Int): Any {
        return defaultValueInternal
    }

    override fun onSetInitialValue(defaultValue: Any?) {
        val def = when (defaultValue) {
            is Float -> defaultValue
            is Number -> defaultValue.toFloat()
            is String -> defaultValue.toFloatOrNull() ?: defaultValueInternal
            else -> defaultValueInternal
        }

        defaultValueInternal = clampAndSnap(def)

        value = if (shouldPersist() && sharedPreferences?.contains(key) == true) {
            clampAndSnap(getPersistedFloatCompat(defaultValueInternal))
        } else {
            defaultValueInternal
        }

        persistFloatCompat(value)
        updateSummary()
        updateSeekBarProgress()
    }

    override fun onClick() {
        // Inline slider only. Row click intentionally does nothing.
    }

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
        super.onBindViewHolder(holder)

        val txtValue = holder.findViewById(R.id.txtValue) as? TextView
        val btnReset = holder.findViewById(R.id.btnReset) as? ImageButton
        val seekBar = holder.findViewById(R.id.seekBar) as? SeekBar

        valueTextView = txtValue
        resetButton = btnReset
        seekBarView = seekBar

        txtValue?.paintFlags = (txtValue?.paintFlags ?: 0) or Paint.UNDERLINE_TEXT_FLAG
        txtValue?.text = formatDisplayValue(value)
        txtValue?.setOnClickListener {
            showInputDialog()
        }

        btnReset?.visibility = if (showReset) View.VISIBLE else View.GONE
        btnReset?.setOnClickListener {
            setValue(defaultValueInternal, true)
        }

        val totalSteps = toStepIndex(maxFloat)
        seekBar?.max = totalSteps
        seekBar?.progress = toStepIndex(value)

        seekBar?.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (!fromUser) return

                val newValue = progressToValue(progress)
                valueTextView?.text = formatDisplayValue(newValue)

                if (!isTracking) {
                    setValue(newValue, true)
                }
            }

            override fun onStartTrackingTouch(bar: SeekBar?) {
                isTracking = true
            }

            override fun onStopTrackingTouch(bar: SeekBar?) {
                isTracking = false
                val progress = bar?.progress ?: 0
                setValue(progressToValue(progress), true)
            }
        })

        updateSeekBarProgress()
        updateSummary()
    }

    fun setValue(newValue: Float, notify: Boolean = true) {
        val snapped = clampAndSnap(newValue)

        if (snapped == value) {
            updateSeekBarProgress()
            updateSummary()
            return
        }

        if (!callChangeListener(snapped)) {
            updateSeekBarProgress()
            updateSummary()
            return
        }

        value = snapped
        persistFloatCompat(value)
        updateSummary()
        updateSeekBarProgress()

        if (notify) {
            notifyChanged()
        }
    }

    private fun updateSummary() {
        summary = summaryText
        valueTextView?.text = formatDisplayValue(value)
    }

    private fun updateSeekBarProgress() {
        val seekBar = seekBarView ?: return
        val progress = toStepIndex(value)

        if (seekBar.progress != progress) {
            seekBar.progress = progress
        }

        valueTextView?.text = formatDisplayValue(value)
    }

    private fun formatValue(v: Float): String {
        return "%.${decimals}f".format(Locale.US, v)
    }

    private fun formatDisplayValue(v: Float): String {
        val suffix = unit?.trim().orEmpty()
        return if (suffix.isEmpty()) {
            formatValue(v)
        } else {
            "${formatValue(v)} $suffix"
        }
    }

    private fun clampAndSnap(v: Float): Float {
        val clamped = v.coerceIn(minFloat, maxFloat)
        val steps = ((clamped - minFloat) / stepFloat).roundToInt()
        val snapped = minFloat + (steps * stepFloat)
        return snapped.coerceIn(minFloat, maxFloat)
    }

    private fun toStepIndex(v: Float): Int {
        return (((clampAndSnap(v) - minFloat) / stepFloat) + 0.5f).toInt()
    }

    private fun progressToValue(progress: Int): Float {
        return clampAndSnap(minFloat + (progress * stepFloat))
    }

    private fun showInputDialog() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(56, 20, 56, 0)
        }

        val minView = TextView(context).apply {
            text = formatValue(minFloat)
            textSize = 22f
        }

        val leftOp = TextView(context).apply {
            text = " ≤ "
            textSize = 22f
            setPadding(18, 0, 18, 0)
        }

        val inputLayout = TextInputLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
            )
            isHintEnabled = false
            boxBackgroundMode = TextInputLayout.BOX_BACKGROUND_NONE
        }

        val editText = TextInputEditText(context).apply {
            inputType = InputType.TYPE_CLASS_NUMBER or
                    InputType.TYPE_NUMBER_FLAG_DECIMAL or
                    InputType.TYPE_NUMBER_FLAG_SIGNED
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            textSize = 24f
            setText(formatValue(value))
            setSelectAllOnFocus(true)
            paintFlags = paintFlags or Paint.UNDERLINE_TEXT_FLAG
        }

        val rightOp = TextView(context).apply {
            text = " ≤ "
            textSize = 22f
            setPadding(18, 0, 18, 0)
        }

        val maxView = TextView(context).apply {
            text = formatValue(maxFloat)
            textSize = 22f
        }

        inputLayout.addView(editText)
        container.addView(minView)
        container.addView(leftOp)
        container.addView(inputLayout)
        container.addView(rightOp)
        container.addView(maxView)

        val dialog = MaterialAlertDialogBuilder(context)
            .setView(container)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.ok, null)
            .create()

        dialog.setOnShowListener {
            val positive = dialog.getButton(AlertDialog.BUTTON_POSITIVE)
            positive.setOnClickListener {
                val raw = editText.text?.toString()?.trim().orEmpty()
                val entered = raw.toFloatOrNull()

                when {
                    entered == null -> {
                        inputLayout.error = context.getString(R.string.invalid_number)
                    }

                    entered < minFloat || entered > maxFloat -> {
                        inputLayout.error = context.getString(
                            R.string.value_must_be_between,
                            formatValue(minFloat),
                            formatValue(maxFloat)
                        )
                    }

                    else -> {
                        inputLayout.error = null
                        setValue(entered, true)
                        dialog.dismiss()
                    }
                }
            }
        }

        editText.doAfterTextChanged {
            inputLayout.error = null
        }

        dialog.show()
    }

    private fun persistFloatCompat(v: Float): Boolean {
        return persistFloat(v)
    }

    private fun getPersistedFloatCompat(defaultValue: Float): Float {
        if (!shouldPersist()) return defaultValue

        return try {
            getPersistedFloat(defaultValue)
        } catch (_: ClassCastException) {
            val prefs = sharedPreferences ?: return defaultValue
            val keyName = key ?: return defaultValue
            if (!prefs.contains(keyName)) return defaultValue

            val migrated = when (val raw = prefs.all[keyName]) {
                is Float -> raw
                is Double -> raw.toFloat()
                is Int -> raw.toFloat()
                is Long -> raw.toFloat()
                is String -> raw.toFloatOrNull()
                else -> null
            } ?: defaultValue

            prefs.edit().putFloat(keyName, migrated).apply()
            migrated
        }
    }

    private inline fun TypedArray.use(block: (TypedArray) -> Unit) {
        try {
            block(this)
        } finally {
            recycle()
        }
    }
}