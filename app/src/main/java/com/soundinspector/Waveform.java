/*
 * SoundInspector
 * Copyright (C) 2026 Adam Williams <broadcast at earthling dot net>
 * 
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 * 
 */


package com.soundinspector;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

public class Waveform extends SurfaceView implements SurfaceHolder.Callback, View.OnTouchListener {
    public Waveform(Context context, AttributeSet attr) {
        super(context, attr);
        getHolder().addCallback(this);
    }


    @Override
    public void surfaceCreated(SurfaceHolder surfaceHolder) {
        Canvas canvas = getHolder().lockCanvas();

        if (canvas != null) {
            // capture the extents here
            Stuff.canvasW = canvas.getWidth();
            Stuff.canvasH = canvas.getHeight();
            Stuff.waveformW = canvas.getWidth();
            Stuff.waveformH = canvas.getHeight() * 9 / 10;
            Stuff.waveformY = Stuff.canvasH - Stuff.waveformH;
            Stuff.maxBitmapW = canvas.getMaximumBitmapWidth();
            Log.i("Waveform", "surfaceCreated canvas=" + canvas +
                    " canvas w=" + Stuff.canvasW +
                    " canvas h=" + Stuff.canvasH +
                    " max w=" + canvas.getMaximumBitmapWidth() + " max h=" + canvas.getMaximumBitmapHeight());

            getHolder().unlockCanvasAndPost(canvas);

            setOnTouchListener(this);
            PlayerWin.instance.drawSpectrum();
        }
    }

    @Override
    public void surfaceChanged(SurfaceHolder surfaceHolder, int i, int i1, int i2) {

    }

    @Override
    public void surfaceDestroyed(SurfaceHolder surfaceHolder) {

    }

    int prev_pointers;
    int starting_dx;
    int starting_dy;
    float starting_zoomX;
    float starting_zoomY;
    int starting_x;
    int starting_y;
    float starting_viewX;
    float starting_viewY;
    float prev_viewX;
    float prev_viewY;
    boolean isCursor = false;
    Timer timer = new Timer();
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        int pointers = motionEvent.getPointerCount();
        int actionPointerId = (motionEvent.getAction() & MotionEvent.ACTION_POINTER_ID_MASK) >>
                MotionEvent.ACTION_POINTER_ID_SHIFT;
        int actionEnum = motionEvent.getAction() & MotionEvent.ACTION_MASK;
        // screen coords
        int current_dx = 0;
        int current_dy = 0;
        int current_x = 0;
        int current_y = 0;
        for(int i = 0; i < pointers; i++)
        {
            current_x += motionEvent.getX(i);
            current_y += motionEvent.getY(i);
            for(int j = 0; j < pointers; j++)
            {
                if(j != i)
                {
                    int dx = (int)Math.abs(motionEvent.getX(i) - motionEvent.getX(j));
                    int dy = (int)Math.abs(motionEvent.getY(i) - motionEvent.getY(j));
                    if(dx > current_dx) current_dx = dx;
                    if(dy > current_dy) current_dy = dy;
                }
            }
        }
        if(pointers > 0) {
            current_x /= pointers;
            current_y /= pointers;
        }

