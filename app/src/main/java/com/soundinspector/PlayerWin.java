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

import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.view.Choreographer;
import android.view.SurfaceView;
import android.view.View;
import android.view.Window;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

public class PlayerWin extends Activity
{
    static PlayerWin instance;
    Bitmap waveformBitmap;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        instance = this;
        Stuff.init(this);

        Log.i("PlayerWin", "onCreate FileReader.instance=" + FileReader.instance);


        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.playerwin);
        update();
    }


    public void onResume() {
        super.onResume();
        Log.i("PlayerWin", "onResume FileSelect.success=" +
                    FileSelect.success +
                " selectedFile=" + FileSelect.selectedFile);
        update();
    }

    void update()
    {
        // user finished a file selection
        if(FileSelect.success)
        {
            FileSelect.success = false;
            Player.instance.stopPlayback();
            Stuff.instance.reset(true, true);
            Stuff.currentDir = FileSelect.selectedDir;
            Stuff.currentFile = FileSelect.selectedFile;
            Stuff.save(getApplicationContext());
        }

        // read the sound file
        if(Files.exists(Paths.get(Stuff.currentDir + "/" + Stuff.currentFile)))
        {
            FileReader.instance.submitFile();
        }

        TextView text = (TextView)findViewById(R.id.currentFile);
        if(text != null) text.setText(Stuff.currentFile);

        updateButton();

    }

    void updateButton()
    {
        runOnUiThread(new Runnable() {
            public void run() {
                Button button = (Button) findViewById(R.id.play);
                if (button != null) {
                    if (Stuff.playing) {
                        button.setText(Html.fromHtml("&#9208;").toString());
                    } else {
                        button.setText(Html.fromHtml("&#9654;").toString());
                    }
                }
            }
        });
    }

	public void drawMessage(String s)
    {
        runOnUiThread(new Runnable()
        {
            public void run()
            {
                SurfaceView waveform = (SurfaceView)PlayerWin.instance.findViewById(R.id.waveform);
                if(waveform != null) {
                    Canvas canvas = waveform.getHolder().lockCanvas();
                    if (canvas != null) {
                        Paint p = new Paint();
                        Rect textSize = new Rect();

                        p.setStyle(Paint.Style.FILL);
                        p.setColor(Color.WHITE);
                        canvas.drawRect(0, 0, canvas.getWidth(), canvas.getHeight(), p);
                        p.setColor(Color.BLACK);
                        p.setTypeface(Typeface.create("SansSerif", Typeface.BOLD));
                        p.setTextSize(40);
                        p.getTextBounds(s, 0, s.length(), textSize);

                        canvas.drawText(s,
                                canvas.getWidth() / 2 - textSize.width() / 2,
                                canvas.getHeight() / 2 - textSize.height() / 2,
                                p);
                        waveform.getHolder().unlockCanvasAndPost(canvas);
                    }
                }
            }
        });
    }

    // v = 0 - 1
    static float freq_scale(float v)
    {
        final float gamma = 1000;
        return (float)((Math.pow(gamma + 1, v) - 1) / gamma);
    }

    // v = 0 - 1
    static float mag_scale(float v)
    {
        final float gamma = 5;
        if(v == 0) return 0;
        return (float)(Math.pow(v, 1/gamma));
    }

    public void drawSpectrum()
    {
        runOnUiThread(new Runnable() {
            public void run() {
                SurfaceView waveform = (SurfaceView)PlayerWin.instance.findViewById(R.id.waveform);
                if(waveform != null) {
                    Canvas canvas = waveform.getHolder().lockCanvas();
                    if (canvas != null)
                    {
                        synchronized (Stuff.instance)
                        {
                            if(Stuff.waveformBuffer != null) {
                                Paint p = new Paint();
                                if (waveformBitmap == null || waveformBitmap.getWidth() != Stuff.bitmapW) {
                                    waveformBitmap = Bitmap.createBitmap(Stuff.bitmapW, Stuff.BITMAP_H, Bitmap.Config.ARGB_8888);
                                    Log.i("PlayerWin", "drawSpectrum rowspan=" + waveformBitmap.getRowBytes());
                                }
                                Log.i("PlayerWin", "drawSpectrum zoomX=" + Stuff.zoomX +
                                        " zoomY=" + Stuff.zoomY +
                                        " viewX=" + Stuff.viewX +
                                        " viewY=" + Stuff.viewY);

                                ByteBuffer byteBuffer = ByteBuffer.wrap(Stuff.waveformBuffer);
                                waveformBitmap.copyPixelsFromBuffer(byteBuffer);
// get exact src coords
                                float srcX1 = Stuff.viewX * Stuff.zoomX; // starting input column in bitmap
                                float srcY1 = Stuff.viewY * Stuff.zoomY; // starting input row in bitmap
                                float srcX2 = srcX1 + Stuff.waveformW * Stuff.zoomX;
                                float srcY2 = srcY1 + Stuff.waveformH * Stuff.zoomY;

// round src coords to nearest int to fix jitter
                                srcX2 = (int)srcX2 + 1;
                                srcY2 = (int)srcY2 + 1;
                                srcX1 = (int)srcX1;
                                srcY1 = (int)srcY1;

// convert src coords to dst coords
                                float dstX1 = srcX1 / Stuff.zoomX - Stuff.viewX;
                                float dstY1 = Stuff.waveformY + srcY1 / Stuff.zoomY - Stuff.viewY;
                                float dstX2 = Stuff.waveformW + srcX2 / Stuff.zoomX - (Stuff.viewX + Stuff.waveformW);
                                float dstY2 = Stuff.waveformY + Stuff.waveformH + srcY2 / Stuff.zoomY - (Stuff.viewY + Stuff.waveformH);


                                canvas.drawBitmap(waveformBitmap,
                                        new Rect((int)srcX1, (int)srcY1, (int)srcX2, (int)srcY2), // src
                                        new RectF(dstX1, dstY1, dstX2, dstY2), // dst
                                        p);

                                p.setStyle(Paint.Style.FILL);
                                p.setColor(Color.WHITE);
                                canvas.drawRect(new Rect(0, 0, Stuff.canvasW, Stuff.waveformY - 2), p);
                                p.setColor(Color.BLACK);                                p.setStrokeWidth(5);
                                p.setStrokeWidth(1);
                                p.setStyle(Paint.Style.STROKE);
                                canvas.drawLine(0, Stuff.waveformY - 1, Stuff.canvasW, Stuff.waveformY - 1, p);

                                p.setStyle(Paint.Style.STROKE);
                                p.setColor(Color.RED);
                                p.setStrokeWidth(5);
                                float playStartX = Stuff.playStart / Stuff.zoomX - Stuff.viewX;
                                canvas.drawLine(playStartX, 0, playStartX, Stuff.canvasH, p);

                                if(Stuff.playing || Stuff.paused) {
                                    p.setColor(Color.GREEN);
                                    float playCurrentX = Stuff.playCurrent / Stuff.zoomX - Stuff.viewX;
                                    canvas.drawLine(playCurrentX, 0, playCurrentX, Stuff.canvasH, p);
                                }
                            } else {
                                Log.i("PlayerWin", "drawSpectrum no canvas or waveformBuffer");
                            }
                        }

                        waveform.getHolder().unlockCanvasAndPost(canvas);
                    }
                }
            }
        });
    }

    // dt in ms
    void handleVelocity(boolean first, long dt)
    {
        Stuff.velocity_timer.reset();
        final double FPS = 60;
        final float minV = (float)0.5;
//        final float elapsed_brake = (float)0.96;
// Braking rate per ms
        final double brake = Math.pow(0.96, FPS / 1000);
// Total amount of braking in the elapsed time
        double elapsed_brake = Math.pow(brake, dt);
//        Log.i("PlayerWin", "handleVelocity dt=" + dt+ " brake=" + brake + " elapsed_brake=" + elapsed_brake);
        if(!first && !Stuff.is_dragging) {
            synchronized (Stuff.instance) {
                Stuff.viewX += Stuff.velocity_x * (1000 / FPS);
                Stuff.viewY += Stuff.velocity_y * (1000 / FPS);
                Stuff.viewX = Stuff.clamp(Stuff.viewX, 0, Stuff.bitmapW / Stuff.zoomX - Stuff.waveformW);
                Stuff.viewY = Stuff.clamp(Stuff.viewY, 0, Stuff.BITMAP_H / Stuff.zoomY - Stuff.waveformH);
                drawSpectrum();
            }
            if(Math.abs(Stuff.velocity_x) > minV)
                Stuff.velocity_x *= elapsed_brake;
            else
                Stuff.velocity_x = 0;
            if(Math.abs(Stuff.velocity_y) > minV)
                Stuff.velocity_y *= elapsed_brake;
            else
                Stuff.velocity_y = 0;
        }
        // schedule next frame
        if(!Stuff.is_dragging && (Stuff.velocity_x != 0 || Stuff.velocity_y != 0)) {
            Choreographer.getInstance().postFrameCallbackDelayed(new Choreographer.FrameCallback() {
                 @Override
                 public void doFrame(long l) {
                    PlayerWin.instance.handleVelocity(false, Stuff.velocity_timer.getDiff());
                 }
             },
            (int)(1000 / FPS));
        }
    }

    public void onClick(View view)
    {
    	switch (view.getId()) {
            case R.id.load:
                FileSelect.nextWindow = PlayerWin.class;
                startActivity( new Intent(getApplicationContext(), FileSelect.class));
                break;

            case R.id.save:
                break;

            case R.id.play:
                if(Stuff.playing)
                    Player.instance.pause();
                else
                    Player.instance.play();
                updateButton();
                break;
            case R.id.stop:
                if(Stuff.playing || Stuff.paused)
                    Player.instance.stopPlayback();
                updateButton();
                drawSpectrum();
                break;

            case R.id.rewind:
                Player.instance.stopPlayback();
                Stuff.playStart = 0;
                Stuff.viewX = 0;
                updateButton();
                drawSpectrum();
                break;

            case R.id.end:
                Player.instance.stopPlayback();
                Stuff.playStart = Stuff.bitmapW;
                Stuff.viewX = Stuff.bitmapW / Stuff.zoomX - Stuff.waveformW;
                updateButton();
                drawSpectrum();
                break;
        }
    }
}

