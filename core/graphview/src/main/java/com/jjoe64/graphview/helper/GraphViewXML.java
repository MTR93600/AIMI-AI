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
package com.jjoe64.graphview.helper;

import android.content.Context;
import android.content.res.TypedArray;
import android.graphics.Color;
import android.util.AttributeSet;
import android.util.Log;

import com.jjoe64.graphview.GraphView;
import com.jjoe64.graphview.R;
import com.jjoe64.graphview.series.BarGraphSeries;
import com.jjoe64.graphview.series.BaseSeries;
import com.jjoe64.graphview.series.DataPoint;
import com.jjoe64.graphview.series.DataPointInterface;
import com.jjoe64.graphview.series.LineGraphSeries;
import com.jjoe64.graphview.series.PointsGraphSeries;
import com.jjoe64.graphview.series.Series;

/**
 * helper class to use GraphView directly
 * in a XML layout file.
 *
 * You can set the data via attribute <b>app:seriesData</b>
 * in the format: "X=Y;X=Y;..." e.g. "0=5.0;1=5;2=4;3=9"
 *
 * Other styling options:
 * <li>app:seriesType="line|bar|points"</li>
 * <li>app:seriesColor="#ff0000"</li>
 * <li>app:seriesTitle="foobar" - if this is set, the legend will be drawn</li>
 * <li>android:title="foobar"</li>
 *
 * Example:
 * <pre>
 * {@code
 *  <com.jjoe64.graphview.helper.GraphViewXML
 *      android:layout_width="match_parent"
 *      android:layout_height="100dip"
 *      app:seriesData="0=5;2=5;3=0;4=2"
 *      app:seriesType="line"
 *      app:seriesColor="#ee0000" />
 * }
 * </pre>
 *
 * @author jjoe64
 */
public class GraphViewXML extends GraphView {
    /**
     * creates the graphview object with data and
     * other options from xml attributes.
     *
     * @param context
     * @param attrs
     */
    public GraphViewXML(Context context, AttributeSet attrs) {
        super(context, attrs);

        // get attributes
        TypedArray a=context.obtainStyledAttributes(
                attrs,
                R.styleable.GraphViewXML);

        String dataStr = a.getString(R.styleable.GraphViewXML_seriesData);
        int color = a.getColor(R.styleable.GraphViewXML_seriesColor, Color.TRANSPARENT);
        String type = a.getString(R.styleable.GraphViewXML_seriesType);
        String seriesTitle = a.getString(R.styleable.GraphViewXML_seriesTitle);
        String title = a.getString(R.styleable.GraphViewXML_android_title);

        a.recycle();

        // decode data
        DataPoint[] data;
        if (dataStr == null || dataStr.isEmpty()) {
            throw new IllegalArgumentException("Attribute seriesData is required in the format: 0=5.0;1=5;2=4;3=9");
        } else {
            String[] d = dataStr.split(";");
            try {
                data = new DataPoint[d.length];
                int i = 0;
                for (String dd : d) {
                    String[] xy = dd.split("=");
                    data[i] = new DataPoint(Double.parseDouble(xy[0]), Double.parseDouble(xy[1]));
                    i++;
                }
            } catch (Exception e) {
                Log.e("GraphViewXML", e.toString());
                throw new IllegalArgumentException("Attribute seriesData is broken. Use this format: 0=5.0;1=5;2=4;3=9");
            }
        }

        // create series
        BaseSeries<DataPoint> series;
        if (type == null || type.isEmpty()) {
            type = "line";
        }
        if (type.equals("line")) {
            series = new LineGraphSeries<DataPoint>(data);
        } else if (type.equals("bar")) {
            series = new BarGraphSeries<DataPoint>(data);
        } else if (type.equals("points")) {
            series = new PointsGraphSeries<DataPoint>(data);
        } else {
            throw new IllegalArgumentException("unknown graph type: "+type+". Possible is line|bar|points");
        }
        if (color != Color.TRANSPARENT) {
            series.setColor(color);
        }
        addSeries(series);

        if (seriesTitle != null && !seriesTitle.isEmpty()) {
            series.setTitle(seriesTitle);
            getLegendRenderer().setVisible(true);
        }

        if (title != null && !title.isEmpty()) {
            setTitle(title);
        }
    }
}
