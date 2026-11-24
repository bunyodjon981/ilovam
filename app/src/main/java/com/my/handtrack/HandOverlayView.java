package com.my.handtrack;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

// Qo'l bo'g'imlari (landmark) ni kamera tasviri ustiga chizadi.
// Old kamera ko'zgu rejimida bo'lgani uchun x o'qi teskari.
public class HandOverlayView extends View {

    // 21 nuqta orasidagi chiziqlar (barmoqlar va kaft)
    static final int[][] BONES = {
            {0, 1}, {1, 2}, {2, 3}, {3, 4},
            {0, 5}, {5, 6}, {6, 7}, {7, 8},
            {5, 9}, {9, 10}, {10, 11}, {11, 12},
            {9, 13}, {13, 14}, {14, 15}, {15, 16},
            {13, 17}, {17, 18}, {18, 19}, {19, 20}, {0, 17}
    };

    final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Paint dot = new Paint(Paint.ANTI_ALIAS_FLAG);
    volatile List<float[][]> hands = new ArrayList<float[][]>();
    volatile int iw = 1, ih = 1;

    public HandOverlayView(Context c) {
        super(c);
        line.setColor(0xFF00FF78);
        line.setStrokeWidth(6f);
        dot.setColor(0xFFFFFFFF);
    }

    public void setHands(List<float[][]> h, int w, int hh) {
        hands = h;
        iw = Math.max(1, w);
        ih = Math.max(1, hh);
        postInvalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        super.onDraw(c);
        float vw = getWidth(), vh = getHeight();
        // PreviewView FILL_CENTER rejimi bilan bir xil moslash
        float s = Math.max(vw / iw, vh / ih);
        float ox = (vw - iw * s) / 2f;
        float oy = (vh - ih * s) / 2f;

        for (float[][] pts : hands) {
            int n = pts.length;
            float[] X = new float[n];
            float[] Y = new float[n];
            for (int i = 0; i < n; i++) {
                X[i] = ox + (1f - pts[i][0]) * iw * s;
                Y[i] = oy + pts[i][1] * ih * s;
            }
            for (int[] b : BONES) {
                if (b[0] < n && b[1] < n) {
                    c.drawLine(X[b[0]], Y[b[0]], X[b[1]], Y[b[1]], line);
                }
            }
            for (int i = 0; i < n; i++) {
                c.drawCircle(X[i], Y[i], 10f, dot);
            }
        }
    }
}
