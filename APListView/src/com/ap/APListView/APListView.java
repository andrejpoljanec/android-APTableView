package com.ap.APListView;

import android.animation.Animator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Point;
import android.os.Handler;
import android.util.AttributeSet;
import android.util.Log;
import android.view.*;
import android.view.animation.Animation;
import android.view.animation.Transformation;
import android.widget.ListAdapter;
import android.widget.ListView;

/**
 * Created by andrejp on 16.5.2015.
 */
public class APListView extends ListView {

    private APListScroller listScroller = null;
    private APListHeaderView headerView = null;
    private APListAdapter listAdapter = null;
    private Point windowSize;

    private float dragX;
    private float dragY;
    private float dragXOffset;
    private float dragYOffset;
    private Bitmap dragBitmap;
    private ValueAnimator dragAnimator;
    private int dragPosition;
    private int dragOriginalPosition;
    private View dragExpandedView;
    private int dragViewHeight;
    private VelocityTracker dragVelocity;

    public APListView(Context context) {
        super(context);
        initialize();
    }

    public APListView(Context context, AttributeSet attrs) {
        super(context, attrs);
        initialize();
    }

    public APListView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        initialize();
    }

    public APListView(Context context, AttributeSet attrs, int defStyleAttr, int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
        initialize();
    }

    private void initialize() {
        listScroller = new APListScroller(getContext(), this);
        headerView = new APListHeaderView();

        WindowManager windowManager = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
        windowSize = new Point();
        windowManager.getDefaultDisplay().getSize(windowSize);

    }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        headerView.draw(canvas);
        if (dragBitmap != null) {
            canvas.drawBitmap(dragBitmap, dragX, dragY, null);
        }
        listScroller.draw(canvas);
    }

    private void collapseDragPlaceholder() {
        if (dragExpandedView != null) {
            View animatedView = dragExpandedView;
            Animation animation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    animatedView.getLayoutParams().height = (int)(dragViewHeight * (1 - interpolatedTime));
                    animatedView.requestLayout();
                }
                @Override
                public boolean willChangeBounds() {
                    return true;
                }
            };
            animation.setDuration(100);
            animatedView.startAnimation(animation);
        }
    }

    private void expandDragPlaceholder() {
        if (dragExpandedView != null) {
            View animatedView = dragExpandedView;
            Animation animation = new Animation() {
                @Override
                protected void applyTransformation(float interpolatedTime, Transformation t) {
                    animatedView.getLayoutParams().height = (int)(dragViewHeight * interpolatedTime);
                    animatedView.requestLayout();
                }
                @Override
                public boolean willChangeBounds() {
                    return true;
                }
            };
            animation.setDuration(100);
            animatedView.startAnimation(animation);
        }
    }

    private void hideDragOriginal() {
        listAdapter.hidePosition(dragOriginalPosition);
    }

    private void showDragOriginal() {
        listAdapter.showPosition(dragOriginalPosition);
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (dragBitmap != null && ev.getAction() == MotionEvent.ACTION_MOVE) {
            dragVelocity.addMovement(ev);
            int x = (int) ev.getX();
            int y = (int) ev.getY();
            dragX = x - dragXOffset;
            dragY = y - dragYOffset;
            invalidate();
            int position = pointToPosition(x, y);
            if (position % 2 == 0) {
                position--;
            }
            if (Math.abs(dragOriginalPosition - position) <= 1) {
                position = dragOriginalPosition;
            }
            position -= getFirstVisiblePosition();
            if (position != dragPosition && position > 0) {
                collapseDragPlaceholder();
                dragPosition = position;
                dragExpandedView = getChildAt(dragPosition);
                expandDragPlaceholder();
            }
            int scrollAmount = 0;
            if (y > getHeight() * 3 / 4) {
                // scroll down
                if (getLastVisiblePosition() < getCount() - 1) {
                    scrollAmount = (y - getHeight() * 3 / 4);
                } else {
                    scrollAmount = 0;
                }
            } else if (y < getHeight() / 4) {
                // scroll up
                if (getFirstVisiblePosition() > 0) {
                    scrollAmount = (y - getHeight() / 4);
                } else {
                    scrollAmount = 0;
                }
            }
            if (scrollAmount != 0) {
                smoothScrollBy(scrollAmount, 20);
            }
            return true;
        } else if (dragBitmap != null && ev.getAction() == MotionEvent.ACTION_UP || ev.getAction() == MotionEvent.ACTION_CANCEL) {

            dragVelocity.computeCurrentVelocity(10);
            dragX = ev.getX() - dragXOffset;
            dragY = ev.getY() - dragYOffset;
            collapseDragPlaceholder();

            if (Math.abs(dragPosition + getFirstVisiblePosition() - dragOriginalPosition) <= 1) {
                // No change
                View dragOriginalView = getChildAt(dragOriginalPosition - getFirstVisiblePosition());
                animateDragTo(dragOriginalView.getX(), dragOriginalView.getY());
            } else if (Math.abs(dragVelocity.getXVelocity()) > 20 && (dragPosition < getFirstVisiblePosition() || dragPosition > getLastVisiblePosition())) {
                // Cancel it out
                animateDragTo(getWidth(), dragY);
            } else if (Math.abs(dragVelocity.getXVelocity()) > 20) {
                // Cancel it to the original position
                View dragOriginalView = getChildAt(dragOriginalPosition - getFirstVisiblePosition());
                animateDragTo(dragOriginalView.getX(), dragOriginalView.getY());
            } else {
                // Reorder
                View dropView = getChildAt(dragPosition);
                animateDragTo(dropView.getX(), dropView.getY());
                // TODO: Reorder it!
            }

            return true;
        }
        return listScroller.onTouchEvent(ev) || super.onTouchEvent(ev);
    }

    private void animateDragTo(float toX, float toY) {
        dragAnimator = ValueAnimator.ofFloat(0, 1);
        dragAnimator.setDuration(200);
        dragAnimator.addUpdateListener(new ValueAnimator.AnimatorUpdateListener() {
            @Override
            public void onAnimationUpdate(ValueAnimator animation) {
                float value = (float) animation.getAnimatedValue();
                dragX = dragX + value * (toX - dragX);
                dragY = dragY + value * (toY - dragY);
                invalidate();
            }
        });
        dragAnimator.addListener(new Animator.AnimatorListener() {
            @Override
            public void onAnimationEnd(Animator animation) {
                dragBitmap = null;
                showDragOriginal();
            }

            @Override
            public void onAnimationCancel(Animator animation) {
                dragBitmap = null;
            }

            @Override
            public void onAnimationStart(Animator animation) {
            }

            @Override
            public void onAnimationRepeat(Animator animation) {
            }
        });
        dragAnimator.start();
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent ev) {
        if (ev.getAction() == MotionEvent.ACTION_DOWN) {
            if (dragVelocity == null) {
                dragVelocity = VelocityTracker.obtain();
            } else {
                dragVelocity.clear();
            }
            dragVelocity.addMovement(ev);
            int x = (int) ev.getX();
            int y = (int) ev.getY();
            if (x >= 64) {
                return false;
            }
            dragOriginalPosition = pointToPosition(x, y);
            View dragOriginalView = getChildAt(dragOriginalPosition - getFirstVisiblePosition());
            if (dragOriginalView == null) {
                return false;
            }
            dragViewHeight = dragOriginalView.getHeight();
            listAdapter.setPlaceholderHeight(dragViewHeight);
            dragOriginalView.setDrawingCacheEnabled(true);
            dragBitmap = Bitmap.createBitmap(dragOriginalView.getDrawingCache());
            dragOriginalView.setDrawingCacheEnabled(false);
            dragX = dragOriginalView.getX();
            dragY = dragOriginalView.getY();
            dragXOffset = ev.getX() - dragX;
            dragYOffset = ev.getY() - dragY;
            hideDragOriginal();
            dragPosition = dragOriginalPosition - getFirstVisiblePosition() + 1;
            dragExpandedView = getChildAt(dragPosition);
            expandDragPlaceholder();
        }
        return true;
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(adapter);
        if (adapter instanceof APListAdapter) {
            listScroller.setAdapter(adapter);
            listAdapter = (APListAdapter) adapter;
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        listScroller.onSizeChanged(w, h, oldw, oldh);
    }

    @Override
    protected void onScrollChanged(int l, int t, int oldl, int oldt) {
        super.onScrollChanged(l, t, oldl, oldt);
        int section = listAdapter.getSectionForPosition(getFirstVisiblePosition());
        if (listAdapter.getPositionForSection(section) == getFirstVisiblePosition()) {
            // replace header
            View headerForSection = listAdapter.getHeaderForSection(section);
            if (headerForSection == null) {
                return;
            }
            headerForSection.measure(0, 0);
            headerView.setHeader(headerForSection, section);
        } else if (headerView.getPosition() > section){
            // clear header
            headerView.clearHeader();
            headerView.setPosition(section);
        } else if (headerView.getPosition() == section) {
            // scroll header
            View headerForNextSection = listAdapter.getHeaderForSection(section + 1);
            if (headerForNextSection == null) {
                return;
            }
            if (headerForNextSection.getTop() < headerView.getHeight() + 2) {
                headerView.setOffset(headerForNextSection.getTop() - headerView.getHeight() - 2);
            }
        }
    }

}