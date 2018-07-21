package com.hoc.drinkshop.transitions


import android.animation.Animator
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.util.Property
import android.view.ViewGroup
import android.widget.TextView
import androidx.transition.Transition
import androidx.transition.TransitionValues

class Recolor : Transition {
    constructor()

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs)

    private fun captureValues(transitionValues: TransitionValues) {
        val view = transitionValues.view
        transitionValues.values[PROPNAME_BACKGROUND] = view.background
        if (view is TextView) {
            transitionValues.values[PROPNAME_TEXT_COLOR] = view.currentTextColor
        }
    }

    override fun captureStartValues(transitionValues: TransitionValues) {
        captureValues(transitionValues)
    }

    override fun captureEndValues(transitionValues: TransitionValues) {
        captureValues(transitionValues)
    }

    override fun createAnimator(sceneRoot: ViewGroup, startValues: TransitionValues?,
                                endValues: TransitionValues?): Animator? {
        if (startValues == null || endValues == null) {
            return null
        }
        val view = endValues.view
        val startBackground = startValues.values[PROPNAME_BACKGROUND] as? Drawable
        val endBackground = endValues.values[PROPNAME_BACKGROUND] as? Drawable
        var bgAnimator: ObjectAnimator? = null

        if (startBackground is ColorDrawable && endBackground is ColorDrawable) {
            if (startBackground.color != endBackground.color) {
                val finalColor = endBackground.color
                endBackground.color = startBackground.color
                bgAnimator = ObjectAnimator.ofInt(endBackground, COLORDRAWABLE_COLOR, startBackground.color, finalColor)
                bgAnimator!!.setEvaluator(ArgbEvaluator())
            }
        }
        var textColorAnimator: ObjectAnimator? = null

        if (view is TextView) {
            val start = startValues.values[PROPNAME_TEXT_COLOR] as Int
            val end = endValues.values[PROPNAME_TEXT_COLOR] as Int
            if (start != end) {
                view.setTextColor(end)
                textColorAnimator = ObjectAnimator.ofInt(view, TEXTVIEW_TEXT_COLOR, start, end)
                textColorAnimator!!.setEvaluator(ArgbEvaluator())
            }
        }

        return mergeAnimators(bgAnimator, textColorAnimator)
    }

    companion object {
        private const val PROPNAME_BACKGROUND = "android:recolor:background"
        private const val PROPNAME_TEXT_COLOR = "android:recolor:textColor"
        val TEXTVIEW_TEXT_COLOR = object : IntProperty<TextView>() {
            override fun setValue(`object`: TextView, value: Int) = `object`.setTextColor(value)
            override fun get(`object`: TextView) = 0
        }.optimize()
        val COLORDRAWABLE_COLOR: Property<ColorDrawable, Int> = object : IntProperty<ColorDrawable>() {
            override fun setValue(`object`: ColorDrawable, value: Int) {
                `object`.color = value
            }

            override fun get(`object`: ColorDrawable) = `object`.color
        }.optimize()
    }
}

internal abstract class IntProperty<T> : Property<T, Int>(Int::class.java, null) {

    abstract fun setValue(`object`: T, value: Int)

    override fun set(`object`: T, value: Int) = setValue(`object`, value)

    /**
     * Just default realisation. Some of properties can have no getter. Override for real getter
     */
    override fun get(`object`: T) = 0

    fun optimize(): Property<T, Int> {
        return object : AndroidIntProperty<T>(null) {
            override fun setValue(`object`: T, value: Int) {
                this@IntProperty.setValue(`object`, value)
            }

            override fun get(`object`: T): Int {
                return this@IntProperty.get(`object`)
            }
        }
    }
}

abstract class AndroidIntProperty<T>(name: String?) : Property<T, Int>(Int::class.java, name) {
    /**
     * A type-specific variant of [.set] that is faster when dealing
     * with fields of type `int`.
     */
    abstract fun setValue(`object`: T, value: Int)

    override fun set(`object`: T, value: Int) = setValue(`object`, value)
}