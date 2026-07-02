package com.theermite.hoso.onboarding

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.theermite.hoso.R
import com.theermite.hoso.databinding.ItemOnboardingPageBinding

/**
 * Binds each [OnboardingStep] to its icon + title + body. Existing app
 * drawables are reused (no new assets); the Android-side resource mapping
 * lives here so [OnboardingStep] stays a pure, testable model.
 */
class OnboardingPagerAdapter :
    RecyclerView.Adapter<OnboardingPagerAdapter.PageViewHolder>() {

    private data class PageContent(
        val iconRes: Int,
        val titleRes: Int,
        val bodyRes: Int,
    )

    private fun content(step: OnboardingStep): PageContent = when (step) {
        OnboardingStep.WELCOME -> PageContent(
            R.drawable.ic_stream,
            R.string.onboarding_welcome_title,
            R.string.onboarding_welcome_body,
        )
        OnboardingStep.PERMISSIONS -> PageContent(
            R.drawable.ic_privacy_on,
            R.string.onboarding_permissions_title,
            R.string.onboarding_permissions_body,
        )
        OnboardingStep.CONNECT_CHANNEL -> PageContent(
            R.drawable.ic_overlay_trigger,
            R.string.onboarding_connect_title,
            R.string.onboarding_connect_body,
        )
        OnboardingStep.GO_LIVE -> PageContent(
            R.drawable.ic_play,
            R.string.onboarding_golive_title,
            R.string.onboarding_golive_body,
        )
    }

    class PageViewHolder(val binding: ItemOnboardingPageBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(
        parent: ViewGroup,
        viewType: Int
    ): PageViewHolder {
        val binding = ItemOnboardingPageBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return PageViewHolder(binding)
    }

    override fun onBindViewHolder(holder: PageViewHolder, position: Int) {
        val c = content(OnboardingStep.ordered[position])
        holder.binding.onboardingIcon.setImageResource(c.iconRes)
        holder.binding.onboardingTitle.setText(c.titleRes)
        holder.binding.onboardingBody.setText(c.bodyRes)
    }

    override fun getItemCount(): Int = OnboardingStep.ordered.size
}