        switch(motionEvent.getAction())
        {
// single point
            case MotionEvent.ACTION_DOWN:
                if(current_y < Stuff.waveformY)
                {
                    isCursor = true;
                    Stuff.playStart = (int)((Stuff.viewX + current_x) * Stuff.zoomX);
                    Player.instance.stopPlayback();
                    PlayerWin.instance.updateButton();
                    PlayerWin.instance.drawSpectrum();
                    break;
                }
                
                if(prev_pointers >= 2)
// reset the zoom
                {
                    starting_dx = current_dx;
                    starting_dy = current_dy;
                }
// reset the translation
                starting_x = current_x;
                starting_y = current_y;
                starting_viewX = Stuff.viewX;
                starting_viewY = Stuff.viewY;
                starting_zoomX = Stuff.zoomX;
                starting_zoomY = Stuff.zoomY;

                prev_pointers = pointers;
                prev_viewX = Stuff.viewX;
                prev_viewY = Stuff.viewY;
                Stuff.is_dragging = true;
                timer.reset();
                Log.i("Waveform", "onTouch ACTION_DOWN current_y=" + current_y);
                break;
            case MotionEvent.ACTION_UP: {
                long dt = timer.getDiff();
                prev_pointers = 0;
                Stuff.is_dragging = false;
                isCursor = false;
                Stuff.save(getContext());

                PlayerWin.instance.handleVelocity(true, 0);
//                Log.i("Waveform", "onTouch ACTION_UP velocity=" + Stuff.velocity_x + " " + Stuff.velocity_y + " dt=" + dt);
                break;
            }
// multi point
            default:
                if(isCursor)
                {
                    int new_playstart = (int)((Stuff.viewX + current_x) * Stuff.zoomX);
                    if(new_playstart != Stuff.playStart) {
                        Stuff.playStart = new_playstart;
                        PlayerWin.instance.drawSpectrum();
                    }
                    break;
                }

                float new_zoomX = Stuff.zoomX;
                float new_zoomY = Stuff.zoomY;
                float new_viewX = Stuff.viewX;
                float new_viewY = Stuff.viewY;
// reset the translation
                if(pointers != prev_pointers)
                {
                    starting_x = current_x;
                    starting_y = current_y;
                    starting_viewX = Stuff.viewX;
                    starting_viewY = Stuff.viewY;
                    starting_zoomX = Stuff.zoomX;
                    starting_zoomY = Stuff.zoomY;
                }

                if(pointers >= 2)
                {
// reset the zoom
                    if(pointers != prev_pointers) {
                        starting_dx = current_dx;
                        starting_dy = current_dy;
                    }

                    new_zoomX = starting_zoomX + (float)(starting_dx - current_dx) / (Stuff.waveformW / starting_zoomX);
                    new_zoomX = Stuff.clamp(new_zoomX, (float)0.02, (float) Stuff.bitmapW / Stuff.waveformW);

                    new_zoomY = starting_zoomY + (float)(starting_dy - current_dy) / (Stuff.waveformH / starting_zoomY);
                    new_zoomY = Stuff.clamp(new_zoomY, (float).02, (float)Stuff.BITMAP_H / Stuff.waveformH);

                    //Log.i("Waveform", "onTouch " + String.valueOf(current_dx - starting_dx) + " " + String.valueOf(current_dy - starting_dy));
                }

                new_viewX = ((starting_viewX + starting_x) * starting_zoomX - // advance to starting tap in bitmap columns
                        (current_x - starting_x) * new_zoomX - // shift in bitmap columns
                        starting_x * new_zoomX) / // rewind to left edge in bitmap columns
                        new_zoomX; // convert to screen columns

                new_viewX = Stuff.clamp(new_viewX, 0, Stuff.bitmapW / new_zoomX - Stuff.waveformW);

                new_viewY = ((starting_viewY + starting_y) * starting_zoomY -
                        (current_y - starting_y) * new_zoomY -
                        starting_y * new_zoomY) /
                        new_zoomY;
                new_viewY = Stuff.clamp(new_viewY, 0, Stuff.BITMAP_H / new_zoomY - Stuff.waveformH);

                long dt = timer.getDiff();
                timer.reset();
                if(dt > 0) {
                    Stuff.velocity_x = (float) (new_viewX - prev_viewX) / dt;
                    Stuff.velocity_y = (float) (new_viewY - prev_viewY) / dt;
                }
                else
                {
                    Stuff.velocity_x = 0;
                    Stuff.velocity_y = 0;
                }
                //Log.i("Waveform", "onTouch starting_viewX=" + starting_viewX + " new_viewX=" + new_viewX);

                prev_pointers = pointers;
                prev_viewX = new_viewX;
                prev_viewY = new_viewY;
                //Log.i("Waveform", "onTouch default pointers=" + pointers + " actionPointerId=" + actionPointerId + " enum=" + actionEnum);
                if(Stuff.zoomX != new_zoomX ||
                    Stuff.zoomY != new_zoomY ||
                    Stuff.viewX != new_viewX ||
                    Stuff.viewY != new_viewY)
                {
                    synchronized (Stuff.instance)
                    {
                        Stuff.zoomX = new_zoomX;
                        Stuff.zoomY = new_zoomY;
                        Stuff.viewX = new_viewX;
                        Stuff.viewY = new_viewY;
                        PlayerWin.instance.drawSpectrum();
                    }
                }

        }
        return true;
    }
}




