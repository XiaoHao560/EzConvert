package com.tech.ezconvert;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

public class AnimationUtils {
    
    // 按钮点击动画
    public static void animateButtonClick(View view) {
        ObjectAnimator scaleDownX = ObjectAnimator.ofFloat(view, "scaleX", 0.95f);
        ObjectAnimator scaleDownY = ObjectAnimator.ofFloat(view, "scaleY", 0.95f);
        scaleDownX.setDuration(100);
        scaleDownY.setDuration(100);
        
        ObjectAnimator scaleUpX = ObjectAnimator.ofFloat(view, "scaleX", 1f);
        ObjectAnimator scaleUpY = ObjectAnimator.ofFloat(view, "scaleY", 1f);
        scaleUpX.setDuration(100);
        scaleUpY.setDuration(100);
        scaleUpX.setStartDelay(100);
        scaleUpY.setStartDelay(100);
        
        scaleDownX.addListener(new AnimatorListenerAdapter() {
            @Override
            public void onAnimationEnd(Animator animation) {
                scaleUpX.start();
                scaleUpY.start();
            }
        });
        
        scaleDownX.start();
        scaleDownY.start();
    }
    
    // 卡片入场动画
    public static void animateCardEntrance(View view, int delay) {
        view.setAlpha(0f);
        view.setTranslationY(50f);
        
        view.animate()
            .alpha(1f)
            .translationY(0f)
            .setStartDelay(delay)
            .setDuration(400)
            .setInterpolator(new DecelerateInterpolator())
            .start();
    }
    
    // 进度条平滑动画
    public static void animateProgressSmoothly(ProgressBar progressBar, int progress) {
        ObjectAnimator progressAnimator = ObjectAnimator.ofInt(progressBar, "progress", progress);
        progressAnimator.setDuration(300);
        progressAnimator.setInterpolator(new AccelerateDecelerateInterpolator());
        progressAnimator.start();
    }
    
    // 淡入动画
    public static void fadeInView(View view, int duration) {
        AlphaAnimation fadeIn = new AlphaAnimation(0f, 1f);
        fadeIn.setDuration(duration);
        fadeIn.setFillAfter(true);
        view.startAnimation(fadeIn);
    }
    
    // 微弹跳动画
    public static void animateBounce(View view) {
        ObjectAnimator scaleX = ObjectAnimator.ofFloat(view, "scaleX", 1f, 1.05f, 1f);
        ObjectAnimator scaleY = ObjectAnimator.ofFloat(view, "scaleY", 1f, 1.05f, 1f);
        
        scaleX.setDuration(200);
        scaleY.setDuration(200);
        scaleX.setInterpolator(new OvershootInterpolator());
        scaleY.setInterpolator(new OvershootInterpolator());
        
        scaleX.start();
        scaleY.start();
    }
    
    // 状态更新动画
    public static void animateStatusUpdate(View view) {
        view.setAlpha(0.7f);
        view.animate()
            .alpha(1f)
            .setDuration(500)
            .setInterpolator(new AccelerateDecelerateInterpolator())
            .start();
    }
}