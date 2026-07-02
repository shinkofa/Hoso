package com.theermite.hoso

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.LinearLayout
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.viewpager2.widget.ViewPager2
import com.theermite.hoso.config.StreamConfig
import com.theermite.hoso.databinding.ActivityOnboardingBinding
import com.theermite.hoso.onboarding.OnboardingPagerAdapter
import com.theermite.hoso.onboarding.OnboardingStep

/**
 * First-launch onboarding — a 4-step swipeable guide (welcome, permissions,
 * connect a channel, go live). Shown once at first launch and re-openable
 * later via the "Revoir le tutoriel" link. Dignity: fully skippable, no
 * dark pattern; ND-friendly: honors the system reduce-motion setting for
 * page transitions.
 */
class OnboardingActivity : AppCompatActivity() {

    private lateinit var binding: ActivityOnboardingBinding
    private val stepCount = OnboardingStep.ordered.size
    private val dots = mutableListOf<View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityOnboardingBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.onboardingPager.adapter = OnboardingPagerAdapter()
        buildDots()

        binding.onboardingPager.registerOnPageChangeCallback(
            object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    renderForPage(position)
                }
            }
        )

        binding.btnSkip.setOnClickListener { finishOnboarding() }
        binding.btnNext.setOnClickListener {
            val current = binding.onboardingPager.currentItem
            if (current < stepCount - 1) {
                binding.onboardingPager.setCurrentItem(
                    current + 1, !reduceMotion()
                )
            } else {
                finishOnboarding()
            }
        }

        renderForPage(0)
    }

    /** Mark the guide as seen and close. Idempotent for the replay path. */
    private fun finishOnboarding() {
        StreamConfig(this).onboardingSeen = true
        finish()
    }

    /**
     * Build one dot per step. Dots are simple programmatic circles so no
     * drawable asset is needed; the active dot is the sky-blue accent, the
     * others a dim divider color.
     */
    private fun buildDots() {
        val size = dp(8)
        val margin = dp(4)
        repeat(stepCount) {
            val dot = View(this)
            val lp = LinearLayout.LayoutParams(size, size).apply {
                marginStart = margin
                marginEnd = margin
            }
            dot.layoutParams = lp
            binding.onboardingDots.addView(dot)
            dots.add(dot)
        }
    }

    private fun renderForPage(position: Int) {
        dots.forEachIndexed { i, dot ->
            dot.background = circle(
                if (i == position) R.color.stream_start else R.color.divider
            )
        }
        val isLast = position == stepCount - 1
        binding.btnNext.setText(
            if (isLast) R.string.onboarding_finish else R.string.onboarding_next
        )
        // Announce progress for screen readers.
        binding.onboardingPager.contentDescription = getString(
            R.string.onboarding_page_indicator, position + 1, stepCount
        )
    }

    private fun circle(colorRes: Int): GradientDrawable = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(ContextCompat.getColor(this@OnboardingActivity, colorRes))
    }

    /**
     * True when the user has reduced animations at the OS level
     * (Settings > Accessibility). We then jump pages without the slide.
     */
    private fun reduceMotion(): Boolean {
        val scale = Settings.Global.getFloat(
            contentResolver,
            Settings.Global.ANIMATOR_DURATION_SCALE,
            1f
        )
        return scale == 0f
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()
}
