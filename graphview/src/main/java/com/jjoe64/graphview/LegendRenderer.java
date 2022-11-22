/**
 * GraphView
 * Copyright (C) 2014  Jonas Gehring
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License,
 * with the "Linking Exception", which can be found at the license.txt
 * file in this program.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * with the "Linking Exception" along with this program; if not,
 * write to the author Jonas Gehring <g.jjoe64@gmail.com>.
 */
package com.jjoe64.graphview;

import android.content.res.TypedArray;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.RectF;
import android.util.TypedValue;

import com.jjoe64.graphview.series.Series;

import java.util.ArrayList;
import java.util.List;

/**
 * The default renderer for the legend box
 *
 * @author jjoe64
 */
public class LegendRenderer {
    /**
     * wrapped styles regarding to the
     * legend
     */
    private final class Styles {
        float textSize;
        int spacing;
        int padding;
        int width;
        int backgroundColor;
        int textColor;
        int margin;
        LegendAlign align;
        Point fixedPosition;
    }

    /**
     * alignment of the legend
     */
    public enum LegendAlign {
        /**
         * top right corner
         */
        TOP,

        /**
         * middle right
         */
        MIDDLE,

        /**
         * bottom right corner
         */
        BOTTOM
    }

    /**
     * wrapped styles
     */
    private Styles mStyles;

    /**
     * reference to the graphview
     */
    private final GraphView mGraphView;

    /**
     * flag whether legend will be
     * drawn
     */
    private boolean mIsVisible;

    /**
     * paint for the drawing
     */
    private Paint mPaint;

    /**
     * cached legend width
     * this will be filled in the drawing.
     * Can be cleared via {@link #resetStyles()}
     */
    private int cachedLegendWidth;

    /**
     * creates legend renderer
     *
     * @param graphView regarding graphview
     */
    public LegendRenderer(GraphView graphView) {
        mGraphView = graphView;
        mIsVisible = false;
        mPaint = new Paint();
        mPaint.setTextAlign(Paint.Align.LEFT);
        mStyles = new Styles();
        cachedLegendWidth = 0;
        resetStyles();
    }

    /**
     * resets the styles to the defaults
     * and clears the legend width cache
     */
    public void resetStyles() {
        mStyles.align = LegendAlign.MIDDLE;
        mStyles.textSize = mGraphView.getGridLabelRenderer().getTextSize();
        mStyles.spacing = (int) (mStyles.textSize / 5);
        mStyles.padding = (int) (mStyles.textSize / 2);
        mStyles.width = 0;
        mStyles.backgroundColor = Color.argb(180, 100, 100, 100);
        mStyles.margin = (int) (mStyles.textSize / 5);

        // get matching styles from theme
        TypedValue typedValue = new TypedValue();
        mGraphView.getContext().getTheme().resolveAttribute(android.R.attr.textAppearanceSmall, typedValue, true);

        int color1;

        try {
            TypedArray array = mGraphView.getContext().obtainStyledAttributes(typedValue.data, new int[]{
                    android.R.attr.textColorPrimary});
            color1 = array.getColor(0, Color.BLACK);
            array.recycle();
        } catch (Exception e) {
            color1 = Color.BLACK;
        }

        mStyles.textColor = color1;

        cachedLegendWidth = 0;
    }

