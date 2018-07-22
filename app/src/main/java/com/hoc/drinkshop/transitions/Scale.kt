package com.hoc.drinkshop.transitions

import android.animation.Animator
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.view.ViewGroup
import androidx.transition.Transition
import androidx.transition.TransitionListenerAdapter
import androidx.transition.TransitionValues
import androidx.transition.Visibility
import com.hoc.drinkshop.R

class Scale : Visibility {
    private var disappearedScale = 0f

    constructor()

    /**
     * @param disappearedScale Value of scale on start of appearing or in finish of disappearing.
     * Default value is 0. Can be useful for mixing some Visibility
     * transitions, for example Scale and Fade
     */
    constructor(disappearedScale: Float) {
        setDisappearedScale(disappearedScale)
    }

    constructor(context: Context, attrs: AttributeSet) : super(context, attrs) {
        val a = context.obtainStyledAttributes(attrs, R.styleable.Scale)
        setDisappearedScale(a.getFloat(R.styleable.Scale_disappearedScale, disappearedScale))
        a.recycle()
    }

    override fun captureStartValues(transitionValues: TransitionValues) {
        super.captureStartValues(transitionValues)
        transitionValues.values[PROPNAME_SCALE_X] = transitionValues.view.scaleX
        transitionValues.values[PROPNAME_SCALE_Y] = transitionValues.view.scaleY
    }

    /**
     * @param disappearedScale Value of scale on start of appearing or in finish of disappearing.
     * Default value is 0. Can be useful for mixing some Visibility
     * transitions, for example Scale and Fade
     * @return This Scale object.
     */
    fun setDisappearedScale(disappearedScale: Float): Scale {
        if (disappearedScale < 0f) {
            throw IllegalArgumentException("disappearedScale cannot be negative!")
        }
        this.disappearedScale = disappearedScale
        return this
    }

    private fun createAnimation(
        view: View,
        startScale: Float,
        endScale: Float,
        values: TransitionValues?
    ): Animator? {
        val initialScaleX = view.scaleX
        val initialScaleY = view.scaleY
        var startScaleX = initialScaleX * startScale
        val endScaleX = initialScaleX * endScale
        var startScaleY = initialScaleY * startScale
        val endScaleY = initialScaleY * endScale

        if (values != null) {
            val savedScaleX = values.values[PROPNAME_SCALE_X] as? Float
            val savedScaleY = values.values[PROPNAME_SCALE_Y] as? Float
            // if saved value is not equal initial value it means that previous
            // transition was interrupted and in the onTransitionEnd
            // we've applied endScale. we should apply proper value to
            // continue animation from the interrupted state
            if (savedScaleX != null && savedScaleX != initialScaleX) {
                startScaleX = savedScaleX
            }
            if (savedScaleY != null && savedScaleY != initialScaleY) {
                startScaleY = savedScaleY
            }
        }

        view.scaleX = startScaleX
        view.scaleY = startScaleY

        val animator = mergeAnimators(
            ObjectAnimator.ofFloat(view, View.SCALE_X, startScaleX, endScaleX),
            ObjectAnimator.ofFloat(view, View.SCALE_Y, startScaleY, endScaleY)
        )
        addListener(object : TransitionListenerAdapter() {
            override fun onTransitionEnd(transition: Transition) {
                view.scaleX = initialScaleX
                view.scaleY = initialScaleY
                transition.removeListener(this)
            }
        })
        return animator
    }

    override fun onAppear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator? {
        return createAnimation(view, disappearedScale, 1f, startValues)
    }

    override fun onDisappear(
        sceneRoot: ViewGroup,
        view: View,
        startValues: TransitionValues?,
        endValues: TransitionValues?
    ): Animator? {
        return createAnimation(view, 1f, disappearedScale, startValues)
    }

    companion object {
        const val PROPNAME_SCALE_X = "scale:scaleX"
        const val PROPNAME_SCALE_Y = "scale:scaleY"
    }
}

fun mergeAnimators(animator1: Animator?, animator2: Animator?) = when {
    animator1 == null -> animator2
    animator2 == null -> animator1
    else -> AnimatorSet().apply { playTogether(animator1, animator2) }
}