    /**
     * draws the legend if it is visible
     *
     * @param canvas canvas
     * @see #setVisible(boolean)
     */
    public void draw(Canvas canvas) {
        if (!mIsVisible) return;

        mPaint.setTextSize(mStyles.textSize);

        int shapeSize = (int) (mStyles.textSize*0.8d);

        List<Series> allSeries = new ArrayList<Series>();
        allSeries.addAll(mGraphView.getSeries());
        if (mGraphView.mSecondScale != null) {
            allSeries.addAll(mGraphView.getSecondScale().getSeries());
        }

        // width
        int legendWidth = mStyles.width;
        if (legendWidth == 0) {
            // auto
            legendWidth = cachedLegendWidth;

            if (legendWidth == 0) {
                Rect textBounds = new Rect();
                for (Series s : allSeries) {
                    if (s.getTitle() != null) {
                        mPaint.getTextBounds(s.getTitle(), 0, s.getTitle().length(), textBounds);
                        legendWidth = Math.max(legendWidth, textBounds.width());
                    }
                }
                if (legendWidth == 0) legendWidth = 1;

                // add shape size
                legendWidth += shapeSize+mStyles.padding*2 + mStyles.spacing;
                cachedLegendWidth = legendWidth;
            }
        }

        // rect
        float legendHeight = (mStyles.textSize+mStyles.spacing)*allSeries.size() -mStyles.spacing;
        float lLeft;
        float lTop;
        if (mStyles.fixedPosition != null) {
            // use fied position
            lLeft = mGraphView.getGraphContentLeft() + mStyles.margin + mStyles.fixedPosition.x;
            lTop = mGraphView.getGraphContentTop() + mStyles.margin + mStyles.fixedPosition.y;
        } else {
            lLeft = mGraphView.getGraphContentLeft() + mGraphView.getGraphContentWidth() - legendWidth - mStyles.margin;
            switch (mStyles.align) {
                case TOP:
                    lTop = mGraphView.getGraphContentTop() + mStyles.margin;
                    break;
                case MIDDLE:
                    lTop = mGraphView.getHeight() / 2 - legendHeight / 2;
                    break;
                default:
                    lTop = mGraphView.getGraphContentTop() + mGraphView.getGraphContentHeight() - mStyles.margin - legendHeight - 2*mStyles.padding;
            }
        }
        float lRight = lLeft+legendWidth;
        float lBottom = lTop+legendHeight+2*mStyles.padding;
        mPaint.setColor(mStyles.backgroundColor);
        canvas.drawRoundRect(new RectF(lLeft, lTop, lRight, lBottom), 8, 8, mPaint);

        int i=0;
        for (Series series : allSeries) {
            mPaint.setColor(series.getColor());
            canvas.drawRect(new RectF(lLeft+mStyles.padding, lTop+mStyles.padding+(i*(mStyles.textSize+mStyles.spacing)), lLeft+mStyles.padding+shapeSize, lTop+mStyles.padding+(i*(mStyles.textSize+mStyles.spacing))+shapeSize), mPaint);
            if (series.getTitle() != null) {
                mPaint.setColor(mStyles.textColor);
                canvas.drawText(series.getTitle(), lLeft+mStyles.padding+shapeSize+mStyles.spacing, lTop+mStyles.padding+mStyles.textSize+(i*(mStyles.textSize+mStyles.spacing)), mPaint);
            }
            i++;
        }
    }

    /**
     * @return the flag whether the legend will be drawn
     */
    public boolean isVisible() {
        return mIsVisible;
    }

    /**
     * set the flag whether the legend will be drawn
     *
     * @param mIsVisible visible flag
     */
    public void setVisible(boolean mIsVisible) {
        this.mIsVisible = mIsVisible;
    }

    /**
     * @return font size
     */
    public float getTextSize() {
        return mStyles.textSize;
    }

    /**
     * sets the font size. this will clear
     * the internal legend width cache
     *
     * @param textSize font size
     */
    public void setTextSize(float textSize) {
        mStyles.textSize = textSize;
        cachedLegendWidth = 0;
    }

    /**
     * @return the spacing between the text lines
     */
    public int getSpacing() {
        return mStyles.spacing;
    }

    /**
     * set the spacing between the text lines
     *
     * @param spacing the spacing between the text lines
     */
    public void setSpacing(int spacing) {
        mStyles.spacing = spacing;
    }

    /**
     * padding is the space between the edge of the box
     * and the beginning of the text
     *
     * @return padding from edge to text
     */
    public int getPadding() {
        return mStyles.padding;
    }

    /**
     * padding is the space between the edge of the box
     * and the beginning of the text
     *
     * @param padding padding from edge to text
     */
    public void setPadding(int padding) {
        mStyles.padding = padding;
    }

    /**
     * the width of the box exclusive padding
     *
     * @return  the width of the box
     *          0 => auto
     */
    public int getWidth() {
        return mStyles.width;
    }

    /**
     * the width of the box exclusive padding
     * @param width     the width of the box exclusive padding
     *                  0 => auto
     */
    public void setWidth(int width) {
        mStyles.width = width;
    }

    /**
     * @return  background color of the box
     *          it is recommended to use semi-transparent
     *          color.
     */
    public int getBackgroundColor() {
        return mStyles.backgroundColor;
    }

    /**
     * @param backgroundColor   background color of the box
     *                          it is recommended to use semi-transparent
     *                          color.
     */
    public void setBackgroundColor(int backgroundColor) {
        mStyles.backgroundColor = backgroundColor;
    }

    /**
     * @return  margin from the edge of the box
     *          to the corner of the graphview
     */
    public int getMargin() {
        return mStyles.margin;
    }

    /**
     * @param margin    margin from the edge of the box
     *                  to the corner of the graphview
     */
    public void setMargin(int margin) {
        mStyles.margin = margin;
    }

    /**
     * @return the vertical alignment of the box
     */
    public LegendAlign getAlign() {
        return mStyles.align;
    }

    /**
     * @param align the vertical alignment of the box
     */
    public void setAlign(LegendAlign align) {
        mStyles.align = align;
    }

    /**
     * @return font color
     */
    public int getTextColor() {
        return mStyles.textColor;
    }

    /**
     * @param textColor font color
     */
    public void setTextColor(int textColor) {
        mStyles.textColor = textColor;
    }

    /**
     * Use fixed coordinates to position the legend.
     * This will override the align setting.
     *
     * @param x x coordinates in pixel
     * @param y y coordinates in pixel
     */
    public void setFixedPosition(int x, int y) {
        mStyles.fixedPosition = new Point(x, y);
    }
}
